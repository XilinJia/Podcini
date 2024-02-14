package de.danoeh.antennapod.core.service.playback

import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer
import de.danoeh.antennapod.playback.base.PlayerStatus

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
