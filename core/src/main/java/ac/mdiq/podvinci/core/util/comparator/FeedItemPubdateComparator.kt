package ac.mdiq.podvinci.core.util.comparator

import ac.mdiq.podvinci.model.feed.FeedItem

/**
 * Compares the pubDate of two FeedItems for sorting.
 */
class FeedItemPubdateComparator : Comparator<FeedItem> {
    /**
     * Returns a new instance of this comparator in reverse order.
     */
    override fun compare(lhs: FeedItem, rhs: FeedItem): Int {
        if (rhs.pubDate == null && lhs.pubDate == null) {
            return 0
        } else if (rhs.pubDate == null) {
            return 1
        } else if (lhs.pubDate == null) {
            return -1
        }
        return rhs.getPubDate()?.compareTo(lhs.pubDate)?:-1
    }
}
