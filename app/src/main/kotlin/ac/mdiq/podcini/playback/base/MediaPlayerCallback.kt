package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.playback.base.MediaPlayerBase.MediaPlayerInfo
import ac.mdiq.podcini.storage.model.Episode

import ac.mdiq.podcini.storage.model.MediaType

interface MediaPlayerCallback {
    fun statusChanged(newInfo: MediaPlayerInfo?)

    fun onMediaChanged(reloadUI: Boolean)

    fun onPostPlayback(playable: Episode?, ended: Boolean, skipped: Boolean, playingNext: Boolean)

    fun onPlaybackStart(playable: Episode, position: Int)

    fun onPlaybackPause(playable: Episode?, position: Int)

    fun getNextInQueue(currentMedia: Episode?): Episode?

    fun findMedia(url: String): Episode?

    fun onPlaybackEnded(mediaType: MediaType?, stopPlaying: Boolean)

    fun ensureMediaInfoLoaded(media: Episode)
}
