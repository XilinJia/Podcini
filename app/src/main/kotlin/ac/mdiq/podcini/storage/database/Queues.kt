package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.storage.database.Episodes.setPlayState
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.utils.EpisodesPermutors.getPermutor
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Job
import java.util.*

object Queues {
    private val TAG: String = Queues::class.simpleName ?: "Anonymous"

    enum class EnqueueLocation {
        BACK, FRONT, AFTER_CURRENTLY_PLAYING, RANDOM
    }

    var isQueueLocked: Boolean
        get() = appPrefs.getBoolean(UserPreferences.Prefs.prefQueueLocked.name, false)
        set(locked) {
            appPrefs.edit().putBoolean(UserPreferences.Prefs.prefQueueLocked.name, locked).apply()
        }

    var isQueueKeepSorted: Boolean
        /**
         * Returns if the queue is in keep sorted mode.
         * @see .queueKeepSortedOrder
         */
        get() = appPrefs.getBoolean(UserPreferences.Prefs.prefQueueKeepSorted.name, false)
        /**
         * Enables/disables the keep sorted mode of the queue.
         * @see .queueKeepSortedOrder
         */
        set(keepSorted) {
            appPrefs.edit().putBoolean(UserPreferences.Prefs.prefQueueKeepSorted.name, keepSorted).apply()
        }

    var queueKeepSortedOrder: EpisodeSortOrder?
        /**
         * Returns the sort order for the queue keep sorted mode.
         * Note: This value is stored independently from the keep sorted state.
         * @see .isQueueKeepSorted
         */
        get() {
            val sortOrderStr = appPrefs.getString(UserPreferences.Prefs.prefQueueKeepSortedOrder.name, "use-default")
            return EpisodeSortOrder.parseWithDefault(sortOrderStr, EpisodeSortOrder.DATE_NEW_OLD)
        }
        /**
         * Sets the sort order for the queue keep sorted mode.
         * @see .setQueueKeepSorted
         */
        set(sortOrder) {
            if (sortOrder == null) return
            appPrefs.edit().putString(UserPreferences.Prefs.prefQueueKeepSortedOrder.name, sortOrder.name).apply()
        }

    var enqueueLocation: EnqueueLocation
        get() {
            val valStr = appPrefs.getString(UserPreferences.Prefs.prefEnqueueLocation.name, EnqueueLocation.BACK.name)
            try {
                return EnqueueLocation.valueOf(valStr!!)
            } catch (t: Throwable) {
                // should never happen but just in case
                Log.e(TAG, "getEnqueueLocation: invalid value '$valStr' Use default.", t)
                return EnqueueLocation.BACK
            }
        }
        set(location) {
            appPrefs.edit().putString(UserPreferences.Prefs.prefEnqueueLocation.name, location.name).apply()
        }

    fun getInQueueEpisodeIds(): Set<Long> {
        Logd(TAG, "getQueueIDList() called")
        val queues = realm.query(PlayQueue::class).find()
        val ids = mutableSetOf<Long>()
        for (queue in queues) {
            ids.addAll(queue.episodeIds)
        }
        return ids
    }

    /**
     * Appends Episode objects to the end of the queue. The 'read'-attribute of all episodes will be set to true.
     * If a Episode is already in the queue, the Episode will not change its position in the queue.
     * @param markAsUnplayed      true if the episodes should be marked as unplayed when enqueueing
     * @param episodes               the Episode objects that should be added to the queue.
     */
    @UnstableApi @JvmStatic @Synchronized
    fun addToQueue(markAsUnplayed: Boolean, vararg episodes: Episode) : Job {
        Logd(TAG, "addToQueue( ... ) called")
        return runOnIOScope {
            if (episodes.isEmpty()) return@runOnIOScope

            var queueModified = false
            val markAsUnplayeds = mutableListOf<Episode>()
            val events: MutableList<FlowEvent.QueueEvent> = ArrayList()
            val updatedItems: MutableList<Episode> = ArrayList()
            val positionCalculator = EnqueuePositionCalculator(enqueueLocation)
            val currentlyPlaying = curMedia
            var insertPosition = positionCalculator.calcPosition(curQueue.episodes, currentlyPlaying)

            val qItems = curQueue.episodes.toMutableList()
            val items_ = episodes.toList()
            for (episode in items_) {
                if (curQueue.episodeIds.contains(episode.id)) continue

                events.add(FlowEvent.QueueEvent.added(episode, insertPosition))
                curQueue.episodeIds.add(insertPosition, episode.id)
                updatedItems.add(episode)
                qItems.add(insertPosition, episode)
                queueModified = true
                if (episode.isNew) markAsUnplayeds.add(episode)
                insertPosition++
            }
            if (queueModified) {
                applySortOrder(qItems, events)
                curQueue.update()
                curQueue.episodes.clear()
                curQueue.episodes.addAll(qItems)
                upsert(curQueue) {}

                for (event in events) {
                    EventFlow.postEvent(event)
                }
//                EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(updatedItems))
                if (markAsUnplayed && markAsUnplayeds.size > 0) setPlayState(Episode.UNPLAYED, false, *markAsUnplayeds.toTypedArray())
//                if (performAutoDownload) autodownloadEpisodeMedia(context)
            }
        }
    }

    suspend fun addToQueueSync(markAsUnplayed: Boolean, episode: Episode, queue_: PlayQueue? = null) {
        Logd(TAG, "addToQueueSync( ... ) called")

        val queue = queue_ ?: curQueue
        val currentlyPlaying = curMedia
        val positionCalculator = EnqueuePositionCalculator(enqueueLocation)
        var insertPosition = positionCalculator.calcPosition(queue.episodes, currentlyPlaying)

        if (queue.episodeIds.contains(episode.id)) return

        queue.episodeIds.add(insertPosition, episode.id)
        queue.episodes.add(insertPosition, episode)
        insertPosition++
        if (queue.id == curQueue.id) queue.update()
        upsert(queue) {}

        if (markAsUnplayed && episode.isNew) setPlayState(Episode.UNPLAYED, false, episode)

        if (queue.id == curQueue.id) EventFlow.postEvent(FlowEvent.QueueEvent.added(episode, insertPosition))
//                if (performAutoDownload) autodownloadEpisodeMedia(context)
    }

    /**
     * Sorts the queue depending on the configured sort order.
     * If the queue is not in keep sorted mode, nothing happens.
     * @param queue  The queue to be sorted.
     * @param events Replaces the events by a single SORT event if the list has to be sorted automatically.
     */
    private fun applySortOrder(queue: MutableList<Episode>, events: MutableList<FlowEvent.QueueEvent>) {
        // queue is not in keep sorted mode, there's nothing to do
        if (!isQueueKeepSorted) return

        // Sort queue by configured sort order
        val sortOrder = queueKeepSortedOrder
        // do not shuffle the list on every change
        if (sortOrder == EpisodeSortOrder.RANDOM) return

        if (sortOrder != null) {
            val permutor = getPermutor(sortOrder)
            permutor.reorder(queue)
        }
        // Replace ADDED events by a single SORTED event
        events.clear()
        events.add(FlowEvent.QueueEvent.sorted(queue))
    }

    fun clearQueue() : Job {
        Logd(TAG, "clearQueue called")
        return runOnIOScope {
            curQueue.update()
            curQueue.episodes.clear()
            curQueue.episodeIds.clear()
            upsert(curQueue) {}
            EventFlow.postEvent(FlowEvent.QueueEvent.cleared())
        }
    }

    /**
     * Removes a Episode object from the queue.
     * @param episodes                FeedItems that should be removed.
     */
    @OptIn(UnstableApi::class) @JvmStatic
    fun removeFromQueue(vararg episodes: Episode) : Job {
        return runOnIOScope { removeFromQueueSync(curQueue, *episodes) }
    }

    @OptIn(UnstableApi::class)
    fun removeFromAllQueuesSync(vararg episodes: Episode) {
        Logd(TAG, "removeFromAllQueues called ")
        val queues = realm.query(PlayQueue::class).find()
        for (q in queues) {
            if (q.id != curQueue.id) removeFromQueueSync(q, *episodes)
        }
//        ensure curQueue is last updated
        removeFromQueueSync(curQueue, *episodes)
    }

    /**
     * @param queue_    if null, use curQueue
     */
    @UnstableApi
    internal fun removeFromQueueSync(queue_: PlayQueue?, vararg episodes: Episode) {
        Logd(TAG, "removeFromQueueSync called ")
        if (episodes.isEmpty()) return

        var queue = queue_ ?: curQueue
        val events: MutableList<FlowEvent.QueueEvent> = ArrayList()
        val pos: MutableList<Int> = mutableListOf()
        val qItems = queue.episodes.toMutableList()
        for (i in qItems.indices) {
            val episode = qItems[i]
            if (episodes.contains(episode)) {
                Logd(TAG, "removing from queue: ${episode.id} ${episode.title}")
                pos.add(i)
                if (queue.id == curQueue.id) events.add(FlowEvent.QueueEvent.removed(episode))
            }
        }
        if (pos.isNotEmpty()) {
            for (i in pos.indices.reversed()) qItems.removeAt(pos[i])
            queue.update()
            queue.episodeIds.clear()
            for (e in qItems) queue.episodeIds.add(e.id)
            upsertBlk(queue) {}
            if (queue.id == curQueue.id) {
                queue.episodes.clear()
                queue.episodes.addAll(qItems)
            }
            for (event in events) EventFlow.postEvent(event)
        } else Logd(TAG, "Queue was not modified by call to removeQueueItem")
    }

    suspend fun removeFromAllQueuesQuiet(episodeIds: List<Long>) {
        Logd(TAG, "removeFromAllQueuesQuiet called ")
        var eidsInQueues: MutableSet<Long>
        val queues = realm.query(PlayQueue::class).find()
        for (q in queues) {
            if (q.id == curQueue.id) continue
            eidsInQueues = q.episodeIds.intersect(episodeIds.toSet()).toMutableSet()
            if (eidsInQueues.isNotEmpty()) {
                val qeids = q.episodeIds.minus(eidsInQueues)
                upsert(q) {
                    it.episodeIds.clear()
                    it.episodeIds.addAll(qeids)
                    it.update()
                }
            }
        }
        //        ensure curQueue is last updated
        val q = curQueue
        eidsInQueues = q.episodeIds.intersect(episodeIds.toSet()).toMutableSet()
        if (eidsInQueues.isNotEmpty()) {
            val qeids = q.episodeIds.minus(eidsInQueues)
            upsert(q) {
                it.episodeIds.clear()
                it.episodeIds.addAll(qeids)
                it.update()
            }
        }
    }

    /**
     * Changes the position of a Episode in the queue.
     * @param from            Source index. Must be in range 0..queue.size()-1.
     * @param to              Destination index. Must be in range 0..queue.size()-1.
     * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
     * false if the caller wants to avoid unexpected updates of the GUI.
     * @throws IndexOutOfBoundsException if (to < 0 || to >= queue.size()) || (from < 0 || from >= queue.size())
     */
    fun moveInQueue(from: Int, to: Int, broadcastUpdate: Boolean) : Job {
        return runOnIOScope { moveInQueueSync(from, to, broadcastUpdate) }
    }

    /**
     * Changes the position of a Episode in the queue.
     * This function must be run using the ExecutorService (dbExec).
     * @param from            Source index. Must be in range 0..queue.size()-1.
     * @param to              Destination index. Must be in range 0..queue.size()-1.
     * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
     * false if the caller wants to avoid unexpected updates of the GUI.
     * @throws IndexOutOfBoundsException if (to < 0 || to >= queue.size()) || (from < 0 || from >= queue.size())
     */
    fun moveInQueueSync(from: Int, to: Int, broadcastUpdate: Boolean) {
        val episodes = curQueue.episodes.toMutableList()
        if (episodes.isNotEmpty()) {
            if ((from in 0 ..< episodes.size) && (to in 0..<episodes.size)) {
                val episode = episodes.removeAt(from)
                episodes.add(to, episode)
                if (broadcastUpdate) EventFlow.postEvent(FlowEvent.QueueEvent.moved(episode, to))
            }
        } else Log.e(TAG, "moveQueueItemHelper: Could not load queue")
        curQueue.update()
        curQueue.episodes.clear()
        curQueue.episodes.addAll(episodes)
        curQueue.episodeIds.clear()
        for (e in curQueue.episodes) curQueue.episodeIds.add(e.id)
        upsertBlk(curQueue) {}
    }

    class EnqueuePositionCalculator(private val enqueueLocation: EnqueueLocation) {
        /**
         * Determine the position (0-based) that the item(s) should be inserted to the named queue.
         * @param queueItems           the queue to which the item is to be inserted
         * @param currentPlaying     the currently playing media
         */
        fun calcPosition(queueItems: List<Episode>, currentPlaying: Playable?): Int {
            when (enqueueLocation) {
                EnqueueLocation.BACK -> return queueItems.size
                EnqueueLocation.FRONT ->                 // Return not necessarily 0, so that when a list of items are downloaded and enqueued
                    // in succession of calls (e.g., users manually tapping download one by one),
                    // the items enqueued are kept the same order.
                    // Simply returning 0 will reverse the order.
                    return getPositionOfFirstNonDownloadingItem(0, queueItems)
                EnqueueLocation.AFTER_CURRENTLY_PLAYING -> {
                    val currentlyPlayingPosition = getCurrentlyPlayingPosition(queueItems, currentPlaying)
                    return getPositionOfFirstNonDownloadingItem(currentlyPlayingPosition + 1, queueItems)
                }
                EnqueueLocation.RANDOM -> {
                    val random = Random()
                    return random.nextInt(queueItems.size + 1)
                }
                else -> throw AssertionError("calcPosition() : unrecognized enqueueLocation option: $enqueueLocation")
            }
        }
        private fun getPositionOfFirstNonDownloadingItem(startPosition: Int, queueItems: List<Episode>): Int {
            val curQueueSize = queueItems.size
            for (i in startPosition until curQueueSize) {
                if (!isItemAtPositionDownloading(i, queueItems)) return i
            }
            return curQueueSize
        }
        private fun isItemAtPositionDownloading(position: Int, queueItems: List<Episode>): Boolean {
            val curItem = try { queueItems[position] } catch (e: IndexOutOfBoundsException) { null }
            if (curItem?.media?.downloadUrl == null) return false
            return curItem.media != null && DownloadServiceInterface.get()?.isDownloadingEpisode(curItem.media!!.downloadUrl!!)?:false
        }
        private fun getCurrentlyPlayingPosition(queueItems: List<Episode>, currentPlaying: Playable?): Int {
            if (currentPlaying !is EpisodeMedia) return -1
            val curPlayingItemId = currentPlaying.episode!!.id
            for (i in queueItems.indices) {
                if (curPlayingItemId == queueItems[i].id) return i
            }
            return -1
        }
    }
}