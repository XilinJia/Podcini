package ac.mdiq.podcini.net.download.service

import ac.mdiq.podcini.net.download.serviceinterface.DownloadRequest

interface DownloaderFactory {
    fun create(request: DownloadRequest): Downloader?
}