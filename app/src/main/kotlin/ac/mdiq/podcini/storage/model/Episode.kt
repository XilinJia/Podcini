package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.model.VolumeAdaptionSetting.Companion.fromInteger
import ac.mdiq.podcini.util.Logd
import ac.mdiq.vista.extractor.Vista
import ac.mdiq.vista.extractor.stream.StreamInfo
import android.media.MediaMetadataRetriever
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.math.max

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
    var shortDescription: String? = null

    @FullText
    var description: String? = null

    @FullText
    var transcript: String? = null

    var link: String? = null

    @get:JvmName("getPubDateProperty")
    @set:JvmName("setPubDateProperty")
    var pubDate: Long = 0

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
        get() = position > 0

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
            downloadUrl != null -> downloadUrl
            else -> link
        }

    @Ignore
    val imageLocation: String?
        get() = when {
            imageUrl != null -> imageUrl
//            TODO: this can be very expensive for list
//            media != null && media?.hasEmbeddedPicture() == true -> EpisodeMedia.FILENAME_PREFIX_EMBEDDED_COVER + media!!.fileUrl
            feed != null -> feed!!.imageUrl
            hasEmbeddedPicture() -> FILENAME_PREFIX_EMBEDDED_COVER + fileUrl
            else -> null
        }

    @Ignore
    var streamInfo: StreamInfo? = null
        get() {
            if (field == null) {
                if (downloadUrl == null) return null
                field = StreamInfo.getInfo(Vista.getService(0), downloadUrl!!)
            }
            return field
        }

    @Ignore
    val isRemote = mutableStateOf(false)

    // from EpisodeMedia

    var fileUrl: String? = null

    var downloadUrl: String? = null

    var downloaded: Boolean = false

    var downloadTime: Long = 0

    var duration = 0    // in milliseconds

    @set:JvmName("setPositionProperty")
    var position = 0 // Current position in file, in milliseconds

    var lastPlayedTime: Long = 0 // Last time this media was played (in ms)

    var startPosition: Int = -1

    var playedDurationWhenStarted: Int = 0

    var playedDuration: Int = 0 // How many ms of this file have been played

    var timeSpentOnStart: Int = 0 // How many ms of this file have been played in actual time
    var startTime: Long = 0 // time in ms when start playing

    var timeSpent: Int = 0 // How many ms of this file have been played in actual time

    // File size in Byte
    var size: Long = 0L

    var mimeType: String? = ""

    @Ignore
    var playbackCompletionDate: Date? = null
        get() = field?.clone() as? Date
        set(value) {
            field = value?.clone() as? Date
            this.playbackCompletionTime = value?.time ?: 0
        }
    var playbackCompletionTime: Long = 0

    @Ignore
    var volumeAdaptionSetting: VolumeAdaptionSetting = VolumeAdaptionSetting.OFF
        get() = fromInteger(volumeAdaption)
        set(value) {
            field = value
            volumeAdaption = field.toInteger()
        }
    @Ignore
    var volumeAdaption: Int = 0

    // if null: unknown, will be checked
    // TODO: what to do with this? can be expensive
    @Ignore
    var hasEmbeddedPicture: Boolean? = null

    @Ignore
    var forceVideo by mutableStateOf(false)

    @Ignore
    var effectUrl = ""

    @Ignore
    var effectMimeType = ""

    @Ignore
    var bitrate: Int = 0

    // above from EpisodeMedia


    constructor() {
        this.playState = PlayState.NEW.code
    }

    // used only in LocalFeedUpdater
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

    fun updateFromOther(other: Episode, includingState: Boolean = false) {
        if (other.imageUrl != null) this.imageUrl = other.imageUrl
        if (other.title != null) title = other.title
        if (other.description != null) description = other.description
        if (other.link != null) link = other.link
        if (other.pubDate != 0L && other.pubDate != pubDate) pubDate = other.pubDate

        this.downloadUrl = other.downloadUrl

        if (other.size > 0) size = other.size
        // Do not overwrite duration that we measured after downloading
        if (other.duration > 0 && duration <= 0) duration = other.duration
        if (other.mimeType != null) mimeType = other.mimeType

        if (other.paymentLink != null) paymentLink = other.paymentLink
        if (other.chapters.isNotEmpty()) {
            chapters.clear()
            chapters.addAll(other.chapters)
        }
        if (other.podcastIndexChapterUrl != null) podcastIndexChapterUrl = other.podcastIndexChapterUrl

        if (includingState) {
            this.rating = other.rating
            this.playState = other.playState
            this.position = other.position
            this.playbackCompletionTime = other.playbackCompletionTime
            this.playedDuration = other.playedDuration
            this.hasEmbeddedPicture = other.hasEmbeddedPicture
            this.lastPlayedTime = other.lastPlayedTime
            this.isAutoDownloadEnabled = other.isAutoDownloadEnabled
        }
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

        if (fileUrl != other.fileUrl) return false
        if (downloadUrl != other.downloadUrl) return false
        if (downloaded != other.downloaded) return false
        if (downloadTime != other.downloadTime) return false
        if (duration != other.duration) return false
        if (position != other.position) return false
        if (lastPlayedTime != other.lastPlayedTime) return false
        if (playedDuration != other.playedDuration) return false
        if (size != other.size) return false
        if (mimeType != other.mimeType) return false
        if (playbackCompletionDate != other.playbackCompletionDate) return false
        if (playbackCompletionTime != other.playbackCompletionTime) return false
        if (startPosition != other.startPosition) return false
        if (playedDurationWhenStarted != other.playedDurationWhenStarted) return false
        if (hasEmbeddedPicture != other.hasEmbeddedPicture) return false

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
//        result = 31 * result + (media?.hashCode() ?: 0)
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

        result = 31 * result + (fileUrl?.hashCode() ?: 0)
        result = 31 * result + (downloadUrl?.hashCode() ?: 0)
        result = 31 * result + downloaded.hashCode()
        result = 31 * result + downloadTime.hashCode()
        result = 31 * result + duration
        result = 31 * result + position
        result = 31 * result + lastPlayedTime.hashCode()
        result = 31 * result + playedDuration
        result = 31 * result + size.hashCode()
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        result = 31 * result + (playbackCompletionDate?.hashCode() ?: 0)
        result = 31 * result + playbackCompletionTime.hashCode()
        result = 31 * result + startPosition
        result = 31 * result + playedDurationWhenStarted
        result = 31 * result + (hasEmbeddedPicture?.hashCode() ?: 0)

        return result
    }

    fun fillMedia(duration: Int, position: Int,
                  size: Long, mimeType: String?, fileUrl: String?, downloadUrl: String?,
                  downloaded: Boolean, playbackCompletionDate: Date?, playedDuration: Int,
                  lastPlayedTime: Long) {
        this.duration = duration
        this.position = position
        this.playedDuration = playedDuration
        this.playedDurationWhenStarted = playedDuration
        this.size = size
        this.mimeType = mimeType
        this.playbackCompletionDate =  playbackCompletionDate?.clone() as? Date
        this.playbackCompletionTime =  playbackCompletionDate?.time ?: 0
        this.lastPlayedTime = lastPlayedTime
        setfileUrlOrNull(fileUrl)
        this.downloadUrl = downloadUrl
        if (downloaded) setIsDownloaded()
        else this.downloaded = downloaded
    }

    fun fillMedia(downloadUrl: String?, size: Long, mimeType: String?) {
        this.size = size
        this.mimeType = mimeType
        setfileUrlOrNull(null)
        this.downloadUrl = downloadUrl
    }

    // from EpisodeMedia

    /**
     * Uses mimetype to determine the type of media.
     */
    fun getMediaType(): MediaType {
        return MediaType.fromMimeType(mimeType)
    }

//    fun updateFromOther(other: Episode) {
//        this.downloadUrl = other.downloadUrl
//
//        if (other.size > 0) size = other.size
//        // Do not overwrite duration that we measured after downloading
//        if (other.duration > 0 && duration <= 0) duration = other.duration
//        if (other.mimeType != null) mimeType = other.mimeType
//    }

    fun compareWithOther(other: Episode): Boolean {
        if (downloadUrl != other.downloadUrl) return true

        if (other.mimeType != null) {
            if (mimeType == null || mimeType != other.mimeType) return true
        }
        if (other.size > 0 && other.size != size) return true
        if (other.duration > 0 && duration <= 0) return true

        return false
    }

    fun setIsDownloaded() {
        downloaded = true
        downloadTime = Date().time
        if (isNew == true) setPlayed(false)
    }

    fun setfileUrlOrNull(url: String?) {
        fileUrl = url
        if (url == null) downloaded = false
    }

    fun setPosition(newPosition: Int) {
        this.position = newPosition
        if (newPosition > 0 && isNew == true) setPlayed(false)
    }

    fun fileExists(): Boolean {
        if (fileUrl == null) return false
        else {
            val f = File(fileUrl!!)
            return f.exists()
        }
    }

    /**
     * Indicates we asked the service what the size was, but didn't
     * get a valid answer and we shoudln't check using the network again.
     */
    fun setCheckedOnSizeButUnknown() {
        this.size = CHECKED_ON_SIZE_BUT_UNKNOWN.toLong()
    }

    fun checkedOnSizeButUnknown(): Boolean {
        return (CHECKED_ON_SIZE_BUT_UNKNOWN.toLong() == this.size)
    }

//    override fun describeContents(): Int {
//        return 0
//    }

    fun hasEmbeddedPicture(): Boolean {
//        TODO: checkEmbeddedPicture needs to update current copy
        if (hasEmbeddedPicture == null) checkEmbeddedPicture()
        return hasEmbeddedPicture == true
    }

//    override fun writeToParcel(dest: Parcel, flags: Int) {
//        dest.writeString(id.toString())
//        dest.writeString(if (episode != null) episode!!.id.toString() else "")
//        dest.writeInt(duration)
//        dest.writeInt(position)
//        dest.writeLong(size)
//        dest.writeString(mimeType)
//        dest.writeString(fileUrl)
//        dest.writeString(downloadUrl)
//        dest.writeByte((if (downloaded) 1 else 0).toByte())
//        dest.writeLong(playbackCompletionDate?.time ?: 0)
//        dest.writeInt(playedDuration)
//        dest.writeLong(lastPlayedTime)
//    }

    fun getEpisodeTitle(): String {
        return title ?: identifyingValue ?: "No title"
    }

    /**
     * Returns true if a local file that can be played is available. getFileUrl MUST return a non-null string if this method returns true.
     */
    fun localFileAvailable(): Boolean {
        return downloaded && fileUrl != null
    }

    /**
     * This method should be called every time playback starts on this object.
     * Position held by this EpisodeMedia should be set accurately before a call to this method is made.
     */
    fun onPlaybackStart() {
        Logd(TAG, "onPlaybackStart ${System.currentTimeMillis()}")
        startPosition = max(position.toDouble(), 0.0).toInt()
        playedDurationWhenStarted = playedDuration
        timeSpentOnStart = timeSpent
        startTime = System.currentTimeMillis()
    }

    /**
     * This method should be called every time playback pauses or stops on this object,
     * including just before a seeking operation is performed, after which a call to
     * [.onPlaybackStart] should be made. If playback completes, calling this method is not
     * necessary, as long as a call to [.onPlaybackCompleted] is made.
     * Position held by this EpisodeMedia should be set accurately before a call to this method is made.
     */
    fun onPlaybackPause() {
        Logd(TAG, "onPlaybackPause $position $duration")
        if (position > startPosition) playedDuration = playedDurationWhenStarted + position - startPosition
        timeSpent = timeSpentOnStart + (System.currentTimeMillis() - startTime).toInt()
        startPosition = position
    }

    /**
     * This method should be called when playback completes for this object.
     */
    fun onPlaybackCompleted() {
        startPosition = -1
    }

    fun setChapters(chapters_: List<Chapter>) {
        chapters.clear()
        for (c in chapters_) c.episode = this
        chapters.addAll(chapters_)
    }

    /**
     * Returns the location of the image or null if no image is available.
     * This can be the feed item image URL, the local embedded media image path, the feed image URL,
     * or the remote media image URL, depending on what's available.
     */
//    @JvmName("getImageLocationFunction")
//    fun getImageLocation(): String? {
//        return when {
//            imageUrl != null -> imageUrl
////            TODO: this can be very expensive for list
////            media != null && media?.hasEmbeddedPicture() == true -> EpisodeMedia.FILENAME_PREFIX_EMBEDDED_COVER + media!!.fileUrl
//            feed != null -> feed!!.imageUrl
////            episode != null -> episode!!.imageLocation
//            hasEmbeddedPicture() -> FILENAME_PREFIX_EMBEDDED_COVER + fileUrl
//            else -> null
//        }
//    }

    fun checkEmbeddedPicture(persist: Boolean = true) {
        if (!localFileAvailable()) hasEmbeddedPicture = false
        else {
            try {
                MediaMetadataRetrieverCompat().use { mmr ->
                    mmr.setDataSource(fileUrl)
                    val image = mmr.embeddedPicture
                    hasEmbeddedPicture = image != null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                hasEmbeddedPicture = false
            }
        }
//        if (persist && episode != null) upsertBlk(episode!!) {}
    }

    /**
     * On SDK<29, this class does not have a close method yet, so the app crashes when using try-with-resources.
     */
    class MediaMetadataRetrieverCompat : MediaMetadataRetriever(), AutoCloseable {
        override fun close() {
            try { release() } catch (e: IOException) { e.printStackTrace() }
        }
    }

    // above from EpisodeMedia

    companion object {
        val TAG: String = Episode::class.simpleName ?: "Anonymous"

        // from EpisodeMedia
        const val INVALID_TIME: Int = -1
        const val FEEDFILETYPE_FEEDMEDIA: Int = 2
        const val PLAYABLE_TYPE_FEEDMEDIA: Int = 1
        const val FILENAME_PREFIX_EMBEDDED_COVER: String = "metadata-retriever:"
        /**
         * Indicates we've checked on the size of the item via the network
         * and got an invalid response. Using Integer.MIN_VALUE because
         * 1) we'll still check on it in case it gets downloaded (it's <= 0)
         * 2) By default all EpisodeMedia have a size of 0 if we don't know it,
         * so this won't conflict with existing practice.
         */
        private const val CHECKED_ON_SIZE_BUT_UNKNOWN = Int.MIN_VALUE

    }
}
