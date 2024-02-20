package ac.mdiq.podcini.core.service.playback

import ac.mdiq.podcini.model.feed.FeedMedia
import ac.mdiq.podcini.model.feed.VolumeAdaptionSetting
import ac.mdiq.podcini.playback.base.PlaybackServiceMediaPlayer
import ac.mdiq.podcini.playback.base.PlayerStatus

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
