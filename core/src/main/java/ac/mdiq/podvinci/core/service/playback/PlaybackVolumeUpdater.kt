package ac.mdiq.podvinci.core.service.playback

import ac.mdiq.podvinci.model.feed.FeedMedia
import ac.mdiq.podvinci.model.feed.VolumeAdaptionSetting
import ac.mdiq.podvinci.playback.base.PlaybackServiceMediaPlayer
import ac.mdiq.podvinci.playback.base.PlayerStatus

internal class PlaybackVolumeUpdater {
    fun updateVolumeIfNecessary(mediaPlayer: PlaybackServiceMediaPlayer, feedId: Long,
                                volumeAdaptionSetting: VolumeAdaptionSetting
    ) {
        val playable = mediaPlayer.getPlayable()

        if (playable is FeedMedia) {
            updateFeedMediaVolumeIfNecessary(mediaPlayer, feedId, volumeAdaptionSetting, playable)
        }
    }

    private fun updateFeedMediaVolumeIfNecessary(mediaPlayer: PlaybackServiceMediaPlayer, feedId: Long,
                                                 volumeAdaptionSetting: VolumeAdaptionSetting, feedMedia: FeedMedia
    ) {
        if (feedMedia.getItem()?.feed?.id == feedId) {
            val preferences = feedMedia.getItem()!!.feed!!.preferences
            if (preferences != null) preferences.volumeAdaptionSetting = volumeAdaptionSetting

            if (mediaPlayer.playerStatus == PlayerStatus.PLAYING) {
                forceUpdateVolume(mediaPlayer)
            }
        }
    }

    private fun forceUpdateVolume(mediaPlayer: PlaybackServiceMediaPlayer) {
        mediaPlayer.pause(false, false)
        mediaPlayer.resume()
    }
}
