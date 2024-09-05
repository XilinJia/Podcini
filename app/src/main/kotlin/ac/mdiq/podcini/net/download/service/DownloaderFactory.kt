package ac.mdiq.podcini.net.download.service

interface DownloaderFactory {
    fun create(request: DownloadRequest): Downloader?
}