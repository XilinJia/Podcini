package ac.mdiq.podcini.storage.algorithms

import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.utils.NetworkUtils.isAutoDownloadAllowed
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.InTheatre.isCurMedia
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.episodeCacheSize
import ac.mdiq.podcini.preferences.UserPreferences.isEnableAutodownload
import ac.mdiq.podcini.preferences.UserPreferences.isEnableAutodownloadOnBattery
import ac.mdiq.podcini.storage.database.Episodes.getEpisodesCount
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.media3.common.util.UnstableApi
import io.realm.kotlin.UpdatePolicy
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

    var downloadAlgorithm: AutoDownloadAlgorithm = FeedBasedAutoDLAlgorithm()

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
    fun autodownloadEpisodeMedia(context: Context, feeds: List<Feed>? = null): Future<*> {
        Logd(TAG, "autodownloadEpisodeMedia")
        return autodownloadExec.submit(downloadAlgorithm.autoDownloadEpisodeMedia(context, feeds))
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
//        likely not needed
        @UnstableApi
        open fun autoDownloadEpisodeMedia(context: Context, feeds: List<Feed>? = null): Runnable? {
            return Runnable {}
        }

        /**
         * @return true if the device is charging
         */
        protected fun deviceCharging(context: Context): Boolean {
            // from http://developer.android.com/training/monitoring-device-state/battery-monitoring.html
            val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, iFilter)

            val status = batteryStatus!!.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            return (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
        }
    }

    class FeedBasedAutoDLAlgorithm : AutoDownloadAlgorithm() {

        @UnstableApi
        override fun autoDownloadEpisodeMedia(context: Context, feeds: List<Feed>?): Runnable {
            return Runnable {
                // true if we should auto download based on network status
                val networkShouldAutoDl = (isAutoDownloadAllowed && isEnableAutodownload)
                // true if we should auto download based on power status
                val powerShouldAutoDl = (deviceCharging(context) || isEnableAutodownloadOnBattery)
                Logd(TAG, "autoDownloadEpisodeMedia prepare $networkShouldAutoDl $powerShouldAutoDl")
                // we should only auto download if both network AND power are happy
                if (networkShouldAutoDl && powerShouldAutoDl) {
                    Logd(TAG, "autoDownloadEpisodeMedia Performing auto-dl of undownloaded episodes")
                    val candidates: MutableSet<Episode> = mutableSetOf()
                    val queueItems = realm.query(Episode::class).query("id IN $0 AND media.downloaded == false", curQueue.episodeIds).find()
                    Logd(TAG, "autoDownloadEpisodeMedia add from queue: ${queueItems.size}")
                    if (queueItems.isNotEmpty()) candidates.addAll(queueItems)
                    val feeds = feeds ?: getFeedList()
                    feeds.forEach { f ->
                        if (f.preferences?.autoDownload == true && !f.isLocalFeed) {
                            var episodes = mutableListOf<Episode>()
                            val dlFilter =
                                if (f.preferences?.countingPlayed == true) EpisodeFilter(EpisodeFilter.States.downloaded.name)
                                else EpisodeFilter(EpisodeFilter.States.downloaded.name, EpisodeFilter.States.unplayed.name, EpisodeFilter.States.inQueue.name,
                                    EpisodeFilter.States.inProgress.name, EpisodeFilter.States.skipped.name)
                            val downloadedCount = getEpisodesCount(dlFilter, f.id)
                            val allowedDLCount = (f.preferences?.autoDLMaxEpisodes?:0) - downloadedCount
                            Logd(TAG, "autoDownloadEpisodeMedia ${f.preferences?.autoDLMaxEpisodes} downloadedCount: $downloadedCount allowedDLCount: $allowedDLCount")
                            if (allowedDLCount > 0) {
                                var queryString = "feedId == ${f.id} AND isAutoDownloadEnabled == true AND media != nil AND media.downloaded == false"
                                when (f.preferences?.autoDLPolicy) {
                                    FeedPreferences.AutoDownloadPolicy.ONLY_NEW -> {
                                        queryString += " AND playState == ${PlayState.NEW.code} SORT(pubDate DESC) LIMIT(${3*allowedDLCount})"
                                        episodes = realm.query(Episode::class).query(queryString).find().toMutableList()
                                    }
                                    FeedPreferences.AutoDownloadPolicy.NEWER -> {
                                        queryString += " AND playState < ${PlayState.SKIPPED.code} SORT(pubDate DESC) LIMIT(${3*allowedDLCount})"
                                        episodes = realm.query(Episode::class).query(queryString).find().toMutableList()
                                    }
                                    FeedPreferences.AutoDownloadPolicy.OLDER -> {
                                        queryString += " AND playState < ${PlayState.SKIPPED.code} SORT(pubDate ASC) LIMIT(${3*allowedDLCount})"
                                        episodes = realm.query(Episode::class).query(queryString).find().toMutableList()
                                    }
                                    else -> {}
                                }
                                if (episodes.isNotEmpty()) {
                                    var count = 0
                                    for (e in episodes) {
                                        if (isCurMedia(e.media)) continue
                                        if (f.preferences?.autoDownloadFilter?.shouldAutoDownload(e) == true) {
                                            Logd(TAG, "autoDownloadEpisodeMedia add to cadidates: ${e.title} ${e.isDownloaded}")
                                            candidates.add(e)
                                            if (++count >= allowedDLCount) break
                                        } else upsertBlk(e) { it.setPlayed(true)}
                                    }
                                }
                            }
                            episodes.clear()
                            Logd(TAG, "autoDownloadEpisodeMedia ${f.title} candidate size: ${candidates.size}")
                        }
                        runOnIOScope {
                            realm.write {
                                while (true) {
                                    val episodesNew = query(Episode::class, "feedId == ${f.id} AND playState == ${PlayState.NEW.code} LIMIT(20)").find()
                                    if (episodesNew.isEmpty()) break
                                    Logd(TAG, "autoDownloadEpisodeMedia episodesNew: ${episodesNew.size}")
                                    episodesNew.map { e ->
                                        e.setPlayed(false)
                                        Logd(TAG, "autoDownloadEpisodeMedia reset NEW ${e.title} ${e.playState}")
                                        copyToRealm(e, UpdatePolicy.ALL)
                                    }
                                }
                            }
//                            TODO: probably need to send an event
                        }
                    }
                    if (candidates.isNotEmpty()) {
                        val autoDownloadableCount = candidates.size
                        val downloadedCount = getEpisodesCount(EpisodeFilter(EpisodeFilter.States.downloaded.name))
                        val deletedCount = AutoCleanups.build().makeRoomForEpisodes(context, autoDownloadableCount)
                        val cacheIsUnlimited = episodeCacheSize == UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED
                        val allowedCount =
                            if (cacheIsUnlimited || episodeCacheSize >= downloadedCount + autoDownloadableCount) autoDownloadableCount
                            else episodeCacheSize - (downloadedCount - deletedCount)
                        if (allowedCount in 0..candidates.size) {
                            val itemsToDownload: MutableList<Episode> = candidates.toMutableList().subList(0, allowedCount)
                            if (itemsToDownload.isNotEmpty()) {
                                Logd(TAG, "Enqueueing " + itemsToDownload.size + " items for download")
                                for (episode in itemsToDownload) DownloadServiceInterface.get()?.download(context, episode)
                            }
                            itemsToDownload.clear()
                        }
                        candidates.clear()
                    }
                }
                else Logd(TAG, "not auto downloaded networkShouldAutoDl: $networkShouldAutoDl powerShouldAutoDl $powerShouldAutoDl")
            }
        }
    }
}