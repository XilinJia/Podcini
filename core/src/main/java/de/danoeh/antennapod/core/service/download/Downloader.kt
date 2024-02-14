package de.danoeh.antennapod.core.service.download

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import de.danoeh.antennapod.core.ClientConfig
import de.danoeh.antennapod.core.R
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.net.download.serviceinterface.DownloadRequest
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