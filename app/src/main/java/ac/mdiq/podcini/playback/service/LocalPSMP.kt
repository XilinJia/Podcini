package ac.mdiq.podcini.playback.service

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Pair
import android.view.SurfaceHolder
import androidx.core.util.Consumer
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.feed.util.PlaybackSpeedUtils
import ac.mdiq.podcini.util.event.playback.BufferUpdateEvent
import ac.mdiq.podcini.util.event.playback.SpeedChangedEvent
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.storage.model.playback.MediaType
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.playback.base.PlaybackServiceMediaPlayer
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.base.RewindAfterPauseUtils
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.util.event.PlayerErrorEvent
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.Volatile

/**
 * Manages the MediaPlayer object of the PlaybackService.
 */
@UnstableApi
class LocalPSMP(context: Context, callback: PSMPCallback) : PlaybackServiceMediaPlayer(context, callback) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    private var statusBeforeSeeking: PlayerStatus? = null

    @Volatile
    private var playerWrapper: ExoPlayerWrapper? = null

    @Volatile
    private var playable: Playable? = null

    @Volatile
    private var isStreaming = false

    @Volatile
    private var mediaType: MediaType
    private val startWhenPrepared = AtomicBoolean(false)

    @Volatile
    private var pausedBecauseOfTransientAudiofocusLoss = false

    @Volatile
    private var videoSize: Pair<Int, Int>? = null
    private val audioFocusRequest: AudioFocusRequestCompat
    private val audioFocusCanceller = Handler(Looper.getMainLooper())
    private var isShutDown = false
    private var seekLatch: CountDownLatch? = null

    /**
     * Starts or prepares playback of the specified Playable object. If another Playable object is already being played, the currently playing
     * episode will be stopped and replaced with the new Playable object. If the Playable object is already being played, the method will
     * not do anything.
     * Whether playback starts immediately depends on the given parameters. See below for more details.
     *
     *
     * States:
     * During execution of the method, the object will be in the INITIALIZING state. The end state depends on the given parameters.
     *
     *
     * If 'prepareImmediately' is set to true, the method will go into PREPARING state and after that into PREPARED state. If
     * 'startWhenPrepared' is set to true, the method will additionally go into PLAYING state.
     *
     *
     * If an unexpected error occurs while loading the Playable's metadata or while setting the MediaPlayers data source, the object
     * will enter the ERROR state.
     *
     *
     * This method is executed on an internal executor service.
     *
     * @param playable           The Playable object that is supposed to be played. This parameter must not be null.
     * @param stream             The type of playback. If false, the Playable object MUST provide access to a locally available file via
     * getLocalMediaUrl. If true, the Playable object MUST provide access to a resource that can be streamed by
     * the Android MediaPlayer via getStreamUrl.
     * @param startWhenPrepared  Sets the 'startWhenPrepared' flag. This flag determines whether playback will start immediately after the
     * episode has been prepared for playback. Setting this flag to true does NOT mean that the episode will be prepared
     * for playback immediately (see 'prepareImmediately' parameter for more details)
     * @param prepareImmediately Set to true if the method should also prepare the episode for playback.
     */
    override fun playMediaObject(playable: Playable, stream: Boolean, startWhenPrepared: Boolean, prepareImmediately: Boolean) {
        Log.d(TAG, "playMediaObject(...)")
        try {
            playMediaObject(playable, false, stream, startWhenPrepared, prepareImmediately)
        } catch (e: RuntimeException) {
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Internal implementation of playMediaObject. This method has an additional parameter that allows the caller to force a media player reset even if
     * the given playable parameter is the same object as the currently playing media.
     *
     *
     * This method requires the playerLock and is executed on the caller's thread.
     *
     * @see .playMediaObject
     */
    private fun playMediaObject(playable: Playable, forceReset: Boolean, stream: Boolean, startWhenPrepared: Boolean, prepareImmediately: Boolean) {
        if (this.playable != null) {
            if (!forceReset && this.playable!!.getIdentifier() == playable.getIdentifier() && playerStatus == PlayerStatus.PLAYING) {
                // episode is already playing -> ignore method call
                Log.d(TAG, "Method call to playMediaObject was ignored: media file already playing.")
                return
            } else {
                // stop playback of this episode
                if (playerStatus == PlayerStatus.PAUSED || playerStatus == PlayerStatus.PLAYING || playerStatus == PlayerStatus.PREPARED)
                    playerWrapper?.stop()

                // set temporarily to pause in order to update list with current position
                if (playerStatus == PlayerStatus.PLAYING) callback.onPlaybackPause(this.playable, getPosition())

                if (this.playable!!.getIdentifier() != playable.getIdentifier()) {
                    val oldMedia: Playable = this.playable!!
                    callback.onPostPlayback(oldMedia, ended = false, skipped = false, true)
                }

                setPlayerStatus(PlayerStatus.INDETERMINATE, null)
            }
        }

        this.playable = playable
        this.isStreaming = stream
        mediaType = this.playable!!.getMediaType()
        videoSize = null
        createMediaPlayer()
        this@LocalPSMP.startWhenPrepared.set(startWhenPrepared)
        setPlayerStatus(PlayerStatus.INITIALIZING, this.playable)
        try {
            callback.ensureMediaInfoLoaded(this.playable!!)
            callback.onMediaChanged(false)
            setPlaybackParams(PlaybackSpeedUtils.getCurrentPlaybackSpeed(this.playable), UserPreferences.isSkipSilence)
            when {
                stream -> {
                    val streamurl = this.playable!!.getStreamUrl()
                    if (streamurl != null) {
                        if (playable is FeedMedia && playable.item?.feed?.preferences != null) {
                            val preferences = playable.item!!.feed!!.preferences!!
                            playerWrapper?.setDataSource(streamurl, preferences.username, preferences.password)
                        } else playerWrapper?.setDataSource(streamurl)
                    }
                }
                else -> {
                    val localMediaurl = this.playable!!.getLocalMediaUrl()
                    if (localMediaurl != null && File(localMediaurl).canRead()) playerWrapper?.setDataSource(localMediaurl)
                    else throw IOException("Unable to read local file $localMediaurl")
                }
            }
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            if (uiModeManager.currentModeType != Configuration.UI_MODE_TYPE_CAR) setPlayerStatus(PlayerStatus.INITIALIZED,
                this.playable)

            if (prepareImmediately) {
                setPlayerStatus(PlayerStatus.PREPARING, this.playable)
                playerWrapper?.prepare()
                onPrepared(startWhenPrepared)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            setPlayerStatus(PlayerStatus.ERROR, null)
            EventBus.getDefault().postSticky(PlayerErrorEvent(e.localizedMessage ?: ""))
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            setPlayerStatus(PlayerStatus.ERROR, null)
            EventBus.getDefault().postSticky(PlayerErrorEvent(e.localizedMessage ?: ""))
        }
    }

    /**
     * Resumes playback if the PSMP object is in PREPARED or PAUSED state. If the PSMP object is in an invalid state.
     * nothing will happen.
     *
     *
     * This method is executed on an internal executor service.
     */
    override fun resume() {
        if (playerStatus == PlayerStatus.PAUSED || playerStatus == PlayerStatus.PREPARED) {
            val focusGained = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest)

            if (focusGained == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d(TAG, "Audiofocus successfully requested")
                Log.d(TAG, "Resuming/Starting playback")
                acquireWifiLockIfNecessary()
                setPlaybackParams(PlaybackSpeedUtils.getCurrentPlaybackSpeed(playable), UserPreferences.isSkipSilence)
                setVolume(1.0f, 1.0f)

                if (playable != null && playerStatus == PlayerStatus.PREPARED && playable!!.getPosition() > 0) {
                    val newPosition = RewindAfterPauseUtils.calculatePositionWithRewind(playable!!.getPosition(), playable!!.getLastPlayedTime())
                    seekTo(newPosition)
                }
                playerWrapper?.start()

                setPlayerStatus(PlayerStatus.PLAYING, playable)
                pausedBecauseOfTransientAudiofocusLoss = false
            } else Log.e(TAG, "Failed to request audio focus")
        } else Log.d(TAG, "Call to resume() was ignored because current state of PSMP object is $playerStatus")
    }


    /**
     * Saves the current position and pauses playback. Note that, if audiofocus
     * is abandoned, the lockscreen controls will also disapear.
     *
     *
     * This method is executed on an internal executor service.
     *
     * @param abandonFocus is true if the service should release audio focus
     * @param reinit       is true if service should reinit after pausing if the media
     * file is being streamed
     */
    override fun pause(abandonFocus: Boolean, reinit: Boolean) {
        releaseWifiLockIfNecessary()
        if (playerStatus == PlayerStatus.PLAYING) {
            Log.d(TAG, "Pausing playback.")
            playerWrapper?.pause()
            setPlayerStatus(PlayerStatus.PAUSED, playable, getPosition())

            if (abandonFocus) {
                abandonAudioFocus()
                pausedBecauseOfTransientAudiofocusLoss = false
            }
            if (isStreaming && reinit) reinit()
        } else {
            Log.d(TAG, "Ignoring call to pause: Player is in $playerStatus state")
        }
    }

    private fun abandonAudioFocus() {
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
    }

    /**
     * Prepares media player for playback if the service is in the INITALIZED
     * state.
     *
     *
     * This method is executed on an internal executor service.
     */
    override fun prepare() {
        if (playerStatus == PlayerStatus.INITIALIZED) {
            Log.d(TAG, "Preparing media player")
            setPlayerStatus(PlayerStatus.PREPARING, playable)
            playerWrapper?.prepare()
            onPrepared(startWhenPrepared.get())
        }
    }

    /**
     * Called after media player has been prepared. This method is executed on the caller's thread.
     */
    private fun onPrepared(startWhenPrepared: Boolean) {
        check(playerStatus == PlayerStatus.PREPARING) { "Player is not in PREPARING state" }
        Log.d(TAG, "Resource prepared")

        if (playerWrapper != null && mediaType == MediaType.VIDEO) videoSize = Pair(playerWrapper!!.videoWidth, playerWrapper!!.videoHeight)

        if (playable != null) {
            // TODO this call has no effect!
            if (playable!!.getPosition() > 0) seekTo(playable!!.getPosition())

            if (playable!!.getDuration() <= 0) {
                Log.d(TAG, "Setting duration of media")
                if (playerWrapper != null) playable!!.setDuration(playerWrapper!!.duration)
            }
        }
        setPlayerStatus(PlayerStatus.PREPARED, playable)

        if (startWhenPrepared) resume()
    }

    /**
     * Resets the media player and moves it into INITIALIZED state.
     *
     *
     * This method is executed on an internal executor service.
     */
    override fun reinit() {
        Log.d(TAG, "reinit()")
        releaseWifiLockIfNecessary()
        when {
            playable != null -> playMediaObject(playable!!, true, isStreaming, startWhenPrepared.get(), false)
            playerWrapper != null -> playerWrapper!!.reset()
            else -> Log.d(TAG, "Call to reinit was ignored: media and mediaPlayer were null")
        }
    }

    /**
     * Seeks to the specified position. If the PSMP object is in an invalid state, this method will do nothing.
     * Invalid time values (< 0) will be ignored.
     *
     *
     * This method is executed on an internal executor service.
     */
    override fun seekTo(t0: Int) {
        var t = t0
        if (t < 0) t = 0

        if (t >= getDuration()) {
            Log.d(TAG, "Seek reached end of file, skipping to next episode")
            endPlayback(true, wasSkipped = true, true, toStoppedState = true)
            return
        }

        when (playerStatus) {
            PlayerStatus.PLAYING, PlayerStatus.PAUSED, PlayerStatus.PREPARED -> {
                if (seekLatch != null && seekLatch!!.count > 0) {
                    try {
                        seekLatch!!.await(3, TimeUnit.SECONDS)
                    } catch (e: InterruptedException) {
                        Log.e(TAG, Log.getStackTraceString(e))
                    }
                }
                seekLatch = CountDownLatch(1)
                statusBeforeSeeking = playerStatus
                setPlayerStatus(PlayerStatus.SEEKING, playable, getPosition())
                playerWrapper?.seekTo(t)
                if (statusBeforeSeeking == PlayerStatus.PREPARED) playable?.setPosition(t)
                try {
                    seekLatch!!.await(3, TimeUnit.SECONDS)
                } catch (e: InterruptedException) {
                    Log.e(TAG, Log.getStackTraceString(e))
                }
            }
            PlayerStatus.INITIALIZED -> {
                playable?.setPosition(t)
                startWhenPrepared.set(false)
                prepare()
            }
            else -> {}
        }
    }

    /**
     * Seek a specific position from the current position
     *
     * @param d offset from current position (positive or negative)
     */
    override fun seekDelta(d: Int) {
        val currentPosition = getPosition()
        if (currentPosition != Playable.INVALID_TIME) seekTo(currentPosition + d)
        else Log.e(TAG, "getPosition() returned INVALID_TIME in seekDelta")
    }

    /**
     * Returns the duration of the current media object or INVALID_TIME if the duration could not be retrieved.
     */
    override fun getDuration(): Int {
        var retVal = Playable.INVALID_TIME
        if (playerStatus == PlayerStatus.PLAYING || playerStatus == PlayerStatus.PAUSED || playerStatus == PlayerStatus.PREPARED) {
            if (playerWrapper != null) retVal = playerWrapper!!.duration
        }
        if (retVal <= 0 && playable != null && playable!!.getDuration() > 0) retVal = playable!!.getDuration()
        return retVal
    }

    /**
     * Returns the position of the current media object or INVALID_TIME if the position could not be retrieved.
     */
    override fun getPosition(): Int {
        var retVal = Playable.INVALID_TIME
        if (playerStatus.isAtLeast(PlayerStatus.PREPARED)) {
            if (playerWrapper != null) retVal = playerWrapper!!.currentPosition
        }
        if (retVal <= 0 && playable != null && playable!!.getPosition() >= 0) retVal = playable!!.getPosition()
        return retVal
    }

    override fun isStartWhenPrepared(): Boolean {
        return startWhenPrepared.get()
    }

    override fun setStartWhenPrepared(startWhenPrepared: Boolean) {
        this.startWhenPrepared.set(startWhenPrepared)
    }

    /**
     * Sets the playback speed.
     * This method is executed on an internal executor service.
     */
    override fun setPlaybackParams(speed: Float, skipSilence: Boolean) {
        Log.d(TAG, "Playback speed was set to $speed")
        EventBus.getDefault().post(SpeedChangedEvent(speed))
        playerWrapper?.setPlaybackParams(speed, skipSilence)
    }

    /**
     * Returns the current playback speed. If the playback speed could not be retrieved, 1 is returned.
     */
    override fun getPlaybackSpeed(): Float {
        var retVal = 1f
        if (playerStatus == PlayerStatus.PLAYING || playerStatus == PlayerStatus.PAUSED || playerStatus == PlayerStatus.INITIALIZED || playerStatus == PlayerStatus.PREPARED) {
            if (playerWrapper != null) retVal = playerWrapper!!.currentSpeedMultiplier
        }
        return retVal
    }

    /**
     * Sets the playback volume.
     * This method is executed on an internal executor service.
     */
    override fun setVolume(volumeLeft: Float, volumeRight: Float) {
        var volumeLeft = volumeLeft
        var volumeRight = volumeRight
        val playable = getPlayable()
        if (playable is FeedMedia && playable.item?.feed?.preferences != null) {
            val preferences = playable.item!!.feed!!.preferences!!
            val volumeAdaptionSetting = preferences.volumeAdaptionSetting
            if (volumeAdaptionSetting != null) {
                val adaptionFactor = volumeAdaptionSetting.adaptionFactor
                volumeLeft *= adaptionFactor
                volumeRight *= adaptionFactor
            }
        }
        playerWrapper?.setVolume(volumeLeft, volumeRight)
        Log.d(TAG, "Media player volume was set to $volumeLeft $volumeRight")
    }

    override fun getCurrentMediaType(): MediaType {
        return mediaType
    }

    override fun isStreaming(): Boolean {
        return isStreaming
    }

    /**
     * Releases internally used resources. This method should only be called when the object is not used anymore.
     */
    override fun shutdown() {
        if (playerWrapper != null) {
            try {
                clearMediaPlayerListeners()
                if (playerWrapper!!.isPlaying) playerWrapper!!.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            playerWrapper!!.release()
            playerWrapper = null
            playerStatus = PlayerStatus.STOPPED
        }
        isShutDown = true
        abandonAudioFocus()
        releaseWifiLockIfNecessary()
    }

    override fun setVideoSurface(surface: SurfaceHolder?) {
        playerWrapper?.setDisplay(surface)
    }

    override fun resetVideoSurface() {
        if (mediaType == MediaType.VIDEO) {
            Log.d(TAG, "Resetting video surface")
            playerWrapper?.setDisplay(null)
            reinit()
        } else {
            Log.e(TAG, "Resetting video surface for media of Audio type")
        }
    }

    /**
     * Return width and height of the currently playing video as a pair.
     *
     * @return Width and height as a Pair or null if the video size could not be determined. The method might still
     * return an invalid non-null value if the getVideoWidth() and getVideoHeight() methods of the media player return
     * invalid values.
     */
    override fun getVideoSize(): Pair<Int, Int>? {
        if (playerWrapper != null && playerStatus != PlayerStatus.ERROR && mediaType == MediaType.VIDEO)
            videoSize = Pair(playerWrapper!!.videoWidth, playerWrapper!!.videoHeight)
        return videoSize
    }

    /**
     * Returns the current media, if you need the media and the player status together, you should
     * use getPSMPInfo() to make sure they're properly synchronized. Otherwise a race condition
     * could result in nonsensical results (like a status of PLAYING, but a null playable)
     * @return the current media. May be null
     */
    override fun getPlayable(): Playable? {
        return playable
    }

    override fun setPlayable(playable: Playable?) {
        this.playable = playable
    }

    override fun getAudioTracks(): List<String> {
        return playerWrapper?.audioTracks?: listOf()
    }

    override fun setAudioTrack(track: Int) {
        if (playerWrapper != null) playerWrapper!!.setAudioTrack(track)
    }

    override fun getSelectedAudioTrack(): Int {
        return playerWrapper?.selectedAudioTrack?:0
    }

    override fun createMediaPlayer() {
        playerWrapper?.release()

        if (playable == null) {
            playerWrapper = null
            playerStatus = PlayerStatus.STOPPED
            return
        }

        playerWrapper = ExoPlayerWrapper(context)
        playerWrapper!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
        setMediaPlayerListeners(playerWrapper)
    }

    private val audioFocusChangeListener = OnAudioFocusChangeListener { focusChange ->
        if (isShutDown) return@OnAudioFocusChangeListener

        when {
            !PlaybackService.isRunning -> {
                abandonAudioFocus()
                Log.d(TAG, "onAudioFocusChange: PlaybackService is no longer running")
                return@OnAudioFocusChangeListener
            }
            focusChange == AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Lost audio focus")
                pause(true, reinit = false)
//                callback.shouldStop()
            }
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK && !UserPreferences.shouldPauseForFocusLoss() -> {
                if (playerStatus == PlayerStatus.PLAYING) {
                    Log.d(TAG, "Lost audio focus temporarily. Ducking...")
                    setVolume(0.25f, 0.25f)
                    pausedBecauseOfTransientAudiofocusLoss = false
                }
            }
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (playerStatus == PlayerStatus.PLAYING) {
                    Log.d(TAG, "Lost audio focus temporarily. Pausing...")
                    playerWrapper?.pause() // Pause without telling the PlaybackService
                    pausedBecauseOfTransientAudiofocusLoss = true

                    audioFocusCanceller.removeCallbacksAndMessages(null)
                    // Still did not get back the audio focus. Now actually pause.
                    audioFocusCanceller.postDelayed({ if (pausedBecauseOfTransientAudiofocusLoss) pause(abandonFocus = true, reinit = false) },
                        30000)
                }
            }
            focusChange == AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Gained audio focus")
                audioFocusCanceller.removeCallbacksAndMessages(null)
                if (pausedBecauseOfTransientAudiofocusLoss) playerWrapper?.start()    // we paused => play now
                else setVolume(1.0f, 1.0f)   // we ducked => raise audio level back

                pausedBecauseOfTransientAudiofocusLoss = false
            }
        }
    }

    init {
        mediaType = MediaType.UNKNOWN

        val audioAttributes = AudioAttributesCompat.Builder()
            .setUsage(AudioAttributesCompat.USAGE_MEDIA)
            .setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
            .build()
        audioFocusRequest = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .setWillPauseWhenDucked(true)
            .build()
    }

    override fun endPlayback(hasEnded: Boolean, wasSkipped: Boolean, shouldContinue: Boolean, toStoppedState: Boolean) {
        releaseWifiLockIfNecessary()

        val isPlaying = playerStatus == PlayerStatus.PLAYING

        // we're relying on the position stored in the Playable object for post-playback processing
        val position = getPosition()
        if (position >= 0) playable?.setPosition(position)

        playerWrapper?.reset()

        abandonAudioFocus()

        val currentMedia = playable
        var nextMedia: Playable? = null

        if (shouldContinue) {
            // Load next episode if previous episode was in the queue and if there
            // is an episode in the queue left.
            // Start playback immediately if continuous playback is enabled
            nextMedia = callback.getNextInQueue(currentMedia)
            if (nextMedia != null) {
                callback.onPlaybackEnded(nextMedia.getMediaType(), false)
                // setting media to null signals to playMediaObject() that
                // we're taking care of post-playback processing
                playable = null
                playMediaObject(nextMedia, false, !nextMedia.localFileAvailable(), isPlaying, isPlaying)
            }
        }
        when {
            shouldContinue || toStoppedState -> {
                if (nextMedia == null) {
                    callback.onPlaybackEnded(null, true)
                    stop()
                }
                val hasNext = nextMedia != null
                callback.onPostPlayback(currentMedia, hasEnded, wasSkipped, hasNext)
            }
            isPlaying -> callback.onPlaybackPause(currentMedia, currentMedia!!.getPosition())
        }
    }

    /**
     * Moves the LocalPSMP into STOPPED state. This call is only valid if the player is currently in
     * INDETERMINATE state, for example after a call to endPlayback.
     * This method will only take care of changing the PlayerStatus of this object! Other tasks like
     * abandoning audio focus have to be done with other methods.
     */
    private fun stop() {
        releaseWifiLockIfNecessary()
        if (playerStatus == PlayerStatus.INDETERMINATE) setPlayerStatus(PlayerStatus.STOPPED, null)
        else Log.d(TAG, "Ignored call to stop: Current player state is: $playerStatus")
    }

    override fun shouldLockWifi(): Boolean {
        return isStreaming
    }

    private fun setMediaPlayerListeners(mp: ExoPlayerWrapper?) {
        if (mp == null || playable == null) return

        mp.setOnCompletionListener(Runnable { endPlayback(hasEnded = true, wasSkipped = false, shouldContinue = true, toStoppedState = true) })
        mp.setOnSeekCompleteListener(Runnable { this.genericSeekCompleteListener() })
        mp.setOnBufferingUpdateListener(Consumer { percent: Int ->
            when (percent) {
                ExoPlayerWrapper.BUFFERING_STARTED -> EventBus.getDefault().post(BufferUpdateEvent.started())
                ExoPlayerWrapper.BUFFERING_ENDED -> EventBus.getDefault().post(BufferUpdateEvent.ended())
                else -> EventBus.getDefault().post(BufferUpdateEvent.progressUpdate(0.01f * percent))
            }
        })
        mp.setOnErrorListener(Consumer { message: String ->
            EventBus.getDefault().postSticky(PlayerErrorEvent(message))
        })
    }

    private fun clearMediaPlayerListeners() {
        if (playerWrapper == null) return
        playerWrapper!!.setOnCompletionListener {}
        playerWrapper!!.setOnSeekCompleteListener {}
        playerWrapper!!.setOnBufferingUpdateListener { }
        playerWrapper!!.setOnErrorListener { }
    }

    private fun genericSeekCompleteListener() {
        Log.d(TAG, "genericSeekCompleteListener")
        seekLatch?.countDown()

        if (playerStatus == PlayerStatus.PLAYING) {
            if (playable != null) callback.onPlaybackStart(playable!!, getPosition())
        }
        if (playerStatus == PlayerStatus.SEEKING && statusBeforeSeeking != null) setPlayerStatus(statusBeforeSeeking!!, playable, getPosition())
    }

    override fun isCasting(): Boolean {
        return false
    }

    companion object {
        private const val TAG = "LclPlaybackSvcMPlayer"
    }
}
