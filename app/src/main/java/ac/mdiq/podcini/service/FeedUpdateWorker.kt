package ac.mdiq.podcini.service

import ac.mdiq.podcini.R
import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.util.UnstableApi
import androidx.work.*
import com.annimon.stream.Collectors
import com.annimon.stream.Stream
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import ac.mdiq.podcini.util.config.ClientConfigurator
import ac.mdiq.podcini.feed.LocalFeedUpdater
import ac.mdiq.podcini.service.download.DefaultDownloaderFactory
import ac.mdiq.podcini.service.download.DownloadRequestCreator.create
import ac.mdiq.podcini.service.download.NewEpisodesNotification
import ac.mdiq.podcini.service.download.handler.FeedSyncTask
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBTasks
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.util.NetworkUtils
import ac.mdiq.podcini.net.download.FeedUpdateManager
import ac.mdiq.podcini.ui.utils.NotificationUtils
import ac.mdiq.podcini.storage.model.download.DownloadError
import ac.mdiq.podcini.storage.model.download.DownloadResult
import ac.mdiq.podcini.storage.model.feed.Feed
import android.os.Build
import java.util.*

class FeedUpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val newEpisodesNotification = NewEpisodesNotification()
    private val notificationManager = NotificationManagerCompat.from(context)

    @UnstableApi override fun doWork(): Result {
        ClientConfigurator.initialize(applicationContext)
        newEpisodesNotification.loadCountersBeforeRefresh()

        val toUpdate: MutableList<Feed>
        val feedId = inputData.getLong(FeedUpdateManager.EXTRA_FEED_ID, -1)
        var allAreLocal = true
        var force = false
        if (feedId == -1L) { // Update all
            toUpdate = DBReader.getFeedList().toMutableList()
            val itr = toUpdate.iterator()
            while (itr.hasNext()) {
                val feed = itr.next()
                if (feed.preferences?.keepUpdated == false) {
                    itr.remove()
                }
                if (!feed.isLocalFeed) {
                    allAreLocal = false
                }
            }
            toUpdate.shuffle() // If the worker gets cancelled early, every feed has a chance to be updated
        } else {
            val feed = DBReader.getFeed(feedId) ?: return Result.success()
            Log.d(TAG, "doWork feed.download_url: ${feed.download_url}")
            if (!feed.isLocalFeed) {
                allAreLocal = false
            }
            toUpdate = ArrayList()
            toUpdate.add(feed) // Needs to be updatable, so no singletonList
            force = true
        }

        if (!inputData.getBoolean(FeedUpdateManager.EXTRA_EVEN_ON_MOBILE, false) && !allAreLocal) {
            if (!NetworkUtils.networkAvailable() || !NetworkUtils.isFeedRefreshAllowed) {
                Log.d(TAG, "Blocking automatic update")
                return Result.retry()
            }
        }
        refreshFeeds(toUpdate, force)

        notificationManager.cancel(R.id.notification_updating_feeds)
        DBTasks.autodownloadUndownloadedItems(applicationContext)
        return Result.success()
    }

    private fun createNotification(toUpdate: List<Feed?>?): Notification {
        val context = applicationContext
        var contentText = ""
        var bigText: String? = ""
        if (toUpdate != null) {
            contentText = context.resources.getQuantityString(R.plurals.downloads_left,
                toUpdate.size, toUpdate.size)
            bigText = Stream.of(toUpdate).map { feed: Feed? -> "• " + feed!!.title }
                .collect(Collectors.joining("\n"))
        }
        return NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID_DOWNLOADING)
            .setContentTitle(context.getString(R.string.download_notification_title_feeds))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setOngoing(true)
            .addAction(R.drawable.ic_cancel, context.getString(R.string.cancel_label),
                WorkManager.getInstance(context).createCancelPendingIntent(id))
            .build()
    }

    override fun getForegroundInfoAsync(): ListenableFuture<ForegroundInfo> {
        return Futures.immediateFuture(ForegroundInfo(R.id.notification_updating_feeds, createNotification(null)))
    }

    @UnstableApi private fun refreshFeeds(toUpdate: MutableList<Feed>, force: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this.applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Log.e(TAG, "refreshFeeds: require POST_NOTIFICATIONS permission")
//            Toast.makeText(applicationContext, R.string.notification_permission_text, Toast.LENGTH_LONG).show()
            return
        }

        while (toUpdate.isNotEmpty()) {
            if (isStopped) {
                return
            }
            notificationManager.notify(R.id.notification_updating_feeds, createNotification(toUpdate))
            val feed = toUpdate[0]
            try {
                if (feed.isLocalFeed) {
                    LocalFeedUpdater.updateFeed(feed, applicationContext, null)
                } else {
                    refreshFeed(feed, force)
                }
            } catch (e: Exception) {
                DBWriter.setFeedLastUpdateFailed(feed.id, true)
                val status = DownloadResult(feed, feed.title?:"",
                    DownloadError.ERROR_IO_ERROR, false, e.message?:"")
                DBWriter.addDownloadStatus(status)
            }
            toUpdate.removeAt(0)
        }
    }

    @UnstableApi @Throws(Exception::class)
    fun refreshFeed(feed: Feed, force: Boolean) {
        val nextPage = (inputData.getBoolean(FeedUpdateManager.EXTRA_NEXT_PAGE, false)
                && feed.nextPageLink != null)
        if (nextPage) {
            feed.pageNr += 1
        }
        val builder = create(feed)
        builder.setForce(force || feed.hasLastUpdateFailed())
        if (nextPage) {
            builder.source = feed.nextPageLink
        }
        val request = builder.build()

        val downloader = DefaultDownloaderFactory().create(request) ?: throw Exception("Unable to create downloader")

        downloader.call()

        if (!downloader.result.isSuccessful) {
            if (downloader.cancelled || downloader.result.reason == DownloadError.ERROR_DOWNLOAD_CANCELLED) {
                return
            }
            DBWriter.setFeedLastUpdateFailed(request.feedfileId, true)
            DBWriter.addDownloadStatus(downloader.result)
            return
        }

        val feedSyncTask = FeedSyncTask(applicationContext, request)
        val success = feedSyncTask.run()

        if (!success) {
            DBWriter.setFeedLastUpdateFailed(request.feedfileId, true)
            DBWriter.addDownloadStatus(feedSyncTask.downloadStatus)
            return
        }

        if (request.feedfileId == 0L) {
            return  // No download logs for new subscriptions
        }
        // we create a 'successful' download log if the feed's last refresh failed
        val log = DBReader.getFeedDownloadLog(request.feedfileId)
        if (log.isNotEmpty() && !log[0].isSuccessful) {
            DBWriter.addDownloadStatus(feedSyncTask.downloadStatus)
        }
        newEpisodesNotification.showIfNeeded(applicationContext, feedSyncTask.savedFeed!!)
        if (!request.source.isNullOrEmpty()) {
            when {
                !downloader.permanentRedirectUrl.isNullOrEmpty() -> {
                    DBWriter.updateFeedDownloadURL(request.source, downloader.permanentRedirectUrl!!)
                }
                feedSyncTask.redirectUrl.isNotEmpty() && feedSyncTask.redirectUrl != request.source -> {
                    DBWriter.updateFeedDownloadURL(request.source, feedSyncTask.redirectUrl)
                }
            }
        }
    }

    companion object {
        private const val TAG = "FeedUpdateWorker"
    }
}
