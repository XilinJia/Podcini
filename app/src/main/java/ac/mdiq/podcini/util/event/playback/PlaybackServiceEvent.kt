package ac.mdiq.podcini.util.event.playback

class PlaybackServiceEvent(@JvmField val action: Action) {
    enum class Action {
        SERVICE_STARTED,
        SERVICE_SHUT_DOWN,
//        SERVICE_RESTARTED
    }
}
