package ac.mdiq.podcini.net.download

class DownloadStatus(
        @JvmField val state: Int,
        @JvmField val progress: Int) {

    enum class State {
        UNKNOWN,
        QUEUED,
        RUNNING,
        COMPLETED     // Both successful and not successful
    }
}
