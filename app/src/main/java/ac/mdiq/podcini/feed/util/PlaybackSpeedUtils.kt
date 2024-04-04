package ac.mdiq.podcini.feed.util

import android.util.Log
import ac.mdiq.podcini.preferences.PlaybackPreferences
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.storage.model.feed.FeedPreferences
import ac.mdiq.podcini.storage.model.playback.MediaType
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.preferences.UserPreferences

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
                val item = media.item
                if (item != null) {
                    val feed = item.feed
                    if (feed?.preferences != null) {
                        playbackSpeed = feed.preferences!!.feedPlaybackSpeed
                        Log.d(TAG, "using feed speed $playbackSpeed")
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
