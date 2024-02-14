package ac.mdiq.podvinci.core.storage

import ac.mdiq.podvinci.model.feed.FeedItem
import ac.mdiq.podvinci.model.feed.FeedMedia
import ac.mdiq.podvinci.model.playback.Playable
import ac.mdiq.podvinci.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podvinci.storage.preferences.UserPreferences.EnqueueLocation
import java.util.*

/**
 * @see DBWriter.addQueueItem
 */
class ItemEnqueuePositionCalculator(private val enqueueLocation: EnqueueLocation) {
    /**
     * Determine the position (0-based) that the item(s) should be inserted to the named queue.
     *
     * @param curQueue           the queue to which the item is to be inserted
     * @param currentPlaying     the currently playing media
     */
    fun calcPosition(curQueue: List<FeedItem>, currentPlaying: Playable?): Int {
        when (enqueueLocation) {
            EnqueueLocation.BACK -> return curQueue.size
            EnqueueLocation.FRONT ->                 // Return not necessarily 0, so that when a list of items are downloaded and enqueued
                // in succession of calls (e.g., users manually tapping download one by one),
                // the items enqueued are kept the same order.
                // Simply returning 0 will reverse the order.
                return getPositionOfFirstNonDownloadingItem(0, curQueue)
            EnqueueLocation.AFTER_CURRENTLY_PLAYING -> {
                val currentlyPlayingPosition = getCurrentlyPlayingPosition(curQueue, currentPlaying)
                return getPositionOfFirstNonDownloadingItem(
                    currentlyPlayingPosition + 1, curQueue)
            }
            EnqueueLocation.RANDOM -> {
                val random = Random()
                return random.nextInt(curQueue.size + 1)
            }
            else -> throw AssertionError("calcPosition() : unrecognized enqueueLocation option: $enqueueLocation")
        }
    }

    private fun getPositionOfFirstNonDownloadingItem(startPosition: Int, curQueue: List<FeedItem>): Int {
        val curQueueSize = curQueue.size
        for (i in startPosition until curQueueSize) {
            if (!isItemAtPositionDownloading(i, curQueue)) {
                return i
            } // else continue to search;
        }
        return curQueueSize
    }

    private fun isItemAtPositionDownloading(position: Int, curQueue: List<FeedItem>): Boolean {
        val curItem = try {
            curQueue[position]
        } catch (e: IndexOutOfBoundsException) {
            null
        }
        if (curItem?.media?.download_url == null) return false
        return curItem.media != null && DownloadServiceInterface.get()?.isDownloadingEpisode(curItem.media!!.download_url!!)?:false
    }

    companion object {
        private fun getCurrentlyPlayingPosition(curQueue: List<FeedItem>,
                                                currentPlaying: Playable?
        ): Int {
            if (currentPlaying !is FeedMedia) {
                return -1
            }
            val curPlayingItemId = currentPlaying.getItem()!!.id
            for (i in curQueue.indices) {
                if (curPlayingItemId == curQueue[i].id) {
                    return i
                }
            }
            return -1
        }
    }
}
