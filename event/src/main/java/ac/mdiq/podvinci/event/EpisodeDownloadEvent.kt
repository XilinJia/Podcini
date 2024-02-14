package ac.mdiq.podvinci.event

import ac.mdiq.podvinci.model.download.DownloadStatus

class EpisodeDownloadEvent(private val map: Map<String, DownloadStatus>) {
    val urls: Set<String>
        get() = map.keys
}
