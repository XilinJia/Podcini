package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.util.Logd
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.annotations.FullText
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle

import java.util.*

/**
 * Episode within a feed.
 *
 */
class Episode : RealmObject {

    @PrimaryKey
    var id: Long = 0L   // increments from Date().time * 100 at time of creation

    /**
     * The id/guid that can be found in the rss/atom feed. Might not be set.
     */
    @Index
    var identifier: String? = null

    @FullText
    var title: String? = null

    @FullText
    var description: String? = null

    @FullText
    var transcript: String? = null

    var link: String? = null

    @get:JvmName("getPubDateProperty")
    @set:JvmName("setPubDateProperty")
    var pubDate: Long = 0

    @set:JvmName("setMediaProperty")
    var media: EpisodeMedia? = null

//    val feedlink: RealmResults<Feed> by backlinks(Feed::episodes)

    @Ignore
    var feed: Feed? = null
        get() {
//            Logd(TAG, "feed.get() ${field == null} ${title}")
            if (field == null && feedId != null) field = getFeed(feedId!!)
            return field
        }

    var feedId: Long? = null

    var podcastIndexChapterUrl: String? = null

    var playState: Int

    var paymentLink: String? = null

    /**
     * Is true if the database contains any chapters that belong to this item. This attribute is only
     * written once by DBReader on initialization.
     * The FeedItem might still have a non-null chapters value. In this case, the list of chapters
     * has not been saved in the database yet.
     */
//    private var hasChapters: Boolean

    /**
     * Returns the image of this item, as specified in the feed.
     * To load the image that can be displayed to the user, use [.getImageLocation],
     * which also considers embedded pictures or the feed picture if no other picture is present.
     */
    var imageUrl: String? = null

    var isAutoDownloadEnabled: Boolean = true
        private set

    var tags: RealmSet<String> = realmSetOf()

    /**
     * The list of chapters of this item. This might be null even if there are chapters of this item
     * in the database. The 'hasChapters' attribute should be used to check if this item has any chapters.
     */
    var chapters: RealmList<Chapter> = realmListOf()

    var isFavorite: Boolean = false

    @Ignore
    val isNew: Boolean
        get() = playState == NEW

    @Ignore
    val isInProgress: Boolean
        get() = (media != null && media!!.isInProgress)

    @Ignore
    val isDownloaded: Boolean
        get() = media != null && media!!.downloaded

    /**
     * Returns the value that uniquely identifies this FeedItem. If the
     * itemIdentifier attribute is not null, it will be returned. Else it will
     * try to return the title. If the title is not given, it will use the link
     * of the entry.
     */
    @Ignore
    val identifyingValue: String?
        get() = when {
            !identifier.isNullOrEmpty() -> identifier
            !title.isNullOrEmpty() -> title
            media?.downloadUrl != null -> media!!.downloadUrl
            else -> link
        }

    @Ignore
    val imageLocation: String?
        get() = when {
            imageUrl != null -> imageUrl
            media != null && media!!.hasEmbeddedPicture() -> EpisodeMedia.FILENAME_PREFIX_EMBEDDED_COVER + media!!.getLocalMediaUrl()
            feed != null -> {
                feed!!.imageUrl
            }
            else -> null
        }

    constructor() {
        this.playState = UNPLAYED
//        this.hasChapters = false
    }

    /**
     * This constructor is used by DBReader.
     */
//    constructor(id: Long, title: String?, link: String?, pubDate: Date?, paymentLink: String?, feedId: Long,
//                hasChapters: Boolean, imageUrl: String?, state: Int,
//                itemIdentifier: String?, autoDownloadEnabled: Boolean, podcastIndexChapterUrl: String?) {
//        this.id = id
//        this.title = title
//        this.link = link
//        this.pubDate = pubDate?.time ?: 0
//        this.paymentLink = paymentLink
//        this.feedId = feedId
////        this.hasChapters = hasChapters
//        this.imageUrl = imageUrl
//        this.playState = state
//        this.identifier = itemIdentifier
//        this.isAutoDownloadEnabled = autoDownloadEnabled
//        this.podcastIndexChapterUrl = podcastIndexChapterUrl
//    }

    /**
     * This constructor should be used for creating test objects.
     */
    constructor(id: Long, title: String?, itemIdentifier: String?, link: String?, pubDate: Date?, state: Int, feed: Feed?) {
        this.id = id
        this.title = title
        this.identifier = itemIdentifier
        this.link = link
        this.pubDate = if (pubDate != null) pubDate.time else 0
        this.playState = state
        if (feed != null) this.feedId = feed.id
        this.feed = feed
//        this.hasChapters = false
    }

    fun updateFromOther(other: Episode) {
        if (other.imageUrl != null) this.imageUrl = other.imageUrl
        if (other.title != null) title = other.title
        if (other.description != null) description = other.description
        if (other.link != null) link = other.link
        if (other.pubDate != 0L && other.pubDate != pubDate) pubDate = other.pubDate

        if (other.media != null) {
            when {
                media == null -> {
                    setMedia(other.media)
                    // reset to new if feed item did link to a file before
                    setNew()
                }
                media!!.compareWithOther(other.media!!) -> media!!.updateFromOther(other.media!!)
            }
        }
        if (other.paymentLink != null) paymentLink = other.paymentLink
        if (other.chapters.isNotEmpty()) {
            chapters.clear()
            chapters.addAll(other.chapters)
        }
        if (other.podcastIndexChapterUrl != null) podcastIndexChapterUrl = other.podcastIndexChapterUrl
    }


    @JvmName("getPubDateFunction")
    fun getPubDate(): Date? {
        return if (pubDate > 0) Date(pubDate) else null
    }

    @JvmName("setPubDateFunction")
    fun setPubDate(pubDate: Date?) {
        if (pubDate != null) this.pubDate = pubDate.time
        else this.pubDate = 0
    }

    /**
     * Sets the media object of this FeedItem. If the given
     * EpisodeMedia object is not null, it's 'item'-attribute value
     * will also be set to this item.
     */
    @JvmName("setMediaFunction")
    fun setMedia(media: EpisodeMedia?) {
        this.media = media
    }


    fun setNew() {
        playState = NEW
    }

    fun isPlayed(): Boolean {
        return playState == PLAYED
    }

    fun setPlayed(played: Boolean) {
        playState = if (played) PLAYED else UNPLAYED
    }

    fun setBuilding() {
        playState = BUILDING
    }

    /**
     * Updates this item's description property if the given argument is longer than the already stored description
     * @param newDescription The new item description, content:encoded, itunes:description, etc.
     */
    fun setDescriptionIfLonger(newDescription: String?) {
        if (newDescription.isNullOrEmpty()) return

        when {
            this.description == null -> this.description = newDescription
            description!!.length < newDescription.length -> this.description = newDescription
        }
    }

    fun setTranscriptIfLonger(newTranscript: String?) {
        if (newTranscript.isNullOrEmpty()) return

        when {
            this.transcript == null -> this.transcript = newTranscript
            transcript!!.length < newTranscript.length -> this.transcript = newTranscript
        }
    }

//    enum class State {
//        UNREAD, IN_PROGRESS, READ, PLAYING
//    }

    fun getHumanReadableIdentifier(): String? {
        return title
    }

//    fun hasChapters(): Boolean {
//        return chapters.isNotEmpty()
//    }

    /**
     * Get the link for the feed item for the purpose of Share. It fallbacks to
     * use the feed's link if the named feed item has no link.
     */
    fun getLinkWithFallback(): String? {
        return when {
            link.isNullOrBlank() -> link
            !feed?.link.isNullOrEmpty() -> feed!!.link
            else -> null
        }
    }

    fun disableAutoDownload() {
        this.isAutoDownloadEnabled = false
    }

    override fun toString(): String {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is Episode) return false
        return id == o.id
    }

    override fun hashCode(): Int {
        return (id xor (id ushr 32)).toInt()
    }

    companion object {
        val TAG: String = Episode::class.simpleName ?: "Anonymous"

        const val NEW: Int = -1
        const val UNPLAYED: Int = 0
        const val PLAYED: Int = 1
        const val BUILDING: Int = 2
    }
}
