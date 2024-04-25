package ac.mdiq.podcini.net.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.OnNetworkActiveListener
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.media3.common.util.UnstableApi

class ConnectionStateMonitor

    : ConnectivityManager.NetworkCallback(), OnNetworkActiveListener {
    val networkRequest: NetworkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()

    @UnstableApi override fun onNetworkActive() {
        Log.d(TAG, "ConnectionStateMonitor::onNetworkActive network connection changed")
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
        private const val TAG = "ConnectionStateMonitor"
    }
}