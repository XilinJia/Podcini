package de.danoeh.antennapod.event

import de.danoeh.antennapod.model.download.DownloadStatus

class EpisodeDownloadEvent(private val map: Map<String, DownloadStatus>) {
    val urls: Set<String>
        get() = map.keys
}
