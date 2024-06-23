package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.storage.utils.MediaType
import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import org.apache.commons.lang3.builder.HashCodeBuilder
import java.util.*

/**
 * Playable implementation for media for which a local version of
 * [EpisodeMedia] hasn't been found.
 * Used for Casting and for previewing unsubscribed feeds.
 */
class RemoteMedia : Playable {
    val TAG = this::class.simpleName ?: "Anonymous"

    private var itemIdentifier: String? = null
    private val downloadUrl: String? = null
    private val imageUrl: String?
    private val notes: String? = null

    private val streamUrl: String?
    val feedUrl: String?
    private val feedTitle: String?
    private val episodeTitle: String?
    val episodeLink: String?
    private val feedAuthor: String?
    private val imageLocation: String?
    val feedLink: String?
    private val mimeType: String?
    private val pubDate: Long
    private val description: String?
    private var chapters: List<Chapter>? = null
    private var duration: Int = 0
    private var position: Int = 0
    private var lastPlayedTime: Long = 0

    constructor(downloadUrl: String?, itemId: String?, feedUrl: String?, feedTitle: String?,
                episodeTitle: String?, episodeLink: String?, feedAuthor: String?,
                imageUrl: String?, feedLink: String?, mimeType: String?, pubDate: Date?,
                notes: String?) {
        this.streamUrl = downloadUrl
        this.itemIdentifier = itemId
        this.feedUrl = feedUrl
        this.feedTitle = feedTitle
        this.episodeTitle = episodeTitle
        this.episodeLink = episodeLink
        this.feedAuthor = feedAuthor
        this.imageLocation = imageUrl
        this.imageUrl = imageUrl
        this.feedLink = feedLink
        this.mimeType = mimeType
        this.pubDate = pubDate?.time ?: 0
        this.description = notes
    }

    constructor(item: Episode) {
        this.streamUrl = item.media?.downloadUrl
        this.itemIdentifier = item.identifier
        this.feedUrl = item.feed?.downloadUrl
        this.feedTitle = item.feed?.title
        this.episodeTitle = item.title
        this.episodeLink = item.link
        this.feedAuthor = item.feed?.author
        if (!item.imageUrl.isNullOrEmpty()) {
            this.imageLocation = item.imageLocation
            this.imageUrl = item.imageUrl
        } else {
            this.imageLocation = item.feed?.imageUrl
            this.imageUrl = item.feed?.imageUrl
        }
        this.feedLink = item.feed?.link
        this.mimeType = item.media?.mimeType
        this.pubDate = item.pubDate
        this.description = item.description

        this.duration = item.media?.getDuration() ?: 0
    }

    fun getEpisodeIdentifier(): String? {
        return itemIdentifier
    }

    fun getFeedAuthor(): String {
        return feedAuthor ?:"No author"
    }

    fun getMimeType(): String {
        return mimeType?:"Unknown"
    }

    override fun getPubDate(): Date {
        return Date(pubDate)
    }

//    override fun writeToPreferences(prefEditor: SharedPreferences.Editor) {
//        //it seems pointless to do it, since the session should be kept by the remote device.
//    }

    override fun getChapters(): List<Chapter> {
        return chapters ?: listOf()
    }

    override fun chaptersLoaded(): Boolean {
        return chapters != null
    }

    override fun getEpisodeTitle(): String {
        return episodeTitle ?: "No title"
    }

    override fun getFeedTitle(): String {
        return feedTitle ?: "No title"
    }

    override fun getWebsiteLink(): String {
        return episodeLink ?: feedUrl ?: ""
    }
    override fun getIdentifier(): Any {
        return "$itemIdentifier@$feedUrl"
    }
    override fun getDuration(): Int {
        return duration
    }

    override fun getPosition(): Int {
        return position
    }

    override fun getLastPlayedTime(): Long {
        return lastPlayedTime
    }

    override fun getMediaType(): MediaType {
        return MediaType.fromMimeType(mimeType)
    }

    override fun getStreamUrl(): String? {
        return streamUrl
    }

    override fun getLocalMediaUrl(): String? {
        return null
    }

    override fun localFileAvailable(): Boolean {
        return false
    }

    override fun setPosition(newPosition: Int) {
        position = newPosition
    }

    override fun setDuration(newDuration: Int) {
        duration = newDuration
    }

    override fun setLastPlayedTime(lastPlayedTimestamp: Long) {
        lastPlayedTime = lastPlayedTimestamp
    }

    override fun onPlaybackStart() {
        // no-op
    }

    override fun onPlaybackPause(context: Context) {
        // no-op
    }

    override fun onPlaybackCompleted(context: Context) {
        // no-op
    }
    override fun getPlayableType(): Int {
        return PLAYABLE_TYPE_REMOTE_MEDIA
    }

    override fun setChapters(chapters: List<Chapter>) {
        this.chapters = chapters
    }

    override fun getImageLocation(): String? {
        return imageLocation
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun getDescription(): String? {
        return notes
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(streamUrl)
        dest.writeString(itemIdentifier)
        dest.writeString(feedUrl)
        dest.writeString(feedTitle)
        dest.writeString(episodeTitle)
        dest.writeString(episodeLink)
        dest.writeString(feedAuthor)
        dest.writeString(imageLocation)
        dest.writeString(feedLink)
        dest.writeString(mimeType)
        dest.writeLong(pubDate)
        dest.writeString(description)
        dest.writeInt(duration)
        dest.writeInt(position)
        dest.writeLong(lastPlayedTime)
    }

    override fun equals(other: Any?): Boolean {
        if (other is RemoteMedia)
            return ((streamUrl == other.streamUrl) && (feedUrl == other.feedUrl) && (itemIdentifier == other.itemIdentifier))

        if (other is EpisodeMedia) {
            if (streamUrl != other.getStreamUrl()) return false

            val fi = other.episode
            if (fi == null || itemIdentifier != fi.identifier) return false

            val feed = fi.feed
            return feed != null && feedUrl == feed.downloadUrl
        }
        return false
    }

    override fun hashCode(): Int {
        return HashCodeBuilder()
            .append(streamUrl)
            .append(feedUrl)
            .toHashCode()
    }

    companion object {
        const val PLAYABLE_TYPE_REMOTE_MEDIA: Int = 3

        @JvmField
        val CREATOR: Parcelable.Creator<RemoteMedia> = object : Parcelable.Creator<RemoteMedia> {
            override fun createFromParcel(inVal: Parcel): RemoteMedia {
                val result = RemoteMedia(inVal.readString(), inVal.readString(), inVal.readString(),
                    inVal.readString(), inVal.readString(), inVal.readString(), inVal.readString(), inVal.readString(),
                    inVal.readString(), inVal.readString(), Date(inVal.readLong()), inVal.readString())
                result.duration = inVal.readInt()
                result.position = inVal.readInt()
                result.lastPlayedTime = inVal.readLong()
                return result
            }

            override fun newArray(size: Int): Array<RemoteMedia?> {
                return arrayOfNulls(size)
            }
        }
    }
}
