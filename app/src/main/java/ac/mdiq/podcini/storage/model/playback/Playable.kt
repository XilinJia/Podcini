package ac.mdiq.podcini.storage.model.playback

import android.content.Context
import android.content.SharedPreferences
import android.os.Parcelable
import ac.mdiq.podcini.storage.model.feed.Chapter
import java.io.Serializable
import java.util.*

/**
 * Interface for objects that can be played by the PlaybackService.
 */
interface Playable : Parcelable, Serializable {
    /**
     * Save information about the playable in a preference so that it can be
     * restored later via PlaybackPreferences.createInstanceFromPreferences.
     * Implementations must NOT call commit() after they have written the values
     * to the preferences file.
     */
    fun writeToPreferences(prefEditor: SharedPreferences.Editor)

    /**
     * Returns the title of the episode that this playable represents
     */
    fun getEpisodeTitle(): String

    /**
     * Returns a list of chapter marks or null if this Playable has no chapters.
     */
    fun getChapters(): List<Chapter>

    fun chaptersLoaded(): Boolean

    /**
     * Returns a link to a website that is meant to be shown in a browser
     */
    fun getWebsiteLink(): String?

    /**
     * Returns the title of the feed this Playable belongs to.
     */
    fun getFeedTitle(): String

    /**
     * Returns the published date
     */
    fun getPubDate(): Date?

    /**
     * Returns a unique identifier, for example a file url or an ID from a
     * database.
     */
    fun getIdentifier(): Any

    /**
     * Return duration of object or 0 if duration is unknown.
     */
    fun getDuration(): Int

    /**
     * Return position of object or 0 if position is unknown.
     */
    fun getPosition(): Int

    /**
     * Returns last time (in ms) when this playable was played or 0
     * if last played time is unknown.
     */
    /**
     * @param lastPlayedTimestamp  timestamp in ms
     */
    fun getLastPlayedTime(): Long

    /**
     * Returns the description of the item, if available.
     * For FeedItems, the description needs to be loaded from the database first.
     */
    fun getDescription(): String?

    /**
     * Returns the type of media.
     */
    fun getMediaType(): MediaType

    /**
     * Returns an url to a local file that can be played or null if this file
     * does not exist.
     */
    fun getLocalMediaUrl(): String?

    /**
     * Returns an url to a file that can be streamed by the player or null if
     * this url is not known.
     */
    fun getStreamUrl(): String?

    /**
     * Returns true if a local file that can be played is available. getFileUrl
     * MUST return a non-null string if this method returns true.
     */
    fun localFileAvailable(): Boolean

    /**
     * This method should be called every time playback starts on this object.
     *
     *
     * Position held by this Playable should be set accurately before a call to this method is made.
     */
    fun onPlaybackStart()

    /**
     * This method should be called every time playback pauses or stops on this object,
     * including just before a seeking operation is performed, after which a call to
     * [.onPlaybackStart] should be made. If playback completes, calling this method is not
     * necessary, as long as a call to [.onPlaybackCompleted] is made.
     *
     *
     * Position held by this Playable should be set accurately before a call to this method is made.
     */
    fun onPlaybackPause(context: Context)

    /**
     * This method should be called when playback completes for this object.
     * @param context
     */
    fun onPlaybackCompleted(context: Context)

    /**
     * Returns an integer that must be unique among all Playable classes. The
     * return value is later used by PlaybackPreferences to determine the type of the
     * Playable object that is restored.
     */
    fun getPlayableType(): Int

    fun setChapters(chapters: List<Chapter>)

    fun setPosition(newPosition: Int)

    fun setDuration(newDuration: Int)

    fun setLastPlayedTime(lastPlayedTimestamp: Long)

    /**
     * Returns the location of the image or null if no image is available.
     * This can be the feed item image URL, the local embedded media image path, the feed image URL,
     * or the remote media image URL, depending on what's available.
     */
    fun getImageLocation(): String?

    companion object {
        const val INVALID_TIME: Int = -1
    }
}
