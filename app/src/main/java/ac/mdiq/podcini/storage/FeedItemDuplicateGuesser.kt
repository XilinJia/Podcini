package ac.mdiq.podcini.storage

import android.text.TextUtils
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import java.text.DateFormat
import java.util.*
import kotlin.math.abs

/**
 * Publishers sometimes mess up their feed by adding episodes twice or by changing the ID of existing episodes.
 * This class tries to guess if publishers actually meant another episode,
 * even if their feed explicitly says that the episodes are different.
 */
object FeedItemDuplicateGuesser {
    @JvmStatic
    fun seemDuplicates(item1: FeedItem, item2: FeedItem): Boolean {
        if (sameAndNotEmpty(item1.itemIdentifier, item2.itemIdentifier)) {
            return true
        }
        val media1 = item1.media
        val media2 = item2.media
        if (media1 == null || media2 == null) {
            return false
        }
        if (sameAndNotEmpty(media1.getStreamUrl(), media2.getStreamUrl())) {
            return true
        }
        return (titlesLookSimilar(item1, item2)
                && datesLookSimilar(item1, item2)
                && durationsLookSimilar(media1, media2)
                && mimeTypeLooksSimilar(media1, media2))
    }

    private fun sameAndNotEmpty(string1: String?, string2: String?): Boolean {
        if (string1.isNullOrEmpty() || string2.isNullOrEmpty()) {
            return false
        }
        return string1 == string2
    }

    private fun datesLookSimilar(item1: FeedItem, item2: FeedItem): Boolean {
        if (item1.getPubDate() == null || item2.getPubDate() == null) {
            return false
        }
        val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.US) // MM/DD/YY
        val dateOriginal = dateFormat.format(item2.getPubDate()!!)
        val dateNew = dateFormat.format(item1.getPubDate()!!)
        return TextUtils.equals(dateOriginal, dateNew) // Same date; time is ignored.
    }

    private fun durationsLookSimilar(media1: FeedMedia, media2: FeedMedia): Boolean {
        return abs((media1.getDuration() - media2.getDuration()).toDouble()) < 10 * 60L * 1000L
    }

    private fun mimeTypeLooksSimilar(media1: FeedMedia, media2: FeedMedia): Boolean {
        var mimeType1 = media1.mime_type
        var mimeType2 = media2.mime_type
        if (mimeType1 == null || mimeType2 == null) {
            return true
        }
        if (mimeType1.contains("/") && mimeType2.contains("/")) {
            mimeType1 = mimeType1.substring(0, mimeType1.indexOf("/"))
            mimeType2 = mimeType2.substring(0, mimeType2.indexOf("/"))
        }
        return TextUtils.equals(mimeType1, mimeType2)
    }

    private fun titlesLookSimilar(item1: FeedItem, item2: FeedItem): Boolean {
        return sameAndNotEmpty(canonicalizeTitle(item1.title), canonicalizeTitle(item2.title))
    }

    private fun canonicalizeTitle(title: String?): String {
        if (title == null) {
            return ""
        }
        return title
            .trim { it <= ' ' }
            .replace('“', '"')
            .replace('”', '"')
            .replace('„', '"')
            .replace('—', '-')
    }
}
