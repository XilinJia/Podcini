package ac.mdiq.podcini.core.service.download

import ac.mdiq.podcini.net.download.serviceinterface.DownloadRequest

interface DownloaderFactory {
    fun create(request: DownloadRequest): Downloader?
}