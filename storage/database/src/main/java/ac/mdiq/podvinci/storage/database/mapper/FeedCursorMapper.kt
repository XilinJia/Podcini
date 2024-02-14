package ac.mdiq.podvinci.storage.database.mapper

import android.database.Cursor
import ac.mdiq.podvinci.model.feed.Feed
import ac.mdiq.podvinci.model.feed.SortOrder.Companion.fromCodeString
import ac.mdiq.podvinci.storage.database.PodDBAdapter

/**
 * Converts a [Cursor] to a [Feed] object.
 */
object FeedCursorMapper {
    /**
     * Create a [Feed] instance from a database row (cursor).
     */
    @JvmStatic
    fun convert(cursor: Cursor): Feed {
        val indexId = cursor.getColumnIndexOrThrow(PodDBAdapter.SELECT_KEY_FEED_ID)
        val indexLastUpdate = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_LASTUPDATE)
        val indexTitle = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_TITLE)
        val indexCustomTitle = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_CUSTOM_TITLE)
        val indexLink = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_LINK)
        val indexDescription = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_DESCRIPTION)
        val indexPaymentLink = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_PAYMENT_LINK)
        val indexAuthor = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_AUTHOR)
        val indexLanguage = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_LANGUAGE)
        val indexType = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_TYPE)
        val indexFeedIdentifier = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_FEED_IDENTIFIER)
        val indexFileUrl = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_FILE_URL)
        val indexDownloadUrl = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_DOWNLOAD_URL)
        val indexDownloaded = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_DOWNLOADED)
        val indexIsPaged = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_IS_PAGED)
        val indexNextPageLink = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_NEXT_PAGE_LINK)
        val indexHide = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_HIDE)
        val indexSortOrder = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_SORT_ORDER)
        val indexLastUpdateFailed = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_LAST_UPDATE_FAILED)
        val indexImageUrl = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_IMAGE_URL)

        val feed = Feed(
            cursor.getLong(indexId),
            cursor.getString(indexLastUpdate),
            cursor.getString(indexTitle),
            cursor.getString(indexCustomTitle),
            cursor.getString(indexLink),
            cursor.getString(indexDescription),
            cursor.getString(indexPaymentLink),
            cursor.getString(indexAuthor),
            cursor.getString(indexLanguage),
            cursor.getString(indexType),
            cursor.getString(indexFeedIdentifier),
            cursor.getString(indexImageUrl),
            cursor.getString(indexFileUrl),
            cursor.getString(indexDownloadUrl),
            cursor.getInt(indexDownloaded) > 0,
            cursor.getInt(indexIsPaged) > 0,
            cursor.getString(indexNextPageLink),
            cursor.getString(indexHide),
            fromCodeString(cursor.getString(indexSortOrder)),
            cursor.getInt(indexLastUpdateFailed) > 0
        )

        val preferences = FeedPreferencesCursorMapper.convert(cursor)
        feed.preferences = preferences
        return feed
    }
}
