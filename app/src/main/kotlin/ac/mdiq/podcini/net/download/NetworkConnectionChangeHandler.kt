package ac.mdiq.podcini.net.download

import android.content.Context
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.net.utils.NetworkUtils.isAutoDownloadAllowed
import ac.mdiq.podcini.net.utils.NetworkUtils.isNetworkRestricted
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.storage.algorithms.AutoDownloads.autodownloadEpisodeMedia
import ac.mdiq.podcini.util.Logd

@UnstableApi
object NetworkConnectionChangeHandler {
    private val TAG: String = NetworkConnectionChangeHandler::class.simpleName ?: "Anonymous"
    private lateinit var context: Context

    @JvmStatic
    fun init(context: Context) {
        NetworkConnectionChangeHandler.context = context
    }

    @JvmStatic
    fun networkChangedDetected() {
        if (isAutoDownloadAllowed) {
            Logd(TAG, "auto-dl network available, starting auto-download")
            autodownloadEpisodeMedia(context)
        } else { // if new network is Wi-Fi, finish ongoing downloads,
            // otherwise cancel all downloads
            if (isNetworkRestricted) {
                Log.i(TAG, "Device is no longer connected to Wi-Fi. Cancelling ongoing downloads")
                DownloadServiceInterface.get()?.cancelAll(context)
            }
        }
    }
}
