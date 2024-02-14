package de.danoeh.antennapod.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.media3.common.util.UnstableApi
import de.danoeh.antennapod.core.ClientConfigurator
import de.danoeh.antennapod.core.util.download.FeedUpdateManager

/**
 * Refreshes all feeds when it receives an intent
 */
class FeedUpdateReceiver : BroadcastReceiver() {
    @UnstableApi
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received intent")
        ClientConfigurator.initialize(context)

        FeedUpdateManager.runOnce(context)
    }

    companion object {
        private const val TAG = "FeedUpdateReceiver"
    }
}
