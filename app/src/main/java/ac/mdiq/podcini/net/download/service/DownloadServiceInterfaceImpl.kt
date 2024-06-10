package ac.mdiq.podcini.net.download.service

import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.work.*
import androidx.work.Constraints.Builder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit


class DownloadServiceInterfaceImpl : DownloadServiceInterface() {
    override fun downloadNow(context: Context, item: FeedItem, ignoreConstraints: Boolean) {
        val workRequest: OneTimeWorkRequest.Builder = getRequest(context, item)
        workRequest.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        if (ignoreConstraints) workRequest.setConstraints(Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        else workRequest.setConstraints(constraints)

        if (item.media?.download_url != null)
            WorkManager.getInstance(context).enqueueUniqueWork(item.media!!.download_url!!, ExistingWorkPolicy.KEEP, workRequest.build())
    }

    override fun download(context: Context, item: FeedItem) {
        val workRequest: OneTimeWorkRequest.Builder = getRequest(context, item)
        workRequest.setConstraints(constraints)
        if (item.media?.download_url != null)
            WorkManager.getInstance(context).enqueueUniqueWork(item.media!!.download_url!!, ExistingWorkPolicy.KEEP, workRequest.build())
    }

    @OptIn(UnstableApi::class) override fun cancel(context: Context, media: FeedMedia) {
        // This needs to be done here, not in the worker. Reason: The worker might or might not be running.
        DBWriter.deleteFeedMediaOfItem(context, media.id) // Remove partially downloaded file
        val tag = WORK_TAG_EPISODE_URL + media.download_url
        val future: Future<List<WorkInfo>> = WorkManager.getInstance(context).getWorkInfosByTag(tag)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val workInfoList = future.get() // Wait for the completion of the future operation and retrieve the result
                workInfoList.forEach { workInfo ->
                    if (workInfo.tags.contains(WORK_DATA_WAS_QUEUED)) {
                        if (media.item != null) DBWriter.removeQueueItem(context, false, media.item!!)
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

    companion object {
        @OptIn(UnstableApi::class) private fun getRequest(context: Context, item: FeedItem): OneTimeWorkRequest.Builder {
            val workRequest: OneTimeWorkRequest.Builder = OneTimeWorkRequest.Builder(EpisodeDownloadWorker::class.java)
                .setInitialDelay(0L, TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .addTag(WORK_TAG_EPISODE_URL + item.media!!.download_url)
            if (!item.isTagged(FeedItem.TAG_QUEUE) && UserPreferences.enqueueDownloadedEpisodes()) {
                DBWriter.addQueueItem(context, false, item.id)
                workRequest.addTag(WORK_DATA_WAS_QUEUED)
            }
            workRequest.setInputData(Data.Builder().putLong(WORK_DATA_MEDIA_ID, item.media!!.id).build())
            return workRequest
        }

        private val constraints: Constraints
            get() {
                val constraints = Builder()
                if (UserPreferences.isAllowMobileEpisodeDownload) constraints.setRequiredNetworkType(NetworkType.CONNECTED)
                else constraints.setRequiredNetworkType(NetworkType.UNMETERED)

                return constraints.build()
            }
    }
}
