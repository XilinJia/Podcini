package ac.mdiq.podcini.util

import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.model.playback.Playable
import org.apache.commons.lang3.StringUtils

object FeedItemUtil {
    @JvmStatic
    fun indexOfItemWithId(items: List<FeedItem?>, id: Long): Int {
        for (i in items.indices) {
            val item = items[i]
            if (item?.id == id) return i
        }
        return -1
    }

    @JvmStatic
    fun indexOfItemWithDownloadUrl(items: List<FeedItem?>, downloadUrl: String): Int {
        for (i in items.indices) {
            val item = items[i]
            if (item?.media?.download_url == downloadUrl) return i
        }
        return -1
    }

    @JvmStatic
    fun getIds(items: List<FeedItem>?): LongArray {
        if (items.isNullOrEmpty()) return LongArray(0)

        val result = LongArray(items.size)
        for (i in items.indices) {
            result[i] = items[i].id
        }
        return result
    }

    @JvmStatic
    fun getIdList(items: List<FeedItem>): List<Long> {
        val result: MutableList<Long> = ArrayList()
        for (item in items) {
            result.add(item.id)
        }
        return result
    }

    /**
     * Get the link for the feed item for the purpose of Share. It fallbacks to
     * use the feed's link if the named feed item has no link.
     */
    @JvmStatic
    fun getLinkWithFallback(item: FeedItem?): String? {
        when {
            item == null -> return null
            StringUtils.isNotBlank(item.link) -> return item.link
            item.feed != null && !item.feed!!.link.isNullOrEmpty() -> return item.feed!!.link
            else -> return null
        }
    }

    @JvmStatic
    fun hasAlmostEnded(media: Playable): Boolean {
        val smartMarkAsPlayedSecs = UserPreferences.smartMarkAsPlayedSecs
        return media.getDuration() > 0 && media.getPosition() >= media.getDuration() - smartMarkAsPlayedSecs * 1000
    }
}
