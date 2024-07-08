package ac.mdiq.podcini.storage.algorithms

import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.net.utils.NetworkUtils.isAutoDownloadAllowed
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.InTheatre.isCurMedia
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.episodeCacheSize
import ac.mdiq.podcini.preferences.UserPreferences.isEnableAutodownload
import ac.mdiq.podcini.preferences.UserPreferences.isEnableAutodownloadOnBattery
import ac.mdiq.podcini.storage.database.Episodes.getEpisodes
import ac.mdiq.podcini.storage.database.Episodes.getEpisodesCount
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.SortOrder
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

object AutoDownloads {
    private val TAG: String = AutoDownloads::class.simpleName ?: "Anonymous"

    /**
     * Executor service used by the autodownloadUndownloadedEpisodes method.
     */
    private val autodownloadExec: ExecutorService = Executors.newSingleThreadExecutor { r: Runnable? ->
        val t = Thread(r)
        t.priority = Thread.MIN_PRIORITY
        t
    }

    var downloadAlgorithm = AutoDownloadAlgorithm()

    /**
     * Looks for non-downloaded episodes in the queue or list of unread episodes and request a download if
     * 1. Network is available
     * 2. The device is charging or the user allows auto download on battery
     * 3. There is free space in the episode cache
     * This method is executed on an internal single thread executor.
     * @param context  Used for accessing the DB.
     * @return A Future that can be used for waiting for the methods completion.
     */
    @UnstableApi
    fun autodownloadEpisodeMedia(context: Context): Future<*> {
        Logd(TAG, "autodownloadEpisodeMedia")
        return autodownloadExec.submit(downloadAlgorithm.autoDownloadEpisodeMedia(context))
    }

    /**
     * Implements the automatic download algorithm used by Podcini. This class assumes that
     * the client uses the [EpisodeCleanupAlgorithm].
     */
    open class AutoDownloadAlgorithm {
        /**
         * Looks for undownloaded episodes in the queue or list of new items and request a download if
         * 1. Network is available
         * 2. The device is charging or the user allows auto download on battery
         * 3. There is free space in the episode cache
         * This method is executed on an internal single thread executor.
         * @param context  Used for accessing the DB.
         * @return A Runnable that will be submitted to an ExecutorService.
         */
        @UnstableApi
        open fun autoDownloadEpisodeMedia(context: Context): Runnable? {
            return Runnable {
                // true if we should auto download based on network status
//            val networkShouldAutoDl = (isAutoDownloadAllowed)
                val networkShouldAutoDl = (isAutoDownloadAllowed && isEnableAutodownload)
                // true if we should auto download based on power status
                val powerShouldAutoDl = (deviceCharging(context) || isEnableAutodownloadOnBattery)
                Logd(TAG, "prepare autoDownloadUndownloadedItems $networkShouldAutoDl $powerShouldAutoDl")
                // we should only auto download if both network AND power are happy
                if (networkShouldAutoDl && powerShouldAutoDl) {
                    Logd(TAG, "Performing auto-dl of undownloaded episodes")
                    val candidates: MutableList<Episode>
                    val queue = curQueue.episodes
                    val newItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.NEW), SortOrder.DATE_NEW_OLD)
                    Logd(TAG, "newItems: ${newItems.size}")
                    candidates = ArrayList(queue.size + newItems.size)
                    candidates.addAll(queue)
                    for (newItem in newItems) {
                        val feedPrefs = newItem.feed!!.preferences
                        if (feedPrefs!!.autoDownload && !candidates.contains(newItem) && feedPrefs.autoDownloadFilter.shouldAutoDownload(newItem)) candidates.add(newItem)
                    }
                    // filter items that are not auto downloadable
                    val it = candidates.iterator()
                    while (it.hasNext()) {
                        val item = it.next()
                        if (!item.isAutoDownloadEnabled || item.isDownloaded || item.media == null || isCurMedia(item.media) || item.feed?.isLocalFeed == true)
                            it.remove()
                    }
                    val autoDownloadableEpisodes = candidates.size
                    val downloadedEpisodes = getEpisodesCount(EpisodeFilter(EpisodeFilter.DOWNLOADED))
                    val deletedEpisodes = AutoCleanups.build().makeRoomForEpisodes(context, autoDownloadableEpisodes)
                    val cacheIsUnlimited = episodeCacheSize == UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED
                    val episodeCacheSize = episodeCacheSize
                    val episodeSpaceLeft =
                        if (cacheIsUnlimited || episodeCacheSize >= downloadedEpisodes + autoDownloadableEpisodes) autoDownloadableEpisodes
                        else episodeCacheSize - (downloadedEpisodes - deletedEpisodes)
                    val itemsToDownload: List<Episode> = candidates.subList(0, episodeSpaceLeft)
                    if (itemsToDownload.isNotEmpty()) {
                        Logd(TAG, "Enqueueing " + itemsToDownload.size + " items for download")
                        for (episode in itemsToDownload) DownloadServiceInterface.get()?.download(context, episode)
                    }
                }
                else Logd(TAG, "not auto downloaded networkShouldAutoDl: $networkShouldAutoDl powerShouldAutoDl $powerShouldAutoDl")
            }
        }
        /**
         * @return true if the device is charging
         */
        private fun deviceCharging(context: Context): Boolean {
            // from http://developer.android.com/training/monitoring-device-state/battery-monitoring.html
            val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, iFilter)

            val status = batteryStatus!!.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            return (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
        }
        companion object {
            private val TAG: String = AutoDownloadAlgorithm::class.simpleName ?: "Anonymous"
        }
    }
}