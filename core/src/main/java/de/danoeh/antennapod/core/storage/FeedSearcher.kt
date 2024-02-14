package de.danoeh.antennapod.core.storage

import de.danoeh.antennapod.core.storage.DBTasks.searchFeedItems
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import java.util.concurrent.ExecutionException

/**
 * Performs search on Feeds and FeedItems.
 */
object FeedSearcher {
    fun searchFeedItems(query: String?, selectedFeed: Long): List<FeedItem> {
        try {
            val itemSearchTask = searchFeedItems(selectedFeed, query)
            itemSearchTask.run()
            return itemSearchTask.get()
        } catch (e: ExecutionException) {
            e.printStackTrace()
            return emptyList()
        } catch (e: InterruptedException) {
            e.printStackTrace()
            return emptyList()
        }
    }

    fun searchFeeds(query: String?): List<Feed> {
        try {
            val feedSearchTask = DBTasks.searchFeeds(query)
            feedSearchTask.run()
            return feedSearchTask.get()
        } catch (e: ExecutionException) {
            e.printStackTrace()
            return emptyList()
        } catch (e: InterruptedException) {
            e.printStackTrace()
            return emptyList()
        }
    }
}
