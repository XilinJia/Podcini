package ac.mdiq.podcini.model.feed

import android.text.TextUtils
import java.io.Serializable

class FeedItemFilter(vararg properties: String) : Serializable {
    private val properties: Array<String> = arrayOf(*properties)

    @JvmField
    val showPlayed: Boolean
    @JvmField
    val showUnplayed: Boolean
    @JvmField
    val showPaused: Boolean
    @JvmField
    val showNotPaused: Boolean
    @JvmField
    val showNew: Boolean
    @JvmField
    val showQueued: Boolean
    @JvmField
    val showNotQueued: Boolean
    @JvmField
    val showDownloaded: Boolean
    @JvmField
    val showNotDownloaded: Boolean
    @JvmField
    val showHasMedia: Boolean
    @JvmField
    val showNoMedia: Boolean
    @JvmField
    val showIsFavorite: Boolean
    @JvmField
    val showNotFavorite: Boolean

    constructor(properties: String) : this(*TextUtils.split(properties, ","))

    init {

        // see R.arrays.feed_filter_values
        showUnplayed = hasProperty(UNPLAYED)
        showPaused = hasProperty(PAUSED)
        showNotPaused = hasProperty(NOT_PAUSED)
        showPlayed = hasProperty(PLAYED)
        showQueued = hasProperty(QUEUED)
        showNotQueued = hasProperty(NOT_QUEUED)
        showDownloaded = hasProperty(DOWNLOADED)
        showNotDownloaded = hasProperty(NOT_DOWNLOADED)
        showHasMedia = hasProperty(HAS_MEDIA)
        showNoMedia = hasProperty(NO_MEDIA)
        showIsFavorite = hasProperty(IS_FAVORITE)
        showNotFavorite = hasProperty(NOT_FAVORITE)
        showNew = hasProperty(NEW)
    }

    private fun hasProperty(property: String): Boolean {
        return listOf(*properties).contains(property)
    }

    val values: Array<String>
        get() = properties.clone()

    val valuesList: List<String>
        get() = listOf(*properties)

    fun matches(item: FeedItem): Boolean {
        if (showNew && !item.isNew) {
            return false
        } else if (showPlayed && !item.isPlayed()) {
            return false
        } else if (showUnplayed && item.isPlayed()) {
            return false
        } else if (showPaused && !item.isInProgress) {
            return false
        } else if (showNotPaused && item.isInProgress) {
            return false
        } else if (showNew && !item.isNew) {
            return false
        } else if (showQueued && !item.isTagged(FeedItem.TAG_QUEUE)) {
            return false
        } else if (showNotQueued && item.isTagged(FeedItem.TAG_QUEUE)) {
            return false
        } else if (showDownloaded && !item.isDownloaded) {
            return false
        } else if (showNotDownloaded && item.isDownloaded) {
            return false
        } else if (showHasMedia && !item.hasMedia()) {
            return false
        } else if (showNoMedia && item.hasMedia()) {
            return false
        } else if (showIsFavorite && !item.isTagged(FeedItem.TAG_FAVORITE)) {
            return false
        } else if (showNotFavorite && item.isTagged(FeedItem.TAG_FAVORITE)) {
            return false
        }
        return true
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
        fun unfiltered(): FeedItemFilter {
            return FeedItemFilter("")
        }
    }
}
