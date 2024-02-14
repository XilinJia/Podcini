package ac.mdiq.podvinci.storage.database.mapper

import android.database.Cursor
import ac.mdiq.podvinci.model.feed.FeedItem
import ac.mdiq.podvinci.storage.database.PodDBAdapter
import java.util.*

/**
 * Converts a [Cursor] to a [FeedItem] object.
 */
object FeedItemCursorMapper {
    /**
     * Create a [FeedItem] instance from a database row (cursor).
     */
    @JvmStatic
    fun convert(cursor: Cursor): FeedItem {
        val indexId = cursor.getColumnIndexOrThrow(PodDBAdapter.SELECT_KEY_ITEM_ID)
        val indexTitle = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_TITLE)
        val indexLink = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_LINK)
        val indexPubDate = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_PUBDATE)
        val indexPaymentLink = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_PAYMENT_LINK)
        val indexFeedId = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_FEED)
        val indexHasChapters = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_HAS_CHAPTERS)
        val indexRead = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_READ)
        val indexItemIdentifier = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_ITEM_IDENTIFIER)
        val indexAutoDownload = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_AUTO_DOWNLOAD_ENABLED)
        val indexImageUrl = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_IMAGE_URL)
        val indexPodcastIndexChapterUrl = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_PODCASTINDEX_CHAPTER_URL)

        val id = cursor.getInt(indexId).toLong()
        val title = cursor.getString(indexTitle)
        val link = cursor.getString(indexLink)
        val pubDate = Date(cursor.getLong(indexPubDate))
        val paymentLink = cursor.getString(indexPaymentLink)
        val feedId = cursor.getLong(indexFeedId)
        val hasChapters = cursor.getInt(indexHasChapters) > 0
        val state = cursor.getInt(indexRead)
        val itemIdentifier = cursor.getString(indexItemIdentifier)
        val autoDownloadEnabled = cursor.getLong(indexAutoDownload) > 0
        val imageUrl = cursor.getString(indexImageUrl)
        val podcastIndexChapterUrl = cursor.getString(indexPodcastIndexChapterUrl)

        return FeedItem(id, title, link, pubDate, paymentLink, feedId,
            hasChapters, imageUrl, state, itemIdentifier, autoDownloadEnabled, podcastIndexChapterUrl)
    }
}
