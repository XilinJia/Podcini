package de.danoeh.antennapod.core.storage

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import de.danoeh.antennapod.core.storage.DBReader.getEpisodes
import de.danoeh.antennapod.core.storage.DBReader.getQueue
import de.danoeh.antennapod.core.storage.DBReader.getTotalEpisodeCount
import de.danoeh.antennapod.core.storage.EpisodeCleanupAlgorithmFactory.build
import de.danoeh.antennapod.core.util.NetworkUtils.isAutoDownloadAllowed
import de.danoeh.antennapod.core.util.PlaybackStatus.isPlaying
import de.danoeh.antennapod.core.util.PowerUtils.deviceCharging
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.storage.preferences.UserPreferences.episodeCacheSize
import de.danoeh.antennapod.storage.preferences.UserPreferences.isEnableAutodownload
import de.danoeh.antennapod.storage.preferences.UserPreferences.isEnableAutodownloadOnBattery

/**
 * Implements the automatic download algorithm used by AntennaPod. This class assumes that
 * the client uses the [EpisodeCleanupAlgorithm].
 */
open class AutomaticDownloadAlgorithm {
    /**
     * Looks for undownloaded episodes in the queue or list of new items and request a download if
     * 1. Network is available
     * 2. The device is charging or the user allows auto download on battery
     * 3. There is free space in the episode cache
     * This method is executed on an internal single thread executor.
     *
     * @param context  Used for accessing the DB.
     * @return A Runnable that will be submitted to an ExecutorService.
     */
    @UnstableApi open fun autoDownloadUndownloadedItems(context: Context?): Runnable? {
        return Runnable {
            // true if we should auto download based on network status
            val networkShouldAutoDl = (isAutoDownloadAllowed
                    && isEnableAutodownload)

            // true if we should auto download based on power status
            val powerShouldAutoDl = (deviceCharging(context!!)
                    || isEnableAutodownloadOnBattery)

            // we should only auto download if both network AND power are happy
            if (networkShouldAutoDl && powerShouldAutoDl) {
                Log.d(TAG, "Performing auto-dl of undownloaded episodes")

                val candidates: MutableList<FeedItem>
                val queue = getQueue()
                val newItems = getEpisodes(0, Int.MAX_VALUE,
                    FeedItemFilter(FeedItemFilter.NEW), SortOrder.DATE_NEW_OLD)
                candidates = ArrayList(queue.size + newItems.size)
                candidates.addAll(queue)
                for (newItem in newItems) {
                    val feedPrefs = newItem.feed!!.preferences
                    if (feedPrefs!!.autoDownload
                            && !candidates.contains(newItem)
                            && feedPrefs.filter.shouldAutoDownload(newItem)) {
                        candidates.add(newItem)
                    }
                }

                // filter items that are not auto downloadable
                val it = candidates.iterator()
                while (it.hasNext()) {
                    val item = it.next()
                    if (!item.isAutoDownloadEnabled
                            || item.isDownloaded
                            || !item.hasMedia()
                            || isPlaying(item.media)
                            || item.feed!!.isLocalFeed) {
                        it.remove()
                    }
                }

                val autoDownloadableEpisodes = candidates.size
                val downloadedEpisodes = getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.DOWNLOADED))
                val deletedEpisodes = build()
                    .makeRoomForEpisodes(context, autoDownloadableEpisodes)
                val cacheIsUnlimited =
                    episodeCacheSize == UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED
                val episodeCacheSize = episodeCacheSize
                val episodeSpaceLeft =
                    if (cacheIsUnlimited || episodeCacheSize >= downloadedEpisodes + autoDownloadableEpisodes) {
                        autoDownloadableEpisodes
                    } else {
                        episodeCacheSize - (downloadedEpisodes - deletedEpisodes)
                    }

                val itemsToDownload: List<FeedItem> = candidates.subList(0, episodeSpaceLeft)
                if (itemsToDownload.isNotEmpty()) {
                    Log.d(TAG, "Enqueueing " + itemsToDownload.size + " items for download")

                    for (episode in itemsToDownload) {
                        DownloadServiceInterface.get()?.download(context, episode)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "DownloadAlgorithm"
    }
}
