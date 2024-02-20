package ac.mdiq.podcini.event.playback

class PlaybackHistoryEvent private constructor() {
    override fun toString(): String {
        return "PlaybackHistoryEvent"
    }

    companion object {
        @JvmStatic
        fun listUpdated(): PlaybackHistoryEvent {
            return PlaybackHistoryEvent()
        }
    }
}
