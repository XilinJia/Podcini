package ac.mdiq.podcini.core.feed.util

import android.util.Log
import ac.mdiq.podcini.core.preferences.PlaybackPreferences
import ac.mdiq.podcini.model.feed.FeedMedia
import ac.mdiq.podcini.model.feed.FeedPreferences
import ac.mdiq.podcini.model.playback.MediaType
import ac.mdiq.podcini.model.playback.Playable
import ac.mdiq.podcini.storage.preferences.UserPreferences

/**
 * Utility class to use the appropriate playback speed based on [PlaybackPreferences]
 */
object PlaybackSpeedUtils {
    private const val TAG = "PlaybackSpeedUtils"

    /**
     * Returns the currently configured playback speed for the specified media.
     */
    @JvmStatic
    fun getCurrentPlaybackSpeed(media: Playable?): Float {
        var playbackSpeed = FeedPreferences.SPEED_USE_GLOBAL
        var mediaType: MediaType? = null

        if (media != null) {
            mediaType = media.getMediaType()
            playbackSpeed = PlaybackPreferences.currentlyPlayingTemporaryPlaybackSpeed

            if (playbackSpeed == FeedPreferences.SPEED_USE_GLOBAL && media is FeedMedia) {
                val item = media.getItem()
                if (item != null) {
                    val feed = item.feed
                    if (feed?.preferences != null) {
                        playbackSpeed = feed.preferences!!.feedPlaybackSpeed
                    } else {
                        Log.d(TAG, "Can not get feed specific playback speed: $feed")
                    }
                }
            }
        }

        if (mediaType != null && playbackSpeed == FeedPreferences.SPEED_USE_GLOBAL) {
            playbackSpeed = UserPreferences.getPlaybackSpeed(mediaType)
        }

        return playbackSpeed
    }
}
