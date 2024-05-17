package ac.mdiq.podcini.net.download.service.handler

import ac.mdiq.podcini.net.download.serviceinterface.DownloadRequest
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.model.MediaMetadataRetrieverCompat
import ac.mdiq.podcini.storage.model.download.DownloadError
import ac.mdiq.podcini.storage.model.download.DownloadResult
import ac.mdiq.podcini.util.ChapterUtils
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.util.concurrent.ExecutionException

/**
 * Handles a completed media download.
 */
class MediaDownloadedHandler(private val context: Context, var updatedStatus: DownloadResult, private val request: DownloadRequest) : Runnable {
    @UnstableApi override fun run() {
        val media = DBReader.getFeedMedia(request.feedfileId)
        if (media == null) {
            Log.e(TAG, "Could not find downloaded media object in database")
            return
        }
        // media.setDownloaded modifies played state
        val broadcastUnreadStateUpdate = media.item != null && media.item!!.isNew
        media.setDownloaded(true)
        media.file_url = request.destination
        if (request.destination != null) media.size = File(request.destination).length()
        media.checkEmbeddedPicture() // enforce check

        // check if file has chapters
        if (media.item != null && !media.item!!.hasChapters()) media.setChapters(ChapterUtils.loadChaptersFromMediaFile(media, context))

        if (media.item?.podcastIndexChapterUrl != null) ChapterUtils.loadChaptersFromUrl(media.item!!.podcastIndexChapterUrl!!, false)

        // Get duration
        var durationStr: String? = null
        try {
            MediaMetadataRetrieverCompat().use { mmr ->
                mmr.setDataSource(media.file_url)
                durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                media.setDuration( durationStr!!.toInt())
                Logd(TAG, "Duration of file is " + media.getDuration())
            }
        } catch (e: NumberFormatException) {
            Logd(TAG, "Invalid file duration: $durationStr")
        } catch (e: Exception) {
            Log.e(TAG, "Get duration failed", e)
            media.setDuration(30000)
        }

        val item = media.item

        try {
            DBWriter.persistFeedMedia(media).get()

            // we've received the media, we don't want to autodownload it again
            if (item != null) {
                item.disableAutoDownload()
                // setFeedItem() signals (via EventBus) that the item has been updated,
                // so we do it after the enclosing media has been updated above,
                // to ensure subscribers will get the updated FeedMedia as well
                DBWriter.persistFeedItem(item).get()
                if (broadcastUnreadStateUpdate) EventFlow.postEvent(FlowEvent.UnreadItemsUpdateEvent())
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "MediaHandlerThread was interrupted")
        } catch (e: ExecutionException) {
            Log.e(TAG, "ExecutionException in MediaHandlerThread: " + e.message)
            updatedStatus = DownloadResult(media, media.getEpisodeTitle(), DownloadError.ERROR_DB_ACCESS_ERROR, false, e.message?:"")
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
