package ac.mdiq.podcini.util.event

import ac.mdiq.podcini.storage.model.download.DownloadStatus

class EpisodeDownloadEvent(private val map: Map<String, DownloadStatus>) {
    val urls: Set<String>
        get() = map.keys
}
