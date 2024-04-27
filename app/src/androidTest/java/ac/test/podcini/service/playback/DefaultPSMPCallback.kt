package de.test.podcini.service.playback

import ac.mdiq.podcini.storage.model.playback.MediaType
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.playback.base.MediaPlayerBase.PSMPCallback
import ac.mdiq.podcini.playback.base.MediaPlayerBase.PSMPInfo

open class DefaultPSMPCallback : PSMPCallback {
    override fun statusChanged(newInfo: PSMPInfo?) {
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
