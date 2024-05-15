package ac.mdiq.podcini.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.text.TextUtils
import android.util.Log
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.util.config.ClientConfigurator
import ac.mdiq.podcini.net.download.NetworkConnectionChangeHandler.networkChangedDetected
import ac.mdiq.podcini.util.Logd

class ConnectivityActionReceiver : BroadcastReceiver() {
    @UnstableApi override fun onReceive(context: Context, intent: Intent) {
        if (TextUtils.equals(intent.action, ConnectivityManager.CONNECTIVITY_ACTION)) {
            Logd(TAG, "Received intent")

            ClientConfigurator.initialize(context)
            networkChangedDetected()
        }
    }

    companion object {
        private const val TAG = "ConnectivityActionRecvr"
    }
}
