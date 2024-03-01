package ac.mdiq.podcini.util.event

class DownloadLogEvent private constructor() {
    override fun toString(): String {
        return "DownloadLogEvent"
    }

    companion object {
        @JvmStatic
        fun listUpdated(): DownloadLogEvent {
            return DownloadLogEvent()
        }
    }
}
