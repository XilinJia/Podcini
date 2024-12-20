package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.unmanaged
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.VolumeAdaptionSetting.Companion.fromInteger
import ac.mdiq.podcini.util.Logd
import android.media.MediaMetadataRetriever
import android.os.Parcel
import android.os.Parcelable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.Index
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.util.*
import kotlin.math.max

class EpisodeMedia: EmbeddedRealmObject, Parcelable, Serializable {
    @Index
    var id: Long = 0L   // same as the episode id

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
        private set

    var episode: Episode? = null

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

    constructor() {}

    constructor(e: Episode?, downloadUrl: String?, size: Long, mimeType: String?) {
        this.episode = e
        this.size = size
        this.mimeType = mimeType
        setfileUrlOrNull(null)
        this.downloadUrl = downloadUrl
    }

    constructor(id: Long, item: Episode?, duration: Int, position: Int,
                size: Long, mimeType: String?, fileUrl: String?, downloadUrl: String?,
                downloaded: Boolean, playbackCompletionDate: Date?, playedDuration: Int,
                lastPlayedTime: Long) {
        this.id = id
        this.episode = item
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

    /**
     * Uses mimetype to determine the type of media.
     */
    fun getMediaType(): MediaType {
        return MediaType.fromMimeType(mimeType)
    }

    fun updateFromOther(other: EpisodeMedia) {
        this.downloadUrl = other.downloadUrl

        if (other.size > 0) size = other.size
        // Do not overwrite duration that we measured after downloading
        if (other.duration > 0 && duration <= 0) duration = other.duration
        if (other.mimeType != null) mimeType = other.mimeType
    }

    fun compareWithOther(other: EpisodeMedia): Boolean {
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
        if (episode?.isNew == true) episode!!.setPlayed(false)
    }

    fun setfileUrlOrNull(url: String?) {
        fileUrl = url
        if (url == null) downloaded = false
    }

    fun setPosition(newPosition: Int) {
        this.position = newPosition
        if (newPosition > 0 && episode?.isNew == true) episode!!.setPlayed(false)
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

    override fun describeContents(): Int {
        return 0
    }

    fun hasEmbeddedPicture(): Boolean {
//        TODO: checkEmbeddedPicture needs to update current copy
        if (hasEmbeddedPicture == null) checkEmbeddedPicture()
        return hasEmbeddedPicture == true
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(id.toString())
        dest.writeString(if (episode != null) episode!!.id.toString() else "")
        dest.writeInt(duration)
        dest.writeInt(position)
        dest.writeLong(size)
        dest.writeString(mimeType)
        dest.writeString(fileUrl)
        dest.writeString(downloadUrl)
        dest.writeByte((if (downloaded) 1 else 0).toByte())
        dest.writeLong(playbackCompletionDate?.time ?: 0)
        dest.writeInt(playedDuration)
        dest.writeLong(lastPlayedTime)
    }

    fun getEpisodeTitle(): String {
        return episode?.title ?: episode?.identifyingValue ?: "No title"
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

    fun setChapters(chapters: List<Chapter>) {
        if (episode != null) {
            episode!!.chapters.clear()
            for (c in chapters) c.episode = episode
            episode!!.chapters.addAll(chapters)
        }
    }

    /**
     * Returns the location of the image or null if no image is available.
     * This can be the feed item image URL, the local embedded media image path, the feed image URL,
     * or the remote media image URL, depending on what's available.
     */
    fun getImageLocation(): String? {
        return when {
            episode != null -> episode!!.imageLocation
            hasEmbeddedPicture() -> FILENAME_PREFIX_EMBEDDED_COVER + fileUrl
            else -> null
        }
    }

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

    fun episodeOrFetch(): Episode? {
        return if (episode != null) episode else {
            var item = realm.query(Episode::class).query("id == $id").first().find()
            Logd(TAG, "episodeOrFetch warning: episode of media is null: $id ${item?.title}")
            if (item != null) {
                item = upsertBlk(item) {
                    it.media = this@EpisodeMedia
                    it.media!!.episode = it
                }
            }
            if (item == null || isManaged()) item else unmanaged(item)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EpisodeMedia

        if (id != other.id) return false
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
//        if (isInProgress != other.isInProgress) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
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

    /**
     * On SDK<29, this class does not have a close method yet, so the app crashes when using try-with-resources.
     */
    class MediaMetadataRetrieverCompat : MediaMetadataRetriever(), AutoCloseable {
        override fun close() {
            try { release() } catch (e: IOException) { e.printStackTrace() }
        }
    }

    companion object {
        private val TAG: String = EpisodeMedia::class.simpleName ?: "Anonymous"

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

        @JvmField
        val CREATOR: Parcelable.Creator<EpisodeMedia> = object : Parcelable.Creator<EpisodeMedia> {
            override fun createFromParcel(inVal: Parcel): EpisodeMedia {
                val id = inVal.readLong()
                val itemID = inVal.readLong()
                val result = EpisodeMedia(id,
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
//                result.episodeId = itemID
                return result
            }

            override fun newArray(size: Int): Array<EpisodeMedia?> {
                return arrayOfNulls(size)
            }
        }
    }
}