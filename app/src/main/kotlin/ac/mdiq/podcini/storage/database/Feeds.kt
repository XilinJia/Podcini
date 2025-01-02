package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.net.download.DownloadError
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.preferences.UserPreferences.isAutoDelete
import ac.mdiq.podcini.preferences.UserPreferences.isAutoDeleteLocal
import ac.mdiq.podcini.storage.database.Episodes.deleteEpisodes
import ac.mdiq.podcini.storage.database.Feeds.EpisodeAssistant.searchEpisodeByIdentifyingValue
import ac.mdiq.podcini.storage.database.Feeds.EpisodeDuplicateGuesser.canonicalizeTitle
import ac.mdiq.podcini.storage.database.Feeds.EpisodeDuplicateGuesser.datesLookSimilar
import ac.mdiq.podcini.storage.database.Feeds.EpisodeDuplicateGuesser.durationsLookSimilar
import ac.mdiq.podcini.storage.database.Feeds.EpisodeDuplicateGuesser.mimeTypeLooksSimilar
import ac.mdiq.podcini.storage.database.LogsAndStats.addDownloadStatus
import ac.mdiq.podcini.storage.database.Queues.addToQueueSync
import ac.mdiq.podcini.storage.database.Queues.removeFromAllQueuesQuiet
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.Feed.Companion.MAX_NATURAL_SYNTHETIC_ID
import ac.mdiq.podcini.storage.model.Feed.AudioType
import ac.mdiq.podcini.storage.model.Feed.Companion.TAG_ROOT
import ac.mdiq.podcini.storage.utils.StorageUtils.feedfilePath
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.showStackTrace
import android.app.backup.BackupManager
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.realm.kotlin.ext.asFlow
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.notifications.UpdatedObject
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.*
import java.io.File
import java.text.DateFormat
import java.util.*
import java.util.concurrent.ExecutionException
import kotlin.math.abs

object Feeds {
    private val TAG: String = Feeds::class.simpleName ?: "Anonymous"
    private val tags: MutableList<String> = mutableListOf()

    @Synchronized
    fun getFeedList(queryString: String = ""): List<Feed> {
        return if (queryString.isEmpty()) realm.query(Feed::class).find()
        else realm.query(Feed::class, queryString).find()
    }

    fun getFeedCount(): Int = realm.query(Feed::class).count().find().toInt()

    fun getTags(): List<String> = tags

    fun buildTags() {
        val tagsSet = mutableSetOf<String>()
        val feedsCopy = getFeedList()
        for (feed in feedsCopy) tagsSet.addAll(feed.tags.filter { it != TAG_ROOT })
        val newTags = tagsSet - tags.toSet()
        if (newTags.isNotEmpty()) {
            tags.clear()
            tags.addAll(tagsSet)
            tags.sort()
            EventFlow.postEvent(FlowEvent.FeedTagsChangedEvent())
        }
    }

    fun monitorFeeds() {
        val feeds = realm.query(Feed::class).find()
        for (f in feeds) monitorFeed(f)

        val feedQuery = realm.query(Feed::class)
        CoroutineScope(Dispatchers.Default).launch {
            val feedsFlow = feedQuery.asFlow()
            feedsFlow.collect { changes: ResultsChange<Feed> ->
                when (changes) {
                    is UpdatedResults -> {
                        when {
                            changes.insertions.isNotEmpty() -> {
                                for (i in changes.insertions) {
                                    Logd(TAG, "monitorFeeds inserted feed: ${changes.list[i].title}")
                                    monitorFeed(changes.list[i])
                                }
                                EventFlow.postEvent(FlowEvent.FeedListEvent(FlowEvent.FeedListEvent.Action.ADDED))
                            }
//                            changes.changes.isNotEmpty() -> {
//                                for (i in changes.changes) {
//                                    Logd(TAG, "monitorFeeds feed changed: ${changes.list[i].title}")
//                                }
//                            }
                            changes.deletions.isNotEmpty() -> {
                                Logd(TAG, "monitorFeeds feed deleted: ${changes.deletions.size}")
                                buildTags()
                            }
                        }
                    }
                    else -> {
                        // types other than UpdatedResults are not changes -- ignore them
                    }
                }
            }
        }
    }

    private fun monitorFeed(feed: Feed) {
        CoroutineScope(Dispatchers.Default).launch {
            val feedFlow = feed.asFlow()
            feedFlow.collect { changes: SingleQueryChange<Feed> ->
                when (changes) {
                    is UpdatedObject -> {
                        Logd(TAG, "monitorFeed UpdatedObject ${changes.obj.title} ${changes.changedFields.joinToString()}")
                        EventFlow.postEvent(FlowEvent.FeedChangeEvent(changes.obj))
                    }
//                    is DeletedObject -> {
//                        Logd(TAG, "monitorFeed DeletedObject ${feed.title}")
//                        EventFlow.postEvent(FlowEvent.FeedListEvent(FlowEvent.FeedListEvent.Action.REMOVED, feed.id))
//                    }
                    else -> {}
                }
            }
        }
    }

    fun getFeedListDownloadUrls(): List<String> {
        Logd(TAG, "getFeedListDownloadUrls called")
        val result: MutableList<String> = mutableListOf()
        val feeds = getFeedList()
        for (f in feeds) {
            val url = f.downloadUrl
            if (url != null && !url.startsWith(Feed.PREFIX_LOCAL_FOLDER)) result.add(url)
        }
        return result
    }

    fun getFeed(feedId: Long, copy: Boolean = false): Feed? {
        val f = realm.query(Feed::class, "id == $feedId").first().find()
        return if (f != null) {
            if (copy) realm.copyFromRealm(f) else f
        } else null
    }

    private fun searchFeedByIdentifyingValueOrID(feed: Feed, copy: Boolean = false): Feed? {
        Logd(TAG, "searchFeedByIdentifyingValueOrID called")
        if (feed.id != 0L) return getFeed(feed.id, copy)
        val feeds = getFeedList()
        val feedId = feed.identifyingValue
        for (f in feeds) if (f.identifyingValue == feedId) return if (copy) realm.copyFromRealm(f) else f
        return null
    }

    fun isSubscribed(feed: Feed): Boolean {
        val f = realm.query(Feed::class, "eigenTitle == $0 && author == $1", feed.eigenTitle, feed.author).first().find()
        return f != null
    }

    fun getFeedByTitleAndAuthor(title: String, author: String): Feed? {
        return realm.query(Feed::class, "eigenTitle == $0 && author == $1", title, author).first().find()
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
    fun updateFeed(context: Context, newFeed: Feed, removeUnlistedItems: Boolean = false, overwriteOld: Boolean = false): Feed? {
        Logd(TAG, "updateFeed called")
        showStackTrace()
        var resultFeed: Feed?
//        val unlistedItems: MutableList<Episode> = ArrayList()

        // Look up feed in the feedslist
        val savedFeed = searchFeedByIdentifyingValueOrID(newFeed, true)
        if (savedFeed == null) {
            Logd(TAG, "Found no existing Feed with title ${newFeed.title}. Adding as new one.")
            Logd(TAG, "newFeed.episodes: ${newFeed.episodes.size}")
            resultFeed = newFeed
            try {
                addNewFeedsSync(context, newFeed)
                // Update with default values that are set in database
                resultFeed = searchFeedByIdentifyingValueOrID(newFeed)
                // TODO: This doesn't appear needed as unlistedItems is still empty
//                if (removeUnlistedItems && unlistedItems.isNotEmpty()) runBlocking { deleteEpisodes(context, unlistedItems).join() }
            } catch (e: InterruptedException) { e.printStackTrace()
            } catch (e: ExecutionException) { e.printStackTrace() }
            return resultFeed
        }

        Logd(TAG, "Feed with title " + newFeed.title + " already exists. Syncing new with existing one.")
        newFeed.episodes.sortWith(EpisodePubdateComparator())
        if (newFeed.pageNr == savedFeed.pageNr) {
            if (overwriteOld) savedFeed.updateFromOther(newFeed, true)
            else if (savedFeed.differentFrom(newFeed)) {
                Logd(TAG, "Feed has updated attribute values. Updating old feed's attributes")
                savedFeed.updateFromOther(newFeed)
            }
        } else {
            Logd(TAG, "New feed has a higher page number: ${newFeed.nextPageLink}")
            savedFeed.nextPageLink = newFeed.nextPageLink
        }
        val priorMostRecent = savedFeed.mostRecentItem
        val priorMostRecentDate: Date? = priorMostRecent?.getPubDate()
        var idLong = Feed.newId()
        Logd(TAG, "updateFeed building savedFeedAssistant")
        val savedFeedAssistant = FeedAssistant(savedFeed)

        // Look for new or updated Items
        for (idx in newFeed.episodes.indices) {
            val episode = newFeed.episodes[idx]
            var oldItem = savedFeedAssistant.getEpisodeByIdentifyingValue(episode)
            if (!newFeed.isLocalFeed && oldItem == null) {
                oldItem = savedFeedAssistant.guessDuplicate(episode)
                if (oldItem != null) {
                    Logd(TAG, "Repaired duplicate: $oldItem, $episode")
                    addDownloadStatus(DownloadResult(savedFeed.id,
                        episode.title ?: "", DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE, false,
                        """
                                The podcast host changed the ID of an existing episode instead of just updating the episode itself. Podcini still refreshed the feed and attempted to repair it.
                                
                                Original episode:
                                ${EpisodeAssistant.duplicateEpisodeDetails(oldItem)}
                                
                                Now the feed contains:
                                ${EpisodeAssistant.duplicateEpisodeDetails(episode)}
                                """.trimIndent()))
                    oldItem.identifier = episode.identifier
                    // queue for syncing with server
                    if (isProviderConnected && oldItem.isPlayed()) {
                        val durs = oldItem.duration / 1000
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

            if (oldItem != null) oldItem.updateFromOther(episode, overwriteOld)
            else {
                Logd(TAG, "Found new episode: ${episode.title}")
                episode.feed = savedFeed
                episode.id = idLong++
                episode.feedId = savedFeed.id
                if (!savedFeed.hasVideoMedia && episode.getMediaType() == MediaType.VIDEO) savedFeed.hasVideoMedia = true
                if (idx >= savedFeed.episodes.size) savedFeed.episodes.add(episode)
                else savedFeed.episodes.add(idx, episode)

                savedFeedAssistant.addidvToMap(episode)

                val pubDate = episode.getPubDate()
                if (priorMostRecentDate == null || priorMostRecentDate.before(pubDate) || priorMostRecentDate == pubDate) {
                    Logd(TAG, "Marking episode published on $pubDate new, prior most recent date = $priorMostRecentDate")
                    episode.playState = PlayState.NEW.code
                    if (savedFeed.autoAddNewToQueue == true) {
                        val q = savedFeed.queue
                        if (q != null) runOnIOScope {  addToQueueSync(episode, q) }
                    }
                }
            }
        }
        savedFeedAssistant.clear()

        val unlistedItems: MutableList<Episode> = ArrayList()
        // identify episodes to be removed
        if (removeUnlistedItems) {
            Logd(TAG, "updateFeed building newFeedAssistant")
            val newFeedAssistant = FeedAssistant(newFeed, savedFeed.id)
            val it = savedFeed.episodes.toMutableList().iterator()
            while (it.hasNext()) {
                val feedItem = it.next()
                if (newFeedAssistant.getEpisodeByIdentifyingValue(feedItem) == null) {
                    unlistedItems.add(feedItem)
                    it.remove()
                }
            }
            newFeedAssistant.clear()
        }

        // update attributes
        savedFeed.lastUpdate = newFeed.lastUpdate
        savedFeed.type = newFeed.type
        savedFeed.lastUpdateFailed = false
        savedFeed.totleDuration = 0
        for (e in savedFeed.episodes) savedFeed.totleDuration += e.duration

        resultFeed = savedFeed
        try {
            upsertBlk(savedFeed) {}
            if (removeUnlistedItems && unlistedItems.isNotEmpty()) runBlocking { deleteEpisodes(context, unlistedItems).join() }
        } catch (e: InterruptedException) { e.printStackTrace()
        } catch (e: ExecutionException) { e.printStackTrace() }
        return resultFeed
    }

    @Synchronized
    fun updateFeedSimple(newFeed: Feed): Feed? {
        Logd(TAG, "updateFeedSimple called")
        val savedFeed = searchFeedByIdentifyingValueOrID(newFeed, true) ?: return newFeed

        Logd(TAG, "Feed with title " + newFeed.title + " already exists. Syncing new with existing one.")
        newFeed.episodes.sortWith(EpisodePubdateComparator())
        if (newFeed.pageNr == savedFeed.pageNr) {
            if (savedFeed.differentFrom(newFeed)) {
                Logd(TAG, "Feed has updated attribute values. Updating old feed's attributes")
                savedFeed.updateFromOther(newFeed)
            }
        } else {
            Logd(TAG, "New feed has a higher page number: ${newFeed.nextPageLink}")
            savedFeed.nextPageLink = newFeed.nextPageLink
        }
        val priorMostRecent = savedFeed.mostRecentItem
        val priorMostRecentDate: Date = priorMostRecent?.getPubDate() ?: Date(0)
        var idLong = Feed.newId()
        Logd(TAG, "updateFeedSimple building savedFeedAssistant")

        // Look for new or updated Items
        for (idx in newFeed.episodes.indices) {
            val episode = newFeed.episodes[idx]
            if (episode.duration < 1000) continue
            if (episode.getPubDate() <= priorMostRecentDate || episode.downloadUrl == priorMostRecent?.downloadUrl) continue

            Logd(TAG, "Found new episode: ${episode.title}")
            episode.feed = savedFeed
            episode.id = idLong++
            episode.feedId = savedFeed.id
            if (!savedFeed.hasVideoMedia && episode.getMediaType() == MediaType.VIDEO) savedFeed.hasVideoMedia = true
            if (idx >= savedFeed.episodes.size) savedFeed.episodes.add(episode)
            else savedFeed.episodes.add(idx, episode)

            val pubDate = episode.getPubDate()
            if (priorMostRecentDate < pubDate) {
                Logd(TAG, "Marking episode published on $pubDate new, prior most recent date = $priorMostRecentDate")
                episode.playState = PlayState.NEW.code
                if (savedFeed.autoAddNewToQueue == true) {
                    val q = savedFeed.queue
                    if (q != null) runOnIOScope {  addToQueueSync(episode, q) }
                }
            }
        }

        // update attributes
        savedFeed.lastUpdate = newFeed.lastUpdate
        savedFeed.type = newFeed.type
        savedFeed.lastUpdateFailed = false
        savedFeed.totleDuration = 0
        for (e in savedFeed.episodes) savedFeed.totleDuration += e.duration

        val resultFeed = savedFeed
        try { upsertBlk(savedFeed) {}
        } catch (e: InterruptedException) { e.printStackTrace()
        } catch (e: ExecutionException) { e.printStackTrace() }
        return resultFeed
    }

    fun persistFeedLastUpdateFailed(feed: Feed, lastUpdateFailed: Boolean) : Job {
        Logd(TAG, "persistFeedLastUpdateFailed called")
        return runOnIOScope {
            upsert(feed) { it.lastUpdateFailed = lastUpdateFailed }
            EventFlow.postEvent(FlowEvent.FeedListEvent(FlowEvent.FeedListEvent.Action.ERROR, feed.id))
        }
    }

    fun updateFeedDownloadURL(original: String, updated: String) : Job {
        Logd(TAG, "updateFeedDownloadURL(original: $original, updated: $updated)")
        return runOnIOScope {
            val feed = realm.query(Feed::class).query("downloadUrl == $0", original).first().find()
            if (feed != null) upsert(feed) { it.downloadUrl = updated }
        }
    }

    private fun addNewFeedsSync(context: Context, vararg feeds: Feed) {
        Logd(TAG, "addNewFeedsSync called")
        realm.writeBlocking {
            var idLong = Feed.newId()
            for (feed in feeds) {
                feed.id = idLong
//                if (feed.preferences == null)
//                    feed.preferences = FeedPreferences(feed.id, false, AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, "", "")
//                else feed.preferences!!.feedID = feed.id
                feed.totleDuration = 0
                Logd(TAG, "feed.episodes count: ${feed.episodes.size}")
                for (episode in feed.episodes) {
                    episode.id = idLong++
                    Logd(TAG, "addNewFeedsSync ${episode.id} ${episode.downloadUrl}")
                    episode.feedId = feed.id
                    feed.totleDuration += episode.duration
                }
                copyToRealm(feed)
            }
        }
        for (feed in feeds) {
            if (!feed.isLocalFeed && feed.downloadUrl != null) SynchronizationQueueSink.enqueueFeedAddedIfSyncActive(context, feed.downloadUrl!!)
        }
        val backupManager = BackupManager(context)
        backupManager.dataChanged()
    }

    /**
     * Deletes a Feed and all downloaded files of its components like images and downloaded episodes.
     * @param context A context that is used for opening a database connection.
     * @param feedId  ID of the Feed that should be deleted.
     */
    suspend fun deleteFeedSync(context: Context, feedId: Long, postEvent: Boolean = true) {
        Logd(TAG, "deleteFeed called")
        val feed = getFeed(feedId)
        if (feed != null) {
            val eids = feed.episodes.map { it.id }
//                remove from queues
            removeFromAllQueuesQuiet(eids)
//                remove media files
            realm.write {
                val feed_ = query(Feed::class).query("id == $0", feedId).first().find()
                if (feed_ != null) {
                    val episodes = feed_.episodes.toList()
                    if (episodes.isNotEmpty()) episodes.forEach { episode ->
                        val url = episode.fileUrl
                        when {
                            // Local feed
                            url != null && url.startsWith("content://") -> DocumentFile.fromSingleUri(context, Uri.parse(url))?.delete()
                            url != null -> File(url).delete()
                        }
                        delete(episode)
                    }
                    val feedToDelete = findLatest(feed_)
                    if (feedToDelete != null) delete(feedToDelete)
                }
            }
            if (!feed.isLocalFeed && feed.downloadUrl != null) SynchronizationQueueSink.enqueueFeedRemovedIfSyncActive(context, feed.downloadUrl!!)
        }
    }

    @JvmStatic
    fun allowForAutoDelete(feed: Feed): Boolean {
        if (!isAutoDelete) return false
        return !feed.isLocalFeed || isAutoDeleteLocal
    }

    fun createYTSyndicates() {
        getYoutubeSyndicate(true, false)
        getYoutubeSyndicate(false, false)
        getYoutubeSyndicate(true, true)
        getYoutubeSyndicate(false, true)
    }

    private fun getYoutubeSyndicate(video: Boolean, music: Boolean): Feed {
        var feedId: Long = if (video) 1 else 2
        if (music) feedId += 2  // music feed takes ids 3 and 4
        var feed = getFeed(feedId, true)
        if (feed != null) return feed

        val name = if (music) "YTMusic Syndicate" + if (video) "" else " Audio"
        else "Youtube Syndicate" + if (video) "" else " Audio"
        feed = createSynthetic(feedId, name)
        feed.type = Feed.FeedType.YOUTUBE.name
        feed.hasVideoMedia = video
        feed.audioTypeSetting = if (music) AudioType.MUSIC else AudioType.SPEECH
        feed.videoModePolicy = if (video) VideoMode.WINDOW_VIEW else VideoMode.AUDIO_ONLY
        upsertBlk(feed) {}
        EventFlow.postEvent(FlowEvent.FeedListEvent(FlowEvent.FeedListEvent.Action.ADDED))
        return feed
    }

    fun addToSyndicate(episode: Episode, feed: Feed) : Int {
        Logd(TAG, "addToYoutubeSyndicate: feed: ${feed.title}")
        if (searchEpisodeByIdentifyingValue(feed.episodes, episode) != null) return 2

        Logd(TAG, "addToSyndicate adding new episode: ${episode.title}")
        episode.feed = feed
        episode.id = Feed.newId()
        episode.feedId = feed.id
        upsertBlk(episode) {}
        upsertBlk(feed) { it.episodes.add(episode) }
        EventFlow.postStickyEvent(FlowEvent.FeedUpdatingEvent(false))
        return 1
    }

    fun createSynthetic(feedId: Long, name: String, video: Boolean = false): Feed {
        val feed = Feed()
        var feedId_ = feedId
        if (feedId_ <= 0) {
            var i = MAX_NATURAL_SYNTHETIC_ID
            while (true) {
                if (getFeed(i++) != null) continue
                feedId_ = --i
                break
            }
        }
        feed.id = feedId_
        feed.title = name
        feed.author = "Yours Truly"
        feed.downloadUrl = null
        feed.hasVideoMedia = video
        feed.fileUrl = File(feedfilePath, feed.getFeedfileName()).toString()
//        feed.preferences = FeedPreferences(feed.id, false, AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, "", "")
        feed.keepUpdated = false
        feed.queueId = -2L
        return feed
    }

    private fun getMiscSyndicate(): Feed {
        val feedId: Long = 11
        var feed = getFeed(feedId, true)
        if (feed != null) return feed

        feed = createSynthetic(feedId, "Misc Syndicate")
        feed.type = Feed.FeedType.RSS.name
        upsertBlk(feed) {}
        EventFlow.postEvent(FlowEvent.FeedListEvent(FlowEvent.FeedListEvent.Action.ADDED))
        return feed
    }

    fun addToMiscSyndicate(episode: Episode) {
        val feed = getMiscSyndicate()
        Logd(TAG, "addToMiscSyndicate: feed: ${feed.title}")
        if (searchEpisodeByIdentifyingValue(feed.episodes, episode) != null) return
        Logd(TAG, "addToMiscSyndicate adding new episode: ${episode.title}")
        episode.feed = feed
        episode.id = Feed.newId()
        episode.feedId = feed.id
        upsertBlk(episode) {}
        feed.episodes.add(episode)
        upsertBlk(feed) {}
        EventFlow.postStickyEvent(FlowEvent.FeedUpdatingEvent(false))
    }

    /**
     * Compares the pubDate of two FeedItems for sorting in reverse order
     */
    class EpisodePubdateComparator : Comparator<Episode> {
        override fun compare(lhs: Episode, rhs: Episode): Int {
            return rhs.pubDate.compareTo(lhs.pubDate)
        }
    }

    // savedFeedId == 0L means saved feed
    class FeedAssistant(val feed: Feed, private val savedFeedId: Long = 0L) {
        val map = mutableMapOf<String, Episode>()
        val tag: String = if (savedFeedId == 0L) "Saved feed" else "New feed"

        init {
            val iterator = feed.episodes.iterator()
             while (iterator.hasNext()) {
                 val e = iterator.next()
                 if (!e.identifier.isNullOrEmpty()) {
                     if (map.containsKey(e.identifier!!)) {
                         Logd(TAG, "FeedAssistant init $tag identifier duplicate: ${e.identifier} ${e.title}")
                         if (savedFeedId > 0L) {
                             addDownloadStatus(e, map[e.identifier!!]!!)
                             iterator.remove()
                         }
                         continue
                     }
                     map[e.identifier!!] = e
                 }
                 val idv = e.identifyingValue
                 if (idv != e.identifier && !idv.isNullOrEmpty()) {
                     if (map.containsKey(idv)) {
                         Logd(TAG, "FeedAssistant init $tag identifyingValue duplicate: $idv ${e.title}")
                         if (savedFeedId > 0L) {
                             addDownloadStatus(e, map[idv]!!)
                             iterator.remove()
                         }
                         continue
                     }
                     map[idv] = e
                 }
                 val url = e.downloadUrl
                 if (url != idv && !url.isNullOrEmpty()) {
                     if (map.containsKey(url)) {
                         Logd(TAG, "FeedAssistant init $tag url duplicate: $url ${e.title}")
                         if (savedFeedId > 0L) {
                             addDownloadStatus(e, map[url]!!)
                             iterator.remove()
                         }
                         continue
                     }
                     map[url] = e
                 }
                 val title = canonicalizeTitle(e.title)
                 if (title != idv && title.isNotEmpty()) {
                     if (map.containsKey(title)) {
                         val episode = map[title]
                         if (episode != null) {
                             if (datesLookSimilar(episode, e) && durationsLookSimilar(episode, e) && mimeTypeLooksSimilar(episode, e)) {
                                 Logd(TAG, "FeedAssistant init $tag title duplicate: $title ${e.title}")
                                 if (savedFeedId > 0L) {
                                     addDownloadStatus(e, episode)
                                     iterator.remove()
                                 }
                                 continue
                             }
                         }
                     }
//                     TODO: does it mean there are duplicate titles?
                     map[title] = e
                 }
             }
        }
        fun addUrlToMap(episode: Episode) {
            val url = episode.downloadUrl
            if (url != episode.identifyingValue && !url.isNullOrEmpty() && !map.containsKey(url)) map[url] = episode
        }
        fun addidvToMap(episode: Episode) {
            val idv = episode.identifyingValue
            if (idv != episode.identifier && !idv.isNullOrEmpty()) map[idv] = episode
        }
        private fun addDownloadStatus(episode: Episode, possibleDuplicate: Episode) {
            addDownloadStatus(DownloadResult(savedFeedId, episode.title ?: "", DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE, false,
                """
                                            The podcast host appears to have added the same episode twice. Podcini still refreshed the feed and attempted to repair it.
                
                                            Original episode:
                                            ${EpisodeAssistant.duplicateEpisodeDetails(episode)}
                
                                            Second episode that is also in the feed:
                                            ${EpisodeAssistant.duplicateEpisodeDetails(possibleDuplicate)}
                                            """.trimIndent()))
        }
        fun getEpisodeByIdentifyingValue(item: Episode): Episode? = map[item.identifyingValue]
        fun guessDuplicate(item: Episode): Episode? {
            var episode = map[item.identifier]
            if (episode != null) return episode
            val url = item.downloadUrl
            if (!url.isNullOrEmpty()) {
                episode = map[url]
                if (episode != null) return episode
            }
            val title = canonicalizeTitle(item.title)
            if (title.isNotEmpty()) {
                episode = map[title]
                if (episode != null) {
                    if (datesLookSimilar(episode, item) && durationsLookSimilar(episode, item) && mimeTypeLooksSimilar(episode, item)) return episode
                }
            }
            return null
        }
        fun clear() = map.clear()
    }
    private object EpisodeAssistant {
        fun searchEpisodeByIdentifyingValue(episodes: List<Episode>?, searchItem: Episode): Episode? {
            if (episodes.isNullOrEmpty()) return null
            for (episode in episodes) if (episode.identifyingValue == searchItem.identifyingValue) return episode
            return null
        }
        /**
         * Guess if one of the episodes could actually mean the searched episode, even if it uses another identifying value.
         * This is to work around podcasters breaking their GUIDs.
         */
//        fun searchEpisodeGuessDuplicate(episodes: List<Episode>?, searchItem: Episode): Episode? {
//            if (episodes.isNullOrEmpty()) return null
//            for (episode in episodes) {
//                if (EpisodeDuplicateGuesser.sameAndNotEmpty(episode.identifier, searchItem.identifier)) return episode
//            }
//            for (episode in episodes) {
//                if (EpisodeDuplicateGuesser.seemDuplicates(episode, searchItem)) return episode
//            }
//            return null
//        }
        fun duplicateEpisodeDetails(episode: Episode): String {
            return ("""
                Title: ${episode.title}
                ID: ${episode.identifier}
                """.trimIndent() + """
                 
                URL: ${episode.downloadUrl}
                """.trimIndent())
        }
    }

    /**
     * Publishers sometimes mess up their feed by adding episodes twice or by changing the ID of existing episodes.
     * This class tries to guess if publishers actually meant another episode,
     * even if their feed explicitly says that the episodes are different.
     */
    object EpisodeDuplicateGuesser {
        private fun sameAndNotEmpty(string1: String?, string2: String?): Boolean {
            if (string1.isNullOrEmpty() || string2.isNullOrEmpty()) return false
            return string1 == string2
        }
        internal fun datesLookSimilar(item1: Episode, item2: Episode): Boolean {
//            if (item1.getPubDate() == null || item2.getPubDate() == null) return false
            val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.US) // MM/DD/YY
            val dateOriginal = dateFormat.format(item2.getPubDate())
            val dateNew = dateFormat.format(item1.getPubDate())
            return dateOriginal == dateNew // Same date; time is ignored.
        }
        internal fun durationsLookSimilar(media1: Episode, media2: Episode): Boolean {
            return abs((media1.duration - media2.duration).toDouble()) < 10 * 60L * 1000L
        }
        internal fun mimeTypeLooksSimilar(media1: Episode, media2: Episode): Boolean {
            var mimeType1 = media1.mimeType
            var mimeType2 = media2.mimeType
            if (mimeType1 == null || mimeType2 == null) return true
            if (mimeType1.contains("/") && mimeType2.contains("/")) {
                mimeType1 = mimeType1.substring(0, mimeType1.indexOf("/"))
                mimeType2 = mimeType2.substring(0, mimeType2.indexOf("/"))
            }
            return (mimeType1 == mimeType2)
        }
        private fun titlesLookSimilar(item1: Episode, item2: Episode): Boolean {
            return sameAndNotEmpty(canonicalizeTitle(item1.title), canonicalizeTitle(item2.title))
        }
        internal fun canonicalizeTitle(title: String?): String {
            if (title == null) return ""
            return title.trim { it <= ' ' }.replace('“', '"').replace('”', '"').replace('„', '"').replace('—', '-')
        }
    }
}