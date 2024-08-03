package ac.mdiq.podcini.receiver

import ac.mdiq.podcini.net.download.NetworkConnectionChangeHandler.networkChangedDetected
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.config.ClientConfigurator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.util.Log
import androidx.media3.common.util.UnstableApi

class ConnectivityActionReceiver : BroadcastReceiver() {
    @UnstableApi override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive called with action: ${intent.action}")
        if (intent.action == ConnectivityManager.CONNECTIVITY_ACTION) {
            Logd(TAG, "Received intent")

            ClientConfigurator.initialize(context)
            networkChangedDetected()
        }
    }

    companion object {
        private val TAG: String = ConnectivityActionReceiver::class.simpleName ?: "Anonymous"
    }
}
