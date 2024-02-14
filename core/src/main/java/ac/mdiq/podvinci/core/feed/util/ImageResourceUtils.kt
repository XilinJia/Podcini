package ac.mdiq.podvinci.core.feed.util

import ac.mdiq.podvinci.model.feed.FeedItem
import ac.mdiq.podvinci.model.feed.FeedMedia
import ac.mdiq.podvinci.model.playback.Playable
import ac.mdiq.podvinci.storage.preferences.UserPreferences

/**
 * Utility class to use the appropriate image resource based on [UserPreferences].
 */
object ImageResourceUtils {
    /**
     * returns the image location, does prefer the episode cover if available and enabled in settings.
     */
    @JvmStatic
    fun getEpisodeListImageLocation(playable: Playable): String? {
        return if (UserPreferences.useEpisodeCoverSetting) {
            playable.getImageLocation()
        } else {
            getFallbackImageLocation(playable)
        }
    }

    /**
     * returns the image location, does prefer the episode cover if available and enabled in settings.
     */
    @JvmStatic
    fun getEpisodeListImageLocation(feedItem: FeedItem): String? {
        return if (UserPreferences.useEpisodeCoverSetting) {
            feedItem.imageLocation
        } else {
            getFallbackImageLocation(feedItem)
        }
    }

    @JvmStatic
    fun getFallbackImageLocation(playable: Playable): String? {
        if (playable is FeedMedia) {
            val item = playable.getItem()
            return if (item?.feed != null) {
                item.feed!!.imageUrl
            } else {
                null
            }
        } else {
            return playable.getImageLocation()
        }
    }

    @JvmStatic
    fun getFallbackImageLocation(feedItem: FeedItem): String? {
        return if (feedItem.feed != null) {
            feedItem.feed!!.imageUrl
        } else {
            null
        }
    }
}
