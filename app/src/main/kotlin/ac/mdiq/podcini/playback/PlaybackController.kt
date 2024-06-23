package ac.mdiq.podcini.playback

import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.InTheatre.curState
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.getCurrentPlaybackSpeed
import ac.mdiq.podcini.playback.base.MediaPlayerBase.MediaPlayerInfo
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.currentMediaType
import ac.mdiq.podcini.playback.service.PlaybackService.LocalBinder
import ac.mdiq.podcini.preferences.UserPreferences.isSkipSilence
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.storage.utils.MediaType
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.content.*
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Communicates with the playback service. GUI classes should use this class to
 * control playback instead of communicating with the PlaybackService directly.
 */
@UnstableApi
abstract class PlaybackController(private val activity: FragmentActivity) {

    private var mediaInfoLoaded = false
    private var loadedFeedMediaId: Long = -1
    private var released = false
    private var initialized = false
    private var eventsRegistered = false

    @Synchronized
    fun init() {
        Logd(TAG, "controller init")
        if (!eventsRegistered) {
            procFlowEvents()
            eventsRegistered = true
        }
        if (PlaybackService.isRunning) initServiceRunning()
        else updatePlayButtonShowsPlay(true)
    }

    private var eventSink: Job?     = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    private fun procFlowEvents() {
        if (eventSink != null) return
        eventSink = activity.lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.PlaybackServiceEvent -> if (event.action == FlowEvent.PlaybackServiceEvent.Action.SERVICE_STARTED) init()
                    else -> {}
                }
            }
        }
    }

    @Synchronized
    private fun initServiceRunning() {
        if (initialized) return
        initialized = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(statusUpdate, IntentFilter(PlaybackService.ACTION_PLAYER_STATUS_CHANGED), Context.RECEIVER_NOT_EXPORTED)
            activity.registerReceiver(notificationReceiver, IntentFilter(PlaybackService.ACTION_PLAYER_NOTIFICATION), Context.RECEIVER_NOT_EXPORTED)
        } else {
            activity.registerReceiver(statusUpdate, IntentFilter(PlaybackService.ACTION_PLAYER_STATUS_CHANGED))
            activity.registerReceiver(notificationReceiver, IntentFilter(PlaybackService.ACTION_PLAYER_NOTIFICATION))
        }

//        TODO: java.lang.IllegalStateException: Can't call init() after release() has been called
//        at ac.mdiq.podcini.playback.PlaybackController.initServiceRunning(SourceFile:104)
        if (!released) {
            bindToService()
        } else {
            released = false
            bindToService()
            Logd(TAG, "Testing bindToService if released")
//            throw IllegalStateException("Can't call init() after release() has been called")
        }
        checkMediaInfoLoaded()
    }

    /**
     * Should be called if the PlaybackController is no longer needed, for
     * example in the activity's onStop() method.
     */
    fun release() {
        Logd(TAG, "Releasing PlaybackController")
        try {
            activity.unregisterReceiver(statusUpdate)
            activity.unregisterReceiver(notificationReceiver)
        } catch (e: IllegalArgumentException) {
            // ignore
        }
        unbind()
        cancelFlowEvents()
        released = true
        eventsRegistered = false
    }

    private fun unbind() {
        try { activity.unbindService(mConnection) } catch (e: IllegalArgumentException) { }
        initialized = false
    }

    /**
     * Should be called in the activity's onPause() method.
     */
    fun pause() {
//        TODO: why set it to false
//        mediaInfoLoaded = false
        Logd(TAG, "pause() does nothing")
    }

    /**
     * Tries to establish a connection to the PlaybackService. If it isn't
     * running, the PlaybackService will be started with the last played media
     * as the arguments of the launch intent.
     */
    private fun bindToService() {
        Logd(TAG, "Trying to connect to service")
        check(PlaybackService.isRunning) { "Trying to bind but service is not running" }
        val bound = activity.bindService(Intent(activity, PlaybackService::class.java), mConnection, 0)
        Logd(TAG, "Result for service binding: $bound")
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            if (service is LocalBinder) {
                playbackService = service.service
                onPlaybackServiceConnected()
                if (!released) {
                    queryService()
                    Logd(TAG, "Connection to Service established")
                } else Log.i(TAG, "Connection to playback service has been established, but controller has already been released")
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            playbackService = null
            initialized = false
            Logd(TAG, "Disconnected from Service")
        }
    }

    private var prevStatus = PlayerStatus.STOPPED
    private val statusUpdate: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Logd(TAG, "BroadcastReceiver onReceive")
            if (playbackService != null && mPlayerInfo != null) {
                val info = mPlayerInfo!!
                Logd(TAG, "statusUpdate onReceive $prevStatus ${MediaPlayerBase.status} ${info.playerStatus} ${curMedia?.getIdentifier()} ${info.playable?.getIdentifier()}.")
                if (prevStatus != info.playerStatus || curMedia == null || curMedia!!.getIdentifier() != info.playable?.getIdentifier()) {
                    MediaPlayerBase.status = info.playerStatus
                    prevStatus = MediaPlayerBase.status
                    curMedia = info.playable
                    handleStatus()
                }
            } else {
                Log.w(TAG, "statusUpdate onReceive: Couldn't receive status update: playbackService was null")
                if (PlaybackService.isRunning) bindToService()
                else {
                    MediaPlayerBase.status = PlayerStatus.STOPPED
                    handleStatus()
                }
            }
        }
    }

    private val notificationReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val type = intent.getIntExtra(PlaybackService.EXTRA_NOTIFICATION_TYPE, -1)
            val code = intent.getIntExtra(PlaybackService.EXTRA_NOTIFICATION_CODE, -1)
            if (code == -1 || type == -1) {
                Logd(TAG, "Bad arguments. Won't handle intent")
                return
            }
            when (type) {
                PlaybackService.NOTIFICATION_TYPE_RELOAD -> {
                    if (playbackService == null && PlaybackService.isRunning) {
                        bindToService()
                        return
                    }
                    mediaInfoLoaded = false
                    queryService()
                }
                PlaybackService.NOTIFICATION_TYPE_PLAYBACK_END -> onPlaybackEnd()
            }
        }
    }

    open fun onPlaybackEnd() {}

    /**
     * Is called whenever the PlaybackService changes its status. This method
     * should be used to update the GUI or start/cancel background threads.
     */
    private fun handleStatus() {
        Log.d(TAG, "handleStatus() called status: ${MediaPlayerBase.status}")
        checkMediaInfoLoaded()

        when (MediaPlayerBase.status) {
            PlayerStatus.PLAYING -> updatePlayButtonShowsPlay(false)
            PlayerStatus.PREPARING -> updatePlayButtonShowsPlay(!isStartWhenPrepared)
            PlayerStatus.FALLBACK, PlayerStatus.PAUSED, PlayerStatus.PREPARED, PlayerStatus.STOPPED, PlayerStatus.INITIALIZED ->
                updatePlayButtonShowsPlay(true)
            else -> {}
        }
    }

    private fun checkMediaInfoLoaded() {
        if (!mediaInfoLoaded || loadedFeedMediaId != curState.curMediaId) {
            loadedFeedMediaId = curState.curMediaId
            Logd(TAG, "checkMediaInfoLoaded: $loadedFeedMediaId")
            loadMediaInfo()
        }
        mediaInfoLoaded = true
    }

    protected open fun updatePlayButtonShowsPlay(showPlay: Boolean) {}

    abstract fun loadMediaInfo()

    open fun onPlaybackServiceConnected() { }

    /**
     * Called when connection to playback service has been established or
     * information has to be refreshed
     */
    private fun queryService() {
        Logd(TAG, "Querying service info")
        if (playbackService != null && mPlayerInfo != null) {
            MediaPlayerBase.status = mPlayerInfo!!.playerStatus
            curMedia = mPlayerInfo!!.playable
            // make sure that new media is loaded if it's available
            mediaInfoLoaded = false
            handleStatus()
        } else {
            Log.e(TAG, "queryService() was called without an existing connection to playbackservice")
        }
    }

    fun ensureService() {
        if (curMedia == null) return
        if (playbackService == null) {
            PlaybackServiceStarter(activity, curMedia!!).start()
            Log.w(TAG, "playbackservice was null, restarted!")
        }
    }

    fun playPause() {
        if (curMedia == null) return
        if (playbackService == null) {
            PlaybackServiceStarter(activity, curMedia!!).start()
            Log.w(TAG, "playbackservice was null, restarted!")
            return
        }
        when (MediaPlayerBase.status) {
            PlayerStatus.FALLBACK -> fallbackSpeed(1.0f)
            PlayerStatus.PLAYING -> {
                playbackService?.mediaPlayer?.pause(true, reinit = false)
                playbackService?.isSpeedForward =  false
                playbackService?.isFallbackSpeed = false
//                if (curEpisode != null) EventFlow.postEvent(FlowEvent.PlayEvent(curEpisode!!, FlowEvent.PlayEvent.Action.END))
            }
            PlayerStatus.PAUSED, PlayerStatus.PREPARED -> {
                playbackService?.mediaPlayer?.resume()
                playbackService?.taskManager?.restartSleepTimer()
//                if (curEpisode != null) EventFlow.postEvent(FlowEvent.PlayEvent(curEpisode!!))
            }
            PlayerStatus.PREPARING -> isStartWhenPrepared = !isStartWhenPrepared
            PlayerStatus.INITIALIZED -> {
                if (playbackService != null) isStartWhenPrepared = true
                playbackService?.mediaPlayer?.prepare()
                playbackService?.taskManager?.restartSleepTimer()
            }
            else -> {
                PlaybackServiceStarter(activity, curMedia!!).callEvenIfRunning(true).start()
                Log.w(TAG, "Play/Pause button was pressed and PlaybackService state was unknown")
            }
        }
    }

    companion object {
        private val TAG: String = PlaybackController::class.simpleName ?: "Anonymous"

        var playbackService: PlaybackService? = null

        val position: Int
            get() = playbackService?.currentPosition ?: curMedia?.getPosition() ?: Playable.INVALID_TIME

        val duration: Int
            get() = playbackService?.duration ?: curMedia?.getDuration() ?: Playable.INVALID_TIME

        val curSpeedMultiplier: Float
            get() = playbackService?.currentPlaybackSpeed ?: getCurrentPlaybackSpeed(curMedia)

        val isPlayingVideoLocally: Boolean
            get() = when {
                PlaybackService.isCasting -> false
                playbackService != null -> currentMediaType == MediaType.VIDEO
                else -> curMedia?.getMediaType() == MediaType.VIDEO
            }

        private var isStartWhenPrepared: Boolean
            get() = playbackService?.mediaPlayer?.startWhenPrepared?.get() ?: false
            set(s) {
                playbackService?.mediaPlayer?.startWhenPrepared?.set(s)
            }

        private val mPlayerInfo: MediaPlayerInfo?
            get() = playbackService?.mediaPlayer?.playerInfo

        fun seekTo(time: Int) {
            if (playbackService != null) {
                playbackService!!.mediaPlayer?.seekTo(time)
                if (curMedia != null) EventFlow.postEvent(FlowEvent.PlaybackPositionEvent(time, duration))
            }
        }

        fun fallbackSpeed(speed: Float) {
            if (playbackService != null) {
                when (MediaPlayerBase.status) {
                    PlayerStatus.PLAYING -> {
                        MediaPlayerBase.status = PlayerStatus.FALLBACK
                        fallbackSpeed_(speed)
                    }
                    PlayerStatus.FALLBACK -> {
                        MediaPlayerBase.status = PlayerStatus.PLAYING
                        fallbackSpeed_(speed)
                    }
                    else -> {}
                }
            }
        }

        private fun fallbackSpeed_(speed: Float) {
            if (playbackService?.mediaPlayer == null || playbackService!!.isSpeedForward) return

            if (!playbackService!!.isFallbackSpeed) {
                playbackService!!.normalSpeed = playbackService!!.mediaPlayer!!.getPlaybackSpeed()
                playbackService!!.mediaPlayer!!.setPlaybackParams(speed, isSkipSilence)
            } else playbackService!!.mediaPlayer!!.setPlaybackParams(playbackService!!.normalSpeed, isSkipSilence)

            playbackService!!.isFallbackSpeed = !playbackService!!.isFallbackSpeed
        }

        fun sleepTimerActive(): Boolean {
            return playbackService?.taskManager?.isSleepTimerActive ?: false
        }
    }
}
