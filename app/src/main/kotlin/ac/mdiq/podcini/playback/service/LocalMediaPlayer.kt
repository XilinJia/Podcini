package ac.mdiq.podcini.playback.service

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.HttpCredentialEncoder
import ac.mdiq.podcini.net.download.service.PodciniHttpClient
import ac.mdiq.podcini.net.utils.NetworkUtils.wasDownloadBlocked
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.MediaPlayerCallback
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.storage.utils.MediaType
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.config.ClientConfig
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import ac.mdiq.podcini.util.event.FlowEvent.PlayEvent.Action
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
import androidx.media3.datasource.DefaultDataSource
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
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.ui.DefaultTrackNameProvider
import androidx.media3.ui.TrackNameProvider
import kotlinx.coroutines.*
import java.io.IOException
import java.lang.Runnable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile

/**
 * Manages the MediaPlayer object of the PlaybackService.
 */
@UnstableApi
class LocalMediaPlayer(context: Context, callback: MediaPlayerCallback) : MediaPlayerBase(context, callback) {

    @Volatile
    private var statusBeforeSeeking: PlayerStatus? = null

    @Volatile
    private var videoSize: Pair<Int, Int>? = null
    private var isShutDown = false
    private var seekLatch: CountDownLatch? = null

    private val bufferUpdateInterval = 5000L
    private var mediaSource: MediaSource? = null
    private var mediaItem: MediaItem? = null
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
        get() {
            return exoPlayer?.videoFormat?.width ?: 0
        }

    private val videoHeight: Int
        get() {
            return exoPlayer?.videoFormat?.height ?: 0
        }

    init {
        if (httpDataSourceFactory == null) {
            runOnIOScope {
                httpDataSourceFactory = OkHttpDataSource.Factory(PodciniHttpClient.getHttpClient() as okhttp3.Call.Factory)
                    .setUserAgent(ClientConfig.USER_AGENT)
            }
        }
        if (exoPlayer == null) {
            setupPlayerListener()
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

    @Throws(IllegalStateException::class)
    private fun prepareWR() {
        Logd(TAG, "prepareWR() called")
        if (mediaSource == null && mediaItem == null) return
        if (mediaSource != null) exoPlayer?.setMediaSource(mediaSource!!, false)
        else exoPlayer?.setMediaItem(mediaItem!!)
        exoPlayer?.prepare()
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

    private fun setAudioStreamType(i: Int) {
        val a = exoPlayer!!.audioAttributes
        val b = AudioAttributes.Builder()
        b.setContentType(i)
        b.setFlags(a.flags)
        b.setUsage(a.usage)
        exoPlayer?.setAudioAttributes(b.build(), true)
    }

    private fun buildMetadata(p: Playable): MediaMetadata {
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
    private fun setDataSource(metadata: MediaMetadata, mediaUrl: String, user: String?, password: String?) {
        Logd(TAG, "setDataSource: $mediaUrl")

        mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(mediaUrl))
            .setMediaMetadata(metadata).build()
        mediaSource = null

        if (!user.isNullOrEmpty() && !password.isNullOrEmpty()) {
            if (httpDataSourceFactory == null)
                httpDataSourceFactory = OkHttpDataSource.Factory(PodciniHttpClient.getHttpClient() as okhttp3.Call.Factory)
                    .setUserAgent(ClientConfig.USER_AGENT)

            val requestProperties = HashMap<String, String>()
            requestProperties["Authorization"] = HttpCredentialEncoder.encode(user, password, "ISO-8859-1")
            httpDataSourceFactory!!.setDefaultRequestProperties(requestProperties)

            val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(context, httpDataSourceFactory!!)
            val extractorsFactory = DefaultExtractorsFactory()
            extractorsFactory.setConstantBitrateSeekingEnabled(true)
            extractorsFactory.setMp3ExtractorFlags(Mp3Extractor.FLAG_DISABLE_ID3_METADATA)
            val f = ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)

            mediaSource = f.createMediaSource(mediaItem!!)
        }
    }

    /**
     * Starts or prepares playback of the specified Playable object. If another Playable object is already being played, the currently playing
     * episode will be stopped and replaced with the new Playable object. If the Playable object is already being played, the method will
     * not do anything.
     * Whether playback starts immediately depends on the given parameters. See below for more details.
     * States:
     * During execution of the method, the object will be in the INITIALIZING state. The end state depends on the given parameters.
     * If 'prepareImmediately' is set to true, the method will go into PREPARING state and after that into PREPARED state. If
     * 'startWhenPrepared' is set to true, the method will additionally go into PLAYING state.
     * If an unexpected error occurs while loading the Playable's metadata or while setting the MediaPlayers data source, the object
     * will enter the ERROR state.
     * This method is executed on an internal executor service.
     * @param playable           The Playable object that is supposed to be played. This parameter must not be null.
     * @param stream             The type of playback. If false, the Playable object MUST provide access to a locally available file via
     * getLocalMediaUrl. If true, the Playable object MUST provide access to a resource that can be streamed by
     * the Android MediaPlayer via getStreamUrl.
     * @param startWhenPrepared  Sets the 'startWhenPrepared' flag. This flag determines whether playback will start immediately after the
     * episode has been prepared for playback. Setting this flag to true does NOT mean that the episode will be prepared
     * for playback immediately (see 'prepareImmediately' parameter for more details)
     * @param prepareImmediately Set to true if the method should also prepare the episode for playback.
     */
    override fun playMediaObject(playable: Playable, stream: Boolean, startWhenPrepared: Boolean, prepareImmediately: Boolean, forceReset: Boolean) {
       Logd(TAG, "playMediaObject status=$status stream=$stream startWhenPrepared=$startWhenPrepared prepareImmediately=$prepareImmediately forceReset=$forceReset ${playable.getEpisodeTitle()} ")
        if (curMedia != null) {
            if (!forceReset && curMedia!!.getIdentifier() == prevMedia?.getIdentifier() && status == PlayerStatus.PLAYING) {
                Logd(TAG, "Method call to playMediaObject was ignored: media file already playing.")
                return
            }
            Logd(TAG, "playMediaObject starts new media playable:${playable.getIdentifier()} curMedia:${curMedia!!.getIdentifier()} prevMedia:${prevMedia?.getIdentifier()}")
            // set temporarily to pause in order to update list with current position
            if (status == PlayerStatus.PLAYING) {
                val pos = curMedia?.getPosition() ?: -1
                seekTo(pos)
                callback.onPlaybackPause(curMedia, pos)
            }
            // stop playback of this episode
            if (status == PlayerStatus.PAUSED || status == PlayerStatus.PLAYING || status == PlayerStatus.PREPARED) exoPlayer?.stop()
            if (prevMedia != null && curMedia!!.getIdentifier() != prevMedia?.getIdentifier())
                callback.onPostPlayback(prevMedia, ended = false, skipped = false, true)
            prevMedia = curMedia
            setPlayerStatus(PlayerStatus.INDETERMINATE, null)
        }

        Logd(TAG, "playMediaObject preparing for playable:${playable.getIdentifier()} ${playable.getEpisodeTitle()}")
        curMedia = playable
        this.isStreaming = stream
        mediaType = curMedia!!.getMediaType()
        videoSize = null
        createMediaPlayer()
        this.startWhenPrepared.set(startWhenPrepared)
        setPlayerStatus(PlayerStatus.INITIALIZING, curMedia)
        val metadata = buildMetadata(curMedia!!)
        try {
            callback.ensureMediaInfoLoaded(curMedia!!)
            callback.onMediaChanged(false)
            setPlaybackParams(getCurrentPlaybackSpeed(curMedia), UserPreferences.isSkipSilence)
            when {
                stream -> {
                    val streamurl = curMedia!!.getStreamUrl()
                    if (streamurl != null) {
                        val media = curMedia
                        if (media is EpisodeMedia) {
                            val preferences = media.episode?.feed?.preferences
                            setDataSource(metadata, streamurl, preferences?.username, preferences?.password)
                        } else setDataSource(metadata, streamurl, null, null)
                    }
                }
                else -> {
                    val localMediaurl = curMedia!!.getLocalMediaUrl()
//                    File(localMediaurl).canRead() time consuming, leave it to MediaItem to handle
//                    if (!localMediaurl.isNullOrEmpty() && File(localMediaurl).canRead()) setDataSource(metadata, localMediaurl, null, null)
                    if (!localMediaurl.isNullOrEmpty()) setDataSource(metadata, localMediaurl, null, null)
                    else throw IOException("Unable to read local file $localMediaurl")
                }
            }
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
        }
    }

    override fun resume() {
        if (status == PlayerStatus.PAUSED || status == PlayerStatus.PREPARED) {
            Logd(TAG, "Resuming/Starting playback")
            acquireWifiLockIfNecessary()
            setPlaybackParams(getCurrentPlaybackSpeed(curMedia), UserPreferences.isSkipSilence)
            setVolume(1.0f, 1.0f)

            if (curMedia != null && status == PlayerStatus.PREPARED && curMedia!!.getPosition() > 0) {
                val newPosition = calculatePositionWithRewind(curMedia!!.getPosition(), curMedia!!.getLastPlayedTime())
                seekTo(newPosition)
            }
            if (exoPlayer?.playbackState == STATE_IDLE || exoPlayer?.playbackState == STATE_ENDED ) prepareWR()
            exoPlayer?.play()
            // Can't set params when paused - so always set it on start in case they changed
            exoPlayer?.playbackParameters = playbackParameters
            setPlayerStatus(PlayerStatus.PLAYING, curMedia)
            if (curEpisode != null) EventFlow.postEvent(FlowEvent.PlayEvent(curEpisode!!))
        } else Logd(TAG, "Call to resume() was ignored because current state of PSMP object is $status")
    }

    override fun pause(abandonFocus: Boolean, reinit: Boolean) {
        releaseWifiLockIfNecessary()
        if (status == PlayerStatus.PLAYING) {
            Logd(TAG, "Pausing playback $abandonFocus $reinit")
            exoPlayer?.pause()
            setPlayerStatus(PlayerStatus.PAUSED, curMedia, getPosition())
            if (curEpisode != null) EventFlow.postEvent(FlowEvent.PlayEvent(curEpisode!!, Action.END))
            if (isStreaming && reinit) reinit()
        } else Logd(TAG, "Ignoring call to pause: Player is in $status state")
    }

    override fun prepare() {
        if (status == PlayerStatus.INITIALIZED) {
            Logd(TAG, "Preparing media player")
            setPlayerStatus(PlayerStatus.PREPARING, curMedia)
            prepareWR()
//            onPrepared(startWhenPrepared.get())
            if (mediaType == MediaType.VIDEO) videoSize = Pair(videoWidth, videoHeight)
            if (curMedia != null) {
                val pos = curMedia!!.getPosition()
                if (pos > 0) seekTo(pos)
                if (curMedia != null && curMedia!!.getDuration() <= 0) {
                    Logd(TAG, "Setting duration of media")
                    curMedia!!.setDuration(if (exoPlayer?.duration == C.TIME_UNSET) Playable.INVALID_TIME else exoPlayer!!.duration.toInt())
                }
            }
            setPlayerStatus(PlayerStatus.PREPARED, curMedia)
            if (startWhenPrepared.get()) resume()
        }
    }

    override fun reinit() {
        Logd(TAG, "reinit() called")
        releaseWifiLockIfNecessary()
        when {
            curMedia != null -> playMediaObject(curMedia!!, isStreaming, startWhenPrepared.get(), prepareImmediately = false, true)
            else -> {
                Logd(TAG, "Call to reinit: media and mediaPlayer were null, ignored")
            }
        }
    }

    override fun seekTo(t: Int) {
        var t = t
        if (t < 0) t = 0
        Logd(TAG, "seekTo() called $t")

        if (t >= getDuration()) {
            Logd(TAG, "Seek reached end of file, skipping to next episode")
            exoPlayer?.seekTo(t.toLong())   // can set curMedia to null
            if (curMedia != null) EventFlow.postEvent(FlowEvent.PlaybackPositionEvent(curMedia, t, curMedia!!.getDuration()))
            audioSeekCompleteListener?.run()
            endPlayback(true, wasSkipped = true, true, toStoppedState = true)
            t = getPosition()
//            return
        }

        when (status) {
            PlayerStatus.PLAYING, PlayerStatus.PAUSED, PlayerStatus.PREPARED -> {
                Logd(TAG, "seekTo() called $t")
                if (seekLatch != null && seekLatch!!.count > 0) {
                    try {
                        seekLatch!!.await(3, TimeUnit.SECONDS)
                    } catch (e: InterruptedException) {
                        Log.e(TAG, Log.getStackTraceString(e))
                    }
                }
                seekLatch = CountDownLatch(1)
                statusBeforeSeeking = status
                setPlayerStatus(PlayerStatus.SEEKING, curMedia, t)
                exoPlayer?.seekTo(t.toLong())
                if (curMedia != null) EventFlow.postEvent(FlowEvent.PlaybackPositionEvent(curMedia, t, curMedia!!.getDuration()))
                audioSeekCompleteListener?.run()
                if (statusBeforeSeeking == PlayerStatus.PREPARED) curMedia?.setPosition(t)
                try {
                    seekLatch!!.await(3, TimeUnit.SECONDS)
                } catch (e: InterruptedException) {
                    Log.e(TAG, Log.getStackTraceString(e))
                }
            }
            PlayerStatus.INITIALIZED -> {
                curMedia?.setPosition(t)
                startWhenPrepared.set(false)
                prepare()
            }
            else -> {}
        }
    }

    override fun getDuration(): Int {
        return curMedia?.getDuration() ?: Playable.INVALID_TIME
    }

    override fun getPosition(): Int {
        var retVal = Playable.INVALID_TIME
        if (status.isAtLeast(PlayerStatus.PREPARED)) retVal = exoPlayer!!.currentPosition.toInt()
        if (retVal <= 0 && curMedia != null) retVal = curMedia!!.getPosition()
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
        val playable = curMedia
        if (playable is EpisodeMedia && playable.episode?.feed?.preferences != null) {
            val preferences = playable.episode!!.feed!!.preferences!!
            val volumeAdaptionSetting = preferences.volumeAdaptionSetting
            if (volumeAdaptionSetting != null) {
                val adaptionFactor = volumeAdaptionSetting.adaptionFactor
                volumeLeft *= adaptionFactor
                volumeRight *= adaptionFactor
            }
        }
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
            clearMediaPlayerListeners()
//            TODO: should use: exoPlayer!!.playWhenReady ?
            if (exoPlayer?.isPlaying == true) exoPlayer?.stop()
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
        Logd(TAG, "createMediaPlayer()")
        release()
        if (curMedia == null) {
            status = PlayerStatus.STOPPED
            return
        }
        setAudioStreamType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
        setMediaPlayerListeners()
    }

    override fun endPlayback(hasEnded: Boolean, wasSkipped: Boolean, shouldContinue: Boolean, toStoppedState: Boolean) {
        releaseWifiLockIfNecessary()

        val isPlaying = status == PlayerStatus.PLAYING
        // we're relying on the position stored in the Playable object for post-playback processing
        val position = getPosition()
        if (position >= 0) curMedia?.setPosition(position)
        Logd(TAG, "endPlayback hasEnded=$hasEnded wasSkipped=$wasSkipped shouldContinue=$shouldContinue toStoppedState=$toStoppedState")

        val currentMedia = curMedia
        var nextMedia: Playable? = null
        if (shouldContinue) {
            // Load next episode if previous episode was in the queue and if there is an episode in the queue left.
            // Start playback immediately if continuous playback is enabled
            nextMedia = callback.getNextInQueue(currentMedia)
            if (nextMedia != null) {
                Logd(TAG, "has nextMedia. call callback.onPlaybackEnded false")
                if (wasSkipped) setPlayerStatus(PlayerStatus.INDETERMINATE, null)
                callback.onPlaybackEnded(nextMedia.getMediaType(), false)
                // setting media to null signals to playMediaObject that we're taking care of post-playback processing
                curMedia = null
                playMediaObject(nextMedia, !nextMedia.localFileAvailable(), isPlaying, isPlaying)
            }
        }
        when {
            shouldContinue || toStoppedState -> {
                if (nextMedia == null) {
                    Logd(TAG, "nextMedia is null. call callback.onPlaybackEnded true")
                    callback.onPlaybackEnded(null, true)
                    curMedia = null
                    exoPlayer?.stop()
                    releaseWifiLockIfNecessary()
                    if (status == PlayerStatus.INDETERMINATE) setPlayerStatus(PlayerStatus.STOPPED, null)
                    else Logd(TAG, "Ignored call to stop: Current player state is: $status")
                }
                val hasNext = nextMedia != null
                callback.onPostPlayback(currentMedia, hasEnded, wasSkipped, hasNext)
            }
            isPlaying -> callback.onPlaybackPause(currentMedia, currentMedia!!.getPosition())
        }
    }

    override fun shouldLockWifi(): Boolean {
        return isStreaming
    }

    private fun setMediaPlayerListeners() {
        if (curMedia == null) return

        audioCompletionListener = Runnable {
            Logd(TAG, "audioCompletionListener called")
            endPlayback(hasEnded = true, wasSkipped = false, shouldContinue = true, toStoppedState = true)
        }
        audioSeekCompleteListener = Runnable { this.genericSeekCompleteListener() }
        bufferingUpdateListener = Consumer { percent: Int ->
            when (percent) {
                BUFFERING_STARTED -> EventFlow.postEvent(FlowEvent.BufferUpdateEvent.started())
                BUFFERING_ENDED -> EventFlow.postEvent(FlowEvent.BufferUpdateEvent.ended())
                else -> EventFlow.postEvent(FlowEvent.BufferUpdateEvent.progressUpdate(0.01f * percent))
            }
        }
        audioErrorListener = Consumer { message: String ->
            Log.e(TAG, "PlayerErrorEvent: $message")
            EventFlow.postStickyEvent(FlowEvent.PlayerErrorEvent(message))
        }
    }

    private fun clearMediaPlayerListeners() {
        audioCompletionListener = Runnable {}
        audioSeekCompleteListener = Runnable {}
        bufferingUpdateListener = Consumer { }
        audioErrorListener = Consumer {}
    }

    private fun genericSeekCompleteListener() {
        Logd(TAG, "genericSeekCompleteListener $status ${exoPlayer?.isPlaying} $statusBeforeSeeking")
        seekLatch?.countDown()

        if ((status == PlayerStatus.PLAYING || exoPlayer?.isPlaying != true) && curMedia != null) callback.onPlaybackStart(curMedia!!, getPosition())
        if (status == PlayerStatus.SEEKING && statusBeforeSeeking != null) setPlayerStatus(statusBeforeSeeking!!, curMedia, getPosition())
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
                        audioCompletionListener?.run()
                    }
                    STATE_BUFFERING -> bufferingUpdateListener?.accept(BUFFERING_STARTED)
                    else -> bufferingUpdateListener?.accept(BUFFERING_ENDED)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val stat = if (isPlaying) PlayerStatus.PLAYING else PlayerStatus.PAUSED
                setPlayerStatus(stat, curMedia)
                Logd(TAG, "onIsPlayingChanged $isPlaying")
            }

            override fun onPlayerError(error: PlaybackException) {
                Logd(TAG, "onPlayerError ${error.message}")
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
    }

    companion object {
        private val TAG: String = LocalMediaPlayer::class.simpleName ?: "Anonymous"

        const val BUFFERING_STARTED: Int = -1
        const val BUFFERING_ENDED: Int = -2

        private var httpDataSourceFactory:  OkHttpDataSource.Factory? = null

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
                .setLoadControl(loadControl.build())
                .build()

            exoPlayer?.setSeekParameters(SeekParameters.EXACT)
            exoPlayer!!.trackSelectionParameters = exoPlayer!!.trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(audioOffloadPreferences)
                .build()

            if (BuildConfig.DEBUG) exoPlayer!!.addAnalyticsListener(EventLogger())

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
            httpDataSourceFactory = null
        }
    }
}
