package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
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
import ac.mdiq.podcini.storage.utils.EpisodeUtil.indexOfItemWithId
import ac.mdiq.podcini.storage.utils.EpisodesPermutors.getPermutor
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.util.Log

import kotlinx.coroutines.Job
import java.util.*

object Queues {
    private val TAG: String = Queues::class.simpleName ?: "Anonymous"

    enum class EnqueueLocation {
        BACK, FRONT, AFTER_CURRENTLY_PLAYING, RANDOM
    }

    /**
     * Returns if the queue is in keep sorted mode.
     * Enables/disables the keep sorted mode of the queue.
     * @see .queueKeepSortedOrder
     */
    var isQueueKeepSorted: Boolean
        get() = appPrefs.getBoolean(UserPreferences.Prefs.prefQueueKeepSorted.name, false)
        set(keepSorted) {
            appPrefs.edit().putBoolean(UserPreferences.Prefs.prefQueueKeepSorted.name, keepSorted).apply()
        }

    /**
     * Returns the sort order for the queue keep sorted mode.
     * Note: This value is stored independently from the keep sorted state.
     * Sets the sort order for the queue keep sorted mode.
     * @see .isQueueKeepSorted
     */
    var queueKeepSortedOrder: EpisodeSortOrder?
        get() {
            val sortOrderStr = appPrefs.getString(UserPreferences.Prefs.prefQueueKeepSortedOrder.name, "use-default")
            return EpisodeSortOrder.parseWithDefault(sortOrderStr, EpisodeSortOrder.DATE_NEW_OLD)
        }
        set(sortOrder) {
            if (sortOrder == null) return
            appPrefs.edit().putString(UserPreferences.Prefs.prefQueueKeepSortedOrder.name, sortOrder.name).apply()
        }

    var enqueueLocation: EnqueueLocation
        get() {
            val valStr = appPrefs.getString(UserPreferences.Prefs.prefEnqueueLocation.name, EnqueueLocation.BACK.name)
            try { return EnqueueLocation.valueOf(valStr!!)
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
     * @param episodes               the Episode objects that should be added to the queue.
     */
    @JvmStatic @Synchronized
    fun addToQueue(vararg episodes: Episode) : Job {
        Logd(TAG, "addToQueue( ... ) called")
        return runOnIOScope {
            if (episodes.isEmpty()) return@runOnIOScope

            var queueModified = false
            val setInQueue = mutableListOf<Episode>()
            val events: MutableList<FlowEvent.QueueEvent> = ArrayList()
            val updatedItems: MutableList<Episode> = ArrayList()
            val positionCalculator = EnqueuePositionPolicy(enqueueLocation)
            val currentlyPlaying = curMedia
            var insertPosition = positionCalculator.calcPosition(curQueue.episodes, currentlyPlaying)

            val qItems = curQueue.episodes.toMutableList()
            val qItemIds = curQueue.episodeIds.toMutableList()
            val items_ = episodes.toList()
            for (episode in items_) {
                if (qItemIds.contains(episode.id)) continue
                events.add(FlowEvent.QueueEvent.added(episode, insertPosition))
                qItemIds.add(insertPosition, episode.id)
                updatedItems.add(episode)
                qItems.add(insertPosition, episode)
                queueModified = true
                if (episode.playState < PlayState.INQUEUE.code) setInQueue.add(episode)
                insertPosition++
            }
            if (queueModified) {
//                TODO: handle sorting
                applySortOrder(qItems, events)
                curQueue = upsert(curQueue) {
                    it.episodeIds.clear()
                    it.episodeIds.addAll(qItemIds)
                    it.update()
                }
                for (event in events) EventFlow.postEvent(event)
                setPlayState(PlayState.INQUEUE.code, false, *setInQueue.toTypedArray())
//                if (performAutoDownload) autodownloadEpisodeMedia(context)
            }
        }
    }

    suspend fun addToQueueSync(episode: Episode, queue_: PlayQueue? = null) {
        Logd(TAG, "addToQueueSync( ... ) called")
        val queue = queue_ ?: curQueue
        if (queue.episodeIds.contains(episode.id)) return

        val currentlyPlaying = curMedia
        val positionCalculator = EnqueuePositionPolicy(enqueueLocation)
        var insertPosition = positionCalculator.calcPosition(queue.episodes, currentlyPlaying)
        Logd(TAG, "addToQueueSync insertPosition: $insertPosition")

        val queueNew = upsert(queue) {
            if (!it.episodeIds.contains(episode.id)) it.episodeIds.add(insertPosition, episode.id)
            insertPosition++
            it.update()
        }
        if (queue.id == curQueue.id) curQueue = queueNew

        if (episode.playState < PlayState.INQUEUE.code) setPlayState(PlayState.INQUEUE.code, false, episode)
        if (queue.id == curQueue.id) EventFlow.postEvent(FlowEvent.QueueEvent.added(episode, insertPosition))
//                if (performAutoDownload) autodownloadEpisodeMedia(context)
    }

    /**
     * Sorts the queue depending on the configured sort order.
     * If the queue is not in keep sorted mode, nothing happens.
     * @param queueItems  The queue to be sorted.
     * @param events Replaces the events by a single SORT event if the list has to be sorted automatically.
     */
    private fun applySortOrder(queueItems: MutableList<Episode>, events: MutableList<FlowEvent.QueueEvent>) {
        // queue is not in keep sorted mode, there's nothing to do
        if (!isQueueKeepSorted) return

        // Sort queue by configured sort order
        val sortOrder = queueKeepSortedOrder
        // do not shuffle the list on every change
        if (sortOrder == EpisodeSortOrder.RANDOM) return

        if (sortOrder != null) {
            val permutor = getPermutor(sortOrder)
            permutor.reorder(queueItems)
        }
        // Replace ADDED events by a single SORTED event
        events.clear()
        events.add(FlowEvent.QueueEvent.sorted(queueItems))
    }

    fun clearQueue() : Job {
        Logd(TAG, "clearQueue called")
        return runOnIOScope {
            curQueue = upsert(curQueue) {
                it.idsBinList.addAll(it.episodeIds)
                it.episodeIds.clear()
                it.update()
            }
            for (e in curQueue.episodes) {
                if (e.playState < PlayState.SKIPPED.code) setPlayState(PlayState.SKIPPED.code, false, e)
            }
            curQueue.episodes.clear()
            EventFlow.postEvent(FlowEvent.QueueEvent.cleared())
        }
    }

    fun removeFromAllQueuesSync(vararg episodes: Episode) {
        Logd(TAG, "removeFromAllQueuesSync called ")
        val queues = realm.query(PlayQueue::class).find()
        for (q in queues) {
            if (q.id != curQueue.id) removeFromQueueSync(q, *episodes)
        }
//        ensure curQueue is last updated
        if (curQueue.size() > 0) removeFromQueueSync(curQueue, *episodes)
        else upsertBlk(curQueue) { it.update() }
    }

    /**
     * @param queue_    if null, use curQueue
     */
    internal fun removeFromQueueSync(queue_: PlayQueue?, vararg episodes: Episode) {
        Logd(TAG, "removeFromQueueSync called ")
        if (episodes.isEmpty()) return
        var queue = queue_ ?: curQueue
        if (queue.size() == 0) return

        val events: MutableList<FlowEvent.QueueEvent> = mutableListOf()
        val indicesToRemove: MutableList<Int> = mutableListOf()
        val qItems = queue.episodes.toMutableList()
        val eList = episodes.toList()
        for (i in qItems.indices) {
            val episode = qItems[i]
            if (indexOfItemWithId(eList, episode.id) >= 0) {
                Logd(TAG, "removing from queue: ${episode.id} ${episode.title}")
                indicesToRemove.add(i)
//                if (setState && episode.playState < PlayState.SKIPPED.code) setPlayState(PlayState.SKIPPED.code, false, episode)
                if (queue.id == curQueue.id) events.add(FlowEvent.QueueEvent.removed(episode))
            }
        }
        if (indicesToRemove.isNotEmpty()) {
            val queueNew = upsertBlk(queue) {
                for (i in indicesToRemove.indices.reversed()) {
                    val id = qItems[indicesToRemove[i]].id
                    it.idsBinList.remove(id)
                    it.idsBinList.add(id)
                    qItems.removeAt(indicesToRemove[i])
                }
                it.update()
                it.episodeIds.clear()
                for (e in qItems) it.episodeIds.add(e.id)
            }
            if (queueNew.id == curQueue.id) {
                queueNew.episodes.clear()
                curQueue = queueNew
            }
            for (event in events) EventFlow.postEvent(event)
        } else Logd(TAG, "Queue was not modified by call to removeQueueItem")
    }

    suspend fun removeFromAllQueuesQuiet(episodeIds: List<Long>) {
        Logd(TAG, "removeFromAllQueuesQuiet called ")
        var idsInQueuesToRemove: MutableSet<Long>
        val queues = realm.query(PlayQueue::class).find()
        for (q in queues) {
            if (q.size() == 0 || q.id == curQueue.id) continue
            idsInQueuesToRemove = q.episodeIds.intersect(episodeIds.toSet()).toMutableSet()
            if (idsInQueuesToRemove.isNotEmpty()) {
                val eList = realm.query(Episode::class).query("id IN $0", idsInQueuesToRemove).find()
                for (e in eList) {
                    if (e.playState < PlayState.SKIPPED.code) setPlayState(PlayState.SKIPPED.code, false, e)
                }
                upsert(q) {
                    it.idsBinList.removeAll(idsInQueuesToRemove)
                    it.idsBinList.addAll(idsInQueuesToRemove)
                    val qeids = it.episodeIds.minus(idsInQueuesToRemove)
                    it.episodeIds.clear()
                    it.episodeIds.addAll(qeids)
                    it.update()
                }
            }
        }
        //        ensure curQueue is last updated
        val q = curQueue
        if (q.size() == 0) {
            upsert(q) { it.update() }
            return
        }
        idsInQueuesToRemove = q.episodeIds.intersect(episodeIds.toSet()).toMutableSet()
        if (idsInQueuesToRemove.isNotEmpty()) {
            val eList = realm.query(Episode::class).query("id IN $0", idsInQueuesToRemove).find()
            for (e in eList) {
                if (e.playState < PlayState.SKIPPED.code) setPlayState(PlayState.SKIPPED.code, false, e)
            }
            curQueue = upsert(q) {
                it.idsBinList.removeAll(idsInQueuesToRemove)
                it.idsBinList.addAll(idsInQueuesToRemove)
                val qeids = it.episodeIds.minus(idsInQueuesToRemove)
                it.episodeIds.clear()
                it.episodeIds.addAll(qeids)
                it.update()
            }
        }
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
        curQueue.episodes.clear()
//        curQueue.episodes.addAll(episodes)
        curQueue = upsertBlk(curQueue) {
            it.episodeIds.clear()
            for (e in episodes) it.episodeIds.add(e.id)
            it.update()
        }
    }

    fun inAnyQueue(episode: Episode): Boolean {
        val queues = realm.query(PlayQueue::class).find()
        for (q in queues) {
            if (q.contains(episode)) return true
        }
        return false
    }

    class EnqueuePositionPolicy(private val enqueueLocation: EnqueueLocation) {
        /**
         * Determine the position (0-based) that the item(s) should be inserted to the named queue.
         * @param queueItems           the queue to which the item is to be inserted
         * @param currentPlaying     the currently playing media
         */
        fun calcPosition(queueItems: List<Episode>, currentPlaying: Playable?): Int {
            if (queueItems.isEmpty()) return 0
            when (enqueueLocation) {
                EnqueueLocation.BACK -> return queueItems.size
                // Return not necessarily 0, so that when a list of items are downloaded and enqueued
                // in succession of calls (e.g., users manually tapping download one by one),
                // the items enqueued are kept the same order.
                // Simply returning 0 will reverse the order.
                EnqueueLocation.FRONT -> return getPositionOfFirstNonDownloadingItem(0, queueItems)
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
            val curPlayingItemId = currentPlaying.episodeOrFetch()?.id
            for (i in queueItems.indices) {
                if (curPlayingItemId == queueItems[i].id) return i
            }
            return -1
        }
    }
}