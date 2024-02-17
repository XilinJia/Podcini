package ac.mdiq.podvinci.core.util.playback

import androidx.media3.common.util.UnstableApi
import ac.mdiq.podvinci.core.storage.DBWriter
import ac.mdiq.podvinci.model.feed.FeedItem
import ac.mdiq.podvinci.model.feed.FeedMedia
import ac.mdiq.podvinci.model.playback.Playable

/**
 * Provides utility methods for Playable objects.
 */
object PlayableUtils {
    /**
     * Saves the current position of this object.
     *
     * @param newPosition  new playback position in ms
     * @param timestamp  current time in ms
     */
    @UnstableApi @JvmStatic
    fun saveCurrentPosition(playable: Playable, newPosition: Int, timestamp: Long) {
        playable.setPosition(newPosition)
        playable.setLastPlayedTime(timestamp)

        if (playable is FeedMedia) {
            val item = playable.getItem()
            if (item != null && item.isNew) {
                DBWriter.markItemPlayed(FeedItem.UNPLAYED, item.id)
            }
            if (playable.startPosition >= 0 && playable.getPosition() > playable.startPosition) {
                playable.playedDuration = (playable.playedDurationWhenStarted
                        + playable.getPosition() - playable.startPosition)
            }
            DBWriter.setFeedMediaPlaybackInformation(playable)
        }
    }
}