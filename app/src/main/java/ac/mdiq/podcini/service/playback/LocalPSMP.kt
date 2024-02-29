package ac.mdiq.podcini.service.playback

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
import ac.mdiq.podcini.playback.event.BufferUpdateEvent
import ac.mdiq.podcini.playback.event.SpeedChangedEvent
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.storage.model.playback.MediaType
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.playback.base.PlaybackServiceMediaPlayer
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.base.RewindAfterPauseUtils
import ac.mdiq.podcini.preferences.UserPreferences
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
    private var mediaPlayer: ExoPlayerWrapper? = null

    @Volatile
    private var media: Playable? = null

    @Volatile
    private var stream = false

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
    override fun playMediaObject(playable: Playable,
                                 stream: Boolean,
                                 startWhenPrepared: Boolean,
                                 prepareImmediately: Boolean
    ) {
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
    private fun playMediaObject(playable: Playable,
                                forceReset: Boolean,
                                stream: Boolean,
                                startWhenPrepared: Boolean,
                                prepareImmediately: Boolean
    ) {
        if (media != null) {
            if (!forceReset && media!!.getIdentifier() == playable.getIdentifier() && playerStatus == PlayerStatus.PLAYING) {
                // episode is already playing -> ignore method call
                Log.d(TAG, "Method call to playMediaObject was ignored: media file already playing.")
                return
            } else {
                // stop playback of this episode
                if (playerStatus == PlayerStatus.PAUSED || playerStatus == PlayerStatus.PLAYING || playerStatus == PlayerStatus.PREPARED) {
                    mediaPlayer?.stop()
                }
                // set temporarily to pause in order to update list with current position
                if (playerStatus == PlayerStatus.PLAYING) {
                    callback.onPlaybackPause(media, getPosition())
                }

                if (media!!.getIdentifier() != playable.getIdentifier()) {
                    val oldMedia: Playable = media!!
                    callback.onPostPlayback(oldMedia, false, false, true)
                }

                setPlayerStatus(PlayerStatus.INDETERMINATE, null)
            }
        }

        media = playable
        this.stream = stream
        mediaType = media!!.getMediaType()
        videoSize = null
        createMediaPlayer()
        this@LocalPSMP.startWhenPrepared.set(startWhenPrepared)
        setPlayerStatus(PlayerStatus.INITIALIZING, media)
        try {
            callback.ensureMediaInfoLoaded(media!!)
            callback.onMediaChanged(false)
//            TODO: speed
            setPlaybackParams(PlaybackSpeedUtils.getCurrentPlaybackSpeed(media), UserPreferences.isSkipSilence)
            if (stream) {
                if (media!!.getStreamUrl() != null) {
                    if (playable is FeedMedia && playable.getItem()?.feed?.preferences != null) {
                        val preferences = playable.getItem()!!.feed!!.preferences!!
                        mediaPlayer?.setDataSource(
                            media!!.getStreamUrl()!!,
                            preferences.username,
                            preferences.password)
                    } else {
                        mediaPlayer?.setDataSource(media!!.getStreamUrl()!!)
                    }
                }
            } else if (media!!.getLocalMediaUrl() != null && File(media!!.getLocalMediaUrl()!!).canRead()) {
                mediaPlayer?.setDataSource(media!!.getLocalMediaUrl()!!)
            } else {
                throw IOException("Unable to read local file " + media!!.getLocalMediaUrl())
            }
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            if (uiModeManager.currentModeType != Configuration.UI_MODE_TYPE_CAR) {
                setPlayerStatus(PlayerStatus.INITIALIZED, media)
            }

            if (prepareImmediately) {
                setPlayerStatus(PlayerStatus.PREPARING, media)
                mediaPlayer?.prepare()
                onPrepared(startWhenPrepared)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            setPlayerStatus(PlayerStatus.ERROR, null)
            EventBus.getDefault().postSticky(ac.mdiq.podcini.util.event.PlayerErrorEvent(e.localizedMessage ?: ""))
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            setPlayerStatus(PlayerStatus.ERROR, null)
            EventBus.getDefault().postSticky(ac.mdiq.podcini.util.event.PlayerErrorEvent(e.localizedMessage ?: ""))
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
//  TODO: speed
                setPlaybackParams(PlaybackSpeedUtils.getCurrentPlaybackSpeed(media), UserPreferences.isSkipSilence)
                setVolume(1.0f, 1.0f)

                if (media != null && playerStatus == PlayerStatus.PREPARED && media!!.getPosition() > 0) {
                    val newPosition = RewindAfterPauseUtils.calculatePositionWithRewind(
                        media!!.getPosition(),
                        media!!.getLastPlayedTime())
                    seekTo(newPosition)
                }
                mediaPlayer?.start()

                setPlayerStatus(PlayerStatus.PLAYING, media)
                pausedBecauseOfTransientAudiofocusLoss = false
            } else {
                Log.e(TAG, "Failed to request audio focus")
            }
        } else {
            Log.d(TAG, "Call to resume() was ignored because current state of PSMP object is $playerStatus")
        }
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
            mediaPlayer?.pause()
            setPlayerStatus(PlayerStatus.PAUSED, media, getPosition())

            if (abandonFocus) {
                abandonAudioFocus()
                pausedBecauseOfTransientAudiofocusLoss = false
            }
            if (stream && reinit) {
                reinit()
            }
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
            setPlayerStatus(PlayerStatus.PREPARING, media)
            mediaPlayer?.prepare()
            onPrepared(startWhenPrepared.get())
        }
    }

    /**
     * Called after media player has been prepared. This method is executed on the caller's thread.
     */
    private fun onPrepared(startWhenPrepared: Boolean) {
        check(playerStatus == PlayerStatus.PREPARING) { "Player is not in PREPARING state" }
        Log.d(TAG, "Resource prepared")

        if (mediaPlayer != null && mediaType == MediaType.VIDEO) {
            videoSize = Pair(mediaPlayer!!.videoWidth, mediaPlayer!!.videoHeight)
        }

        if (media != null) {
            // TODO this call has no effect!
            if (media!!.getPosition() > 0) {
                seekTo(media!!.getPosition())
            }

            if (media!!.getDuration() <= 0) {
                Log.d(TAG, "Setting duration of media")
                if (mediaPlayer != null) media!!.setDuration(mediaPlayer!!.duration)
            }
        }
        setPlayerStatus(PlayerStatus.PREPARED, media)

        if (startWhenPrepared) {
            resume()
        }
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
        if (media != null) {
            playMediaObject(media!!, true, stream, startWhenPrepared.get(), false)
        } else if (mediaPlayer != null) {
            mediaPlayer!!.reset()
        } else {
            Log.d(TAG, "Call to reinit was ignored: media and mediaPlayer were null")
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
        if (t < 0) {
            t = 0
        }

        if (t >= getDuration()) {
            Log.d(TAG, "Seek reached end of file, skipping to next episode")
            endPlayback(true, true, true, true)
            return
        }

        if (playerStatus == PlayerStatus.PLAYING || playerStatus == PlayerStatus.PAUSED || playerStatus == PlayerStatus.PREPARED) {
            if (seekLatch != null && seekLatch!!.count > 0) {
                try {
                    seekLatch!!.await(3, TimeUnit.SECONDS)
                } catch (e: InterruptedException) {
                    Log.e(TAG, Log.getStackTraceString(e))
                }
            }
            seekLatch = CountDownLatch(1)
            statusBeforeSeeking = playerStatus
            setPlayerStatus(PlayerStatus.SEEKING, media, getPosition())
            mediaPlayer?.seekTo(t)
            if (statusBeforeSeeking == PlayerStatus.PREPARED) {
                media?.setPosition(t)
            }
            try {
                seekLatch!!.await(3, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        } else if (playerStatus == PlayerStatus.INITIALIZED) {
            media?.setPosition(t)
            startWhenPrepared.set(false)
            prepare()
        }
    }

    /**
     * Seek a specific position from the current position
     *
     * @param d offset from current position (positive or negative)
     */
    override fun seekDelta(d: Int) {
        val currentPosition = getPosition()
        if (currentPosition != Playable.INVALID_TIME) {
            seekTo(currentPosition + d)
        } else {
            Log.e(TAG, "getPosition() returned INVALID_TIME in seekDelta")
        }
    }

    /**
     * Returns the duration of the current media object or INVALID_TIME if the duration could not be retrieved.
     */
    override fun getDuration(): Int {
        var retVal = Playable.INVALID_TIME
        if (playerStatus == PlayerStatus.PLAYING || playerStatus == PlayerStatus.PAUSED || playerStatus == PlayerStatus.PREPARED) {
            if (mediaPlayer != null) retVal = mediaPlayer!!.duration
        }
        if (retVal <= 0 && media != null && media!!.getDuration() > 0) {
            retVal = media!!.getDuration()
        }
        return retVal
    }

    /**
     * Returns the position of the current media object or INVALID_TIME if the position could not be retrieved.
     */
    override fun getPosition(): Int {
        var retVal = Playable.INVALID_TIME
        if (playerStatus.isAtLeast(PlayerStatus.PREPARED)) {
            if (mediaPlayer != null) retVal = mediaPlayer!!.currentPosition
        }
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

    /**
     * Sets the playback speed.
     * This method is executed on an internal executor service.
     */
    override fun setPlaybackParams(speed: Float, skipSilence: Boolean) {
        Log.d(TAG, "Playback speed was set to $speed")
        EventBus.getDefault().post(SpeedChangedEvent(speed))
        mediaPlayer?.setPlaybackParams(speed, skipSilence)
    }

    /**
     * Returns the current playback speed. If the playback speed could not be retrieved, 1 is returned.
     */
    override fun getPlaybackSpeed(): Float {
        var retVal = 1f
        if (playerStatus == PlayerStatus.PLAYING || playerStatus == PlayerStatus.PAUSED || playerStatus == PlayerStatus.INITIALIZED || playerStatus == PlayerStatus.PREPARED) {
            if (mediaPlayer != null) retVal = mediaPlayer!!.currentSpeedMultiplier
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
        if (playable is FeedMedia && playable.getItem()?.feed?.preferences != null) {
            val preferences = playable.getItem()!!.feed!!.preferences!!
            val volumeAdaptionSetting = preferences.volumeAdaptionSetting
            if (volumeAdaptionSetting != null) {
                val adaptionFactor = volumeAdaptionSetting.adaptionFactor
                volumeLeft *= adaptionFactor
                volumeRight *= adaptionFactor
            }
        }
        mediaPlayer?.setVolume(volumeLeft, volumeRight)
        Log.d(TAG, "Media player volume was set to $volumeLeft $volumeRight")
    }

    override fun getCurrentMediaType(): MediaType {
        return mediaType
    }

    override fun isStreaming(): Boolean {
        return stream
    }

    /**
     * Releases internally used resources. This method should only be called when the object is not used anymore.
     */
    override fun shutdown() {
        if (mediaPlayer != null) {
            try {
                clearMediaPlayerListeners()
                if (mediaPlayer!!.isPlaying) {
                    mediaPlayer!!.stop()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            mediaPlayer!!.release()
            mediaPlayer = null
            playerStatus = PlayerStatus.STOPPED
        }
        isShutDown = true
        abandonAudioFocus()
        releaseWifiLockIfNecessary()
    }

    override fun setVideoSurface(surface: SurfaceHolder?) {
        mediaPlayer?.setDisplay(surface)
    }

    override fun resetVideoSurface() {
        if (mediaType == MediaType.VIDEO) {
            Log.d(TAG, "Resetting video surface")
            mediaPlayer?.setDisplay(null)
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
        if (mediaPlayer != null && playerStatus != PlayerStatus.ERROR && mediaType == MediaType.VIDEO) {
            videoSize = Pair(mediaPlayer!!.videoWidth, mediaPlayer!!.videoHeight)
        }
        return videoSize
    }

    /**
     * Returns the current media, if you need the media and the player status together, you should
     * use getPSMPInfo() to make sure they're properly synchronized. Otherwise a race condition
     * could result in nonsensical results (like a status of PLAYING, but a null playable)
     * @return the current media. May be null
     */
    override fun getPlayable(): Playable? {
        return media
    }

    override fun setPlayable(playable: Playable?) {
        media = playable
    }

    override fun getAudioTracks(): List<String> {
        return mediaPlayer?.audioTracks?: listOf()
    }

    override fun setAudioTrack(track: Int) {
        if (mediaPlayer != null) mediaPlayer!!.setAudioTrack(track)
    }

    override fun getSelectedAudioTrack(): Int {
        return mediaPlayer?.selectedAudioTrack?:0
    }

    private fun createMediaPlayer() {
        mediaPlayer?.release()

        if (media == null) {
            mediaPlayer = null
            playerStatus = PlayerStatus.STOPPED
            return
        }

        mediaPlayer = ExoPlayerWrapper(context)
        mediaPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
        setMediaPlayerListeners(mediaPlayer)
    }

    private val audioFocusChangeListener = OnAudioFocusChangeListener { focusChange ->
        if (isShutDown) {
            return@OnAudioFocusChangeListener
        }
        when {
            !PlaybackService.isRunning -> {
                abandonAudioFocus()
                Log.d(TAG, "onAudioFocusChange: PlaybackService is no longer running")
                return@OnAudioFocusChangeListener
            }
            focusChange == AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Lost audio focus")
                pause(true, false)
                callback.shouldStop()
            }
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
                    && !UserPreferences.shouldPauseForFocusLoss() -> {
                if (playerStatus == PlayerStatus.PLAYING) {
                    Log.d(TAG, "Lost audio focus temporarily. Ducking...")
                    setVolume(0.25f, 0.25f)
                    pausedBecauseOfTransientAudiofocusLoss = false
                }
            }
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (playerStatus == PlayerStatus.PLAYING) {
                    Log.d(TAG, "Lost audio focus temporarily. Pausing...")
                    mediaPlayer?.pause() // Pause without telling the PlaybackService
                    pausedBecauseOfTransientAudiofocusLoss = true

                    audioFocusCanceller.removeCallbacksAndMessages(null)
                    audioFocusCanceller.postDelayed({
                        if (pausedBecauseOfTransientAudiofocusLoss) {
                            // Still did not get back the audio focus. Now actually pause.
                            pause(true, false)
                        }
                    }, 30000)
                }
            }
            focusChange == AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Gained audio focus")
                audioFocusCanceller.removeCallbacksAndMessages(null)
                if (pausedBecauseOfTransientAudiofocusLoss) { // we paused => play now
                    mediaPlayer?.start()
                } else { // we ducked => raise audio level back
                    setVolume(1.0f, 1.0f)
                }
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

    override fun endPlayback(hasEnded: Boolean, wasSkipped: Boolean,
                             shouldContinue: Boolean, toStoppedState: Boolean
    ) {
        releaseWifiLockIfNecessary()

        val isPlaying = playerStatus == PlayerStatus.PLAYING

        // we're relying on the position stored in the Playable object for post-playback processing
        val position = getPosition()
        if (position >= 0) {
            media?.setPosition(position)
        }

        mediaPlayer?.reset()

        abandonAudioFocus()

        val currentMedia = media
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
                media = null
                playMediaObject(nextMedia, false, !nextMedia.localFileAvailable(), isPlaying, isPlaying)
            }
        }
        if (shouldContinue || toStoppedState) {
            if (nextMedia == null) {
                callback.onPlaybackEnded(null, true)
                stop()
            }
            val hasNext = nextMedia != null

            callback.onPostPlayback(currentMedia!!, hasEnded, wasSkipped, hasNext)
        } else if (isPlaying) {
            callback.onPlaybackPause(currentMedia, currentMedia!!.getPosition())
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

        if (playerStatus == PlayerStatus.INDETERMINATE) {
            setPlayerStatus(PlayerStatus.STOPPED, null)
        } else {
            Log.d(TAG, "Ignored call to stop: Current player state is: $playerStatus")
        }
    }

    override fun shouldLockWifi(): Boolean {
        return stream
    }

    private fun setMediaPlayerListeners(mp: ExoPlayerWrapper?) {
        if (mp == null || media == null) {
            return
        }
        mp.setOnCompletionListener(Runnable { endPlayback(true, false, true, true) })
        mp.setOnSeekCompleteListener(Runnable { this.genericSeekCompleteListener() })
        mp.setOnBufferingUpdateListener(Consumer { percent: Int ->
            when (percent) {
                ExoPlayerWrapper.BUFFERING_STARTED -> {
                    EventBus.getDefault().post(BufferUpdateEvent.started())
                }
                ExoPlayerWrapper.BUFFERING_ENDED -> {
                    EventBus.getDefault().post(BufferUpdateEvent.ended())
                }
                else -> {
                    EventBus.getDefault().post(BufferUpdateEvent.progressUpdate(0.01f * percent))
                }
            }
        })
        mp.setOnErrorListener(Consumer { message: String ->
            EventBus.getDefault().postSticky(ac.mdiq.podcini.util.event.PlayerErrorEvent(message))
        })
    }

    private fun clearMediaPlayerListeners() {
        if (mediaPlayer == null) return
        mediaPlayer!!.setOnCompletionListener {}
        mediaPlayer!!.setOnSeekCompleteListener {}
        mediaPlayer!!.setOnBufferingUpdateListener { }
        mediaPlayer!!.setOnErrorListener { x: String? -> }
    }

    private fun genericSeekCompleteListener() {
        Log.d(TAG, "genericSeekCompleteListener")
        seekLatch?.countDown()

        if (playerStatus == PlayerStatus.PLAYING) {
            if (media != null) callback.onPlaybackStart(media!!, getPosition())
        }
        if (playerStatus == PlayerStatus.SEEKING && statusBeforeSeeking != null) {
            setPlayerStatus(statusBeforeSeeking!!, media, getPosition())
        }
    }

    override fun isCasting(): Boolean {
        return false
    }

    companion object {
        private const val TAG = "LclPlaybackSvcMPlayer"
    }
}
