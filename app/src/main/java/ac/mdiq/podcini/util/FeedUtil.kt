package ac.mdiq.podcini.util

import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.preferences.UserPreferences

object FeedUtil {
    @JvmStatic
    fun shouldAutoDeleteItemsOnThatFeed(feed: Feed): Boolean {
        if (!UserPreferences.isAutoDelete) return false

        return !feed.isLocalFeed || UserPreferences.isAutoDeleteLocal
    }
}
