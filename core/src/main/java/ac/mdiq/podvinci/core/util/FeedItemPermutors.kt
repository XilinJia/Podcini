package ac.mdiq.podvinci.core.util

import ac.mdiq.podvinci.model.feed.FeedItem
import ac.mdiq.podvinci.model.feed.SortOrder
import java.util.*

/**
 * Provides method for sorting the a list of [FeedItem] according to rules.
 */
object FeedItemPermutors {
    /**
     * Returns a Permutor that sorts a list appropriate to the given sort order.
     *
     * @return Permutor that sorts a list appropriate to the given sort order.
     */
    @JvmStatic
    fun getPermutor(sortOrder: SortOrder): Permutor<FeedItem> {
        var comparator: Comparator<FeedItem>? = null
        var permutor: Permutor<FeedItem>? = null

        when (sortOrder) {
            SortOrder.EPISODE_TITLE_A_Z -> comparator = Comparator { f1: FeedItem?, f2: FeedItem? ->
                itemTitle(f1).compareTo(itemTitle(f2))
            }
            SortOrder.EPISODE_TITLE_Z_A -> comparator = Comparator { f1: FeedItem?, f2: FeedItem? ->
                itemTitle(f2).compareTo(itemTitle(f1))
            }
            SortOrder.DATE_OLD_NEW -> comparator = Comparator { f1: FeedItem?, f2: FeedItem? ->
                pubDate(f1).compareTo(pubDate(f2))
            }
            SortOrder.DATE_NEW_OLD -> comparator = Comparator { f1: FeedItem?, f2: FeedItem? ->
                pubDate(f2).compareTo(pubDate(f1))
            }
            SortOrder.DURATION_SHORT_LONG -> comparator = Comparator { f1: FeedItem?, f2: FeedItem? ->
                duration(f1).compareTo(duration(f2))
            }
            SortOrder.DURATION_LONG_SHORT -> comparator = Comparator { f1: FeedItem?, f2: FeedItem? ->
                duration(f2).compareTo(duration(f1))
            }
            SortOrder.EPISODE_FILENAME_A_Z -> comparator = Comparator { f1: FeedItem?, f2: FeedItem? ->
                itemLink(f1).compareTo(itemLink(f2))
            }
            SortOrder.EPISODE_FILENAME_Z_A -> comparator = Comparator { f1: FeedItem?, f2: FeedItem? ->
                itemLink(f2).compareTo(itemLink(f1))
            }
            SortOrder.FEED_TITLE_A_Z -> comparator = Comparator { f1: FeedItem?, f2: FeedItem? ->
                feedTitle(f1).compareTo(feedTitle(f2))
            }
            SortOrder.FEED_TITLE_Z_A -> comparator = Comparator { f1: FeedItem?, f2: FeedItem? ->
                feedTitle(f2).compareTo(feedTitle(f1))
            }
            SortOrder.RANDOM -> permutor = object : Permutor<FeedItem> {
                override fun reorder(queue: MutableList<FeedItem>?) {if (!queue.isNullOrEmpty()) queue.shuffle()}
            }
            SortOrder.SMART_SHUFFLE_OLD_NEW -> permutor = object : Permutor<FeedItem> {
                override fun reorder(queue: MutableList<FeedItem>?) {if (!queue.isNullOrEmpty()) smartShuffle(queue as MutableList<FeedItem?>, true) }
            }
            SortOrder.SMART_SHUFFLE_NEW_OLD -> permutor = object : Permutor<FeedItem> {
                override fun reorder(queue: MutableList<FeedItem>?) {if (!queue.isNullOrEmpty()) smartShuffle(queue as MutableList<FeedItem?>, false)  }
            }
            SortOrder.SIZE_SMALL_LARGE -> comparator = Comparator { f1: FeedItem?, f2: FeedItem? ->
                size(f1).compareTo(size(f2))
            }
            SortOrder.SIZE_LARGE_SMALL -> comparator = Comparator { f1: FeedItem?, f2: FeedItem? ->
                size(f2).compareTo(size(f1))
            }
        }
        if (comparator != null) {
            val comparator2: Comparator<FeedItem> = comparator
            permutor = object : Permutor<FeedItem> {
                override fun reorder(queue: MutableList<FeedItem>?) {if (!queue.isNullOrEmpty()) queue.sortWith(comparator2)}
            }
        }
        return permutor!!
    }

    // Null-safe accessors
    private fun pubDate(item: FeedItem?): Date {
        return if (item?.pubDate != null) item.pubDate!! else Date(0)
    }

    private fun itemTitle(item: FeedItem?): String {
        return if (item?.title != null) item.title!!.lowercase(Locale.getDefault()) else ""
    }

    private fun duration(item: FeedItem?): Int {
        return if (item?.media != null) item.media!!.getDuration() else 0
    }

    private fun size(item: FeedItem?): Long {
        return if (item?.media != null) item.media!!.size else 0
    }

    private fun itemLink(item: FeedItem?): String {
        return if (item?.link != null) item.link!!.lowercase(Locale.getDefault()) else ""
    }

    private fun feedTitle(item: FeedItem?): String {
        return if (item?.feed != null && item.feed!!.title != null) item.feed!!.title!!.lowercase(Locale.getDefault()) else ""
    }

    /**
     * Implements a reordering by pubdate that avoids consecutive episodes from the same feed in
     * the queue.
     *
     * A listener might want to hear episodes from any given feed in pubdate order, but would
     * prefer a more balanced ordering that avoids having to listen to clusters of consecutive
     * episodes from the same feed. This is what "Smart Shuffle" tries to accomplish.
     *
     * Assume the queue looks like this: `ABCDDEEEEEEEEEE`.
     * This method first starts with a queue of the final size, where each slot is empty (null).
     * It takes the podcast with most episodes (`E`) and places the episodes spread out in the queue: `EE_E_EE_E_EE_EE`.
     * The podcast with the second-most number of episodes (`D`) is then
     * placed spread-out in the *available* slots: `EE_EDEE_EDEE_EE`.
     * This continues, until we end up with: `EEBEDEECEDEEAEE`.
     *
     * Note that episodes aren't strictly ordered in terms of pubdate, but episodes of each feed are.
     *
     * @param queue A (modifiable) list of FeedItem elements to be reordered.
     * @param ascending `true` to use ascending pubdate in the reordering;
     * `false` for descending.
     */
    private fun smartShuffle(queue: MutableList<FeedItem?>, ascending: Boolean) {
        // Divide FeedItems into lists by feed
        val map: MutableMap<Long, MutableList<FeedItem>> = HashMap()
        for (item in queue) {
            if (item == null) continue
            val id = item.feedId
            if (!map.containsKey(id)) {
                map[id] = ArrayList()
            }
            map[id]!!.add(item)
        }

        // Sort each individual list by PubDate (ascending/descending)
        val itemComparator: Comparator<FeedItem> = if (ascending)
            Comparator { f1: FeedItem, f2: FeedItem -> f1.pubDate?.compareTo(f2.pubDate)?:-1 }
        else Comparator { f1: FeedItem, f2: FeedItem -> f2.pubDate?.compareTo(f1.pubDate)?:-1 }

        val feeds: MutableList<List<FeedItem>> = ArrayList()
        for ((_, value) in map) {
            value.sortWith(itemComparator)
            feeds.add(value)
        }

        val emptySlots = ArrayList<Int>()
        for (i in queue.indices) {
            queue[i] = null
            emptySlots.add(i)
        }

        // Starting with the largest feed, place items spread out through the empty slots in the queue
        feeds.sortWith { f1: List<FeedItem>, f2: List<FeedItem> -> f2.size.compareTo(f1.size) }
        for (feedItems in feeds) {
            val spread = emptySlots.size.toDouble() / (feedItems.size + 1)
            val emptySlotIterator = emptySlots.iterator()
            var skipped = 0
            var placed = 0
            while (emptySlotIterator.hasNext()) {
                val nextEmptySlot = emptySlotIterator.next()
                skipped++
                if (skipped >= spread * (placed + 1)) {
                    if (queue[nextEmptySlot] != null) {
                        throw RuntimeException("Slot to be placed in not empty")
                    }
                    queue[nextEmptySlot] = feedItems[placed]
                    emptySlotIterator.remove()
                    placed++
                    if (placed == feedItems.size) {
                        break
                    }
                }
            }
        }
    }
}
