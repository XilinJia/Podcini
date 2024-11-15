package ac.mdiq.podcini.net.download.service

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadError
import ac.mdiq.podcini.net.download.service.DownloadRequestCreator.create
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.net.utils.NetworkUtils.isAllowMobileEpisodeDownload
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.storage.database.Episodes
import ac.mdiq.podcini.storage.database.LogsAndStats
import ac.mdiq.podcini.storage.database.Queues
import ac.mdiq.podcini.storage.database.Queues.removeFromQueueSync
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.EpisodeMedia.MediaMetadataRetrieverCompat
import ac.mdiq.podcini.storage.utils.ChapterUtils
import ac.mdiq.podcini.ui.activity.starter.MainActivityStarter
import ac.mdiq.podcini.ui.utils.NotificationUtils
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.config.ClientConfigurator
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import androidx.work.Constraints.Builder
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class DownloadServiceInterfaceImpl : DownloadServiceInterface() {
    override fun downloadNow(context: Context, item: Episode, ignoreConstraints: Boolean) {
        if (item.media?.downloadUrl.isNullOrEmpty()) return
        Logd(TAG, "starting downloadNow")
        val workRequest: OneTimeWorkRequest.Builder = getRequest(item)
        workRequest.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        if (ignoreConstraints) workRequest.setConstraints(Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        else workRequest.setConstraints(constraints)
        WorkManager.getInstance(context).enqueueUniqueWork(item.media!!.downloadUrl!!, ExistingWorkPolicy.KEEP, workRequest.build())
    }

    override fun download(context: Context, item: Episode) {
        if (item.media?.downloadUrl.isNullOrEmpty()) return
        Logd(TAG, "starting download")
        val workRequest: OneTimeWorkRequest.Builder = getRequest(item)
        workRequest.setConstraints(constraints)
        WorkManager.getInstance(context).enqueueUniqueWork(item.media!!.downloadUrl!!, ExistingWorkPolicy.KEEP, workRequest.build())
    }

    override fun cancel(context: Context, media: EpisodeMedia) {
        Logd(TAG, "starting cancel")
        // This needs to be done here, not in the worker. Reason: The worker might or might not be running.
        val item_ = media.episodeOrFetch()
        if (item_ != null) Episodes.deleteEpisodeMedia(context, item_) // Remove partially downloaded file
        val tag = WORK_TAG_EPISODE_URL + media.downloadUrl
        val future: Future<List<WorkInfo>> = WorkManager.getInstance(context).getWorkInfosByTag(tag)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val workInfoList = future.get() // Wait for the completion of the future operation and retrieve the result
                workInfoList.forEach { workInfo ->
                    if (workInfo.tags.contains(WORK_DATA_WAS_QUEUED)) {
                        val item_ = media.episodeOrFetch()
                        if (item_ != null) removeFromQueueSync(curQueue, item_)
                    }
                }
                WorkManager.getInstance(context).cancelAllWorkByTag(tag)
            } catch (exception: Throwable) {
                WorkManager.getInstance(context).cancelAllWorkByTag(tag)
                exception.printStackTrace()
            }
        }
    }

    override fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }

    class EpisodeDownloadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        private var downloader: Downloader? = null
        private val isLastRunAttempt: Boolean
            get() = runAttemptCount >= 2

        override fun doWork(): Result {
            Logd(TAG, "starting doWork")
            ClientConfigurator.initialize(applicationContext)
            val mediaId = inputData.getLong(WORK_DATA_MEDIA_ID, 0)
            val media = realm.query(EpisodeMedia::class).query("id == $0", mediaId).first().find()
            if (media == null) {
                Log.e(TAG, "media is null for mediaId: $mediaId")
                return Result.failure()
            }
            val request = create(media).build()
            val progressUpdaterThread: Thread = object : Thread() {
                override fun run() {
                    while (true) {
                        try {
                            synchronized(notificationProgress) {
                                if (isInterrupted) return
                                notificationProgress.put(media.getEpisodeTitle(), request.progressPercent)
                            }
                            setProgressAsync(Data.Builder().putInt(WORK_DATA_PROGRESS, request.progressPercent).build()).get()
                            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            nm.notify(R.id.notification_downloading, generateProgressNotification())
                            sleep(1000)
                        } catch (e: InterruptedException) { return
                        } catch (e: ExecutionException) { return }
                    }
                }
            }
            progressUpdaterThread.start()
            var result: Result
            try { result = performDownload(media, request)
            } catch (e: Exception) {
                e.printStackTrace()
                result = Result.failure()
            }
            if (result == Result.failure() && downloader?.downloadRequest?.destination != null)
                FileUtils.deleteQuietly(File(downloader!!.downloadRequest.destination!!))
            progressUpdaterThread.interrupt()
            try { progressUpdaterThread.join() } catch (e: InterruptedException) { e.printStackTrace() }
            synchronized(notificationProgress) {
                notificationProgress.remove(media.getEpisodeTitle())
                if (notificationProgress.isEmpty()) {
                    val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(R.id.notification_downloading)
                }
            }
            Logd(TAG, "Worker for " + media.downloadUrl + " returned.")
            return result
        }
        override fun onStopped() {
            super.onStopped()
            downloader?.cancel()
        }
        override fun getForegroundInfoAsync(): ListenableFuture<ForegroundInfo> {
            return Futures.immediateFuture(ForegroundInfo(R.id.notification_downloading, generateProgressNotification()))
        }
        private fun performDownload(media: EpisodeMedia, request: DownloadRequest): Result {
            Logd(TAG, "starting performDownload")
            if (request.destination == null) {
                Log.e(TAG, "performDownload request.destination is null")
                return Result.failure()
            }
            val dest = File(request.destination)
            if (!dest.exists()) {
                try { dest.createNewFile() } catch (e: IOException) { Log.e(TAG, "performDownload Unable to create file") }
            }
            if (dest.exists()) {
                try {
                    var episode = realm.query(Episode::class).query("id == ${media.id}").first().find()
                    if (episode != null) {
                        episode = upsertBlk(episode) { it.media?.setfileUrlOrNull(request.destination) }
                        EventFlow.postEvent(FlowEvent.EpisodeMediaEvent.updated(episode))
                    } else Log.e(TAG, "performDownload media.episode is null")
                } catch (e: Exception) { Log.e(TAG, "performDownload Exception in writeFileUrl: " + e.message) }
            }
            downloader = DefaultDownloaderFactory().create(request)
            if (downloader == null) {
                Log.e(TAG, "performDownload Unable to create downloader")
                return Result.failure()
            }
            try { downloader!!.call()
            } catch (e: Exception) {
                Log.e(TAG, "failed performDownload exception on downloader!!.call() ${e.message}")
                LogsAndStats.addDownloadStatus(downloader!!.result)
                sendErrorNotification(request.title?:"")
                return Result.failure()
            }
            // This also happens when the worker was preempted, not just when the user cancelled it
            if (downloader!!.cancelled) return Result.success()
            val status = downloader!!.result
            if (status.isSuccessful) {
                val handler = MediaDownloadedHandler(applicationContext, downloader!!.result, request)
                handler.run()
                LogsAndStats.addDownloadStatus(handler.updatedStatus)
                return Result.success()
            }
            if (status.reason == DownloadError.ERROR_HTTP_DATA_ERROR && status.reasonDetailed.toInt() == 416) {
                Logd(TAG, "Requested invalid range, restarting download from the beginning")
                if (downloader?.downloadRequest?.destination != null) FileUtils.deleteQuietly(File(downloader!!.downloadRequest.destination!!))
                sendMessage(request.title?:"", false)
                return retry3times()
            }
            Log.e(TAG, "Download failed ${request.title} ${status.reason}")
            LogsAndStats.addDownloadStatus(status)
            if (status.reason == DownloadError.ERROR_FORBIDDEN || status.reason == DownloadError.ERROR_NOT_FOUND
                    || status.reason == DownloadError.ERROR_UNAUTHORIZED || status.reason == DownloadError.ERROR_IO_BLOCKED) {
                Log.e(TAG, "performDownload failure on various reasons")
                // Fail fast, these are probably unrecoverable
                sendErrorNotification(request.title?:"")
                return Result.failure()
            }
            sendMessage(request.title?:"", false)
            return retry3times()
        }
        private fun retry3times(): Result {
            if (isLastRunAttempt) {
                Log.e(TAG, "retry3times failure on isLastRunAttempt")
                sendErrorNotification(downloader!!.downloadRequest.title?:"")
                return Result.failure()
            } else return Result.retry()
        }
        private fun sendMessage(episodeTitle: String, isImmediateFail: Boolean) {
            var episodeTitle = episodeTitle
            val retrying = !isLastRunAttempt && !isImmediateFail
            if (episodeTitle.length > 20) episodeTitle = episodeTitle.substring(0, 19) + "…"

            EventFlow.postEvent(FlowEvent.MessageEvent(
                applicationContext.getString(if (retrying) R.string.download_error_retrying else R.string.download_error_not_retrying, episodeTitle),
                { ctx: Context -> MainActivityStarter(ctx).withDownloadLogsOpen().start() },
                applicationContext.getString(R.string.download_error_details)))
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
            val builder = NotificationCompat.Builder(applicationContext, NotificationUtils.CHANNEL_ID.error.name)
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
            val builder = NotificationCompat.Builder(applicationContext, NotificationUtils.CHANNEL_ID.downloading.name)
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

        class MediaDownloadedHandler(private val context: Context, var updatedStatus: DownloadResult, private val request: DownloadRequest) : Runnable {
             override fun run() {
                var item = realm.query(Episode::class).query("id == ${request.feedfileId}").first().find()
                if (item == null) {
                    Log.e(TAG, "Could not find downloaded episode object in database")
                    return
                }
                val media = item.media
                if (media == null) {
                    Log.e(TAG, "Could not find downloaded media object in database")
                    return
                }
                val broadcastUnreadStateUpdate = item.isNew
                item = upsertBlk(item) {
                    it.media?.setIsDownloaded()
                    it.media?.setfileUrlOrNull(request.destination)
                    if (request.destination != null) it.media?.size = File(request.destination).length()
                    it.media?.checkEmbeddedPicture(false) // enforce check
                    if (it.chapters.isEmpty()) it.media?.setChapters(ChapterUtils.loadChaptersFromMediaFile(it.media!!, context))
                    if (it.podcastIndexChapterUrl != null) ChapterUtils.loadChaptersFromUrl(it.podcastIndexChapterUrl!!, false)
                    var durationStr: String? = null
                    try {
                        MediaMetadataRetrieverCompat().use { mmr ->
                            if (it.media != null) mmr.setDataSource(it.media!!.fileUrl)
                            durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            if (durationStr != null) it.media?.setDuration(durationStr.toInt())
                        }
                    } catch (e: NumberFormatException) { Logd(TAG, "Invalid file duration: $durationStr")
                    } catch (e: Exception) {
                        Log.e(TAG, "Get duration failed", e)
                        it.media?.setDuration(30000)
                    }
                    it.disableAutoDownload()
                }
                EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item))
//                        TODO: should use different event?
                if (broadcastUnreadStateUpdate) EventFlow.postEvent(FlowEvent.EpisodePlayedEvent(item))
                if (isProviderConnected) {
                    Logd(TAG, "enqueue synch")
                    val action = EpisodeAction.Builder(item, EpisodeAction.DOWNLOAD).currentTimestamp().build()
                    SynchronizationQueueSink.enqueueEpisodeActionIfSyncActive(context, action)
                }
                Logd(TAG, "media.episode.isNew: ${item.isNew} ${item.playState}")
            }
        }

        companion object {
            private val notificationProgress: MutableMap<String, Int> = HashMap()
        }
    }

    companion object {
        private val TAG: String = DownloadServiceInterfaceImpl::class.simpleName ?: "Anonymous"

        private val constraints: Constraints
            get() {
                val constraints = Builder()
                if (isAllowMobileEpisodeDownload) constraints.setRequiredNetworkType(NetworkType.CONNECTED)
                else constraints.setRequiredNetworkType(NetworkType.UNMETERED)
                return constraints.build()
            }

        
        private fun getRequest(item: Episode): OneTimeWorkRequest.Builder {
            Logd(TAG, "starting getRequest")
            val workRequest: OneTimeWorkRequest.Builder = OneTimeWorkRequest.Builder(EpisodeDownloadWorker::class.java)
                .setInitialDelay(0L, TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .addTag(WORK_TAG_EPISODE_URL + item.media!!.downloadUrl)
            if (enqueueDownloadedEpisodes()) {
                if (item.feed?.preferences?.queue != null)
                    runBlocking { Queues.addToQueueSync(item, item.feed?.preferences?.queue) }
                workRequest.addTag(WORK_DATA_WAS_QUEUED)
            }
            workRequest.setInputData(Data.Builder().putLong(WORK_DATA_MEDIA_ID, item.media!!.id).build())
            return workRequest
        }
        private fun enqueueDownloadedEpisodes(): Boolean {
            return appPrefs.getBoolean(UserPreferences.Prefs.prefEnqueueDownloaded.name, true)
        }
    }
}
