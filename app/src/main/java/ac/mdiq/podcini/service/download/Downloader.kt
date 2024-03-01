package ac.mdiq.podcini.service.download

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.serviceinterface.DownloadRequest
import ac.mdiq.podcini.storage.model.download.DownloadResult
import ac.mdiq.podcini.util.config.ClientConfig
import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import java.util.*
import java.util.concurrent.Callable
import kotlin.concurrent.Volatile

/**
 * Downloads files
 */
abstract class Downloader(val downloadRequest: DownloadRequest) : Callable<Downloader> {
    @Volatile
    var isFinished: Boolean = false
        private set

    @JvmField
    @Volatile
    var cancelled: Boolean
    @JvmField
    var permanentRedirectUrl: String? = null

    @JvmField
    val result: DownloadResult

    init {
        this.downloadRequest.setStatusMsg(R.string.download_pending)
        this.cancelled = false
        this.result = DownloadResult(0, this.downloadRequest.title?:"", this.downloadRequest.feedfileId, this.downloadRequest.feedfileType,
            false, null, Date(), "")
    }

    protected abstract fun download()

    override fun call(): Downloader {
        val wifiManager = ClientConfig.applicationCallbacks?.getApplicationInstance()?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        var wifiLock: WifiLock? = null
        if (wifiManager != null) {
            wifiLock = wifiManager.createWifiLock(TAG)
            wifiLock.acquire()
        }

        download()

        wifiLock?.release()

        isFinished = true
        return this
    }

    fun cancel() {
        cancelled = true
    }

    companion object {
        private const val TAG = "Downloader"
    }
}