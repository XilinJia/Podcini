package de.test.antennapod.service.playback

import de.danoeh.antennapod.model.playback.MediaType
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer.PSMPCallback
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer.PSMPInfo

class CancelablePSMPCallback(private val originalCallback: PSMPCallback) : PSMPCallback {
    private var isCancelled = false

    fun cancel() {
        isCancelled = true
    }

    override fun statusChanged(newInfo: PSMPInfo?) {
        if (isCancelled) {
            return
        }
        originalCallback.statusChanged(newInfo)
    }

    override fun shouldStop() {
        if (isCancelled) {
            return
        }
        originalCallback.shouldStop()
    }

    override fun onMediaChanged(reloadUI: Boolean) {
        if (isCancelled) {
            return
        }
        originalCallback.onMediaChanged(reloadUI)
    }

    override fun onPostPlayback(media: Playable, ended: Boolean, skipped: Boolean, playingNext: Boolean) {
        if (isCancelled) {
            return
        }
        originalCallback.onPostPlayback(media, ended, skipped, playingNext)
    }

    override fun onPlaybackStart(playable: Playable, position: Int) {
        if (isCancelled) {
            return
        }
        originalCallback.onPlaybackStart(playable, position)
    }

    override fun onPlaybackPause(playable: Playable?, position: Int) {
        if (isCancelled) {
            return
        }
        originalCallback.onPlaybackPause(playable, position)
    }

    override fun getNextInQueue(currentMedia: Playable?): Playable? {
        if (isCancelled) {
            return null
        }
        return originalCallback.getNextInQueue(currentMedia)
    }

    override fun findMedia(url: String): Playable? {
        if (isCancelled) {
            return null
        }
        return originalCallback.findMedia(url)
    }

    override fun onPlaybackEnded(mediaType: MediaType?, stopPlaying: Boolean) {
        if (isCancelled) {
            return
        }
        originalCallback.onPlaybackEnded(mediaType, stopPlaying)
    }

    override fun ensureMediaInfoLoaded(media: Playable) {
        if (isCancelled) {
            return
        }
        originalCallback.ensureMediaInfoLoaded(media)
    }
}