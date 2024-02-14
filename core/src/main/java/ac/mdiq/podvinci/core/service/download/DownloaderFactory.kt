package ac.mdiq.podvinci.core.service.download

import ac.mdiq.podvinci.net.download.serviceinterface.DownloadRequest

interface DownloaderFactory {
    fun create(request: DownloadRequest): Downloader?
}