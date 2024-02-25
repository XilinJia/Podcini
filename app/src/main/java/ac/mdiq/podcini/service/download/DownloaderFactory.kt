package ac.mdiq.podcini.service.download

import ac.mdiq.podcini.net.download.serviceinterface.DownloadRequest

interface DownloaderFactory {
    fun create(request: DownloadRequest): ac.mdiq.podcini.service.download.Downloader?
}