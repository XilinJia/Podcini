package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.playback.base.MediaPlayerBase.MediaPlayerInfo
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.storage.utils.MediaType

interface MediaPlayerCallback {
    fun statusChanged(newInfo: MediaPlayerInfo?)

    // TODO: not used
    fun shouldStop() {}

    fun onMediaChanged(reloadUI: Boolean)

    fun onPostPlayback(playable: Playable?, ended: Boolean, skipped: Boolean, playingNext: Boolean)

    fun onPlaybackStart(playable: Playable, position: Int)

    fun onPlaybackPause(playable: Playable?, position: Int)

    fun getNextInQueue(currentMedia: Playable?): Playable?

    fun findMedia(url: String): Playable?

    fun onPlaybackEnded(mediaType: MediaType?, stopPlaying: Boolean)

    fun ensureMediaInfoLoaded(media: Playable)
}
