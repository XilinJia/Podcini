package de.danoeh.antennapod.core.service.download.handler

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.media3.common.util.UnstableApi
import de.danoeh.antennapod.core.storage.DBReader
import de.danoeh.antennapod.core.storage.DBWriter
import de.danoeh.antennapod.core.sync.queue.SynchronizationQueueSink
import de.danoeh.antennapod.core.util.ChapterUtils
import de.danoeh.antennapod.event.UnreadItemsUpdateEvent
import de.danoeh.antennapod.model.MediaMetadataRetrieverCompat
import de.danoeh.antennapod.model.download.DownloadError
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.net.download.serviceinterface.DownloadRequest
import de.danoeh.antennapod.net.sync.model.EpisodeAction
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.util.concurrent.ExecutionException

/**
 * Handles a completed media download.
 */
class MediaDownloadedHandler(private val context: Context, var updatedStatus: DownloadResult,
                             private val request: DownloadRequest
) : Runnable {
    @UnstableApi override fun run() {
        val media = DBReader.getFeedMedia(request.feedfileId)
        if (media == null) {
            Log.e(TAG, "Could not find downloaded media object in database")
            return
        }
        // media.setDownloaded modifies played state
        val broadcastUnreadStateUpdate = media.getItem() != null && media.getItem()!!.isNew
        media.setDownloaded(true)
        media.file_url = request.destination
        if (request.destination != null) media.size = File(request.destination!!).length()
        media.checkEmbeddedPicture() // enforce check

        // check if file has chapters
        if (media.getItem() != null && !media.getItem()!!.hasChapters()) {
            media.setChapters(ChapterUtils.loadChaptersFromMediaFile(media, context))
        }

        if (media.getItem()?.podcastIndexChapterUrl != null) {
            ChapterUtils.loadChaptersFromUrl(media.getItem()!!.podcastIndexChapterUrl!!, false)
        }
        // Get duration
        var durationStr: String? = null
        try {
            MediaMetadataRetrieverCompat().use { mmr ->
                mmr.setDataSource(media.file_url)
                durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                media.setDuration( durationStr!!.toInt())
                Log.d(TAG, "Duration of file is " + media.getDuration())
            }
        } catch (e: NumberFormatException) {
            Log.d(TAG, "Invalid file duration: $durationStr")
        } catch (e: Exception) {
            Log.e(TAG, "Get duration failed", e)
        }

        val item = media.getItem()

        try {
            DBWriter.setFeedMedia(media).get()

            // we've received the media, we don't want to autodownload it again
            if (item != null) {
                item.disableAutoDownload()
                // setFeedItem() signals (via EventBus) that the item has been updated,
                // so we do it after the enclosing media has been updated above,
                // to ensure subscribers will get the updated FeedMedia as well
                DBWriter.setFeedItem(item).get()
                if (broadcastUnreadStateUpdate) {
                    EventBus.getDefault().post(UnreadItemsUpdateEvent())
                }
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "MediaHandlerThread was interrupted")
        } catch (e: ExecutionException) {
            Log.e(TAG, "ExecutionException in MediaHandlerThread: " + e.message)
            updatedStatus = DownloadResult(media, media.getEpisodeTitle(),
                DownloadError.ERROR_DB_ACCESS_ERROR, false, e.message?:"")
        }

        if (item != null) {
            val action = EpisodeAction.Builder(item, EpisodeAction.DOWNLOAD)
                .currentTimestamp()
                .build()
            SynchronizationQueueSink.enqueueEpisodeActionIfSynchronizationIsActive(context, action)
        }
    }

    companion object {
        private const val TAG = "MediaDownloadedHandler"
    }
}
