package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.net.download.service.HttpCredentialEncoder
import ac.mdiq.podcini.net.download.service.PodciniHttpClient
import ac.mdiq.podcini.net.utils.NetworkUtils.isNetworkRestricted
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.InTheatre.curState
import ac.mdiq.podcini.preferences.UserPreferences.Prefs
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.preferences.UserPreferences.prefLowQualityMedia
import ac.mdiq.podcini.preferences.UserPreferences.setPlaybackSpeed
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.config.ClientConfig
import ac.mdiq.vista.extractor.MediaFormat
import ac.mdiq.vista.extractor.stream.AudioStream
import ac.mdiq.vista.extractor.stream.DeliveryMethod
import ac.mdiq.vista.extractor.stream.VideoStream
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.util.Log
import android.util.Pair
import android.view.SurfaceHolder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

abstract class MediaPlayerBase protected constructor(protected val context: Context, protected val callback: MediaPlayerCallback) {

    @Volatile
    private var oldStatus: PlayerStatus? = null
    internal var prevMedia: EpisodeMedia? = null

    protected var mediaSource: MediaSource? = null
    protected var mediaItem: MediaItem? = null

    internal var mediaType: MediaType = MediaType.UNKNOWN
    internal val startWhenPrepared = AtomicBoolean(false)

    var isStreaming = false

    /**
     * A wifi-lock that is acquired if the media file is being streamed.
     */
    private var wifiLock: WifiLock? = null

    /**
     * Returns a PSMInfo object that contains information about the current state of the PSMP object.
     * @return The PSMPInfo object.
     */
    @get:Synchronized
    val playerInfo: MediaPlayerInfo
        get() = MediaPlayerInfo(oldStatus, status, curMedia)

    val isAudioChannelInUse: Boolean
        get() {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            return (audioManager.mode != AudioManager.MODE_NORMAL || audioManager.isMusicActive)
        }
    
    init {
        status = PlayerStatus.STOPPED
    }

    protected open fun setPlayable(playable: EpisodeMedia?) {
        if (playable != null && playable !== curMedia) curMedia = playable
    }

    open fun getVideoSize(): Pair<Int, Int>? {
        return null
    }

    abstract fun getPlaybackSpeed(): Float

    abstract fun getDuration(): Int

    abstract fun getPosition(): Int

    open fun getAudioTracks(): List<String?>? {
        return emptyList()
    }

    open fun getSelectedAudioTrack(): Int {
        return -1
    }

    open fun createMediaPlayer() {}

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    protected fun setDataSource(metadata: MediaMetadata, mediaUrl: String, user: String?, password: String?) {
        Logd(TAG, "setDataSource: $mediaUrl")
        mediaItem = MediaItem.Builder().setUri(Uri.parse(mediaUrl)).setMediaMetadata(metadata).build()
        mediaSource = null
        setSourceCredentials(user, password)
    }

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    protected open fun setDataSource(metadata: MediaMetadata, media: EpisodeMedia) {
        Logd(TAG, "setDataSource1 called")
        val url = media.getStreamUrl() ?: return
        val preferences = media.episodeOrFetch()?.feed?.preferences
        val user = preferences?.username
        val password = preferences?.password
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
                media.bitrate = audioStream.bitrate
                Logd(TAG, "setDataSource1 use audio quality: ${audioStream.bitrate} forceVideo: ${media.forceVideo}")

                media.effectUrl = audioStream.content
                val aSource = DefaultMediaSourceFactory(context).createMediaSource(
                    MediaItem.Builder().setMediaMetadata(metadata).setTag(metadata).setUri(Uri.parse(audioStream.content)).build())
                if (media.forceVideo || media.episode?.feed?.preferences?.videoModePolicy != VideoMode.AUDIO_ONLY) {
                    Logd(TAG, "setDataSource1 result: $streamInfo")
                    Logd(TAG, "setDataSource1 videoStreams: ${streamInfo.videoStreams.size} videoOnlyStreams: ${streamInfo.videoOnlyStreams.size} audioStreams: ${streamInfo.audioStreams.size}")
//                    val videoStreamsList = getSortedStreamVideosList(streamInfo.videoStreams, streamInfo.videoOnlyStreams, true, true)
                    val videoStreamsList = getSortedStreamVideosList(listOf(), streamInfo.videoOnlyStreams, true, true)
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
                    val vSource = DefaultMediaSourceFactory(context).createMediaSource(
                        MediaItem.Builder().setMediaMetadata(metadata).setTag(metadata).setUri(Uri.parse(videoStream.content)).build())
                    val mediaSources: MutableList<MediaSource> = ArrayList()
                    mediaSources.add(vSource)
                    mediaSources.add(aSource)
                    mediaSource = MergingMediaSource(true, *mediaSources.toTypedArray<MediaSource>())
//                mediaSource = null
                } else mediaSource = aSource
                mediaItem = mediaSource?.mediaItem
                setSourceCredentials(user, password)
            } catch (throwable: Throwable) { Log.e(TAG, "setDataSource1 error: ${throwable.message}") }
        } else {
            Logd(TAG, "setDataSource1 setting for Podcast source")
            setDataSource(metadata, url,user, password)
        }
    }

    private fun setSourceCredentials(user: String?, password: String?) {
        if (!user.isNullOrEmpty() && !password.isNullOrEmpty()) {
            if (httpDataSourceFactory == null)
                httpDataSourceFactory = OkHttpDataSource.Factory(PodciniHttpClient.getHttpClient() as okhttp3.Call.Factory).setUserAgent(ClientConfig.USER_AGENT)

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
     * Join the two lists of video streams (video_only and normal videos),
     * and sort them according with default format chosen by the user.
     * @param videoStreams           normal videos list
     * @param videoOnlyStreams       video only stream list
     * @param ascendingOrder         true -> smallest to greatest | false -> greatest to smallest
     * @param preferVideoOnlyStreams if video-only streams should preferred when both video-only streams and normal video streams are available
     * @return the sorted list
     */
    protected fun getSortedStreamVideosList(videoStreams: List<VideoStream>?, videoOnlyStreams: List<VideoStream>?, ascendingOrder: Boolean,
                                          preferVideoOnlyStreams: Boolean): List<VideoStream> {
        val videoStreamsOrdered = if (preferVideoOnlyStreams) listOf(videoStreams, videoOnlyStreams) else listOf(videoOnlyStreams, videoStreams)
        val allInitialStreams = videoStreamsOrdered.filterNotNull().flatten().toList()
        val comparator = compareBy<VideoStream> { it.resolution.toResolutionValue() }
        return if (ascendingOrder) allInitialStreams.sortedWith(comparator) else { allInitialStreams.sortedWith(comparator.reversed()) }
    }

    private fun String.toResolutionValue(): Int {
        val match = Regex("(\\d+)p|(\\d+)k").find(this)
        return when {
            match?.groupValues?.get(1) != null -> match.groupValues[1].toInt()
            match?.groupValues?.get(2) != null -> match.groupValues[2].toInt() * 1024
            else -> 0
        }
    }

    protected fun getFilteredAudioStreams(audioStreams: List<AudioStream>?): List<AudioStream> {
        if (audioStreams == null) return listOf()
        val collectedStreams = mutableSetOf<AudioStream>()
        for (stream in audioStreams) {
            Logd(TAG, "getFilteredAudioStreams stream: ${stream.audioTrackId} ${stream.bitrate} ${stream.deliveryMethod} ${stream.format}")
            if (stream.deliveryMethod == DeliveryMethod.TORRENT || (stream.deliveryMethod == DeliveryMethod.HLS && stream.format == MediaFormat.OPUS))
                continue
            collectedStreams.add(stream)
        }
        return collectedStreams.toList().sortedWith(compareBy { it.bitrate })
    }

    /**
     * Starts or prepares playback of the specified EpisodeMedia object. If another EpisodeMedia object is already being played, the currently playing
     * episode will be stopped and replaced with the new EpisodeMedia object. If the EpisodeMedia object is already being played, the method will
     * not do anything.
     * Whether playback starts immediately depends on the given parameters. See below for more details.
     *
     * States:
     * During execution of the method, the object will be in the INITIALIZING state. The end state depends on the given parameters.
     *
     * If 'prepareImmediately' is set to true, the method will go into PREPARING state and after that into PREPARED state. If
     * 'startWhenPrepared' is set to true, the method will additionally go into PLAYING state.
     *
     * If an unexpected error occurs while loading the EpisodeMedia's metadata or while setting the MediaPlayers data source, the object
     * will enter the ERROR state.
     *
     * This method is executed on an internal executor service.
     *
     * @param playable           The EpisodeMedia object that is supposed to be played. This parameter must not be null.
     * @param streaming             The type of playback. If false, the EpisodeMedia object MUST provide access to a locally available file via
     * getLocalMediaUrl. If true, the EpisodeMedia object MUST provide access to a resource that can be streamed by
     * the Android MediaPlayer via getStreamUrl.
     * @param startWhenPrepared  Sets the 'startWhenPrepared' flag. This flag determines whether playback will start immediately after the
     * episode has been prepared for playback. Setting this flag to true does NOT mean that the episode will be prepared
     * for playback immediately (see 'prepareImmediately' parameter for more details)
     * @param prepareImmediately Set to true if the method should also prepare the episode for playback.
     */
    abstract fun playMediaObject(playable: EpisodeMedia, streaming: Boolean, startWhenPrepared: Boolean, prepareImmediately: Boolean, forceReset: Boolean = false)

    /**
     * Resumes playback if the PSMP object is in PREPARED or PAUSED state. If the PSMP object is in an invalid state.
     * nothing will happen.
     * This method is executed on an internal executor service.
     */
    abstract fun resume()

    /**
     * Saves the current position and pauses playback. Note that, if audiofocus
     * is abandoned, the lockscreen controls will also disapear.
     * This method is executed on an internal executor service.
     * @param abandonFocus is true if the service should release audio focus
     * @param reinit       is true if service should reinit after pausing if the media
     * file is being streamed
     */
    abstract fun pause(reinit: Boolean)

    /**
     * Prepared media player for playback if the service is in the INITALIZED
     * state.
     * This method is executed on an internal executor service.
     */
    abstract fun prepare()

    /**
     * Resets the media player and moves it into INITIALIZED state.
     * This method is executed on an internal executor service.
     */
    abstract fun reinit()

    /**
     * Seeks to the specified position. If the PSMP object is in an invalid state, this method will do nothing.
     * Invalid time values (< 0) will be ignored.
     * This method is executed on an internal executor service.
     */
    abstract fun seekTo(t: Int)

    /**
     * Seek a specific position from the current position
     * @param delta offset from current position (positive or negative)
     */
    fun seekDelta(delta: Int) {
        val curPosition = getPosition()
        if (curPosition != EpisodeMedia.INVALID_TIME) seekTo(curPosition + delta)
        else Log.e(TAG, "seekDelta getPosition() returned INVALID_TIME in seekDelta")
    }

    /**
     * Sets the playback parameters.
     * - Speed
     * - SkipSilence (ExoPlayer only)
     * This method is executed on an internal executor service.
     */
    abstract fun setPlaybackParams(speed: Float, skipSilence: Boolean)

    /**
     * Sets the playback volume.
     * This method is executed on an internal executor service.
     */
    abstract fun setVolume(volumeLeft: Float, volumeRight: Float)

    /**
     * Releases internally used resources. This method should only be called when the object is not used anymore.
     */
    abstract fun shutdown()

    open fun setVideoSurface(surface: SurfaceHolder?) {
        throw UnsupportedOperationException("Setting Video Surface unsupported in Remote Media Player")
    }

    open fun resetVideoSurface() {
        Log.e(TAG, "Resetting Video Surface unsupported in Remote Media Player")
    }

    open fun setAudioTrack(track: Int) {}

    fun skip() {
//        in first second of playback, ignoring skip
        if (getPosition() < 1000) return
        endPlayback(hasEnded = false, wasSkipped = true, shouldContinue = true, toStoppedState = true)
    }

    /**
     * Internal method that handles end of playback.
     * Currently, it has 5 use cases:
     *  * Media playback has completed: call with (true, false, true, true)
     *  * User asks to skip to next episode: call with (false, true, true, true)
     *  * Skipping to next episode due to playback error: call with (false, false, true, true)
     *  * Stopping the media player: call with (false, false, false, true)
     *  * We want to change the media player implementation: call with (false, false, false, false)
     *
     * @param hasEnded         If true, we assume the current media's playback has ended, for
     * purposes of post playback processing.
     * @param wasSkipped       Whether the user chose to skip the episode (by pressing the skip button).
     * @param shouldContinue   If true, the media player should try to load, and possibly play,
     * the next item, based on the user preferences and whether such item exists.
     * @param toStoppedState   If true, the playback state gets set to STOPPED if the media player
     * is not loading/playing after this call, and the UI will reflect that.
     * Only relevant if {@param shouldContinue} is set to false, otherwise
     * this method's behavior defaults as if this parameter was true.
     *
     * @return a Future, just for the purpose of tracking its execution.
     */
    internal abstract fun endPlayback(hasEnded: Boolean, wasSkipped: Boolean, shouldContinue: Boolean, toStoppedState: Boolean)

    /**
     * @return `true` if the WifiLock feature should be used, `false` otherwise.
     */
    protected open fun shouldLockWifi(): Boolean {
        return false
    }

    abstract fun isCasting(): Boolean

    @Synchronized
    protected fun acquireWifiLockIfNecessary() {
        if (shouldLockWifi()) {
            if (wifiLock == null) {
                wifiLock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(WifiManager.WIFI_MODE_FULL, TAG)
                wifiLock?.setReferenceCounted(false)
            }
            wifiLock?.acquire()
        }
    }

    @Synchronized
    protected fun releaseWifiLockIfNecessary() {
        if (wifiLock != null && wifiLock!!.isHeld) wifiLock!!.release()
    }

    /**
     * Sets the player status of the PSMP object. PlayerStatus and media attributes have to be set at the same time
     * so that getPSMPInfo can't return an invalid state (e.g. status is PLAYING, but media is null).
     * This method will notify the callback about the change of the player status (even if the new status is the same
     * as the old one).
     * It will also call [MediaPlayerCallback.onPlaybackPause] or [MediaPlayerCallback.onPlaybackStart]
     * depending on the status change.
     * @param newStatus The new PlayerStatus. This must not be null.
     * @param newMedia  The new playable object of the PSMP object. This can be null.
     * @param position  The position to be set to the current EpisodeMedia object in case playback started or paused.
     * Will be ignored if given the value of [EpisodeMedia.INVALID_TIME].
     */
    @Synchronized
    protected fun setPlayerStatus(newStatus: PlayerStatus, newMedia: EpisodeMedia?, position: Int = EpisodeMedia.INVALID_TIME) {
        Log.d(TAG, "${this.javaClass.simpleName}: Setting player status to $newStatus")
        this.oldStatus = status
        status = newStatus
        if (newMedia != null) {
            // TODO: test, this appears not necessary
//            setPlayable(newMedia)
            if (newStatus != PlayerStatus.INDETERMINATE) {
                when {
                    oldStatus == PlayerStatus.PLAYING && newStatus != PlayerStatus.PLAYING -> callback.onPlaybackPause(newMedia, position)
                    oldStatus != PlayerStatus.PLAYING && newStatus == PlayerStatus.PLAYING -> callback.onPlaybackStart(newMedia, position)
                }
            }
        }
        callback.statusChanged(MediaPlayerInfo(oldStatus, status, curMedia))
    }

    class MediaPlayerInfo(
            @JvmField val oldPlayerStatus: PlayerStatus?,
            @JvmField var playerStatus: PlayerStatus,
            @JvmField var playable: EpisodeMedia?)

    companion object {
        private val TAG: String = MediaPlayerBase::class.simpleName ?: "Anonymous"

        @get:Synchronized
        @JvmStatic
        var status by mutableStateOf(PlayerStatus.STOPPED)

        @JvmField
        val ELAPSED_TIME_FOR_SHORT_REWIND: Long = TimeUnit.MINUTES.toMillis(1)
        @JvmField
        val ELAPSED_TIME_FOR_MEDIUM_REWIND: Long = TimeUnit.HOURS.toMillis(1)
        @JvmField
        val ELAPSED_TIME_FOR_LONG_REWIND: Long = TimeUnit.DAYS.toMillis(1)

        @JvmField
        val SHORT_REWIND: Long = TimeUnit.SECONDS.toMillis(3)
        @JvmField
        val MEDIUM_REWIND: Long = TimeUnit.SECONDS.toMillis(10)
        @JvmField
        val LONG_REWIND: Long = TimeUnit.SECONDS.toMillis(20)

        @JvmStatic
        var httpDataSourceFactory:  OkHttpDataSource.Factory? = null

        val prefPlaybackSpeed: Float
            get() {
                try { return appPrefs.getString(Prefs.prefPlaybackSpeed.name, "1.00")!!.toFloat()
                } catch (e: NumberFormatException) {
                    Log.e(TAG, Log.getStackTraceString(e))
                    setPlaybackSpeed(1.0f)
                    return 1.0f
                }
            }

        fun buildMetadata(p: EpisodeMedia): MediaMetadata {
            val builder = MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(true)
                .setArtist(p.getFeedTitle())
                .setTitle(p.getEpisodeTitle())
                .setAlbumArtist(p.getFeedTitle())
                .setDisplayTitle(p.getEpisodeTitle())
                .setSubtitle(p.getFeedTitle())
                .setArtworkUri(null)
            return builder.build()
        }

        fun buildMediaItem(p: EpisodeMedia): MediaItem? {
            val url = p.getStreamUrl() ?: return null
            val metadata = buildMetadata(p)
            return MediaItem.Builder()
                .setMediaId(url)
                .setUri(Uri.parse(url))
                .setMediaMetadata(metadata).build()
        }

        /**
         * @param currentPosition  current position in a media file in ms
         * @param lastPlayedTime  timestamp when was media paused
         * @return  new rewinded position for playback in milliseconds
         */
        @JvmStatic
        fun calculatePositionWithRewind(currentPosition: Int, lastPlayedTime: Long): Int {
            if (currentPosition > 0 && lastPlayedTime > 0) {
                val elapsedTime = System.currentTimeMillis() - lastPlayedTime
                var rewindTime: Long = 0
                when {
                    elapsedTime > ELAPSED_TIME_FOR_LONG_REWIND -> rewindTime = LONG_REWIND
                    elapsedTime > ELAPSED_TIME_FOR_MEDIUM_REWIND -> rewindTime = MEDIUM_REWIND
                    elapsedTime > ELAPSED_TIME_FOR_SHORT_REWIND -> rewindTime = SHORT_REWIND
                }
                val newPosition = currentPosition - rewindTime.toInt()
                return max(newPosition.toDouble(), 0.0).toInt()
            } else return currentPosition
        }

        /**
         * Returns the currently configured playback speed for the specified media.
         */
        @JvmStatic
        fun getCurrentPlaybackSpeed(media: EpisodeMedia?): Float {
            var playbackSpeed = FeedPreferences.SPEED_USE_GLOBAL
            if (media != null) {
                playbackSpeed = curState.curTempSpeed
                // TODO: something strange here?
                if (playbackSpeed == FeedPreferences.SPEED_USE_GLOBAL) {
                    val prefs_ = media.episodeOrFetch()?.feed?.preferences
                    if (prefs_ != null) playbackSpeed = prefs_.playSpeed
                }
            }
            if (playbackSpeed == FeedPreferences.SPEED_USE_GLOBAL) playbackSpeed = prefPlaybackSpeed
            return playbackSpeed
        }
    }
}
