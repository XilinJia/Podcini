package ac.mdiq.podcini.playback.event

class PlaybackServiceEvent(@JvmField val action: Action) {
    enum class Action {
        SERVICE_STARTED,
        SERVICE_SHUT_DOWN
    }
}
