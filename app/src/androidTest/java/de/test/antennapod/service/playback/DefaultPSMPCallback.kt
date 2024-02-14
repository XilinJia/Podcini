package de.test.antennapod.service.playback

import de.danoeh.antennapod.model.playback.MediaType
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer.PSMPCallback
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer.PSMPInfo

open class DefaultPSMPCallback : PSMPCallback {
    override fun statusChanged(newInfo: PSMPInfo?) {
    }

    override fun shouldStop() {
    }

    override fun onMediaChanged(reloadUI: Boolean) {
    }

    override fun onPostPlayback(media: Playable, ended: Boolean, skipped: Boolean, playingNext: Boolean) {
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