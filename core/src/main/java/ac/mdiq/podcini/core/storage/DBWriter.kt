package ac.mdiq.podcini.core.storage

import android.app.backup.BackupManager
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import ac.mdiq.podcini.core.R
import ac.mdiq.podcini.core.event.DownloadLogEvent
import ac.mdiq.podcini.core.feed.FeedEvent
import ac.mdiq.podcini.core.feed.LocalFeedUpdater.updateFeed
import ac.mdiq.podcini.core.preferences.PlaybackPreferences.Companion.createInstanceFromPreferences
import ac.mdiq.podcini.core.preferences.PlaybackPreferences.Companion.currentlyPlayingFeedMediaId
import ac.mdiq.podcini.core.preferences.PlaybackPreferences.Companion.writeNoMediaPlaying
import ac.mdiq.podcini.core.service.playback.PlaybackServiceInterface
import ac.mdiq.podcini.core.storage.DBReader.getFeed
import ac.mdiq.podcini.core.storage.DBReader.getFeedItem
import ac.mdiq.podcini.core.storage.DBReader.getFeedItemList
import ac.mdiq.podcini.core.storage.DBReader.getFeedMedia
import ac.mdiq.podcini.core.storage.DBReader.getQueue
import ac.mdiq.podcini.core.storage.DBReader.getQueueIDList
import ac.mdiq.podcini.core.storage.DBTasks.autodownloadUndownloadedItems
import ac.mdiq.podcini.core.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.core.util.FeedItemPermutors.getPermutor
import ac.mdiq.podcini.core.util.IntentUtils.sendLocalBroadcast
import ac.mdiq.podcini.core.util.LongList
import ac.mdiq.podcini.event.*
import ac.mdiq.podcini.event.FeedItemEvent.Companion.updated
import ac.mdiq.podcini.event.QueueEvent.Companion.added
import ac.mdiq.podcini.event.QueueEvent.Companion.cleared
import ac.mdiq.podcini.event.QueueEvent.Companion.irreversibleRemoved
import ac.mdiq.podcini.event.QueueEvent.Companion.moved
import ac.mdiq.podcini.event.QueueEvent.Companion.removed
import ac.mdiq.podcini.event.playback.PlaybackHistoryEvent
import ac.mdiq.podcini.model.download.DownloadResult
import ac.mdiq.podcini.model.feed.*
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.storage.database.PodDBAdapter.Companion.getInstance
import ac.mdiq.podcini.storage.preferences.UserPreferences.enqueueLocation
import ac.mdiq.podcini.storage.preferences.UserPreferences.isQueueKeepSorted
import ac.mdiq.podcini.storage.preferences.UserPreferences.queueKeepSortedOrder
import ac.mdiq.podcini.storage.preferences.UserPreferences.shouldDeleteRemoveFromQueue
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Provides methods for writing data to Podcini's database.
 * In general, DBWriter-methods will be executed on an internal ExecutorService.
 * Some methods return a Future-object which the caller can use for waiting for the method's completion. The returned Future's
 * will NOT contain any results.
 */
@UnstableApi object DBWriter {
    private const val TAG = "DBWriter"

    private val dbExec: ExecutorService = Executors.newSingleThreadExecutor { r: Runnable? ->
        val t = Thread(r)
        t.name = "DatabaseExecutor"
        t.priority = Thread.MIN_PRIORITY
        t
    }

    /**
     * Wait until all threads are finished to avoid the "Illegal connection pointer" error of
     * Robolectric. Call this method only for unit tests.
     */
    @JvmStatic
    fun tearDownTests() {
        try {
            dbExec.awaitTermination(1, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            // ignore error
        }
    }

    /**
     * Deletes a downloaded FeedMedia file from the storage device.
     *
     * @param context A context that is used for opening a database connection.
     * @param mediaId ID of the FeedMedia object whose downloaded file should be deleted.
     */
    @JvmStatic
    fun deleteFeedMediaOfItem(context: Context, mediaId: Long): Future<*> {
        return runOnDbThread {
            val media = getFeedMedia(mediaId)
            if (media != null) {
                val result = deleteFeedMediaSynchronous(context, media)

                if (result && shouldDeleteRemoveFromQueue()) {
                    removeQueueItemSynchronous(context, false, media.getItem()!!.id)
                }
            }
        }
    }

    private fun deleteFeedMediaSynchronous(context: Context, media: FeedMedia): Boolean {
        Log.i(TAG, String.format(Locale.US, "Requested to delete FeedMedia [id=%d, title=%s, downloaded=%s",
            media.id, media.getEpisodeTitle(), media.isDownloaded()))
        var localDelete = false
        if (media.getFile_url() != null && media.getFile_url()!!.startsWith("content://")) {
            // Local feed
            val documentFile = DocumentFile.fromSingleUri(context, Uri.parse(media.getFile_url()))
            if (documentFile == null || !documentFile.exists() || !documentFile.delete()) {
                EventBus.getDefault().post(MessageEvent(context.getString(R.string.delete_local_failed)))
                return false
            }
            media.setFile_url(null)
            localDelete = true
        } else if (media.getFile_url() != null) {
            // delete downloaded media file
            val mediaFile = File(media.getFile_url()!!)
            if (mediaFile.exists() && !mediaFile.delete()) {
                val evt = MessageEvent(context.getString(R.string.delete_failed))
                EventBus.getDefault().post(evt)
                return false
            }
            media.setDownloaded(false)
            media.setFile_url(null)
            media.setHasEmbeddedPicture(false)
            val adapter = getInstance()
            adapter?.open()
            adapter?.setMedia(media)
            adapter?.close()
        }

        if (media.id == currentlyPlayingFeedMediaId) {
            writeNoMediaPlaying()
            sendLocalBroadcast(context, PlaybackServiceInterface.ACTION_SHUTDOWN_PLAYBACK_SERVICE)

            val nm = NotificationManagerCompat.from(context)
            nm.cancel(R.id.notification_playing)
        }

        if (localDelete) {
            // Do full update of this feed to get rid of the item
            updateFeed(media.getItem()!!.feed!!, context.applicationContext, null)
        } else {
            // Gpodder: queue delete action for synchronization
            val item = media.getItem()
            if (item != null) {
                val action = EpisodeAction.Builder(item, EpisodeAction.DELETE)
                    .currentTimestamp()
                    .build()
                SynchronizationQueueSink.enqueueEpisodeActionIfSynchronizationIsActive(context, action)
                EventBus.getDefault().post(updated(item))
            }
        }
        return true
    }

    /**
     * Deletes a Feed and all downloaded files of its components like images and downloaded episodes.
     *
     * @param context A context that is used for opening a database connection.
     * @param feedId  ID of the Feed that should be deleted.
     */
    @JvmStatic
    fun deleteFeed(context: Context, feedId: Long): Future<*> {
        return runOnDbThread {
            val feed = getFeed(feedId) ?: return@runOnDbThread
            // delete stored media files and mark them as read
            if (feed.items.isEmpty()) {
                getFeedItemList(feed)
            }
            deleteFeedItemsSynchronous(context, feed.items)

            // delete feed
            val adapter = getInstance()
            adapter?.open()
            adapter?.removeFeed(feed)
            adapter?.close()

            if (!feed.isLocalFeed && feed.download_url != null) {
                SynchronizationQueueSink.enqueueFeedRemovedIfSynchronizationIsActive(context, feed.download_url!!)
            }
            EventBus.getDefault().post(FeedListUpdateEvent(feed))
        }
    }

    /**
     * Remove the listed items and their FeedMedia entries.
     * Deleting media also removes the download log entries.
     */
    fun deleteFeedItems(context: Context, items: List<FeedItem>): Future<*> {
        return runOnDbThread { deleteFeedItemsSynchronous(context, items) }
    }

    /**
     * Remove the listed items and their FeedMedia entries.
     * Deleting media also removes the download log entries.
     */
    private fun deleteFeedItemsSynchronous(context: Context, items: List<FeedItem>) {
        val queue = getQueue().toMutableList()
        val removedFromQueue: MutableList<FeedItem> = ArrayList()
        for (item in items) {
            if (queue.remove(item)) {
                removedFromQueue.add(item)
            }
            if (item.media != null) {
                if (item.media?.id == currentlyPlayingFeedMediaId) {
                    // Applies to both downloaded and streamed media
                    writeNoMediaPlaying()
                    sendLocalBroadcast(context, PlaybackServiceInterface.ACTION_SHUTDOWN_PLAYBACK_SERVICE)
                }
                if (item.feed != null && !item.feed!!.isLocalFeed) {
                    DownloadServiceInterface.get()?.cancel(context, item.media!!)
                    if (item.media!!.isDownloaded()) {
                        deleteFeedMediaSynchronous(context, item.media!!)
                    }
                }
            }
        }

        val adapter = getInstance()
        adapter?.open()
        if (removedFromQueue.isNotEmpty()) {
            adapter?.setQueue(queue)
        }
        adapter?.removeFeedItems(items)
        adapter?.close()

        for (item in removedFromQueue) {
            EventBus.getDefault().post(irreversibleRemoved(item))
        }

        // we assume we also removed download log entries for the feed or its media files.
        // especially important if download or refresh failed, as the user should not be able
        // to retry these
        EventBus.getDefault().post(DownloadLogEvent.listUpdated())

        val backupManager = BackupManager(context)
        backupManager.dataChanged()
    }

    /**
     * Deletes the entire playback history.
     */
    fun clearPlaybackHistory(): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter?.open()
            adapter?.clearPlaybackHistory()
            adapter?.close()
            EventBus.getDefault().post(PlaybackHistoryEvent.listUpdated())
        }
    }

    /**
     * Deletes the entire download log.
     */
    fun clearDownloadLog(): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter?.open()
            adapter?.clearDownloadLog()
            adapter?.close()
            EventBus.getDefault().post(DownloadLogEvent.listUpdated())
        }
    }

    fun deleteFromPlaybackHistory(feedItem: FeedItem): Future<*> {
        return addItemToPlaybackHistory(feedItem.media, Date(0))
    }

    /**
     * Adds a FeedMedia object to the playback history. A FeedMedia object is in the playback history if
     * its playback completion date is set to a non-null value. This method will set the playback completion date to the
     * current date regardless of the current value.
     *
     * @param media FeedMedia that should be added to the playback history.
     * @param date PlaybackCompletionDate for `media`
     */
    /**
     * Adds a FeedMedia object to the playback history. A FeedMedia object is in the playback history if
     * its playback completion date is set to a non-null value. This method will set the playback completion date to the
     * current date regardless of the current value.
     *
     * @param media FeedMedia that should be added to the playback history.
     */
    @JvmOverloads
    fun addItemToPlaybackHistory(media: FeedMedia?, date: Date? = Date()): Future<*> {
        return runOnDbThread {
            Log.d(TAG, "Adding item to playback history")
            media!!.setPlaybackCompletionDate(date)

            val adapter = getInstance()
            adapter?.open()
            adapter?.setFeedMediaPlaybackCompletionDate(media)
            adapter?.close()
            EventBus.getDefault().post(PlaybackHistoryEvent.listUpdated())
        }
    }

    /**
     * Adds a Download status object to the download log.
     *
     * @param status The DownloadStatus object.
     */
    fun addDownloadStatus(status: DownloadResult?): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter?.open()
            adapter?.setDownloadStatus(status!!)
            adapter?.close()
            EventBus.getDefault().post(DownloadLogEvent.listUpdated())
        }
    }

    /**
     * Inserts a FeedItem in the queue at the specified index. The 'read'-attribute of the FeedItem will be set to
     * true. If the FeedItem is already in the queue, the queue will not be modified.
     *
     * @param context             A context that is used for opening a database connection.
     * @param itemId              ID of the FeedItem that should be added to the queue.
     * @param index               Destination index. Must be in range 0..queue.size()
     * @param performAutoDownload True if an auto-download process should be started after the operation
     * @throws IndexOutOfBoundsException if index < 0 || index >= queue.size()
     */
    @UnstableApi fun addQueueItemAt(context: Context, itemId: Long,
                       index: Int, performAutoDownload: Boolean
    ): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter?.open()
            val queue = getQueue(adapter).toMutableList()
            val item: FeedItem?

            if (!itemListContains(queue, itemId)) {
                item = getFeedItem(itemId)
                if (item != null) {
                    queue.add(index, item)
                    adapter?.setQueue(queue)
                    item.addTag(FeedItem.TAG_QUEUE)
                    EventBus.getDefault().post(added(item, index))
                    EventBus.getDefault().post(updated(item))
                    if (item.isNew) {
                        markItemPlayed(FeedItem.UNPLAYED, item.id)
                    }
                }
            }

            adapter?.close()
            if (performAutoDownload) {
                autodownloadUndownloadedItems(context)
            }
        }
    }

    @JvmStatic
    fun addQueueItem(context: Context?, vararg items: FeedItem): Future<*> {
        return addQueueItem(context, true, *items)
    }

    fun addQueueItem(context: Context?, markAsUnplayed: Boolean, vararg items: FeedItem): Future<*> {
        val itemIds = LongList(items.size)
        for (item in items) {
            if (!item.hasMedia()) {
                continue
            }
            itemIds.add(item.id)
            item.addTag(FeedItem.TAG_QUEUE)
        }
        return addQueueItem(context, false, markAsUnplayed, *itemIds.toArray())
    }

    /**
     * Appends FeedItem objects to the end of the queue. The 'read'-attribute of all items will be set to true.
     * If a FeedItem is already in the queue, the FeedItem will not change its position in the queue.
     *
     * @param context             A context that is used for opening a database connection.
     * @param performAutoDownload true if an auto-download process should be started after the operation.
     * @param itemIds             IDs of the FeedItem objects that should be added to the queue.
     */
    @UnstableApi fun addQueueItem(context: Context?, performAutoDownload: Boolean,
                     vararg itemIds: Long
    ): Future<*> {
        return addQueueItem(context, performAutoDownload, true, *itemIds)
    }

    /**
     * Appends FeedItem objects to the end of the queue. The 'read'-attribute of all items will be set to true.
     * If a FeedItem is already in the queue, the FeedItem will not change its position in the queue.
     *
     * @param context             A context that is used for opening a database connection.
     * @param performAutoDownload true if an auto-download process should be started after the operation.
     * @param markAsUnplayed      true if the items should be marked as unplayed when enqueueing
     * @param itemIds             IDs of the FeedItem objects that should be added to the queue.
     */
    @UnstableApi fun addQueueItem(context: Context?, performAutoDownload: Boolean,
                     markAsUnplayed: Boolean, vararg itemIds: Long
    ): Future<*> {
        return runOnDbThread {
            if (itemIds.isEmpty()) {
                return@runOnDbThread
            }
            val adapter = getInstance()
            adapter?.open()
            val queue = getQueue(adapter).toMutableList()

            var queueModified = false
            val markAsUnplayedIds = LongList()
            val events: MutableList<QueueEvent> = ArrayList()
            val updatedItems: MutableList<FeedItem> = ArrayList()
            val positionCalculator =
                ItemEnqueuePositionCalculator(enqueueLocation)
            val currentlyPlaying = createInstanceFromPreferences(context!!)
            var insertPosition = positionCalculator.calcPosition(queue, currentlyPlaying)
            for (itemId in itemIds) {
                if (!itemListContains(queue, itemId)) {
                    val item = getFeedItem(itemId)
                    if (item != null) {
                        queue.add(insertPosition, item)
                        events.add(added(item, insertPosition))

                        item.addTag(FeedItem.TAG_QUEUE)
                        updatedItems.add(item)
                        queueModified = true
                        if (item.isNew) {
                            markAsUnplayedIds.add(item.id)
                        }
                        insertPosition++
                    }
                }
            }
            if (queueModified) {
                applySortOrder(queue, events)
                adapter?.setQueue(queue)
                for (event in events) {
                    EventBus.getDefault().post(event)
                }
                EventBus.getDefault().post(updated(updatedItems))
                if (markAsUnplayed && markAsUnplayedIds.size() > 0) {
                    markItemPlayed(FeedItem.UNPLAYED, *markAsUnplayedIds.toArray())
                }
            }
            adapter?.close()
            if (performAutoDownload) {
                autodownloadUndownloadedItems(context)
            }
        }
    }

    /**
     * Sorts the queue depending on the configured sort order.
     * If the queue is not in keep sorted mode, nothing happens.
     *
     * @param queue  The queue to be sorted.
     * @param events Replaces the events by a single SORT event if the list has to be sorted automatically.
     */
    private fun applySortOrder(queue: MutableList<FeedItem>, events: MutableList<QueueEvent>) {
        if (!isQueueKeepSorted) {
            // queue is not in keep sorted mode, there's nothing to do
            return
        }

        // Sort queue by configured sort order
        val sortOrder = queueKeepSortedOrder
        if (sortOrder == SortOrder.RANDOM) {
            // do not shuffle the list on every change
            return
        }
        val permutor = getPermutor(sortOrder!!)
        permutor.reorder(queue)

        // Replace ADDED events by a single SORTED event
        events.clear()
        events.add(QueueEvent.sorted(queue))
    }

    /**
     * Removes all FeedItem objects from the queue.
     */
    @JvmStatic
    fun clearQueue(): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter?.open()
            adapter?.clearQueue()
            adapter?.close()
            EventBus.getDefault().post(cleared())
        }
    }

    /**
     * Removes a FeedItem object from the queue.
     *
     * @param context             A context that is used for opening a database connection.
     * @param performAutoDownload true if an auto-download process should be started after the operation.
     * @param item                FeedItem that should be removed.
     */
    @JvmStatic
    fun removeQueueItem(context: Context,
                        performAutoDownload: Boolean, item: FeedItem
    ): Future<*> {
        return runOnDbThread { removeQueueItemSynchronous(context, performAutoDownload, item.id) }
    }

    @JvmStatic
    fun removeQueueItem(context: Context, performAutoDownload: Boolean,
                        vararg itemIds: Long
    ): Future<*> {
        return runOnDbThread { removeQueueItemSynchronous(context, performAutoDownload, *itemIds) }
    }

    @UnstableApi private fun removeQueueItemSynchronous(context: Context,
                                           performAutoDownload: Boolean,
                                           vararg itemIds: Long
    ) {
        if (itemIds.isEmpty()) {
            return
        }
        val adapter = getInstance()
        adapter?.open()
        val queue = getQueue(adapter).toMutableList()

        var queueModified = false
        val events: MutableList<QueueEvent> = ArrayList()
        val updatedItems: MutableList<FeedItem> = ArrayList()
        for (itemId in itemIds) {
            val position = indexInItemList(queue, itemId)
            if (position >= 0) {
                val item = getFeedItem(itemId)
                if (item == null) {
                    Log.e(TAG, "removeQueueItem - item in queue but somehow cannot be loaded." +
                            " Item ignored. It should never happen. id:" + itemId)
                    continue
                }
                queue.removeAt(position)
                item.removeTag(FeedItem.TAG_QUEUE)
                events.add(removed(item))
                updatedItems.add(item)
                queueModified = true
            } else {
                Log.v(TAG, "removeQueueItem - item  not in queue:$itemId")
            }
        }
        if (queueModified) {
            adapter?.setQueue(queue)
            for (event in events) {
                EventBus.getDefault().post(event)
            }
            EventBus.getDefault().post(updated(updatedItems))
        } else {
            Log.w(TAG, "Queue was not modified by call to removeQueueItem")
        }
        adapter?.close()
        if (performAutoDownload) {
            autodownloadUndownloadedItems(context)
        }
    }

    fun toggleFavoriteItem(item: FeedItem): Future<*> {
        return if (item.isTagged(FeedItem.TAG_FAVORITE)) {
            removeFavoriteItem(item)
        } else {
            addFavoriteItem(item)
        }
    }

    fun addFavoriteItem(item: FeedItem): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()?.open()
            adapter?.addFavoriteItem(item)
            adapter?.close()
            item.addTag(FeedItem.TAG_FAVORITE)
            EventBus.getDefault().post(FavoritesEvent())
            EventBus.getDefault().post(updated(item))
        }
    }

    fun removeFavoriteItem(item: FeedItem): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()?.open()
            adapter?.removeFavoriteItem(item)
            adapter?.close()
            item.removeTag(FeedItem.TAG_FAVORITE)
            EventBus.getDefault().post(FavoritesEvent())
            EventBus.getDefault().post(updated(item))
        }
    }

    /**
     * Moves the specified item to the top of the queue.
     *
     * @param itemId          The item to move to the top of the queue
     * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
     */
    fun moveQueueItemToTop(itemId: Long, broadcastUpdate: Boolean): Future<*> {
        return runOnDbThread {
            val queueIdList = getQueueIDList()
            val index = queueIdList.indexOf(itemId)
            if (index >= 0) {
                moveQueueItemHelper(index, 0, broadcastUpdate)
            } else {
                Log.e(TAG, "moveQueueItemToTop: item not found")
            }
        }
    }

    /**
     * Moves the specified item to the bottom of the queue.
     *
     * @param itemId          The item to move to the bottom of the queue
     * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
     */
    fun moveQueueItemToBottom(itemId: Long,
                              broadcastUpdate: Boolean
    ): Future<*> {
        return runOnDbThread {
            val queueIdList = getQueueIDList()
            val index = queueIdList.indexOf(itemId)
            if (index >= 0) {
                moveQueueItemHelper(index, queueIdList.size() - 1,
                    broadcastUpdate)
            } else {
                Log.e(TAG, "moveQueueItemToBottom: item not found")
            }
        }
    }

    /**
     * Changes the position of a FeedItem in the queue.
     *
     * @param from            Source index. Must be in range 0..queue.size()-1.
     * @param to              Destination index. Must be in range 0..queue.size()-1.
     * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
     * false if the caller wants to avoid unexpected updates of the GUI.
     * @throws IndexOutOfBoundsException if (to < 0 || to >= queue.size()) || (from < 0 || from >= queue.size())
     */
    @JvmStatic
    fun moveQueueItem(from: Int,
                      to: Int, broadcastUpdate: Boolean
    ): Future<*> {
        return runOnDbThread { moveQueueItemHelper(from, to, broadcastUpdate) }
    }

    /**
     * Changes the position of a FeedItem in the queue.
     *
     *
     * This function must be run using the ExecutorService (dbExec).
     *
     * @param from            Source index. Must be in range 0..queue.size()-1.
     * @param to              Destination index. Must be in range 0..queue.size()-1.
     * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
     * false if the caller wants to avoid unexpected updates of the GUI.
     * @throws IndexOutOfBoundsException if (to < 0 || to >= queue.size()) || (from < 0 || from >= queue.size())
     */
    private fun moveQueueItemHelper(from: Int,
                                    to: Int, broadcastUpdate: Boolean
    ) {
        val adapter = getInstance()
        adapter?.open()
        val queue = getQueue(adapter).toMutableList()

        if (queue.isNotEmpty()) {
            if (from >= 0 && from < queue.size && to >= 0 && to < queue.size) {
                val item: FeedItem = queue.removeAt(from)
                queue.add(to, item)

                adapter?.setQueue(queue)
                if (broadcastUpdate) {
                    EventBus.getDefault().post(moved(item, to))
                }
            }
        } else {
            Log.e(TAG, "moveQueueItemHelper: Could not load queue")
        }
        adapter?.close()
    }

    fun resetPagedFeedPage(feed: Feed?): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter?.open()
            adapter?.resetPagedFeedPage(feed!!)
            adapter?.close()
        }
    }

    /*
     * Sets the 'read'-attribute of all specified FeedItems
     *
     * @param played  New value of the 'read'-attribute, one of FeedItem.PLAYED, FeedItem.NEW,
     *                FeedItem.UNPLAYED
     * @param itemIds IDs of the FeedItems.
     */
    fun markItemPlayed(played: Int, vararg itemIds: Long): Future<*> {
        return markItemPlayed(played, true, *itemIds)
    }

    /*
     * Sets the 'read'-attribute of all specified FeedItems
     *
     * @param played  New value of the 'read'-attribute, one of FeedItem.PLAYED, FeedItem.NEW,
     *                FeedItem.UNPLAYED
     * @param broadcastUpdate true if this operation should trigger a UnreadItemsUpdate broadcast.
     *        This option is usually set to true
     * @param itemIds IDs of the FeedItems.
     */
    fun markItemPlayed(played: Int, broadcastUpdate: Boolean,
                       vararg itemIds: Long
    ): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter?.open()
            adapter?.setFeedItemRead(played, *itemIds)
            adapter?.close()
            if (broadcastUpdate) {
                EventBus.getDefault().post(UnreadItemsUpdateEvent())
            }
        }
    }

    /**
     * Sets the 'read'-attribute of a FeedItem to the specified value.
     *
     * @param item               The FeedItem object
     * @param played             New value of the 'read'-attribute one of FeedItem.PLAYED,
     * FeedItem.NEW, FeedItem.UNPLAYED
     * @param resetMediaPosition true if this method should also reset the position of the FeedItem's FeedMedia object.
     */
    fun markItemPlayed(item: FeedItem, played: Int, resetMediaPosition: Boolean): Future<*> {
        val mediaId = if ((item.hasMedia())) item.media!!.id else 0
        return markItemPlayed(item.id, played, mediaId, resetMediaPosition)
    }

    private fun markItemPlayed(itemId: Long,
                               played: Int,
                               mediaId: Long,
                               resetMediaPosition: Boolean
    ): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter?.open()
            adapter?.setFeedItemRead(played, itemId, mediaId,
                resetMediaPosition)
            adapter?.close()
            EventBus.getDefault().post(UnreadItemsUpdateEvent())
        }
    }

    /**
     * Sets the 'read'-attribute of all NEW FeedItems of a specific Feed to UNPLAYED.
     *
     * @param feedId ID of the Feed.
     */
    fun removeFeedNewFlag(feedId: Long): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter?.open()
            adapter?.setFeedItems(FeedItem.NEW, FeedItem.UNPLAYED, feedId)
            adapter?.close()
            EventBus.getDefault().post(UnreadItemsUpdateEvent())
        }
    }

    /**
     * Sets the 'read'-attribute of all NEW FeedItems to UNPLAYED.
     */
    @JvmStatic
    fun removeAllNewFlags(): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter?.open()
            adapter?.setFeedItems(FeedItem.NEW, FeedItem.UNPLAYED)
            adapter?.close()
            EventBus.getDefault().post(UnreadItemsUpdateEvent())
        }
    }

    fun addNewFeed(context: Context, vararg feeds: Feed): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter?.open()
            adapter?.setCompleteFeed(*feeds)
            adapter?.close()

            for (feed in feeds) {
                if (!feed.isLocalFeed && feed.download_url != null) {
                    SynchronizationQueueSink.enqueueFeedAddedIfSynchronizationIsActive(context, feed.download_url!!)
                }
            }

            val backupManager = BackupManager(context)
            backupManager.dataChanged()
        }
    }

    fun setCompleteFeed(vararg feeds: Feed): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter?.open()
            adapter?.setCompleteFeed(*feeds)
            adapter?.close()
        }
    }

    fun setItemList(items: List<FeedItem>): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter?.open()
            adapter?.storeFeedItemlist(items)
            adapter?.close()
            EventBus.getDefault().post(updated(items))
        }
    }

    /**
     * Saves a FeedMedia object in the database. This method will save all attributes of the FeedMedia object. The
     * contents of FeedComponent-attributes (e.g. the FeedMedia's 'item'-attribute) will not be saved.
     *
     * @param media The FeedMedia object.
     */
    fun setFeedMedia(media: FeedMedia?): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter!!.open()
            adapter.setMedia(media)
            adapter.close()
        }
    }

    /**
     * Saves the 'position', 'duration' and 'last played time' attributes of a FeedMedia object
     *
     * @param media The FeedMedia object.
     */
    @JvmStatic
    fun setFeedMediaPlaybackInformation(media: FeedMedia?): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter!!.open()
            adapter.setFeedMediaPlaybackInformation(media!!)
            adapter.close()
        }
    }

    /**
     * Saves a FeedItem object in the database. This method will save all attributes of the FeedItem object including
     * the content of FeedComponent-attributes.
     *
     * @param item The FeedItem object.
     */
    @JvmStatic
    fun setFeedItem(item: FeedItem?): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter!!.open()
            adapter.setSingleFeedItem(item!!)
            adapter.close()
            EventBus.getDefault().post(updated(item))
        }
    }

    /**
     * Updates download URL of a feed
     */
    fun updateFeedDownloadURL(original: String, updated: String): Future<*> {
        Log.d(TAG, "updateFeedDownloadURL(original: $original, updated: $updated)")
        return runOnDbThread {
            val adapter = getInstance()
            adapter!!.open()
            adapter.setFeedDownloadUrl(original, updated)
            adapter.close()
        }
    }

    /**
     * Saves a FeedPreferences object in the database. The Feed ID of the FeedPreferences-object MUST NOT be 0.
     *
     * @param preferences The FeedPreferences object.
     */
    fun setFeedPreferences(preferences: FeedPreferences): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter!!.open()
            adapter.setFeedPreferences(preferences)
            adapter.close()
            EventBus.getDefault().post(FeedListUpdateEvent(preferences.feedID))
        }
    }

    private fun itemListContains(items: List<FeedItem?>, itemId: Long): Boolean {
        return indexInItemList(items, itemId) >= 0
    }

    private fun indexInItemList(items: List<FeedItem?>, itemId: Long): Int {
        for (i in items.indices) {
            val item = items[i]
            if (item!!.id == itemId) {
                return i
            }
        }
        return -1
    }

    /**
     * Saves if a feed's last update failed
     *
     * @param lastUpdateFailed true if last update failed
     */
    fun setFeedLastUpdateFailed(feedId: Long,
                                lastUpdateFailed: Boolean
    ): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter!!.open()
            adapter.setFeedLastUpdateFailed(feedId, lastUpdateFailed)
            adapter.close()
            EventBus.getDefault().post(FeedListUpdateEvent(feedId))
        }
    }

    fun setFeedCustomTitle(feed: Feed): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter!!.open()
            adapter.setFeedCustomTitle(feed.id, feed.getCustomTitle())
            adapter.close()
            EventBus.getDefault().post(FeedListUpdateEvent(feed))
        }
    }

    /**
     * Sort the FeedItems in the queue with the given the named sort order.
     *
     * @param broadcastUpdate `true` if this operation should trigger a
     * QueueUpdateBroadcast. This option should be set to `false`
     * if the caller wants to avoid unexpected updates of the GUI.
     */
    fun reorderQueue(sortOrder: SortOrder?, broadcastUpdate: Boolean): Future<*> {
        if (sortOrder == null) {
            Log.w(TAG, "reorderQueue() - sortOrder is null. Do nothing.")
            return runOnDbThread {}
        }
        val permutor = getPermutor(sortOrder)
        return runOnDbThread {
            val adapter = getInstance()
            adapter!!.open()
            val queue = getQueue(adapter).toMutableList()

            permutor.reorder(queue)
            adapter.setQueue(queue)
            if (broadcastUpdate) {
                EventBus.getDefault().post(QueueEvent.sorted(queue))
            }
            adapter.close()
        }
    }

    /**
     * Set filter of the feed
     *
     * @param feedId       The feed's ID
     * @param filterValues Values that represent properties to filter by
     */
    fun setFeedItemsFilter(feedId: Long,
                           filterValues: Set<String>
    ): Future<*> {
        Log.d(TAG, "setFeedItemsFilter() called with: feedId = [$feedId], filterValues = [$filterValues]")
        return runOnDbThread {
            val adapter = getInstance()
            adapter!!.open()
            adapter.setFeedItemFilter(feedId, filterValues)
            adapter.close()
            EventBus.getDefault().post(FeedEvent(FeedEvent.Action.FILTER_CHANGED, feedId))
        }
    }

    /**
     * Set item sort order of the feed
     *
     */
    fun setFeedItemSortOrder(feedId: Long, sortOrder: SortOrder?): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter!!.open()
            adapter.setFeedItemSortOrder(feedId, sortOrder)
            adapter.close()
            EventBus.getDefault().post(FeedEvent(FeedEvent.Action.SORT_ORDER_CHANGED, feedId))
        }
    }

    /**
     * Reset the statistics in DB
     */
    fun resetStatistics(): Future<*> {
        return runOnDbThread {
            val adapter = getInstance()
            adapter!!.open()
            adapter.resetAllMediaPlayedDuration()
            adapter.close()
        }
    }

    /**
     * Submit to the DB thread only if caller is not already on the DB thread. Otherwise,
     * just execute synchronously
     */
    private fun runOnDbThread(runnable: Runnable): Future<*> {
        if ("DatabaseExecutor" == Thread.currentThread().name) {
            runnable.run()
            return Futures.immediateFuture<Any?>(null)
        } else {
            return dbExec.submit(runnable)
        }
    }
}
