package ac.mdiq.podcini.core.util.download

import android.content.Context
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.core.storage.DBTasks
import ac.mdiq.podcini.core.util.NetworkUtils.isAutoDownloadAllowed
import ac.mdiq.podcini.core.util.NetworkUtils.isNetworkRestricted
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface

@UnstableApi
object NetworkConnectionChangeHandler {
    private const val TAG = "NetConnectChangeHandler"
    private lateinit var context: Context

    @JvmStatic
    fun init(context: Context) {
        NetworkConnectionChangeHandler.context = context
    }

    @JvmStatic
    fun networkChangedDetected() {
        if (isAutoDownloadAllowed) {
            Log.d(TAG, "auto-dl network available, starting auto-download")
            DBTasks.autodownloadUndownloadedItems(context)
        } else { // if new network is Wi-Fi, finish ongoing downloads,
            // otherwise cancel all downloads
            if (isNetworkRestricted) {
                Log.i(TAG, "Device is no longer connected to Wi-Fi. Cancelling ongoing downloads")
                DownloadServiceInterface.get()?.cancelAll(context)
            }
        }
    }
}
