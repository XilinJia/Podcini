package ac.mdiq.podcini.net.download

import ac.mdiq.podcini.R
import android.content.Context
import android.content.DialogInterface
import android.util.Log
import androidx.work.*
import androidx.work.Constraints.Builder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.net.download.service.FeedUpdateWorker
import ac.mdiq.podcini.util.NetworkUtils.isFeedRefreshAllowed
import ac.mdiq.podcini.util.NetworkUtils.isNetworkRestricted
import ac.mdiq.podcini.util.NetworkUtils.isVpnOverWifi
import ac.mdiq.podcini.util.NetworkUtils.networkAvailable
import ac.mdiq.podcini.util.event.MessageEvent
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.preferences.UserPreferences
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.TimeUnit

object FeedUpdateManager {
    const val WORK_TAG_FEED_UPDATE: String = "feedUpdate"
    private const val WORK_ID_FEED_UPDATE = "ac.mdiq.podcini.service.download.FeedUpdateWorker"
    private const val WORK_ID_FEED_UPDATE_MANUAL = "feedUpdateManual"
    const val EXTRA_FEED_ID: String = "feed_id"
    const val EXTRA_NEXT_PAGE: String = "next_page"
    const val EXTRA_EVEN_ON_MOBILE: String = "even_on_mobile"
    private const val TAG = "AutoUpdateManager"

    /**
     * Start / restart periodic auto feed refresh
     * @param context Context
     */
    @JvmStatic
    fun restartUpdateAlarm(context: Context, replace: Boolean) {
        if (UserPreferences.isAutoUpdateDisabled) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_ID_FEED_UPDATE)
        } else {
            val workRequest: PeriodicWorkRequest = PeriodicWorkRequest.Builder(
                FeedUpdateWorker::class.java, UserPreferences.updateInterval, TimeUnit.HOURS)
                .setConstraints(Builder()
                    .setRequiredNetworkType(if (UserPreferences.isAllowMobileFeedRefresh) NetworkType.CONNECTED else NetworkType.UNMETERED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_ID_FEED_UPDATE,
                if (replace) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP, workRequest)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun runOnce(context: Context, feed: Feed? = null, nextPage: Boolean = false) {
        val workRequest: OneTimeWorkRequest.Builder = OneTimeWorkRequest.Builder(FeedUpdateWorker::class.java)
            .setInitialDelay(0L, TimeUnit.MILLISECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(WORK_TAG_FEED_UPDATE)
        if (feed == null || !feed.isLocalFeed) workRequest.setConstraints(Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())

        val builder = Data.Builder()
        builder.putBoolean(EXTRA_EVEN_ON_MOBILE, true)
        if (feed != null) {
            builder.putLong(EXTRA_FEED_ID, feed.id)
            builder.putBoolean(EXTRA_NEXT_PAGE, nextPage)
        }
        workRequest.setInputData(builder.build())
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_ID_FEED_UPDATE_MANUAL, ExistingWorkPolicy.REPLACE, workRequest.build())
    }

    @JvmStatic
    @JvmOverloads
    fun runOnceOrAsk(context: Context, feed: Feed? = null) {
        Log.d(TAG, "Run auto update immediately in background.")
        when {
            feed != null && feed.isLocalFeed -> runOnce(context, feed)
            !networkAvailable() -> EventBus.getDefault().post(MessageEvent(context.getString(R.string.download_error_no_connection)))
            isFeedRefreshAllowed -> runOnce(context, feed)
            else -> confirmMobileRefresh(context, feed)
        }
    }

    private fun confirmMobileRefresh(context: Context, feed: Feed?) {
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.feed_refresh_title)
            .setPositiveButton(R.string.confirm_mobile_streaming_button_once) { _: DialogInterface?, _: Int -> runOnce(context, feed) }
            .setNeutralButton(R.string.confirm_mobile_streaming_button_always) { _: DialogInterface?, _: Int ->
                UserPreferences.isAllowMobileFeedRefresh = true
                runOnce(context, feed)
            }
            .setNegativeButton(R.string.no, null)
        if (isNetworkRestricted && isVpnOverWifi) builder.setMessage(R.string.confirm_mobile_feed_refresh_dialog_message_vpn)
        else builder.setMessage(R.string.confirm_mobile_feed_refresh_dialog_message)

        builder.show()
    }
}
