package ac.mdiq.podcini.net.download

import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.OnNetworkActiveListener
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.media3.common.util.UnstableApi

class ConnectionStateMonitor
    : ConnectivityManager.NetworkCallback(), OnNetworkActiveListener {

    val networkRequest: NetworkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()

    @UnstableApi override fun onNetworkActive() {
        Logd(TAG, "ConnectionStateMonitor::onNetworkActive network connection changed")
        NetworkConnectionChangeHandler.networkChangedDetected()
    }

    fun enable(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerNetworkCallback(networkRequest, this)
        connectivityManager.addDefaultNetworkActiveListener(this)
    }

    fun disable(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.unregisterNetworkCallback(this)
        connectivityManager.removeDefaultNetworkActiveListener(this)
    }

    companion object {
        private val TAG: String = ConnectionStateMonitor::class.simpleName ?: "Anonymous"
    }
}