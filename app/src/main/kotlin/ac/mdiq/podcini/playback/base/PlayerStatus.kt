package ac.mdiq.podcini.playback.base

enum class PlayerStatus(private val statusValue: Int) {
    ERROR(-1),
    INDETERMINATE(0),  // player is currently changing its state, listeners should wait until the state is left
    STOPPED(5),
    INITIALIZING(9),  // playback service is loading the EpisodeMedia's metadata
    INITIALIZED(10), // playback service was started, data source of media player was set
    PREPARING(19),
    PREPARED(20),
    SEEKING(29),
    PAUSED(30),
    FALLBACK(35),
    PLAYING(40);

    fun isAtLeast(other: PlayerStatus?): Boolean {
        return other == null || this.statusValue >= other.statusValue
    }

    companion object {
        private val fromOrdinalLookup = entries.toTypedArray()
    }
}
