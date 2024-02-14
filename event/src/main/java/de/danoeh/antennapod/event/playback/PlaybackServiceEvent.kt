package de.danoeh.antennapod.event.playback

class PlaybackServiceEvent(@JvmField val action: Action) {
    enum class Action {
        SERVICE_STARTED,
        SERVICE_SHUT_DOWN
    }
}
