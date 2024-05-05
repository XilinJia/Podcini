package ac.mdiq.podcini.playback

import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.storage.model.playback.Playable

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
            val item = playable.item
            if (item != null && item.isNew) DBWriter.markItemPlayed(FeedItem.UNPLAYED, item.id)

            if (playable.startPosition >= 0 && playable.getPosition() > playable.startPosition)
                playable.playedDuration = (playable.playedDurationWhenStarted + playable.getPosition() - playable.startPosition)

            DBWriter.persistFeedMediaPlaybackInformation(playable)
        }
    }
}
