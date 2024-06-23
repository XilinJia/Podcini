package ac.mdiq.podcini.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.util.config.ClientConfigurator
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.util.Logd

/**
 * Refreshes all feeds when it receives an intent
 */
class FeedUpdateReceiver : BroadcastReceiver() {
    @UnstableApi
    override fun onReceive(context: Context, intent: Intent) {
        Logd(TAG, "Received intent")
        ClientConfigurator.initialize(context)

        FeedUpdateManager.runOnce(context)
    }

    companion object {
        private val TAG: String = FeedUpdateReceiver::class.simpleName ?: "Anonymous"
    }
}
