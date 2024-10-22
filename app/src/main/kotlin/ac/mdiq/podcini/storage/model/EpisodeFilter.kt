package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.storage.database.Queues.inAnyQueue
import java.io.Serializable

class EpisodeFilter(vararg properties: String) : Serializable {
    private val properties: Array<String> = arrayOf(*properties.filter { it.isNotEmpty() }.map {it.trim()}.toTypedArray())

    val showPlayed: Boolean = hasProperty(States.played.name)
    val showUnplayed: Boolean = hasProperty(States.unplayed.name)
    val showPaused: Boolean = hasProperty(States.paused.name)
    val showNotPaused: Boolean = hasProperty(States.not_paused.name)
    val showNew: Boolean = hasProperty(States.new.name)
    val showQueued: Boolean = hasProperty(States.queued.name)
    val showNotQueued: Boolean = hasProperty(States.not_queued.name)
    val showDownloaded: Boolean = hasProperty(States.downloaded.name)
    val showNotDownloaded: Boolean = hasProperty(States.not_downloaded.name)
    val showAutoDownloadable: Boolean = hasProperty(States.auto_downloadable.name)
    val showNotAutoDownloadable: Boolean = hasProperty(States.not_auto_downloadable.name)
    val showHasMedia: Boolean = hasProperty(States.has_media.name)
    val showNoMedia: Boolean = hasProperty(States.no_media.name)
    val showIsFavorite: Boolean = hasProperty(States.is_favorite.name)
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
            showPlayed && item.playState < PlayState.PLAYED.code -> return false
            showUnplayed && item.playState >= PlayState.PLAYED.code -> return false
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
            showQueued && !inAnyQueue(item) -> return false
            showNotQueued && inAnyQueue(item) -> return false
            else -> return true
        }
    }

//    filter on queues does not have a query string so it's not applied on query results, need to filter separately
    fun matchesForQueues(item: Episode): Boolean {
    return when {
        showQueued && !inAnyQueue(item) -> false
        showNotQueued && inAnyQueue(item) -> false
        else -> true
    }
    }

    fun queryString(): String {
        val statements: MutableList<String> = ArrayList()
        when {
            showPlayed -> statements.add("playState >= ${PlayState.PLAYED.code}")
            showUnplayed -> statements.add(" playState < ${PlayState.PLAYED.code}> ") // Match "New" items (read = -1) as well
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

    @Suppress("EnumEntryName")
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
