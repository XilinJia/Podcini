package de.danoeh.antennapod.core.util

import androidx.media3.common.util.UnstableApi
import de.danoeh.antennapod.core.preferences.PlaybackPreferences
import de.danoeh.antennapod.core.service.playback.PlaybackService
import de.danoeh.antennapod.model.feed.FeedMedia

@UnstableApi
object PlaybackStatus {
    /**
     * Reads playback preferences to determine whether this FeedMedia object is
     * currently being played and the current player status is playing.
     */
    @JvmStatic
    fun isCurrentlyPlaying(media: FeedMedia?): Boolean {
        return (isPlaying(media) && PlaybackService.isRunning
                && ((PlaybackPreferences.currentPlayerStatus == PlaybackPreferences.PLAYER_STATUS_PLAYING)))
    }

    @JvmStatic
    fun isPlaying(media: FeedMedia?): Boolean {
        return PlaybackPreferences.currentlyPlayingMediaType == FeedMedia.PLAYABLE_TYPE_FEEDMEDIA.toLong() && media != null && PlaybackPreferences.currentlyPlayingFeedMediaId == media.id
    }
}
