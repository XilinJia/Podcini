package ac.mdiq.podcini.event

import ac.mdiq.podcini.model.download.DownloadStatus

class EpisodeDownloadEvent(private val map: Map<String, DownloadStatus>) {
    val urls: Set<String>
        get() = map.keys
}
