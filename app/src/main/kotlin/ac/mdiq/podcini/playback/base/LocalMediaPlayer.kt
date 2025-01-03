package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.PodciniHttpClient
import ac.mdiq.podcini.net.utils.NetworkUtils.wasDownloadBlocked
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curIndexInQueue
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.preferences.AppPreferences.fastForwardSecs
import ac.mdiq.podcini.preferences.AppPreferences.isSkipSilence
import ac.mdiq.podcini.preferences.AppPreferences.rewindSecs
import ac.mdiq.podcini.storage.database.Episodes
import ac.mdiq.podcini.storage.database.Episodes.setPlayStateSync
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.MediaType
import ac.mdiq.podcini.storage.model.PlayState
import ac.mdiq.podcini.storage.model.VolumeAdaptionSetting
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.FlowEvent.PlayEvent.Action
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.config.ClientConfig
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import android.util.Pair
import android.view.SurfaceHolder
import androidx.core.util.Consumer
import androidx.media3.common.*
import androidx.media3.common.Player.*
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector.SelectionOverride
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.ui.DefaultTrackNameProvider
import androidx.media3.ui.TrackNameProvider
import kotlinx.coroutines.*
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LocalMediaPlayer(context: Context, callback: MediaPlayerCallback) : MediaPlayerBase(context, callback) {

    @Volatile
    private var statusBeforeSeeking: PlayerStatus? = null

    @Volatile
    private var videoSize: Pair<Int, Int>? = null
    private var isShutDown = false
    private var seekLatch: CountDownLatch? = null

    private val bufferUpdateInterval = 5000L
    private var playbackParameters: PlaybackParameters

    private var bufferedPercentagePrev = 0

    private val formats: List<Format>
        get() {
            val formats_: MutableList<Format> = arrayListOf()
            val trackInfo = trackSelector!!.currentMappedTrackInfo ?: return emptyList()
            val trackGroups = trackInfo.getTrackGroups(audioRendererIndex)
            for (i in 0 until trackGroups.length) {
                formats_.add(trackGroups[i].getFormat(0))
            }
            return formats_
        }

    private val audioRendererIndex: Int
        get() {
            for (i in 0 until exoPlayer!!.rendererCount) {
                if (exoPlayer?.getRendererType(i) == C.TRACK_TYPE_AUDIO) return i
            }
            return -1
        }

    private val videoWidth: Int
        get() = exoPlayer?.videoFormat?.width ?: 0

    private val videoHeight: Int
        get() = exoPlayer?.videoFormat?.height ?: 0

    init {
        if (httpDataSourceFactory == null) {
            runOnIOScope {
                httpDataSourceFactory = OkHttpDataSource.Factory(PodciniHttpClient.getHttpClient() as okhttp3.Call.Factory).setUserAgent(ClientConfig.USER_AGENT)
            }
        }
        if (exoPlayer == null) {
            exoplayerListener = object : Listener {
                override fun onPlaybackStateChanged(playbackState: @State Int) {
                    Logd(TAG, "onPlaybackStateChanged $playbackState")
                    when (playbackState) {
                        STATE_ENDED -> {
                            exoPlayer?.seekTo(C.TIME_UNSET)
                            audioCompletionListener?.run()
                        }
                        STATE_BUFFERING -> bufferingUpdateListener?.accept(BUFFERING_STARTED)
                        else -> bufferingUpdateListener?.accept(BUFFERING_ENDED)
                    }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
//                    val stat = if (isPlaying) PlayerStatus.PLAYING else PlayerStatus.PAUSED
//                    TODO: test: changing PAUSED to STOPPED or INDETERMINATE makes resume not possible if interrupted
                    val stat = if (isPlaying) PlayerStatus.PLAYING else PlayerStatus.PAUSED
                    setPlayerStatus(stat, curEpisode)
                    Logd(TAG, "onIsPlayingChanged $isPlaying")
                }
                override fun onPlayerError(error: PlaybackException) {
                    Log.d(TAG, "onPlayerError ${error.message}")
                    if (wasDownloadBlocked(error)) audioErrorListener?.accept(context.getString(R.string.download_error_blocked))
                    else {
                        var cause = error.cause
                        if (cause is HttpDataSourceException && cause.cause != null) cause = cause.cause
                        if (cause != null && "Source error" == cause.message) cause = cause.cause
                        audioErrorListener?.accept((if (cause != null) cause.message else error.message) ?:"no message")
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
            createStaticPlayer(context)
        }
        playbackParameters = exoPlayer!!.playbackParameters
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            while (true) {
                delay(bufferUpdateInterval)
                withContext(Dispatchers.Main) {
                    if (exoPlayer != null && bufferedPercentagePrev != exoPlayer!!.bufferedPercentage) {
                        bufferingUpdateListener?.accept(exoPlayer!!.bufferedPercentage)
                        bufferedPercentagePrev = exoPlayer!!.bufferedPercentage
                    }
                }
            }
        }
    }

    private fun release() {
        Logd(TAG, "release() called")
        exoPlayer?.stop()
        exoPlayer?.seekTo(0L)
        audioSeekCompleteListener = null
        audioCompletionListener = null
        audioErrorListener = null
        bufferingUpdateListener = null
    }

    /**
     * Starts or prepares playback of the specified EpisodeMedia object. If another EpisodeMedia object is already being played, the currently playing
     * episode will be stopped and replaced with the new EpisodeMedia object. If the EpisodeMedia object is already being played, the method will not do anything.
     * Whether playback starts immediately depends on the given parameters. See below for more details.
     * States:
     * During execution of the method, the object will be in the INITIALIZING state. The end state depends on the given parameters.
     * If 'prepareImmediately' is set to true, the method will go into PREPARING state and after that into PREPARED state.
     * If 'startWhenPrepared' is set to true, the method will additionally go into PLAYING state.
     * If an unexpected error occurs while loading the EpisodeMedia's metadata or while setting the MediaPlayers data source, the object will enter the ERROR state.
     * This method is executed on an internal executor service.
     * @param playable           The EpisodeMedia object that is supposed to be played. This parameter must not be null.
     * @param streaming             The type of playback. If false, the EpisodeMedia object MUST provide access to a locally available file via
     * getLocalMediaUrl. If true, the EpisodeMedia object MUST provide access to a resource that can be streamed by
     * the Android MediaPlayer via getStreamUrl.
     * @param startWhenPrepared  Sets the 'startWhenPrepared' flag. This flag determines whether playback will start immediately after the
     * episode has been prepared for playback. Setting this flag to true does NOT mean that the episode will be prepared
     * for playback immediately (see 'prepareImmediately' parameter for more details)
     * @param prepareImmediately Set to true if the method should also prepare the episode for playback.
     */
    override fun playMediaObject(playable: Episode, streaming: Boolean, startWhenPrepared: Boolean, prepareImmediately: Boolean, forceReset: Boolean) {
        Logd(TAG, "playMediaObject status=$status stream=$streaming startWhenPrepared=$startWhenPrepared prepareImmediately=$prepareImmediately forceReset=$forceReset ${playable.getEpisodeTitle()} ")
//       showStackTrace()
        if (curEpisode != null) {
            Logd(TAG, "playMediaObject: curMedia exist status=$status")
            if (!forceReset && curEpisode!!.id == prevMedia?.id && status == PlayerStatus.PLAYING) {
                Logd(TAG, "Method call to playMediaObject was ignored: media file already playing.")
                return
            }
            Logd(TAG, "playMediaObject starts new playable:${playable.id} curMedia:${curEpisode!!.id} prevMedia:${prevMedia?.id}")
            // set temporarily to pause in order to update list with current position
            if (status == PlayerStatus.PLAYING) {
                val pos = curEpisode?.position ?: -1
                seekTo(pos)
                callback.onPlaybackPause(curEpisode, pos)
//                callback.onPostPlayback(curMedia, false, true, true)
            }
            // stop playback of this episode
            if (status == PlayerStatus.PAUSED || status == PlayerStatus.PLAYING || status == PlayerStatus.PREPARED) exoPlayer?.stop()
            if (prevMedia != null && curEpisode?.id != prevMedia?.id)
                callback.onPostPlayback(prevMedia, ended = false, skipped = true, true)
            setPlayerStatus(PlayerStatus.INDETERMINATE, null)
        }

        Logd(TAG, "playMediaObject preparing for playable:${playable.id} ${playable.getEpisodeTitle()}")
        curEpisode = playable
        val media_ = curEpisode!!
        var item = media_
        if (item.playState < PlayState.PROGRESS.code) item = runBlocking { setPlayStateSync(PlayState.PROGRESS.code, item, false) }
        val eList = if (item.feed?.queue != null) curQueue.episodes else item.feed?.getVirtualQueueItems() ?: listOf()
        curIndexInQueue = Episodes.indexOfItemWithId(eList, media_.id)

        prevMedia = curEpisode
        this.isStreaming = streaming
        mediaType = curEpisode!!.getMediaType()
        videoSize = null
        createMediaPlayer()
        this.startWhenPrepared.set(startWhenPrepared)
        setPlayerStatus(PlayerStatus.INITIALIZING, curEpisode)
        val metadata = buildMetadata(curEpisode!!)
        try {
            callback.ensureMediaInfoLoaded(curEpisode!!)
            // TODO: test
            callback.onMediaChanged(true)
            setPlaybackParams(getCurrentPlaybackSpeed(curEpisode), isSkipSilence)
            CoroutineScope(Dispatchers.IO).launch {
                when {
                    streaming -> {
                        val streamurl = curEpisode!!.downloadUrl
                        if (streamurl != null) {
                            mediaItem = null
                            mediaSource = null
                            setDataSource(metadata, curEpisode!!)
                        }
                    }
                    else -> {
                        val localMediaurl = curEpisode!!.fileUrl
                        if (!localMediaurl.isNullOrEmpty()) setDataSource(metadata, localMediaurl, null, null)
                        else throw IOException("Unable to read local file $localMediaurl")
                    }
                }
                withContext(Dispatchers.Main) {
                    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
                    if (uiModeManager.currentModeType != Configuration.UI_MODE_TYPE_CAR) setPlayerStatus(PlayerStatus.INITIALIZED, curEpisode)
                    if (prepareImmediately) prepare()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            setPlayerStatus(PlayerStatus.ERROR, null)
            EventFlow.postEvent(FlowEvent.PlayerErrorEvent(e.localizedMessage ?: ""))
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            setPlayerStatus(PlayerStatus.ERROR, null)
            EventFlow.postEvent(FlowEvent.PlayerErrorEvent(e.localizedMessage ?: ""))
        } finally { }
    }

    override fun resume() {
        Logd(TAG, "resume(): exoPlayer?.playbackState: ${exoPlayer?.playbackState}")
        if (status == PlayerStatus.PAUSED || status == PlayerStatus.PREPARED) {
            Logd(TAG, "Resuming/Starting playback")
            acquireWifiLockIfNecessary()
            setPlaybackParams(getCurrentPlaybackSpeed(curEpisode), isSkipSilence)
            setVolume(1.0f, 1.0f)
            if (curEpisode != null && status == PlayerStatus.PREPARED && curEpisode!!.position > 0) {
                val newPosition = calculatePositionWithRewind(curEpisode!!.position, curEpisode!!.lastPlayedTime)
                seekTo(newPosition)
            }
            if (exoPlayer?.playbackState == STATE_IDLE || exoPlayer?.playbackState == STATE_ENDED ) {
                if (mediaSource != null || mediaItem != null) {
                    if (mediaSource != null) exoPlayer?.setMediaSource(mediaSource!!, false)
                    else exoPlayer?.setMediaItem(mediaItem!!)
                    exoPlayer?.prepare()
                }
            }
            exoPlayer?.play()
            // Can't set params when paused - so always set it on start in case they changed
            exoPlayer?.playbackParameters = playbackParameters
            setPlayerStatus(PlayerStatus.PLAYING, curEpisode)
            if (curEpisode != null) EventFlow.postEvent(FlowEvent.PlayEvent(curEpisode!!))
        } else Logd(TAG, "Call to resume() was ignored because current state of PSMP object is $status")
    }

    override fun pause(reinit: Boolean) {
        releaseWifiLockIfNecessary()
        if (status == PlayerStatus.PLAYING) {
            Logd(TAG, "Pausing playback $reinit")
            exoPlayer?.pause()
            setPlayerStatus(PlayerStatus.PAUSED, curEpisode, getPosition())
            if (curEpisode != null) EventFlow.postEvent(FlowEvent.PlayEvent(curEpisode!!, Action.END))
            if (isStreaming && reinit) reinit()
        } else Logd(TAG, "Ignoring call to pause: Player is in $status state")
    }

    override fun prepare() {
        if (status == PlayerStatus.INITIALIZED) {
            Logd(TAG, "Preparing media player")
            setPlayerStatus(PlayerStatus.PREPARING, curEpisode)
            if (mediaSource != null || mediaItem != null) {
                if (mediaSource != null) exoPlayer?.setMediaSource(mediaSource!!, false)
                else exoPlayer?.setMediaItem(mediaItem!!)
                exoPlayer?.prepare()
            }
//            onPrepared(startWhenPrepared.get())
            if (mediaType == MediaType.VIDEO) videoSize = Pair(videoWidth, videoHeight)
            if (curEpisode != null) {
                val pos = curEpisode!!.position
                if (pos > 0) seekTo(pos)
                if (curEpisode != null && curEpisode!!.duration <= 0) {
                    Logd(TAG, "Setting duration of media")
                    curEpisode!!.duration = (if (exoPlayer?.duration == C.TIME_UNSET) Episode.INVALID_TIME else exoPlayer!!.duration.toInt())
                }
            }
            setPlayerStatus(PlayerStatus.PREPARED, curEpisode)
            if (startWhenPrepared.get()) resume()
        }
    }

    override fun reinit() {
        Logd(TAG, "reinit() called")
        releaseWifiLockIfNecessary()
        when {
            curEpisode != null -> playMediaObject(curEpisode!!, isStreaming, startWhenPrepared.get(), prepareImmediately = false, true)
            else -> Logd(TAG, "Call to reinit: media and mediaPlayer were null, ignored")
        }
    }

    override fun seekTo(t: Int) {
        var t = t
        if (t < 0) t = 0
        Logd(TAG, "seekTo() called $t")

        if (t >= getDuration()) {
            Logd(TAG, "Seek reached end of file, skipping to next episode")
            exoPlayer?.seekTo(t.toLong())   // can set curMedia to null
            if (curEpisode != null) EventFlow.postEvent(FlowEvent.PlaybackPositionEvent(curEpisode, t, curEpisode!!.duration))
            audioSeekCompleteListener?.run()
            endPlayback(true, wasSkipped = true, true, toStoppedState = true)
            t = getPosition()
//            return
        }

        when (status) {
            PlayerStatus.PLAYING, PlayerStatus.PAUSED, PlayerStatus.PREPARED -> {
                Logd(TAG, "seekTo t: $t")
                if (seekLatch != null && seekLatch!!.count > 0) {
                    try { seekLatch!!.await(3, TimeUnit.SECONDS) } catch (e: InterruptedException) { Log.e(TAG, Log.getStackTraceString(e)) }
                }
                seekLatch = CountDownLatch(1)
                statusBeforeSeeking = status
                setPlayerStatus(PlayerStatus.SEEKING, curEpisode, t)
                exoPlayer?.seekTo(t.toLong())
                if (curEpisode != null) EventFlow.postEvent(FlowEvent.PlaybackPositionEvent(curEpisode, t, curEpisode!!.duration))
                audioSeekCompleteListener?.run()
                if (statusBeforeSeeking == PlayerStatus.PREPARED) curEpisode?.setPosition(t)
                try { seekLatch!!.await(3, TimeUnit.SECONDS) } catch (e: InterruptedException) { Log.e(TAG, Log.getStackTraceString(e)) }
            }
            PlayerStatus.INITIALIZED -> {
                curEpisode?.setPosition(t)
                startWhenPrepared.set(false)
                prepare()
            }
            else -> {}
        }
    }

    override fun getDuration(): Int {
        return curEpisode?.duration ?: Episode.INVALID_TIME
    }

    override fun getPosition(): Int {
        var retVal = Episode.INVALID_TIME
        if (exoPlayer != null && status.isAtLeast(PlayerStatus.PREPARED)) retVal = exoPlayer!!.currentPosition.toInt()
        if (retVal <= 0 && curEpisode != null) retVal = curEpisode!!.position
        return retVal
    }

    override fun setPlaybackParams(speed: Float, skipSilence: Boolean) {
        EventFlow.postEvent(FlowEvent.SpeedChangedEvent(speed))
        Logd(TAG, "setPlaybackParams speed=$speed pitch=${playbackParameters.pitch} skipSilence=$skipSilence")
        playbackParameters = PlaybackParameters(speed, playbackParameters.pitch)
        exoPlayer!!.skipSilenceEnabled = skipSilence
        exoPlayer!!.playbackParameters = playbackParameters
    }

    override fun getPlaybackSpeed(): Float {
        var retVal = 1f
        if (status == PlayerStatus.PLAYING || status == PlayerStatus.PAUSED || status == PlayerStatus.INITIALIZED || status == PlayerStatus.PREPARED)
            retVal = playbackParameters.speed
        return retVal
    }

    override fun setVolume(volumeLeft: Float, volumeRight: Float) {
        var volumeLeft = volumeLeft
        var volumeRight = volumeRight
        Logd(TAG, "setVolume: $volumeLeft $volumeRight")
        val playable = curEpisode
        if (playable != null) {
            var adaptionFactor = 1f
            if (playable.volumeAdaptionSetting != VolumeAdaptionSetting.OFF) adaptionFactor = playable.volumeAdaptionSetting.adaptionFactor
            else {
                val feed = playable.feed
                if (feed != null) {
                    val volumeAdaptionSetting = feed.volumeAdaptionSetting
                    adaptionFactor = volumeAdaptionSetting.adaptionFactor
                }
            }
            if (adaptionFactor != 1f) {
                volumeLeft *= adaptionFactor
                volumeRight *= adaptionFactor
            }
        }
        Logd(TAG, "setVolume 1: $volumeLeft $volumeRight")
        if (volumeLeft > 1) {
            exoPlayer?.volume = 1f
            loudnessEnhancer?.setEnabled(true)
            loudnessEnhancer?.setTargetGain((1000 * (volumeLeft - 1)).toInt())
        } else {
            exoPlayer?.volume = volumeLeft
            loudnessEnhancer?.setEnabled(false)
        }
        Logd(TAG, "Media player volume was set to $volumeLeft $volumeRight")
    }

    override fun shutdown() {
        Logd(TAG, "shutdown() called")
        try {
            audioCompletionListener = Runnable {}
            audioSeekCompleteListener = Runnable {}
            bufferingUpdateListener = Consumer { }
            audioErrorListener = Consumer {}

//            TODO: should use: exoPlayer!!.playWhenReady ?
            if (exoPlayer?.isPlaying == true) exoPlayer?.stop()
        } catch (e: Exception) { e.printStackTrace() }
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
     * @return Width and height as a Pair or null if the video size could not be determined. The method might still
     * return an invalid non-null value if the getVideoWidth() and getVideoHeight() methods of the media player return
     * invalid values.
     */
    override fun getVideoSize(): Pair<Int, Int>? {
        if (status != PlayerStatus.ERROR && mediaType == MediaType.VIDEO) videoSize = Pair(videoWidth, videoHeight)
        return videoSize
    }

    override fun getAudioTracks(): List<String> {
        val trackNames: MutableList<String> = ArrayList()
        val trackNameProvider: TrackNameProvider = DefaultTrackNameProvider(context.resources)
        for (format in formats) trackNames.add(trackNameProvider.getTrackName(format))
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
        Logd(TAG, "createMediaPlayer()")
        release()
        if (curEpisode == null) {
            status = PlayerStatus.STOPPED
            return
        }
        val i = curEpisode?.feed?.audioType?: C.AUDIO_CONTENT_TYPE_SPEECH
        val a = exoPlayer!!.audioAttributes
        val b = AudioAttributes.Builder()
        b.setContentType(i)
        b.setFlags(a.flags)
        b.setUsage(a.usage)
        exoPlayer?.setAudioAttributes(b.build(), true)

        if (curEpisode != null) {
            audioCompletionListener = Runnable {
                Logd(TAG, "audioCompletionListener called")
                endPlayback(hasEnded = true, wasSkipped = false, shouldContinue = true, toStoppedState = true)
            }
            audioSeekCompleteListener = Runnable {
                Logd(TAG, "genericSeekCompleteListener $status ${exoPlayer?.isPlaying} $statusBeforeSeeking")
                seekLatch?.countDown()
                if ((status == PlayerStatus.PLAYING || exoPlayer?.isPlaying != true) && curEpisode != null) callback.onPlaybackStart(curEpisode!!, getPosition())
                if (status == PlayerStatus.SEEKING && statusBeforeSeeking != null) setPlayerStatus(statusBeforeSeeking!!, curEpisode, getPosition())
            }
            bufferingUpdateListener = Consumer { percent: Int ->
                when (percent) {
                    BUFFERING_STARTED -> EventFlow.postEvent(FlowEvent.BufferUpdateEvent.started())
                    BUFFERING_ENDED -> EventFlow.postEvent(FlowEvent.BufferUpdateEvent.ended())
                    else -> EventFlow.postEvent(FlowEvent.BufferUpdateEvent.progressUpdate(0.01f * percent))
                }
            }
            audioErrorListener = Consumer { message: String ->
                Log.e(TAG, "PlayerErrorEvent: $message")
                EventFlow.postEvent(FlowEvent.PlayerErrorEvent(message))
            }
        }
    }

    override fun endPlayback(hasEnded: Boolean, wasSkipped: Boolean, shouldContinue: Boolean, toStoppedState: Boolean) {
        releaseWifiLockIfNecessary()
        if (curEpisode == null) return

        val isPlaying = status == PlayerStatus.PLAYING
        // we're relying on the position stored in the EpisodeMedia object for post-playback processing
        val position = getPosition()
        if (position >= 0) curEpisode?.setPosition(position)
        Logd(TAG, "endPlayback hasEnded=$hasEnded wasSkipped=$wasSkipped shouldContinue=$shouldContinue toStoppedState=$toStoppedState")
//            showStackTrace()

        val currentMedia = curEpisode
        var nextMedia: Episode? = null
        if (shouldContinue) {
            // Load next episode if previous episode was in the queue and if there is an episode in the queue left.
            // Start playback immediately if continuous playback is enabled
            nextMedia = callback.getNextInQueue(currentMedia)
            if (nextMedia != null) {
                Logd(TAG, "has nextMedia. call callback.onPlaybackEnded false")
                if (wasSkipped) setPlayerStatus(PlayerStatus.INDETERMINATE, null)
                callback.onPlaybackEnded(nextMedia.getMediaType(), false)
                // setting media to null signals to playMediaObject that we're taking care of post-playback processing
                curEpisode = null
                playMediaObject(nextMedia, !nextMedia.localFileAvailable(), isPlaying, isPlaying)
            }
        }
        when {
            shouldContinue || toStoppedState -> {
                if (nextMedia == null) {
                    Logd(TAG, "nextMedia is null. call callback.onPlaybackEnded true")
                    callback.onPlaybackEnded(null, true)
                    curEpisode = null
                    exoPlayer?.stop()
                    releaseWifiLockIfNecessary()
                    if (status == PlayerStatus.INDETERMINATE) setPlayerStatus(PlayerStatus.STOPPED, null)
                    else Logd(TAG, "Ignored call to stop: Current player state is: $status")
                }
                val hasNext = nextMedia != null
                if (currentMedia != null) callback.onPostPlayback(currentMedia, hasEnded, wasSkipped, hasNext)
//                curMedia = nextMedia
            }
            isPlaying -> callback.onPlaybackPause(currentMedia, currentMedia!!.position)
        }
    }

    override fun shouldLockWifi(): Boolean = isStreaming

    companion object {
        private val TAG: String = LocalMediaPlayer::class.simpleName ?: "Anonymous"

        const val BUFFERING_STARTED: Int = -1
        const val BUFFERING_ENDED: Int = -2

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
            loadControl.setBackBuffer(rewindSecs * 1000 + 500, true)
            trackSelector = DefaultTrackSelector(context)
            val audioOffloadPreferences = AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED) // Add additional options as needed
                .setIsGaplessSupportRequired(true)
                .setIsSpeedChangeSupportRequired(true)
                .build()
            Logd(TAG, "createStaticPlayer creating exoPlayer_")

            val defaultRenderersFactory = DefaultRenderersFactory(context)
//            defaultRenderersFactory.setMediaCodecSelector { mimeType: String?, requiresSecureDecoder: Boolean, requiresTunnelingDecoder: Boolean ->
//                val decoderInfos: List<MediaCodecInfo> = MediaCodecUtil.getDecoderInfos(mimeType!!, requiresSecureDecoder, requiresTunnelingDecoder)
//                val result: MutableList<MediaCodecInfo> = ArrayList()
//                for (decoderInfo in decoderInfos) {
//                    Logd(TAG, "decoderInfo.name: ${decoderInfo.name}")
//                    if (decoderInfo.name == "c2.android.mp3.decoder") {
//                        continue
//                    }
//                    result.add(decoderInfo)
//                }
//                result
//            }
            exoPlayer = ExoPlayer.Builder(context, defaultRenderersFactory)
                .setTrackSelector(trackSelector!!)
                .setSeekBackIncrementMs(rewindSecs * 1000L)
                .setSeekForwardIncrementMs(fastForwardSecs * 1000L)
                .setLoadControl(loadControl.build())
                .build()

            exoPlayer?.setSeekParameters(SeekParameters.EXACT)
            exoPlayer!!.trackSelectionParameters = exoPlayer!!.trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(audioOffloadPreferences)
                .build()

//            if (BuildConfig.DEBUG) exoPlayer!!.addAnalyticsListener(EventLogger())

            if (exoplayerListener != null) {
                exoPlayer?.removeListener(exoplayerListener!!)
                exoPlayer?.addListener(exoplayerListener!!)
            }
            initLoudnessEnhancer(exoPlayer!!.audioSessionId)
        }

        private fun initLoudnessEnhancer(audioStreamId: Int) {
            runOnIOScope {
                val newEnhancer = LoudnessEnhancer(audioStreamId)
                val oldEnhancer = loudnessEnhancer
                if (oldEnhancer != null) {
                    newEnhancer.setEnabled(oldEnhancer.enabled)
                    if (oldEnhancer.enabled) newEnhancer.setTargetGain(oldEnhancer.targetGain.toInt())
                    oldEnhancer.release()
                }
                loudnessEnhancer = newEnhancer
            }
        }

        fun cleanup() {
            if (exoplayerListener != null) exoPlayer?.removeListener(exoplayerListener!!)
            exoplayerListener = null
            audioSeekCompleteListener = null
            audioCompletionListener = null
            audioErrorListener = null
            bufferingUpdateListener = null
            loudnessEnhancer = null
            httpDataSourceFactory = null
        }
    }
}
