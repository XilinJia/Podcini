package ac.mdiq.podcini.playback.cast

import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.MediaPlayerCallback
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.storage.model.RemoteMedia
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.android.gms.cast.*
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min

/**
 * Implementation of MediaPlayerBase suitable for remote playback on Cast Devices.
 */
@SuppressLint("VisibleForTests")
class CastPsmp(context: Context, callback: MediaPlayerCallback) : MediaPlayerBase(context, callback) {

    val TAG = this::class.simpleName ?: "Anonymous"

    @Volatile
    private var remoteMedia: MediaInfo? = null

    @Volatile
    private var remoteState: Int
    private val castContext = CastContext.getSharedInstance(context)
    private val remoteMediaClient = castContext.sessionManager.currentCastSession!!.remoteMediaClient

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
            EventFlow.postEvent(FlowEvent.PlayerErrorEvent(mediaError.reason!!))
        }
    }

    init {
        remoteMediaClient!!.registerCallback(remoteMediaClientCallback)
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

    private fun localVersion(info: MediaInfo?): Playable? {
        if (info == null || info.metadata == null) return null
        if (CastUtils.matches(info, curMedia)) return curMedia

        val streamUrl = info.metadata!!.getString(CastUtils.KEY_STREAM_URL)
        return if (streamUrl == null) CastUtils.makeRemoteMedia(info) else callback.findMedia(streamUrl)
    }

    private fun remoteVersion(playable: Playable?): MediaInfo? {
        return when {
            playable == null -> null
            CastUtils.matches(remoteMedia, playable) -> remoteMedia
            playable is EpisodeMedia -> MediaInfoCreator.from(playable)
            playable is RemoteMedia -> MediaInfoCreator.from(playable)
//            playable is RemoteMedia -> MediaInfoCreator.from(playable)
            else -> null
        }
    }

    private fun onRemoteMediaPlayerStatusUpdated() {
        val mediaStatus = remoteMediaClient!!.mediaStatus
        if (mediaStatus == null) {
            Logd(TAG, "Received null MediaStatus")
            return
        } else Logd(TAG, "Received remote status/media update. New state=" + mediaStatus.playerState)

        var state = mediaStatus.playerState
        val oldState = remoteState
        remoteMedia = mediaStatus.mediaInfo
        val mediaChanged = !CastUtils.matches(remoteMedia, curMedia)
        var stateChanged = state != oldState
        if (!mediaChanged && !stateChanged) {
            Logd(TAG, "Both media and state haven't changed, so nothing to do")
            return
        }
        val currentMedia = if (mediaChanged) localVersion(remoteMedia) else curMedia
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

        setBuffering(state == MediaStatus.PLAYER_STATE_BUFFERING)

        when (state) {
            MediaStatus.PLAYER_STATE_PLAYING -> {
                if (!stateChanged) {
                    //These steps are necessary because they won't be performed by setPlayerStatus()
                    if (position >= 0) currentMedia!!.setPosition(position)
                    currentMedia!!.onPlaybackStart()
                }
                setPlayerStatus(PlayerStatus.PLAYING, currentMedia, position)
            }
            MediaStatus.PLAYER_STATE_PAUSED -> setPlayerStatus(PlayerStatus.PAUSED, currentMedia, position)
            MediaStatus.PLAYER_STATE_BUFFERING -> setPlayerStatus(
                if ((mediaChanged || status == PlayerStatus.PREPARING)) PlayerStatus.PREPARING
                else PlayerStatus.SEEKING,
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
            MediaStatus.PLAYER_STATE_UNKNOWN -> if (status != PlayerStatus.INDETERMINATE || curMedia !== currentMedia) {
                setPlayerStatus(PlayerStatus.INDETERMINATE, currentMedia)
            }
            else -> Log.w(TAG, "Remote media state undetermined!")
        }
        if (mediaChanged) {
            callback.onMediaChanged(true)
            if (oldMedia != null) callback.onPostPlayback(oldMedia, ended = false, skipped = false, playingNext = currentMedia != null)
        }
    }

    override fun createMediaPlayer() {}

//    private var prevMedia: Playable? = null

    /**
     * Internal implementation of playMediaObject. This method has an additional parameter that
     * allows the caller to force a media player reset even if
     * the given playable parameter is the same object as the currently playing media.
     *
     * @see .playMediaObject
     */
    override fun playMediaObject(playable: Playable, stream: Boolean, startWhenPrepared: Boolean, prepareImmediately: Boolean, forceReset: Boolean) {
        if (!CastUtils.isCastable(playable, castContext.sessionManager.currentCastSession)) {
            Logd(TAG, "media provided is not compatible with cast device")
            EventFlow.postEvent(FlowEvent.PlayerErrorEvent("Media not compatible with cast device"))
            var nextPlayable: Playable? = playable
            do {
                nextPlayable = callback.getNextInQueue(nextPlayable)
            } while (nextPlayable != null && !CastUtils.isCastable(nextPlayable, castContext.sessionManager.currentCastSession))

            if (nextPlayable != null) playMediaObject(nextPlayable, stream, startWhenPrepared, prepareImmediately, forceReset)

            return
        }

        if (curMedia != null) {
            if (!forceReset && curMedia!!.getIdentifier() == prevMedia?.getIdentifier() && status == PlayerStatus.PLAYING) {
                // episode is already playing -> ignore method call
                Logd(TAG, "Method call to playMediaObject was ignored: media file already playing.")
                return
            } else {
                // set temporarily to pause in order to update list with current position
                val isPlaying = remoteMediaClient!!.isPlaying
                val position = remoteMediaClient.approximateStreamPosition.toInt()
                if (isPlaying) callback.onPlaybackPause(curMedia, position)
                if (status == PlayerStatus.PLAYING) {
                    val pos = curMedia?.getPosition() ?: -1
                    seekTo(pos)
                    callback.onPlaybackPause(curMedia, pos)
                }

                if (prevMedia != null && curMedia!!.getIdentifier() != prevMedia?.getIdentifier()) {
                    callback.onPostPlayback(prevMedia, false, skipped = false, playingNext = true)
                }
                prevMedia = curMedia

                setPlayerStatus(PlayerStatus.INDETERMINATE, null)
            }
        }

        curMedia = playable
        remoteMedia = remoteVersion(playable)
        this.mediaType = curMedia!!.getMediaType()
        this.startWhenPrepared.set(startWhenPrepared)
        setPlayerStatus(PlayerStatus.INITIALIZING, curMedia)
        callback.ensureMediaInfoLoaded(curMedia!!)
        callback.onMediaChanged(true)
        setPlayerStatus(PlayerStatus.INITIALIZED, curMedia)
        if (prepareImmediately) prepare()
    }

    override fun resume() {
        val newPosition = calculatePositionWithRewind(curMedia!!.getPosition(), curMedia!!.getLastPlayedTime())
        seekTo(newPosition)
        remoteMediaClient!!.play()
    }

    override fun pause(abandonFocus: Boolean, reinit: Boolean) {
        remoteMediaClient!!.pause()
    }

    override fun prepare() {
        if (status == PlayerStatus.INITIALIZED) {
            Logd(TAG, "Preparing media player")
            setPlayerStatus(PlayerStatus.PREPARING, curMedia)
            var position = curMedia!!.getPosition()
            if (position > 0) position = calculatePositionWithRewind(position, curMedia!!.getLastPlayedTime())

            remoteMediaClient!!.load(MediaLoadRequestData.Builder()
                .setMediaInfo(remoteMedia)
                .setAutoplay(startWhenPrepared.get())
                .setCurrentTime(position.toLong()).build())
        }
    }

    override fun reinit() {
        Logd(TAG, "reinit() called")
        if (curMedia != null) playMediaObject(curMedia!!, stream = false, startWhenPrepared = startWhenPrepared.get(), prepareImmediately = false, true)
        else Logd(TAG, "Call to reinit was ignored: media was null")
    }

    override fun seekTo(t: Int) {
        Exception("Seeking to $t").printStackTrace()
        remoteMediaClient!!.seek(MediaSeekOptions.Builder().setPosition(t.toLong()).build())
    }

    override fun getDuration(): Int {
        var retVal = remoteMediaClient!!.streamDuration.toInt()
        if (retVal == Playable.INVALID_TIME && curMedia != null && curMedia!!.getDuration() > 0) retVal = curMedia!!.getDuration()
        return retVal
    }

    override fun getPosition(): Int {
        var retVal = remoteMediaClient!!.approximateStreamPosition.toInt()
        if (retVal <= 0 && curMedia != null && curMedia!!.getPosition() >= 0) retVal = curMedia!!.getPosition()
        return retVal
    }

    override fun setPlaybackParams(speed: Float, skipSilence: Boolean) {
        val playbackRate = max(MediaLoadOptions.PLAYBACK_RATE_MIN, min(MediaLoadOptions.PLAYBACK_RATE_MAX, speed.toDouble())).toFloat().toDouble()
        remoteMediaClient!!.setPlaybackRate(playbackRate)
    }

    override fun getPlaybackSpeed(): Float {
        val status = remoteMediaClient!!.mediaStatus
        return status?.playbackRate?.toFloat() ?: 1.0f
    }

    override fun setVolume(volumeLeft: Float, volumeRight: Float) {
        Logd(TAG, "Setting the Stream volume on Remote Media Player")
        remoteMediaClient!!.setStreamVolume(volumeLeft.toDouble())
    }

    override fun shutdown() {
        remoteMediaClient!!.unregisterCallback(remoteMediaClientCallback)
    }

    override fun setPlayable(playable: Playable?) {
        if (playable !== curMedia) {
            curMedia = playable
            remoteMedia = remoteVersion(playable)
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
                playMediaObject(nextMedia, stream = true, startWhenPrepared = playNextEpisode, prepareImmediately = playNextEpisode, forceReset = false)
            }
        }
        when {
            shouldContinue || toStoppedState -> {
                if (nextMedia == null) {
                    remoteMediaClient!!.stop()
                    // Otherwise we rely on the chromecast callback to tell us the playback has stopped.
                    callback.onPostPlayback(currentMedia!!, hasEnded, wasSkipped, false)
                } else callback.onPostPlayback(currentMedia!!, hasEnded, wasSkipped, true)
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

            try {
                if (CastContext.getSharedInstance(context).castState == CastState.CONNECTED) return CastPsmp(context, callback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }
    }
}
