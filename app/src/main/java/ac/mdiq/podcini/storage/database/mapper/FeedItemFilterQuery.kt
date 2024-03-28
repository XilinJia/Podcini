package ac.mdiq.podcini.storage.database.mapper

import ac.mdiq.podcini.storage.model.feed.FeedItemFilter
import ac.mdiq.podcini.storage.database.PodDBAdapter

object FeedItemFilterQuery {
    /**
     * Express the filter using an SQL boolean statement that can be inserted into an SQL WHERE clause
     * to yield output filtered according to the rules of this filter.
     *
     * @return An SQL boolean statement that matches the desired items,
     * empty string if there is nothing to filter
     */
    @JvmStatic
    fun generateFrom(filter: FeedItemFilter): String {
        // The keys used within this method, but explicitly combined with their table
        val keyRead = PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + PodDBAdapter.KEY_READ
        val keyPosition = PodDBAdapter.TABLE_NAME_FEED_MEDIA + "." + PodDBAdapter.KEY_POSITION
        val keyDownloaded = PodDBAdapter.TABLE_NAME_FEED_MEDIA + "." + PodDBAdapter.KEY_DOWNLOADED
        val keyMediaId = PodDBAdapter.TABLE_NAME_FEED_MEDIA + "." + PodDBAdapter.KEY_ID
        val keyItemId = PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + PodDBAdapter.KEY_ID
        val keyFeedItem = PodDBAdapter.KEY_FEEDITEM
        val tableQueue = PodDBAdapter.TABLE_NAME_QUEUE
        val tableFavorites = PodDBAdapter.TABLE_NAME_FAVORITES

        val statements: MutableList<String> = ArrayList()
        when {
            filter.showPlayed -> {
                statements.add("$keyRead = 1 ")
            }
            filter.showUnplayed -> {
                statements.add(" NOT $keyRead = 1 ") // Match "New" items (read = -1) as well
            }
            filter.showNew -> {
                statements.add("$keyRead = -1 ")
            }
        }
        when {
            filter.showPaused -> {
                statements.add(" ($keyPosition NOT NULL AND $keyPosition > 0 ) ")
            }
            filter.showNotPaused -> {
                statements.add(" ($keyPosition IS NULL OR $keyPosition = 0 ) ")
            }
        }
        when {
            filter.showQueued -> {
                statements.add("$keyItemId IN (SELECT $keyFeedItem FROM $tableQueue) ")
            }
            filter.showNotQueued -> {
                statements.add("$keyItemId NOT IN (SELECT $keyFeedItem FROM $tableQueue) ")
            }
        }
        when {
            filter.showDownloaded -> {
                statements.add("$keyDownloaded = 1 ")
            }
            filter.showNotDownloaded -> {
                statements.add("$keyDownloaded = 0 ")
            }
        }
        when {
            filter.showHasMedia -> {
                statements.add("$keyMediaId NOT NULL ")
            }
            filter.showNoMedia -> {
                statements.add("$keyMediaId IS NULL ")
            }
        }
        when {
            filter.showIsFavorite -> {
                statements.add("$keyItemId IN (SELECT $keyFeedItem FROM $tableFavorites) ")
            }
            filter.showNotFavorite -> {
                statements.add("$keyItemId NOT IN (SELECT $keyFeedItem FROM $tableFavorites) ")
            }
        }

        if (statements.isEmpty()) {
            return ""
        }

        val query = StringBuilder(" (" + statements[0])
        for (r in statements.subList(1, statements.size)) {
            query.append(" AND ")
            query.append(r)
        }
        query.append(") ")
        return query.toString()
    }
}
