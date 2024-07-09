package ac.mdiq.podcini.util.sorting

import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import java.util.*

/**
 * Provides method for sorting the a list of [Episode] according to rules.
 */
object EpisodesPermutors {
    /**
     * Returns a Permutor that sorts a list appropriate to the given sort order.
     *
     * @return Permutor that sorts a list appropriate to the given sort order.
     */
    @JvmStatic
    fun getPermutor(sortOrder: EpisodeSortOrder): Permutor<Episode> {
        var comparator: Comparator<Episode>? = null
        var permutor: Permutor<Episode>? = null

        when (sortOrder) {
            EpisodeSortOrder.EPISODE_TITLE_A_Z -> comparator = Comparator { f1: Episode?, f2: Episode? -> itemTitle(f1).compareTo(itemTitle(f2)) }
            EpisodeSortOrder.EPISODE_TITLE_Z_A -> comparator = Comparator { f1: Episode?, f2: Episode? -> itemTitle(f2).compareTo(itemTitle(f1)) }
            EpisodeSortOrder.DATE_OLD_NEW -> comparator = Comparator { f1: Episode?, f2: Episode? -> pubDate(f1).compareTo(pubDate(f2)) }
            EpisodeSortOrder.DATE_NEW_OLD -> comparator = Comparator { f1: Episode?, f2: Episode? -> pubDate(f2).compareTo(pubDate(f1)) }
            EpisodeSortOrder.DURATION_SHORT_LONG -> comparator = Comparator { f1: Episode?, f2: Episode? -> duration(f1).compareTo(duration(f2)) }
            EpisodeSortOrder.DURATION_LONG_SHORT -> comparator = Comparator { f1: Episode?, f2: Episode? -> duration(f2).compareTo(duration(f1)) }
            EpisodeSortOrder.EPISODE_FILENAME_A_Z -> comparator = Comparator { f1: Episode?, f2: Episode? -> itemLink(f1).compareTo(itemLink(f2)) }
            EpisodeSortOrder.EPISODE_FILENAME_Z_A -> comparator = Comparator { f1: Episode?, f2: Episode? -> itemLink(f2).compareTo(itemLink(f1)) }
            EpisodeSortOrder.PLAYED_DATE_OLD_NEW -> comparator = Comparator { f1: Episode?, f2: Episode? -> playDate(f1).compareTo(playDate(f2)) }
            EpisodeSortOrder.PLAYED_DATE_NEW_OLD -> comparator = Comparator { f1: Episode?, f2: Episode? -> playDate(f2).compareTo(playDate(f1)) }
            EpisodeSortOrder.COMPLETED_DATE_OLD_NEW -> comparator = Comparator { f1: Episode?, f2: Episode? -> completeDate(f1).compareTo(completeDate(f2)) }
            EpisodeSortOrder.COMPLETED_DATE_NEW_OLD -> comparator = Comparator { f1: Episode?, f2: Episode? -> completeDate(f2).compareTo(completeDate(f1)) }

            EpisodeSortOrder.FEED_TITLE_A_Z -> comparator = Comparator { f1: Episode?, f2: Episode? -> feedTitle(f1).compareTo(feedTitle(f2)) }
            EpisodeSortOrder.FEED_TITLE_Z_A -> comparator = Comparator { f1: Episode?, f2: Episode? -> feedTitle(f2).compareTo(feedTitle(f1)) }
            EpisodeSortOrder.RANDOM -> permutor = object : Permutor<Episode> {
                override fun reorder(queue: MutableList<Episode>?) {
                    if (!queue.isNullOrEmpty()) queue.shuffle()
                }
            }
            EpisodeSortOrder.SMART_SHUFFLE_OLD_NEW -> permutor = object : Permutor<Episode> {
                override fun reorder(queue: MutableList<Episode>?) {
                    if (!queue.isNullOrEmpty()) smartShuffle(queue as MutableList<Episode?>, true)
                }
            }
            EpisodeSortOrder.SMART_SHUFFLE_NEW_OLD -> permutor = object : Permutor<Episode> {
                override fun reorder(queue: MutableList<Episode>?) {
                    if (!queue.isNullOrEmpty()) smartShuffle(queue as MutableList<Episode?>, false)
                }
            }
            EpisodeSortOrder.SIZE_SMALL_LARGE -> comparator = Comparator { f1: Episode?, f2: Episode? -> size(f1).compareTo(size(f2)) }
            EpisodeSortOrder.SIZE_LARGE_SMALL -> comparator = Comparator { f1: Episode?, f2: Episode? -> size(f2).compareTo(size(f1)) }
        }
        if (comparator != null) {
            val comparator2: Comparator<Episode> = comparator
            permutor = object : Permutor<Episode> {
                override fun reorder(queue: MutableList<Episode>?) {if (!queue.isNullOrEmpty()) queue.sortWith(comparator2)}
            }
        }
        return permutor!!
    }

    private fun pubDate(item: Episode?): Date {
        return if (item == null) Date() else Date(item.pubDate)
    }

    private fun playDate(item: Episode?): Long {
        return item?.media?.getLastPlayedTime() ?: 0
    }

    private fun completeDate(item: Episode?): Date {
        return item?.media?.playbackCompletionDate ?: Date(0)
    }

    private fun itemTitle(item: Episode?): String {
        return (item?.title ?: "").lowercase(Locale.getDefault())
    }

    private fun duration(item: Episode?): Int {
        return item?.media?.getDuration() ?: 0
    }

    private fun size(item: Episode?): Long {
        return item?.media?.size ?: 0
    }

    private fun itemLink(item: Episode?): String {
        return (item?.link ?: "").lowercase(Locale.getDefault())
    }

    private fun feedTitle(item: Episode?): String {
        return (item?.feed?.title ?: "").lowercase(Locale.getDefault())
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
    private fun smartShuffle(queue: MutableList<Episode?>, ascending: Boolean) {
        // Divide FeedItems into lists by feed
        val map: MutableMap<Long, MutableList<Episode>> = HashMap()
        for (item in queue) {
            if (item == null) continue
            val id = item.feedId
            if (id != null) {
                if (!map.containsKey(id)) map[id] = ArrayList()
                map[id]!!.add(item)
            }
        }

        // Sort each individual list by PubDate (ascending/descending)
        val itemComparator: Comparator<Episode> =
            if (ascending) Comparator { f1: Episode, f2: Episode -> f1.pubDate?.compareTo(f2.pubDate)?:-1 }
            else Comparator { f1: Episode, f2: Episode -> f2.pubDate?.compareTo(f1.pubDate)?:-1 }

        val feeds: MutableList<List<Episode>> = ArrayList()
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
        feeds.sortWith { f1: List<Episode>, f2: List<Episode> -> f2.size.compareTo(f1.size) }
        for (feedItems in feeds) {
            val spread = emptySlots.size.toDouble() / (feedItems.size + 1)
            val emptySlotIterator = emptySlots.iterator()
            var skipped = 0
            var placed = 0
            while (emptySlotIterator.hasNext()) {
                val nextEmptySlot = emptySlotIterator.next()
                skipped++
                if (skipped >= spread * (placed + 1)) {
                    if (queue[nextEmptySlot] != null) throw RuntimeException("Slot to be placed in not empty")
                    queue[nextEmptySlot] = feedItems[placed]
                    emptySlotIterator.remove()
                    placed++
                    if (placed == feedItems.size) break
                }
            }
        }
    }

    /**
     * Interface for passing around list permutor method. This is used for cases where a simple comparator
     * won't work (e.g. Random, Smart Shuffle, etc).
     *
     * @param <E> the type of elements in the list
    </E> */
    interface Permutor<E> {
        /**
         * Reorders the specified list.
         * @param queue A (modifiable) list of elements to be reordered
         */
        fun reorder(queue: MutableList<E>?)
    }
}
