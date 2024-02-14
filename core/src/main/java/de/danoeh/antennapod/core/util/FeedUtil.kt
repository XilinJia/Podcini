package de.danoeh.antennapod.core.util

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.storage.preferences.UserPreferences

object FeedUtil {
    @JvmStatic
    fun shouldAutoDeleteItemsOnThatFeed(feed: Feed): Boolean {
        if (!UserPreferences.isAutoDelete) {
            return false
        }
        return !feed.isLocalFeed || UserPreferences.isAutoDeleteLocal
    }
}
