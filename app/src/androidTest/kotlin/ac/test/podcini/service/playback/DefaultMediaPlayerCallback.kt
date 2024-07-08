package de.test.podcini.service.playback

import ac.mdiq.podcini.storage.model.MediaType
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.playback.base.MediaPlayerBase.MediaPlayerInfo
import ac.mdiq.podcini.playback.base.MediaPlayerCallback

open class DefaultMediaPlayerCallback : MediaPlayerCallback {
    override fun statusChanged(newInfo: MediaPlayerInfo?) {
    }

    override fun shouldStop() {
    }

    override fun onMediaChanged(reloadUI: Boolean) {
    }

    override fun onPostPlayback(media: Playable?, ended: Boolean, skipped: Boolean, playingNext: Boolean) {
    }

    override fun onPlaybackStart(playable: Playable, position: Int) {
    }

    override fun onPlaybackPause(playable: Playable?, position: Int) {
    }

    override fun getNextInQueue(currentMedia: Playable?): Playable? {
        return null
    }

    override fun findMedia(url: String): Playable? {
        return null
    }

    override fun onPlaybackEnded(mediaType: MediaType?, stopPlaying: Boolean) {
    }

    override fun ensureMediaInfoLoaded(media: Playable) {
    }
}
