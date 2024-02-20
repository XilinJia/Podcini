package ac.mdiq.podcini.core.storage

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import ac.mdiq.podcini.core.storage.DBReader.getEpisodes
import ac.mdiq.podcini.model.feed.FeedItem
import ac.mdiq.podcini.model.feed.FeedItemFilter
import ac.mdiq.podcini.model.feed.SortOrder
import java.util.*
import java.util.concurrent.ExecutionException

/**
 * Implementation of the EpisodeCleanupAlgorithm interface used by Podcini.
 */
class APCleanupAlgorithm(
        /** the number of days after playback to wait before an item is eligible to be cleaned up.
         * Fractional for number of hours, e.g., 0.5 = 12 hours, 0.0416 = 1 hour.   */
        @JvmField @get:VisibleForTesting val numberOfHoursAfterPlayback: Int
) : EpisodeCleanupAlgorithm() {
    /**
     * @return the number of episodes that *could* be cleaned up, if needed
     */
    override fun getReclaimableItems(): Int {
        return candidates.size
    }

    public override fun performCleanup(context: Context?, numberOfEpisodesToDelete: Int): Int {
        val candidates = candidates.toMutableList()

        candidates.sortWith(Comparator { lhs: FeedItem, rhs: FeedItem ->
            var l = lhs.media!!.getPlaybackCompletionDate()
            var r = rhs.media!!.getPlaybackCompletionDate()

            if (l == null) {
                l = Date()
            }
            if (r == null) {
                r = Date()
            }
            l.compareTo(r)
        })

        val delete = if (candidates.size > numberOfEpisodesToDelete) {
            candidates.subList(0, numberOfEpisodesToDelete)
        } else {
            candidates
        }

        for (item in delete) {
            try {
                DBWriter.deleteFeedMediaOfItem(context!!, item.media!!.id).get()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } catch (e: ExecutionException) {
                e.printStackTrace()
            }
        }

        val counter = delete.size


        Log.i(TAG, String.format(Locale.US,
            "Auto-delete deleted %d episodes (%d requested)", counter,
            numberOfEpisodesToDelete))

        return counter
    }

    @VisibleForTesting
    fun calcMostRecentDateForDeletion(currentDate: Date): Date {
        return minusHours(currentDate, numberOfHoursAfterPlayback)
    }

    private val candidates: List<FeedItem>
        get() {
            val candidates: MutableList<FeedItem> = ArrayList()
            val downloadedItems = getEpisodes(0, Int.MAX_VALUE,
                FeedItemFilter(FeedItemFilter.DOWNLOADED), SortOrder.DATE_NEW_OLD)

            val mostRecentDateForDeletion = calcMostRecentDateForDeletion(Date())
            for (item in downloadedItems) {
                if (item.hasMedia()
                        && item.media!!.isDownloaded()
                        && !item.isTagged(FeedItem.TAG_QUEUE)
                        && item.isPlayed()
                        && !item.isTagged(FeedItem.TAG_FAVORITE)) {
                    val media = item.media
                    // make sure this candidate was played at least the proper amount of days prior
                    // to now
                    if (media?.getPlaybackCompletionDate() != null && media.getPlaybackCompletionDate()!!
                                .before(mostRecentDateForDeletion)) {
                        candidates.add(item)
                    }
                }
            }
            return candidates
        }

    public override fun getDefaultCleanupParameter(): Int {
        return getNumEpisodesToCleanup(0)
    }

    companion object {
        private const val TAG = "APCleanupAlgorithm"
        private fun minusHours(baseDate: Date, numberOfHours: Int): Date {
            val cal = Calendar.getInstance()
            cal.time = baseDate

            cal.add(Calendar.HOUR_OF_DAY, -1 * numberOfHours)

            return cal.time
        }
    }
}
