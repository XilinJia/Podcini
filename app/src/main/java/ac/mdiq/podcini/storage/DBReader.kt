package ac.mdiq.podcini.storage

import android.database.Cursor
import android.util.Log
import androidx.collection.ArrayMap
import ac.mdiq.podcini.storage.NavDrawerData.*
import ac.mdiq.podcini.util.FeedItemPermutors.getPermutor
import ac.mdiq.podcini.util.LongList
import ac.mdiq.podcini.util.comparator.DownloadResultComparator
import ac.mdiq.podcini.util.comparator.PlaybackCompletionDateComparator
import ac.mdiq.podcini.storage.model.download.DownloadResult
import ac.mdiq.podcini.storage.model.feed.*
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter.Companion.unfiltered
import ac.mdiq.podcini.storage.database.PodDBAdapter
import ac.mdiq.podcini.storage.database.PodDBAdapter.Companion.getInstance
import ac.mdiq.podcini.storage.database.mapper.*
import ac.mdiq.podcini.storage.database.mapper.DownloadResultCursorMapper.convert
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.feedCounterSetting
import ac.mdiq.podcini.preferences.UserPreferences.feedOrder
import ac.mdiq.podcini.storage.model.feed.FeedPreferences.Companion.TAG_ROOT
import java.util.*
import kotlin.math.min


/**
 * Provides methods for reading data from the Podcini database.
 * In general, all database calls in DBReader-methods are executed on the caller's thread.
 * This means that the caller should make sure that DBReader-methods are not executed on the GUI-thread.
 */
object DBReader {
    private const val TAG = "DBReader"
    private var feeds: MutableList<Feed> = mutableListOf()
    private var tags: MutableList<String> = mutableListOf()
    private val feedListLock = Any()

    /**
     * Maximum size of the list returned by [.getDownloadLog].
     */
    private const val DOWNLOAD_LOG_SIZE = 200

    @JvmStatic
    fun getFeedList(): List<Feed> {
        return feeds
    }

    fun getTags(): List<String> {
        return tags
    }

    fun updateFeedList() {
        val adapter = getInstance()
        adapter.open()
        try {
            updateFeedList(adapter)
        } finally {
            adapter.close()
        }
    }

    fun updateFeedList(adapter: PodDBAdapter) {
        synchronized(feedListLock) {
            adapter.allFeedsCursor.use { cursor ->
//                feeds = ArrayList(cursor.count)
                feeds.clear()
                while (cursor.moveToNext()) {
                    val feed = extractFeedFromCursorRow(cursor)
                    feeds.add(feed)
                }
                buildTags()
            }
        }
    }

    fun buildTags() {
        val tagsSet = mutableSetOf<String>()
        for (feed in feeds) {
            if (feed.preferences != null) {
                for (tag in feed.preferences!!.getTags()) {
                    if (tag != TAG_ROOT) tagsSet.add(tag)
                }
            }
        }
        tags.clear()
        tags.addAll(tagsSet)
        tags.sort()
    }

    @JvmStatic
    fun getFeedListDownloadUrls(): List<String> {
        val adapter = getInstance()
        adapter.open()
        try {
            adapter.feedCursorDownloadUrls.use { cursor ->
                val result: MutableList<String> = ArrayList(cursor.count)
                while (cursor.moveToNext()) {
                    val url = cursor.getString(1)
                    if (url != null && !url.startsWith(Feed.PREFIX_LOCAL_FOLDER)) result.add(url)
                }
                return result
            }
        } finally {
            adapter.close()
        }
    }

    /**
     * Loads additional data in to the feed items from other database queries
     *
     * @param items the FeedItems who should have other data loaded
     */
    fun loadAdditionalFeedItemListData(items: List<FeedItem>) {
        loadTagsOfFeedItemList(items)

        synchronized(feedListLock) {
            loadFeedDataOfFeedItemList(items)
        }
    }

    private fun loadTagsOfFeedItemList(items: List<FeedItem>) {
        val favoriteIds = getFavoriteIDList()
        val queueIds = getQueueIDList()

        for (item in items) {
            if (favoriteIds.contains(item.id)) item.addTag(FeedItem.TAG_FAVORITE)
            if (queueIds.contains(item.id)) item.addTag(FeedItem.TAG_QUEUE)
        }
    }

    /**
     * Takes a list of FeedItems and loads their corresponding Feed-objects from the database.
     * The feedID-attribute of a FeedItem must be set to the ID of its feed or the method will
     * not find the correct feed of an item.
     *
     * @param items The FeedItems whose Feed-objects should be loaded.
     */
    private fun loadFeedDataOfFeedItemList(items: List<FeedItem>) {
        Log.d(TAG, "loadFeedDataOfFeedItemList called")
        val feedIndex: MutableMap<Long, Feed> = ArrayMap(feeds.size)
        val feedsCopy = ArrayList(feeds)
        for (feed in feedsCopy) {
            feedIndex[feed.id] = feed
        }
        for (item in items) {
            var feed = feedIndex[item.feedId]
            if (feed == null) {
                Log.w(TAG, "No match found for item with ID " + item.id + ". Feed ID was " + item.feedId)
                feed = Feed("", "", "Error: Item without feed")
            }
            item.feed = feed
        }
    }

    /**
     * Loads the list of FeedItems for a certain Feed-object.
     * This method should NOT be used if the FeedItems are not used.
     *
     * @param feed The Feed whose items should be loaded
     * @return A list with the FeedItems of the Feed. The Feed-attribute of the FeedItems will already be set correctly.
     * The method does NOT change the items-attribute of the feed.
     */
    @JvmStatic
    fun getFeedItemList(feed: Feed?): List<FeedItem> {
        return getFeedItemList(feed, unfiltered())
    }

    fun getFeedItemList(feed: Feed?, filter: FeedItemFilter?): List<FeedItem> {
        return getFeedItemList(feed, filter, SortOrder.DATE_NEW_OLD)
    }

    fun getFeedItemList(feed: Feed?, filter: FeedItemFilter?, sortOrder: SortOrder?): List<FeedItem> {
        Log.d(TAG, "getFeedItemList() called with: feed = [$feed]")

        val adapter = getInstance()
        adapter.open()
        try {
            if (feed != null) {
                adapter.getItemsOfFeedCursor(feed, filter).use { cursor ->
                    val items = extractItemlistFromCursor(adapter, cursor).toMutableList()
                    if (sortOrder != null) getPermutor(sortOrder).reorder(items)
                    feed.items = items
                    for (item in items) {
                        item.feed = feed
                    }
                    return items
                }
            } else {
                Log.e(TAG, "getFeedItemList feed is null")
            }
        } finally {
            adapter.close()
        }
        return listOf()
    }

    @JvmStatic
    fun extractItemlistFromCursor(itemlistCursor: Cursor): List<FeedItem> {
        Log.d(TAG, "extractItemlistFromCursor() called with: itemlistCursor = [$itemlistCursor]")
        val adapter = getInstance()
        adapter.open()
        try {
            return extractItemlistFromCursor(adapter, itemlistCursor)
        } finally {
            adapter.close()
        }
    }

    private fun extractItemlistFromCursor(adapter: PodDBAdapter?, cursor: Cursor?): List<FeedItem> {
        if (cursor == null) return listOf()
        val result: MutableList<FeedItem> = ArrayList(cursor.count)
        if (cursor.moveToFirst()) {
            val indexMediaId = cursor.getColumnIndexOrThrow(PodDBAdapter.SELECT_KEY_MEDIA_ID)
            do {
                val item = FeedItemCursorMapper.convert(cursor)
                result.add(item)
                if (!cursor.isNull(indexMediaId)) item.setMedia(FeedMediaCursorMapper.convert(cursor))
            } while (cursor.moveToNext())
        }
        return result
    }

    private fun extractFeedFromCursorRow(cursor: Cursor): Feed {
        val feed = FeedCursorMapper.convert(cursor)
        val preferences = FeedPreferencesCursorMapper.convert(cursor)
        feed.preferences = preferences
        return feed
    }

    @JvmStatic
    fun getQueue(adapter: PodDBAdapter?): List<FeedItem> {
        Log.d(TAG, "getQueue(adapter)")
        adapter?.queueCursor.use { cursor ->
            val items = extractItemlistFromCursor(adapter, cursor)
            loadAdditionalFeedItemListData(items)
            return items
        }
    }

    @JvmStatic
    fun getQueueIDList(): LongList {
        Log.d(TAG, "getQueueIDList() called")
//        printStackTrace()

        val adapter = getInstance()
        adapter.open()
        try {
            return getQueueIDList(adapter)
        } finally {
            adapter.close()
        }
    }

    private fun getQueueIDList(adapter: PodDBAdapter?): LongList {
        adapter?.queueIDCursor?.use { cursor ->
            val queueIds = LongList(cursor.count)
            while (cursor.moveToNext()) {
                queueIds.add(cursor.getLong(0))
            }
            return queueIds
        }
        return LongList()
    }

    @JvmStatic
    fun getQueue(): List<FeedItem> {
        Log.d(TAG, "getQueue() called")

        val adapter = getInstance()
        adapter.open()
        try {
            return getQueue(adapter)
        } finally {
            adapter.close()
        }
    }

    private fun getFavoriteIDList(): LongList {
        Log.d(TAG, "getFavoriteIDList() called")

        val adapter = getInstance()
        adapter.open()
        try {
            adapter.getFavoritesIdsCursor(0, Int.MAX_VALUE).use { cursor ->
                val favoriteIDs = LongList(cursor.count)
                while (cursor.moveToNext()) {
                    favoriteIDs.add(cursor.getLong(0))
                }
                return favoriteIDs
            }
        } finally {
            adapter.close()
        }
    }

    /**
     *
     * @param offset The first episode that should be loaded.
     * @param limit The maximum number of episodes that should be loaded.
     * @param filter The filter describing which episodes to filter out.
     */
    @JvmStatic
    fun getEpisodes(offset: Int, limit: Int, filter: FeedItemFilter?, sortOrder: SortOrder?): List<FeedItem> {
        Log.d(TAG, "getEpisodes called with: offset=$offset, limit=$limit")
        val adapter = getInstance()
        adapter.open()
        try {
            adapter.getEpisodesCursor(offset, limit, filter, sortOrder).use { cursor ->
                val items = extractItemlistFromCursor(adapter, cursor)
                loadAdditionalFeedItemListData(items)
                return items
            }
        } finally {
            adapter.close()
        }
    }

    @JvmStatic
    fun getTotalEpisodeCount(filter: FeedItemFilter?): Int {
        Log.d(TAG, "getTotalEpisodeCount called")
        val adapter = getInstance()
        adapter.open()
        try {
            adapter.getEpisodeCountCursor(filter).use { cursor ->
                if (cursor.moveToFirst()) return cursor.getInt(0)
                return -1
            }
        } finally {
            adapter.close()
        }
    }

    fun getRandomEpisodes(limit: Int, seed: Int): List<FeedItem> {
        val adapter = getInstance()
        adapter.open()
        try {
            adapter.getRandomEpisodesCursor(limit, seed).use { cursor ->
                val items = extractItemlistFromCursor(adapter, cursor)
                loadAdditionalFeedItemListData(items)
                return items
            }
        } finally {
            adapter.close()
        }
    }

    /**
     * Loads the playback history from the database. A FeedItem is in the playback history if playback of the correpsonding episode
     * has been completed at least once.
     *
     * @param limit The maximum number of items to return.
     *
     * @return The playback history. The FeedItems are sorted by their media's playbackCompletionDate in descending order.
     */
    @JvmStatic
    fun getPlaybackHistory(offset: Int, limit: Int): List<FeedItem> {
        Log.d(TAG, "getPlaybackHistory() called")

        val adapter = getInstance()
        adapter.open()

        var mediaCursor: Cursor? = null
        var itemCursor: Cursor? = null
        try {
            mediaCursor = adapter.getCompletedMediaCursor(offset, limit)
            val itemIds = arrayOfNulls<String>(mediaCursor.count)
            var i = 0
            while (i < itemIds.size && mediaCursor.moveToPosition(i)) {
                val index = mediaCursor.getColumnIndex(PodDBAdapter.KEY_FEEDITEM)
                itemIds[i] = mediaCursor.getLong(index).toString()
                i++
            }
            itemCursor = adapter.getFeedItemCursor(itemIds.filterNotNull().toTypedArray())
            val items = extractItemlistFromCursor(adapter, itemCursor).toMutableList()
            loadAdditionalFeedItemListData(items)
            items.sortWith(PlaybackCompletionDateComparator())
            return items
        } finally {
            mediaCursor?.close()
            itemCursor?.close()
            adapter.close()
        }
    }

    @JvmStatic
    fun getPlaybackHistoryLength(): Long {
        val adapter = getInstance()
        adapter.open()

        try {
            return adapter.completedMediaLength
        } finally {
            adapter.close()
        }
    }

    @JvmStatic
    fun getDownloadLog(): List<DownloadResult> {
        Log.d(TAG, "getDownloadLog() called")

        val adapter = getInstance()
        adapter.open()
        try {
            adapter.getDownloadLogCursor(DOWNLOAD_LOG_SIZE).use { cursor ->
                val downloadLog: MutableList<DownloadResult> = ArrayList(cursor.count)
                while (cursor.moveToNext()) {
                    downloadLog.add(convert(cursor))
                }
                downloadLog.sortWith(DownloadResultComparator())
                return downloadLog
            }
        } finally {
            adapter.close()
        }
    }

    /**
     * Loads the download log for a particular feed from the database.
     *
     * @param feedId Feed id for which the download log is loaded
     * @return A list with DownloadStatus objects that represent the feed's download log,
     * newest events first.
     */
    fun getFeedDownloadLog(feedId: Long): List<DownloadResult> {
        Log.d(TAG, "getFeedDownloadLog() called with: feed = [$feedId]")

        val adapter = getInstance()
        adapter.open()
        try {
            adapter.getDownloadLog(Feed.FEEDFILETYPE_FEED, feedId).use { cursor ->
                val downloadLog: MutableList<DownloadResult> = ArrayList(cursor.count)
                while (cursor.moveToNext()) {
                    downloadLog.add(convert(cursor))
                }
                downloadLog.sortWith(DownloadResultComparator())
                return downloadLog
            }
        } finally {
            adapter.close()
        }
    }

    /**
     * Loads a specific Feed from the database.
     *
     * @param feedId The ID of the Feed
     * @return The Feed or null if the Feed could not be found. The Feeds FeedItems will also be loaded from the
     * database and the items-attribute will be set correctly.
     */
    @JvmStatic
    fun getFeed(feedId: Long): Feed? {
        return getFeed(feedId, false)
    }

    /**
     * Loads a specific Feed from the database.
     *
     * @param feedId The ID of the Feed
     * @param filtered `true` if only the visible items should be loaded according to the feed filter.
     * @return The Feed or null if the Feed could not be found. The Feeds FeedItems will also be loaded from the
     * database and the items-attribute will be set correctly.
     */
    fun getFeed(feedId: Long, filtered: Boolean): Feed? {
        Log.d(TAG, "getFeed() called with: feedId = [$feedId]")
        val adapter = getInstance()
        adapter.open()
        try {
            adapter.getFeedCursor(feedId).use { cursor ->
                var feed: Feed? = null
                if (cursor.moveToNext()) {
                    feed = extractFeedFromCursorRow(cursor)
                    if (filtered) feed.items = getFeedItemList(feed, feed.itemFilter).toMutableList()
                    else feed.items = getFeedItemList(feed).toMutableList()
                } else {
                    Log.e(TAG, "getFeed could not find feed with id $feedId")
                }
                return feed
            }
        } finally {
            adapter.close()
        }
    }

    private fun getFeedItem(itemId: Long, adapter: PodDBAdapter?): FeedItem? {
        Log.d(TAG, "Loading feeditem with id $itemId")

        var item: FeedItem? = null
        adapter?.getFeedItemCursor(itemId.toString())?.use { cursor ->
            if (cursor.moveToNext()) {
                val list = extractItemlistFromCursor(adapter, cursor)
                if (list.isNotEmpty()) {
                    item = list[0]
                    loadAdditionalFeedItemListData(list)
                }
            }
            return item
        }
        return null
    }

    /**
     * Loads a specific FeedItem from the database. This method should not be used for loading more
     * than one FeedItem because this method might query the database several times for each item.
     *
     * @param itemId The ID of the FeedItem
     * @return The FeedItem or null if the FeedItem could not be found.
     */
    @JvmStatic
    fun getFeedItem(itemId: Long): FeedItem? {
        Log.d(TAG, "getFeedItem() called with: itemId = [$itemId]")

        val adapter = getInstance()
        adapter.open()
        try {
            return getFeedItem(itemId, adapter)
        } finally {
            adapter.close()
        }
    }

    /**
     * Get next feed item in queue following a particular feeditem
     *
     * @param item The FeedItem
     * @return The FeedItem next in queue or null if the FeedItem could not be found.
     */
    fun getNextInQueue(item: FeedItem): FeedItem? {
        Log.d(TAG, "getNextInQueue() called with: itemId = [${item.id}]")
        val adapter = getInstance()
        adapter.open()
        try {
            var nextItem: FeedItem? = null
            try {
                adapter.getNextInQueue(item).use { cursor ->
                    val list = extractItemlistFromCursor(adapter, cursor)
                    if (list.isNotEmpty()) {
                        nextItem = list[0]
                        loadAdditionalFeedItemListData(list)
                    }
                    return nextItem
                }
            } catch (e: Exception) {
                Log.d(TAG, "getNextInQueue error: ${e.message}")
                return null
            }
        } finally {
            adapter.close()
        }
    }

    fun getPausedQueue(limit: Int): List<FeedItem> {
        Log.d(TAG, "getPausedQueue() called ")
        val adapter = getInstance()
        adapter.open()
        try {
            adapter.getPausedQueueCursor(limit).use { cursor ->
                val items = extractItemlistFromCursor(adapter, cursor)
                loadAdditionalFeedItemListData(items)
                return items
            }
        } finally {
            adapter.close()
        }
    }

    /**
     * Loads a specific FeedItem from the database.
     *
     * @param guid feed item guid
     * @param episodeUrl the feed item's url
     * @return The FeedItem or null if the FeedItem could not be found.
     * Does NOT load additional attributes like feed or queue state.
     */
    private fun getFeedItemByGuidOrEpisodeUrl(guid: String?, episodeUrl: String,
                                              adapter: PodDBAdapter?): FeedItem? {
        adapter?.getFeedItemCursor(guid, episodeUrl)?.use { cursor ->
            if (!cursor.moveToNext()) return null
            val list = extractItemlistFromCursor(adapter, cursor)
            if (list.isNotEmpty()) return list[0]
            return null
        }
        return null
    }

    /**
     * Returns credentials based on image URL
     *
     * @param imageUrl The URL of the image
     * @return Credentials in format "Username:Password", empty String if no authorization given
     */
    fun getImageAuthentication(imageUrl: String): String {
        Log.d(TAG, "getImageAuthentication() called with: imageUrl = [$imageUrl]")

        val adapter = getInstance()
        adapter.open()
        try {
            return getImageAuthentication(imageUrl, adapter)
        } finally {
            adapter.close()
        }
    }

    private fun getImageAuthentication(imageUrl: String, adapter: PodDBAdapter?): String {
        var credentials = ""
        adapter?.getImageAuthenticationCursor(imageUrl)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val username = cursor.getString(0)
                val password = cursor.getString(1)
                credentials = if (!username.isNullOrEmpty() && password != null) "$username:$password" else ""
            } else {
                credentials = ""
            }
        }
        return credentials
    }

    /**
     * Loads a specific FeedItem from the database.
     *
     * @param guid feed item guid
     * @param episodeUrl the feed item's url
     * @return The FeedItem or null if the FeedItem could not be found.
     * Does NOT load additional attributes like feed or queue state.
     */
    @JvmStatic
    fun getFeedItemByGuidOrEpisodeUrl(guid: String?, episodeUrl: String): FeedItem? {
        Log.d(TAG, "getFeedItemByGuidOrEpisodeUrl called")
        val adapter = getInstance()
        adapter.open()
        try {
            return getFeedItemByGuidOrEpisodeUrl(guid, episodeUrl, adapter)
        } finally {
            adapter.close()
        }
    }

    /**
     * Loads shownotes information about a FeedItem.
     *
     * @param item The FeedItem
     */
    fun loadDescriptionOfFeedItem(item: FeedItem) {
        Log.d(TAG, "loadDescriptionOfFeedItem() called with: item = [$item]")
        //        TODO: need to find out who are often calling this
//        printStackTrace()
        val adapter = getInstance()
        adapter.open()
        try {
            adapter.getDescriptionOfItem(item).use { cursor ->
                if (cursor.moveToFirst()) {
                    val indexDescription = cursor.getColumnIndex(PodDBAdapter.KEY_DESCRIPTION)
                    val description = cursor.getString(indexDescription)
                    item.setDescriptionIfLonger(description)
                }
            }
        } finally {
            adapter.close()
        }
    }

    /**
     * Loads the list of chapters that belongs to this FeedItem if available. This method overwrites
     * any chapters that this FeedItem has. If no chapters were found in the database, the chapters
     * reference of the FeedItem will be set to null.
     *
     * @param item The FeedItem
     */
    @JvmStatic
    fun loadChaptersOfFeedItem(item: FeedItem): List<Chapter>? {
        Log.d(TAG, "loadChaptersOfFeedItem() called with: item = [${item.title}]")
//        TODO: need to find out who are often calling this
//        val stackTraceElements = Thread.currentThread().stackTrace
//        stackTraceElements.forEach { element ->
//            println(element)
//        }
        val adapter = getInstance()
        adapter.open()
        try {
            return loadChaptersOfFeedItem(adapter, item)
        } finally {
            adapter.close()
        }
    }

    private fun loadChaptersOfFeedItem(adapter: PodDBAdapter?, item: FeedItem): List<Chapter>? {
        adapter?.getSimpleChaptersOfFeedItemCursor(item)?.use { cursor ->
            val chaptersCount = cursor.count
            if (chaptersCount == 0) {
                item.chapters = null
                return null
            }
            val chapters = ArrayList<Chapter>()
            while (cursor.moveToNext()) {
                chapters.add(ChapterCursorMapper.convert(cursor))
            }
            return chapters
        }
        return null
    }

    /**
     * Searches the DB for a FeedMedia of the given id.
     *
     * @param mediaId The id of the object
     * @return The found object, or null if it does not exist
     */
    @JvmStatic
    fun getFeedMedia(mediaId: Long): FeedMedia? {
        Log.d(TAG, "getFeedMedia called")
        val adapter = getInstance()
        adapter.open()
        try {
            adapter.getSingleFeedMediaCursor(mediaId).use { mediaCursor ->
                if (!mediaCursor.moveToFirst()) return null

                val indexFeedItem = mediaCursor.getColumnIndex(PodDBAdapter.KEY_FEEDITEM)
                val itemId = mediaCursor.getLong(indexFeedItem)
                val media = FeedMediaCursorMapper.convert(mediaCursor)
                val item = getFeedItem(itemId)
                if (item != null) {
                    media.setItem(item)
                    item.setMedia(media)
                }
                return media
            }
        } finally {
            adapter.close()
        }
    }

    fun getFeedItemsWithUrl(urls: List<String?>?): List<FeedItem> {
        Log.d(TAG, "getFeedItemsWithUrl() called ")
        val adapter = getInstance()
        adapter.open()
        try {
            if (urls != null) {
                adapter.getFeedItemCursorByUrl(urls).use { itemCursor ->
                    val items = extractItemlistFromCursor(adapter, itemCursor).toMutableList()
                    loadAdditionalFeedItemListData(items)
                    items.sortWith(PlaybackCompletionDateComparator())
                    return items
                }
            }
        } finally {
            adapter.close()
        }
        return listOf()
    }

    fun getMonthlyTimeStatistics(): List<MonthlyStatisticsItem> {
        val months: MutableList<MonthlyStatisticsItem> = ArrayList()
        val adapter = getInstance()
        adapter.open()
        adapter.monthlyStatisticsCursor.use { cursor ->
            val indexMonth = cursor.getColumnIndexOrThrow("month")
            val indexYear = cursor.getColumnIndexOrThrow("year")
            val indexTotalDuration = cursor.getColumnIndexOrThrow("total_duration")
            while (cursor.moveToNext()) {
                val item = MonthlyStatisticsItem()
                item.month = cursor.getString(indexMonth).toInt()
                item.year = cursor.getString(indexYear).toInt()
                item.timePlayed = cursor.getLong(indexTotalDuration)
                months.add(item)
            }
        }
        adapter.close()
        return months
    }

    /**
     * Searches the DB for statistics.
     *
     * @return The list of statistics objects
     */
    fun getStatistics(includeMarkedAsPlayed: Boolean,
                      timeFilterFrom: Long, timeFilterTo: Long): StatisticsResult {
        val adapter = getInstance()
        adapter.open()

        val result = StatisticsResult()
        adapter.getFeedStatisticsCursor(includeMarkedAsPlayed, timeFilterFrom, timeFilterTo).use { cursor ->
            val indexOldestDate = cursor.getColumnIndexOrThrow("oldest_date")
            val indexNumEpisodes = cursor.getColumnIndexOrThrow("num_episodes")
            val indexEpisodesStarted = cursor.getColumnIndexOrThrow("episodes_started")
            val indexTotalTime = cursor.getColumnIndexOrThrow("total_time")
            val indexPlayedTime = cursor.getColumnIndexOrThrow("played_time")
            val indexNumDownloaded = cursor.getColumnIndexOrThrow("num_downloaded")
            val indexDownloadSize = cursor.getColumnIndexOrThrow("download_size")
            while (cursor.moveToNext()) {
                val feed = extractFeedFromCursorRow(cursor)

                val feedPlayedTime = cursor.getString(indexPlayedTime).toLong() / 1000
                val feedTotalTime = cursor.getString(indexTotalTime).toLong() / 1000
                val episodes = cursor.getString(indexNumEpisodes).toLong()
                val episodesStarted = cursor.getString(indexEpisodesStarted).toLong()
                val totalDownloadSize = cursor.getString(indexDownloadSize).toLong()
                val episodesDownloadCount = cursor.getString(indexNumDownloaded).toLong()
                val oldestDate = cursor.getString(indexOldestDate).toLong()

                if (episodes > 0 && oldestDate < Long.MAX_VALUE)
                    result.oldestDate = min(result.oldestDate.toDouble(), oldestDate.toDouble()).toLong()

                result.feedTime.add(StatisticsItem(feed, feedTotalTime, feedPlayedTime, episodes, episodesStarted, totalDownloadSize, episodesDownloadCount))
            }
        }
        adapter.close()
        return result
    }

    fun getTimeBetweenReleaseAndPlayback(timeFilterFrom: Long, timeFilterTo: Long): Long {
        val adapter = getInstance()
        adapter.open()
        adapter.getTimeBetweenReleaseAndPlayback(timeFilterFrom, timeFilterTo).use { cursor ->
            cursor.moveToFirst()
            val result = cursor.getString(0).toLong()
            adapter.close()
            return result
        }
    }

    /**
     * Returns data necessary for displaying the navigation drawer. This includes
     * the list of subscriptions, the number of items in the queue and the number of unread
     * items.
     */
    @JvmStatic
    fun getNavDrawerData(subscriptionsFilter: SubscriptionsFilter?): NavDrawerData {
        Log.d(TAG, "getNavDrawerData() called with: " + "")
        val adapter = getInstance()
        adapter.open()

        val feedCounters: Map<Long, Int> = adapter.getFeedCounters(feedCounterSetting)
//        getFeedList(adapter)

//        TODO:
//        if (false && subscriptionsFilter != null) {
//            feeds = subscriptionsFilter.filter(feeds, feedCounters as Map<Long?, Int>).toMutableList()
//        }

        val comparator: Comparator<Feed>
        val feedOrder = feedOrder
        when (feedOrder) {
            UserPreferences.FEED_ORDER_COUNTER -> {
                comparator = Comparator { lhs: Feed, rhs: Feed ->
                    val counterLhs = (if (feedCounters.containsKey(lhs.id)) feedCounters[lhs.id]!! else 0).toLong()
                    val counterRhs = (if (feedCounters.containsKey(rhs.id)) feedCounters[rhs.id]!! else 0).toLong()
                    when {
                        // reverse natural order: podcast with most unplayed episodes first
                        counterLhs > counterRhs -> return@Comparator -1
                        counterLhs == counterRhs -> return@Comparator lhs.title?.compareTo(rhs.title!!, ignoreCase = true) ?: -1
                        else -> return@Comparator 1
                    }
                }
            }
            UserPreferences.FEED_ORDER_ALPHABETICAL -> {
                comparator = Comparator { lhs: Feed, rhs: Feed ->
                    val t1 = lhs.title
                    val t2 = rhs.title
                    when {
                        t1 == null -> return@Comparator 1
                        t2 == null -> return@Comparator -1
                        else -> return@Comparator t1.compareTo(t2, ignoreCase = true)
                    }
                }
            }
            UserPreferences.FEED_ORDER_MOST_PLAYED -> {
                val playedCounters = adapter.getPlayedEpisodesCounters()

                comparator = Comparator { lhs: Feed, rhs: Feed ->
                    val counterLhs = (if (playedCounters.containsKey(lhs.id)) playedCounters[lhs.id] else 0)!!.toLong()
                    val counterRhs = (if (playedCounters.containsKey(rhs.id)) playedCounters[rhs.id] else 0)!!.toLong()
                    when {
                        // podcast with most played episodes first
                        counterLhs > counterRhs -> return@Comparator -1
                        counterLhs == counterRhs -> return@Comparator lhs.title!!.compareTo(rhs.title!!, ignoreCase = true)
                        else -> return@Comparator 1
                    }
                }
            }
            UserPreferences.FEED_ORDER_LAST_UPDATED -> {
                val recentPubDates = adapter.mostRecentItemDates
                comparator = Comparator { lhs: Feed, rhs: Feed ->
                    val dateLhs = if (recentPubDates.containsKey(lhs.id)) recentPubDates[lhs.id]!! else 0
                    val dateRhs = if (recentPubDates.containsKey(rhs.id)) recentPubDates[rhs.id]!! else 0
//                    Log.d(TAG, "FEED_ORDER_LAST_UPDATED ${lhs.title} ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(dateLhs))}")
                    dateRhs.compareTo(dateLhs)
                }
            }
            else -> {
                val recentUnreadPubDates = adapter.mostRecentUnreadItemDates
                comparator = Comparator { lhs: Feed, rhs: Feed ->
                    val dateLhs = if (recentUnreadPubDates.containsKey(lhs.id)) recentUnreadPubDates[lhs.id]!! else 0
                    val dateRhs = if (recentUnreadPubDates.containsKey(rhs.id)) recentUnreadPubDates[rhs.id]!! else 0
                    dateRhs.compareTo(dateLhs)
                }
            }
        }

        feeds.sortWith(comparator)
        val queueSize = adapter.queueSize
        val numNewItems = getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.NEW))
        val numDownloadedItems = getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.DOWNLOADED))

        val items: MutableList<FeedDrawerItem> = ArrayList()
        for (feed in feeds) {
            val counter = if (feedCounters.containsKey(feed.id)) feedCounters[feed.id]!! else 0
            val drawerItem = FeedDrawerItem(feed, feed.id, counter)
            items.add(drawerItem)
        }
        val result = NavDrawerData(items, queueSize, numNewItems, numDownloadedItems, feedCounters, EpisodeCleanupAlgorithmFactory.build().getReclaimableItems())
        adapter.close()
        return result
    }

    class MonthlyStatisticsItem {
        var year: Int = 0
        var month: Int = 0
        var timePlayed: Long = 0
    }

    class StatisticsResult {
        var feedTime: MutableList<StatisticsItem> = ArrayList()
        var oldestDate: Long = System.currentTimeMillis()
    }
}
