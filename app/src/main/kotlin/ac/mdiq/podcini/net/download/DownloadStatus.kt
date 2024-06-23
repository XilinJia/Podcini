package ac.mdiq.podcini.net.download

class DownloadStatus(@JvmField val state: Int, @JvmField val progress: Int) {
    companion object {
        const val STATE_QUEUED: Int = 0
        const val STATE_COMPLETED: Int = 1 // Both successful and not successful
        const val STATE_RUNNING: Int = 2
    }
}
