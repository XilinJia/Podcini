package ac.mdiq.podcini.core.service.download

import android.util.Log
import android.webkit.URLUtil
import ac.mdiq.podcini.net.download.serviceinterface.DownloadRequest

class DefaultDownloaderFactory : DownloaderFactory {
    override fun create(request: DownloadRequest): Downloader? {
        if (!URLUtil.isHttpUrl(request.source) && !URLUtil.isHttpsUrl(request.source)) {
            Log.e(TAG, "Could not find appropriate downloader for " + request.source)
            return null
        }
        return HttpDownloader(request)
    }

    companion object {
        private const val TAG = "DefaultDwnldrFactory"
    }
}