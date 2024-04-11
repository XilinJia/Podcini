package ac.mdiq.podcini.playback.cast

import ac.mdiq.podcini.util.event.PlayerErrorEvent
import ac.mdiq.podcini.util.event.playback.BufferUpdateEvent
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.storage.model.playback.MediaType
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.storage.model.playback.RemoteMedia
import ac.mdiq.podcini.playback.base.PlaybackServiceMediaPlayer
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.base.RewindAfterPauseUtils.calculatePositionWithRewind
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.util.Pair
import android.view.SurfaceHolder
import com.google.android.gms.cast.*
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min

/**
 * Implementation of PlaybackServiceMediaPlayer suitable for remote playback on Cast Devices.
 */
@SuppressLint("VisibleForTests")
class CastPsmp(context: Context, callback: PSMPCallback) : PlaybackServiceMediaPlayer(context, callback) {
    @Volatile
    private var media: Playable?

    @Volatile
    private var mediaType: MediaType?

    @Volatile
    private var remoteMedia: MediaInfo? = null

    @Volatile
    private var remoteState: Int
    private val castContext = CastContext.getSharedInstance(context)
    private val remoteMediaClient = castContext.sessionManager.currentCastSession!!.remoteMediaClient

    private val isBuffering: AtomicBoolean

    private val startWhenPrepared: AtomicBoolean

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
            EventBus.getDefault().post(PlayerErrorEvent(mediaError.reason!!))
        }
    }

    init {
        remoteMediaClient!!.registerCallback(remoteMediaClientCallback)
        media = null
        mediaType = null
        startWhenPrepared = AtomicBoolean(false)
        isBuffering = AtomicBoolean(false)
        remoteState = MediaStatus.PLAYER_STATE_UNKNOWN
    }

    private fun setBuffering(buffering: Boolean) {
        if (buffering && isBuffering.compareAndSet(false, true)) {
            EventBus.getDefault().post(BufferUpdateEvent.started())
        } else if (!buffering && isBuffering.compareAndSet(true, false)) {
            EventBus.getDefault().post(BufferUpdateEvent.ended())
        }
    }

    private fun localVersion(info: MediaInfo?): Playable? {
        if (info == null || info.metadata == null) {
            return null
        }
        if (CastUtils.matches(info, media)) {
            return media
        }
        val streamUrl = info.metadata!!.getString(CastUtils.KEY_STREAM_URL)
        return if (streamUrl == null) CastUtils.makeRemoteMedia(info) else callback.findMedia(streamUrl)
    }

    private fun remoteVersion(playable: Playable?): MediaInfo? {
        if (playable == null) {
            return null
        }
        if (CastUtils.matches(remoteMedia, playable)) {
            return remoteMedia
        }
        if (playable is FeedMedia) {
            return MediaInfoCreator.from(playable)
        }
        if (playable is RemoteMedia) {
            return MediaInfoCreator.from(playable)
        }
        return null
    }

    private fun onRemoteMediaPlayerStatusUpdated() {
        val status = remoteMediaClient!!.mediaStatus
        if (status == null) {
            Log.d(TAG, "Received null MediaStatus")
            return
        } else {
            Log.d(TAG, "Received remote status/media update. New state=" + status.playerState)
        }
        var state = status.playerState
        val oldState = remoteState
        remoteMedia = status.mediaInfo
        val mediaChanged = !CastUtils.matches(remoteMedia, media)
        var stateChanged = state != oldState
        if (!mediaChanged && !stateChanged) {
            Log.d(TAG, "Both media and state haven't changed, so nothing to do")
            return
        }
        val currentMedia = if (mediaChanged) localVersion(remoteMedia) else media
        val oldMedia = media
        val position = status.streamPosition.toInt()
        // check for incompatible states
        if ((state == MediaStatus.PLAYER_STATE_PLAYING || state == MediaStatus.PLAYER_STATE_PAUSED)
                && currentMedia == null) {
            Log.w(TAG, "RemoteMediaPlayer returned playing or pausing state, but with no media")
            state = MediaStatus.PLAYER_STATE_UNKNOWN
            stateChanged = oldState != MediaStatus.PLAYER_STATE_UNKNOWN
        }

        if (stateChanged) {
            remoteState = state
        }

        if (mediaChanged && stateChanged && oldState == MediaStatus.PLAYER_STATE_PLAYING && state != MediaStatus.PLAYER_STATE_IDLE) {
            callback.onPlaybackPause(null, Playable.INVALID_TIME)
            // We don't want setPlayerStatus to handle the onPlaybackPause callback
            setPlayerStatus(PlayerStatus.INDETERMINATE, currentMedia)
        }

        setBuffering(state == MediaStatus.PLAYER_STATE_BUFFERING)

        when (state) {
            MediaStatus.PLAYER_STATE_PLAYING -> {
                if (!stateChanged) {
                    //These steps are necessary because they won't be performed by setPlayerStatus()
                    if (position >= 0) {
                        currentMedia!!.setPosition(position)
                    }
                    currentMedia!!.onPlaybackStart()
                }
                setPlayerStatus(PlayerStatus.PLAYING, currentMedia, position)
            }
            MediaStatus.PLAYER_STATE_PAUSED -> setPlayerStatus(PlayerStatus.PAUSED, currentMedia, position)
            MediaStatus.PLAYER_STATE_BUFFERING -> setPlayerStatus(if ((mediaChanged || playerStatus == PlayerStatus.PREPARING)
            ) PlayerStatus.PREPARING else PlayerStatus.SEEKING, currentMedia,
                currentMedia?.getPosition() ?: Playable.INVALID_TIME)
            MediaStatus.PLAYER_STATE_IDLE -> {
                val reason = status.idleReason
                when (reason) {
                    MediaStatus.IDLE_REASON_CANCELED -> {
                        // Essentially means stopped at the request of a user
                        callback.onPlaybackEnded(null, true)
                        setPlayerStatus(PlayerStatus.STOPPED, currentMedia)
                        if (oldMedia != null) {
                            if (position >= 0) {
                                oldMedia.setPosition(position)
                            }
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
                    MediaStatus.IDLE_REASON_NONE ->                         // This probably only happens when we connected but no command has been sent yet.
                        setPlayerStatus(PlayerStatus.INITIALIZED, currentMedia)
                    MediaStatus.IDLE_REASON_FINISHED -> {
                        // This is our onCompletionListener...
                        if (mediaChanged && currentMedia != null) {
                            media = currentMedia
                        }
                        endPlayback(true, wasSkipped = false, shouldContinue = true, toStoppedState = true)
                        return
                    }
                    MediaStatus.IDLE_REASON_ERROR -> {
                        Log.w(TAG, "Got an error status from the Chromecast. "
                                + "Skipping, if possible, to the next episode...")
                        EventBus.getDefault().post(PlayerErrorEvent("Chromecast error code 1"))
                        endPlayback(false, wasSkipped = false, shouldContinue = true, toStoppedState = true)
                        return
                    }
                    else -> return
                }
            }
            MediaStatus.PLAYER_STATE_UNKNOWN -> if (playerStatus != PlayerStatus.INDETERMINATE || media !== currentMedia) {
                setPlayerStatus(PlayerStatus.INDETERMINATE, currentMedia)
            }
            else -> Log.w(TAG, "Remote media state undetermined!")
        }
        if (mediaChanged) {
            callback.onMediaChanged(true)
            if (oldMedia != null) {
                callback.onPostPlayback(oldMedia, ended = false, skipped = false, playingNext = currentMedia != null)
            }
        }
    }

    override fun playMediaObject(playable: Playable, stream: Boolean,
                                 startWhenPrepared: Boolean, prepareImmediately: Boolean
    ) {
        Log.d(TAG, "playMediaObject() called")
        playMediaObject(playable, false, stream, startWhenPrepared, prepareImmediately)
    }

    /**
     * Internal implementation of playMediaObject. This method has an additional parameter that
     * allows the caller to force a media player reset even if
     * the given playable parameter is the same object as the currently playing media.
     *
     * @see .playMediaObject
     */
    private fun playMediaObject(playable: Playable, forceReset: Boolean,
                                stream: Boolean, startWhenPrepared: Boolean, prepareImmediately: Boolean
    ) {
        if (!CastUtils.isCastable(playable, castContext.sessionManager.currentCastSession)) {
            Log.d(TAG, "media provided is not compatible with cast device")
            EventBus.getDefault().post(PlayerErrorEvent("Media not compatible with cast device"))
            var nextPlayable: Playable? = playable
            do {
                nextPlayable = callback.getNextInQueue(nextPlayable)
            } while (nextPlayable != null && !CastUtils.isCastable(nextPlayable,
                        castContext.sessionManager.currentCastSession))
            if (nextPlayable != null) {
                playMediaObject(nextPlayable, forceReset, stream, startWhenPrepared, prepareImmediately)
            }
            return
        }

        if (media != null) {
            if (!forceReset && media!!.getIdentifier() == playable.getIdentifier() && playerStatus == PlayerStatus.PLAYING) {
                // episode is already playing -> ignore method call
                Log.d(TAG, "Method call to playMediaObject was ignored: media file already playing.")
                return
            } else {
                // set temporarily to pause in order to update list with current position
                val isPlaying = remoteMediaClient!!.isPlaying
                val position = remoteMediaClient.approximateStreamPosition.toInt()
                if (isPlaying) {
                    callback.onPlaybackPause(media, position)
                }
                if (media != null && media?.getIdentifier() != playable.getIdentifier()) {
                    val oldMedia: Playable = media!!
                    callback.onPostPlayback(oldMedia, false, skipped = false, playingNext = true)
                }
                setPlayerStatus(PlayerStatus.INDETERMINATE, null)
            }
        }

        this.media = playable
        remoteMedia = remoteVersion(playable)
        this.mediaType = media!!.getMediaType()
        this.startWhenPrepared.set(startWhenPrepared)
        setPlayerStatus(PlayerStatus.INITIALIZING, media)
        callback.ensureMediaInfoLoaded(media!!)
        callback.onMediaChanged(true)
        setPlayerStatus(PlayerStatus.INITIALIZED, media)
        if (prepareImmediately) {
            prepare()
        }
    }

    override fun resume() {
        val newPosition = calculatePositionWithRewind(
            media!!.getPosition(),
            media!!.getLastPlayedTime())
        seekTo(newPosition)
        remoteMediaClient!!.play()
    }

    override fun pause(abandonFocus: Boolean, reinit: Boolean) {
        remoteMediaClient!!.pause()
    }

    override fun prepare() {
        if (playerStatus == PlayerStatus.INITIALIZED) {
            Log.d(TAG, "Preparing media player")
            setPlayerStatus(PlayerStatus.PREPARING, media)
            var position = media!!.getPosition()
            if (position > 0) {
                position = calculatePositionWithRewind(
                    position,
                    media!!.getLastPlayedTime())
            }
            remoteMediaClient!!.load(MediaLoadRequestData.Builder()
                .setMediaInfo(remoteMedia)
                .setAutoplay(startWhenPrepared.get())
                .setCurrentTime(position.toLong()).build())
        }
    }

    override fun reinit() {
        Log.d(TAG, "reinit() called")
        if (media != null) {
            playMediaObject(media!!, true,
                stream = false,
                startWhenPrepared = startWhenPrepared.get(),
                prepareImmediately = false)
        } else {
            Log.d(TAG, "Call to reinit was ignored: media was null")
        }
    }

    override fun seekTo(t: Int) {
        Exception("Seeking to $t").printStackTrace()
        remoteMediaClient!!.seek(MediaSeekOptions.Builder()
            .setPosition(t.toLong()).build())
    }

    override fun seekDelta(d: Int) {
        val position = getPosition()
        if (position != Playable.INVALID_TIME) {
            seekTo(position + d)
        } else {
            Log.e(TAG, "getPosition() returned INVALID_TIME in seekDelta")
        }
    }

    override fun getDuration(): Int {
        var retVal = remoteMediaClient!!.streamDuration.toInt()
        if (retVal == Playable.INVALID_TIME && media != null && media!!.getDuration() > 0) {
            retVal = media!!.getDuration()
        }
        return retVal
    }

    override fun getPosition(): Int {
        var retVal = remoteMediaClient!!.approximateStreamPosition.toInt()
        if (retVal <= 0 && media != null && media!!.getPosition() >= 0) {
            retVal = media!!.getPosition()
        }
        return retVal
    }

    override fun isStartWhenPrepared(): Boolean {
        return startWhenPrepared.get()
    }

    override fun setStartWhenPrepared(startWhenPrepared: Boolean) {
        this.startWhenPrepared.set(startWhenPrepared)
    }

    override fun setPlaybackParams(speed: Float, skipSilence: Boolean) {
        val playbackRate = max(MediaLoadOptions.PLAYBACK_RATE_MIN,
            min(MediaLoadOptions.PLAYBACK_RATE_MAX, speed.toDouble())).toFloat().toDouble()
        remoteMediaClient!!.setPlaybackRate(playbackRate)
    }

    override fun getPlaybackSpeed(): Float {
        val status = remoteMediaClient!!.mediaStatus
        return status?.playbackRate?.toFloat() ?: 1.0f
    }

    override fun setVolume(volumeLeft: Float, volumeRight: Float) {
        Log.d(TAG, "Setting the Stream volume on Remote Media Player")
        remoteMediaClient!!.setStreamVolume(volumeLeft.toDouble())
    }

    override fun getCurrentMediaType(): MediaType? {
        return mediaType
    }

    override fun isStreaming(): Boolean {
        return true
    }

    override fun shutdown() {
        remoteMediaClient!!.unregisterCallback(remoteMediaClientCallback)
    }

    override fun setVideoSurface(surface: SurfaceHolder?) {
        throw UnsupportedOperationException("Setting Video Surface unsupported in Remote Media Player")
    }

    override fun resetVideoSurface() {
        Log.e(TAG, "Resetting Video Surface unsupported in Remote Media Player")
    }

    override fun getVideoSize(): Pair<Int, Int>? {
        return null
    }

    override fun getPlayable(): Playable? {
        return media
    }

    override fun setPlayable(playable: Playable?) {
        if (playable !== media) {
            media = playable
            remoteMedia = remoteVersion(playable)
        }
    }

    override fun getAudioTracks(): List<String> {
        return emptyList()
    }

    override fun setAudioTrack(track: Int) {
    }

    override fun getSelectedAudioTrack(): Int {
        return -1
    }

    override fun endPlayback(hasEnded: Boolean, wasSkipped: Boolean, shouldContinue: Boolean,
                             toStoppedState: Boolean
    ) {
        Log.d(TAG, "endPlayback() called")
        val isPlaying = playerStatus == PlayerStatus.PLAYING
        if (playerStatus != PlayerStatus.INDETERMINATE) {
            setPlayerStatus(PlayerStatus.INDETERMINATE, media)
        }
        if (media != null && wasSkipped) {
            // current position only really matters when we skip
            val position = getPosition()
            if (position >= 0) {
                media!!.setPosition(position)
            }
        }
        val currentMedia = media
        var nextMedia: Playable? = null
        if (shouldContinue) {
            nextMedia = callback.getNextInQueue(currentMedia)

            val playNextEpisode = isPlaying && nextMedia != null
            if (playNextEpisode) {
                Log.d(TAG, "Playback of next episode will start immediately.")
            } else if (nextMedia == null) {
                Log.d(TAG, "No more episodes available to play")
            } else {
                Log.d(TAG, "Loading next episode, but not playing automatically.")
            }

            if (nextMedia != null) {
                callback.onPlaybackEnded(nextMedia.getMediaType(), !playNextEpisode)
                // setting media to null signals to playMediaObject() that we're taking care of post-playback processing
                media = null
                playMediaObject(nextMedia,
                    forceReset = false,
                    stream = true,
                    startWhenPrepared = playNextEpisode,
                    prepareImmediately = playNextEpisode)
            }
        }
        if (shouldContinue || toStoppedState) {
            if (nextMedia == null) {
                remoteMediaClient!!.stop()
                // Otherwise we rely on the chromecast callback to tell us the playback has stopped.
                callback.onPostPlayback(currentMedia!!, hasEnded, wasSkipped, false)
            } else {
                callback.onPostPlayback(currentMedia!!, hasEnded, wasSkipped, true)
            }
        } else if (isPlaying) {
            callback.onPlaybackPause(currentMedia,
                currentMedia?.getPosition() ?: Playable.INVALID_TIME)
        }
    }

    override fun shouldLockWifi(): Boolean {
        return false
    }

    override fun isCasting(): Boolean {
        return true
    }

    companion object {
        const val TAG: String = "CastPSMP"

        fun getInstanceIfConnected(context: Context,
                                   callback: PSMPCallback
        ): PlaybackServiceMediaPlayer? {
            if (GoogleApiAvailability.getInstance()
                        .isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
                return null
            }
            try {
                if (CastContext.getSharedInstance(context).castState == CastState.CONNECTED) {
                    return CastPsmp(context, callback)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }
    }
}
