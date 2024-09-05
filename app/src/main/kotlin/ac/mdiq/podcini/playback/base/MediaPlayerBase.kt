package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.InTheatre.curState
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.preferences.UserPreferences.setPlaybackSpeed
import ac.mdiq.podcini.preferences.UserPreferences.videoPlaybackSpeed
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.FeedPreferences
import ac.mdiq.podcini.storage.model.MediaType
import ac.mdiq.podcini.storage.model.Playable
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.util.Log
import android.util.Pair
import android.view.SurfaceHolder
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.Volatile
import kotlin.math.max

/*
* An inconvenience of an implementation like this is that some members and methods that once were
* private are now protected, allowing for access from classes of the same package, namely
* PlaybackService. A workaround would be to move this to a dedicated package.
*/
/**
 * Abstract class that allows for different implementations of the PlaybackServiceMediaPlayer for local
 * and remote (cast devices) playback.
 */
abstract class MediaPlayerBase protected constructor(protected val context: Context, protected val callback: MediaPlayerCallback) {

    @Volatile
    private var oldStatus: PlayerStatus? = null
    internal var prevMedia: Playable? = null

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

    protected open fun setPlayable(playable: Playable?) {
        if (playable != null && playable !== curMedia) {
            curMedia = playable
        }
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

    abstract fun createMediaPlayer()

    /**
     * Starts or prepares playback of the specified Playable object. If another Playable object is already being played, the currently playing
     * episode will be stopped and replaced with the new Playable object. If the Playable object is already being played, the method will
     * not do anything.
     * Whether playback starts immediately depends on the given parameters. See below for more details.
     *
     * States:
     * During execution of the method, the object will be in the INITIALIZING state. The end state depends on the given parameters.
     *
     * If 'prepareImmediately' is set to true, the method will go into PREPARING state and after that into PREPARED state. If
     * 'startWhenPrepared' is set to true, the method will additionally go into PLAYING state.
     *
     * If an unexpected error occurs while loading the Playable's metadata or while setting the MediaPlayers data source, the object
     * will enter the ERROR state.
     *
     * This method is executed on an internal executor service.
     *
     * @param playable           The Playable object that is supposed to be played. This parameter must not be null.
     * @param streaming             The type of playback. If false, the Playable object MUST provide access to a locally available file via
     * getLocalMediaUrl. If true, the Playable object MUST provide access to a resource that can be streamed by
     * the Android MediaPlayer via getStreamUrl.
     * @param startWhenPrepared  Sets the 'startWhenPrepared' flag. This flag determines whether playback will start immediately after the
     * episode has been prepared for playback. Setting this flag to true does NOT mean that the episode will be prepared
     * for playback immediately (see 'prepareImmediately' parameter for more details)
     * @param prepareImmediately Set to true if the method should also prepare the episode for playback.
     */
    abstract fun playMediaObject(playable: Playable, streaming: Boolean, startWhenPrepared: Boolean, prepareImmediately: Boolean, forceReset: Boolean = false)

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
    abstract fun pause(abandonFocus: Boolean, reinit: Boolean)

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
        if (curPosition != Playable.INVALID_TIME) {
            val prevMedia = curMedia
            seekTo(curPosition + delta)
        }
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
     * @param position  The position to be set to the current Playable object in case playback started or paused.
     * Will be ignored if given the value of [Playable.INVALID_TIME].
     */
    @Synchronized
    protected fun setPlayerStatus(newStatus: PlayerStatus, newMedia: Playable?, position: Int = Playable.INVALID_TIME) {
        Log.d(TAG, "${this.javaClass.simpleName}: Setting player status to $newStatus")
        this.oldStatus = status
        status = newStatus
        if (newMedia != null) setPlayable(newMedia)
        if (newMedia != null && newStatus != PlayerStatus.INDETERMINATE) {
            when {
                oldStatus == PlayerStatus.PLAYING && newStatus != PlayerStatus.PLAYING -> callback.onPlaybackPause(newMedia, position)
                oldStatus != PlayerStatus.PLAYING && newStatus == PlayerStatus.PLAYING -> callback.onPlaybackStart(newMedia, position)
            }
        }
        callback.statusChanged(MediaPlayerInfo(oldStatus, status, curMedia))
    }

    class MediaPlayerInfo(
            @JvmField val oldPlayerStatus: PlayerStatus?,
            @JvmField var playerStatus: PlayerStatus,
            @JvmField var playable: Playable?)

    companion object {
        private val TAG: String = MediaPlayerBase::class.simpleName ?: "Anonymous"

        @get:Synchronized
        @Volatile
        @JvmStatic
        var status: PlayerStatus = PlayerStatus.STOPPED

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

        val audioPlaybackSpeed: Float
            get() {
                try { return appPrefs.getString(UserPreferences.Prefs.prefPlaybackSpeed.name, "1.00")!!.toFloat()
                } catch (e: NumberFormatException) {
                    Log.e(TAG, Log.getStackTraceString(e))
                    setPlaybackSpeed(1.0f)
                    return 1.0f
                }
            }

        fun buildMetadata(p: Playable): MediaMetadata {
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

        fun buildMediaItem(p: Playable): MediaItem? {
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
        fun getCurrentPlaybackSpeed(media: Playable?): Float {
            var playbackSpeed = FeedPreferences.SPEED_USE_GLOBAL
            val mediaType: MediaType? = media?.getMediaType()
            if (media != null) {
                playbackSpeed = curState.curTempSpeed
                if (playbackSpeed == FeedPreferences.SPEED_USE_GLOBAL && media is EpisodeMedia) {
                    val prefs_ = media.episodeOrFetch()?.feed?.preferences
                    if (prefs_ != null) playbackSpeed = prefs_.playSpeed
                }
            }
            if (mediaType != null && playbackSpeed == FeedPreferences.SPEED_USE_GLOBAL) playbackSpeed = getPlaybackSpeed(mediaType)
            return playbackSpeed
        }

        fun getPlaybackSpeed(mediaType: MediaType): Float {
            return if (mediaType == MediaType.VIDEO) videoPlaybackSpeed else audioPlaybackSpeed
        }
    }
}
