package ac.mdiq.podcini.core.util

import ac.mdiq.podcini.model.feed.Feed
import ac.mdiq.podcini.storage.preferences.UserPreferences

object FeedUtil {
    @JvmStatic
    fun shouldAutoDeleteItemsOnThatFeed(feed: Feed): Boolean {
        if (!UserPreferences.isAutoDelete) {
            return false
        }
        return !feed.isLocalFeed || UserPreferences.isAutoDeleteLocal
    }
}
