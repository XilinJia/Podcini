package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.playback.base.MediaPlayerBase.MediaPlayerInfo
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.MediaType

interface MediaPlayerCallback {
    fun statusChanged(newInfo: MediaPlayerInfo?)

    fun onMediaChanged(reloadUI: Boolean)

    fun onPostPlayback(playable: EpisodeMedia?, ended: Boolean, skipped: Boolean, playingNext: Boolean)

    fun onPlaybackStart(playable: EpisodeMedia, position: Int)

    fun onPlaybackPause(playable: EpisodeMedia?, position: Int)

    fun getNextInQueue(currentMedia: EpisodeMedia?): EpisodeMedia?

    fun findMedia(url: String): EpisodeMedia?

    fun onPlaybackEnded(mediaType: MediaType?, stopPlaying: Boolean)

    fun ensureMediaInfoLoaded(media: EpisodeMedia)
}
