package ac.mdiq.podcini.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import ac.mdiq.podcini.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.util.regex.Pattern

object NetworkUtils {
    private const val REGEX_PATTERN_IP_ADDRESS = "([0-9]{1,3}[\\.]){3}[0-9]{1,3}"

    private lateinit var context: Context

    @JvmStatic
    fun init(context: Context) {
        NetworkUtils.context = context
    }

    @JvmStatic
    val isAutoDownloadAllowed: Boolean
        get() {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = cm.activeNetworkInfo ?: return false
            return when (networkInfo.type) {
                ConnectivityManager.TYPE_WIFI -> {
                    if (UserPreferences.isEnableAutodownloadWifiFilter) isInAllowedWifiNetwork
                    else !isNetworkMetered
                }
                ConnectivityManager.TYPE_ETHERNET -> true
                else -> UserPreferences.isAllowMobileAutoDownload || !isNetworkRestricted
            }
        }

    @JvmStatic
    fun networkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val info = cm.activeNetworkInfo
        return info != null && info.isConnected
    }

    @JvmStatic
    val isEpisodeDownloadAllowed: Boolean
        get() = UserPreferences.isAllowMobileEpisodeDownload || !isNetworkRestricted

    @JvmStatic
    val isEpisodeHeadDownloadAllowed: Boolean
        // It is not an image but it is a similarly tiny request
        // that is probably not even considered a download by most users
        get() = isImageAllowed

    @JvmStatic
    val isImageAllowed: Boolean
        get() = UserPreferences.isAllowMobileImages || !isNetworkRestricted

    @JvmStatic
    val isStreamingAllowed: Boolean
        get() = UserPreferences.isAllowMobileStreaming || !isNetworkRestricted

    @JvmStatic
    val isFeedRefreshAllowed: Boolean
        get() = UserPreferences.isAllowMobileFeedRefresh || !isNetworkRestricted

    @JvmStatic
    val isNetworkRestricted: Boolean
        get() = isNetworkMetered || isNetworkCellular

    private val isNetworkMetered: Boolean
        get() {
            val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return connManager.isActiveNetworkMetered
        }

    @JvmStatic
    val isVpnOverWifi: Boolean
        get() {
            val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = connManager.getNetworkCapabilities(connManager.activeNetwork)
            return (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
        }

    private val isNetworkCellular: Boolean
        get() {
            val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//            if (Build.VERSION.SDK_INT >= 23) {
            val network = connManager.activeNetwork ?: return false // Nothing connected
            val info = connManager.getNetworkInfo(network) ?: return true // Better be safe than sorry
            val capabilities = connManager.getNetworkCapabilities(network) ?: return true // Better be safe than sorry
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
//            } else {
//                // if the default network is a VPN,
//                // this method will return the NetworkInfo for one of its underlying networks
//                val info = connManager.activeNetworkInfo ?: return false // Nothing connected
//                return info.type == ConnectivityManager.TYPE_MOBILE
//            }
        }

    private val isInAllowedWifiNetwork: Boolean
        get() {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val selectedNetworks = listOf(*UserPreferences.autodownloadSelectedNetworks)
            return selectedNetworks.contains(wm.connectionInfo.networkId.toString())
        }

    @JvmStatic
    fun wasDownloadBlocked(throwable: Throwable?): Boolean {
        val message = throwable!!.message
        if (message != null) {
            val pattern = Pattern.compile(REGEX_PATTERN_IP_ADDRESS)
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val ip = matcher.group()
                return ip.startsWith("127.") || ip.startsWith("0.")
            }
        }
        if (throwable.cause != null) return wasDownloadBlocked(throwable.cause)
        return false
    }

    suspend fun fetchHtmlSource(urlString: String): String = withContext(Dispatchers.IO) {
        val url = URL(urlString)
        val connection = url.openConnection()
        val inputStream = connection.getInputStream()
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))

        val stringBuilder = StringBuilder()
        var line = ""
        while (bufferedReader.readLine()?.also { line = it } != null) {
            stringBuilder.append(line)
        }

        bufferedReader.close()
        inputStream.close()

        stringBuilder.toString()
    }
}
