package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.vista.extractor.Vista
import ac.mdiq.vista.extractor.stream.StreamInfo
import androidx.compose.runtime.mutableStateOf
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

class Episode : RealmObject {
    @PrimaryKey
    var id: Long = 0L   // increments from Date().time * 100 at time of creation

    /**
     * The id/guid that can be found in the rss/atom feed. Might not be set, especially in youtube feeds
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
            if (field == null && feedId != null) field = getFeed(feedId!!)
            return field
        }

    var feedId: Long? = null

    // parent in these refers to the original parent of the content (shared)
    var parentTitle: String? = null

    var parentURL: String? = null

    var podcastIndexChapterUrl: String? = null

    var playState: Int

    var paymentLink: String? = null

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

    var rating: Int = Rating.UNRATED.code

    // info from youtube
    var viewCount: Int = 0

    @Ignore
    var isSUPER: Boolean = (rating == Rating.SUPER.code)
        private set

    @FullText
    var comment: String = ""

    var commentTime: Long = 0L

    @Ignore
    val isNew: Boolean
        get() = playState == PlayState.NEW.code

    @Ignore
    val isInProgress: Boolean
        get() = (media != null && media!!.position > 0)

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
//            TODO: this can be very expensive for list
//            media != null && media?.hasEmbeddedPicture() == true -> EpisodeMedia.FILENAME_PREFIX_EMBEDDED_COVER + media!!.getLocalMediaUrl()
            feed != null -> feed!!.imageUrl
            else -> null
        }

    @Ignore
    var streamInfo: StreamInfo? = null
        get() {
            if (field == null) {
                if (media?.downloadUrl == null) return null
                field = StreamInfo.getInfo(Vista.getService(0), media!!.downloadUrl!!)
            }
            return field
        }

    @Ignore
    val isRemote = mutableStateOf(false)

    constructor() {
        this.playState = PlayState.NEW.code
    }

    /**
     * This constructor should be used for creating test objects.
     */
    constructor(id: Long, title: String?, itemIdentifier: String?, link: String?, pubDate: Date?, state: Int, feed: Feed?) {
        this.id = id
        this.title = title
        this.identifier = itemIdentifier
        this.link = link
        this.pubDate = pubDate?.time ?: 0
        this.playState = state
        if (feed != null) this.feedId = feed.id
        this.feed = feed
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
//                    setNew()
                    playState = PlayState.NEW.code
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
    fun getPubDate(): Date {
        return Date(pubDate)
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

    fun isPlayed(): Boolean {
        return playState >= PlayState.SKIPPED.code
    }

    fun setPlayed(played: Boolean) {
        playState = if (played) PlayState.PLAYED.code else PlayState.UNPLAYED.code
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Episode

        if (id != other.id) return false
        if (identifier != other.identifier) return false
        if (title != other.title) return false
        if (description != other.description) return false
        if (transcript != other.transcript) return false
        if (link != other.link) return false
        if (pubDate != other.pubDate) return false
        if (media != other.media) return false
        if (feedId != other.feedId) return false
        if (podcastIndexChapterUrl != other.podcastIndexChapterUrl) return false
        if (playState != other.playState) return false
        if (paymentLink != other.paymentLink) return false
        if (imageUrl != other.imageUrl) return false
        if (isAutoDownloadEnabled != other.isAutoDownloadEnabled) return false
        if (tags != other.tags) return false
        if (chapters != other.chapters) return false
        if (rating != other.rating) return false
        if (isInProgress != other.isInProgress) return false
        if (isDownloaded != other.isDownloaded) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (identifier?.hashCode() ?: 0)
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + (transcript?.hashCode() ?: 0)
        result = 31 * result + (link?.hashCode() ?: 0)
        result = 31 * result + pubDate.hashCode()
        result = 31 * result + (media?.hashCode() ?: 0)
        result = 31 * result + (feedId?.hashCode() ?: 0)
        result = 31 * result + (podcastIndexChapterUrl?.hashCode() ?: 0)
        result = 31 * result + playState
        result = 31 * result + (paymentLink?.hashCode() ?: 0)
        result = 31 * result + (imageUrl?.hashCode() ?: 0)
        result = 31 * result + isAutoDownloadEnabled.hashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + chapters.hashCode()
        result = 31 * result + rating.hashCode()
        result = 31 * result + isInProgress.hashCode()
        result = 31 * result + isDownloaded.hashCode()
        return result
    }

    companion object {
        val TAG: String = Episode::class.simpleName ?: "Anonymous"
    }
}
