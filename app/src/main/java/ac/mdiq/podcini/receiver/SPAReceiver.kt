package ac.mdiq.podcini.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.R
import ac.mdiq.podcini.util.config.ClientConfigurator
import ac.mdiq.podcini.storage.DBTasks
import ac.mdiq.podcini.net.download.FeedUpdateManager.runOnce
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.util.Logd

/**
 * Receives intents from Podcini Single Purpose apps
 */
class SPAReceiver : BroadcastReceiver() {
    @UnstableApi override fun onReceive(context: Context, intent: Intent) {
        if (!TextUtils.equals(intent.action, ACTION_SP_APPS_QUERY_FEEDS_REPSONSE)) return

        Logd(TAG, "Received SP_APPS_QUERY_RESPONSE")
        if (!intent.hasExtra(ACTION_SP_APPS_QUERY_FEEDS_REPSONSE_FEEDS_EXTRA)) {
            Log.e(TAG, "Received invalid SP_APPS_QUERY_RESPONSE: Contains no extra")
            return
        }
        val feedUrls = intent.getStringArrayExtra(ACTION_SP_APPS_QUERY_FEEDS_REPSONSE_FEEDS_EXTRA)
        if (feedUrls == null) {
            Log.e(TAG, "Received invalid SP_APPS_QUERY_REPSONSE: extra was null")
            return
        }
        Logd(TAG, "Received feeds list: " + feedUrls.contentToString())
        ClientConfigurator.initialize(context)
        for (url in feedUrls) {
            val feed = Feed(url, null, "Unknown podcast")
            feed.items = mutableListOf()
            DBTasks.updateFeed(context, feed, false)
        }
        Toast.makeText(context, R.string.sp_apps_importing_feeds_msg, Toast.LENGTH_LONG).show()
        runOnce(context)
    }

    companion object {
        private const val TAG = "SPAReceiver"

        const val ACTION_SP_APPS_QUERY_FEEDS: String = "de.danoeh.antennapdsp.intent.SP_APPS_QUERY_FEEDS"
        private const val ACTION_SP_APPS_QUERY_FEEDS_REPSONSE = "de.danoeh.antennapdsp.intent.SP_APPS_QUERY_FEEDS_RESPONSE"
        private const val ACTION_SP_APPS_QUERY_FEEDS_REPSONSE_FEEDS_EXTRA = "feeds"
    }
}
