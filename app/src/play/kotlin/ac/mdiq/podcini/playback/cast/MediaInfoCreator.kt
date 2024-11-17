package ac.mdiq.podcini.playback.cast

import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.MediaType
import ac.mdiq.podcini.storage.model.RemoteMedia
import ac.mdiq.podcini.util.Logd
import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.common.images.WebImage
import java.util.*

object MediaInfoCreator {
    fun from(media: RemoteMedia): MediaInfo {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC)

        metadata.putString(MediaMetadata.KEY_TITLE, media.getEpisodeTitle())
        metadata.putString(MediaMetadata.KEY_SUBTITLE, media.getFeedTitle())
        if (!media.getImageLocation().isNullOrEmpty()) metadata.addImage(WebImage(Uri.parse(media.getImageLocation())))
        val calendar = Calendar.getInstance()
        calendar.time = media.getPubDate()
        metadata.putDate(MediaMetadata.KEY_RELEASE_DATE, calendar)
        if (media.getFeedAuthor().isNotEmpty()) metadata.putString(MediaMetadata.KEY_ARTIST, media.getFeedAuthor())
        if (!media.feedUrl.isNullOrEmpty()) metadata.putString(CastUtils.KEY_FEED_URL, media.feedUrl)
        if (!media.feedLink.isNullOrEmpty()) metadata.putString(CastUtils.KEY_FEED_WEBSITE, media.feedLink)
        if (!media.getEpisodeIdentifier().isNullOrEmpty()) metadata.putString(CastUtils.KEY_EPISODE_IDENTIFIER, media.getEpisodeIdentifier()!!)
        else {
            if (media.getStreamUrl() != null) metadata.putString(CastUtils.KEY_EPISODE_IDENTIFIER, media.getStreamUrl()!!)
        }
        if (!media.episodeLink.isNullOrEmpty()) metadata.putString(CastUtils.KEY_EPISODE_LINK, media.episodeLink)

        val notes: String? = media.getDescription()
        if (notes != null) metadata.putString(CastUtils.KEY_EPISODE_NOTES, notes)
        // Default id value
        metadata.putInt(CastUtils.KEY_MEDIA_ID, 0)
        metadata.putInt(CastUtils.KEY_FORMAT_VERSION, CastUtils.FORMAT_VERSION_VALUE)
        metadata.putString(CastUtils.KEY_STREAM_URL, media.getStreamUrl()!!)

        val builder = MediaInfo.Builder(media.getStreamUrl()?:"")
            // TODO: test
//            .setContentType(media.getMimeType())
            .setContentType("audio/*")
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(metadata)
        if (media.getDuration() > 0) builder.setStreamDuration(media.getDuration().toLong())
        return builder.build()
    }

    /**
     * Converts [EpisodeMedia] objects into a format suitable for sending to a Cast Device.
     * Before using this method, one should make sure isCastable(Playable) returns
     * `true`. This method should not run on the main thread.
     *
     * @param media The [EpisodeMedia] object to be converted.
     * @return [MediaInfo] object in a format proper for casting.
     */
    fun from(media: EpisodeMedia?): MediaInfo? {
        if (media == null) return null
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC)
        checkNotNull(media.episode) { "item is null" }
        val feedItem = media.episode
        if (feedItem != null) {
            metadata.putString(MediaMetadata.KEY_TITLE, media.getEpisodeTitle())
            val subtitle = media.getFeedTitle()
            metadata.putString(MediaMetadata.KEY_SUBTITLE, subtitle)

            val feed: Feed? = feedItem.feed
            // Manual because cast does not support embedded images
            val url: String = if (feedItem.imageUrl == null && feed != null) feed.imageUrl?:"" else feedItem.imageUrl?:""
            if (url.isNotEmpty()) metadata.addImage(WebImage(Uri.parse(url)))
            val calendar = Calendar.getInstance()
            calendar.time = feedItem.getPubDate()
            metadata.putDate(MediaMetadata.KEY_RELEASE_DATE, calendar)
            if (feed != null) {
                if (!feed.author.isNullOrEmpty()) metadata.putString(MediaMetadata.KEY_ARTIST, feed.author!!)
                if (!feed.downloadUrl.isNullOrEmpty()) metadata.putString(CastUtils.KEY_FEED_URL, feed.downloadUrl!!)
                if (!feed.link.isNullOrEmpty()) metadata.putString(CastUtils.KEY_FEED_WEBSITE, feed.link!!)
            }
            if (!feedItem.identifier.isNullOrEmpty()) metadata.putString(CastUtils.KEY_EPISODE_IDENTIFIER, feedItem.identifier!!)
            else metadata.putString(CastUtils.KEY_EPISODE_IDENTIFIER, media.getStreamUrl() ?: "")
            if (!feedItem.link.isNullOrEmpty()) metadata.putString(CastUtils.KEY_EPISODE_LINK, feedItem.link!!)
        }
        // This field only identifies the id on the device that has the original version.
        // Idea is to perhaps, on a first approach, check if the version on the local DB with the
        // same id matches the remote object, and if not then search for episode and feed identifiers.
        // This at least should make media recognition for a single device much quicker.
        metadata.putInt(CastUtils.KEY_MEDIA_ID, (media.getIdentifier() as Long).toInt())
        // A way to identify different casting media formats in case we change it in the future and
        // senders with different versions share a casting device.
        metadata.putInt(CastUtils.KEY_FORMAT_VERSION, CastUtils.FORMAT_VERSION_VALUE)
        metadata.putString(CastUtils.KEY_STREAM_URL, media.getStreamUrl()!!)

        Logd("MediaInfoCreator", "media.mimeType: ${media.mimeType} ${media.audioUrl}")
        // TODO: these are hardcoded for audio only
//        val builder = MediaInfo.Builder(media.getStreamUrl()!!)
//            .setContentType(media.mimeType)
        var url: String = if (media.getMediaType() == MediaType.AUDIO) media.getStreamUrl() ?: "" else media.audioUrl
        val builder = MediaInfo.Builder(url)
            .setContentType("audio/*")
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(metadata)
        if (media.getDuration() > 0) builder.setStreamDuration(media.getDuration().toLong())
        return builder.build()
    }
}
