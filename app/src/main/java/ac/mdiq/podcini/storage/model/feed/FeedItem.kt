package ac.mdiq.podcini.storage.model.feed

import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle
import java.io.Serializable
import java.util.*

/**
 * Item (episode) within a feed.
 *
 * @author daniel
 */
class FeedItem : FeedComponent, Serializable {
    /**
     * The id/guid that can be found in the rss/atom feed. Might not be set.
     */
    @JvmField
    var itemIdentifier: String? = null
    @JvmField
    var title: String? = null

    /**
     * The description of a feeditem.
     */
    var description: String? = null
        private set

    @JvmField
    var link: String? = null

    @get:JvmName("getPubDateProperty")
    @set:JvmName("setPubDateProperty")
    var pubDate: Date? = null

    @set:JvmName("setMediaProperty")
    var media: FeedMedia? = null

    @JvmField
    @Transient
    var feed: Feed? = null

    @JvmField
    var feedId: Long = 0

    @JvmField
    var podcastIndexChapterUrl: String? = null

    var playState: Int
        private set
    @JvmField
    var paymentLink: String? = null

    /**
     * Is true if the database contains any chapters that belong to this item. This attribute is only
     * written once by DBReader on initialization.
     * The FeedItem might still have a non-null chapters value. In this case, the list of chapters
     * has not been saved in the database yet.
     */
    private val hasChapters: Boolean

    /**
     * The list of chapters of this item. This might be null even if there are chapters of this item
     * in the database. The 'hasChapters' attribute should be used to check if this item has any chapters.
     */
    @JvmField
    @Transient
    var chapters: MutableList<Chapter>? = null

    /**
     * Returns the image of this item, as specified in the feed.
     * To load the image that can be displayed to the user, use [.getImageLocation],
     * which also considers embedded pictures or the feed picture if no other picture is present.
     */
    @JvmField
    var imageUrl: String? = null

    var isAutoDownloadEnabled: Boolean = true
        private set

    /**
     * Any tags assigned to this item
     */
    private val tags: MutableSet<String> = HashSet()

    constructor() {
        this.playState = UNPLAYED
        this.hasChapters = false
    }

    /**
     * This constructor is used by DBReader.
     */
    constructor(id: Long, title: String?, link: String?, pubDate: Date?, paymentLink: String?, feedId: Long,
                hasChapters: Boolean, imageUrl: String?, state: Int,
                itemIdentifier: String?, autoDownloadEnabled: Boolean, podcastIndexChapterUrl: String?
    ) {
        this.id = id
        this.title = title
        this.link = link
        this.pubDate = pubDate
        this.paymentLink = paymentLink
        this.feedId = feedId
        this.hasChapters = hasChapters
        this.imageUrl = imageUrl
        this.playState = state
        this.itemIdentifier = itemIdentifier
        this.isAutoDownloadEnabled = autoDownloadEnabled
        this.podcastIndexChapterUrl = podcastIndexChapterUrl
    }

    /**
     * This constructor should be used for creating test objects.
     */
    constructor(id: Long,
                title: String?,
                itemIdentifier: String?,
                link: String?,
                pubDate: Date?,
                state: Int,
                feed: Feed?
    ) {
        this.id = id
        this.title = title
        this.itemIdentifier = itemIdentifier
        this.link = link
        this.pubDate = if (pubDate != null) pubDate.clone() as Date else null
        this.playState = state
        this.feed = feed
        this.hasChapters = false
    }

    /**
     * This constructor should be used for creating test objects involving chapter marks.
     */
    constructor(id: Long,
                title: String?,
                itemIdentifier: String?,
                link: String?,
                pubDate: Date?,
                state: Int,
                feed: Feed?,
                hasChapters: Boolean
    ) {
        this.id = id
        this.title = title
        this.itemIdentifier = itemIdentifier
        this.link = link
        this.pubDate = if (pubDate != null) pubDate.clone() as Date else null
        this.playState = state
        this.feed = feed
        this.hasChapters = hasChapters
    }

    fun updateFromOther(other: FeedItem) {
        super.updateFromOther(other)
        if (other.imageUrl != null) {
            this.imageUrl = other.imageUrl
        }
        if (other.title != null) {
            title = other.title
        }
        if (other.description != null) {
            description = other.description
        }
        if (other.link != null) {
            link = other.link
        }
        if (other.pubDate != null && other.pubDate != pubDate) {
            pubDate = other.pubDate
        }
        if (other.media != null) {
            if (media == null) {
                setMedia(other.media)
                // reset to new if feed item did link to a file before
                setNew()
            } else if (media!!.compareWithOther(other.media)) {
                media!!.updateFromOther(other.media)
            }
        }
        if (other.paymentLink != null) {
            paymentLink = other.paymentLink
        }
        if (other.chapters != null) {
            if (!hasChapters) {
                chapters = other.chapters
            }
        }
        if (other.podcastIndexChapterUrl != null) {
            podcastIndexChapterUrl = other.podcastIndexChapterUrl
        }
    }

    val identifyingValue: String?
        /**
         * Returns the value that uniquely identifies this FeedItem. If the
         * itemIdentifier attribute is not null, it will be returned. Else it will
         * try to return the title. If the title is not given, it will use the link
         * of the entry.
         */
        get() = if (itemIdentifier != null && itemIdentifier!!.isNotEmpty()) {
            itemIdentifier
        } else if (title != null && title!!.isNotEmpty()) {
            title
        } else if (hasMedia() && media!!.download_url != null) {
            media!!.download_url
        } else {
            link
        }

    @JvmName("getPubDateFunction")
    fun getPubDate(): Date? {
        return if (pubDate != null) {
            pubDate!!.clone() as Date
        } else {
            null
        }
    }

    @JvmName("setPubDateFunction")
    fun setPubDate(pubDate: Date?) {
        if (pubDate != null) {
            this.pubDate = pubDate.clone() as Date
        } else {
            this.pubDate = null
        }
    }

    /**
     * Sets the media object of this FeedItem. If the given
     * FeedMedia object is not null, it's 'item'-attribute value
     * will also be set to this item.
     */
    @JvmName("setMediaFunction")
    fun setMedia(media: FeedMedia?) {
        this.media = media
        if (media != null && media.getItem() !== this) {
            media.setItem(this)
        }
    }

    val isNew: Boolean
        get() = playState == NEW

    fun setNew() {
        playState = NEW
    }

    fun isPlayed(): Boolean {
        return playState == PLAYED
    }

    fun setPlayed(played: Boolean) {
        playState = if (played) {
            PLAYED
        } else {
            UNPLAYED
        }
    }

    val isInProgress: Boolean
        get() = (media != null && media!!.isInProgress)

    /**
     * Updates this item's description property if the given argument is longer than the already stored description
     * @param newDescription The new item description, content:encoded, itunes:description, etc.
     */
    fun setDescriptionIfLonger(newDescription: String?) {
        if (newDescription == null) {
            return
        }
        if (this.description == null) {
            this.description = newDescription
        } else if (description!!.length < newDescription.length) {
            this.description = newDescription
        }
    }

    fun hasMedia(): Boolean {
        return media != null
    }

    val imageLocation: String?
        get() = if (imageUrl != null) {
            imageUrl
        } else if (media != null && media!!.hasEmbeddedPicture()) {
            FeedMedia.FILENAME_PREFIX_EMBEDDED_COVER + media!!.getLocalMediaUrl()
        } else if (feed != null) {
            feed!!.imageUrl
        } else {
            null
        }

    enum class State {
        UNREAD, IN_PROGRESS, READ, PLAYING
    }

    override fun getHumanReadableIdentifier(): String? {
        return title
    }

    fun hasChapters(): Boolean {
        return chapters?.isNotEmpty() ?: hasChapters
    }

    fun disableAutoDownload() {
        this.isAutoDownloadEnabled = false
    }

    val isDownloaded: Boolean
        get() = media != null && media!!.isDownloaded()

    /**
     * @return true if the item has this tag
     */
    fun isTagged(tag: String): Boolean {
        return tags.contains(tag)
    }

    /**
     * @param tag adds this tag to the item. NOTE: does NOT persist to the database
     */
    fun addTag(tag: String) {
        tags.add(tag)
    }

    /**
     * @param tag the to remove
     */
    fun removeTag(tag: String) {
        tags.remove(tag)
    }

    override fun toString(): String {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE)
    }

    companion object {
        /** tag that indicates this item is in the queue  */
        const val TAG_QUEUE: String = "Queue"

        /** tag that indicates this item is in favorites  */
        const val TAG_FAVORITE: String = "Favorite"

        const val NEW: Int = -1
        const val UNPLAYED: Int = 0
        const val PLAYED: Int = 1
    }
}
