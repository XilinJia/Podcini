package ac.mdiq.podcini.util.event

class DownloadLogEvent private constructor() {
    override fun toString(): String {
        return "DownloadLogEvent"
    }

    companion object {
        @JvmStatic
        fun listUpdated(): ac.mdiq.podcini.util.event.DownloadLogEvent {
            return ac.mdiq.podcini.util.event.DownloadLogEvent()
        }
    }
}
