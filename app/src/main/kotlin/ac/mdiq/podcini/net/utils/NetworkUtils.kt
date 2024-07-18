package ac.mdiq.podcini.net.utils

import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
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

    var isAllowMobileStreaming: Boolean
        get() = isAllowMobileFor("streaming")
        set(allow) {
            setAllowMobileFor("streaming", allow)
        }

    var isAllowMobileAutoDownload: Boolean
        get() = isAllowMobileFor("auto_download")
        set(allow) {
            setAllowMobileFor("auto_download", allow)
        }

    fun isAllowMobileFor(type: String): Boolean {
        val defaultValue = HashSet<String>()
        defaultValue.add("images")
        val allowed = appPrefs.getStringSet(UserPreferences.Prefs.prefMobileUpdateTypes.name, defaultValue)
        return allowed!!.contains(type)
    }

    fun setAllowMobileFor(type: String, allow: Boolean) {
        val defaultValue = HashSet<String>()
        defaultValue.add("images")
        val getValueStringSet = appPrefs.getStringSet(UserPreferences.Prefs.prefMobileUpdateTypes.name, defaultValue)
        val allowed: MutableSet<String> = HashSet(getValueStringSet!!)
        if (allow) allowed.add(type)
        else allowed.remove(type)

        appPrefs.edit().putStringSet(UserPreferences.Prefs.prefMobileUpdateTypes.name, allowed).apply()
    }

    val isEnableAutodownloadWifiFilter: Boolean
        get() = Build.VERSION.SDK_INT < 29 && appPrefs.getBoolean(UserPreferences.Prefs.prefEnableAutoDownloadWifiFilter.name, false)

    @JvmStatic
    val isAutoDownloadAllowed: Boolean
        get() {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = cm.activeNetworkInfo ?: return false
            return when (networkInfo.type) {
                ConnectivityManager.TYPE_WIFI -> {
                    if (isEnableAutodownloadWifiFilter) isInAllowedWifiNetwork
                    else !isNetworkMetered
                }
                ConnectivityManager.TYPE_ETHERNET -> true
                else -> isAllowMobileAutoDownload || !isNetworkRestricted
            }
        }

    @JvmStatic
    fun networkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val info = cm.activeNetworkInfo
        return info != null && info.isConnected
    }

    var isAllowMobileFeedRefresh: Boolean
        get() = isAllowMobileFor("feed_refresh")
        set(allow) {
            setAllowMobileFor("feed_refresh", allow)
        }

    var isAllowMobileEpisodeDownload: Boolean
        get() = isAllowMobileFor("episode_download")
        set(allow) {
            setAllowMobileFor("episode_download", allow)
        }

    @JvmStatic
    val isEpisodeDownloadAllowed: Boolean
        get() = isAllowMobileEpisodeDownload || !isNetworkRestricted

    @JvmStatic
    val isEpisodeHeadDownloadAllowed: Boolean
        // It is not an image but it is a similarly tiny request
        // that is probably not even considered a download by most users
        get() = isImageAllowed

    var isAllowMobileImages: Boolean
        get() = isAllowMobileFor("images")
        set(allow) {
            setAllowMobileFor("images", allow)
        }

    @JvmStatic
    val isImageAllowed: Boolean
        get() = isAllowMobileImages || !isNetworkRestricted

    @JvmStatic
    val isStreamingAllowed: Boolean
        get() = isAllowMobileStreaming || !isNetworkRestricted

    @JvmStatic
    val isFeedRefreshAllowed: Boolean
        get() = isAllowMobileFeedRefresh || !isNetworkRestricted

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

    val autodownloadSelectedNetworks: Array<String>
        get() {
            val selectedNetWorks = appPrefs.getString(UserPreferences.Prefs.prefAutodownloadSelectedNetworks.name, "")
            return selectedNetWorks?.split(",")?.toTypedArray() ?: arrayOf()
        }

    private val isInAllowedWifiNetwork: Boolean
        get() {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val selectedNetworks = listOf(*autodownloadSelectedNetworks)
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
