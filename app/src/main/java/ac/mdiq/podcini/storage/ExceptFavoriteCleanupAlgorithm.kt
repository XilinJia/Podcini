package ac.mdiq.podcini.storage

import android.content.Context
import android.util.Log
import ac.mdiq.podcini.storage.DBReader.getEpisodes
import ac.mdiq.podcini.storage.DBReader.getTotalEpisodeCount
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter
import ac.mdiq.podcini.storage.model.feed.SortOrder
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.episodeCacheSize
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.ExecutionException

/**
 * A cleanup algorithm that removes any item that isn't a favorite but only if space is needed.
 */
class ExceptFavoriteCleanupAlgorithm : EpisodeCleanupAlgorithm() {
    /**
     * The maximum number of episodes that could be cleaned up.
     *
     * @return the number of episodes that *could* be cleaned up, if needed
     */
    override fun getReclaimableItems(): Int {
        return candidates.size
    }

    @OptIn(UnstableApi::class) public override fun performCleanup(context: Context, numberOfEpisodesToDelete: Int): Int {
        var candidates = candidates

        // in the absence of better data, we'll sort by item publication date
        candidates = candidates.sortedWith { lhs: FeedItem, rhs: FeedItem ->
            val l = lhs.getPubDate()
            val r = rhs.getPubDate()
            if (l != null && r != null) return@sortedWith l.compareTo(r)
            else return@sortedWith lhs.id.compareTo(rhs.id)  // No date - compare by id which should be always incremented
        }

        val delete = if (candidates.size > numberOfEpisodesToDelete) candidates.subList(0, numberOfEpisodesToDelete) else candidates

        for (item in delete) {
            if (item.media == null) continue
            try {
                runBlocking { DBWriter.deleteFeedMediaOfItem(context, item.media!!.id).join() }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } catch (e: ExecutionException) {
                e.printStackTrace()
            }
        }

        val counter = delete.size
        Log.i(TAG, String.format(Locale.US, "Auto-delete deleted %d episodes (%d requested)", counter, numberOfEpisodesToDelete))

        return counter
    }

    private val candidates: List<FeedItem>
        get() {
            val candidates: MutableList<FeedItem> = ArrayList()
            val downloadedItems = getEpisodes(0, Int.MAX_VALUE, FeedItemFilter(FeedItemFilter.DOWNLOADED), SortOrder.DATE_NEW_OLD)
            for (item in downloadedItems) {
                if (item.hasMedia() && item.media!!.isDownloaded() && !item.isTagged(FeedItem.TAG_FAVORITE)) candidates.add(item)
            }
            return candidates
        }

    public override fun getDefaultCleanupParameter(): Int {
        val cacheSize = episodeCacheSize
        if (cacheSize != UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED) {
            val downloadedEpisodes = getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.DOWNLOADED))
            if (downloadedEpisodes > cacheSize) return downloadedEpisodes - cacheSize
        }
        return 0
    }

    companion object {
        private const val TAG = "ExceptFavCleanupAlgo"
    }
}
