package ac.mdiq.podcini.playback.cast

import ac.mdiq.podcini.storage.model.*
import android.content.ContentResolver
import android.util.Log
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastSession

/**
 * Helper functions for Cast support.
 */
object CastUtils {
    private val TAG: String = CastUtils::class.simpleName ?: "Anonymous"

    const val KEY_MEDIA_ID: String = "ac.mdiq.podcini.cast.MediaId"

    const val KEY_EPISODE_IDENTIFIER: String = "ac.mdiq.podcini.cast.EpisodeId"
    const val KEY_EPISODE_LINK: String = "ac.mdiq.podcini.cast.EpisodeLink"
    const val KEY_STREAM_URL: String = "ac.mdiq.podcini.cast.StreamUrl"
    const val KEY_FEED_URL: String = "ac.mdiq.podcini.cast.FeedUrl"
    const val KEY_FEED_WEBSITE: String = "ac.mdiq.podcini.cast.FeedWebsite"
    const val KEY_EPISODE_NOTES: String = "ac.mdiq.podcini.cast.EpisodeNotes"

    /**
     * The field `Podcini.FormatVersion` specifies which version of MediaMetaData
     * fields we're using. Future implementations should try to be backwards compatible with earlier
     * versions, and earlier versions should be forward compatible until the version indicated by
     * `MAX_VERSION_FORWARD_COMPATIBILITY`. If an update makes the format unreadable for
     * an earlier version, then its version number should be greater than the
     * `MAX_VERSION_FORWARD_COMPATIBILITY` value set on the earlier one, so that it
     * doesn't try to parse the object.
     */
    const val KEY_FORMAT_VERSION: String = "ac.mdiq.podcini.ui.cast.FormatVersion"
    const val FORMAT_VERSION_VALUE: Int = 1
    const val MAX_VERSION_FORWARD_COMPATIBILITY: Int = 9999

    fun isCastable(media: Playable?, castSession: CastSession?): Boolean {
        if (media == null || castSession == null || castSession.castDevice == null) return false
        if (media is EpisodeMedia || media is RemoteMedia) {
            val url = media.getStreamUrl()
            if (url.isNullOrEmpty()) return false
            if (url.startsWith(ContentResolver.SCHEME_CONTENT)) return false /* Local feed */
            return when (media.getMediaType()) {
                MediaType.AUDIO -> castSession.castDevice!!.hasCapability(CastDevice.CAPABILITY_AUDIO_OUT)
                MediaType.VIDEO -> castSession.castDevice!!.hasCapability(CastDevice.CAPABILITY_VIDEO_OUT)
                else -> false
            }
        }
        return false
    }

    /**
     * Converts [MediaInfo] objects into the appropriate implementation of [Playable].
     * @return [Playable] object in a format proper for casting.
     */
    fun makeRemoteMedia(media: MediaInfo): Playable? {
        val metadata = media.metadata
        val version = metadata!!.getInt(KEY_FORMAT_VERSION)
        if (version <= 0 || version > MAX_VERSION_FORWARD_COMPATIBILITY) {
            Log.w(TAG, "MediaInfo object obtained from the cast device is not compatible with this"
                    + "version of Podcini CastUtils, curVer=" + FORMAT_VERSION_VALUE
                    + ", object version=" + version)
            return null
        }
        val imageList = metadata.images
        var imageUrl: String? = null
        if (imageList.isNotEmpty()) imageUrl = imageList[0].url.toString()
        val notes = metadata.getString(KEY_EPISODE_NOTES)
        val result = RemoteMedia(media.contentId,
            metadata.getString(KEY_EPISODE_IDENTIFIER),
            metadata.getString(KEY_FEED_URL),
            metadata.getString(MediaMetadata.KEY_SUBTITLE),
            metadata.getString(MediaMetadata.KEY_TITLE),
            metadata.getString(KEY_EPISODE_LINK),
            metadata.getString(MediaMetadata.KEY_ARTIST),
            imageUrl,
            metadata.getString(KEY_FEED_WEBSITE),
            media.contentType,
            metadata.getDate(MediaMetadata.KEY_RELEASE_DATE)!!.time,
            notes)
        if (result.getDuration() == 0 && media.streamDuration > 0) result.setDuration(media.streamDuration.toInt())
        return result
    }

    /**
     * Compares a [MediaInfo] instance with a [EpisodeMedia] one and evaluates whether they
     * represent the same podcast episode.
     *
     * @param info      the [MediaInfo] object to be compared.
     * @param media     the [EpisodeMedia] object to be compared.
     * @return <true>true</true> if there's a match, `false` otherwise.
     *
     * @see RemoteMedia.equals
     */
    fun matches(info: MediaInfo?, media: EpisodeMedia?): Boolean {
        if (info == null || media == null) return false
        if (info.contentId != media.getStreamUrl()) return false

        val metadata = info.metadata
        val fi = media.episode
        if (fi == null || metadata == null || metadata.getString(KEY_EPISODE_IDENTIFIER) != fi.identifier) return false

        val feed: Feed? = fi.feed
        return feed != null && metadata.getString(KEY_FEED_URL) == feed.downloadUrl
    }

    /**
     * Compares a [MediaInfo] instance with a [RemoteMedia] one and evaluates whether they
     * represent the same podcast episode.
     *
     * @param info      the [MediaInfo] object to be compared.
     * @param media     the [RemoteMedia] object to be compared.
     * @return <true>true</true> if there's a match, `false` otherwise.
     *
     * @see RemoteMedia.equals
     */
    fun matches(info: MediaInfo?, media: RemoteMedia?): Boolean {
        if (info == null || media == null) return false
        if (info.contentId != media.getStreamUrl()) return false

        val metadata = info.metadata
        return (metadata != null && metadata.getString(KEY_EPISODE_IDENTIFIER) == media.getEpisodeIdentifier()
                && metadata.getString(KEY_FEED_URL) == media.feedUrl)
    }

    /**
     * Compares a [MediaInfo] instance with a [Playable] and evaluates whether they
     * represent the same podcast episode. Useful every time we get a MediaInfo from the Cast Device
     * and want to avoid unnecessary conversions.
     *
     * @param info      the [MediaInfo] object to be compared.
     * @param media     the [Playable] object to be compared.
     * @return <true>true</true> if there's a match, `false` otherwise.
     *
     * @see RemoteMedia.equals
     */
    fun matches(info: MediaInfo?, media: Playable?): Boolean {
        if (info == null || media == null) return false
        if (media is RemoteMedia) return matches(info, media as RemoteMedia?)
        return media is EpisodeMedia && matches(info, media as EpisodeMedia?)
    }
}
