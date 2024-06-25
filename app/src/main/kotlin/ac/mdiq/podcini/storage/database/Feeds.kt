package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.net.download.DownloadError
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.database.Episodes.EpisodeDuplicateGuesser
import ac.mdiq.podcini.storage.database.Episodes.deleteEpisodes
import ac.mdiq.podcini.storage.database.LogsAndStats.addDownloadStatus
import ac.mdiq.podcini.storage.database.Queues.removeFromAllQueuesQuiet
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.FeedPreferences
import ac.mdiq.podcini.storage.model.FeedPreferences.AutoDeleteAction
import ac.mdiq.podcini.storage.model.FeedPreferences.Companion.TAG_ROOT
import ac.mdiq.podcini.storage.utils.VolumeAdaptionSetting
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import ac.mdiq.podcini.util.sorting.EpisodePubdateComparator
import android.app.backup.BackupManager
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.*
import java.util.concurrent.ExecutionException

object Feeds {
    private val TAG: String = Feeds::class.simpleName ?: "Anonymous"
//    internal val feeds: MutableList<Feed> = mutableListOf()
    private val feedMap: MutableMap<Long, Feed> = mutableMapOf()
    private val tags: MutableList<String> = mutableListOf()

    fun getFeedList(): List<Feed> {
        return feedMap.values.toList()
    }

    fun getTags(): List<String> {
        return tags
    }

    fun updateFeedMap() {
        Logd(TAG, "updateFeedMap called")
        val feeds_ = realm.query(Feed::class).find()
        feedMap.clear()
        feedMap.putAll(feeds_.associateBy { it.id })
        buildTags()
    }

    fun buildTags() {
        val tagsSet = mutableSetOf<String>()
        val feedsCopy = feedMap.values
        for (feed in feedsCopy) {
            if (feed.preferences != null) {
                for (tag in feed.preferences!!.tags) {
                    if (tag != TAG_ROOT) tagsSet.add(tag)
                }
            }
        }
        tags.clear()
        tags.addAll(tagsSet)
        tags.sort()
    }

    fun getFeedListDownloadUrls(): List<String> {
        Logd(TAG, "getFeedListDownloadUrls called")
        val result: MutableList<String> = mutableListOf()
//        val feeds = realm.query(Feed::class).find()
        for (f in feedMap.values) {
            val url = f.downloadUrl
            if (url != null && !url.startsWith(Feed.PREFIX_LOCAL_FOLDER)) result.add(url)
        }
        return result
    }

//    TODO: some callers don't need to copy
    fun getFeed(feedId: Long, copy: Boolean = false): Feed? {
//        Logd(TAG, "getFeed() called with: $feedId")
//        val f = realm.query(Feed::class).query("id == $0", feedId).first().find()
//        return if (f != null && f.isManaged()) realm.copyFromRealm(f) else null
        val f = feedMap[feedId]
        return if (f != null) {
            if (copy) realm.copyFromRealm(f)
            else f
        } else null
    }

    private fun searchFeedByIdentifyingValueOrID(feed: Feed, copy: Boolean = false): Feed? {
        Logd(TAG, "searchFeedByIdentifyingValueOrID called")
        if (feed.id != 0L) return getFeed(feed.id, copy)
        val feeds = getFeedList()
        for (f in feeds) {
            if (f.identifyingValue == feed.identifyingValue) return if (copy) realm.copyFromRealm(f) else f
        }
        return null
    }

    /**
     * Adds new Feeds to the database or updates the old versions if they already exists. If another Feed with the same
     * identifying value already exists, this method will add new FeedItems from the new Feed to the existing Feed.
     * These FeedItems will be marked as unread with the exception of the most recent FeedItem.
     * This method can update multiple feeds at once. Submitting a feed twice in the same method call can result in undefined behavior.
     * This method should NOT be executed on the GUI thread.
     * @param context Used for accessing the DB.
     * @param newFeed The new Feed object.
     * @param removeUnlistedItems The episode list in the new Feed object is considered to be exhaustive.
     * I.e. episodes are removed from the database if they are not in this episode list.
     * @return The updated Feed from the database if it already existed, or the new Feed from the parameters otherwise.
     */
    @Synchronized
    fun updateFeed(context: Context, newFeed: Feed, removeUnlistedItems: Boolean): Feed? {
        Logd(TAG, "updateFeed called")
//        TODO: check further on enclosing in realm write block
        var resultFeed: Feed?
        val unlistedItems: MutableList<Episode> = ArrayList()

        // Look up feed in the feedslist
        val savedFeed = searchFeedByIdentifyingValueOrID(newFeed, true)
        if (savedFeed == null) {
            Logd(TAG, "Found no existing Feed with title " + newFeed.title + ". Adding as new one.")
            Logd(TAG, "newFeed.episodes: ${newFeed.episodes.size}")
            resultFeed = newFeed
        } else {
            Logd(TAG, "Feed with title " + newFeed.title + " already exists. Syncing new with existing one.")
            newFeed.episodes.sortWith(EpisodePubdateComparator())

            if (newFeed.pageNr == savedFeed.pageNr) {
                if (savedFeed.compareWithOther(newFeed)) {
                    Logd(TAG, "Feed has updated attribute values. Updating old feed's attributes")
                    savedFeed.updateFromOther(newFeed)
                }
            } else {
                Logd(TAG, "New feed has a higher page number.")
                savedFeed.nextPageLink = newFeed.nextPageLink
            }
            if (savedFeed.preferences != null && savedFeed.preferences!!.compareWithOther(newFeed.preferences)) {
                Logd(TAG, "Feed has updated preferences. Updating old feed's preferences")
                savedFeed.preferences!!.updateFromOther(newFeed.preferences)
            }

            val priorMostRecent = savedFeed.mostRecentItem
            val priorMostRecentDate: Date? = priorMostRecent?.getPubDate()

            var idLong = Feed.newId()
            // Look for new or updated Items
            for (idx in newFeed.episodes.indices) {
                val episode = newFeed.episodes[idx]

                val possibleDuplicate = searchEpisodeGuessDuplicate(newFeed.episodes, episode)
                if (!newFeed.isLocalFeed && possibleDuplicate != null && episode !== possibleDuplicate) {
                    // Canonical episode is the first one returned (usually oldest)
                    addDownloadStatus(DownloadResult(savedFeed.id, episode.title ?: "", DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE, false,
                        """
                            The podcast host appears to have added the same episode twice. Podcini still refreshed the feed and attempted to repair it.
                            
                            Original episode:
                            ${duplicateEpisodeDetails(episode)}
                            
                            Second episode that is also in the feed:
                            ${duplicateEpisodeDetails(possibleDuplicate)}
                            """.trimIndent()))
                    continue
                }

                var oldItem = searchEpisodeByIdentifyingValue(savedFeed.episodes, episode)
                if (!newFeed.isLocalFeed && oldItem == null) {
                    oldItem = searchEpisodeGuessDuplicate(savedFeed.episodes, episode)
                    if (oldItem != null) {
                        Logd(TAG, "Repaired duplicate: $oldItem, $episode")
                        addDownloadStatus(DownloadResult(savedFeed.id,
                            episode.title ?: "", DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE, false,
                            """
                                The podcast host changed the ID of an existing episode instead of just updating the episode itself. Podcini still refreshed the feed and attempted to repair it.
                                
                                Original episode:
                                ${duplicateEpisodeDetails(oldItem)}
                                
                                Now the feed contains:
                                ${duplicateEpisodeDetails(episode)}
                                """.trimIndent()))
                        oldItem.identifier = episode.identifier

                        if (oldItem.isPlayed() && oldItem.media != null) {
                            val durs = oldItem.media!!.getDuration() / 1000
                            val action = EpisodeAction.Builder(oldItem, EpisodeAction.PLAY)
                                .currentTimestamp()
                                .started(durs)
                                .position(durs)
                                .total(durs)
                                .build()
                            SynchronizationQueueSink.enqueueEpisodeActionIfSyncActive(context, action)
                        }
                    }
                }

                if (oldItem != null) oldItem.updateFromOther(episode)
                else {
                    Logd(TAG, "Found new episode: ${episode.title}")
                    episode.feed = savedFeed
                    episode.id = idLong++
                    episode.feedId = savedFeed.id
                    if (episode.media != null) episode.media!!.id = episode.id

                    if (idx >= savedFeed.episodes.size) savedFeed.episodes.add(episode)
                    else savedFeed.episodes.add(idx, episode)

                    val pubDate = episode.getPubDate()
                    if (pubDate == null || priorMostRecentDate == null || priorMostRecentDate.before(pubDate) || priorMostRecentDate == pubDate) {
                        Logd(TAG, "Marking episode published on $pubDate new, prior most recent date = $priorMostRecentDate")
                        episode.setNew()
                    }
//                    idLong += 1
                }
            }

            // identify episodes to be removed
            if (removeUnlistedItems) {
                val it = savedFeed.episodes.toMutableList().iterator()
                while (it.hasNext()) {
                    val feedItem = it.next()
                    if (searchEpisodeByIdentifyingValue(newFeed.episodes, feedItem) == null) {
                        unlistedItems.add(feedItem)
                        it.remove()
                    }
                }
            }

            // update attributes
            savedFeed.lastUpdate = newFeed.lastUpdate
            savedFeed.type = newFeed.type
            savedFeed.lastUpdateFailed = false
            resultFeed = savedFeed
        }

        try {
            if (savedFeed == null) {
                addNewFeedsSync(context, newFeed)
                // Update with default values that are set in database
                resultFeed = searchFeedByIdentifyingValueOrID(newFeed)
            } else persistFeedsSync(savedFeed)
            updateFeedMap()
            if (removeUnlistedItems) runBlocking { deleteEpisodes(context, unlistedItems).join() }
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: ExecutionException) {
            e.printStackTrace()
        }

        if (savedFeed != null) EventFlow.postEvent(FlowEvent.FeedListUpdateEvent(savedFeed))
        else EventFlow.postEvent(FlowEvent.FeedListUpdateEvent(emptyList<Long>()))

        return resultFeed
    }

    /**
     * Get an episode by its identifying value in the given list
     */
    private fun searchEpisodeByIdentifyingValue(episodes: List<Episode>?, searchItem: Episode): Episode? {
        if (episodes.isNullOrEmpty()) return null
        for (episode in episodes) {
            if (episode.identifyingValue == searchItem.identifyingValue) return episode
        }
        return null
    }

    /**
     * Guess if one of the episodes could actually mean the searched episode, even if it uses another identifying value.
     * This is to work around podcasters breaking their GUIDs.
     */
    private fun searchEpisodeGuessDuplicate(episodes: List<Episode>?, searchItem: Episode): Episode? {
        if (episodes.isNullOrEmpty()) return null
        for (episode in episodes) {
            if (EpisodeDuplicateGuesser.sameAndNotEmpty(episode.identifier, searchItem.identifier)) return episode
        }
        for (episode in episodes) {
            if (EpisodeDuplicateGuesser.seemDuplicates(episode, searchItem)) return episode
        }
        return null
    }

    private fun duplicateEpisodeDetails(episode: Episode): String {
        return ("""
    Title: ${episode.title}
    ID: ${episode.identifier}
    """.trimIndent()
                + (if ((episode.media == null)) "" else """
     
     URL: ${episode.media!!.downloadUrl}
     """.trimIndent()))
    }

    fun persistFeedLastUpdateFailed(feed: Feed, lastUpdateFailed: Boolean) : Job {
        Logd(TAG, "persistFeedLastUpdateFailed called")
        return runOnIOScope {
            feed.lastUpdateFailed = lastUpdateFailed
            upsert(feed) {}
            EventFlow.postEvent(FlowEvent.FeedListUpdateEvent(feed.id))
        }
    }

    fun updateFeedDownloadURL(original: String, updated: String) : Job {
        Logd(TAG, "updateFeedDownloadURL(original: $original, updated: $updated)")
        return runOnIOScope {
            realm.write {
                val feed = query(Feed::class).query("downloadUrl == $0", original).first().find()
                if (feed != null) {
                    feed.downloadUrl = updated
//                    upsert(feed) {}
                }
            }
        }
    }

    private fun addNewFeedsSync(context: Context, vararg feeds: Feed) {
        Logd(TAG, "addNewFeeds called")
        realm.writeBlocking {
            var idLong = Feed.newId()
            for (feed in feeds) {
                feed.id = idLong
                if (feed.preferences == null)
                    feed.preferences = FeedPreferences(feed.id, false, AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, "", "")
                else feed.preferences!!.feedID = feed.id

                Logd(TAG, "feed.episodes: ${feed.episodes.size}")
                for (episode in feed.episodes) {
                    episode.id = idLong++
                    episode.feedId = feed.id
                    if (episode.media != null) episode.media!!.id = episode.id
//                        copyToRealm(episode)  // no need if episodes is a relation of feed, otherwise yes.
//                    idLong += 1
                }
                copyToRealm(feed)
            }
        }
        for (feed in feeds) {
            if (!feed.isLocalFeed && feed.downloadUrl != null)
                SynchronizationQueueSink.enqueueFeedAddedIfSyncActive(context, feed.downloadUrl!!)
        }
        val backupManager = BackupManager(context)
        backupManager.dataChanged()
    }

    private fun persistFeedsSync(vararg feeds: Feed) {
        Logd(TAG, "persistCompleteFeeds called")
        for (feed in feeds) {
            upsertBlk(feed) {}
        }
    }

    fun persistFeedPreferences(feed: Feed) : Job {
        Logd(TAG, "persistCompleteFeeds called")
        return runOnIOScope {
            val feed_ = realm.query(Feed::class, "id == ${feed.id}").first().find()
            if (feed_ != null) {
                realm.write {
                    findLatest(feed_)?.let { it.preferences = feed.preferences }
                }
            } else upsert(feed) {}
            if (feed.preferences != null) EventFlow.postEvent(FlowEvent.FeedPrefsChangeEvent(feed.preferences!!))
        }
    }

    /**
     * Deletes a Feed and all downloaded files of its components like images and downloaded episodes.
     * @param context A context that is used for opening a database connection.
     * @param feedId  ID of the Feed that should be deleted.
     */
    fun deleteFeed(context: Context, feedId: Long, postEvent: Boolean = true) : Job {
        Logd(TAG, "deleteFeed called")
        return runOnIOScope {
            val feed = getFeed(feedId)
            if (feed != null) {
                val eids = feed.episodes.map { it.id }
//                remove from queues
                removeFromAllQueuesQuiet(eids)
//                remove media files
                deleteMediaFilesQuiet(context, feed.episodes)
                realm.write {
                    val feed_ = query(Feed::class).query("id == $0", feedId).first().find()
                    if (feed_ != null) {
                        val episodes = feed_.episodes.toList()
                        if (episodes.isNotEmpty()) episodes.forEach { e -> delete(e) }
                        val feedToDelete = findLatest(feed_)
                        if (feedToDelete != null) delete(feedToDelete)
                    }
                }
                if (!feed.isLocalFeed && feed.downloadUrl != null) SynchronizationQueueSink.enqueueFeedRemovedIfSyncActive(context, feed.downloadUrl!!)
                if (postEvent) EventFlow.postEvent(FlowEvent.FeedListUpdateEvent(feed))
            }
        }
    }

    private fun deleteMediaFilesQuiet(context: Context, episodes: List<Episode>) {
        for (episode in episodes) {
            val media = episode.media ?: continue
            val url = media.fileUrl
            when {
                url != null && url.startsWith("content://") -> {
                    // Local feed
                    val documentFile = DocumentFile.fromSingleUri(context, Uri.parse(media.fileUrl))
                    documentFile?.delete()
                    episode.media?.fileUrl = null
                }
                url != null -> {
                    // delete downloaded media file
                    val mediaFile = File(url)
                    mediaFile.delete()
                    episode.media?.downloaded = false
                    episode.media?.fileUrl = null
                    episode.media?.hasEmbeddedPicture = false
                }
            }
        }
    }

    @JvmStatic
    fun shouldAutoDeleteItemsOnFeed(feed: Feed): Boolean {
        if (!UserPreferences.isAutoDelete) return false
        return !feed.isLocalFeed || UserPreferences.isAutoDeleteLocal
    }
}