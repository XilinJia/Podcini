package de.test.podcini.service.playback

import ac.mdiq.podcini.storage.model.playback.MediaType
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.playback.base.MediaPlayerBase.MediaPlayerCallback
import ac.mdiq.podcini.playback.base.MediaPlayerBase.MediaPlayerInfo

class CancelableMediaPlayerCallback(private val originalCallback: MediaPlayerCallback) : MediaPlayerCallback {
    private var isCancelled = false

    fun cancel() {
        isCancelled = true
    }

    override fun statusChanged(newInfo: MediaPlayerInfo?) {
        if (isCancelled) {
            return
        }
        originalCallback.statusChanged(newInfo)
    }

    override fun shouldStop() {
        if (isCancelled) return

//        originalCallback.shouldStop()
    }

    override fun onMediaChanged(reloadUI: Boolean) {
        if (isCancelled) {
            return
        }
        originalCallback.onMediaChanged(reloadUI)
    }

    override fun onPostPlayback(media: Playable?, ended: Boolean, skipped: Boolean, playingNext: Boolean) {
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
