package de.danoeh.antennapod.core.service.download

import de.danoeh.antennapod.net.download.serviceinterface.DownloadRequest

interface DownloaderFactory {
    fun create(request: DownloadRequest): Downloader?
}