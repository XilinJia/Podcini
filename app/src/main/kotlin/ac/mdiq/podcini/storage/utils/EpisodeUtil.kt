package ac.mdiq.podcini.storage.utils

import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Playable
import org.apache.commons.lang3.StringUtils

object EpisodeUtil {
    private val TAG: String = EpisodeUtil::class.simpleName ?: "Anonymous"

    @JvmStatic
    fun indexOfItemWithId(episodes: List<Episode?>, id: Long): Int {
        for (i in episodes.indices) {
            val episode = episodes[i]
            if (episode?.id == id) return i
        }
        return -1
    }

    @JvmStatic
    fun episodeListContains(episodes: List<Episode?>, itemId: Long): Boolean {
        return indexOfItemWithId(episodes, itemId) >= 0
    }

    @JvmStatic
    fun indexOfItemWithDownloadUrl(items: List<Episode?>, downloadUrl: String): Int {
        for (i in items.indices) {
            val item = items[i]
            if (item?.media?.downloadUrl == downloadUrl) return i
        }
        return -1
    }

//    only used in tests
    @JvmStatic
    fun getIdList(items: List<Episode>): List<Long> {
        val result: MutableList<Long> = ArrayList()
        for (item in items) {
            result.add(item.id)
        }
        return result
    }

    @JvmStatic
    fun hasAlmostEnded(media: Playable): Boolean {
        val smartMarkAsPlayedSecs = UserPreferences.smartMarkAsPlayedSecs
        return media.getDuration() > 0 && media.getPosition() >= media.getDuration() - smartMarkAsPlayedSecs * 1000
    }
}
