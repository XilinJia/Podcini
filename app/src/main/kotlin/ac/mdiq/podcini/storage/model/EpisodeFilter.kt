package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import java.io.Serializable

class EpisodeFilter(vararg properties: String) : Serializable {

    private val properties: Array<String> = arrayOf(*properties.filter { it.isNotEmpty() }.map {it.trim()}.toTypedArray())

    @JvmField
    val showPlayed: Boolean = hasProperty(States.played.name)
    @JvmField
    val showUnplayed: Boolean = hasProperty(States.unplayed.name)
    @JvmField
    val showPaused: Boolean = hasProperty(States.paused.name)
    @JvmField
    val showNotPaused: Boolean = hasProperty(States.not_paused.name)
    @JvmField
    val showNew: Boolean = hasProperty(States.new.name)
    @JvmField
    val showQueued: Boolean = hasProperty(States.queued.name)
    @JvmField
    val showNotQueued: Boolean = hasProperty(States.not_queued.name)
    @JvmField
    val showDownloaded: Boolean = hasProperty(States.downloaded.name)
    @JvmField
    val showNotDownloaded: Boolean = hasProperty(States.not_downloaded.name)
    @JvmField
    val showAutoDownloadable: Boolean = hasProperty(States.auto_downloadable.name)
    @JvmField
    val showNotAutoDownloadable: Boolean = hasProperty(States.not_auto_downloadable.name)
    @JvmField
    val showHasMedia: Boolean = hasProperty(States.has_media.name)
    @JvmField
    val showNoMedia: Boolean = hasProperty(States.no_media.name)
    @JvmField
    val showIsFavorite: Boolean = hasProperty(States.is_favorite.name)
    @JvmField
    val showNotFavorite: Boolean = hasProperty(States.not_favorite.name)

    constructor(properties: String) : this(*(properties.split(",").toTypedArray()))

    private fun hasProperty(property: String): Boolean {
        return listOf(*properties).contains(property)
    }

    val values: Array<String>
        get() = properties.clone()

    val valuesList: List<String>
        get() = listOf(*properties)

    fun matches(item: Episode): Boolean {
        when {
            showNew && !item.isNew -> return false
            showPlayed && !item.isPlayed() -> return false
            showUnplayed && item.isPlayed() -> return false
            showPaused && !item.isInProgress -> return false
            showNotPaused && item.isInProgress -> return false
            showDownloaded && !item.isDownloaded -> return false
            showNotDownloaded && item.isDownloaded -> return false
            showAutoDownloadable && !item.isAutoDownloadEnabled -> return false
            showNotAutoDownloadable && item.isAutoDownloadEnabled -> return false
            showHasMedia && item.media == null -> return false
            showNoMedia && item.media != null -> return false
            showIsFavorite && !item.isFavorite -> return false
            showNotFavorite && item.isFavorite -> return false
            showQueued && curQueue.isInQueue(item) -> return false
            showNotQueued && !curQueue.isInQueue(item) -> return false
            else -> return true
        }
    }

    fun queryString(): String {
        val statements: MutableList<String> = ArrayList()
        when {
            showPlayed -> statements.add("playState == 1 ")
            showUnplayed -> statements.add(" playState != 1 ") // Match "New" items (read = -1) as well
            showNew -> statements.add("playState == -1 ")
        }
        when {
            showPaused -> statements.add(" media.position > 0 ")
            showNotPaused -> statements.add(" media.position == 0 ")
        }
//        when {
//            showQueued -> statements.add("$keyItemId IN (SELECT $keyFeedItem FROM $tableQueue) ")
//            showNotQueued -> statements.add("$keyItemId NOT IN (SELECT $keyFeedItem FROM $tableQueue) ")
//        }
        when {
            showDownloaded -> statements.add("media.downloaded == true ")
            showNotDownloaded -> statements.add("media.downloaded == false ")
        }
        when {
            showAutoDownloadable -> statements.add("isAutoDownloadEnabled == true ")
            showNotAutoDownloadable -> statements.add("isAutoDownloadEnabled == false ")
        }
        when {
            showHasMedia -> statements.add("media != nil ")
            showNoMedia -> statements.add("media == nil ")
        }
        when {
            showIsFavorite -> statements.add("isFavorite == true ")
            showNotFavorite -> statements.add("isFavorite == false ")
        }

        if (statements.isEmpty()) return "id > 0"

        val query = StringBuilder(" (" + statements[0])
        for (r in statements.subList(1, statements.size)) {
            query.append(" AND ")
            query.append(r)
        }
        query.append(") ")

        return query.toString()
    }

    enum class States {
        played,
        unplayed,
        new,
        paused,
        not_paused,
        is_favorite,
        not_favorite,
        has_media,
        no_media,
        queued,
        not_queued,
        downloaded,
        not_downloaded,
        auto_downloadable,
        not_auto_downloadable
    }
    companion object {

        @JvmStatic
        fun unfiltered(): EpisodeFilter {
            return EpisodeFilter("")
        }
    }
}
