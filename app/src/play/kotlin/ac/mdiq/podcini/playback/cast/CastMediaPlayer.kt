package ac.mdiq.podcini.playback.cast

import ac.mdiq.podcini.net.utils.NetworkUtils.isNetworkRestricted
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.MediaPlayerCallback
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.preferences.UserPreferences.isSkipSilence
import ac.mdiq.podcini.preferences.UserPreferences.prefLowQualityMedia
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.media3.common.MediaMetadata
import com.google.android.gms.cast.*
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
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
    private val castContext = CastContext.getSharedInstance(context)
    private val remoteMediaClient = castContext.sessionManager.currentCastSession?.remoteMediaClient
    private val isBuffering: AtomicBoolean

    private val remoteMediaClientCallback: RemoteMediaClient.Callback = object : RemoteMediaClient.Callback() {
        override fun onMetadataUpdated() {
            super.onMetadataUpdated()
            onRemoteMediaPlayerStatusUpdated()
        }
        override fun onPreloadStatusUpdated() {
            super.onPreloadStatusUpdated()
            onRemoteMediaPlayerStatusUpdated()
        }
        override fun onStatusUpdated() {
            super.onStatusUpdated()
            onRemoteMediaPlayerStatusUpdated()
        }
        override fun onMediaError(mediaError: MediaError) {
            EventFlow.postEvent(FlowEvent.PlayerErrorEvent(mediaError.reason?: "No reason"))
        }
    }

    init {
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

    private fun toPlayable(info: MediaInfo?): Playable? {
        if (info == null || info.metadata == null) return null
        if (CastUtils.matches(info, curMedia)) return curMedia
        val streamUrl = info.metadata!!.getString(CastUtils.KEY_STREAM_URL)
        return if (streamUrl == null) CastUtils.makeRemoteMedia(info) else callback.findMedia(streamUrl)
    }

    private fun toMediaInfo(playable: Playable?): MediaInfo? {
        return when {
            playable == null -> null
            CastUtils.matches(mediaInfo, playable) -> mediaInfo
            playable is EpisodeMedia -> MediaInfoCreator.from(playable)
            playable is RemoteMedia -> MediaInfoCreator.from(playable)
//            playable is RemoteMedia -> MediaInfoCreator.from(playable)
            else -> null
        }
    }

    private fun onRemoteMediaPlayerStatusUpdated() {
        val mediaStatus = remoteMediaClient?.mediaStatus
        if (mediaStatus == null) {
            Logd(TAG, "Received null MediaStatus")
            return
        } else Logd(TAG, "Received remote status/media update. New state=" + mediaStatus.playerState)

        var state = mediaStatus.playerState
        val oldState = remoteState
        mediaInfo = mediaStatus.mediaInfo
        val mediaChanged = !CastUtils.matches(mediaInfo, curMedia)
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
            Log.w(TAG, "RemoteMediaPlayer returned playing or pausing state, but with no media")
            state = MediaStatus.PLAYER_STATE_UNKNOWN
            stateChanged = oldState != MediaStatus.PLAYER_STATE_UNKNOWN
        }
        if (stateChanged) remoteState = state
        if (mediaChanged && stateChanged && oldState == MediaStatus.PLAYER_STATE_PLAYING && state != MediaStatus.PLAYER_STATE_IDLE) {
            callback.onPlaybackPause(null, Playable.INVALID_TIME)
            // We don't want setPlayerStatus to handle the onPlaybackPause callback
            setPlayerStatus(PlayerStatus.INDETERMINATE, currentMedia)
        }
        Log.w(TAG, "RemoteMediaPlayer state: $state")
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
                currentMedia, currentMedia?.getPosition() ?: Playable.INVALID_TIME)
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
                            callback.onPlaybackPause(null, Playable.INVALID_TIME)
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
            MediaStatus.PLAYER_STATE_UNKNOWN -> if (status != PlayerStatus.INDETERMINATE || curMedia !== currentMedia)
                setPlayerStatus(PlayerStatus.INDETERMINATE, currentMedia)
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
    override fun playMediaObject(playable: Playable, streaming: Boolean, startWhenPrepared: Boolean, prepareImmediately: Boolean, forceReset: Boolean) {
       Logd(TAG, "playMediaObject")
        if (!CastUtils.isCastable(playable, castContext.sessionManager.currentCastSession)) {
            Logd(TAG, "media provided is not compatible with cast device")
            EventFlow.postEvent(FlowEvent.PlayerErrorEvent("Media not compatible with cast device"))
            var nextPlayable: Playable? = playable
            do { nextPlayable = callback.getNextInQueue(nextPlayable)
            } while (nextPlayable != null && !CastUtils.isCastable(nextPlayable, castContext.sessionManager.currentCastSession))
            if (nextPlayable != null) playMediaObject(nextPlayable, streaming, startWhenPrepared, prepareImmediately, forceReset)
            return
        }

        if (curMedia != null) {
            if (!forceReset && curMedia!!.getIdentifier() == prevMedia?.getIdentifier() && status == PlayerStatus.PLAYING) {
                // episode is already playing -> ignore method call
                Logd(TAG, "Method call to playMediaObject was ignored: media file already playing.")
                return
            } else {
                if (curMedia?.getIdentifier() != prevMedia?.getIdentifier()) {
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
                    val streamurl = curMedia!!.getStreamUrl()
                    if (streamurl != null) {
                        val media = curMedia
                        if (media is EpisodeMedia) {
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
                media.audioUrl = audioStream.content
            } catch (throwable: Throwable) { Log.e(TAG, "setDataSource1 error: ${throwable.message}") }
        }
    }

    override fun resume() {
        val newPosition = calculatePositionWithRewind(curMedia!!.getPosition(), curMedia!!.getLastPlayedTime())
        seekTo(newPosition)
        remoteMediaClient?.play()
    }

    override fun pause(abandonFocus: Boolean, reinit: Boolean) {
        remoteMediaClient?.pause()
    }

    override fun prepare() {
        if (status == PlayerStatus.INITIALIZED) {
            Logd(TAG, "Preparing media player $mediaInfo")
            setPlayerStatus(PlayerStatus.PREPARING, curMedia)
            var position = curMedia!!.getPosition()
            if (position > 0) position = calculatePositionWithRewind(position, curMedia!!.getLastPlayedTime())
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
                if (curMedia != null) EventFlow.postEvent(FlowEvent.PlaybackPositionEvent(curMedia, t, curMedia!!.getDuration()))
            } else Log.e(TAG, "Seek failed")
        }
    }

    override fun getDuration(): Int {
//        if (curMedia != null && remoteMediaClient?.currentItem?.media?.entity != curMedia?.getIdentifier().toString()) return curMedia!!.getDuration()
        var retVal = remoteMediaClient?.streamDuration?.toInt() ?: Playable.INVALID_TIME
        if (retVal == Playable.INVALID_TIME && curMedia != null && curMedia!!.getDuration() > 0) retVal = curMedia!!.getDuration()
        return retVal
    }

    override fun getPosition(): Int {
//        Logd(TAG, "getPosition: $status ${remoteMediaClient?.approximateStreamPosition} ${curMedia?.getPosition()} ${remoteMediaClient?.currentItem?.media?.entity} ${curMedia?.getIdentifier().toString()} ${curMedia?.getEpisodeTitle()}")
        var retVal = remoteMediaClient?.approximateStreamPosition?.toInt() ?: Playable.INVALID_TIME
        if (retVal <= 0 && curMedia != null && curMedia!!.getPosition() >= 0) retVal = curMedia!!.getPosition()
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

    override fun setPlayable(playable: Playable?) {
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
        var nextMedia: Playable? = null
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
            isPlaying -> callback.onPlaybackPause(currentMedia, currentMedia?.getPosition() ?: Playable.INVALID_TIME)
        }
    }

    override fun isCasting(): Boolean {
        return true
    }

    companion object {
        fun getInstanceIfConnected(context: Context, callback: MediaPlayerCallback): MediaPlayerBase? {
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) return null
            try { if (CastContext.getSharedInstance(context).castState == CastState.CONNECTED) return CastMediaPlayer(context, callback) } catch (e: Exception) { e.printStackTrace() }
            return null
        }
    }
}
