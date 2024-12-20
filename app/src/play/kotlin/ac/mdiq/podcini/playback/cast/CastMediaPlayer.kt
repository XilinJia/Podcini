package ac.mdiq.podcini.playback.cast

import ac.mdiq.podcini.net.utils.NetworkUtils.isNetworkRestricted
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.MediaPlayerCallback
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.preferences.UserPreferences.isSkipSilence
import ac.mdiq.podcini.preferences.UserPreferences.prefLowQualityMedia
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.FeedPreferences
import ac.mdiq.podcini.storage.model.MediaType
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.ContentResolver
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaMetadata
import com.google.android.gms.cast.*
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage
import java.io.IOException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Implementation of MediaPlayerBase suitable for remote playback on Cast Devices.
 */
@SuppressLint("VisibleForTests")
class CastMediaPlayer(context: Context, callback: MediaPlayerCallback) : MediaPlayerBase(context, callback) {
    val TAG = this::class.simpleName ?: "Anonymous"

    @Volatile
    private var mediaInfo: MediaInfo? = null
    @Volatile
    private var remoteState: Int
//    private val castContext by lazy { CastContext.getSharedInstance(context) }
    private val remoteMediaClient: RemoteMediaClient?
    private val isBuffering: AtomicBoolean

    private val remoteMediaClientCallback: RemoteMediaClient.Callback = object : RemoteMediaClient.Callback() {
        override fun onMetadataUpdated() {
            super.onMetadataUpdated()
            this@CastMediaPlayer.onStatusUpdated()
        }
        override fun onPreloadStatusUpdated() {
            super.onPreloadStatusUpdated()
            this@CastMediaPlayer.onStatusUpdated()
        }
        override fun onStatusUpdated() {
            super.onStatusUpdated()
            this@CastMediaPlayer.onStatusUpdated()
        }
        override fun onMediaError(mediaError: MediaError) {
            EventFlow.postEvent(FlowEvent.PlayerErrorEvent(mediaError.reason?: "No reason"))
        }
    }

    init {
        if (castContext == null) castContext = CastContext.getSharedInstance(context.applicationContext)
        remoteMediaClient = castContext!!.sessionManager.currentCastSession?.remoteMediaClient
        remoteMediaClient?.registerCallback(remoteMediaClientCallback)
        curMedia = null
        isStreaming = true
        isBuffering = AtomicBoolean(false)
        remoteState = MediaStatus.PLAYER_STATE_UNKNOWN
    }

    private fun setBuffering(buffering: Boolean) {
        when {
            buffering && isBuffering.compareAndSet(false, true) -> EventFlow.postEvent(FlowEvent.BufferUpdateEvent.started())
            !buffering && isBuffering.compareAndSet(true, false) -> EventFlow.postEvent(FlowEvent.BufferUpdateEvent.ended())
        }
    }

    private fun toPlayable(info: MediaInfo?): EpisodeMedia? {
        if (info == null || info.metadata == null) return null
        if (matches(info, curMedia)) return curMedia
        val streamUrl = info.metadata!!.getString(KEY_STREAM_URL)
//        return if (streamUrl == null) makeRemoteMedia(info) else callback.findMedia(streamUrl)
        return if (streamUrl == null) null else callback.findMedia(streamUrl)
    }

    private fun toMediaInfo(playable: EpisodeMedia?): MediaInfo? {
        return when {
            playable == null -> null
            matches(mediaInfo, playable) -> mediaInfo
            else -> from(playable)
        }
    }

    private fun onStatusUpdated() {
        val mediaStatus = remoteMediaClient?.mediaStatus
        if (mediaStatus == null) {
            Logd(TAG, "Received null MediaStatus")
            return
        } else Logd(TAG, "Received remote status/media update. New state=" + mediaStatus.playerState)

        var state = mediaStatus.playerState
        val oldState = remoteState
        mediaInfo = mediaStatus.mediaInfo
        val mediaChanged = !matches(mediaInfo, curMedia)
        var stateChanged = state != oldState
        if (!mediaChanged && !stateChanged) {
            Logd(TAG, "Both media and state haven't changed, so nothing to do")
            return
        }
        val currentMedia = if (mediaChanged) toPlayable(mediaInfo) else curMedia
        val oldMedia = curMedia
        val position = mediaStatus.streamPosition.toInt()
        // check for incompatible states
        if ((state == MediaStatus.PLAYER_STATE_PLAYING || state == MediaStatus.PLAYER_STATE_PAUSED) && currentMedia == null) {
            Log.w(TAG, "onStatusUpdated returned playing or pausing state, but with no media")
            state = MediaStatus.PLAYER_STATE_UNKNOWN
            stateChanged = oldState != MediaStatus.PLAYER_STATE_UNKNOWN
        }
        if (stateChanged) remoteState = state
        if (mediaChanged && stateChanged && oldState == MediaStatus.PLAYER_STATE_PLAYING && state != MediaStatus.PLAYER_STATE_IDLE) {
            callback.onPlaybackPause(null, EpisodeMedia.INVALID_TIME)
            // We don't want setPlayerStatus to handle the onPlaybackPause callback
            setPlayerStatus(PlayerStatus.INDETERMINATE, currentMedia)
        }
        Log.w(TAG, "onStatusUpdated state: $state")
        setBuffering(state == MediaStatus.PLAYER_STATE_BUFFERING)
        when (state) {
            MediaStatus.PLAYER_STATE_PLAYING -> {
                if (!stateChanged) {
                    //These steps are necessary because they won't be performed by setPlayerStatus()
                    if (position >= 0) currentMedia?.setPosition(position)
                    currentMedia?.onPlaybackStart()
                }
                setPlayerStatus(PlayerStatus.PLAYING, currentMedia, position)
            }
            MediaStatus.PLAYER_STATE_PAUSED -> setPlayerStatus(PlayerStatus.PAUSED, currentMedia, position)
            MediaStatus.PLAYER_STATE_LOADING -> { Logd(TAG, "Remote player loading") }
            MediaStatus.PLAYER_STATE_BUFFERING -> setPlayerStatus(
                if ((mediaChanged || status == PlayerStatus.PREPARING)) PlayerStatus.PREPARING else PlayerStatus.SEEKING,
                currentMedia, currentMedia?.position ?: EpisodeMedia.INVALID_TIME)
            MediaStatus.PLAYER_STATE_IDLE -> {
                val reason = mediaStatus.idleReason
                when (reason) {
                    MediaStatus.IDLE_REASON_CANCELED -> {
                        // Essentially means stopped at the request of a user
                        callback.onPlaybackEnded(null, true)
                        setPlayerStatus(PlayerStatus.STOPPED, currentMedia)
                        if (oldMedia != null) {
                            if (position >= 0) oldMedia.setPosition(position)
                            callback.onPostPlayback(oldMedia, ended = false, skipped = false, playingNext = false)
                        }
                        // onPlaybackEnded pretty much takes care of updating the UI
                        return
                    }
                    MediaStatus.IDLE_REASON_INTERRUPTED -> {
                        // Means that a request to load a different media was sent
                        // Not sure if currentMedia already reflects the to be loaded one
                        if (mediaChanged && oldState == MediaStatus.PLAYER_STATE_PLAYING) {
                            callback.onPlaybackPause(null, EpisodeMedia.INVALID_TIME)
                            setPlayerStatus(PlayerStatus.INDETERMINATE, currentMedia)
                        }
                        setPlayerStatus(PlayerStatus.PREPARING, currentMedia)
                    }
                    // This probably only happens when we connected but no command has been sent yet.
                    MediaStatus.IDLE_REASON_NONE -> setPlayerStatus(PlayerStatus.INITIALIZED, currentMedia)
                    MediaStatus.IDLE_REASON_FINISHED -> {
                        // This is our onCompletionListener...
                        if (mediaChanged && currentMedia != null) curMedia = currentMedia
                        endPlayback(true, wasSkipped = false, shouldContinue = true, toStoppedState = true)
                        return
                    }
                    MediaStatus.IDLE_REASON_ERROR -> {
                        Log.w(TAG, "Got an error status from the Chromecast. Skipping, if possible, to the next episode...")
                        EventFlow.postEvent(FlowEvent.PlayerErrorEvent("Chromecast error code 1"))
                        endPlayback(false, wasSkipped = false, shouldContinue = true, toStoppedState = true)
                        return
                    }
                    else -> return
                }
            }
            MediaStatus.PLAYER_STATE_UNKNOWN -> if (status != PlayerStatus.INDETERMINATE || curMedia !== currentMedia) setPlayerStatus(PlayerStatus.INDETERMINATE, currentMedia)
            else -> Log.w(TAG, "Remote media state undetermined!")
        }
        if (mediaChanged) {
            callback.onMediaChanged(true)
            if (oldMedia != null) callback.onPostPlayback(oldMedia, ended = false, skipped = false, playingNext = currentMedia != null)
        }
    }

    /**
     * Internal implementation of playMediaObject. This method has an additional parameter that
     * allows the caller to force a media player reset even if
     * the given playable parameter is the same object as the currently playing media.
     * @see .playMediaObject
     */
    override fun playMediaObject(playable: EpisodeMedia, streaming: Boolean, startWhenPrepared: Boolean, prepareImmediately: Boolean, forceReset: Boolean) {
       Logd(TAG, "playMediaObject")
        if (!isCastable(playable, castContext?.sessionManager?.currentCastSession)) {
            Logd(TAG, "media provided is not compatible with cast device")
            EventFlow.postEvent(FlowEvent.PlayerErrorEvent("Media not compatible with cast device"))
            var nextPlayable: EpisodeMedia? = playable
            do { nextPlayable = callback.getNextInQueue(nextPlayable)
            } while (nextPlayable != null && !isCastable(nextPlayable, castContext?.sessionManager?.currentCastSession))
            if (nextPlayable != null) playMediaObject(nextPlayable, streaming, startWhenPrepared, prepareImmediately, forceReset)
            return
        }

        if (curMedia != null) {
            if (!forceReset && curMedia!!.id == prevMedia?.id && status == PlayerStatus.PLAYING) {
                // episode is already playing -> ignore method call
                Logd(TAG, "Method call to playMediaObject was ignored: media file already playing.")
                return
            } else {
                if (curMedia?.id != prevMedia?.id) {
                    prevMedia = curMedia
                    callback.onPostPlayback(prevMedia, false, false, true)
                }
                setPlayerStatus(PlayerStatus.INDETERMINATE, null)
            }
        }

        curMedia = playable
        this.mediaType = curMedia!!.getMediaType()
        this.startWhenPrepared.set(startWhenPrepared)
        setPlayerStatus(PlayerStatus.INITIALIZING, curMedia)

        val metadata = buildMetadata(curMedia!!)
        try {
            callback.ensureMediaInfoLoaded(curMedia!!)
            // TODO: test
            callback.onMediaChanged(true)
            setPlaybackParams(getCurrentPlaybackSpeed(curMedia), isSkipSilence)
            when {
                streaming -> {
                    val streamurl = curMedia!!.downloadUrl
                    if (streamurl != null) {
                        val media = curMedia
                        if (media != null) {
                            mediaItem = null
                            mediaSource = null
                            setDataSource(metadata, media)
                        } else setDataSource(metadata, streamurl, null, null)
                    }
                }
                else -> {}
            }
            mediaInfo = toMediaInfo(playable)
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            if (uiModeManager.currentModeType != Configuration.UI_MODE_TYPE_CAR) setPlayerStatus(PlayerStatus.INITIALIZED, curMedia)
            if (prepareImmediately) prepare()
        } catch (e: IOException) {
            e.printStackTrace()
            setPlayerStatus(PlayerStatus.ERROR, null)
            EventFlow.postStickyEvent(FlowEvent.PlayerErrorEvent(e.localizedMessage ?: ""))
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            setPlayerStatus(PlayerStatus.ERROR, null)
            EventFlow.postStickyEvent(FlowEvent.PlayerErrorEvent(e.localizedMessage ?: ""))
        } finally { }
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override fun setDataSource(metadata: MediaMetadata, media: EpisodeMedia) {
        Logd(TAG, "setDataSource1 called")
        if (media.episode?.feed?.type == Feed.FeedType.YOUTUBE.name) {
            Logd(TAG, "setDataSource1 setting for YouTube source")
            try {
                val streamInfo = media.episode!!.streamInfo ?: return
                if (!media.forceVideo && media.episode?.feed?.preferences?.videoModePolicy == VideoMode.AUDIO_ONLY) {
                    val audioStreamsList = getFilteredAudioStreams(streamInfo.audioStreams)
                    Logd(TAG, "setDataSource1 audioStreamsList ${audioStreamsList.size}")
                    val audioIndex = if (isNetworkRestricted && prefLowQualityMedia && media.episode?.feed?.preferences?.audioQualitySetting == FeedPreferences.AVQuality.GLOBAL) 0 else {
                        when (media.episode?.feed?.preferences?.audioQualitySetting) {
                            FeedPreferences.AVQuality.LOW -> 0
                            FeedPreferences.AVQuality.MEDIUM -> audioStreamsList.size / 2
                            FeedPreferences.AVQuality.HIGH -> audioStreamsList.size - 1
                            else -> audioStreamsList.size - 1
                        }
                    }
                    val audioStream = audioStreamsList[audioIndex]
                    Logd(TAG, "setDataSource1 use audio quality: ${audioStream.bitrate} forceVideo: ${media.forceVideo}")
                    media.effectUrl = audioStream.content
                    media.effectMimeType = "audio/*"
                } else {
                    val videoStreamsList = getSortedStreamVideosList(streamInfo.videoStreams, listOf(), true, false)
                    Logd(TAG, "setDataSource1 videoStreamsList ${videoStreamsList.size}")
                    val videoIndex = if (isNetworkRestricted && prefLowQualityMedia && media.episode?.feed?.preferences?.videoQualitySetting == FeedPreferences.AVQuality.GLOBAL) 0 else {
                        when (media.episode?.feed?.preferences?.videoQualitySetting) {
                            FeedPreferences.AVQuality.LOW -> 0
                            FeedPreferences.AVQuality.MEDIUM -> videoStreamsList.size / 2
                            FeedPreferences.AVQuality.HIGH -> videoStreamsList.size - 1
                            else -> 0
                        }
                    }
                    val videoStream = videoStreamsList[videoIndex]
                    Logd(TAG, "setDataSource1 use video quality: ${videoStream.resolution}")
                    media.effectUrl = videoStream.content
                    media.effectMimeType = "video/*"
                }
            } catch (throwable: Throwable) { Log.e(TAG, "setDataSource1 error: ${throwable.message}") }
        } else {
            media.effectUrl = media.downloadUrl ?: ""
            media.effectMimeType = media.mimeType ?: ""
        }
    }

    override fun resume() {
        val newPosition = calculatePositionWithRewind(curMedia!!.position, curMedia!!.lastPlayedTime)
        seekTo(newPosition)
        remoteMediaClient?.play()
    }

    override fun pause(reinit: Boolean) {
        remoteMediaClient?.pause()
    }

    override fun prepare() {
        if (status == PlayerStatus.INITIALIZED) {
            Logd(TAG, "Preparing media player $mediaInfo")
            setPlayerStatus(PlayerStatus.PREPARING, curMedia)
            var position = curMedia!!.position
            if (position > 0) position = calculatePositionWithRewind(position, curMedia!!.lastPlayedTime)
            remoteMediaClient?.load(MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(startWhenPrepared.get())
                .setCurrentTime(position.toLong()).build())
        }
    }

    override fun reinit() {
        Logd(TAG, "reinit() called")
        if (curMedia != null) playMediaObject(curMedia!!, streaming = false, startWhenPrepared = startWhenPrepared.get(), prepareImmediately = false, true)
        else Logd(TAG, "Call to reinit was ignored: media was null")
    }

    override fun seekTo(t: Int) {
//        Exception("Seeking to $t").printStackTrace()
        remoteMediaClient?.seek(MediaSeekOptions.Builder().setPosition(t.toLong()).setResumeState(MediaSeekOptions.RESUME_STATE_PLAY).build())?.addStatusListener {
            if (it.isSuccess) {
                Logd(TAG, "seekTo Seek succeeded to position $t ms")
                if (curMedia != null) EventFlow.postEvent(FlowEvent.PlaybackPositionEvent(curMedia, t, curMedia!!.duration))
            } else Log.e(TAG, "Seek failed")
        }
    }

    override fun getDuration(): Int {
//        if (curMedia != null && remoteMediaClient?.currentItem?.media?.entity != curMedia?.id.toString()) return curMedia!!.getDuration()
        var retVal = remoteMediaClient?.streamDuration?.toInt() ?: EpisodeMedia.INVALID_TIME
        if (retVal == EpisodeMedia.INVALID_TIME && curMedia != null && curMedia!!.duration > 0) retVal = curMedia!!.duration
        return retVal
    }

    override fun getPosition(): Int {
//        Logd(TAG, "getPosition: $status ${remoteMediaClient?.approximateStreamPosition} ${curMedia?.getPosition()} ${remoteMediaClient?.currentItem?.media?.entity} ${curMedia?.id.toString()} ${curMedia?.getEpisodeTitle()}")
        var retVal = remoteMediaClient?.approximateStreamPosition?.toInt() ?: EpisodeMedia.INVALID_TIME
        if (retVal <= 0 && curMedia != null && curMedia!!.position >= 0) retVal = curMedia!!.position
        return retVal
    }

    override fun setPlaybackParams(speed: Float, skipSilence: Boolean) {
        val playbackRate = max(MediaLoadOptions.PLAYBACK_RATE_MIN, min(MediaLoadOptions.PLAYBACK_RATE_MAX, speed.toDouble())).toFloat().toDouble()
        remoteMediaClient?.setPlaybackRate(playbackRate)
    }

    override fun getPlaybackSpeed(): Float {
        return remoteMediaClient?.mediaStatus?.playbackRate?.toFloat() ?: 1.0f
    }

    override fun setVolume(volumeLeft: Float, volumeRight: Float) {
        Logd(TAG, "Setting the Stream volume on Remote Media Player")
        remoteMediaClient?.setStreamVolume(volumeLeft.toDouble())
    }

    override fun shutdown() {
        remoteMediaClient?.stop()
        remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
    }

    override fun setPlayable(playable: EpisodeMedia?) {
        if (playable != null && playable !== curMedia) {
            curMedia = playable
            mediaInfo = toMediaInfo(playable)
        }
    }

    override fun endPlayback(hasEnded: Boolean, wasSkipped: Boolean, shouldContinue: Boolean, toStoppedState: Boolean) {
        Logd(TAG, "endPlayback() called")
        val isPlaying = status == PlayerStatus.PLAYING
        if (status != PlayerStatus.INDETERMINATE) setPlayerStatus(PlayerStatus.INDETERMINATE, curMedia)

        if (curMedia != null && wasSkipped) {
            // current position only really matters when we skip
            val position = getPosition()
            if (position >= 0) curMedia!!.setPosition(position)
        }
        val currentMedia = curMedia
        var nextMedia: EpisodeMedia? = null
        if (shouldContinue) {
            nextMedia = callback.getNextInQueue(currentMedia)
            val playNextEpisode = isPlaying && nextMedia != null
            when {
                playNextEpisode -> Logd(TAG, "Playback of next episode will start immediately.")
                nextMedia == null -> Logd(TAG, "No more episodes available to play")
                else -> Logd(TAG, "Loading next episode, but not playing automatically.")
            }
            if (nextMedia != null) {
                callback.onPlaybackEnded(nextMedia.getMediaType(), !playNextEpisode)
                // setting media to null signals to playMediaObject() that we're taking care of post-playback processing
                curMedia = null
                playMediaObject(nextMedia, streaming = true, startWhenPrepared = playNextEpisode, prepareImmediately = playNextEpisode, forceReset = false)
            }
        }
        when {
            shouldContinue || toStoppedState -> {
                if (nextMedia == null) {
                    remoteMediaClient?.stop()
                    // Otherwise we rely on the chromecast callback to tell us the playback has stopped.
                    callback.onPostPlayback(currentMedia, hasEnded, wasSkipped, false)
                } else callback.onPostPlayback(currentMedia, hasEnded, wasSkipped, true)
            }
            isPlaying -> callback.onPlaybackPause(currentMedia, currentMedia?.position ?: EpisodeMedia.INVALID_TIME)
        }
    }

    override fun isCasting(): Boolean {
        return true
    }

    companion object {
        /**
         * Converts [EpisodeMedia] objects into a format suitable for sending to a Cast Device.
         * Before using this method, one should make sure isCastable(EpisodeMedia) returns
         * `true`. This method should not run on the main thread.
         *
         * @param media The [EpisodeMedia] object to be converted.
         * @return [MediaInfo] object in a format proper for casting.
         */
        fun from(media: EpisodeMedia?): MediaInfo? {
            if (media == null) return null
            val metadata = MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_GENERIC)
            checkNotNull(media.episode) { "item is null" }
            val feedItem = media.episode
            if (feedItem != null) {
                metadata.putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, media.getEpisodeTitle())
                val subtitle = media.episode?.feed?.title?:""
                metadata.putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, subtitle)

                val feed: Feed? = feedItem.feed
                // Manual because cast does not support embedded images
                val url: String = if (feedItem.imageUrl == null && feed != null) feed.imageUrl?:"" else feedItem.imageUrl?:""
                if (url.isNotEmpty()) metadata.addImage(WebImage(Uri.parse(url)))
                val calendar = Calendar.getInstance()
                calendar.time = feedItem.getPubDate()
                metadata.putDate(com.google.android.gms.cast.MediaMetadata.KEY_RELEASE_DATE, calendar)
                if (feed != null) {
                    if (!feed.author.isNullOrEmpty()) metadata.putString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST, feed.author!!)
                    if (!feed.downloadUrl.isNullOrEmpty()) metadata.putString(KEY_FEED_URL, feed.downloadUrl!!)
                    if (!feed.link.isNullOrEmpty()) metadata.putString(KEY_FEED_WEBSITE, feed.link!!)
                }
                if (!feedItem.identifier.isNullOrEmpty()) metadata.putString(KEY_EPISODE_IDENTIFIER, feedItem.identifier!!)
                else metadata.putString(KEY_EPISODE_IDENTIFIER, media.downloadUrl ?: "")
                if (!feedItem.link.isNullOrEmpty()) metadata.putString(KEY_EPISODE_LINK, feedItem.link!!)
            }
            // This field only identifies the id on the device that has the original version.
            // Idea is to perhaps, on a first approach, check if the version on the local DB with the
            // same id matches the remote object, and if not then search for episode and feed identifiers.
            // This at least should make media recognition for a single device much quicker.
            metadata.putInt(KEY_MEDIA_ID, media.id.toInt())
            // A way to identify different casting media formats in case we change it in the future and
            // senders with different versions share a casting device.
            metadata.putInt(KEY_FORMAT_VERSION, FORMAT_VERSION_VALUE)
            metadata.putString(KEY_STREAM_URL, media.downloadUrl!!)

            Logd("MediaInfoCreator", "media: ${media.id} ${feedItem?.title}")
            Logd("MediaInfoCreator", "url: ${media.getMediaType()} $media.effectUrl")
            val builder = MediaInfo.Builder(media.effectUrl)
                .setEntity(media.id.toString())
                .setContentType(media.effectMimeType)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(metadata)
            if (media.duration > 0) builder.setStreamDuration(media.duration.toLong())
            return builder.build()
        }

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

        fun isCastable(media: EpisodeMedia?, castSession: CastSession?): Boolean {
            if (media == null || castSession == null || castSession.castDevice == null) return false
//        if (media is EpisodeMedia || media is RemoteMedia) {
            val url = media.downloadUrl
            if (url.isNullOrEmpty()) return false
            if (url.startsWith(ContentResolver.SCHEME_CONTENT)) return false /* Local feed */
            return when (media.getMediaType()) {
                MediaType.AUDIO -> castSession.castDevice!!.hasCapability(CastDevice.CAPABILITY_AUDIO_OUT)
                MediaType.VIDEO -> {
                    if (media.episode?.feed?.preferences?.videoModePolicy ==  VideoMode.AUDIO_ONLY)
                        castSession.castDevice!!.hasCapability(CastDevice.CAPABILITY_AUDIO_OUT)
                    else castSession.castDevice!!.hasCapability(CastDevice.CAPABILITY_VIDEO_OUT)
                }
                else -> false
            }
            return false
        }
        
        /**
         * Compares a [MediaInfo] instance with a [EpisodeMedia] one and evaluates whether they
         * represent the same podcast episode.
         * @param info      the [MediaInfo] object to be compared.
         * @param media     the [EpisodeMedia] object to be compared.
         * @return <true>true</true> if there's a match, `false` otherwise.
         */
        fun matches(info: MediaInfo?, media: EpisodeMedia?): Boolean {
            if (info == null || media == null) return false
            if (info.contentId != media.downloadUrl) return false

            val metadata = info.metadata
            val fi = media.episode
            if (fi == null || metadata == null || metadata.getString(KEY_EPISODE_IDENTIFIER) != fi.identifier) return false

            val feed: Feed? = fi.feed
            return feed != null && metadata.getString(KEY_FEED_URL) == feed.downloadUrl
        }

        @SuppressLint("StaticFieldLeak")
        var castContext:  CastContext? = null
        fun getInstanceIfConnected(context: Context, callback: MediaPlayerCallback): MediaPlayerBase? {
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) return null
            try {
                if (castContext == null) castContext = CastContext.getSharedInstance(context.applicationContext)
                if (castContext?.castState == CastState.CONNECTED) return CastMediaPlayer(context, callback) } catch (e: Exception) { e.printStackTrace() }
            return null
        }
    }
}
