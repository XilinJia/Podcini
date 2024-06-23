package ac.mdiq.podcini.util.sorting

import ac.mdiq.podcini.storage.model.Episode

/**
 * Compares the pubDate of two FeedItems for sorting.
 */
class EpisodePubdateComparator : Comparator<Episode> {
    /**
     * Returns a new instance of this comparator in reverse order.
     */
    override fun compare(lhs: Episode, rhs: Episode): Int {
        return when {
            rhs.pubDate == null && lhs.pubDate == null -> 0
            rhs.pubDate == null -> 1
            lhs.pubDate == null -> -1
            else -> rhs.pubDate.compareTo(lhs.pubDate) ?: -1
        }
    }
}
