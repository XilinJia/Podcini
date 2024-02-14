package ac.mdiq.podvinci.core.storage

import android.content.Context
import android.text.TextUtils
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podvinci.core.R
import ac.mdiq.podvinci.core.storage.DBReader.extractItemlistFromCursor
import ac.mdiq.podvinci.core.storage.DBReader.getFeed
import ac.mdiq.podvinci.core.storage.DBReader.getFeedItemList
import ac.mdiq.podvinci.core.storage.DBReader.getFeedList
import ac.mdiq.podvinci.core.storage.DBReader.loadAdditionalFeedItemListData
import ac.mdiq.podvinci.core.sync.queue.SynchronizationQueueSink
import ac.mdiq.podvinci.core.util.comparator.FeedItemPubdateComparator
import ac.mdiq.podvinci.event.FeedItemEvent.Companion.updated
import ac.mdiq.podvinci.event.FeedListUpdateEvent
import ac.mdiq.podvinci.event.MessageEvent
import ac.mdiq.podvinci.model.download.DownloadError
import ac.mdiq.podvinci.model.download.DownloadResult
import ac.mdiq.podvinci.model.feed.Feed
import ac.mdiq.podvinci.model.feed.FeedItem
import ac.mdiq.podvinci.model.feed.FeedMedia
import ac.mdiq.podvinci.model.feed.FeedPreferences.NewEpisodesAction
import ac.mdiq.podvinci.net.sync.model.EpisodeAction
import ac.mdiq.podvinci.storage.database.PodDBAdapter
import ac.mdiq.podvinci.storage.database.PodDBAdapter.Companion.getInstance
import ac.mdiq.podvinci.storage.database.mapper.FeedCursorMapper.convert
import ac.mdiq.podvinci.storage.preferences.UserPreferences.newEpisodesAction
import org.greenrobot.eventbus.EventBus
import java.util.*
import java.util.concurrent.*

/**
 * Provides methods for doing common tasks that use DBReader and DBWriter.
 */
@UnstableApi object DBTasks {
    private const val TAG = "DBTasks"

    /**
     * Executor service used by the autodownloadUndownloadedEpisodes method.
     */
    private val autodownloadExec: ExecutorService =
        Executors.newSingleThreadExecutor { r: Runnable? ->
            val t = Thread(r)
            t.priority = Thread.MIN_PRIORITY
            t
        }

    private var downloadAlgorithm = AutomaticDownloadAlgorithm()

    /**
     * Removes the feed with the given download url. This method should NOT be executed on the GUI thread.
     *
     * @param context     Used for accessing the db
     * @param downloadUrl URL of the feed.
     */
    @UnstableApi @JvmStatic
    fun removeFeedWithDownloadUrl(context: Context?, downloadUrl: String) {
        val adapter = getInstance()
        adapter!!.open()
        val cursor = adapter.feedCursorDownloadUrls
        var feedID: Long = 0
        if (cursor.moveToFirst()) {
            do {
                if (cursor.getString(1) == downloadUrl) {
                    feedID = cursor.getLong(0)
                }
            } while (cursor.moveToNext())
        }
        cursor.close()
        adapter.close()

        if (feedID != 0L) {
            try {
                DBWriter.deleteFeed(context!!, feedID).get()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } catch (e: ExecutionException) {
                e.printStackTrace()
            }
        } else {
            Log.w(TAG, "removeFeedWithDownloadUrl: Could not find feed with url: $downloadUrl")
        }
    }

    /**
     * Notifies the database about a missing FeedMedia file. This method will correct the FeedMedia object's
     * values in the DB and send a FeedItemEvent.
     */
    fun notifyMissingFeedMediaFile(context: Context, media: FeedMedia) {
        Log.i(TAG, "The feedmanager was notified about a missing episode. It will update its database now.")
        media.setDownloaded(false)
        media.setFile_url(null)
        DBWriter.setFeedMedia(media)
        EventBus.getDefault().post(updated(media.getItem()!!))
        EventBus.getDefault().post(MessageEvent(context.getString(R.string.error_file_not_found)))
    }

    /**
     * Looks for non-downloaded episodes in the queue or list of unread items and request a download if
     * 1. Network is available
     * 2. The device is charging or the user allows auto download on battery
     * 3. There is free space in the episode cache
     * This method is executed on an internal single thread executor.
     *
     * @param context  Used for accessing the DB.
     * @return A Future that can be used for waiting for the methods completion.
     */
    @UnstableApi @JvmStatic
    fun autodownloadUndownloadedItems(context: Context): Future<*> {
        Log.d(TAG, "autodownloadUndownloadedItems")
        return autodownloadExec.submit(downloadAlgorithm.autoDownloadUndownloadedItems(context))
    }

    /**
     * For testing purpose only.
     */
    @JvmStatic
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun setDownloadAlgorithm(newDownloadAlgorithm: AutomaticDownloadAlgorithm) {
        downloadAlgorithm = newDownloadAlgorithm
    }

    /**
     * Removed downloaded episodes outside of the queue if the episode cache is full. Episodes with a smaller
     * 'playbackCompletionDate'-value will be deleted first.
     *
     *
     * This method should NOT be executed on the GUI thread.
     *
     * @param context Used for accessing the DB.
     */
    @JvmStatic
    fun performAutoCleanup(context: Context?) {
        EpisodeCleanupAlgorithmFactory.build().performCleanup(context)
    }

    private fun searchFeedByIdentifyingValueOrID(feed: Feed): Feed? {
        if (feed.id != 0L) {
            return getFeed(feed.id)
        } else {
            val feeds = getFeedList()
            for (f in feeds) {
                if (f.identifyingValue == feed.identifyingValue) {
                    f.items = getFeedItemList(f).toMutableList()
                    return f
                }
            }
        }
        return null
    }

    /**
     * Get a FeedItem by its identifying value.
     */
    private fun searchFeedItemByIdentifyingValue(items: List<FeedItem>?, searchItem: FeedItem): FeedItem? {
        for (item in items!!) {
            if (TextUtils.equals(item.identifyingValue, searchItem.identifyingValue)) {
                return item
            }
        }
        return null
    }

    /**
     * Guess if one of the items could actually mean the searched item, even if it uses another identifying value.
     * This is to work around podcasters breaking their GUIDs.
     */
    private fun searchFeedItemGuessDuplicate(items: List<FeedItem>?, searchItem: FeedItem): FeedItem? {
        for (item in items!!) {
            if (FeedItemDuplicateGuesser.seemDuplicates(item, searchItem)) {
                return item
            }
        }
        return null
    }

    /**
     * Adds new Feeds to the database or updates the old versions if they already exists. If another Feed with the same
     * identifying value already exists, this method will add new FeedItems from the new Feed to the existing Feed.
     * These FeedItems will be marked as unread with the exception of the most recent FeedItem.
     *
     *
     * This method can update multiple feeds at once. Submitting a feed twice in the same method call can result in undefined behavior.
     *
     *
     * This method should NOT be executed on the GUI thread.
     *
     * @param context Used for accessing the DB.
     * @param newFeed The new Feed object.
     * @param removeUnlistedItems The item list in the new Feed object is considered to be exhaustive.
     * I.e. items are removed from the database if they are not in this item list.
     * @return The updated Feed from the database if it already existed, or the new Feed from the parameters otherwise.
     */
    @JvmStatic
    @Synchronized
    fun updateFeed(context: Context, newFeed: Feed, removeUnlistedItems: Boolean): Feed? {
        var resultFeed: Feed?
        val unlistedItems: MutableList<FeedItem> = ArrayList()

        val adapter = getInstance()
        adapter!!.open()

        // Look up feed in the feedslist
        val savedFeed = searchFeedByIdentifyingValueOrID(newFeed)
        if (savedFeed == null) {
            Log.d(TAG, "Found no existing Feed with title " + newFeed.title + ". Adding as new one.")

            resultFeed = newFeed
        } else {
            Log.d(TAG, "Feed with title " + newFeed.title + " already exists. Syncing new with existing one.")

            Collections.sort(newFeed.items, FeedItemPubdateComparator())

            if (newFeed.pageNr == savedFeed.pageNr) {
                if (savedFeed.compareWithOther(newFeed)) {
                    Log.d(TAG, "Feed has updated attribute values. Updating old feed's attributes")
                    savedFeed.updateFromOther(newFeed)
                }
            } else {
                Log.d(TAG, "New feed has a higher page number.")
                savedFeed.nextPageLink = newFeed.nextPageLink
            }
            if (savedFeed.preferences!!.compareWithOther(newFeed.preferences)) {
                Log.d(TAG, "Feed has updated preferences. Updating old feed's preferences")
                savedFeed.preferences!!.updateFromOther(newFeed.preferences)
            }

            // get the most recent date now, before we start changing the list
            val priorMostRecent = savedFeed.mostRecentItem
            var priorMostRecentDate: Date? = Date()
            if (priorMostRecent != null) {
                priorMostRecentDate = priorMostRecent.getPubDate()
            }

            // Look for new or updated Items
            for (idx in newFeed.items.indices) {
                val item = newFeed.items[idx]

                val possibleDuplicate = searchFeedItemGuessDuplicate(newFeed.items, item)
                if (!newFeed.isLocalFeed && possibleDuplicate != null && item !== possibleDuplicate) {
                    // Canonical episode is the first one returned (usually oldest)
                    DBWriter.addDownloadStatus(DownloadResult(savedFeed,
                        item.title!!, DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE, false,
                        """
                            The podcast host appears to have added the same episode twice. PodVinci still refreshed the feed and attempted to repair it.
                            
                            Original episode:
                            ${duplicateEpisodeDetails(item)}
                            
                            Second episode that is also in the feed:
                            ${duplicateEpisodeDetails(possibleDuplicate)}
                            """.trimIndent()))
                    continue
                }

                var oldItem = searchFeedItemByIdentifyingValue(savedFeed.items, item)
                if (!newFeed.isLocalFeed && oldItem == null) {
                    oldItem = searchFeedItemGuessDuplicate(savedFeed.items, item)
                    if (oldItem != null) {
                        Log.d(TAG, "Repaired duplicate: $oldItem, $item")
                        DBWriter.addDownloadStatus(DownloadResult(savedFeed,
                            item.title!!, DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE, false,
                            """
                                The podcast host changed the ID of an existing episode instead of just updating the episode itself. PodVinci still refreshed the feed and attempted to repair it.
                                
                                Original episode:
                                ${duplicateEpisodeDetails(oldItem)}
                                
                                Now the feed contains:
                                ${duplicateEpisodeDetails(item)}
                                """.trimIndent()))
                        oldItem.itemIdentifier = item.itemIdentifier

                        if (oldItem.isPlayed() && oldItem.media != null) {
                            val action = EpisodeAction.Builder(oldItem, EpisodeAction.PLAY)
                                .currentTimestamp()
                                .started(oldItem.media!!.getDuration() / 1000)
                                .position(oldItem.media!!.getDuration() / 1000)
                                .total(oldItem.media!!.getDuration() / 1000)
                                .build()
                            SynchronizationQueueSink.enqueueEpisodeActionIfSynchronizationIsActive(context, action)
                        }
                    }
                }

                if (oldItem != null) {
                    oldItem.updateFromOther(item)
                } else {
                    Log.d(TAG, "Found new item: " + item.title)
                    item.feed = savedFeed

                    if (idx >= savedFeed.items.size) {
                        savedFeed.items.add(item)
                    } else {
                        savedFeed.items.add(idx, item)
                    }

                    var action = savedFeed.preferences!!.newEpisodesAction
                    if (action == NewEpisodesAction.GLOBAL) {
                        action = newEpisodesAction
                    }
                    if (action == NewEpisodesAction.ADD_TO_INBOX
                            && (item.getPubDate() == null || priorMostRecentDate == null || priorMostRecentDate.before(
                                item.getPubDate()) || priorMostRecentDate == item.getPubDate())) {
                        Log.d(TAG, "Marking item published on " + item.getPubDate()
                                + " new, prior most recent date = " + priorMostRecentDate)
                        item.setNew()
                    }
                }
            }

            // identify items to be removed
            if (removeUnlistedItems) {
                val it = savedFeed.items.toMutableList().iterator()
                while (it.hasNext()) {
                    val feedItem = it.next()
                    if (searchFeedItemByIdentifyingValue(newFeed.items, feedItem) == null) {
                        unlistedItems.add(feedItem)
                        it.remove()
                    }
                }
            }

            // update attributes
            savedFeed.lastUpdate = newFeed.lastUpdate
            savedFeed.type = newFeed.type
            savedFeed.setLastUpdateFailed(false)

            resultFeed = savedFeed
        }

        try {
            if (savedFeed == null) {
                DBWriter.addNewFeed(context, newFeed).get()
                // Update with default values that are set in database
                resultFeed = searchFeedByIdentifyingValueOrID(newFeed)
            } else {
                DBWriter.setCompleteFeed(savedFeed).get()
            }
            if (removeUnlistedItems) {
                DBWriter.deleteFeedItems(context, unlistedItems).get()
            }
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: ExecutionException) {
            e.printStackTrace()
        }

        adapter.close()

        if (savedFeed != null) {
            EventBus.getDefault().post(FeedListUpdateEvent(savedFeed))
        } else {
            EventBus.getDefault().post(FeedListUpdateEvent(emptyList()))
        }

        return resultFeed
    }

    private fun duplicateEpisodeDetails(item: FeedItem): String {
        return ("""
    Title: ${item.title}
    ID: ${item.itemIdentifier}
    """.trimIndent()
                + (if ((item.media == null)) "" else """
     
     URL: ${item.media!!.download_url}
     """.trimIndent()))
    }

    /**
     * Searches the FeedItems of a specific Feed for a given string.
     *
     * @param feedID  The id of the feed whose items should be searched.
     * @param query   The search string.
     * @return A FutureTask object that executes the search request
     * and returns the search result as a List of FeedItems.
     */
    @JvmStatic
    fun searchFeedItems(feedID: Long, query: String): FutureTask<List<FeedItem>> {
        return FutureTask(object : QueryTask<List<FeedItem>>() {
            override fun execute(adapter: PodDBAdapter?) {
                val searchResult = adapter!!.searchItems(feedID, query)
                val items = extractItemlistFromCursor(searchResult)
                loadAdditionalFeedItemListData(items)
                setResult(items)
                searchResult.close()
            }
        })
    }

    @JvmStatic
    fun searchFeeds(query: String): FutureTask<List<Feed>> {
        return FutureTask(object : QueryTask<List<Feed>>() {
            override fun execute(adapter: PodDBAdapter?) {
                val cursor = adapter!!.searchFeeds(query)
                val items: MutableList<Feed> = ArrayList()
                if (cursor.moveToFirst()) {
                    do {
                        items.add(convert(cursor))
                    } while (cursor.moveToNext())
                }
                setResult(items)
                cursor.close()
            }
        })
    }

    /**
     * A runnable which should be used for database queries. The onCompletion
     * method is executed on the database executor to handle Cursors correctly.
     * This class automatically creates a PodDBAdapter object and closes it when
     * it is no longer in use.
     */
    internal abstract class QueryTask<T> : Callable<T> {
        private var result: T? = null

        @Throws(Exception::class)
        override fun call(): T? {
            val adapter = getInstance()
            adapter!!.open()
            execute(adapter)
            adapter.close()
            return result
        }

        abstract fun execute(adapter: PodDBAdapter?)

        fun setResult(result: T) {
            this.result = result
        }
    }
}
