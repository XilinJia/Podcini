package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.net.feed.LocalFeedUpdater.updateFeed
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.InTheatre.curState
import ac.mdiq.podcini.playback.base.InTheatre.writeNoMediaPlaying
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.ACTION_SHUTDOWN_PLAYBACK_SERVICE
import ac.mdiq.podcini.preferences.UserPreferences.shouldDeleteRemoveFromQueue
import ac.mdiq.podcini.preferences.UserPreferences.shouldRemoveFromQueuesMarkPlayed
import ac.mdiq.podcini.storage.database.Queues.removeFromAllQueuesSync
import ac.mdiq.podcini.storage.database.Queues.removeFromQueueSync
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.SortOrder
import ac.mdiq.podcini.util.IntentUtils.sendLocalBroadcast
import ac.mdiq.podcini.util.Logd
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
import java.util.*
import kotlin.math.min

object Episodes {
    private val TAG: String = Episodes::class.simpleName ?: "Anonymous"

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

// @JvmStatic is needed because some Runnable blocks call this
    @OptIn(UnstableApi::class) @JvmStatic
    fun deleteMediaOfEpisode(context: Context, episode: Episode) : Job {
        Logd(TAG, "deleteMediaOfEpisode called ${episode.title}")
        return runOnIOScope {
            if (episode.media == null) return@runOnIOScope
            val episode_ = deleteMediaSync(context, episode)
            if (shouldDeleteRemoveFromQueue()) removeFromQueueSync(null, null, episode_)
        }
    }

    @OptIn(UnstableApi::class)
    fun deleteMediaSync(context: Context, episode: Episode): Episode {
        Logd(TAG, "deleteMediaSync called")
        val media = episode.media ?: return episode
        Logd(TAG, String.format(Locale.US, "Requested to delete EpisodeMedia [id=%d, title=%s, downloaded=%s", media.id, media.getEpisodeTitle(), media.downloaded))
        var localDelete = false
        val url = media.fileUrl
        when {
            url != null && url.startsWith("content://") -> {
                // Local feed
                val documentFile = DocumentFile.fromSingleUri(context, Uri.parse(media.fileUrl))
                if (documentFile == null || !documentFile.exists() || !documentFile.delete()) {
                    EventFlow.postEvent(FlowEvent.MessageEvent(context.getString(R.string.delete_local_failed)))
                    return episode
                }
                upsertBlk(episode) {
                    it.media?.fileUrl = null
                    if (media.downloadUrl.isNullOrEmpty()) it.media = null
                }
                localDelete = true
            }
            url != null -> {
                // delete downloaded media file
                val mediaFile = File(url)
                if (mediaFile.exists() && !mediaFile.delete()) {
                    Log.e(TAG, "delete media file failed: $url")
                    val evt = FlowEvent.MessageEvent(context.getString(R.string.delete_failed))
                    EventFlow.postEvent(evt)
                    return episode
                }
                upsertBlk(episode) {
                    it.media?.downloaded = false
                    it.media?.fileUrl = null
                    it.media?.hasEmbeddedPicture = false
                    if (media.downloadUrl.isNullOrEmpty()) it.media = null
                }
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
        return episode
    }

    /**
     * Remove the listed episodes and their EpisodeMedia entries.
     * Deleting media also removes the download log entries.
     */
    fun deleteEpisodes(context: Context, episodes: List<Episode>) : Job {
        return runOnIOScope {
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
     * @param episode Episode that should be added to the playback history.
     * @param date PlaybackCompletionDate for `media`
     */
    fun addToHistory(episode: Episode, date: Date? = Date()) : Job {
        Logd(TAG, "addToHistory called")
        return runOnIOScope {
            upsert(episode) {
                it.media?.playbackCompletionDate = date
            }
            EventFlow.postEvent(FlowEvent.HistoryEvent())
        }
    }

    @JvmStatic
    fun setFavorite(episode: Episode, stat: Boolean) : Job {
        Logd(TAG, "setFavorite called $stat")
        return runOnIOScope {
            val result = upsert(episode) {
                it.isFavorite = stat
            }
            EventFlow.postEvent(FlowEvent.FavoritesEvent(result))
        }
    }

    /**
     * Sets the 'read'-attribute of all specified FeedItems
     * @param played  New value of the 'read'-attribute, one of Episode.PLAYED, Episode.NEW, Episode.UNPLAYED
     * @param episodes   the FeedItems.
     * @param resetMediaPosition true if this method should also reset the position of the Episode's EpisodeMedia object.
     */
    @OptIn(UnstableApi::class)
    fun setPlayState(played: Int, resetMediaPosition: Boolean, vararg episodes: Episode) : Job {
        Logd(TAG, "setPlayState called")
        return runOnIOScope {
            for (episode in episodes) {
                setPlayStateSync(played, resetMediaPosition, episode)
            }
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun setPlayStateSync(played: Int, resetMediaPosition: Boolean, episode: Episode) : Episode {
        Logd(TAG, "setPlayStateSync called resetMediaPosition: $resetMediaPosition")
        val result = upsert(episode) {
            it.playState = played
            if (resetMediaPosition) it.media?.setPosition(0)
        }
        if (played == Episode.PLAYED && shouldRemoveFromQueuesMarkPlayed()) removeFromAllQueuesSync(result)
        EventFlow.postEvent(FlowEvent.EpisodePlayedEvent(result))
        return result
    }
}