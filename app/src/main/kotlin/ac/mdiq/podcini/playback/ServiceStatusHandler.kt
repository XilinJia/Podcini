package ac.mdiq.podcini.playback

import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.InTheatre.curState
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isRunning
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Communicates with the playback service. GUI classes should use this class to
 * control playback instead of communicating with the PlaybackService directly.
 */
abstract class ServiceStatusHandler(private val activity: FragmentActivity) {

    private var mediaInfoLoaded = false
    private var loadedFeedMediaId: Long = -1
    private var initialized = false

    private var prevStatus = PlayerStatus.STOPPED
    private val statusUpdate: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "statusUpdate onReceive called with action: ${intent.action}")
            if (playbackService != null && PlaybackService.mPlayerInfo != null) {
                val info = PlaybackService.mPlayerInfo!!
//                Logd(TAG, "statusUpdate onReceive $prevStatus ${MediaPlayerBase.status} ${info.playerStatus} ${curMedia?.id} ${info.playable?.id}.")
                if (prevStatus != info.playerStatus || curMedia == null || curMedia!!.id != info.playable?.id) {
                    Logd(TAG, "statusUpdate onReceive doing updates")
                    MediaPlayerBase.status = info.playerStatus
                    prevStatus = MediaPlayerBase.status
//                    curMedia = info.playable
                    handleStatus()
                }
            } else {
                Logd(TAG, "statusUpdate onReceive: Couldn't receive status update: playbackService was null")
                if (!isRunning) {
                    MediaPlayerBase.status = PlayerStatus.STOPPED
                    handleStatus()
                }
            }
        }
    }

    private val notificationReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "notificationReceiver onReceive called with action: ${intent.action}")
            val type = intent.getIntExtra(PlaybackService.EXTRA_NOTIFICATION_TYPE, -1)
            val code = intent.getIntExtra(PlaybackService.EXTRA_NOTIFICATION_CODE, -1)
            if (code == -1 || type == -1) {
                Logd(TAG, "Bad arguments. Won't handle intent")
                return
            }
            when (type) {
                PlaybackService.NOTIFICATION_TYPE_RELOAD -> {
                    if (playbackService == null && isRunning) return
                    mediaInfoLoaded = false
                    updateStatus()
                }
                PlaybackService.NOTIFICATION_TYPE_PLAYBACK_END -> onPlaybackEnd()
            }
        }
    }

    @Synchronized
    fun init() {
        Logd(TAG, "controller init")
        procFlowEvents()
        if (isRunning) initServiceRunning()
        else updatePlayButton(true)
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
                    is FlowEvent.PlaybackServiceEvent -> {
                        if (event.action == FlowEvent.PlaybackServiceEvent.Action.SERVICE_STARTED) {
                            init()
                            updateStatus()
                        }
                    }
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
        } catch (e: IllegalArgumentException) {/* ignore */ }
        initialized = false
        cancelFlowEvents()
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
            PlayerStatus.PLAYING -> updatePlayButton(false)
            PlayerStatus.PREPARING -> updatePlayButton(!PlaybackService.isStartWhenPrepared)
            PlayerStatus.FALLBACK, PlayerStatus.PAUSED, PlayerStatus.PREPARED, PlayerStatus.STOPPED, PlayerStatus.INITIALIZED -> updatePlayButton(true)
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

    protected open fun updatePlayButton(showPlay: Boolean) {}

    abstract fun loadMediaInfo()

    /**
     * Called when connection to playback service has been established or
     * information has to be refreshed
     */
    private fun updateStatus() {
        Logd(TAG, "Querying service info")
        if (playbackService != null && PlaybackService.mPlayerInfo != null) {
            MediaPlayerBase.status = PlaybackService.mPlayerInfo!!.playerStatus
//            curMedia = PlaybackService.mPlayerInfo!!.playable
            // make sure that new media is loaded if it's available
            mediaInfoLoaded = false
            handleStatus()
        } else Log.e(TAG, "queryService() was called without an existing connection to playbackservice")
    }

    companion object {
        private val TAG: String = ServiceStatusHandler::class.simpleName ?: "Anonymous"
    }
}
