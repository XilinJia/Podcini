package ac.mdiq.podcini.core.service.download

import android.content.Context
import androidx.work.*
import androidx.work.Constraints.Builder
import ac.mdiq.podcini.core.storage.DBWriter
import ac.mdiq.podcini.model.feed.FeedItem
import ac.mdiq.podcini.model.feed.FeedMedia
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.storage.preferences.UserPreferences
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit


class DownloadServiceInterfaceImpl : DownloadServiceInterface() {
    override fun downloadNow(context: Context, item: FeedItem, ignoreConstraints: Boolean) {
        val workRequest: OneTimeWorkRequest.Builder = getRequest(context, item)
        workRequest.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        if (ignoreConstraints) {
            workRequest.setConstraints(Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        } else {
            workRequest.setConstraints(constraints)
        }
        if (item.media?.download_url != null) WorkManager.getInstance(context).enqueueUniqueWork(item.media!!.download_url!!,
            ExistingWorkPolicy.KEEP, workRequest.build())
    }

    override fun download(context: Context, item: FeedItem) {
        val workRequest: OneTimeWorkRequest.Builder = getRequest(context, item)
        workRequest.setConstraints(constraints)
        if (item.media?.download_url != null) WorkManager.getInstance(context).enqueueUniqueWork(item.media!!.download_url!!,
            ExistingWorkPolicy.KEEP, workRequest.build())
    }

    override fun cancel(context: Context, media: FeedMedia) {
        // This needs to be done here, not in the worker. Reason: The worker might or might not be running.
        DBWriter.deleteFeedMediaOfItem(context, media.id) // Remove partially downloaded file
        val tag = WORK_TAG_EPISODE_URL + media.download_url
        val future: Future<List<WorkInfo>> = WorkManager.getInstance(context).getWorkInfosByTag(tag)
        Observable.fromFuture(future)
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe(
                { workInfos: List<WorkInfo> ->
                    for (info in workInfos) {
                        if (info.tags.contains(WORK_DATA_WAS_QUEUED)) {
                            if (media.getItem() != null) DBWriter.removeQueueItem(context, false, media.getItem()!!)
                        }
                    }
                    WorkManager.getInstance(context).cancelAllWorkByTag(tag)
                }, { exception: Throwable ->
                    WorkManager.getInstance(context).cancelAllWorkByTag(tag)
                    exception.printStackTrace()
                })
    }

    override fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }

    companion object {
        private fun getRequest(context: Context, item: FeedItem): OneTimeWorkRequest.Builder {
            val workRequest: OneTimeWorkRequest.Builder = OneTimeWorkRequest.Builder(EpisodeDownloadWorker::class.java)
                .setInitialDelay(0L, TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .addTag(WORK_TAG_EPISODE_URL + item.media!!.download_url)
            if (!item.isTagged(FeedItem.TAG_QUEUE) && UserPreferences.enqueueDownloadedEpisodes()) {
                DBWriter.addQueueItem(context, false, item.id)
                workRequest.addTag(WORK_DATA_WAS_QUEUED)
            }
            workRequest.setInputData(Data.Builder().putLong(WORK_DATA_MEDIA_ID, item.media!!
                .id).build())
            return workRequest
        }

        private val constraints: Constraints
            get() {
                val constraints = Builder()
                if (UserPreferences.isAllowMobileEpisodeDownload) {
                    constraints.setRequiredNetworkType(NetworkType.CONNECTED)
                } else {
                    constraints.setRequiredNetworkType(NetworkType.UNMETERED)
                }
                return constraints.build()
            }
    }
}
