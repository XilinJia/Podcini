package ac.mdiq.podcini.storage.database

import android.content.ContentValues
import android.content.Context
import android.database.*
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CursorFactory
import android.database.sqlite.SQLiteOpenHelper
import android.text.TextUtils
import android.util.Log
import androidx.annotation.VisibleForTesting
import ac.mdiq.podcini.storage.model.download.DownloadResult
import ac.mdiq.podcini.storage.model.feed.*
import ac.mdiq.podcini.storage.model.feed.SortOrder.Companion.toCodeString
import ac.mdiq.podcini.storage.database.mapper.FeedItemFilterQuery.generateFrom
import ac.mdiq.podcini.storage.database.mapper.FeedItemSortQuery.generateFrom
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.util.*

/**
 * Implements methods for accessing the database
 */
class PodDBAdapter private constructor() {
    private val db: SQLiteDatabase
    private val dbHelper: PodDBHelper = PodDBHelper(context, DATABASE_NAME, null)

    init {
        db = openDb()
    }

    private fun openDb(): SQLiteDatabase {
        var newDb: SQLiteDatabase
        try {
            newDb = dbHelper.writableDatabase
            newDb.disableWriteAheadLogging()
        } catch (ex: SQLException) {
            Log.e(TAG, Log.getStackTraceString(ex))
            newDb = dbHelper.readableDatabase
        }
        return newDb
    }

    @Synchronized
    fun open(): PodDBAdapter {
        // do nothing
        return this
    }

    @Synchronized
    fun close() {
        // do nothing
    }

    /**
     * Inserts or updates a feed entry
     *
     * @return the id of the entry
     */
    private fun setFeed(feed: Feed): Long {
        val values = ContentValues()
        values.put(KEY_TITLE, feed.feedTitle)
        values.put(KEY_LINK, feed.link)
        values.put(KEY_DESCRIPTION, feed.description)
        values.put(KEY_PAYMENT_LINK, FeedFunding.getPaymentLinksAsString(feed.paymentLinks))
        values.put(KEY_AUTHOR, feed.author)
        values.put(KEY_LANGUAGE, feed.language)
        values.put(KEY_IMAGE_URL, feed.imageUrl)

        values.put(KEY_FILE_URL, feed.getFile_url())
        values.put(KEY_DOWNLOAD_URL, feed.download_url)
        values.put(KEY_DOWNLOADED, feed.isDownloaded())
        values.put(KEY_LASTUPDATE, feed.lastUpdate)
        values.put(KEY_TYPE, feed.type)
        values.put(KEY_FEED_IDENTIFIER, feed.feedIdentifier)

        values.put(KEY_IS_PAGED, feed.isPaged)
        values.put(KEY_NEXT_PAGE_LINK, feed.nextPageLink)
        if (!feed.itemFilter?.values.isNullOrEmpty()) {
            values.put(KEY_HIDE, TextUtils.join(",", feed.itemFilter!!.values))
        } else {
            values.put(KEY_HIDE, "")
        }
        values.put(KEY_SORT_ORDER, toCodeString(
            feed.sortOrder))
        values.put(KEY_LAST_UPDATE_FAILED, feed.hasLastUpdateFailed())
        if (feed.id == 0L) {
            // Create new entry
            Log.d(this.toString(), "Inserting new Feed into db")
            feed.id = db.insert(TABLE_NAME_FEEDS, null, values)
        } else {
            Log.d(this.toString(), "Updating existing Feed in db")
            db.update(TABLE_NAME_FEEDS, values, "$KEY_ID=?",
                arrayOf(feed.id.toString()))
        }
        return feed.id
    }

    fun setFeedPreferences(prefs: FeedPreferences) {
        require(prefs.feedID != 0L) { "Feed ID of preference must not be null" }

        val values = ContentValues()
        values.put(KEY_AUTO_DOWNLOAD_ENABLED, prefs.autoDownload)
        values.put(KEY_KEEP_UPDATED, prefs.keepUpdated)
        values.put(KEY_AUTO_DELETE_ACTION, prefs.currentAutoDelete.code)
        values.put(KEY_FEED_VOLUME_ADAPTION, prefs.volumeAdaptionSetting!!.toInteger())
        values.put(KEY_USERNAME, prefs.username)
        values.put(KEY_PASSWORD, prefs.password)
        values.put(KEY_INCLUDE_FILTER, prefs.filter.includeFilterRaw)
        values.put(KEY_EXCLUDE_FILTER, prefs.filter.excludeFilterRaw)
        values.put(KEY_MINIMAL_DURATION_FILTER, prefs.filter.minimalDurationFilter)
        values.put(KEY_FEED_PLAYBACK_SPEED, prefs.feedPlaybackSpeed)
        values.put(KEY_FEED_TAGS, prefs.tagsAsString)
        values.put(KEY_FEED_SKIP_INTRO, prefs.feedSkipIntro)
        values.put(KEY_FEED_SKIP_ENDING, prefs.feedSkipEnding)
        values.put(KEY_EPISODE_NOTIFICATION, prefs.showEpisodeNotification)
        values.put(KEY_NEW_EPISODES_ACTION, prefs.newEpisodesAction!!.code)
        db.update(TABLE_NAME_FEEDS, values, "$KEY_ID=?", arrayOf(
            prefs.feedID.toString()))
    }

    fun setFeedItemFilter(feedId: Long, filterValues: Set<String?>?) {
        val valuesList = TextUtils.join(",", filterValues!!)
        Log.d(TAG, String.format(Locale.US,
            "setFeedItemFilter() called with: feedId = [%d], filterValues = [%s]", feedId, valuesList))
        val values = ContentValues()
        values.put(KEY_HIDE, valuesList)
        db.update(TABLE_NAME_FEEDS, values, "$KEY_ID=?", arrayOf(feedId.toString()))
    }

    fun setFeedItemSortOrder(feedId: Long, sortOrder: SortOrder?) {
        val values = ContentValues()
        values.put(KEY_SORT_ORDER, toCodeString(sortOrder))
        db.update(TABLE_NAME_FEEDS, values, "$KEY_ID=?", arrayOf(feedId.toString()))
    }

    /**
     * Inserts or updates a media entry
     *
     * @return the id of the entry
     */
    fun setMedia(media: FeedMedia?): Long {
        val values = ContentValues()
        values.put(KEY_DURATION, media!!.getDuration())
        values.put(KEY_POSITION, media.getPosition())
        values.put(KEY_SIZE, media.size)
        values.put(KEY_MIME_TYPE, media.mime_type)
        values.put(KEY_DOWNLOAD_URL, media.download_url)
        values.put(KEY_DOWNLOADED, media.isDownloaded())
        values.put(KEY_FILE_URL, media.getFile_url())
        values.put(KEY_HAS_EMBEDDED_PICTURE, media.hasEmbeddedPicture())
        values.put(KEY_LAST_PLAYED_TIME, media.getLastPlayedTime())

        if (media.getPlaybackCompletionDate() != null) {
            values.put(KEY_PLAYBACK_COMPLETION_DATE, media.getPlaybackCompletionDate()!!.time)
        } else {
            values.put(KEY_PLAYBACK_COMPLETION_DATE, 0)
        }
        if (media.getItem() != null) {
            values.put(KEY_FEEDITEM, media.getItem()!!.id)
        }
        if (media.id == 0L) {
            media.id = db.insert(TABLE_NAME_FEED_MEDIA, null, values)
        } else {
            db.update(TABLE_NAME_FEED_MEDIA, values, "$KEY_ID=?",
                arrayOf(media.id.toString()))
        }
        return media.id
    }

    fun setFeedMediaPlaybackInformation(media: FeedMedia) {
        if (media.id != 0L) {
            val values = ContentValues()
            values.put(KEY_POSITION, media.getPosition())
            values.put(KEY_DURATION, media.getDuration())
            values.put(KEY_PLAYED_DURATION, media.playedDuration)
            values.put(KEY_LAST_PLAYED_TIME, media.getLastPlayedTime())
            db.update(TABLE_NAME_FEED_MEDIA, values, "$KEY_ID=?",
                arrayOf(media.id.toString()))
        } else {
            Log.e(TAG, "setFeedMediaPlaybackInformation: ID of media was 0")
        }
    }

    fun setFeedMediaPlaybackCompletionDate(media: FeedMedia) {
        if (media.id != 0L) {
            val values = ContentValues()
            values.put(KEY_PLAYBACK_COMPLETION_DATE, media.getPlaybackCompletionDate()!!.time)
            values.put(KEY_PLAYED_DURATION, media.playedDuration)
            db.update(TABLE_NAME_FEED_MEDIA, values, "$KEY_ID=?",
                arrayOf(media.id.toString()))
        } else {
            Log.e(TAG, "setFeedMediaPlaybackCompletionDate: ID of media was 0")
        }
    }

    fun resetAllMediaPlayedDuration() {
        try {
            db.beginTransactionNonExclusive()
            val values = ContentValues()
            values.put(KEY_PLAYED_DURATION, 0)
            db.update(TABLE_NAME_FEED_MEDIA, values, null, arrayOfNulls(0))
            db.setTransactionSuccessful()
        } catch (e: SQLException) {
            Log.e(TAG, Log.getStackTraceString(e))
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Insert all FeedItems of a feed and the feed object itself in a single
     * transaction
     */
    fun setCompleteFeed(vararg feeds: Feed) {
        try {
            db.beginTransactionNonExclusive()
            for (feed in feeds) {
                setFeed(feed)
                if (feed.items.isNotEmpty()) {
                    for (item in feed.items) {
                        updateOrInsertFeedItem(item, false)
                    }
                }
                if (feed.preferences != null) {
                    setFeedPreferences(feed.preferences!!)
                }
            }
            db.setTransactionSuccessful()
        } catch (e: SQLException) {
            Log.e(TAG, Log.getStackTraceString(e))
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Updates the download URL of a Feed.
     */
    fun setFeedDownloadUrl(original: String, updated: String?) {
        val values = ContentValues()
        values.put(KEY_DOWNLOAD_URL, updated)
        db.update(TABLE_NAME_FEEDS, values, "$KEY_DOWNLOAD_URL=?", arrayOf(original))
    }

    fun storeFeedItemlist(items: List<FeedItem>) {
        try {
            db.beginTransactionNonExclusive()
            for (item in items) {
                updateOrInsertFeedItem(item, true)
            }
            db.setTransactionSuccessful()
        } catch (e: SQLException) {
            Log.e(TAG, Log.getStackTraceString(e))
        } finally {
            db.endTransaction()
        }
    }

    fun setSingleFeedItem(item: FeedItem): Long {
        var result: Long = 0
        try {
            db.beginTransactionNonExclusive()
            result = updateOrInsertFeedItem(item, true)
            db.setTransactionSuccessful()
        } catch (e: SQLException) {
            Log.e(TAG, Log.getStackTraceString(e))
        } finally {
            db.endTransaction()
        }
        return result
    }

    /**
     * Inserts or updates a feeditem entry
     *
     * @param item     The FeedItem
     * @param saveFeed true if the Feed of the item should also be saved. This should be set to
     * false if the method is executed on a list of FeedItems of the same Feed.
     * @return the id of the entry
     */
    private fun updateOrInsertFeedItem(item: FeedItem, saveFeed: Boolean): Long {
        if (item.id == 0L && item.getPubDate() == null) {
            Log.e(TAG, "Newly saved item has no pubDate. Using current date as pubDate")
            item.setPubDate(Date())
        }

        val values = ContentValues()
        values.put(KEY_TITLE, item.title)
        values.put(KEY_LINK, item.link)
        if (item.description != null) {
            values.put(KEY_DESCRIPTION, item.description)
        }
        values.put(KEY_PUBDATE, item.getPubDate()!!.time)
        values.put(KEY_PAYMENT_LINK, item.paymentLink)
        if (item.feed != null) {
            if (saveFeed) setFeed(item.feed!!)
            values.put(KEY_FEED, item.feed!!.id)
        }
        if (item.isNew) {
            values.put(KEY_READ, FeedItem.NEW)
        } else if (item.isPlayed()) {
            values.put(KEY_READ, FeedItem.PLAYED)
        } else {
            values.put(KEY_READ, FeedItem.UNPLAYED)
        }
        values.put(KEY_HAS_CHAPTERS, item.chapters != null || item.hasChapters())
        values.put(KEY_ITEM_IDENTIFIER, item.itemIdentifier)
        values.put(KEY_AUTO_DOWNLOAD_ENABLED, item.isAutoDownloadEnabled)
        values.put(KEY_IMAGE_URL, item.imageUrl)
        values.put(KEY_PODCASTINDEX_CHAPTER_URL, item.podcastIndexChapterUrl)

        if (item.id == 0L) {
            item.id = db.insert(TABLE_NAME_FEED_ITEMS, null, values)
        } else {
            db.update(TABLE_NAME_FEED_ITEMS, values, "$KEY_ID=?",
                arrayOf(item.id.toString()))
        }
        if (item.media != null) {
            setMedia(item.media)
        }
        if (item.chapters != null) {
            setChapters(item)
        }
        return item.id
    }

    fun setFeedItemRead(played: Int, itemId: Long, mediaId: Long,
                        resetMediaPosition: Boolean
    ) {
        try {
            db.beginTransactionNonExclusive()
            val values = ContentValues()

            values.put(KEY_READ, played)
            db.update(TABLE_NAME_FEED_ITEMS, values, "$KEY_ID=?", arrayOf(itemId.toString()))

            if (resetMediaPosition) {
                values.clear()
                values.put(KEY_POSITION, 0)
                db.update(TABLE_NAME_FEED_MEDIA, values, "$KEY_ID=?", arrayOf(mediaId.toString()))
            }

            db.setTransactionSuccessful()
        } catch (e: SQLException) {
            Log.e(TAG, Log.getStackTraceString(e))
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Sets the 'read' attribute of the item.
     *
     * @param read    must be one of FeedItem.PLAYED, FeedItem.NEW, FeedItem.UNPLAYED
     * @param itemIds items to change the value of
     */
    fun setFeedItemRead(read: Int, vararg itemIds: Long) {
        try {
            db.beginTransactionNonExclusive()
            val values = ContentValues()
            for (id in itemIds) {
                values.clear()
                values.put(KEY_READ, read)
                db.update(TABLE_NAME_FEED_ITEMS, values, "$KEY_ID=?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } catch (e: SQLException) {
            Log.e(TAG, Log.getStackTraceString(e))
        } finally {
            db.endTransaction()
        }
    }

    private fun setChapters(item: FeedItem) {
        val values = ContentValues()
        for (chapter in item.chapters!!) {
            values.put(KEY_TITLE, chapter.title)
            values.put(KEY_START, chapter.start)
            values.put(KEY_FEEDITEM, item.id)
            values.put(KEY_LINK, chapter.link)
            values.put(KEY_IMAGE_URL, chapter.imageUrl)
            if (chapter.id == 0L) {
                chapter.id = db.insert(TABLE_NAME_SIMPLECHAPTERS, null, values)
            } else {
                db.update(TABLE_NAME_SIMPLECHAPTERS, values, "$KEY_ID=?",
                    arrayOf(chapter.id.toString()))
            }
        }
    }

    fun resetPagedFeedPage(feed: Feed) {
        val sql = ("UPDATE " + TABLE_NAME_FEEDS
                + " SET " + KEY_NEXT_PAGE_LINK + "=" + KEY_DOWNLOAD_URL
                + " WHERE " + KEY_ID + "=" + feed.id)
        db.execSQL(sql)
    }

    fun setFeedLastUpdateFailed(feedId: Long, failed: Boolean) {
        val sql = ("UPDATE " + TABLE_NAME_FEEDS
                + " SET " + KEY_LAST_UPDATE_FAILED + "=" + (if (failed) "1" else "0")
                + " WHERE " + KEY_ID + "=" + feedId)
        db.execSQL(sql)
    }

    fun setFeedCustomTitle(feedId: Long, customTitle: String?) {
        val values = ContentValues()
        values.put(KEY_CUSTOM_TITLE, customTitle)
        db.update(TABLE_NAME_FEEDS, values, "$KEY_ID=?", arrayOf(feedId.toString()))
    }

    /**
     * Inserts or updates a download status.
     */
    fun setDownloadStatus(status: DownloadResult): Long {
        val values = ContentValues()
        values.put(KEY_FEEDFILE, status.feedfileId)
        values.put(KEY_FEEDFILETYPE, status.feedfileType)
        values.put(KEY_REASON, status.reason?.code)
        values.put(KEY_SUCCESSFUL, status.isSuccessful)
        values.put(KEY_COMPLETION_DATE, status.getCompletionDate().time)
        values.put(KEY_REASON_DETAILED, status.reasonDetailed)
        values.put(KEY_DOWNLOADSTATUS_TITLE, status.title)
        if (status.id == 0L) {
            status.id = db.insert(TABLE_NAME_DOWNLOAD_LOG, null, values)
        } else {
            db.update(TABLE_NAME_DOWNLOAD_LOG, values, "$KEY_ID=?",
                arrayOf(status.id.toString()))
        }
        return status.id
    }

    fun setFavorites(favorites: List<FeedItem>) {
        val values = ContentValues()
        try {
            db.beginTransactionNonExclusive()
            db.delete(TABLE_NAME_FAVORITES, null, null)
            for (i in favorites.indices) {
                val item = favorites[i]
                values.put(KEY_ID, i)
                values.put(KEY_FEEDITEM, item.id)
                values.put(KEY_FEED, item.feed!!.id)
                db.insertWithOnConflict(TABLE_NAME_FAVORITES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } catch (e: SQLException) {
            Log.e(TAG, Log.getStackTraceString(e))
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Adds the item to favorites
     */
    fun addFavoriteItem(item: FeedItem) {
        // don't add an item that's already there...
        if (isItemInFavorites(item)) {
            Log.d(TAG, "item already in favorites")
            return
        }
        val values = ContentValues()
        values.put(KEY_FEEDITEM, item.id)
        values.put(KEY_FEED, item.feedId)
        db.insert(TABLE_NAME_FAVORITES, null, values)
    }

    fun removeFavoriteItem(item: FeedItem) {
        val deleteClause = String.format("DELETE FROM %s WHERE %s=%s AND %s=%s",
            TABLE_NAME_FAVORITES,
            KEY_FEEDITEM, item.id,
            KEY_FEED, item.feedId)
        db.execSQL(deleteClause)
    }

    private fun isItemInFavorites(item: FeedItem): Boolean {
        val query = String.format(Locale.US, "SELECT %s from %s WHERE %s=%d",
            KEY_ID, TABLE_NAME_FAVORITES, KEY_FEEDITEM, item.id)
        val c = db.rawQuery(query, null)
        val count = c.count
        c.close()
        return count > 0
    }

    fun setQueue(queue: List<FeedItem>) {
        val values = ContentValues()
        try {
            db.beginTransactionNonExclusive()
            db.delete(TABLE_NAME_QUEUE, null, null)
            for (i in queue.indices) {
                val item = queue[i]
                values.put(KEY_ID, i)
                values.put(KEY_FEEDITEM, item.id)
                values.put(KEY_FEED, item.feed!!.id)
                db.insertWithOnConflict(TABLE_NAME_QUEUE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } catch (e: SQLException) {
            Log.e(TAG, Log.getStackTraceString(e))
        } finally {
            db.endTransaction()
        }
    }

    fun clearQueue() {
        db.delete(TABLE_NAME_QUEUE, null, null)
    }

    /**
     * Remove the listed items and their FeedMedia entries.
     */
    fun removeFeedItems(items: List<FeedItem>) {
        try {
            val mediaIds = StringBuilder()
            val itemIds = StringBuilder()
            for (item in items) {
                if (item.media != null) {
                    if (mediaIds.isNotEmpty()) {
                        mediaIds.append(",")
                    }
                    mediaIds.append(item.media!!.id)
                }
                if (itemIds.isNotEmpty()) {
                    itemIds.append(",")
                }
                itemIds.append(item.id)
            }

            db.beginTransactionNonExclusive()
            db.delete(TABLE_NAME_SIMPLECHAPTERS, "$KEY_FEEDITEM IN ($itemIds)", null)
            db.delete(TABLE_NAME_DOWNLOAD_LOG, (KEY_FEEDFILETYPE + "=" + FeedMedia.FEEDFILETYPE_FEEDMEDIA
                    ) + " AND " + KEY_FEEDFILE + " IN (" + mediaIds + ")", null)
            db.delete(TABLE_NAME_FEED_MEDIA, "$KEY_ID IN ($mediaIds)", null)
            db.delete(TABLE_NAME_FEED_ITEMS, "$KEY_ID IN ($itemIds)", null)
            db.setTransactionSuccessful()
        } catch (e: SQLException) {
            Log.e(TAG, Log.getStackTraceString(e))
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Remove a feed with all its FeedItems and Media entries.
     */
    fun removeFeed(feed: Feed) {
        try {
            db.beginTransactionNonExclusive()
            if (feed.items.isNotEmpty()) {
                removeFeedItems(feed.items)
            }
            // delete download log entries for feed
            db.delete(TABLE_NAME_DOWNLOAD_LOG, "$KEY_FEEDFILE=? AND $KEY_FEEDFILETYPE=?",
                arrayOf(feed.id.toString(), Feed.FEEDFILETYPE_FEED.toString()))

            db.delete(TABLE_NAME_FEEDS, "$KEY_ID=?",
                arrayOf(feed.id.toString()))
            db.setTransactionSuccessful()
        } catch (e: SQLException) {
            Log.e(TAG, Log.getStackTraceString(e))
        } finally {
            db.endTransaction()
        }
    }

    fun clearPlaybackHistory() {
        val values = ContentValues()
        values.put(KEY_PLAYBACK_COMPLETION_DATE, 0)
        db.update(TABLE_NAME_FEED_MEDIA, values, null, null)
    }

    fun clearDownloadLog() {
        db.delete(TABLE_NAME_DOWNLOAD_LOG, null, null)
    }

    val allFeedsCursor: Cursor
        /**
         * Get all Feeds from the Feed Table.
         *
         * @return The cursor of the query
         */
        get() {
            val query = ("SELECT " + KEYS_FEED
                    + " FROM " + TABLE_NAME_FEEDS
                    + " ORDER BY " + TABLE_NAME_FEEDS + "." + KEY_TITLE + " COLLATE NOCASE ASC")
            return db.rawQuery(query, null)
        }

    val feedCursorDownloadUrls: Cursor
        get() = db.query(TABLE_NAME_FEEDS, arrayOf(KEY_ID, KEY_DOWNLOAD_URL), null, null, null, null, null)

    /**
     * Returns a cursor with all FeedItems of a Feed. Uses FEEDITEM_SEL_FI_SMALL
     *
     * @param feed The feed you want to get the FeedItems from.
     * @return The cursor of the query
     */
    fun getItemsOfFeedCursor(feed: Feed, filter: FeedItemFilter?): Cursor {
        val filterQuery = generateFrom(filter!!)
        val whereClauseAnd = if ("" == filterQuery) "" else " AND $filterQuery"
        val query = (SELECT_FEED_ITEMS_AND_MEDIA
                + " WHERE " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + "=" + feed.id
                + whereClauseAnd)
        return db.rawQuery(query, null)
    }

    /**
     * Return the description and content_encoded of item
     */
    fun getDescriptionOfItem(item: FeedItem): Cursor {
        val query = ("SELECT " + KEY_DESCRIPTION
                + " FROM " + TABLE_NAME_FEED_ITEMS
                + " WHERE " + KEY_ID + "=" + item.id)
        return db.rawQuery(query, null)
    }

    fun getSimpleChaptersOfFeedItemCursor(item: FeedItem): Cursor {
        return db.query(TABLE_NAME_SIMPLECHAPTERS, null, KEY_FEEDITEM
                + "=?", arrayOf(item.id.toString()), null,
            null, null
        )
    }

    fun getDownloadLog(feedFileType: Int, feedFileId: Long): Cursor {
        val query = ("SELECT * FROM " + TABLE_NAME_DOWNLOAD_LOG +
                " WHERE " + KEY_FEEDFILE + "=" + feedFileId + " AND " + KEY_FEEDFILETYPE + "=" + feedFileType
                + " ORDER BY " + KEY_ID + " DESC")
        return db.rawQuery(query, null)
    }

    fun getDownloadLogCursor(limit: Int): Cursor {
        return db.query(TABLE_NAME_DOWNLOAD_LOG, null, null, null, null,
            null, "$KEY_COMPLETION_DATE DESC LIMIT $limit")
    }

    val queueCursor: Cursor
        /**
         * Returns a cursor which contains all feed items in the queue. The returned
         * cursor uses the FEEDITEM_SEL_FI_SMALL selection.
         * cursor uses the FEEDITEM_SEL_FI_SMALL selection.
         */
        get() {
            val query = ("SELECT " + KEYS_FEED_ITEM_WITHOUT_DESCRIPTION + ", " + KEYS_FEED_MEDIA
                    + " FROM " + TABLE_NAME_QUEUE
                    + " INNER JOIN " + TABLE_NAME_FEED_ITEMS
                    + " ON " + SELECT_KEY_ITEM_ID + " = " + TABLE_NAME_QUEUE + "." + KEY_FEEDITEM
                    + JOIN_FEED_ITEM_AND_MEDIA
                    + " ORDER BY " + TABLE_NAME_QUEUE + "." + KEY_ID)
            return db.rawQuery(query, null)
        }

    val queueIDCursor: Cursor
        get() = db.query(TABLE_NAME_QUEUE, arrayOf(KEY_FEEDITEM), null, null, null, null, "$KEY_ID ASC", null)

    fun getNextInQueue(item: FeedItem): Cursor {
        val query = ("SELECT " + KEYS_FEED_ITEM_WITHOUT_DESCRIPTION + ", " + KEYS_FEED_MEDIA
                + " FROM " + TABLE_NAME_QUEUE
                + " INNER JOIN " + TABLE_NAME_FEED_ITEMS
                + " ON " + SELECT_KEY_ITEM_ID + " = " + TABLE_NAME_QUEUE + "." + KEY_FEEDITEM
                + JOIN_FEED_ITEM_AND_MEDIA
                + " WHERE Queue.ID > (SELECT Queue.ID FROM Queue WHERE Queue.FeedItem = "
                + item.id
                + ")"
                + " ORDER BY Queue.ID"
                + " LIMIT 1")
        return db.rawQuery(query, null)
    }

    fun getPausedQueueCursor(limit: Int): Cursor {
        val hasPositionOrRecentlyPlayed = (TABLE_NAME_FEED_MEDIA + "." + KEY_POSITION + " >= 1000"
                + " OR " + TABLE_NAME_FEED_MEDIA + "." + KEY_LAST_PLAYED_TIME
                + " >= " + (System.currentTimeMillis() - 30000))
        val query = ("SELECT " + KEYS_FEED_ITEM_WITHOUT_DESCRIPTION + ", " + KEYS_FEED_MEDIA
                + " FROM " + TABLE_NAME_QUEUE
                + " INNER JOIN " + TABLE_NAME_FEED_ITEMS
                + " ON " + SELECT_KEY_ITEM_ID + " = " + TABLE_NAME_QUEUE + "." + KEY_FEEDITEM
                + JOIN_FEED_ITEM_AND_MEDIA
                + " ORDER BY (CASE WHEN " + hasPositionOrRecentlyPlayed + " THEN "
                + TABLE_NAME_FEED_MEDIA + "." + KEY_LAST_PLAYED_TIME + " ELSE 0 END) DESC , "
                + TABLE_NAME_QUEUE + "." + KEY_ID
                + " LIMIT " + limit)
        return db.rawQuery(query, null)
    }

    fun getFavoritesIdsCursor(offset: Int, limit: Int): Cursor {
        // Way faster than selecting all columns
        val query = ("SELECT " + TABLE_NAME_FEED_ITEMS + "." + KEY_ID
                + " FROM " + TABLE_NAME_FEED_ITEMS
                + " INNER JOIN " + TABLE_NAME_FAVORITES
                + " ON " + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + " = " + TABLE_NAME_FAVORITES + "." + KEY_FEEDITEM
                + " ORDER BY " + TABLE_NAME_FEED_ITEMS + "." + KEY_PUBDATE + " DESC"
                + " LIMIT " + offset + ", " + limit)
        return db.rawQuery(query, null)
    }

    fun setFeedItems(oldState: Int, newState: Int) {
        setFeedItems(oldState, newState, 0)
    }

    fun setFeedItems(oldState: Int, newState: Int, feedId: Long) {
        var sql = "UPDATE $TABLE_NAME_FEED_ITEMS SET $KEY_READ=$newState"
        if (feedId > 0) {
            sql += " WHERE $KEY_FEED=$feedId"
        }
        if (FeedItem.NEW <= oldState && oldState <= FeedItem.PLAYED) {
            sql += if (feedId > 0) " AND " else " WHERE "
            sql += "$KEY_READ=$oldState"
        }
        db.execSQL(sql)
    }

    fun getEpisodesCursor(offset: Int, limit: Int, filter: FeedItemFilter?, sortOrder: SortOrder?): Cursor {
        val orderByQuery = generateFrom(sortOrder)
        val filterQuery = generateFrom(filter!!)
        val whereClause = if ("" == filterQuery) "" else " WHERE $filterQuery"
        val query = (SELECT_FEED_ITEMS_AND_MEDIA + whereClause
                + "ORDER BY " + orderByQuery + " LIMIT " + offset + ", " + limit)
        return db.rawQuery(query, null)
    }

    fun getEpisodeCountCursor(filter: FeedItemFilter?): Cursor {
        val filterQuery = generateFrom(filter!!)
        val whereClause = if ("" == filterQuery) "" else " WHERE $filterQuery"
        val query = ("SELECT count(" + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + ") FROM " + TABLE_NAME_FEED_ITEMS
                + JOIN_FEED_ITEM_AND_MEDIA + whereClause)
        return db.rawQuery(query, null)
    }

    fun getRandomEpisodesCursor(limit: Int, seed: Int): Cursor {
        val allItemsRandomOrder = (SELECT_FEED_ITEMS_AND_MEDIA
                + " WHERE (" + KEY_READ + " = " + FeedItem.NEW + " OR " + KEY_READ + " = " + FeedItem.UNPLAYED + ") " // Only from the last two years. Older episodes often contain broken covers and stuff like that
                + " AND " + KEY_PUBDATE + " > " + (System.currentTimeMillis() - 1000L * 3600L * 24L * 356L * 2) // Hide episodes that have been played but not completed
                + " AND (" + KEY_LAST_PLAYED_TIME + " == 0"
                + " OR " + KEY_LAST_PLAYED_TIME + " > " + (System.currentTimeMillis() - 1000L * 3600L) + ")"
                + " ORDER BY " + randomEpisodeNumber(seed))
        val query = ("SELECT * FROM (" + allItemsRandomOrder + ")"
                + " GROUP BY " + KEY_FEED
                + " ORDER BY " + randomEpisodeNumber(seed * 3) + " DESC LIMIT " + limit)
        return db.rawQuery(query, null)
    }

    /**
     * SQLite does not support random seeds. Create our own "random" number based on that seed and the item ID
     */
    private fun randomEpisodeNumber(seed: Int): String {
        return "(($SELECT_KEY_ITEM_ID * $seed) % 46471)"
    }

    /**
     * Returns a cursor which contains feed media objects with a playback
     * completion date in ascending order.
     *
     * @param offset The row to start at.
     * @param limit The maximum row count of the returned cursor. Must be an
     * integer >= 0.
     * @throws IllegalArgumentException if limit < 0
     */
    fun getCompletedMediaCursor(offset: Int, limit: Int): Cursor {
        require(limit >= 0) { "Limit must be >= 0" }

        return db.query(TABLE_NAME_FEED_MEDIA, null,
            "$KEY_PLAYBACK_COMPLETION_DATE > 0", null, null,
            null, String.format(Locale.US, "%s DESC LIMIT %d, %d", KEY_PLAYBACK_COMPLETION_DATE, offset, limit))
    }

    val completedMediaLength: Long
        get() = DatabaseUtils.queryNumEntries(db, TABLE_NAME_FEED_MEDIA, "$KEY_PLAYBACK_COMPLETION_DATE> 0")

    fun getSingleFeedMediaCursor(id: Long): Cursor {
        val query = ("SELECT " + KEYS_FEED_MEDIA + " FROM " + TABLE_NAME_FEED_MEDIA
                + " WHERE " + KEY_ID + "=" + id)
        return db.rawQuery(query, null)
    }

    fun getFeedCursor(id: Long): Cursor {
        val query = ("SELECT " + KEYS_FEED
                + " FROM " + TABLE_NAME_FEEDS
                + " WHERE " + SELECT_KEY_FEED_ID + " = " + id)
        return db.rawQuery(query, null)
    }

    fun getFeedItemCursor(id: String): Cursor {
        return getFeedItemCursor(arrayOf(id))
    }

    fun getFeedItemCursor(ids: Array<String>): Cursor {
        require(ids.size <= IN_OPERATOR_MAXIMUM) { "number of IDs must not be larger than $IN_OPERATOR_MAXIMUM" }
        val query = (SELECT_FEED_ITEMS_AND_MEDIA
                + " WHERE " + SELECT_KEY_ITEM_ID + " IN (" + TextUtils.join(",", ids) + ")")
        return db.rawQuery(query, null)
    }

    fun getFeedItemCursorByUrl(urls: List<String?>): Cursor {
        require(urls.size <= IN_OPERATOR_MAXIMUM) { "number of IDs must not be larger than $IN_OPERATOR_MAXIMUM" }
        val urlsString = StringBuilder()
        for (i in urls.indices) {
            if (i != 0) {
                urlsString.append(",")
            }
            urlsString.append(DatabaseUtils.sqlEscapeString(urls[i]))
        }
        val query = (SELECT_FEED_ITEMS_AND_MEDIA
                + " WHERE " + KEY_DOWNLOAD_URL + " IN (" + urlsString + ")")
        return db.rawQuery(query, null)
    }

    fun getFeedItemCursor(guid: String?, episodeUrl: String?): Cursor {
        val escapedEpisodeUrl = DatabaseUtils.sqlEscapeString(episodeUrl)
        var whereClauseCondition = "$TABLE_NAME_FEED_MEDIA.$KEY_DOWNLOAD_URL=$escapedEpisodeUrl"

        if (guid != null) {
            val escapedGuid = DatabaseUtils.sqlEscapeString(guid)
            whereClauseCondition = "$TABLE_NAME_FEED_ITEMS.$KEY_ITEM_IDENTIFIER=$escapedGuid"
        }

        val query = (SELECT_FEED_ITEMS_AND_MEDIA
                + " INNER JOIN " + TABLE_NAME_FEEDS
                + " ON " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + "=" + TABLE_NAME_FEEDS + "." + KEY_ID
                + " WHERE " + whereClauseCondition)
        return db.rawQuery(query, null)
    }

    fun getImageAuthenticationCursor(imageUrl: String?): Cursor {
        val downloadUrl = DatabaseUtils.sqlEscapeString(imageUrl)
        val query = (""
                + "SELECT " + KEY_USERNAME + "," + KEY_PASSWORD + " FROM " + TABLE_NAME_FEED_ITEMS
                + " INNER JOIN " + TABLE_NAME_FEEDS
                + " ON " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + " = " + TABLE_NAME_FEEDS + "." + KEY_ID
                + " WHERE " + TABLE_NAME_FEED_ITEMS + "." + KEY_IMAGE_URL + "=" + downloadUrl
                + " UNION SELECT " + KEY_USERNAME + "," + KEY_PASSWORD + " FROM " + TABLE_NAME_FEEDS
                + " WHERE " + TABLE_NAME_FEEDS + "." + KEY_IMAGE_URL + "=" + downloadUrl)
        return db.rawQuery(query, null)
    }

    val monthlyStatisticsCursor: Cursor
        get() {
            val query = ("SELECT SUM(" + KEY_PLAYED_DURATION + ") AS total_duration"
                    + ", strftime('%m', datetime(" + KEY_LAST_PLAYED_TIME + "/1000, 'unixepoch')) AS month"
                    + ", strftime('%Y', datetime(" + KEY_LAST_PLAYED_TIME + "/1000, 'unixepoch')) AS year"
                    + " FROM " + TABLE_NAME_FEED_MEDIA
                    + " WHERE " + KEY_LAST_PLAYED_TIME + " > 0 AND " + KEY_PLAYED_DURATION + " > 0"
                    + " GROUP BY year, month"
                    + " ORDER BY year, month")
            return db.rawQuery(query, null)
        }

    fun getFeedStatisticsCursor(includeMarkedAsPlayed: Boolean, timeFilterFrom: Long, timeFilterTo: Long): Cursor {
        val lastPlayedTime = "$TABLE_NAME_FEED_MEDIA.$KEY_LAST_PLAYED_TIME"
        var wasStarted = (TABLE_NAME_FEED_MEDIA + "." + KEY_PLAYBACK_COMPLETION_DATE + " > 0"
                + " AND " + TABLE_NAME_FEED_MEDIA + "." + KEY_PLAYED_DURATION + " > 0")
        if (includeMarkedAsPlayed) {
            wasStarted = ("(" + wasStarted + ") OR "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_READ + "=" + FeedItem.PLAYED + " OR "
                    + TABLE_NAME_FEED_MEDIA + "." + KEY_POSITION + "> 0")
        }
        val timeFilter = (lastPlayedTime + ">=" + timeFilterFrom
                + " AND " + lastPlayedTime + "<" + timeFilterTo)
        var playedTime = "$TABLE_NAME_FEED_MEDIA.$KEY_PLAYED_DURATION"
        if (includeMarkedAsPlayed) {
            playedTime = ("(CASE WHEN " + playedTime + " != 0"
                    + " THEN " + playedTime + " ELSE ("
                    + "CASE WHEN " + TABLE_NAME_FEED_ITEMS + "." + KEY_READ + "=" + FeedItem.PLAYED
                    + " THEN " + TABLE_NAME_FEED_MEDIA + "." + KEY_DURATION + " ELSE 0 END"
                    + ") END)")
        }

        val query = ("SELECT " + KEYS_FEED + ", "
                + "COUNT(*) AS num_episodes, "
                + "MIN(CASE WHEN " + lastPlayedTime + " > 0"
                + " THEN " + lastPlayedTime + " ELSE " + Long.MAX_VALUE + " END) AS oldest_date, "
                + "SUM(CASE WHEN (" + wasStarted + ") THEN 1 ELSE 0 END) AS episodes_started, "
                + "IFNULL(SUM(CASE WHEN (" + timeFilter + ")"
                + " THEN (" + playedTime + ") ELSE 0 END), 0) AS played_time, "
                + "IFNULL(SUM(" + TABLE_NAME_FEED_MEDIA + "." + KEY_DURATION + "), 0) AS total_time, "
                + "SUM(CASE WHEN " + TABLE_NAME_FEED_MEDIA + "." + KEY_DOWNLOADED + " > 0"
                + " THEN 1 ELSE 0 END) AS num_downloaded, "
                + "SUM(CASE WHEN " + TABLE_NAME_FEED_MEDIA + "." + KEY_DOWNLOADED + " > 0"
                + " THEN " + TABLE_NAME_FEED_MEDIA + "." + KEY_SIZE + " ELSE 0 END) AS download_size"
                + " FROM " + TABLE_NAME_FEED_ITEMS
                + JOIN_FEED_ITEM_AND_MEDIA
                + " INNER JOIN " + TABLE_NAME_FEEDS
                + " ON " + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + "=" + TABLE_NAME_FEEDS + "." + KEY_ID
                + " GROUP BY " + TABLE_NAME_FEEDS + "." + KEY_ID)
        return db.rawQuery(query, null)
    }

    fun getTimeBetweenReleaseAndPlayback(timeFilterFrom: Long, timeFilterTo: Long): Cursor {
        val from = (" FROM " + TABLE_NAME_FEED_ITEMS
                + JOIN_FEED_ITEM_AND_MEDIA
                + " WHERE " + TABLE_NAME_FEED_MEDIA + "." + KEY_LAST_PLAYED_TIME + ">=" + timeFilterFrom
                + " AND " + TABLE_NAME_FEED_ITEMS + "." + KEY_PUBDATE + ">=" + timeFilterFrom
                + " AND " + TABLE_NAME_FEED_MEDIA + "." + KEY_LAST_PLAYED_TIME + "<" + timeFilterTo)
        val query = ("SELECT " + TABLE_NAME_FEED_MEDIA + "." + KEY_LAST_PLAYED_TIME
                + " - " + TABLE_NAME_FEED_ITEMS + "." + KEY_PUBDATE + " AS diff"
                + from
                + " ORDER BY diff ASC"
                + " LIMIT 1"
                + " OFFSET (SELECT count(*)/2 " + from + ")")
        return db.rawQuery(query, null)
    }

    val queueSize: Int
        get() {
            val query = String.format("SELECT COUNT(%s) FROM %s", KEY_ID, TABLE_NAME_QUEUE)
            val c = db.rawQuery(query, null)
            var result = 0
            if (c.moveToFirst()) {
                result = c.getInt(0)
            }
            c.close()
            return result
        }

    fun getFeedCounters(setting: FeedCounter?, vararg feedIds: Long): Map<Long, Int> {
        val whereRead = when (setting) {
            FeedCounter.SHOW_NEW -> KEY_READ + "=" + FeedItem.NEW
            FeedCounter.SHOW_UNPLAYED -> ("(" + KEY_READ + "=" + FeedItem.NEW
                    + " OR " + KEY_READ + "=" + FeedItem.UNPLAYED + ")")
            FeedCounter.SHOW_DOWNLOADED -> "$KEY_DOWNLOADED=1"
            FeedCounter.SHOW_DOWNLOADED_UNPLAYED -> ("(" + KEY_READ + "=" + FeedItem.NEW
                    + " OR " + KEY_READ + "=" + FeedItem.UNPLAYED + ")"
                    + " AND " + KEY_DOWNLOADED + "=1")
            FeedCounter.SHOW_NONE -> return HashMap()
            else -> return HashMap()
        }
        return conditionalFeedCounterRead(whereRead, *feedIds)
    }

    private fun conditionalFeedCounterRead(whereRead: String, vararg feedIds: Long): Map<Long, Int> {
        var limitFeeds = ""
        if (feedIds.isNotEmpty()) {
            // work around TextUtils.join wanting only boxed items
            // and StringUtils.join() causing NoSuchMethodErrors on MIUI
            val builder = StringBuilder()
            for (id in feedIds) {
                builder.append(id)
                builder.append(',')
            }
            // there's an extra ',', get rid of it
            builder.deleteCharAt(builder.length - 1)
            limitFeeds = "$KEY_FEED IN ($builder) AND "
        }

        val query = ("SELECT " + KEY_FEED + ", COUNT(" + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + ") AS count "
                + " FROM " + TABLE_NAME_FEED_ITEMS
                + " LEFT JOIN " + TABLE_NAME_FEED_MEDIA + " ON "
                + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + "=" + TABLE_NAME_FEED_MEDIA + "." + KEY_FEEDITEM
                + " WHERE " + limitFeeds + " "
                + whereRead + " GROUP BY " + KEY_FEED)

        val c = db.rawQuery(query, null)
        val result: MutableMap<Long, Int> = HashMap()
        if (c.moveToFirst()) {
            do {
                val feedId = c.getLong(0)
                val count = c.getInt(1)
                result[feedId] = count
            } while (c.moveToNext())
        }
        c.close()
        return result
    }

    fun getPlayedEpisodesCounters(vararg feedIds: Long): Map<Long, Int> {
        val whereRead = KEY_READ + "=" + FeedItem.PLAYED
        return conditionalFeedCounterRead(whereRead, *feedIds)
    }

    val mostRecentItemDates: Map<Long, Long>
        get() {
            val query = ("SELECT " + KEY_FEED + ","
                    + " MAX(" + TABLE_NAME_FEED_ITEMS + "." + KEY_PUBDATE + ") AS most_recent_pubdate"
                    + " FROM " + TABLE_NAME_FEED_ITEMS
                    + " GROUP BY " + KEY_FEED)

            val c = db.rawQuery(query, null)
            val result: MutableMap<Long, Long> = HashMap()
            if (c.moveToFirst()) {
                do {
                    val feedId = c.getLong(0)
                    val date = c.getLong(1)
                    result[feedId] = date
                } while (c.moveToNext())
            }
            c.close()
            return result
        }

    /**
     * Uses DatabaseUtils to escape a search query and removes ' at the
     * beginning and the end of the string returned by the escape method.
     */
    private fun prepareSearchQuery(query: String): Array<String> {
        val queryWords = query.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (i in queryWords.indices) {
            val builder = StringBuilder()
            DatabaseUtils.appendEscapedSQLString(builder, queryWords[i])
            builder.deleteCharAt(0)
            builder.deleteCharAt(builder.length - 1)
            queryWords[i] = builder.toString()
        }

        return queryWords
    }

    /**
     * Searches for the given query in various values of all items or the items
     * of a specified feed.
     *
     * @return A cursor with all search results in SEL_FI_EXTRA selection.
     */
    fun searchItems(feedID: Long, searchQuery: String): Cursor {
        val queryWords = prepareSearchQuery(searchQuery)
        val queryFeedId = if (feedID != 0L) {
            // search items in specific feed
            "$KEY_FEED = $feedID"
        } else {
            // search through all items
            "1 = 1"
        }

        val queryStart = (SELECT_FEED_ITEMS_AND_MEDIA_WITH_DESCRIPTION
                + " WHERE " + queryFeedId + " AND (")
        val sb = StringBuilder(queryStart)

        for (i in queryWords.indices) {
            sb
                .append("(")
                .append("$KEY_DESCRIPTION LIKE '%").append(queryWords[i])
                .append("%' OR ")
                .append(KEY_TITLE).append(" LIKE '%").append(queryWords[i])
                .append("%') ")

            if (i != queryWords.size - 1) {
                sb.append("AND ")
            }
        }

        sb.append(") ORDER BY $KEY_PUBDATE DESC LIMIT 300")

        return db.rawQuery(sb.toString(), null)
    }

    /**
     * Searches for the given query in various values of all feeds.
     *
     * @return A cursor with all search results in SEL_FI_EXTRA selection.
     */
    fun searchFeeds(searchQuery: String): Cursor {
        val queryWords = prepareSearchQuery(searchQuery)

        val queryStart = "SELECT $KEYS_FEED FROM $TABLE_NAME_FEEDS WHERE "
        val sb = StringBuilder(queryStart)

        for (i in queryWords.indices) {
            sb
                .append("(")
                .append(KEY_TITLE).append(" LIKE '%").append(queryWords[i])
                .append("%' OR ")
                .append(KEY_CUSTOM_TITLE).append(" LIKE '%").append(queryWords[i])
                .append("%' OR ")
                .append(KEY_AUTHOR).append(" LIKE '%").append(queryWords[i])
                .append("%' OR ")
                .append(KEY_DESCRIPTION).append(" LIKE '%").append(queryWords[i])
                .append("%') ")

            if (i != queryWords.size - 1) {
                sb.append("AND ")
            }
        }

        sb.append("ORDER BY $KEY_TITLE ASC LIMIT 300")

        return db.rawQuery(sb.toString(), null)
    }

    /**
     * Insert raw data to the database.
     * Call method only for unit tests.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun insertTestData(table: String, values: ContentValues) {
        db.insert(table, null, values)
    }

    /**
     * Called when a database corruption happens.
     */
    class PodDbErrorHandler : DatabaseErrorHandler {
        override fun onCorruption(db: SQLiteDatabase) {
            Log.e(TAG, "Database corrupted: " + db.path)

            val dbPath = File(db.path)
            val backupFolder = context.getExternalFilesDir(null)
            val backupFile = File(backupFolder, "CorruptedDatabaseBackup.db")
            try {
                FileUtils.copyFile(dbPath, backupFile)
                Log.d(TAG, "Dumped database to " + backupFile.path)
            } catch (e: IOException) {
                Log.d(TAG, Log.getStackTraceString(e))
            }

            DefaultDatabaseErrorHandler().onCorruption(db) // This deletes the database
        }
    }

    /**
     * Helper class for opening the Podcini database.
     */
    private class PodDBHelper
    /**
     * Constructor.
     *
     * @param context Context to use
     * @param name    Name of the database
     * @param factory to use for creating cursor objects
     */
        (context: Context, name: String?, factory: CursorFactory?) :
        SQLiteOpenHelper(context, name, factory, VERSION, PodDbErrorHandler()) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(CREATE_TABLE_FEEDS)
            db.execSQL(CREATE_TABLE_FEED_ITEMS)
            db.execSQL(CREATE_TABLE_FEED_MEDIA)
            db.execSQL(CREATE_TABLE_DOWNLOAD_LOG)
            db.execSQL(CREATE_TABLE_QUEUE)
            db.execSQL(CREATE_TABLE_SIMPLECHAPTERS)
            db.execSQL(CREATE_TABLE_FAVORITES)

            db.execSQL(CREATE_INDEX_FEEDITEMS_FEED)
            db.execSQL(CREATE_INDEX_FEEDITEMS_PUBDATE)
            db.execSQL(CREATE_INDEX_FEEDITEMS_READ)
            db.execSQL(CREATE_INDEX_FEEDMEDIA_FEEDITEM)
            db.execSQL(CREATE_INDEX_QUEUE_FEEDITEM)
            db.execSQL(CREATE_INDEX_SIMPLECHAPTERS_FEEDITEM)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            Log.w("DBAdapter", "Upgrading from version $oldVersion to $newVersion.")
            DBUpgrader.upgrade(db, oldVersion, newVersion)

            db.execSQL("DELETE FROM " + TABLE_NAME_DOWNLOAD_LOG + " WHERE "
                    + KEY_COMPLETION_DATE + "<" + (System.currentTimeMillis() - 7L * 24L * 3600L * 1000L))
        }
    }

    companion object {
        private const val TAG = "PodDBAdapter"
        const val DATABASE_NAME: String = "Podcini.db"
        const val VERSION: Int = 3010000

        /**
         * Maximum number of arguments for IN-operator.
         */
        private const val IN_OPERATOR_MAXIMUM = 800

        // Key-constants
        const val KEY_ID: String = "id"
        const val KEY_TITLE: String = "title"
        const val KEY_CUSTOM_TITLE: String = "custom_title"
        const val KEY_LINK: String = "link"
        const val KEY_DESCRIPTION: String = "description"
        const val KEY_FILE_URL: String = "file_url"
        const val KEY_DOWNLOAD_URL: String = "download_url"
        const val KEY_PUBDATE: String = "pubDate"
        const val KEY_READ: String = "read"
        const val KEY_DURATION: String = "duration"
        const val KEY_POSITION: String = "position"
        const val KEY_SIZE: String = "filesize"
        const val KEY_MIME_TYPE: String = "mime_type"
        const val KEY_IMAGE_URL: String = "image_url"
        const val KEY_FEED: String = "feed"
        const val KEY_MEDIA: String = "media"
        const val KEY_DOWNLOADED: String = "downloaded"
        const val KEY_LASTUPDATE: String = "last_update"
        const val KEY_FEEDFILE: String = "feedfile"
        const val KEY_REASON: String = "reason"
        const val KEY_SUCCESSFUL: String = "successful"
        const val KEY_FEEDFILETYPE: String = "feedfile_type"
        const val KEY_COMPLETION_DATE: String = "completion_date"
        const val KEY_FEEDITEM: String = "feeditem"
        const val KEY_PAYMENT_LINK: String = "payment_link"
        const val KEY_START: String = "start"
        const val KEY_LANGUAGE: String = "language"
        const val KEY_AUTHOR: String = "author"
        const val KEY_HAS_CHAPTERS: String = "has_simple_chapters"
        const val KEY_TYPE: String = "type"
        const val KEY_ITEM_IDENTIFIER: String = "item_identifier"
        const val KEY_FEED_IDENTIFIER: String = "feed_identifier"
        const val KEY_REASON_DETAILED: String = "reason_detailed"
        const val KEY_DOWNLOADSTATUS_TITLE: String = "title"
        const val KEY_PLAYBACK_COMPLETION_DATE: String = "playback_completion_date"
        const val KEY_AUTO_DOWNLOAD_ENABLED: String = "auto_download" // Both tables use the same key
        const val KEY_KEEP_UPDATED: String = "keep_updated"
        const val KEY_AUTO_DELETE_ACTION: String = "auto_delete_action"
        const val KEY_FEED_VOLUME_ADAPTION: String = "feed_volume_adaption"
        const val KEY_PLAYED_DURATION: String = "played_duration"
        const val KEY_USERNAME: String = "username"
        const val KEY_PASSWORD: String = "password"
        const val KEY_IS_PAGED: String = "is_paged"
        const val KEY_NEXT_PAGE_LINK: String = "next_page_link"
        const val KEY_HIDE: String = "hide"
        const val KEY_SORT_ORDER: String = "sort_order"
        const val KEY_LAST_UPDATE_FAILED: String = "last_update_failed"
        const val KEY_HAS_EMBEDDED_PICTURE: String = "has_embedded_picture"
        const val KEY_LAST_PLAYED_TIME: String = "last_played_time"
        const val KEY_INCLUDE_FILTER: String = "include_filter"
        const val KEY_EXCLUDE_FILTER: String = "exclude_filter"
        const val KEY_MINIMAL_DURATION_FILTER: String = "minimal_duration_filter"
        const val KEY_FEED_PLAYBACK_SPEED: String = "feed_playback_speed"
        const val KEY_FEED_SKIP_INTRO: String = "feed_skip_intro"
        const val KEY_FEED_SKIP_ENDING: String = "feed_skip_ending"
        const val KEY_FEED_TAGS: String = "tags"
        const val KEY_EPISODE_NOTIFICATION: String = "episode_notification"
        const val KEY_NEW_EPISODES_ACTION: String = "new_episodes_action"
        const val KEY_PODCASTINDEX_CHAPTER_URL: String = "podcastindex_chapter_url"

        // Table names
        const val TABLE_NAME_FEEDS: String = "Feeds"
        const val TABLE_NAME_FEED_ITEMS: String = "FeedItems"
        const val TABLE_NAME_FEED_IMAGES: String = "FeedImages"
        const val TABLE_NAME_FEED_MEDIA: String = "FeedMedia"
        const val TABLE_NAME_DOWNLOAD_LOG: String = "DownloadLog"
        const val TABLE_NAME_QUEUE: String = "Queue"
        const val TABLE_NAME_SIMPLECHAPTERS: String = "SimpleChapters"
        const val TABLE_NAME_FAVORITES: String = "Favorites"

        // SQL Statements for creating new tables
        private const val TABLE_PRIMARY_KEY = (KEY_ID
                + " INTEGER PRIMARY KEY AUTOINCREMENT ,")

        private const val CREATE_TABLE_FEEDS = ("CREATE TABLE "
                + TABLE_NAME_FEEDS + " (" + TABLE_PRIMARY_KEY + KEY_TITLE
                + " TEXT," + KEY_CUSTOM_TITLE + " TEXT," + KEY_FILE_URL + " TEXT," + KEY_DOWNLOAD_URL + " TEXT,"
                + KEY_DOWNLOADED + " INTEGER," + KEY_LINK + " TEXT,"
                + KEY_DESCRIPTION + " TEXT," + KEY_PAYMENT_LINK + " TEXT,"
                + KEY_LASTUPDATE + " TEXT," + KEY_LANGUAGE + " TEXT," + KEY_AUTHOR
                + " TEXT," + KEY_IMAGE_URL + " TEXT," + KEY_TYPE + " TEXT,"
                + KEY_FEED_IDENTIFIER + " TEXT," + KEY_AUTO_DOWNLOAD_ENABLED + " INTEGER DEFAULT 1,"
                + KEY_USERNAME + " TEXT,"
                + KEY_PASSWORD + " TEXT,"
                + KEY_INCLUDE_FILTER + " TEXT DEFAULT '',"
                + KEY_EXCLUDE_FILTER + " TEXT DEFAULT '',"
                + KEY_MINIMAL_DURATION_FILTER + " INTEGER DEFAULT -1,"
                + KEY_KEEP_UPDATED + " INTEGER DEFAULT 1,"
                + KEY_IS_PAGED + " INTEGER DEFAULT 0,"
                + KEY_NEXT_PAGE_LINK + " TEXT,"
                + KEY_HIDE + " TEXT,"
                + KEY_SORT_ORDER + " TEXT,"
                + KEY_LAST_UPDATE_FAILED + " INTEGER DEFAULT 0,"
                + KEY_AUTO_DELETE_ACTION + " INTEGER DEFAULT 0,"
                + KEY_FEED_PLAYBACK_SPEED + " REAL DEFAULT " + FeedPreferences.SPEED_USE_GLOBAL + ","
                + KEY_FEED_VOLUME_ADAPTION + " INTEGER DEFAULT 0,"
                + KEY_FEED_TAGS + " TEXT,"
                + KEY_FEED_SKIP_INTRO + " INTEGER DEFAULT 0,"
                + KEY_FEED_SKIP_ENDING + " INTEGER DEFAULT 0,"
                + KEY_EPISODE_NOTIFICATION + " INTEGER DEFAULT 0,"
                + KEY_NEW_EPISODES_ACTION + " INTEGER DEFAULT 0)")

        private const val CREATE_TABLE_FEED_ITEMS = ("CREATE TABLE "
                + TABLE_NAME_FEED_ITEMS + " (" + TABLE_PRIMARY_KEY
                + KEY_TITLE + " TEXT," + KEY_PUBDATE + " INTEGER,"
                + KEY_READ + " INTEGER," + KEY_LINK + " TEXT,"
                + KEY_DESCRIPTION + " TEXT," + KEY_PAYMENT_LINK + " TEXT,"
                + KEY_MEDIA + " INTEGER," + KEY_FEED + " INTEGER,"
                + KEY_HAS_CHAPTERS + " INTEGER," + KEY_ITEM_IDENTIFIER + " TEXT,"
                + KEY_IMAGE_URL + " TEXT,"
                + KEY_AUTO_DOWNLOAD_ENABLED + " INTEGER,"
                + KEY_PODCASTINDEX_CHAPTER_URL + " TEXT)")

        private const val CREATE_TABLE_FEED_MEDIA = ("CREATE TABLE "
                + TABLE_NAME_FEED_MEDIA + " (" + TABLE_PRIMARY_KEY + KEY_DURATION
                + " INTEGER," + KEY_FILE_URL + " TEXT," + KEY_DOWNLOAD_URL
                + " TEXT," + KEY_DOWNLOADED + " INTEGER," + KEY_POSITION
                + " INTEGER," + KEY_SIZE + " INTEGER," + KEY_MIME_TYPE + " TEXT,"
                + KEY_PLAYBACK_COMPLETION_DATE + " INTEGER,"
                + KEY_FEEDITEM + " INTEGER,"
                + KEY_PLAYED_DURATION + " INTEGER,"
                + KEY_HAS_EMBEDDED_PICTURE + " INTEGER,"
                + KEY_LAST_PLAYED_TIME + " INTEGER" + ")")

        private const val CREATE_TABLE_DOWNLOAD_LOG = ("CREATE TABLE "
                + TABLE_NAME_DOWNLOAD_LOG + " (" + TABLE_PRIMARY_KEY + KEY_FEEDFILE
                + " INTEGER," + KEY_FEEDFILETYPE + " INTEGER," + KEY_REASON
                + " INTEGER," + KEY_SUCCESSFUL + " INTEGER," + KEY_COMPLETION_DATE
                + " INTEGER," + KEY_REASON_DETAILED + " TEXT,"
                + KEY_DOWNLOADSTATUS_TITLE + " TEXT)")

        private const val CREATE_TABLE_QUEUE = ("CREATE TABLE "
                + TABLE_NAME_QUEUE + "(" + KEY_ID + " INTEGER PRIMARY KEY,"
                + KEY_FEEDITEM + " INTEGER," + KEY_FEED + " INTEGER)")

        private const val CREATE_TABLE_SIMPLECHAPTERS = ("CREATE TABLE "
                + TABLE_NAME_SIMPLECHAPTERS + " (" + TABLE_PRIMARY_KEY + KEY_TITLE
                + " TEXT," + KEY_START + " INTEGER," + KEY_FEEDITEM + " INTEGER,"
                + KEY_LINK + " TEXT," + KEY_IMAGE_URL + " TEXT)")

        // SQL Statements for creating indexes
        const val CREATE_INDEX_FEEDITEMS_FEED: String = ("CREATE INDEX "
                + TABLE_NAME_FEED_ITEMS + "_" + KEY_FEED + " ON " + TABLE_NAME_FEED_ITEMS + " ("
                + KEY_FEED + ")")

        const val CREATE_INDEX_FEEDITEMS_PUBDATE: String = ("CREATE INDEX "
                + TABLE_NAME_FEED_ITEMS + "_" + KEY_PUBDATE + " ON " + TABLE_NAME_FEED_ITEMS + " ("
                + KEY_PUBDATE + ")")

        const val CREATE_INDEX_FEEDITEMS_READ: String = ("CREATE INDEX "
                + TABLE_NAME_FEED_ITEMS + "_" + KEY_READ + " ON " + TABLE_NAME_FEED_ITEMS + " ("
                + KEY_READ + ")")

        const val CREATE_INDEX_QUEUE_FEEDITEM: String = ("CREATE INDEX "
                + TABLE_NAME_QUEUE + "_" + KEY_FEEDITEM + " ON " + TABLE_NAME_QUEUE + " ("
                + KEY_FEEDITEM + ")")

        const val CREATE_INDEX_FEEDMEDIA_FEEDITEM: String = ("CREATE INDEX "
                + TABLE_NAME_FEED_MEDIA + "_" + KEY_FEEDITEM + " ON " + TABLE_NAME_FEED_MEDIA + " ("
                + KEY_FEEDITEM + ")")

        const val CREATE_INDEX_SIMPLECHAPTERS_FEEDITEM: String = ("CREATE INDEX "
                + TABLE_NAME_SIMPLECHAPTERS + "_" + KEY_FEEDITEM + " ON " + TABLE_NAME_SIMPLECHAPTERS + " ("
                + KEY_FEEDITEM + ")")

        const val CREATE_TABLE_FAVORITES: String = ("CREATE TABLE "
                + TABLE_NAME_FAVORITES + "(" + KEY_ID + " INTEGER PRIMARY KEY,"
                + KEY_FEEDITEM + " INTEGER," + KEY_FEED + " INTEGER)")

        /**
         * All the tables in the database
         */
        private val ALL_TABLES = arrayOf(TABLE_NAME_FEEDS,
            TABLE_NAME_FEED_ITEMS,
            TABLE_NAME_FEED_MEDIA,
            TABLE_NAME_DOWNLOAD_LOG,
            TABLE_NAME_QUEUE,
            TABLE_NAME_SIMPLECHAPTERS,
            TABLE_NAME_FAVORITES
        )

        const val SELECT_KEY_ITEM_ID: String = "item_id"
        const val SELECT_KEY_MEDIA_ID: String = "media_id"
        const val SELECT_KEY_FEED_ID: String = "feed_id"

        private const val KEYS_FEED_ITEM_WITHOUT_DESCRIPTION =
            (TABLE_NAME_FEED_ITEMS + "." + KEY_ID + " AS " + SELECT_KEY_ITEM_ID + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_TITLE + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_PUBDATE + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_READ + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_LINK + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_PAYMENT_LINK + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_MEDIA + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_FEED + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_HAS_CHAPTERS + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_ITEM_IDENTIFIER + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_IMAGE_URL + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_AUTO_DOWNLOAD_ENABLED + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_PODCASTINDEX_CHAPTER_URL)

        private const val KEYS_FEED_MEDIA = (TABLE_NAME_FEED_MEDIA + "." + KEY_ID + " AS " + SELECT_KEY_MEDIA_ID + ", "
                + TABLE_NAME_FEED_MEDIA + "." + KEY_DURATION + ", "
                + TABLE_NAME_FEED_MEDIA + "." + KEY_FILE_URL + ", "
                + TABLE_NAME_FEED_MEDIA + "." + KEY_DOWNLOAD_URL + ", "
                + TABLE_NAME_FEED_MEDIA + "." + KEY_DOWNLOADED + ", "
                + TABLE_NAME_FEED_MEDIA + "." + KEY_POSITION + ", "
                + TABLE_NAME_FEED_MEDIA + "." + KEY_SIZE + ", "
                + TABLE_NAME_FEED_MEDIA + "." + KEY_MIME_TYPE + ", "
                + TABLE_NAME_FEED_MEDIA + "." + KEY_PLAYBACK_COMPLETION_DATE + ", "
                + TABLE_NAME_FEED_MEDIA + "." + KEY_FEEDITEM + ", "
                + TABLE_NAME_FEED_MEDIA + "." + KEY_PLAYED_DURATION + ", "
                + TABLE_NAME_FEED_MEDIA + "." + KEY_HAS_EMBEDDED_PICTURE + ", "
                + TABLE_NAME_FEED_MEDIA + "." + KEY_LAST_PLAYED_TIME)

        private const val KEYS_FEED = (TABLE_NAME_FEEDS + "." + KEY_ID + " AS " + SELECT_KEY_FEED_ID + ", "
                + TABLE_NAME_FEEDS + "." + KEY_TITLE + ", "
                + TABLE_NAME_FEEDS + "." + KEY_CUSTOM_TITLE + ", "
                + TABLE_NAME_FEEDS + "." + KEY_FILE_URL + ", "
                + TABLE_NAME_FEEDS + "." + KEY_DOWNLOAD_URL + ", "
                + TABLE_NAME_FEEDS + "." + KEY_DOWNLOADED + ", "
                + TABLE_NAME_FEEDS + "." + KEY_LINK + ", "
                + TABLE_NAME_FEEDS + "." + KEY_DESCRIPTION + ", "
                + TABLE_NAME_FEEDS + "." + KEY_PAYMENT_LINK + ", "
                + TABLE_NAME_FEEDS + "." + KEY_LASTUPDATE + ", "
                + TABLE_NAME_FEEDS + "." + KEY_LANGUAGE + ", "
                + TABLE_NAME_FEEDS + "." + KEY_AUTHOR + ", "
                + TABLE_NAME_FEEDS + "." + KEY_IMAGE_URL + ", "
                + TABLE_NAME_FEEDS + "." + KEY_TYPE + ", "
                + TABLE_NAME_FEEDS + "." + KEY_FEED_IDENTIFIER + ", "
                + TABLE_NAME_FEEDS + "." + KEY_IS_PAGED + ", "
                + TABLE_NAME_FEEDS + "." + KEY_NEXT_PAGE_LINK + ", "
                + TABLE_NAME_FEEDS + "." + KEY_LAST_UPDATE_FAILED + ", "
                + TABLE_NAME_FEEDS + "." + KEY_AUTO_DOWNLOAD_ENABLED + ", "
                + TABLE_NAME_FEEDS + "." + KEY_KEEP_UPDATED + ", "
                + TABLE_NAME_FEEDS + "." + KEY_USERNAME + ", "
                + TABLE_NAME_FEEDS + "." + KEY_PASSWORD + ", "
                + TABLE_NAME_FEEDS + "." + KEY_HIDE + ", "
                + TABLE_NAME_FEEDS + "." + KEY_SORT_ORDER + ", "
                + TABLE_NAME_FEEDS + "." + KEY_AUTO_DELETE_ACTION + ", "
                + TABLE_NAME_FEEDS + "." + KEY_FEED_VOLUME_ADAPTION + ", "
                + TABLE_NAME_FEEDS + "." + KEY_INCLUDE_FILTER + ", "
                + TABLE_NAME_FEEDS + "." + KEY_EXCLUDE_FILTER + ", "
                + TABLE_NAME_FEEDS + "." + KEY_MINIMAL_DURATION_FILTER + ", "
                + TABLE_NAME_FEEDS + "." + KEY_FEED_PLAYBACK_SPEED + ", "
                + TABLE_NAME_FEEDS + "." + KEY_FEED_TAGS + ", "
                + TABLE_NAME_FEEDS + "." + KEY_FEED_SKIP_INTRO + ", "
                + TABLE_NAME_FEEDS + "." + KEY_FEED_SKIP_ENDING + ", "
                + TABLE_NAME_FEEDS + "." + KEY_EPISODE_NOTIFICATION + ", "
                + TABLE_NAME_FEEDS + "." + KEY_NEW_EPISODES_ACTION)

        private const val JOIN_FEED_ITEM_AND_MEDIA = (" LEFT JOIN " + TABLE_NAME_FEED_MEDIA
                + " ON " + TABLE_NAME_FEED_ITEMS + "." + KEY_ID + "=" + TABLE_NAME_FEED_MEDIA + "." + KEY_FEEDITEM + " ")

        private const val SELECT_FEED_ITEMS_AND_MEDIA_WITH_DESCRIPTION =
            ("SELECT " + KEYS_FEED_ITEM_WITHOUT_DESCRIPTION + ", " + KEYS_FEED_MEDIA + ", "
                    + TABLE_NAME_FEED_ITEMS + "." + KEY_DESCRIPTION
                    + " FROM " + TABLE_NAME_FEED_ITEMS
                    + JOIN_FEED_ITEM_AND_MEDIA)
        private const val SELECT_FEED_ITEMS_AND_MEDIA =
            ("SELECT " + KEYS_FEED_ITEM_WITHOUT_DESCRIPTION + ", " + KEYS_FEED_MEDIA
                    + " FROM " + TABLE_NAME_FEED_ITEMS
                    + JOIN_FEED_ITEM_AND_MEDIA)

        private lateinit var context: Context
        private var instance: PodDBAdapter? = null

        @JvmStatic
        fun init(context: Context) {
            Companion.context = context.applicationContext
        }

        @JvmStatic
        @Synchronized
        fun getInstance(): PodDBAdapter {
            if (instance == null) {
                instance = PodDBAdapter()
            }
            return instance!!
        }

        /**
         *
         * Resets all database connections to ensure new database connections for
         * the next test case. Call method only for unit tests.
         *
         *
         * That's a workaround for a Robolectric issue in ShadowSQLiteConnection
         * that leads to an error <tt>IllegalStateException: Illegal connection
         * pointer</tt> if several threads try to use the same database connection.
         * For more information see
         * [robolectric/robolectric#1890](https://github.com/robolectric/robolectric/issues/1890).
         */
        @JvmStatic
        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
        fun tearDownTests() {
            getInstance().dbHelper.close()
            instance = null
        }

        @JvmStatic
        fun deleteDatabase(): Boolean {
            val adapter = getInstance()
            adapter.open()
            try {
                for (tableName in ALL_TABLES) {
                    adapter.db.delete(tableName, "1", null)
                }
                return true
            } finally {
                adapter.close()
            }
        }
    }
}
