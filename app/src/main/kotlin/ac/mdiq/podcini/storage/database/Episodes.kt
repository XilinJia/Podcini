package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.net.feed.LocalFeedUpdater.updateFeed
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.net.utils.NetworkUtils.isAutoDownloadAllowed
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.InTheatre.curState
import ac.mdiq.podcini.playback.base.InTheatre.isCurMedia
import ac.mdiq.podcini.playback.base.InTheatre.writeNoMediaPlaying
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.ACTION_SHUTDOWN_PLAYBACK_SERVICE
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.episodeCacheSize
import ac.mdiq.podcini.preferences.UserPreferences.isEnableAutodownload
import ac.mdiq.podcini.preferences.UserPreferences.isEnableAutodownloadOnBattery
import ac.mdiq.podcini.preferences.UserPreferences.shouldDeleteRemoveFromQueue
import ac.mdiq.podcini.storage.algorithms.EpisodeCleanupAlgorithmFactory
import ac.mdiq.podcini.storage.database.Queues.removeFromAllQueues
import ac.mdiq.podcini.storage.database.Queues.removeFromQueueSync
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.utils.EpisodeFilter
import ac.mdiq.podcini.storage.utils.SortOrder
import ac.mdiq.podcini.util.IntentUtils.sendLocalBroadcast
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.PowerUtils.deviceCharging
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import ac.mdiq.podcini.util.sorting.EpisodesPermutors.getPermutor
import android.app.backup.BackupManager
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Job
import java.io.File
import java.text.DateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.abs
import kotlin.math.min

object Episodes {
    private val TAG: String = Episodes::class.simpleName ?: "Anonymous"

    /**
     * Executor service used by the autodownloadUndownloadedEpisodes method.
     */
    private val autodownloadExec: ExecutorService = Executors.newSingleThreadExecutor { r: Runnable? ->
        val t = Thread(r)
        t.priority = Thread.MIN_PRIORITY
        t
    }

    var downloadAlgorithm = AutomaticDownloadAlgorithm()

    /**
     * @param offset The first episode that should be loaded.
     * @param limit The maximum number of episodes that should be loaded.
     * @param filter The filter describing which episodes to filter out.
     * TODO: filters of queued and notqueued don't work in this
     */
    fun getEpisodes(offset: Int, limit: Int, filter: EpisodeFilter?, sortOrder: SortOrder?, copy: Boolean = true): List<Episode> {
        Logd(TAG, "getEpisodes called with: offset=$offset, limit=$limit")
        val queryString = filter?.queryString()?:"id > 0"
        var episodes = realm.query(Episode::class).query(queryString).find().toMutableList()
        if (sortOrder != null) getPermutor(sortOrder).reorder(episodes)
        if (offset < episodes.size) episodes = episodes.subList(offset, min(episodes.size, offset + limit))
        return if (copy) realm.copyFromRealm(episodes) else episodes
    }

    fun getEpisodesCount(filter: EpisodeFilter?): Int {
        Logd(TAG, "getEpisodesCount called")
        val queryString = filter?.queryString()?:"id > 0"
        return realm.query(Episode::class).query(queryString).count().find().toInt()
    }

//    used in tests only
    fun getEpisode(itemId: Long): Episode? {
        Logd(TAG, "getFeedItem called with id $itemId")
        val it = realm.query(Episode::class).query("id == $0", itemId).first().find()
        return if (it != null) realm.copyFromRealm(it) else null
    }

    /**
     * Loads a specific FeedItem from the database.
     * @param guid feed episode guid
     * @param episodeUrl the feed episode's url
     * @return The FeedItem or null if the FeedItem could not be found.
     * Does NOT load additional attributes like feed or queue state.
     */
    fun getEpisodeByGuidOrUrl(guid: String?, episodeUrl: String): Episode? {
        Logd(TAG, "getEpisodeByGuidOrUrl called $guid $episodeUrl")
        val episode = if (guid != null) realm.query(Episode::class).query("identifier == $0", guid).first().find()
        else realm.query(Episode::class).query("media.downloadUrl == $0", episodeUrl).first().find()
        return if (episode != null) realm.copyFromRealm(episode) else null
    }

    fun getEpisodeMedia(mediaId: Long): EpisodeMedia? {
        Logd(TAG, "getEpisodeMedia called $mediaId")
        val media = realm.query(EpisodeMedia::class).query("id == $0", mediaId).first().find()
        return if (media != null) realm.copyFromRealm(media) else null
    }

    /**
     * Looks for non-downloaded episodes in the queue or list of unread episodes and request a download if
     * 1. Network is available
     * 2. The device is charging or the user allows auto download on battery
     * 3. There is free space in the episode cache
     * This method is executed on an internal single thread executor.
     * @param context  Used for accessing the DB.
     * @return A Future that can be used for waiting for the methods completion.
     */
    @UnstableApi
    fun autodownloadEpisodeMedia(context: Context): Future<*> {
        Logd(TAG, "autodownloadEpisodeMedia")
        return autodownloadExec.submit(downloadAlgorithm.autoDownloadEpisodeMedia(context))
    }

    /**
     * Removed downloaded episodes outside of the queue if the episode cache is full. Episodes with a smaller
     * 'playbackCompletionDate'-value will be deleted first.
     * This method should NOT be executed on the GUI thread.
     * @param context Used for accessing the DB.
     */
    fun performAutoCleanup(context: Context) {
        EpisodeCleanupAlgorithmFactory.build().performCleanup(context)
    }

    /**
     * Implements the automatic download algorithm used by Podcini. This class assumes that
     * the client uses the [EpisodeCleanupAlgorithm].
     */
    open class AutomaticDownloadAlgorithm {
        /**
         * Looks for undownloaded episodes in the queue or list of new items and request a download if
         * 1. Network is available
         * 2. The device is charging or the user allows auto download on battery
         * 3. There is free space in the episode cache
         * This method is executed on an internal single thread executor.
         * @param context  Used for accessing the DB.
         * @return A Runnable that will be submitted to an ExecutorService.
         */
        @UnstableApi open fun autoDownloadEpisodeMedia(context: Context): Runnable? {
            return Runnable {
                // true if we should auto download based on network status
//            val networkShouldAutoDl = (isAutoDownloadAllowed)
                val networkShouldAutoDl = (isAutoDownloadAllowed && isEnableAutodownload)

                // true if we should auto download based on power status
                val powerShouldAutoDl = (deviceCharging(context) || isEnableAutodownloadOnBattery)
                Logd(TAG, "prepare autoDownloadUndownloadedItems $networkShouldAutoDl $powerShouldAutoDl")

                // we should only auto download if both network AND power are happy
                if (networkShouldAutoDl && powerShouldAutoDl) {
                    Logd(TAG, "Performing auto-dl of undownloaded episodes")

                    val candidates: MutableList<Episode>
                    val queue = curQueue.episodes

                    val newItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.NEW), SortOrder.DATE_NEW_OLD)
                    Logd(TAG, "newItems: ${newItems.size}")
                    candidates = ArrayList(queue.size + newItems.size)
                    candidates.addAll(queue)
                    for (newItem in newItems) {
                        val feedPrefs = newItem.feed!!.preferences
                        if (feedPrefs!!.autoDownload && !candidates.contains(newItem) && feedPrefs.filter.shouldAutoDownload(newItem)) candidates.add(newItem)
                    }

                    // filter items that are not auto downloadable
                    val it = candidates.iterator()
                    while (it.hasNext()) {
                        val item = it.next()
                        if (!item.isAutoDownloadEnabled || item.isDownloaded || item.media == null || isCurMedia(item.media) || item.feed?.isLocalFeed == true)
                            it.remove()
                    }

                    val autoDownloadableEpisodes = candidates.size
                    val downloadedEpisodes = getEpisodesCount(EpisodeFilter(EpisodeFilter.DOWNLOADED))
                    val deletedEpisodes = EpisodeCleanupAlgorithmFactory.build().makeRoomForEpisodes(context, autoDownloadableEpisodes)
                    val cacheIsUnlimited = episodeCacheSize == UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED
                    val episodeCacheSize = episodeCacheSize
                    val episodeSpaceLeft = if (cacheIsUnlimited || episodeCacheSize >= downloadedEpisodes + autoDownloadableEpisodes) autoDownloadableEpisodes
                    else episodeCacheSize - (downloadedEpisodes - deletedEpisodes)

                    val itemsToDownload: List<Episode> = candidates.subList(0, episodeSpaceLeft)
                    if (itemsToDownload.isNotEmpty()) {
                        Logd(TAG, "Enqueueing " + itemsToDownload.size + " items for download")
                        for (episode in itemsToDownload) DownloadServiceInterface.get()?.download(context, episode)
                    }
                }
                else Logd(TAG, "not auto downloaded networkShouldAutoDl: $networkShouldAutoDl powerShouldAutoDl $powerShouldAutoDl")
            }
        }
        companion object {
            private val TAG: String = AutomaticDownloadAlgorithm::class.simpleName ?: "Anonymous"
        }
    }

// @JvmStatic is needed because some Runnable blocks call this
    @OptIn(UnstableApi::class) @JvmStatic
    fun deleteMediaOfEpisode(context: Context, episode: Episode) : Job {
        Logd(TAG, "deleteMediaOfEpisode called ${episode.title}")
        return runOnIOScope {
            val media = episode.media ?: return@runOnIOScope
            val result = deleteMediaSync(context, episode)
            if (media.downloadUrl.isNullOrEmpty()) {
                episode.media = null
                upsert(episode) {}
                EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(episode))
            }
            if (result && shouldDeleteRemoveFromQueue()) removeFromQueueSync(null, null, episode)
        }
    }

    @OptIn(UnstableApi::class)
    private fun deleteMediaSync(context: Context, episode: Episode): Boolean {
        Logd(TAG, "deleteMediaSync called")
        val media = episode.media ?: return false
        Log.i(TAG, String.format(Locale.US, "Requested to delete EpisodeMedia [id=%d, title=%s, downloaded=%s", media.id, media.getEpisodeTitle(), media.downloaded))
        var localDelete = false
        val url = media.fileUrl
        when {
            url != null && url.startsWith("content://") -> {
                // Local feed
                val documentFile = DocumentFile.fromSingleUri(context, Uri.parse(media.fileUrl))
                if (documentFile == null || !documentFile.exists() || !documentFile.delete()) {
                    EventFlow.postEvent(FlowEvent.MessageEvent(context.getString(R.string.delete_local_failed)))
                    return false
                }
                episode.media?.fileUrl = null
                upsertBlk(episode) {}
                localDelete = true
            }
            url != null -> {
                // delete downloaded media file
                val mediaFile = File(url)
                if (mediaFile.exists() && !mediaFile.delete()) {
                    Log.e(TAG, "delete media file failed: $url")
                    val evt = FlowEvent.MessageEvent(context.getString(R.string.delete_failed))
                    EventFlow.postEvent(evt)
                    return false
                }
                episode.media?.downloaded = false
                episode.media?.fileUrl = null
                episode.media?.hasEmbeddedPicture = false
                upsertBlk(episode) {}
            }
        }

        if (media.id == curState.curMediaId) {
            writeNoMediaPlaying()
            sendLocalBroadcast(context, ACTION_SHUTDOWN_PLAYBACK_SERVICE)

            val nm = NotificationManagerCompat.from(context)
            nm.cancel(R.id.notification_playing)
        }

        if (localDelete) {
            // Do full update of this feed to get rid of the episode
            if (episode.feed != null) updateFeed(episode.feed!!, context.applicationContext, null)
        } else {
            // Gpodder: queue delete action for synchronization
            val action = EpisodeAction.Builder(episode, EpisodeAction.DELETE).currentTimestamp().build()
            SynchronizationQueueSink.enqueueEpisodeActionIfSyncActive(context, action)
            EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(episode))
        }
        return true
    }

    /**
     * Remove the listed episodes and their EpisodeMedia entries.
     * Deleting media also removes the download log entries.
     */
    fun deleteEpisodes(context: Context, episodes: List<Episode>) : Job {
        return runOnIOScope {
            deleteEpisodesSync(context, episodes)
        }
    }

    /**
     * Remove the listed episodes and their EpisodeMedia entries.
     * Deleting media also removes the download log entries.
     */
    @OptIn(UnstableApi::class)
    internal fun deleteEpisodesSync(context: Context, episodes: List<Episode>) {
        Logd(TAG, "deleteEpisodesSync called")
        val removedFromQueue: MutableList<Episode> = ArrayList()
        val queueItems = curQueue.episodes.toMutableList()
        for (episode in episodes) {
            if (queueItems.remove(episode)) removedFromQueue.add(episode)
            if (episode.media != null) {
                if (episode.media?.id == curState.curMediaId) {
                    // Applies to both downloaded and streamed media
                    writeNoMediaPlaying()
                    sendLocalBroadcast(context, ACTION_SHUTDOWN_PLAYBACK_SERVICE)
                }
                if (episode.feed != null && !episode.feed!!.isLocalFeed) {
                    DownloadServiceInterface.get()?.cancel(context, episode.media!!)
                    if (episode.media!!.downloaded) deleteMediaSync(context, episode)
                }
            }
        }
        if (removedFromQueue.isNotEmpty()) {
            curQueue.episodes.clear()
            curQueue.episodes.addAll(queueItems)
//            upsertBlk(curQueue) {}
        }
//        TODO: need to update download logs?
//        val adapter = getInstance()
//        adapter.open()
//        if (removedFromQueue.isNotEmpty()) adapter.setQueue(queueItems)
//        adapter.removeFeedItems(episodes)
//        adapter.close()

        for (episode in removedFromQueue) EventFlow.postEvent(FlowEvent.QueueEvent.irreversibleRemoved(episode))

        // we assume we also removed download log entries for the feed or its media files.
        // especially important if download or refresh failed, as the user should not be able
        // to retry these
        EventFlow.postEvent(FlowEvent.DownloadLogEvent())

        val backupManager = BackupManager(context)
        backupManager.dataChanged()
    }

    fun persistEpisodes(episodes: List<Episode>) : Job {
        Logd(TAG, "persistEpisodes called")
        return runOnIOScope {
            for (episode in episodes) {
                Logd(TAG, "persistEpisodes: ${episode.playState} ${episode.title}")
                upsert(episode) {}
            }
            EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(episodes))
        }
    }

    fun persistEpisodeMedia(media: EpisodeMedia) : Job {
        Logd(TAG, "persistEpisodeMedia called")
        return runOnIOScope {
            val episode = media.episode
            if (episode != null) {
                episode.media = media
                upsert(episode) {}
                EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(episode))
            } else Log.e(TAG, "persistEpisodeMedia media.episode is null")
        }
    }

    fun persistEpisode(episode: Episode?, isPositionChange: Boolean = false) : Job {
        Logd(TAG, "persistEpisode called")
        return runOnIOScope {
            if (episode != null) {
                upsert(episode) {}
                if (!isPositionChange) EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(episode))
            }
        }
    }

    /**
     * Adds a Episode object to the playback history. A Episode object is in the playback history if
     * its playback completion date is set to a non-null value. This method will set the playback completion date to the
     * current date regardless of the current value.
     *
     * @param episode Episode that should be added to the playback history.
     * @param date PlaybackCompletionDate for `media`
     */
    fun addToHistory(episode: Episode, date: Date? = Date()) : Job {
        Logd(TAG, "addToHistory called")
        return runOnIOScope {
            episode.media?.playbackCompletionDate = date
            upsert(episode) {}
            EventFlow.postEvent(FlowEvent.HistoryEvent())
        }
    }

    @JvmStatic
    fun setFavorite(episode: Episode, stat: Boolean) : Job {
        Logd(TAG, "setFavorite called")
        return runOnIOScope {
            episode.isFavorite = stat
            upsert(episode) {}
            EventFlow.postEvent(FlowEvent.FavoritesEvent(episode))
        }
    }

    /**
     * Sets the 'read'-attribute of all specified FeedItems
     * @param played  New value of the 'read'-attribute, one of Episode.PLAYED, Episode.NEW, Episode.UNPLAYED
     * @param episodes   the FeedItems.
     * @param resetMediaPosition true if this method should also reset the position of the Episode's EpisodeMedia object.
     */
    @OptIn(UnstableApi::class)
    fun markPlayed(played: Int, resetMediaPosition: Boolean, vararg episodes: Episode) : Job {
        Logd(TAG, "markPlayed called")
        return runOnIOScope {
            for (episode in episodes) {
                episode.playState = played
                if (resetMediaPosition) episode.media?.setPosition(0)
                upsert(episode) {}
                if (played == Episode.PLAYED) removeFromAllQueues(episode)
                EventFlow.postEvent(FlowEvent.EpisodePlayedEvent(episode))
            }
        }
    }

    /**
     * Publishers sometimes mess up their feed by adding episodes twice or by changing the ID of existing episodes.
     * This class tries to guess if publishers actually meant another episode,
     * even if their feed explicitly says that the episodes are different.
     */
    object EpisodeDuplicateGuesser {
        fun seemDuplicates(item1: Episode, item2: Episode): Boolean {
            if (sameAndNotEmpty(item1.identifier, item2.identifier)) return true

            val media1 = item1.media
            val media2 = item2.media
            if (media1 == null || media2 == null) return false

            if (sameAndNotEmpty(media1.getStreamUrl(), media2.getStreamUrl())) return true

            return (titlesLookSimilar(item1, item2) && datesLookSimilar(item1, item2) && durationsLookSimilar(media1, media2) && mimeTypeLooksSimilar(media1, media2))
        }

        fun sameAndNotEmpty(string1: String?, string2: String?): Boolean {
            if (string1.isNullOrEmpty() || string2.isNullOrEmpty()) return false
            return string1 == string2
        }

        private fun datesLookSimilar(item1: Episode, item2: Episode): Boolean {
            if (item1.getPubDate() == null || item2.getPubDate() == null) return false

            val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.US) // MM/DD/YY
            val dateOriginal = dateFormat.format(item2.getPubDate()!!)
            val dateNew = dateFormat.format(item1.getPubDate()!!)
            return dateOriginal == dateNew // Same date; time is ignored.
        }

        private fun durationsLookSimilar(media1: EpisodeMedia, media2: EpisodeMedia): Boolean {
            return abs((media1.getDuration() - media2.getDuration()).toDouble()) < 10 * 60L * 1000L
        }

        private fun mimeTypeLooksSimilar(media1: EpisodeMedia, media2: EpisodeMedia): Boolean {
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

        private fun canonicalizeTitle(title: String?): String {
            if (title == null) return ""
            return title
                .trim { it <= ' ' }
                .replace('“', '"')
                .replace('”', '"')
                .replace('„', '"')
                .replace('—', '-')
        }
    }
}