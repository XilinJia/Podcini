package ac.mdiq.podcini.playback.service

import ac.mdiq.podcini.R
import ac.mdiq.podcini.feed.util.PlaybackSpeedUtils
import ac.mdiq.podcini.net.download.service.HttpCredentialEncoder
import ac.mdiq.podcini.net.download.service.PodciniHttpClient
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.base.RewindAfterPauseUtils
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.storage.model.playback.MediaType
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.NetworkUtils.wasDownloadBlocked
import ac.mdiq.podcini.util.config.ClientConfig
import ac.mdiq.podcini.util.event.PlayerErrorEvent
import ac.mdiq.podcini.util.event.playback.BufferUpdateEvent
import ac.mdiq.podcini.util.event.playback.SpeedChangedEvent
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.util.Log
import android.util.Pair
import android.view.SurfaceHolder
import androidx.core.util.Consumer
import androidx.media.AudioAttributesCompat
import androidx.media3.common.*
import androidx.media3.common.Player.*
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSourceFactory
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector.SelectionOverride
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.ui.DefaultTrackNameProvider
import androidx.media3.ui.TrackNameProvider
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
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
class LocalMediaPlayer(context: Context, callback: MediaPlayerCallback) : MediaPlayerBase(context, callback) {

    @Volatile
    private var statusBeforeSeeking: PlayerStatus? = null

    @Volatile
    private var playable: Playable? = null

    @Volatile
    private var isStreaming = false

    @Volatile
    private var mediaType: MediaType
    private val startWhenPrepared = AtomicBoolean(false)

    @Volatile
    private var videoSize: Pair<Int, Int>? = null
    private var isShutDown = false
    private var seekLatch: CountDownLatch? = null

    private val bufferUpdateInterval = 5L
    private val bufferingUpdateDisposable: Disposable
    private var mediaSource: MediaSource? = null
    private var playbackParameters: PlaybackParameters

    private val formats: List<Format>
        get() {
            val formats: MutableList<Format> = arrayListOf()
            val trackInfo = trackSelector!!.currentMappedTrackInfo ?: return emptyList()
            val trackGroups = trackInfo.getTrackGroups(audioRendererIndex)
            for (i in 0 until trackGroups.length) {
                formats.add(trackGroups[i].getFormat(0))
            }
            return formats
        }

    private val audioRendererIndex: Int
        get() {
            for (i in 0 until exoPlayer!!.rendererCount) {
                if (exoPlayer?.getRendererType(i) == C.TRACK_TYPE_AUDIO) return i
            }
            return -1
        }

    private val videoWidth: Int
        get() {
            return exoPlayer?.videoFormat?.width ?: 0
        }

    private val videoHeight: Int
        get() {
            return exoPlayer?.videoFormat?.height ?: 0
        }

    @Throws(IllegalStateException::class)
    private fun prepareWR() {
        if (mediaSource == null) return
        exoPlayer?.setMediaSource(mediaSource!!, false)
        exoPlayer?.prepare()
    }

    private fun release() {
        bufferingUpdateDisposable.dispose()

//        exoplayerListener = null
        exoPlayer?.stop()
        exoPlayer?.seekTo(0L)
        audioSeekCompleteListener = null
        audioCompletionListener = null
        audioErrorListener = null
        bufferingUpdateListener = null
    }

    private fun setAudioStreamType(i: Int) {
        val a = exoPlayer!!.audioAttributes
        val b = AudioAttributes.Builder()
        b.setContentType(i)
        b.setFlags(a.flags)
        b.setUsage(a.usage)
        exoPlayer?.setAudioAttributes(b.build(), true)
    }

    private fun metadata(p: Playable): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .setArtist(p.getFeedTitle())
            .setTitle(p.getEpisodeTitle())
            .setAlbumArtist(p.getFeedTitle())
            .setDisplayTitle(p.getEpisodeTitle())
            .setSubtitle(p.getFeedTitle())
            .setArtworkUri(null)
        return builder.build()
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    private fun setDataSource(m: MediaMetadata, s: String, user: String?, password: String?) {
        Logd(TAG, "setDataSource: $s")

        val httpDataSourceFactory = OkHttpDataSource.Factory(PodciniHttpClient.getHttpClient() as okhttp3.Call.Factory)
            .setUserAgent(ClientConfig.USER_AGENT)

        if (!user.isNullOrEmpty() && !password.isNullOrEmpty()) {
            val requestProperties = HashMap<String, String>()
            requestProperties["Authorization"] = HttpCredentialEncoder.encode(user, password, "ISO-8859-1")
            httpDataSourceFactory.setDefaultRequestProperties(requestProperties)
        }
        val dataSourceFactory: DataSource.Factory = DefaultDataSourceFactory(context, null, httpDataSourceFactory)
        val extractorsFactory = DefaultExtractorsFactory()
        extractorsFactory.setConstantBitrateSeekingEnabled(true)
        extractorsFactory.setMp3ExtractorFlags(Mp3Extractor.FLAG_DISABLE_ID3_METADATA)
        val f = ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(s))
            .setMediaMetadata(m).build()

        mediaSource = f.createMediaSource(mediaItem)
    }

    private fun play() {
        if (exoPlayer?.playbackState == STATE_IDLE || exoPlayer?.playbackState == STATE_ENDED ) prepareWR()
        exoPlayer?.play()
        // Can't set params when paused - so always set it on start in case they changed
        exoPlayer!!.playbackParameters = playbackParameters
    }

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
        Logd(TAG, "playMediaObject(...)")
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
       Logd(TAG, "playMediaObject ${playable.getEpisodeTitle()} $forceReset $stream $startWhenPrepared $prepareImmediately")
        if (this.playable != null) {
            if (!forceReset && this.playable!!.getIdentifier() == playable.getIdentifier() && status == PlayerStatus.PLAYING) {
                // episode is already playing -> ignore method call
                Logd(TAG, "Method call to playMediaObject was ignored: media file already playing.")
                return
            } else {
                // stop playback of this episode
                if (status == PlayerStatus.PAUSED || (status == PlayerStatus.PLAYING) || status == PlayerStatus.PREPARED)
                    exoPlayer?.stop()

                // set temporarily to pause in order to update list with current position
                if (status == PlayerStatus.PLAYING) callback.onPlaybackPause(this.playable, getPosition())
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
        this@LocalMediaPlayer.startWhenPrepared.set(startWhenPrepared)
        setPlayerStatus(PlayerStatus.INITIALIZING, this.playable)
        val metadata = metadata(playable)
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
                            setDataSource(metadata, streamurl, preferences.username, preferences.password)
                        } else setDataSource(metadata, streamurl, null, null)
                    }
                }
                else -> {
                    val localMediaurl = this.playable!!.getLocalMediaUrl()
                    if (!localMediaurl.isNullOrEmpty() && File(localMediaurl).canRead()) setDataSource(metadata, localMediaurl, null, null)
                    else throw IOException("Unable to read local file $localMediaurl")
                }
            }
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            if (uiModeManager.currentModeType != Configuration.UI_MODE_TYPE_CAR) setPlayerStatus(PlayerStatus.INITIALIZED, this.playable)

            if (prepareImmediately) {
                setPlayerStatus(PlayerStatus.PREPARING, this.playable)
                prepareWR()
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
        if (status == PlayerStatus.PAUSED || status == PlayerStatus.PREPARED) {
            Logd(TAG, "Audiofocus successfully requested")
            Logd(TAG, "Resuming/Starting playback")
            acquireWifiLockIfNecessary()
            setPlaybackParams(PlaybackSpeedUtils.getCurrentPlaybackSpeed(playable), UserPreferences.isSkipSilence)
            setVolume(1.0f, 1.0f)

            if (playable != null && status == PlayerStatus.PREPARED && playable!!.getPosition() > 0) {
                val newPosition = RewindAfterPauseUtils.calculatePositionWithRewind(playable!!.getPosition(), playable!!.getLastPlayedTime())
                seekTo(newPosition)
            }
            play()
            setPlayerStatus(PlayerStatus.PLAYING, playable)
        } else Logd(TAG, "Call to resume() was ignored because current state of PSMP object is $status")
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
        if (status == PlayerStatus.PLAYING) {
            Logd(TAG, "Pausing playback.")
            exoPlayer?.pause()
            setPlayerStatus(PlayerStatus.PAUSED, playable, getPosition())
            if (isStreaming && reinit) reinit()
        } else {
            Logd(TAG, "Ignoring call to pause: Player is in $status state")
        }
    }

    /**
     * Prepares media player for playback if the service is in the INITALIZED
     * state.
     *
     *
     * This method is executed on an internal executor service.
     */
    override fun prepare() {
        if (status == PlayerStatus.INITIALIZED) {
            Logd(TAG, "Preparing media player")
            setPlayerStatus(PlayerStatus.PREPARING, playable)
            prepareWR()
            onPrepared(startWhenPrepared.get())
        }
    }

    /**
     * Called after media player has been prepared. This method is executed on the caller's thread.
     */
    private fun onPrepared(startWhenPrepared: Boolean) {
//        TODO: appears the check is not needed
//        check(status == PlayerStatus.PREPARING) { "Player is not in PREPARING state" }
        Logd(TAG, "Resource prepared")
        if (mediaType == MediaType.VIDEO) videoSize = Pair(videoWidth, videoHeight)
        if (playable != null) {
            val pos = playable!!.getPosition()
            if (pos > 0) seekTo(pos)
            if (playable!!.getDuration() <= 0) {
                Logd(TAG, "Setting duration of media")
                playable!!.setDuration(if (exoPlayer?.duration == C.TIME_UNSET) Playable.INVALID_TIME else exoPlayer!!.duration.toInt())
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
        Logd(TAG, "reinit()")
        releaseWifiLockIfNecessary()
        when {
            playable != null -> playMediaObject(playable!!, true, isStreaming, startWhenPrepared.get(), false)
            else -> {
//                if (exoPlayer == null) createStaticPlayer(context)
                Logd(TAG, "Call to reinit: media and mediaPlayer were null")
            }
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
            Logd(TAG, "Seek reached end of file, skipping to next episode")
            exoPlayer?.seekTo(t.toLong())
            audioSeekCompleteListener?.run()
            endPlayback(true, wasSkipped = true, true, toStoppedState = true)
//            return
        }

        when (status) {
            PlayerStatus.PLAYING, PlayerStatus.PAUSED, PlayerStatus.PREPARED -> {
                if (seekLatch != null && seekLatch!!.count > 0) {
                    try {
                        seekLatch!!.await(3, TimeUnit.SECONDS)
                    } catch (e: InterruptedException) {
                        Log.e(TAG, Log.getStackTraceString(e))
                    }
                }
                seekLatch = CountDownLatch(1)
                statusBeforeSeeking = status
                setPlayerStatus(PlayerStatus.SEEKING, playable, getPosition())
                exoPlayer?.seekTo(t.toLong())
                audioSeekCompleteListener?.run()
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
        if (status == PlayerStatus.PLAYING || status == PlayerStatus.PAUSED || status == PlayerStatus.PREPARED)
            retVal = if (exoPlayer?.duration == C.TIME_UNSET) Playable.INVALID_TIME else exoPlayer!!.duration.toInt()

        if (retVal <= 0) {
            val playableDur = playable?.getDuration() ?: -1
            if (playableDur > 0) retVal = playableDur
        }
        return retVal
    }

    /**
     * Returns the position of the current media object or INVALID_TIME if the position could not be retrieved.
     */
    override fun getPosition(): Int {
        var retVal = Playable.INVALID_TIME
//        Log.d(TAG, "getPosition() ${playable?.getIdentifier()} $status")
        if (status.isAtLeast(PlayerStatus.PREPARED)) retVal = exoPlayer!!.currentPosition.toInt()

        if (retVal <= 0) {
            val playablePos = playable?.getPosition() ?: -1
            if (playablePos >= 0) retVal = playablePos
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
        EventBus.getDefault().post(SpeedChangedEvent(speed))
        Logd(TAG, "setPlaybackParams speed=$speed pitch=${playbackParameters.pitch} skipSilence=$skipSilence")
        playbackParameters = PlaybackParameters(speed, playbackParameters.pitch)
        exoPlayer!!.skipSilenceEnabled = skipSilence
        exoPlayer!!.playbackParameters = playbackParameters
    }

    /**
     * Returns the current playback speed. If the playback speed could not be retrieved, 1 is returned.
     */
    override fun getPlaybackSpeed(): Float {
        var retVal = 1f
        if (status == PlayerStatus.PLAYING|| status == PlayerStatus.PAUSED || status == PlayerStatus.INITIALIZED
                || status == PlayerStatus.PREPARED) retVal = playbackParameters.speed

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

        if (volumeLeft > 1) {
            exoPlayer!!.volume = 1f
            loudnessEnhancer?.setEnabled(true)
            loudnessEnhancer?.setTargetGain((1000 * (volumeLeft - 1)).toInt())
        } else {
            exoPlayer!!.volume = volumeLeft
            loudnessEnhancer?.setEnabled(false)
        }

        Logd(TAG, "Media player volume was set to $volumeLeft $volumeRight")
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
        try {
            clearMediaPlayerListeners()
//            TODO: should use: exoPlayer!!.playWhenReady ?
            if (exoPlayer!!.isPlaying) exoPlayer?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        release()
        status = PlayerStatus.STOPPED

        isShutDown = true
        releaseWifiLockIfNecessary()
    }

    override fun setVideoSurface(surface: SurfaceHolder?) {
        exoPlayer?.setVideoSurfaceHolder(surface)
    }

    override fun resetVideoSurface() {
        if (mediaType == MediaType.VIDEO) {
            Logd(TAG, "Resetting video surface")
            exoPlayer?.setVideoSurfaceHolder(null)
            reinit()
        } else Log.e(TAG, "Resetting video surface for media of Audio type")
    }

    /**
     * Return width and height of the currently playing video as a pair.
     *
     * @return Width and height as a Pair or null if the video size could not be determined. The method might still
     * return an invalid non-null value if the getVideoWidth() and getVideoHeight() methods of the media player return
     * invalid values.
     */
    override fun getVideoSize(): Pair<Int, Int>? {
        if (status != PlayerStatus.ERROR && mediaType == MediaType.VIDEO)
            videoSize = Pair(videoWidth, videoHeight)
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
        val trackNames: MutableList<String> = ArrayList()
        val trackNameProvider: TrackNameProvider = DefaultTrackNameProvider(context.resources)
        for (format in formats) {
            trackNames.add(trackNameProvider.getTrackName(format))
        }
        return trackNames
    }

    override fun setAudioTrack(track: Int) {
        val trackInfo = trackSelector!!.currentMappedTrackInfo ?: return
        val trackGroups = trackInfo.getTrackGroups(audioRendererIndex)
        val override = SelectionOverride(track, 0)
        val rendererIndex = audioRendererIndex
        val params = trackSelector!!.buildUponParameters().setSelectionOverride(rendererIndex, trackGroups, override)
        trackSelector!!.setParameters(params)
    }

    override fun getSelectedAudioTrack(): Int {
        val trackSelections = exoPlayer!!.currentTrackSelections
        val availableFormats = formats
        Logd(TAG, "selectedAudioTrack called tracks: ${trackSelections.length} formats: ${availableFormats.size}")
        for (i in 0 until trackSelections.length) {
            val track = trackSelections[i] as? ExoTrackSelection ?: continue
            if (availableFormats.contains(track.selectedFormat)) return availableFormats.indexOf(track.selectedFormat)
        }
        return -1
    }

    override fun createMediaPlayer() {
        release()
        if (playable == null) {
            status = PlayerStatus.STOPPED
            return
        }
        setAudioStreamType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
        setMediaPlayerListeners()
    }

    init {
        mediaType = MediaType.UNKNOWN

        if (exoPlayer == null) {
            setupPlayerListener()
            createStaticPlayer(context)
        }
        playbackParameters = exoPlayer!!.playbackParameters
        bufferingUpdateDisposable = Observable.interval(bufferUpdateInterval, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                bufferingUpdateListener?.accept(exoPlayer!!.bufferedPercentage)
            }
    }

    override fun endPlayback(hasEnded: Boolean, wasSkipped: Boolean, shouldContinue: Boolean, toStoppedState: Boolean) {
        releaseWifiLockIfNecessary()

        val isPlaying = status == PlayerStatus.PLAYING
        // we're relying on the position stored in the Playable object for post-playback processing
        val position = getPosition()
        if (position >= 0) playable?.setPosition(position)

        Logd(TAG, "endPlayback $hasEnded $wasSkipped $shouldContinue $toStoppedState")
//        printStackTrace()

        val currentMedia = playable
        var nextMedia: Playable? = null
        if (shouldContinue) {
            // Load next episode if previous episode was in the queue and if there
            // is an episode in the queue left.
            // Start playback immediately if continuous playback is enabled
            nextMedia = callback.getNextInQueue(currentMedia)
            if (nextMedia != null) {
                Logd(TAG, "has nextMedia. call callback.onPlaybackEnded false")
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
                    Logd(TAG, "nextMedia is null. call callback.onPlaybackEnded true")
                    callback.onPlaybackEnded(null, true)
                    playable = null
                    exoPlayer?.stop()
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
        if (status == PlayerStatus.INDETERMINATE) setPlayerStatus(PlayerStatus.STOPPED, null)
        else Logd(TAG, "Ignored call to stop: Current player state is: $status")
    }

    override fun shouldLockWifi(): Boolean {
        return isStreaming
    }

    private fun setMediaPlayerListeners() {
        if (playable == null) return

        audioCompletionListener = Runnable {
            Logd(TAG, "audioCompletionListener called")
            endPlayback(hasEnded = true, wasSkipped = false, shouldContinue = true, toStoppedState = true)
        }
        audioSeekCompleteListener = Runnable { this.genericSeekCompleteListener() }
        bufferingUpdateListener = Consumer<Int> { percent: Int ->
            when (percent) {
                BUFFERING_STARTED -> EventBus.getDefault().post(BufferUpdateEvent.started())
                BUFFERING_ENDED -> EventBus.getDefault().post(BufferUpdateEvent.ended())
                else -> EventBus.getDefault().post(BufferUpdateEvent.progressUpdate(0.01f * percent))
            }
        }
        audioErrorListener = Consumer<String> { message: String ->
            Log.e(TAG, "PlayerErrorEvent: $message")
            EventBus.getDefault().postSticky(PlayerErrorEvent(message))
        }
    }

    private fun clearMediaPlayerListeners() {
        audioCompletionListener = Runnable {}
        audioSeekCompleteListener = Runnable {}
        bufferingUpdateListener = Consumer<Int> { }
        audioErrorListener = Consumer<String> {}
    }

    private fun genericSeekCompleteListener() {
        Logd(TAG, "genericSeekCompleteListener $status ${exoPlayer?.isPlaying} $statusBeforeSeeking")
        seekLatch?.countDown()

        if ((status == PlayerStatus.PLAYING || exoPlayer?.isPlaying != true) && playable != null) callback.onPlaybackStart(playable!!, getPosition())
        if (status == PlayerStatus.SEEKING && statusBeforeSeeking != null) setPlayerStatus(statusBeforeSeeking!!, playable, getPosition())
    }

    override fun isCasting(): Boolean {
        return false
    }

    private fun setupPlayerListener() {
        exoplayerListener = object : Listener {
            override fun onPlaybackStateChanged(playbackState: @State Int) {
                Logd(TAG, "onPlaybackStateChanged $playbackState")
                when (playbackState) {
                    STATE_ENDED -> {
                        exoPlayer?.seekTo(C.TIME_UNSET)
                        if (audioCompletionListener != null) audioCompletionListener?.run()
                    }
                    STATE_BUFFERING -> bufferingUpdateListener?.accept(BUFFERING_STARTED)
                    else -> bufferingUpdateListener?.accept(BUFFERING_ENDED)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val stat = if (isPlaying) PlayerStatus.PLAYING else PlayerStatus.PAUSED
                setPlayerStatus(stat, playable)
                Logd(TAG, "onIsPlayingChanged $isPlaying")
            }

            override fun onPlayerError(error: PlaybackException) {
                Logd(TAG, "onPlayerError ${error.message}")
                if (wasDownloadBlocked(error)) audioErrorListener?.accept(context.getString(R.string.download_error_blocked))
                else {
                    var cause = error.cause
                    if (cause is HttpDataSourceException && cause.cause != null) cause = cause.cause
                    if (cause != null && "Source error" == cause.message) cause = cause.cause
                    audioErrorListener?.accept(if (cause != null) cause.message else error.message)
                }
            }

            override fun onPositionDiscontinuity(oldPosition: PositionInfo, newPosition: PositionInfo, reason: @DiscontinuityReason Int) {
                Logd(TAG, "onPositionDiscontinuity $oldPosition $newPosition $reason")
                if (reason == DISCONTINUITY_REASON_SEEK) audioSeekCompleteListener?.run()
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                Logd(TAG, "onAudioSessionIdChanged $audioSessionId")
                initLoudnessEnhancer(audioSessionId)
            }
        }
    }

    companion object {
        private const val TAG = "LocalMediaPlayer"

        const val BUFFERING_STARTED: Int = -1
        const val BUFFERING_ENDED: Int = -2
        const val ERROR_CODE_OFFSET: Int = 1000

        private var trackSelector: DefaultTrackSelector? = null
        var exoPlayer: ExoPlayer? = null

        private var exoplayerListener: Listener? = null
        private var audioSeekCompleteListener: Runnable? = null
        private var audioCompletionListener: Runnable? = null
        private var audioErrorListener: Consumer<String>? = null
        private var bufferingUpdateListener: Consumer<Int>? = null
        private var loudnessEnhancer: LoudnessEnhancer? = null

        fun createStaticPlayer(context: Context) {
            val loadControl = DefaultLoadControl.Builder()
            loadControl.setBufferDurationsMs(30000, 120000, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
            loadControl.setBackBuffer(UserPreferences.rewindSecs * 1000 + 500, true)
            trackSelector = DefaultTrackSelector(context)
            val audioOffloadPreferences = AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED) // Add additional options as needed
                .setIsGaplessSupportRequired(true)
                .setIsSpeedChangeSupportRequired(true)
                .build()
            Logd(TAG, "createStaticPlayer creating exoPlayer_")

            exoPlayer = ExoPlayer.Builder(context, DefaultRenderersFactory(context))
                .setTrackSelector(trackSelector!!)
                .setLoadControl(loadControl.build())
                .build()

            exoPlayer?.setSeekParameters(SeekParameters.EXACT)
            exoPlayer!!.trackSelectionParameters = exoPlayer!!.trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(audioOffloadPreferences)
                .build()

            if (exoplayerListener != null) {
                exoPlayer?.removeListener(exoplayerListener!!)
                exoPlayer?.addListener(exoplayerListener!!)
            }
            initLoudnessEnhancer(exoPlayer!!.audioSessionId)
        }

        private fun initLoudnessEnhancer(audioStreamId: Int) {
            val newEnhancer = LoudnessEnhancer(audioStreamId)
            val oldEnhancer = loudnessEnhancer
            if (oldEnhancer != null) {
                newEnhancer.setEnabled(oldEnhancer.enabled)
                if (oldEnhancer.enabled) newEnhancer.setTargetGain(oldEnhancer.targetGain.toInt())
                oldEnhancer.release()
            }
            loudnessEnhancer = newEnhancer
        }

        fun cleanup() {
            if (exoplayerListener != null) exoPlayer?.removeListener(exoplayerListener!!)
            exoplayerListener = null
            audioSeekCompleteListener = null
            audioCompletionListener = null
            audioErrorListener = null
            bufferingUpdateListener = null
            loudnessEnhancer = null
        }
    }
}
