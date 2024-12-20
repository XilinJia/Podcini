package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.R
import java.util.Date
import java.util.Locale

enum class EpisodeSortOrder(val code: Int, val res: Int) {
    DATE_OLD_NEW(1, R.string.publish_date),
    DATE_NEW_OLD(2, R.string.publish_date),
    EPISODE_TITLE_A_Z(3, R.string.episode_title),
    EPISODE_TITLE_Z_A(4, R.string.episode_title),
    DURATION_SHORT_LONG(5, R.string.duration),
    DURATION_LONG_SHORT(6, R.string.duration),
    EPISODE_FILENAME_A_Z(7, R.string.filename),
    EPISODE_FILENAME_Z_A(8, R.string.filename),
    SIZE_SMALL_LARGE(9, R.string.size),
    SIZE_LARGE_SMALL(10, R.string.size),
    PLAYED_DATE_OLD_NEW(11, R.string.last_played_date),
    PLAYED_DATE_NEW_OLD(12, R.string.last_played_date),
    COMPLETED_DATE_OLD_NEW(13, R.string.completed_date),
    COMPLETED_DATE_NEW_OLD(14, R.string.completed_date),
    DOWNLOAD_DATE_OLD_NEW(15, R.string.download_date),
    DOWNLOAD_DATE_NEW_OLD(16, R.string.download_date),
    VIEWS_LOW_HIGH(17, R.string.view_count),
    VIEWS_HIGH_LOW(18, R.string.view_count),
    COMMENT_DATE_OLD_NEW(19, R.string.last_comment_date),
    COMMENT_DATE_NEW_OLD(20, R.string.last_comment_date),

    FEED_TITLE_A_Z(101, R.string.feed_title),
    FEED_TITLE_Z_A(102, R.string.feed_title),
    RANDOM(103, R.string.random),
    RANDOM1(104, R.string.random),
    SMART_SHUFFLE_OLD_NEW(105, R.string.smart_shuffle),
    SMART_SHUFFLE_NEW_OLD(106, R.string.smart_shuffle);

    companion object {
        /**
         * Converts the string representation to its enum value. If the string value is unknown,
         * the given default value is returned.
         */
        fun parseWithDefault(value: String, defaultValue: EpisodeSortOrder): EpisodeSortOrder {
            return try { valueOf(value) } catch (e: IllegalArgumentException) { defaultValue }
        }

        fun fromCodeString(codeStr: String?): EpisodeSortOrder {
            if (codeStr.isNullOrEmpty()) return EPISODE_TITLE_A_Z
            val code = codeStr.toInt()
            for (sortOrder in entries) {
                if (sortOrder.code == code) return sortOrder
            }
            return EPISODE_TITLE_A_Z
//            throw IllegalArgumentException("Unsupported code: $code")
        }

        fun fromCode(code: Int): EpisodeSortOrder {
            return enumValues<EpisodeSortOrder>().firstOrNull { it.code == code } ?: EPISODE_TITLE_A_Z
        }

        fun toCodeString(sortOrder: EpisodeSortOrder): String? {
            return sortOrder.code.toString()
        }

        fun valuesOf(stringValues: Array<String?>): Array<EpisodeSortOrder?> {
            val values = arrayOfNulls<EpisodeSortOrder>(stringValues.size)
            for (i in stringValues.indices) values[i] = valueOf(stringValues[i]!!)
            return values
        }

        /**
         * Returns a Permutor that sorts a list appropriate to the given sort order.
         * @return Permutor that sorts a list appropriate to the given sort order.
         */
        @JvmStatic
        fun getPermutor(sortOrder: EpisodeSortOrder): Permutor<Episode> {
            var comparator: java.util.Comparator<Episode>? = null
            var permutor: Permutor<Episode>? = null

            when (sortOrder) {
                EPISODE_TITLE_A_Z -> comparator = Comparator { f1: Episode?, f2: Episode? -> itemTitle(f1).compareTo(itemTitle(f2)) }
                EPISODE_TITLE_Z_A -> comparator = Comparator { f1: Episode?, f2: Episode? -> itemTitle(f2).compareTo(itemTitle(f1)) }
                DATE_OLD_NEW -> comparator = Comparator { f1: Episode?, f2: Episode? -> pubDate(f1).compareTo(pubDate(f2)) }
                DATE_NEW_OLD -> comparator = Comparator { f1: Episode?, f2: Episode? -> pubDate(f2).compareTo(pubDate(f1)) }
                DURATION_SHORT_LONG -> comparator = Comparator { f1: Episode?, f2: Episode? -> duration(f1).compareTo(duration(f2)) }
                DURATION_LONG_SHORT -> comparator = Comparator { f1: Episode?, f2: Episode? -> duration(f2).compareTo(duration(f1)) }
                EPISODE_FILENAME_A_Z -> comparator = Comparator { f1: Episode?, f2: Episode? -> itemLink(f1).compareTo(itemLink(f2)) }
                EPISODE_FILENAME_Z_A -> comparator = Comparator { f1: Episode?, f2: Episode? -> itemLink(f2).compareTo(itemLink(f1)) }
                PLAYED_DATE_OLD_NEW -> comparator = Comparator { f1: Episode?, f2: Episode? -> playDate(f1).compareTo(playDate(f2)) }
                PLAYED_DATE_NEW_OLD -> comparator = Comparator { f1: Episode?, f2: Episode? -> playDate(f2).compareTo(playDate(f1)) }
                COMPLETED_DATE_OLD_NEW -> comparator = Comparator { f1: Episode?, f2: Episode? -> completeDate(f1).compareTo(completeDate(f2)) }
                COMPLETED_DATE_NEW_OLD -> comparator = Comparator { f1: Episode?, f2: Episode? -> completeDate(f2).compareTo(completeDate(f1)) }
                DOWNLOAD_DATE_OLD_NEW -> comparator = Comparator { f1: Episode?, f2: Episode? -> downloadDate(f1).compareTo(downloadDate(f2)) }
                DOWNLOAD_DATE_NEW_OLD -> comparator = Comparator { f1: Episode?, f2: Episode? -> downloadDate(f2).compareTo(downloadDate(f1)) }
                VIEWS_LOW_HIGH -> comparator = Comparator { f1: Episode?, f2: Episode? -> viewCount(f1).compareTo(viewCount(f2)) }
                VIEWS_HIGH_LOW -> comparator = Comparator { f1: Episode?, f2: Episode? -> viewCount(f2).compareTo(viewCount(f1)) }
                COMMENT_DATE_OLD_NEW -> comparator = Comparator { f1: Episode?, f2: Episode? -> commentDate(f1).compareTo(commentDate(f2)) }
                COMMENT_DATE_NEW_OLD -> comparator = Comparator { f1: Episode?, f2: Episode? -> commentDate(f2).compareTo(playDate(f1)) }

                FEED_TITLE_A_Z -> comparator = Comparator { f1: Episode?, f2: Episode? -> feedTitle(f1).compareTo(feedTitle(f2)) }
                FEED_TITLE_Z_A -> comparator = Comparator { f1: Episode?, f2: Episode? -> feedTitle(f2).compareTo(feedTitle(f1)) }
                RANDOM, RANDOM1 -> permutor = object : Permutor<Episode> {
                    override fun reorder(queue: MutableList<Episode>?) {
                        if (!queue.isNullOrEmpty()) queue.shuffle()
                    }
                }
                SMART_SHUFFLE_OLD_NEW -> permutor = object : Permutor<Episode> {
                    override fun reorder(queue: MutableList<Episode>?) {
                        if (!queue.isNullOrEmpty()) smartShuffle(queue as MutableList<Episode?>, true)
                    }
                }
                SMART_SHUFFLE_NEW_OLD -> permutor = object : Permutor<Episode> {
                    override fun reorder(queue: MutableList<Episode>?) {
                        if (!queue.isNullOrEmpty()) smartShuffle(queue as MutableList<Episode?>, false)
                    }
                }
                SIZE_SMALL_LARGE -> comparator = Comparator { f1: Episode?, f2: Episode? -> size(f1).compareTo(size(f2)) }
                SIZE_LARGE_SMALL -> comparator = Comparator { f1: Episode?, f2: Episode? -> size(f2).compareTo(size(f1)) }
            }
            if (comparator != null) {
                val comparator2: java.util.Comparator<Episode> = comparator
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
            return item?.media?.lastPlayedTime ?: 0
        }

        private fun commentDate(item: Episode?): Long {
            return item?.commentTime ?: 0
        }

        private fun downloadDate(item: Episode?): Long {
            return item?.media?.downloadTime ?: 0
        }

        private fun completeDate(item: Episode?): Date {
            return item?.media?.playbackCompletionDate ?: Date(0)
        }

        private fun itemTitle(item: Episode?): String {
            return (item?.title ?: "").lowercase(Locale.getDefault())
        }

        private fun duration(item: Episode?): Int {
            return item?.media?.duration ?: 0
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

        private fun viewCount(item: Episode?): Int {
            return item?.viewCount ?: 0
        }

        /**
         * Implements a reordering by pubdate that avoids consecutive episodes from the same feed in the queue.
         * A listener might want to hear episodes from any given feed in pubdate order, but would
         * prefer a more balanced ordering that avoids having to listen to clusters of consecutive
         * episodes from the same feed. This is what "Smart Shuffle" tries to accomplish.
         * Assume the queue looks like this: `ABCDDEEEEEEEEEE`.
         * This method first starts with a queue of the final size, where each slot is empty (null).
         * It takes the podcast with most episodes (`E`) and places the episodes spread out in the queue: `EE_E_EE_E_EE_EE`.
         * The podcast with the second-most number of episodes (`D`) is then
         * placed spread-out in the *available* slots: `EE_EDEE_EDEE_EE`.
         * This continues, until we end up with: `EEBEDEECEDEEAEE`.
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
            val itemComparator: java.util.Comparator<Episode> =
                if (ascending) Comparator { f1: Episode, f2: Episode -> f1.pubDate.compareTo(f2.pubDate) }
                else Comparator { f1: Episode, f2: Episode -> f2.pubDate.compareTo(f1.pubDate) }

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
         * won't work (e.g. Random, Smart Shuffle, etc)
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
}
