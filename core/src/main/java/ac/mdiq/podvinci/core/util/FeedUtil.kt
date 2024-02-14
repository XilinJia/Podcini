package ac.mdiq.podvinci.core.util

import ac.mdiq.podvinci.model.feed.Feed
import ac.mdiq.podvinci.storage.preferences.UserPreferences

object FeedUtil {
    @JvmStatic
    fun shouldAutoDeleteItemsOnThatFeed(feed: Feed): Boolean {
        if (!UserPreferences.isAutoDelete) {
            return false
        }
        return !feed.isLocalFeed || UserPreferences.isAutoDeleteLocal
    }
}
