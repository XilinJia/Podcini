package ac.mdiq.podvinci.model.feed

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import ac.mdiq.podvinci.model.MediaMetadataRetrieverCompat
import ac.mdiq.podvinci.model.playback.MediaType
import ac.mdiq.podvinci.model.playback.Playable
import ac.mdiq.podvinci.model.playback.RemoteMedia
import java.util.*
import kotlin.concurrent.Volatile
import kotlin.math.max

class FeedMedia : FeedFile, Playable {
    private var duration = 0
    private var position = 0 // Current position in file
    private var lastPlayedTime: Long = 0 // Last time this media was played (in ms)
    var playedDuration: Int = 0 // How many ms of this file have been played
    @JvmField
    var size: Long // File size in Byte
    var mime_type: String?
        private set

    @Volatile
    private var item: FeedItem?
    private var playbackCompletionDate: Date? = null
    var startPosition: Int = -1
        private set
    var playedDurationWhenStarted: Int = 0
        private set

    // if null: unknown, will be checked
    private var hasEmbeddedPicture: Boolean? = null

    /* Used for loading item when restoring from parcel. */
    var itemId: Long = 0
        private set

    constructor(i: FeedItem?, download_url: String?, size: Long,
                mime_type: String?
    ) : super(null, download_url, false) {
        this.item = i
        this.size = size
        this.mime_type = mime_type
    }

    constructor(id: Long, item: FeedItem?, duration: Int, position: Int,
                size: Long, mime_type: String?, file_url: String?, download_url: String?,
                downloaded: Boolean, playbackCompletionDate: Date?, played_duration: Int,
                lastPlayedTime: Long
    ) : super(file_url, download_url, downloaded) {
        this.id = id
        this.item = item
        this.duration = duration
        this.position = position
        this.playedDuration = played_duration
        this.playedDurationWhenStarted = played_duration
        this.size = size
        this.mime_type = mime_type
        this.playbackCompletionDate = if (playbackCompletionDate == null
        ) null else playbackCompletionDate.clone() as Date
        this.lastPlayedTime = lastPlayedTime
    }

    constructor(id: Long, item: FeedItem?, duration: Int, position: Int,
                size: Long, mime_type: String?, file_url: String?, download_url: String?,
                downloaded: Boolean, playbackCompletionDate: Date?, played_duration: Int,
                hasEmbeddedPicture: Boolean?, lastPlayedTime: Long
    ) : this(id, item, duration, position, size, mime_type, file_url, download_url, downloaded,
        playbackCompletionDate, played_duration, lastPlayedTime) {
        this.hasEmbeddedPicture = hasEmbeddedPicture
    }

    override fun getHumanReadableIdentifier(): String? {
        return if (item != null && item!!.title != null) {
            item!!.title
        } else {
            download_url
        }
    }

    val mediaItem: MediaBrowserCompat.MediaItem
        /**
         * Returns a MediaItem representing the FeedMedia object.
         * This is used by the MediaBrowserService
         */
        get() {
            val p: Playable = this
            val builder = MediaDescriptionCompat.Builder()
                .setMediaId(id.toString())
                .setTitle(p.getEpisodeTitle())
                .setDescription(p.getFeedTitle())
                .setSubtitle(p.getFeedTitle())
            if (item != null) {
                // getImageLocation() also loads embedded images, which we can not send to external devices
                if (item!!.imageUrl != null) {
                    builder.setIconUri(Uri.parse(item!!.imageUrl))
                } else if (item!!.feed != null && item!!.feed!!.imageUrl != null) {
                    builder.setIconUri(Uri.parse(item!!.feed!!.imageUrl))
                }
            }
            return MediaBrowserCompat.MediaItem(builder.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
        }

    /**
     * Uses mimetype to determine the type of media.
     */
    override fun getMediaType(): MediaType {
        return MediaType.fromMimeType(mime_type!!)
    }

    fun updateFromOther(other: FeedMedia) {
        super.updateFromOther(other)
        if (other.size > 0) {
            size = other.size
        }
        if (other.duration > 0 && duration <= 0) { // Do not overwrite duration that we measured after downloading
            duration = other.duration
        }
        if (other.mime_type != null) {
            mime_type = other.mime_type
        }
    }

    fun compareWithOther(other: FeedMedia): Boolean {
        if (super.compareWithOther(other)) {
            return true
        }
        if (other.mime_type != null) {
            if (mime_type == null || mime_type != other.mime_type) {
                return true
            }
        }
        if (other.size > 0 && other.size != size) {
            return true
        }
        if (other.duration > 0 && duration <= 0) {
            return true
        }
        return false
    }

    override fun getTypeAsInt(): Int {
        return FEEDFILETYPE_FEEDMEDIA
    }

    override fun getDuration(): Int {
        return duration
    }

    override fun setDuration(duration: Int) {
        this.duration = duration
    }

    override fun setLastPlayedTime(lastPlayedTime: Long) {
        this.lastPlayedTime = lastPlayedTime
    }

    override fun getPosition(): Int {
        return position
    }

    override fun getLastPlayedTime(): Long {
        return lastPlayedTime
    }

    override fun setPosition(position: Int) {
        this.position = position
        if (position > 0 && item != null && item!!.isNew) {
            item!!.setPlayed(false)
        }
    }

    override fun getDescription(): String? {
        if (item != null) {
            return item!!.description
        }
        return null
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

    fun getItem(): FeedItem? {
        return item
    }

    /**
     * Sets the item object of this FeedMedia. If the given
     * FeedItem object is not null, it's 'media'-attribute value
     * will also be set to this media object.
     */
    fun setItem(item: FeedItem?) {
        this.item = item
        if (item != null && item.media !== this) {
            item.setMedia(this)
        }
    }

    fun getPlaybackCompletionDate(): Date? {
        return if (playbackCompletionDate == null) null
        else playbackCompletionDate!!.clone() as Date
    }

    fun setPlaybackCompletionDate(playbackCompletionDate: Date?) {
        this.playbackCompletionDate = if (playbackCompletionDate == null
        ) null else playbackCompletionDate.clone() as Date
    }

    val isInProgress: Boolean
        get() = (this.position > 0)

    override fun describeContents(): Int {
        return 0
    }

    fun hasEmbeddedPicture(): Boolean {
        if (hasEmbeddedPicture == null) {
            checkEmbeddedPicture()
        }
        return hasEmbeddedPicture!!
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(id)
        dest.writeLong(if (item != null) item!!.id else 0L)

        dest.writeInt(duration)
        dest.writeInt(position)
        dest.writeLong(size)
        dest.writeString(mime_type)
        dest.writeString(file_url)
        dest.writeString(download_url)
        dest.writeByte((if ((isDownloaded())) 1 else 0).toByte())
        dest.writeLong(if ((playbackCompletionDate != null)) playbackCompletionDate!!.time else 0)
        dest.writeInt(playedDuration)
        dest.writeLong(lastPlayedTime)
    }

    override fun writeToPreferences(prefEditor: SharedPreferences.Editor?) {
        if (item != null && item!!.feed != null) {
            prefEditor!!.putLong(PREF_FEED_ID, item!!.feed!!.id)
        } else {
            prefEditor!!.putLong(PREF_FEED_ID, 0L)
        }
        prefEditor.putLong(PREF_MEDIA_ID, id)
    }

    override fun getEpisodeTitle(): String {
        if (item == null) {
            return ""
        }
        return if (item!!.title != null) {
            item!!.title!!
        } else {
            item!!.identifyingValue?:""
        }
    }

    override fun getChapters(): List<Chapter> {
        if (item?.chapters == null) {
            return listOf()
        }
        return item!!.chapters!!
    }

    override fun getWebsiteLink(): String? {
        if (item == null) {
            return null
        }
        return item!!.link
    }

    override fun getFeedTitle(): String {
        if (item?.feed?.title == null) {
            return ""
        }
        return item!!.feed!!.title!!
    }

    override fun getIdentifier(): Any {
        return id
    }

    override fun getLocalMediaUrl(): String? {
        return file_url
    }

    override fun getStreamUrl(): String? {
        return download_url
    }

    override fun getPubDate(): Date? {
        return if (item?.getPubDate() != null) {
            item!!.getPubDate()!!
        } else {
            null
        }
    }

    override fun localFileAvailable(): Boolean {
        return isDownloaded() && file_url != null
    }

    override fun onPlaybackStart() {
        startPosition = max(position.toDouble(), 0.0).toInt()
        playedDurationWhenStarted = playedDuration
    }

    override fun onPlaybackPause(context: Context) {
        if (position > startPosition) {
            playedDuration = playedDurationWhenStarted + position - startPosition
            playedDurationWhenStarted = playedDuration
        }
        startPosition = position
    }

    override fun onPlaybackCompleted(context: Context) {
        startPosition = -1
    }

    override fun getPlayableType(): Int {
        return PLAYABLE_TYPE_FEEDMEDIA
    }

    override fun setChapters(chapters: List<Chapter>) {
        if (item != null) {
            item!!.chapters = chapters.toMutableList()
        }
    }

    override fun getImageLocation(): String? {
        return if (item != null) {
            item!!.imageLocation
        } else if (hasEmbeddedPicture()) {
            FILENAME_PREFIX_EMBEDDED_COVER + getLocalMediaUrl()
        } else {
            null
        }
    }

    fun setHasEmbeddedPicture(hasEmbeddedPicture: Boolean?) {
        this.hasEmbeddedPicture = hasEmbeddedPicture
    }

    override fun setDownloaded(downloaded: Boolean) {
        super.setDownloaded(downloaded)
        if (item != null && downloaded && item!!.isNew) {
            item!!.setPlayed(false)
        }
    }

    fun checkEmbeddedPicture() {
        if (!localFileAvailable()) {
            hasEmbeddedPicture = java.lang.Boolean.FALSE
            return
        }
        try {
            MediaMetadataRetrieverCompat().use { mmr ->
                mmr.setDataSource(getLocalMediaUrl())
                val image = mmr.embeddedPicture
                hasEmbeddedPicture = if (image != null) {
                    java.lang.Boolean.TRUE
                } else {
                    java.lang.Boolean.FALSE
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            hasEmbeddedPicture = java.lang.Boolean.FALSE
        }
    }

    override fun equals(o: Any?): Boolean {
        if (o == null) {
            return false
        }
        if (o is RemoteMedia) {
            return o == this
        }
        return super.equals(o)
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + duration
        result = 31 * result + position
        result = 31 * result + lastPlayedTime.hashCode()
        result = 31 * result + playedDuration
        result = 31 * result + size.hashCode()
        result = 31 * result + (mime_type?.hashCode() ?: 0)
        result = 31 * result + (item?.hashCode() ?: 0)
        result = 31 * result + (playbackCompletionDate?.hashCode() ?: 0)
        result = 31 * result + startPosition
        result = 31 * result + playedDurationWhenStarted
        result = 31 * result + (hasEmbeddedPicture?.hashCode() ?: 0)
        result = 31 * result + itemId.hashCode()
        return result
    }

    companion object {
        const val FEEDFILETYPE_FEEDMEDIA: Int = 2
        const val PLAYABLE_TYPE_FEEDMEDIA: Int = 1
        const val FILENAME_PREFIX_EMBEDDED_COVER: String = "metadata-retriever:"

        const val PREF_MEDIA_ID: String = "FeedMedia.PrefMediaId"
        private const val PREF_FEED_ID = "FeedMedia.PrefFeedId"

        /**
         * Indicates we've checked on the size of the item via the network
         * and got an invalid response. Using Integer.MIN_VALUE because
         * 1) we'll still check on it in case it gets downloaded (it's <= 0)
         * 2) By default all FeedMedia have a size of 0 if we don't know it,
         * so this won't conflict with existing practice.
         */
        private const val CHECKED_ON_SIZE_BUT_UNKNOWN = Int.MIN_VALUE

        @JvmField
        val CREATOR: Parcelable.Creator<FeedMedia> = object : Parcelable.Creator<FeedMedia> {
            override fun createFromParcel(inVal: Parcel): FeedMedia? {
                val id = inVal.readLong()
                val itemID = inVal.readLong()
                val result = FeedMedia(id,
                    null,
                    inVal.readInt(),
                    inVal.readInt(),
                    inVal.readLong(),
                    inVal.readString(),
                    inVal.readString(),
                    inVal.readString(),
                    inVal.readByte().toInt() != 0,
                    Date(inVal.readLong()),
                    inVal.readInt(),
                    inVal.readLong())
                result.itemId = itemID
                return result
            }

            override fun newArray(size: Int): Array<FeedMedia?> {
                return arrayOfNulls(size)
            }
        }
    }
}
