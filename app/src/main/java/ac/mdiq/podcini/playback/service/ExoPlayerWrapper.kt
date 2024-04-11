package ac.mdiq.podcini.playback.service

import ac.mdiq.podcini.R
import ac.mdiq.podcini.util.config.ClientConfig
import ac.mdiq.podcini.net.download.service.HttpCredentialEncoder
import ac.mdiq.podcini.net.download.service.PodciniHttpClient
import ac.mdiq.podcini.util.NetworkUtils.wasDownloadBlocked
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.preferences.UserPreferences
import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.util.Log
import android.view.SurfaceHolder
import androidx.core.util.Consumer
import androidx.media3.common.*
import androidx.media3.common.Player.DiscontinuityReason
import androidx.media3.common.Player.PositionInfo
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSourceFactory
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.*
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
import java.util.concurrent.TimeUnit


@UnstableApi
class ExoPlayerWrapper internal constructor(private val context: Context) {
    //    TODO: need to experiment this, 5 seconds for now
    private val bufferUpdateInterval = 5L

    private val bufferingUpdateDisposable: Disposable
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var trackSelector: DefaultTrackSelector

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var mediaSource: MediaSource? = null
    private var audioSeekCompleteListener: Runnable? = null
    private var audioCompletionListener: Runnable? = null
    private var audioErrorListener: Consumer<String>? = null
    private var bufferingUpdateListener: Consumer<Int>? = null

    private var playbackParameters: PlaybackParameters

    init {
        createPlayer()
        playbackParameters = exoPlayer.playbackParameters
        bufferingUpdateDisposable = Observable.interval(bufferUpdateInterval, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                bufferingUpdateListener?.accept(exoPlayer.bufferedPercentage)
            }
    }

    private fun createPlayer() {
        val loadControl = DefaultLoadControl.Builder()
        loadControl.setBufferDurationsMs(30000, 120000,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
        loadControl.setBackBuffer(UserPreferences.rewindSecs * 1000 + 500, true)
        trackSelector = DefaultTrackSelector(context)
        val audioOffloadPreferences = AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED) // Add additional options as needed
            .setIsGaplessSupportRequired(true)
            .setIsSpeedChangeSupportRequired(true)
            .build()
        exoPlayer = ExoPlayer.Builder(context, DefaultRenderersFactory(context))
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl.build())
            .build()
        exoPlayer.setSeekParameters(SeekParameters.EXACT)
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(audioOffloadPreferences)
            .build()
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: @Player.State Int) {
                when {
                    audioCompletionListener != null && playbackState == Player.STATE_ENDED -> {
                        audioCompletionListener?.run()
                    }
                    playbackState == Player.STATE_BUFFERING -> {
                        bufferingUpdateListener?.accept(BUFFERING_STARTED)
                    }
                    else -> {
                        bufferingUpdateListener?.accept(BUFFERING_ENDED)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (wasDownloadBlocked(error)) {
                    audioErrorListener?.accept(context.getString(R.string.download_error_blocked))
                } else {
                    var cause = error.cause
                    if (cause is HttpDataSourceException) {
                        if (cause.cause != null) {
                            cause = cause.cause
                        }
                    }
                    if (cause != null && "Source error" == cause.message) {
                        cause = cause.cause
                    }
                    audioErrorListener?.accept(if (cause != null) cause.message else error.message)
                }
            }

            override fun onPositionDiscontinuity(oldPosition: PositionInfo,
                                                 newPosition: PositionInfo,
                                                 reason: @DiscontinuityReason Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    audioSeekCompleteListener?.run()
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                initLoudnessEnhancer(audioSessionId)
            }
        })

        initLoudnessEnhancer(exoPlayer.audioSessionId)
    }

    val currentPosition: Int
        get() = exoPlayer.currentPosition.toInt()

    val currentSpeedMultiplier: Float
        get() = playbackParameters.speed

    val duration: Int
        get() {
            if (exoPlayer.duration == C.TIME_UNSET) {
                return Playable.INVALID_TIME
            }
            return exoPlayer.duration.toInt()
        }

    val isPlaying: Boolean
        get() = exoPlayer.playWhenReady

    fun pause() {
        exoPlayer.pause()
    }

    @Throws(IllegalStateException::class)
    fun prepare() {
        if (mediaSource == null) return
        exoPlayer.setMediaSource(mediaSource!!, false)
        exoPlayer.prepare()
    }

    fun release() {
        bufferingUpdateDisposable.dispose()
        exoPlayer.release()

        audioSeekCompleteListener = null
        audioCompletionListener = null
        audioErrorListener = null
        bufferingUpdateListener = null
    }

    fun reset() {
        exoPlayer.release()
        createPlayer()
    }

    @Throws(IllegalStateException::class)
    fun seekTo(i: Int) {
        exoPlayer.seekTo(i.toLong())
        audioSeekCompleteListener?.run()
    }

    fun setAudioStreamType(i: Int) {
        val a = exoPlayer.audioAttributes
        val b = AudioAttributes.Builder()
        b.setContentType(i)
        b.setFlags(a.flags)
        b.setUsage(a.usage)
        exoPlayer.setAudioAttributes(b.build(), false)
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun setDataSource(s: String, user: String?, password: String?) {
        Log.d(TAG, "setDataSource: $s")

        //        Call.Factory callFactory = PodciniHttpClient.getHttpClient(); // Assuming it returns OkHttpClient
//        OkHttpDataSource.Factory httpDataSourceFactory = new OkHttpDataSource.Factory(callFactory)
//                .setUserAgent(ClientConfig.USER_AGENT);
        val httpDataSourceFactory =
            OkHttpDataSource.Factory(PodciniHttpClient.getHttpClient() as okhttp3.Call.Factory)
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
        val mediaItem = MediaItem.fromUri(Uri.parse(s))
        mediaSource = f.createMediaSource(mediaItem)
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun setDataSource(s: String) {
        setDataSource(s, null, null)
    }

    fun setDisplay(sh: SurfaceHolder?) {
        exoPlayer.setVideoSurfaceHolder(sh)
    }

    fun setPlaybackParams(speed: Float, skipSilence: Boolean) {
        Log.d(TAG, "setPlaybackParams speed=$speed pitch=${playbackParameters.pitch} skipSilence=$skipSilence")
        playbackParameters = PlaybackParameters(speed, playbackParameters.pitch)
        exoPlayer.skipSilenceEnabled = skipSilence
        exoPlayer.playbackParameters = playbackParameters
    }

    fun setVolume(v: Float, v1: Float) {
        if (v > 1) {
            exoPlayer.volume = 1f
            loudnessEnhancer?.setEnabled(true)
            loudnessEnhancer?.setTargetGain((1000 * (v - 1)).toInt())
        } else {
            exoPlayer.volume = v
            loudnessEnhancer?.setEnabled(false)
        }
    }

    fun start() {
        exoPlayer.play()
        // Can't set params when paused - so always set it on start in case they changed
        exoPlayer.playbackParameters = playbackParameters
    }

    fun stop() {
        exoPlayer.stop()
    }

    val audioTracks: List<String>
        get() {
            val trackNames: MutableList<String> = ArrayList()
            val trackNameProvider: TrackNameProvider = DefaultTrackNameProvider(context.resources)
            for (format in formats) {
                trackNames.add(trackNameProvider.getTrackName(format))
            }
            return trackNames
        }

    private val formats: List<Format>
        get() {
            val formats: MutableList<Format> = arrayListOf()
            val trackInfo = trackSelector.currentMappedTrackInfo
                ?: return emptyList()
            val trackGroups = trackInfo.getTrackGroups(audioRendererIndex)
            for (i in 0 until trackGroups.length) {
                formats.add(trackGroups[i].getFormat(0))
            }
            return formats
        }

    fun setAudioTrack(track: Int) {
        val trackInfo = trackSelector.currentMappedTrackInfo ?: return
        val trackGroups = trackInfo.getTrackGroups(audioRendererIndex)
        val override = SelectionOverride(track, 0)
        val rendererIndex = audioRendererIndex
        val params = trackSelector.buildUponParameters()
            .setSelectionOverride(rendererIndex, trackGroups, override)
        trackSelector.setParameters(params)
    }

    private val audioRendererIndex: Int
        get() {
            for (i in 0 until exoPlayer.rendererCount) {
                if (exoPlayer.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
                    return i
                }
            }
            return -1
        }

    val selectedAudioTrack: Int
        get() {
            val trackSelections = exoPlayer.currentTrackSelections
            val availableFormats = formats
            for (i in 0 until trackSelections.length) {
                val track = trackSelections[i] as ExoTrackSelection? ?: continue
                if (availableFormats.contains(track.selectedFormat)) {
                    return availableFormats.indexOf(track.selectedFormat)
                }
            }
            return -1
        }

    fun setOnCompletionListener(audioCompletionListener: Runnable?) {
        this.audioCompletionListener = audioCompletionListener
    }

    fun setOnSeekCompleteListener(audioSeekCompleteListener: Runnable?) {
        this.audioSeekCompleteListener = audioSeekCompleteListener
    }

    fun setOnErrorListener(audioErrorListener: Consumer<String>?) {
        this.audioErrorListener = audioErrorListener
    }

    val videoWidth: Int
        get() {
            return exoPlayer.videoFormat?.width ?: 0
        }

    val videoHeight: Int
        get() {
            return exoPlayer.videoFormat?.height ?: 0
        }

    fun setOnBufferingUpdateListener(bufferingUpdateListener: Consumer<Int>?) {
        this.bufferingUpdateListener = bufferingUpdateListener
    }

    private fun initLoudnessEnhancer(audioStreamId: Int) {
        val newEnhancer = LoudnessEnhancer(audioStreamId)
        val oldEnhancer = this.loudnessEnhancer
        if (oldEnhancer != null) {
            newEnhancer.setEnabled(oldEnhancer.enabled)
            if (oldEnhancer.enabled) {
                newEnhancer.setTargetGain(oldEnhancer.targetGain.toInt())
            }
            oldEnhancer.release()
        }

        this.loudnessEnhancer = newEnhancer
    }

    companion object {
        const val BUFFERING_STARTED: Int = -1
        const val BUFFERING_ENDED: Int = -2
        private const val TAG = "ExoPlayerWrapper"
        const val ERROR_CODE_OFFSET: Int = 1000
    }
}
