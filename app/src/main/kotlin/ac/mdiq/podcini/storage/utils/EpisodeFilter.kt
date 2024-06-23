package ac.mdiq.podcini.storage.utils

import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.storage.model.Episode
import java.io.Serializable

class EpisodeFilter(vararg properties: String) : Serializable {

    private val properties: Array<String> = arrayOf(*properties.filter { it.isNotEmpty() }.map {it.trim()}.toTypedArray())

    @JvmField
    val showPlayed: Boolean = hasProperty(PLAYED)
    @JvmField
    val showUnplayed: Boolean = hasProperty(UNPLAYED)
    @JvmField
    val showPaused: Boolean = hasProperty(PAUSED)
    @JvmField
    val showNotPaused: Boolean = hasProperty(NOT_PAUSED)
    @JvmField
    val showNew: Boolean = hasProperty(NEW)
    @JvmField
    val showQueued: Boolean = hasProperty(QUEUED)
    @JvmField
    val showNotQueued: Boolean = hasProperty(NOT_QUEUED)
    @JvmField
    val showDownloaded: Boolean = hasProperty(DOWNLOADED)
    @JvmField
    val showNotDownloaded: Boolean = hasProperty(NOT_DOWNLOADED)
    @JvmField
    val showHasMedia: Boolean = hasProperty(HAS_MEDIA)
    @JvmField
    val showNoMedia: Boolean = hasProperty(NO_MEDIA)
    @JvmField
    val showIsFavorite: Boolean = hasProperty(IS_FAVORITE)
    @JvmField
    val showNotFavorite: Boolean = hasProperty(NOT_FAVORITE)

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
            showHasMedia && item.media == null -> return false
            showNoMedia && item.media != null -> return false
            showIsFavorite && !item.isFavorite -> return false
            showNotFavorite && item.isFavorite -> return false
            showQueued && curQueue.isInQueue(item) -> return false
            showNotQueued && !curQueue.isInQueue(item) -> return false
            else -> return true
        }
    }

    companion object {
        const val PLAYED: String = "played"
        const val UNPLAYED: String = "unplayed"
        const val NEW: String = "new"
        const val PAUSED: String = "paused"
        const val NOT_PAUSED: String = "not_paused"
        const val IS_FAVORITE: String = "is_favorite"
        const val NOT_FAVORITE: String = "not_favorite"
        const val HAS_MEDIA: String = "has_media"
        const val NO_MEDIA: String = "no_media"
        const val QUEUED: String = "queued"
        const val NOT_QUEUED: String = "not_queued"
        const val DOWNLOADED: String = "downloaded"
        const val NOT_DOWNLOADED: String = "not_downloaded"

        @JvmStatic
        fun unfiltered(): EpisodeFilter {
            return EpisodeFilter("")
        }
    }
}
