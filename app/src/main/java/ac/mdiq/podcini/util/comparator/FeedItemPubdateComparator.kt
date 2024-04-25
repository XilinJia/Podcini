package ac.mdiq.podcini.util.comparator

import ac.mdiq.podcini.storage.model.feed.FeedItem

/**
 * Compares the pubDate of two FeedItems for sorting.
 */
class FeedItemPubdateComparator : Comparator<FeedItem> {
    /**
     * Returns a new instance of this comparator in reverse order.
     */
    override fun compare(lhs: FeedItem, rhs: FeedItem): Int {
        return when {
            rhs.pubDate == null && lhs.pubDate == null -> 0
            rhs.pubDate == null -> 1
            lhs.pubDate == null -> -1
            else -> rhs.getPubDate()?.compareTo(lhs.pubDate) ?: -1
        }
    }
}
