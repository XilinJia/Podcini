package ac.mdiq.podcini.net.download.service

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.DownloadRequestCreator.create
import ac.mdiq.podcini.net.download.service.handler.MediaDownloadedHandler
import ac.mdiq.podcini.net.download.serviceinterface.DownloadRequest
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.model.download.DownloadError
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.ui.activity.appstartintent.MainActivityStarter
import ac.mdiq.podcini.ui.utils.NotificationUtils
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.config.ClientConfigurator
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.runBlocking
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutionException

class EpisodeDownloadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private var downloader: Downloader? = null

    @UnstableApi
    override fun doWork(): Result {
        ClientConfigurator.initialize(applicationContext)
        val mediaId = inputData.getLong(DownloadServiceInterface.WORK_DATA_MEDIA_ID, 0)
        val media = DBReader.getFeedMedia(mediaId) ?: return Result.failure()

        val request = create(media).build()
        val progressUpdaterThread: Thread = object : Thread() {
            override fun run() {
                while (true) {
                    try {
                        synchronized(notificationProgress) {
                            if (isInterrupted) return
                            notificationProgress.put(media.getEpisodeTitle(), request.progressPercent)
                        }
                        setProgressAsync(Data.Builder().putInt(DownloadServiceInterface.WORK_DATA_PROGRESS, request.progressPercent).build()).get()
                        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.notify(R.id.notification_downloading, generateProgressNotification())
                        sleep(1000)
                    } catch (e: InterruptedException) {
                        return
                    } catch (e: ExecutionException) {
                        return
                    }
                }
            }
        }
        progressUpdaterThread.start()
        var result: Result
        try {
            result = performDownload(media, request)
        } catch (e: Exception) {
            e.printStackTrace()
            result = Result.failure()
        }
        if (result == Result.failure() && downloader?.downloadRequest?.destination != null)
            FileUtils.deleteQuietly(File(downloader!!.downloadRequest.destination!!))

        progressUpdaterThread.interrupt()
        try {
            progressUpdaterThread.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        synchronized(notificationProgress) {
            notificationProgress.remove(media.getEpisodeTitle())
            if (notificationProgress.isEmpty()) {
                val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(R.id.notification_downloading)
            }
        }
        Logd(TAG, "Worker for " + media.download_url + " returned.")
        return result
    }

    override fun onStopped() {
        super.onStopped()
        downloader?.cancel()
    }

    override fun getForegroundInfoAsync(): ListenableFuture<ForegroundInfo> {
        return Futures.immediateFuture(ForegroundInfo(R.id.notification_downloading, generateProgressNotification()))
    }

    @OptIn(UnstableApi::class) private fun performDownload(media: FeedMedia, request: DownloadRequest): Result {
        val dest = File(request.destination)
        if (!dest.exists()) {
            try {
                dest.createNewFile()
            } catch (e: IOException) {
                Log.e(TAG, "Unable to create file")
            }
        }

        if (dest.exists()) {
            media.file_url = request.destination
            try {
                runBlocking { DBWriter.persistFeedMedia(media).join() }
            } catch (e: Exception) {
                Log.e(TAG, "ExecutionException in writeFileUrl: " + e.message)
            }
        }

        downloader = DefaultDownloaderFactory().create(request)
        if (downloader == null) {
            Logd(TAG, "Unable to create downloader")
            return Result.failure()
        }

        try {
            downloader!!.call()
        } catch (e: Exception) {
            DBWriter.addDownloadStatus(downloader!!.result)
            sendErrorNotification(request.title?:"")
            return Result.failure()
        }

        // This also happens when the worker was preempted, not just when the user cancelled it
        if (downloader!!.cancelled) return Result.success()

        val status = downloader!!.result
        if (status.isSuccessful) {
            val handler = MediaDownloadedHandler(applicationContext, downloader!!.result, request)
            handler.run()
            DBWriter.addDownloadStatus(handler.updatedStatus)
            return Result.success()
        }

        if (status.reason == DownloadError.ERROR_HTTP_DATA_ERROR && status.reasonDetailed.toInt() == 416) {
            Logd(TAG, "Requested invalid range, restarting download from the beginning")
            if (downloader?.downloadRequest?.destination != null) FileUtils.deleteQuietly(File(downloader!!.downloadRequest.destination!!))
            sendMessage(request.title?:"", false)
            return retry3times()
        }

        Log.e(TAG, "Download failed ${request.title} ${status.reason}")
        DBWriter.addDownloadStatus(status)
        if (status.reason == DownloadError.ERROR_FORBIDDEN || status.reason == DownloadError.ERROR_NOT_FOUND || status.reason == DownloadError.ERROR_UNAUTHORIZED || status.reason == DownloadError.ERROR_IO_BLOCKED) {
            // Fail fast, these are probably unrecoverable
            sendErrorNotification(request.title?:"")
            return Result.failure()
        }
        sendMessage(request.title?:"", false)
        return retry3times()
    }

    private fun retry3times(): Result {
        if (isLastRunAttempt) {
            sendErrorNotification(downloader!!.downloadRequest.title?:"")
            return Result.failure()
        } else return Result.retry()
    }

    private val isLastRunAttempt: Boolean
        get() = runAttemptCount >= 2

    private fun sendMessage(episodeTitle: String, isImmediateFail: Boolean) {
        var episodeTitle = episodeTitle
        val retrying = !isLastRunAttempt && !isImmediateFail
        if (episodeTitle.length > 20) episodeTitle = episodeTitle.substring(0, 19) + "…"

        EventFlow.postEvent(FlowEvent.MessageEvent(applicationContext.getString(
            if (retrying) R.string.download_error_retrying else R.string.download_error_not_retrying,
            episodeTitle), { ctx: Context -> MainActivityStarter(ctx).withDownloadLogsOpen().start() }, applicationContext.getString(R.string.download_error_details)))
    }

    private fun getDownloadLogsIntent(context: Context): PendingIntent {
        val intent = MainActivityStarter(context).withDownloadLogsOpen().getIntent()
        return PendingIntent.getActivity(context, R.id.pending_intent_download_service_report, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun getDownloadsIntent(context: Context): PendingIntent {
        val intent = MainActivityStarter(context).withFragmentLoaded("DownloadsFragment").getIntent()
        return PendingIntent.getActivity(context, R.id.pending_intent_download_service_notification, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun sendErrorNotification(title: String) {
//        TODO: need to get number of subscribers in SharedFlow
//        if (EventBus.getDefault().hasSubscriberForEvent(FlowEvent.MessageEvent::class.java)) {
//            sendMessage(title, false)
//            return
//        }

        val builder = NotificationCompat.Builder(applicationContext, NotificationUtils.CHANNEL_ID_DOWNLOAD_ERROR)
        builder.setTicker(applicationContext.getString(R.string.download_report_title))
            .setContentTitle(applicationContext.getString(R.string.download_report_title))
            .setContentText(applicationContext.getString(R.string.download_error_tap_for_details))
            .setSmallIcon(R.drawable.ic_notification_sync_error)
            .setContentIntent(getDownloadLogsIntent(applicationContext))
            .setAutoCancel(true)
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(R.id.notification_download_report, builder.build())
    }

    private fun generateProgressNotification(): Notification {
        val bigTextB = StringBuilder()
        var progressCopy: Map<String, Int>
        synchronized(notificationProgress) {
            progressCopy = HashMap(notificationProgress)
        }
        for ((key, value) in progressCopy) {
            bigTextB.append(String.format(Locale.getDefault(), "%s (%d%%)\n", key, value))
        }
        val bigText = bigTextB.toString().trim { it <= ' ' }
        val contentText = if (progressCopy.size == 1) bigText
        else applicationContext.resources.getQuantityString(R.plurals.downloads_left, progressCopy.size, progressCopy.size)

        val builder = NotificationCompat.Builder(applicationContext, NotificationUtils.CHANNEL_ID_DOWNLOADING)
        builder.setTicker(applicationContext.getString(R.string.download_notification_title_episodes))
            .setContentTitle(applicationContext.getString(R.string.download_notification_title_episodes))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(getDownloadsIntent(applicationContext))
            .setAutoCancel(false)
            .setOngoing(true)
            .setWhen(0)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        return builder.build()
    }

    companion object {
        private const val TAG = "EpisodeDownloadWorker"
        private val notificationProgress: MutableMap<String, Int> = HashMap()
    }
}
