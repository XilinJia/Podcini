package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.feed.LocalFeedUpdater.updateFeed
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.InTheatre.curState
import ac.mdiq.podcini.playback.base.InTheatre.writeNoMediaPlaying
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.ACTION_SHUTDOWN_PLAYBACK_SERVICE
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.storage.database.Queues.removeFromAllQueuesSync
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.storage.model.EpisodeSortOrder.Companion.getPermutor
import ac.mdiq.podcini.storage.model.PlayState
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.IntentUtils.sendLocalBroadcast
import ac.mdiq.podcini.util.Logd
import ac.mdiq.vista.extractor.stream.StreamInfo
import ac.mdiq.vista.extractor.stream.StreamInfoItem
import android.app.backup.BackupManager
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.realm.kotlin.ext.isManaged
import kotlinx.coroutines.Job
import java.io.File
import java.util.*
import kotlin.math.min

object Episodes {
    private val TAG: String = Episodes::class.simpleName ?: "Anonymous"

    private const val smartMarkAsPlayedPercent: Int = 95

    val prefRemoveFromQueueMarkedPlayed by lazy { getPref(AppPrefs.prefRemoveFromQueueMarkedPlayed, true) }
    val prefDeleteRemovesFromQueue by lazy { getPref(AppPrefs.prefDeleteRemovesFromQueue, false) }

    /**
     * @param offset The first episode that should be loaded.
     * @param limit The maximum number of episodes that should be loaded.
     * @param filter The filter describing which episodes to filter out.
     * TODO: filters of queued and notqueued don't work in this
     */
    fun getEpisodes(offset: Int, limit: Int, filter: EpisodeFilter?, sortOrder: EpisodeSortOrder?, copy: Boolean = true): List<Episode> {
        Logd(TAG, "getEpisodes called with: offset=$offset, limit=$limit")
        val queryString = filter?.queryString()?:"id > 0"
        var episodes = realm.query(Episode::class).query(queryString).find().toMutableList()
        if (sortOrder != null) getPermutor(sortOrder).reorder(episodes)
        val size = episodes.size
        if (offset < size && offset + limit < size) episodes = episodes.subList(offset, min(size, offset + limit))
        return if (copy) realm.copyFromRealm(episodes) else episodes
    }

    fun getEpisodesCount(filter: EpisodeFilter?, feedId: Long = -1): Int {
        Logd(TAG, "getEpisodesCount called")
        var queryString = filter?.queryString()?:"id > 0"
        if (feedId >= 0) queryString += " AND feedId == $feedId "
        return realm.query(Episode::class).query(queryString).count().find().toInt()
    }

    fun getEpisodes(filter: EpisodeFilter?, feedId: Long = -1, limit: Int): List<Episode> {
        Logd(TAG, "getEpisodesCount called")
        var queryString = filter?.queryString()?:"id > 0"
        if (feedId >= 0) queryString += " AND feedId == $feedId "
        queryString += " AND SORT(pubDate ASC) LIMIT($limit) "
        return realm.query(Episode::class).query(queryString).find()
    }

    /**
     * Loads a specific FeedItem from the database.
     * @param guid feed episode guid
     * @param episodeUrl the feed episode's url
     * @return The FeedItem or null if the FeedItem could not be found.
     * Does NOT load additional attributes like feed or queue state.
     */
    fun getEpisodeByGuidOrUrl(guid: String?, episodeUrl: String, copy: Boolean = true): Episode? {
        Logd(TAG, "getEpisodeByGuidOrUrl called $guid $episodeUrl")
        val episode = if (guid != null) realm.query(Episode::class).query("identifier == $0", guid).first().find()
        else realm.query(Episode::class).query("downloadUrl == $0", episodeUrl).first().find()
        if (!copy) return episode
        return if (episode != null) realm.copyFromRealm(episode) else null
    }

    fun getEpisodeMedia(mediaId: Long, copy: Boolean = true): Episode? {
        Logd(TAG, "getEpisodeMedia called $mediaId")
        val media = realm.query(Episode::class).query("id == $0", mediaId).first().find()
        if (!copy) return media
        return if (media != null) realm.copyFromRealm(media) else null
    }

    fun deleteEpisodesWarnLocal(context: Context, items: Iterable<Episode>) {
        val localItems: MutableList<Episode> = mutableListOf()
        for (item in items) {
            if (item.feed?.isLocalFeed == true) localItems.add(item)
            else deleteEpisodeMedia(context, item)
        }

        if (localItems.isNotEmpty()) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.delete_episode_label)
                .setMessage(R.string.delete_local_feed_warning_body)
                .setPositiveButton(R.string.delete_label) { dialog: DialogInterface?, which: Int -> for (item in localItems) deleteEpisodeMedia(context, item) }
                .setNegativeButton(R.string.cancel_label, null)
                .show()
        }
    }

    // @JvmStatic is needed because some Runnable blocks call this
    @JvmStatic
    fun deleteEpisodeMedia(context: Context, episode: Episode) : Job {
        Logd(TAG, "deleteMediaOfEpisode called ${episode.title}")
        return runOnIOScope {
            val episode_ = deleteMediaSync(context, episode)
            if (prefDeleteRemovesFromQueue) removeFromAllQueuesSync(episode_)
        }
    }

    fun deleteMediaSync(context: Context, episode: Episode): Episode {
        Logd(TAG, "deleteMediaSync called")
        Logd(TAG, String.format(Locale.US, "Requested to delete EpisodeMedia [id=%d, title=%s, downloaded=%s", episode.id, episode.getEpisodeTitle(), episode.downloaded))
        var localDelete = false
        val url = episode.fileUrl
        var episode = episode
        when {
            url != null && url.startsWith("content://") -> {
                // Local feed
                val documentFile = DocumentFile.fromSingleUri(context, Uri.parse(episode.fileUrl))
                if (documentFile == null || !documentFile.exists() || !documentFile.delete()) {
                    EventFlow.postEvent(FlowEvent.MessageEvent(getAppContext().getString(R.string.delete_local_failed)))
                    return episode
                }
                episode = upsertBlk(episode) {
                    it.setfileUrlOrNull(null)
                    if (it.playState < PlayState.SKIPPED.code) it.playState = PlayState.SKIPPED.code
                }
                EventFlow.postEvent(FlowEvent.EpisodePlayedEvent(episode))
                localDelete = true
            }
            url != null -> {
                // delete downloaded media file
                val mediaFile = File(url)
                if (!mediaFile.delete()) {
                    Log.e(TAG, "delete media file failed: $url")
                    val evt = FlowEvent.MessageEvent(getAppContext().getString(R.string.delete_failed_simple) + ": $url")
                    EventFlow.postEvent(evt)
                    return episode
                }
                episode = upsertBlk(episode) {
                    it.downloaded = false
                    it.setfileUrlOrNull(null)
                    it.hasEmbeddedPicture = false
                    if (it.playState < PlayState.SKIPPED.code) it.playState = PlayState.SKIPPED.code
                }
                EventFlow.postEvent(FlowEvent.EpisodePlayedEvent(episode))
            }
        }

        if (episode.id == curState.curMediaId) {
            writeNoMediaPlaying()
            sendLocalBroadcast(context, ACTION_SHUTDOWN_PLAYBACK_SERVICE)
            val nm = NotificationManagerCompat.from(context)
            nm.cancel(R.id.notification_playing)
        }

        // Do full update of this feed to get rid of the episode
        if (localDelete) {
            if (episode.feed != null) updateFeed(episode.feed!!, context.applicationContext, null)
        } else {
            if (isProviderConnected) {
                // Gpodder: queue delete action for synchronization
                val action = EpisodeAction.Builder(episode, EpisodeAction.DELETE).currentTimestamp().build()
                SynchronizationQueueSink.enqueueEpisodeActionIfSyncActive(context, action)
            }
            EventFlow.postEvent(FlowEvent.EpisodeMediaEvent.removed(episode))
        }
        return episode
    }

    /**
     * This is used when the episodes are not listed with the feed.
     * Remove the listed episodes and their EpisodeMedia entries.
     * Deleting media also removes the download log entries.
     */
    fun deleteEpisodes(context: Context, episodes: List<Episode>) : Job {
        return runOnIOScope {
            val removedFromQueue: MutableList<Episode> = mutableListOf()
            val queueItems = curQueue.episodes.toMutableList()
            for (episode in episodes) {
                if (queueItems.remove(episode)) removedFromQueue.add(episode)
                if (episode.id == curState.curMediaId) {
                    // Applies to both downloaded and streamed media
                    writeNoMediaPlaying()
                    sendLocalBroadcast(context, ACTION_SHUTDOWN_PLAYBACK_SERVICE)
                }
                if (episode.feed != null && !episode.feed!!.isLocalFeed) {
                    DownloadServiceInterface.get()?.cancel(context, episode)
                    if (episode.downloaded) deleteMediaSync(context, episode)
                }
            }
            if (removedFromQueue.isNotEmpty()) removeFromAllQueuesSync(*removedFromQueue.toTypedArray())
            for (episode in removedFromQueue) EventFlow.postEvent(FlowEvent.QueueEvent.irreversibleRemoved(episode))

            // we assume we also removed download log entries for the feed or its media files.
            // especially important if download or refresh failed, as the user should not be able
            // to retry these
            EventFlow.postEvent(FlowEvent.DownloadLogEvent())
            val backupManager = BackupManager(context)
            backupManager.dataChanged()
        }
    }

    fun setRating(episode: Episode, rating: Int) : Job {
        Logd(TAG, "setRating called $rating")
        return runOnIOScope {
            val result = upsert(episode) { it.rating = rating }
            EventFlow.postEvent(FlowEvent.RatingEvent(result, result.rating))
        }
    }

    /**
     * Sets the 'read'-attribute of all specified FeedItems
     * @param played  New value of the 'read'-attribute, one of Episode.PLAYED, Episode.NEW, Episode.UNPLAYED
     * @param episodes   the FeedItems.
     * @param resetMediaPosition true if this method should also reset the position of the Episode's EpisodeMedia object.
     */
    fun setPlayState(played: Int, resetMediaPosition: Boolean, vararg episodes: Episode) : Job {
        Logd(TAG, "setPlayState called")
        return runOnIOScope { for (episode in episodes) setPlayStateSync(played, episode, resetMediaPosition) }
    }

    suspend fun setPlayStateSync(played: Int, episode: Episode, resetMediaPosition: Boolean, removeFromQueue: Boolean = true) : Episode {
        Logd(TAG, "setPlayStateSync called played: $played resetMediaPosition: $resetMediaPosition ${episode.title}")
        var episode_ = episode
        if (!episode.isManaged()) episode_ = realm.query(Episode::class).query("id == $0", episode.id).first().find() ?: episode
        val result = upsert(episode_) {
            if (played != PlayState.UNSPECIFIED.code) it.playState = played
            else {
                if (it.playState == PlayState.PLAYED.code) it.playState = PlayState.UNPLAYED.code
                else it.playState = PlayState.PLAYED.code
            }
            if (resetMediaPosition || it.playState == PlayState.PLAYED.code || it.playState == PlayState.IGNORED.code) it.setPosition(0)
        }
        Logd(TAG, "setPlayStateSync played0: ${result.playState}")
        if (removeFromQueue && played == PlayState.PLAYED.code && prefRemoveFromQueueMarkedPlayed) removeFromAllQueuesSync(result)
        Logd(TAG, "setPlayStateSync played1: ${result.playState}")
        EventFlow.postEvent(FlowEvent.EpisodePlayedEvent(result))
        return result
    }

    fun episodeFromStreamInfoItem(item: StreamInfoItem): Episode {
        val e = Episode()
        e.link = item.url
        e.title = item.name
        e.description = "Short: ${item.shortDescription}"
        e.imageUrl = item.thumbnails.first().url
        e.setPubDate(item.uploadDate?.date()?.time)
        e.viewCount = item.viewCount.toInt()
        e.fillMedia(item.url, 0, "video/*")
        if (item.duration > 0) e.duration = item.duration.toInt() * 1000
        e.fileUrl = e.getMediafilename()
        return e
    }

    fun episodeFromStreamInfo(info: StreamInfo): Episode {
        val e = Episode()
        e.link = info.url
        e.title = info.name
        e.description = info.description?.content
        e.imageUrl = info.thumbnails.first().url
        e.setPubDate(info.uploadDate?.date()?.time)
        e.viewCount = info.viewCount.toInt()
        e.fillMedia(info.url, 0, "video/*")
        if (info.duration > 0) e.duration = info.duration.toInt() * 1000
        e.fileUrl = e.getMediafilename()
        return e
    }

    @JvmStatic
    fun indexOfItemWithId(episodes: List<Episode?>, id: Long): Int {
        for (i in episodes.indices) {
            val episode = episodes[i]
            if (episode?.id == id) return i
        }
        return -1
    }

    @JvmStatic
    fun episodeListContains(episodes: List<Episode?>, itemId: Long): Boolean {
        return indexOfItemWithId(episodes, itemId) >= 0
    }

    @JvmStatic
    fun indexOfItemWithDownloadUrl(items: List<Episode?>, downloadUrl: String): Int {
        for (i in items.indices) {
            val item = items[i]
            if (item?.downloadUrl == downloadUrl) return i
        }
        return -1
    }

    @JvmStatic
    fun hasAlmostEnded(media: Episode): Boolean {
        return media.duration > 0 && media.position >= media.duration * smartMarkAsPlayedPercent * 0.01
    }
}