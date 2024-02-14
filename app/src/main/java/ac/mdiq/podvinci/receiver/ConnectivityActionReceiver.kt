package ac.mdiq.podvinci.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.text.TextUtils
import android.util.Log
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podvinci.core.ClientConfigurator
import ac.mdiq.podvinci.core.util.download.NetworkConnectionChangeHandler.networkChangedDetected

class ConnectivityActionReceiver : BroadcastReceiver() {
    @UnstableApi override fun onReceive(context: Context, intent: Intent) {
        if (TextUtils.equals(intent.action, ConnectivityManager.CONNECTIVITY_ACTION)) {
            Log.d(TAG, "Received intent")

            ClientConfigurator.initialize(context)
            networkChangedDetected()
        }
    }

    companion object {
        private const val TAG = "ConnectivityActionRecvr"
    }
}
