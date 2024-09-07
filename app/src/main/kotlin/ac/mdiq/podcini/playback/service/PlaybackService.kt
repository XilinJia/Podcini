package ac.mdiq.podcini.playback.service

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.net.utils.NetworkUtils.isAllowMobileStreaming
import ac.mdiq.podcini.net.utils.NetworkUtils.isStreamingAllowed
import ac.mdiq.podcini.playback.PlaybackServiceStarter
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curIndexInQueue
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.InTheatre.curState
import ac.mdiq.podcini.playback.base.InTheatre.loadPlayableFromPreferences
import ac.mdiq.podcini.playback.base.InTheatre.writeNoMediaPlaying
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.buildMediaItem
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.getCurrentPlaybackSpeed
import ac.mdiq.podcini.playback.base.MediaPlayerBase.MediaPlayerInfo
import ac.mdiq.podcini.playback.base.MediaPlayerCallback
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.cast.CastPsmp
import ac.mdiq.podcini.playback.cast.CastStateListener
import ac.mdiq.podcini.playback.service.TaskManager.PSTMCallback
import ac.mdiq.podcini.preferences.SleepTimerPreferences.autoEnable
import ac.mdiq.podcini.preferences.SleepTimerPreferences.autoEnableFrom
import ac.mdiq.podcini.preferences.SleepTimerPreferences.autoEnableTo
import ac.mdiq.podcini.preferences.SleepTimerPreferences.isInTimeRange
import ac.mdiq.podcini.preferences.SleepTimerPreferences.timerMillis
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.preferences.UserPreferences.fastForwardSecs
import ac.mdiq.podcini.preferences.UserPreferences.isSkipSilence
import ac.mdiq.podcini.preferences.UserPreferences.rewindSecs
import ac.mdiq.podcini.receiver.MediaButtonReceiver
import ac.mdiq.podcini.storage.database.Episodes.addToHistory
import ac.mdiq.podcini.storage.database.Episodes.deleteMediaSync
import ac.mdiq.podcini.storage.database.Episodes.getEpisodeByGuidOrUrl
import ac.mdiq.podcini.storage.database.Episodes.setPlayStateSync
import ac.mdiq.podcini.storage.database.Episodes.shouldDeleteRemoveFromQueue
import ac.mdiq.podcini.storage.database.Feeds.shouldAutoDeleteItem
import ac.mdiq.podcini.storage.database.Queues.removeFromQueueSync
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.unmanaged
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.CurrentState.Companion.NO_MEDIA_PLAYING
import ac.mdiq.podcini.storage.model.CurrentState.Companion.PLAYER_STATUS_OTHER
import ac.mdiq.podcini.storage.model.CurrentState.Companion.PLAYER_STATUS_PAUSED
import ac.mdiq.podcini.storage.model.CurrentState.Companion.PLAYER_STATUS_PLAYING
import ac.mdiq.podcini.storage.model.FeedPreferences.AutoDeleteAction
import ac.mdiq.podcini.storage.utils.EpisodeUtil
import ac.mdiq.podcini.storage.utils.EpisodeUtil.hasAlmostEnded
import ac.mdiq.podcini.ui.activity.starter.MainActivityStarter
import ac.mdiq.podcini.ui.activity.starter.VideoPlayerActivityStarter
import ac.mdiq.podcini.ui.utils.NotificationUtils
import ac.mdiq.podcini.ui.widget.WidgetUpdater.WidgetState
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.IntentUtils.sendLocalBroadcast
import ac.mdiq.podcini.util.Logd
import ac.mdiq.vista.extractor.Vista
import ac.mdiq.vista.extractor.stream.StreamInfo
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.bluetooth.BluetoothA2dp
import android.content.*
import android.content.Intent.EXTRA_KEY_EVENT
import android.media.AudioManager
import android.os.*
import android.os.Build.VERSION_CODES
import android.service.quicksettings.TileService
import android.util.Log
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_MEDIA_STOP
import android.view.ViewConfiguration
import android.webkit.URLUtil
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.*
import androidx.work.impl.utils.futures.SettableFuture
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.*
import kotlin.concurrent.Volatile
import kotlin.math.max

/**
 * Controls the MediaPlayer that plays a EpisodeMedia-file
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null

    internal var mPlayer: MediaPlayerBase? = null
    internal lateinit var taskManager: TaskManager
    internal var isSpeedForward = false
    internal var isFallbackSpeed = false
    internal var currentitem: Episode? = null

    private val scope = CoroutineScope(Dispatchers.Main)

    private val notificationCustomButtons = NotificationCustomButton.entries.map { command -> command.commandButton }

    private lateinit var customMediaNotificationProvider: CustomMediaNotificationProvider
    private lateinit var castStateListener: CastStateListener

    private var autoSkippedFeedMediaId: String? = null
    internal var normalSpeed = 1.0f

//    private val mBinder: IBinder = LocalBinder()
    private var clickCount = 0
    private val clickHandler = Handler(Looper.getMainLooper())

    private val status: PlayerStatus
        get() = MediaPlayerBase.status

    val curSpeed: Float
        get() = mPlayer?.getPlaybackSpeed() ?: 1.0f

    val curDuration: Int
        get() = mPlayer?.getDuration() ?: Playable.INVALID_TIME

    val curPosition: Int
        get() = mPlayer?.getPosition() ?: Playable.INVALID_TIME

    private var prevPosition: Int = -1

    private val autoStateUpdated: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "autoStateUpdated onReceive called with action: ${intent.action}")
            val status = intent.getStringExtra("media_connection_status")
            val isConnectedToCar = "media_connected" == status
            Logd(TAG, "Received Auto Connection update: $status")
            if (!isConnectedToCar) Logd(TAG, "Car was unplugged during playback.")
            else {
                val playerStatus = MediaPlayerBase.status
                when (playerStatus) {
                    PlayerStatus.PAUSED, PlayerStatus.PREPARED -> mPlayer?.resume()
                    PlayerStatus.PREPARING -> mPlayer?.startWhenPrepared?.set(!mPlayer!!.startWhenPrepared.get())
                    PlayerStatus.INITIALIZED -> {
                        mPlayer?.startWhenPrepared?.set(true)
                        mPlayer?.prepare()
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Pauses playback when the headset is disconnected and the preference set
     */
    private val headsetDisconnected: BroadcastReceiver = object : BroadcastReceiver() {
        private val TAG = "headsetDisconnected"
        private val UNPLUGGED = 0
        private val PLUGGED = 1

        override fun onReceive(context: Context, intent: Intent) {
            // Don't pause playback after we just started, just because the receiver
            // delivers the current headset state (instead of a change)
            if (isInitialStickyBroadcast) return
            Log.d(TAG, "headsetDisconnected onReceive called with action: ${intent.action}")

            if (intent.action == Intent.ACTION_HEADSET_PLUG) {
                val state = intent.getIntExtra("state", -1)
                Logd(TAG, "Headset plug event. State is $state")
                if (state != -1) {
                    when (state) {
                        UNPLUGGED -> Logd(TAG, "Headset was unplugged during playback.")
                        PLUGGED -> {
                            Logd(TAG, "Headset was plugged in during playback.")
                            unpauseIfPauseOnDisconnect(false)
                        }
                    }
                } else Log.e(TAG, "Received invalid ACTION_HEADSET_PLUG intent")
            }
        }
    }

    private val bluetoothStateUpdated: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "bluetoothStateUpdated onReceive called with action: ${intent.action}")
            if (intent.action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1)
                if (state == BluetoothA2dp.STATE_CONNECTED) {
                    Logd(TAG, "Received bluetooth connection intent")
                    unpauseIfPauseOnDisconnect(true)
                }
            }
        }
    }

    private val audioBecomingNoisy: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // sound is about to change, eg. bluetooth -> speaker
            Log.d(TAG, "audioBecomingNoisy onReceive called with action: ${intent.action}")
            Logd(TAG, "Pausing playback because audio is becoming noisy")
            pauseIfPauseOnDisconnect()
        }
    }

    private val taskManagerCallback: PSTMCallback = object : PSTMCallback {
        override fun positionSaverTick() {
            if (curPosition != prevPosition) {
//                Log.d(TAG, "positionSaverTick currentPosition: $currentPosition, currentPlaybackSpeed: $currentPlaybackSpeed")
                if (curMedia != null) EventFlow.postEvent(FlowEvent.PlaybackPositionEvent(curMedia, curPosition, curDuration))
                skipEndingIfNecessary()
                saveCurrentPosition(true, null, Playable.INVALID_TIME)
                prevPosition = curPosition
            }
        }
        override fun requestWidgetState(): WidgetState {
            return WidgetState(curMedia, status, curPosition, curDuration, curSpeed)
        }
        override fun onChapterLoaded(media: Playable?) {
            sendNotificationBroadcast(NOTIFICATION_TYPE_RELOAD, 0)
        }
    }

    private val shutdownReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "shutdownReceiver onReceive called with action: ${intent.action}")
            if (intent.action == ACTION_SHUTDOWN_PLAYBACK_SERVICE)
                EventFlow.postEvent(FlowEvent.PlaybackServiceEvent(FlowEvent.PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN))
        }
    }

    val rootItem = MediaItem.Builder()
        .setMediaId("CurQueue")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .setTitle(curQueue.name)
                .build())
        .build()

    val mediaItemsInQueue: MutableList<MediaItem> by lazy {
        val list = mutableListOf<MediaItem>()
        curQueue.episodes.forEach {
            if (it.media != null) {
                val item = buildMediaItem(it.media!!)
                if (item != null) list += item
            }
        }
        Logd(TAG, "mediaItemsInQueue: ${list.size}")
        list
    }

    private val mediaPlayerCallback: MediaPlayerCallback = object : MediaPlayerCallback {
        override fun statusChanged(newInfo: MediaPlayerInfo?) {
            currentMediaType = mPlayer?.mediaType ?: MediaType.UNKNOWN
            Log.d(TAG, "statusChanged called ${newInfo?.playerStatus}")
            if (newInfo != null) {
                when (newInfo.playerStatus) {
                    PlayerStatus.INITIALIZED -> if (mPlayer != null) writeMediaPlaying(mPlayer!!.playerInfo.playable, mPlayer!!.playerInfo.playerStatus)
                    PlayerStatus.PREPARED -> {
                        if (mPlayer != null) writeMediaPlaying(mPlayer!!.playerInfo.playable, mPlayer!!.playerInfo.playerStatus)
                        if (newInfo.playable != null) taskManager.startChapterLoader(newInfo.playable!!)
                    }
                    PlayerStatus.PAUSED -> writePlayerStatus(MediaPlayerBase.status)
                    PlayerStatus.STOPPED -> {}
                    PlayerStatus.PLAYING -> {
                        writePlayerStatus(MediaPlayerBase.status)
                        saveCurrentPosition(true, null, Playable.INVALID_TIME)
                        recreateMediaSessionIfNeeded()
                        // set sleep timer if auto-enabled
                        var autoEnableByTime = true
                        val fromSetting = autoEnableFrom()
                        val toSetting = autoEnableTo()
                        if (fromSetting != toSetting) {
                            val now: Calendar = GregorianCalendar()
                            now.timeInMillis = System.currentTimeMillis()
                            val currentHour = now[Calendar.HOUR_OF_DAY]
                            autoEnableByTime = isInTimeRange(fromSetting, toSetting, currentHour)
                        }
                        if (newInfo.oldPlayerStatus != null && newInfo.oldPlayerStatus != PlayerStatus.SEEKING && autoEnable() && autoEnableByTime && !taskManager.isSleepTimerActive) {
//                            setSleepTimer(timerMillis())
                            taskManager.setSleepTimer(timerMillis())
                            EventFlow.postEvent(FlowEvent.MessageEvent(getString(R.string.sleep_timer_enabled_label), { taskManager.disableSleepTimer() }, getString(R.string.undo)))
                        }
                    }
                    PlayerStatus.ERROR -> writeNoMediaPlaying()
                    else -> {}
                }
            }
            if (Build.VERSION.SDK_INT >= VERSION_CODES.N)
                TileService.requestListeningState(applicationContext, ComponentName(applicationContext, QuickSettingsTileService::class.java))

            sendLocalBroadcast(applicationContext, ACTION_PLAYER_STATUS_CHANGED)
            bluetoothNotifyChange(newInfo, AVRCP_ACTION_PLAYER_STATUS_CHANGED)
            bluetoothNotifyChange(newInfo, AVRCP_ACTION_META_CHANGED)
            taskManager.requestWidgetUpdate()
        }

        override fun onMediaChanged(reloadUI: Boolean) {
            Logd(TAG, "onMediaChanged reloadUI callback reached")
            if (reloadUI) sendNotificationBroadcast(NOTIFICATION_TYPE_RELOAD, 0)
        }

        override fun onPostPlayback(playable: Playable?, ended: Boolean, skipped: Boolean, playingNext: Boolean) {
            if (playable == null) {
                Log.e(TAG, "Cannot do post-playback processing: media was null")
                return
            }
            Logd(TAG, "onPostPlayback(): ended=$ended skipped=$skipped playingNext=$playingNext media=${playable.getEpisodeTitle()} ")
            if (playable !is EpisodeMedia) {
                Logd(TAG, "Not doing post-playback processing: media not of type EpisodeMedia")
                if (ended) playable.onPlaybackCompleted(applicationContext)
                else playable.onPlaybackPause(applicationContext)
//            TODO: test
//            return
            }
            var item = (playable as? EpisodeMedia)?.episodeOrFetch() ?: currentitem
            val smartMarkAsPlayed = hasAlmostEnded(playable)
            if (!ended && smartMarkAsPlayed) Logd(TAG, "smart mark as played")

            var autoSkipped = false
            if (autoSkippedFeedMediaId != null && autoSkippedFeedMediaId == item?.identifyingValue) {
                autoSkippedFeedMediaId = null
                autoSkipped = true
            }
            if (playable is EpisodeMedia) {
                if (ended || smartMarkAsPlayed) {
                    SynchronizationQueueSink.enqueueEpisodePlayedIfSyncActive(applicationContext, playable, true)
                    playable.onPlaybackCompleted(applicationContext)
                } else {
                    SynchronizationQueueSink.enqueueEpisodePlayedIfSyncActive(applicationContext, playable, false)
                    playable.onPlaybackPause(applicationContext)
                }
            }
            if (item != null) {
                runOnIOScope {
                    if (ended || smartMarkAsPlayed || autoSkipped || (skipped && !shouldSkipKeepEpisode())) {
                        Logd(TAG, "onPostPlayback ended: $ended smartMarkAsPlayed: $smartMarkAsPlayed autoSkipped: $autoSkipped skipped: $skipped")
                        // only mark the item as played if we're not keeping it anyways
                        item = setPlayStateSync(Episode.PlayState.PLAYED.code, ended || (skipped && smartMarkAsPlayed), item!!)
                        val action = item?.feed?.preferences?.autoDeleteAction
                        val shouldAutoDelete = (action == AutoDeleteAction.ALWAYS ||
                                (action == AutoDeleteAction.GLOBAL && item?.feed != null && shouldAutoDeleteItem(item!!.feed!!)))
                        if (playable is EpisodeMedia && shouldAutoDelete && (item?.isFavorite != true || !shouldFavoriteKeepEpisode())) {
                            item = deleteMediaSync(this@PlaybackService, item!!)
                            if (shouldDeleteRemoveFromQueue()) removeFromQueueSync(null, item!!)
                        }
                    }
                    if (playable is EpisodeMedia && (ended || skipped || playingNext)) addToHistory(item!!)
                }
            }
        }

        fun shouldSkipKeepEpisode(): Boolean {
            return appPrefs.getBoolean(UserPreferences.Prefs.prefSkipKeepsEpisode.name, true)
        }

        fun shouldFavoriteKeepEpisode(): Boolean {
            return appPrefs.getBoolean(UserPreferences.Prefs.prefFavoriteKeepsEpisode.name, true)
        }

        override fun onPlaybackStart(playable: Playable, position: Int) {
            Logd(TAG, "onPlaybackStart position: $position")
            taskManager.startWidgetUpdater()
            if (position != Playable.INVALID_TIME) playable.setPosition(position)
            else skipIntro(playable)
            playable.onPlaybackStart()
            taskManager.startPositionSaver()
        }

        override fun onPlaybackPause(playable: Playable?, position: Int) {
            Logd(TAG, "onPlaybackPause $position")
            taskManager.cancelPositionSaver()
            saveCurrentPosition(position == Playable.INVALID_TIME || playable == null, playable, position)
            taskManager.cancelWidgetUpdater()
            if (playable != null) {
                if (playable is EpisodeMedia) SynchronizationQueueSink.enqueueEpisodePlayedIfSyncActive(applicationContext, playable, false)
                playable.onPlaybackPause(applicationContext)
            }
        }

        override fun getNextInQueue(currentMedia: Playable?): Playable? {
            Logd(TAG, "call getNextInQueue currentMedia: ${currentMedia?.getEpisodeTitle()}")
            if (currentMedia !is EpisodeMedia) {
                Logd(TAG, "getNextInQueue(), but playable not an instance of EpisodeMedia, so not proceeding")
                writeNoMediaPlaying()
                return null
            }
            val item = currentMedia.episodeOrFetch()
            if (item == null) {
                Logd(TAG, "getNextInQueue() with EpisodeMedia object whose FeedItem is null")
                writeNoMediaPlaying()
                return null
            }
            if (curIndexInQueue < 0 && item.feed?.preferences?.queue != null) {
                Logd(TAG, "getNextInQueue(), curMedia is not in curQueue")
                writeNoMediaPlaying()
                return null
            }
            val eList = if (item.feed?.preferences?.queue == null) item.feed?.getVirtualQueueItems() else curQueue.episodes
            if (eList.isNullOrEmpty()) {
                Logd(TAG, "getNextInQueue queue is empty")
                writeNoMediaPlaying()
                return null
            }
            Logd(TAG, "getNextInQueue eList: ${eList.size}")
            var j = 0
            val i = EpisodeUtil.indexOfItemWithId(eList, item.id)
            Logd(TAG, "getNextInQueue current i: $i curIndexInQueue: $curIndexInQueue")
            if (i < 0) {
                j = if (curIndexInQueue >= 0 && curIndexInQueue < eList.size) curIndexInQueue else eList.size-1
            } else if (i < eList.size-1) j = i+1
            Logd(TAG, "getNextInQueue next j: $j")
            val nextItem = unmanaged(eList[j])
            Logd(TAG, "getNextInQueue nextItem ${nextItem.title}")
            if (nextItem.media == null) {
                Logd(TAG, "getNextInQueue nextItem: $nextItem media is null")
                writeNoMediaPlaying()
                return null
            }
            if (!isFollowQueue) {
                Logd(TAG, "getNextInQueue(), but follow queue is not enabled.")
                writeMediaPlaying(nextItem.media, PlayerStatus.STOPPED)
                return null
            }
            if (!nextItem.media!!.localFileAvailable() && !isStreamingAllowed && isFollowQueue && nextItem.feed?.isLocalFeed != true) {
                Logd(TAG, "getNextInQueue nextItem has no local file ${nextItem.title}")
                displayStreamingNotAllowedNotification(PlaybackServiceStarter(this@PlaybackService, nextItem.media!!).intent)
                writeNoMediaPlaying()
                return null
            }
            EventFlow.postEvent(FlowEvent.PlayEvent(item, FlowEvent.PlayEvent.Action.END))
            EventFlow.postEvent(FlowEvent.PlayEvent(nextItem))
            return if (nextItem.media == null) null else unmanaged(nextItem.media!!)
        }
        // only used in test
        override fun findMedia(url: String): Playable? {
            val item = getEpisodeByGuidOrUrl(null, url)
            return item?.media
        }
        override fun onPlaybackEnded(mediaType: MediaType?, stopPlaying: Boolean) {
            Logd(TAG, "onPlaybackEnded mediaType: $mediaType stopPlaying: $stopPlaying")
            clearCurTempSpeed()
            if (stopPlaying) taskManager.cancelPositionSaver()
            if (mediaType == null) sendNotificationBroadcast(NOTIFICATION_TYPE_PLAYBACK_END, 0)
            else sendNotificationBroadcast(NOTIFICATION_TYPE_RELOAD,
                when {
                    isCasting -> EXTRA_CODE_CAST
                    mediaType == MediaType.VIDEO -> EXTRA_CODE_VIDEO
                    else -> EXTRA_CODE_AUDIO
                })
        }
        override fun ensureMediaInfoLoaded(media: Playable) {
//            if (media is EpisodeMedia && media.item == null) media.item = DBReader.getFeedItem(media.itemId)
        }
        fun writeMediaPlaying(playable: Playable?, playerStatus: PlayerStatus) {
            Logd(InTheatre.TAG, "Writing playback preferences ${playable?.getIdentifier()}")
            if (playable == null) writeNoMediaPlaying()
            else {
                curState = upsertBlk(curState) {
                    it.curMediaType = playable.getPlayableType().toLong()
                    it.curIsVideo = playable.getMediaType() == MediaType.VIDEO
                    if (playable is EpisodeMedia) {
                        val feedId = playable.episodeOrFetch()?.feed?.id
                        if (feedId != null) it.curFeedId = feedId
                        it.curMediaId = playable.id
                    } else {
                        it.curFeedId = NO_MEDIA_PLAYING
                        it.curMediaId = NO_MEDIA_PLAYING
                    }
                    it.curPlayerStatus = getCurPlayerStatusAsInt(playerStatus)
                }
            }
        }
        fun writePlayerStatus(playerStatus: PlayerStatus) {
            Logd(InTheatre.TAG, "Writing player status playback preferences")
            curState = upsertBlk(curState) {
                it.curPlayerStatus = getCurPlayerStatusAsInt(playerStatus)
            }
        }
        private fun getCurPlayerStatusAsInt(playerStatus: PlayerStatus): Int {
            val playerStatusAsInt = when (playerStatus) {
                PlayerStatus.PLAYING -> PLAYER_STATUS_PLAYING
                PlayerStatus.PAUSED -> PLAYER_STATUS_PAUSED
                else -> PLAYER_STATUS_OTHER
            }
            return playerStatusAsInt
        }
    }

    private val mediaLibrarySessionCK = object: MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            Logd(TAG, "in MyMediaSessionCallback onConnect")
            when {
                session.isMediaNotificationController(controller) -> {
                    Logd(TAG, "MyMediaSessionCallback onConnect isMediaNotificationController")
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    notificationCustomButtons.forEach { commandButton ->
                        Logd(TAG, "MyMediaSessionCallback onConnect commandButton ${commandButton.displayName}")
                        commandButton.sessionCommand?.let(sessionCommands::add)
                    }
                    return MediaSession.ConnectionResult.accept(sessionCommands.build(), playerCommands.build())
                }
                session.isAutoCompanionController(controller) -> {
                    Logd(TAG, "MyMediaSessionCallback onConnect isAutoCompanionController")
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    notificationCustomButtons.forEach { commandButton ->
                        Logd(TAG, "MyMediaSessionCallback onConnect commandButton ${commandButton.displayName}")
                        commandButton.sessionCommand?.let(sessionCommands::add)
                    }
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands.build())
                        .build()
                }
                else -> {
                    Logd(TAG, "MyMediaSessionCallback onConnect other controller: $controller")
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
                }
            }
        }
        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            super.onPostConnect(session, controller)
            Logd(TAG, "MyMediaSessionCallback onPostConnect")
            if (notificationCustomButtons.isNotEmpty()) {
                mediaSession?.setCustomLayout(notificationCustomButtons)
//                mediaSession?.setCustomLayout(customMediaNotificationProvider.notificationMediaButtons)
            }
        }
        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            Log.d(TAG, "MyMediaSessionCallback onCustomCommand ${customCommand.customAction}")
            /* Handling custom command buttons from player notification. */
            when (customCommand.customAction) {
                NotificationCustomButton.REWIND.customAction -> mPlayer?.seekDelta(-rewindSecs * 1000)
                NotificationCustomButton.FORWARD.customAction -> mPlayer?.seekDelta(fastForwardSecs * 1000)
                NotificationCustomButton.SKIP.customAction -> mPlayer?.skip()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        override fun onPlaybackResumption(mediaSession: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            Logd(TAG, "MyMediaSessionCallback onPlaybackResumption ")
            val settable = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
//            scope.launch {
//                // Your app is responsible for storing the playlist and the start position
//                // to use here
//                val resumptionPlaylist = restorePlaylist()
//                settable.set(resumptionPlaylist)
//            }
            return settable
        }
        override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
            Logd(TAG, "in MyMediaSessionCallback onDisconnected")
            when {
                session.isMediaNotificationController(controller) -> {
                    Logd(TAG, "MyMediaSessionCallback onDisconnected isMediaNotificationController")
                }
                session.isAutoCompanionController(controller) -> {
                    Logd(TAG, "MyMediaSessionCallback onDisconnected isAutoCompanionController")
                }
            }
        }
        override fun onMediaButtonEvent(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, intent: Intent): Boolean {
            val keyEvent = if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) intent.extras!!.getParcelable(EXTRA_KEY_EVENT, KeyEvent::class.java)
            else intent.extras!!.getParcelable(EXTRA_KEY_EVENT) as? KeyEvent
            Logd(TAG, "onMediaButtonEvent ${keyEvent?.keyCode}")

            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) {
                val keyCode = keyEvent.keyCode
                if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                    clickCount++
                    clickHandler.removeCallbacksAndMessages(null)
                    clickHandler.postDelayed({
                        when (clickCount) {
                            1 -> handleKeycode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, false)
                            2 -> mPlayer?.seekDelta(fastForwardSecs * 1000)
                            3 -> mPlayer?.seekDelta(-rewindSecs * 1000)
                        }
                        clickCount = 0
                    }, ViewConfiguration.getDoubleTapTimeout().toLong())
                    return true
                } else return handleKeycode(keyCode, false)
            }
            return false
        }
        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
            Logd(TAG, "MyMediaSessionCallback onGetItem called mediaId:$mediaId")
            return super.onGetItem(session, browser, mediaId)
        }
        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
            Logd(TAG, "MyMediaSessionCallback onGetLibraryRoot called")
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }
        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int,
                                   params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            Logd(TAG, "MyMediaSessionCallback onGetChildren called parentId:$parentId page:$page pageSize:$pageSize")
//            return super.onGetChildren(session, browser, parentId, page, pageSize, params)
            return Futures.immediateFuture(LibraryResult.ofItemList(mediaItemsInQueue, params))
        }
        override fun onSubscribe(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
                                 params: LibraryParams?): ListenableFuture<LibraryResult<Void>> {
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }
        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> {
            Logd(TAG, "MyMediaSessionCallback onAddMediaItems called ${mediaItems.size} ${mediaItems[0]}")
            /* This is the trickiest part, if you don't do this here, nothing will play */
            val episode = getEpisodeByGuidOrUrl(null, mediaItems.first().mediaId) ?: return Futures.immediateFuture(mutableListOf())
            val media = episode.media ?: return Futures.immediateFuture(mutableListOf())
            if (!InTheatre.isCurMedia(media)) {
                PlaybackServiceStarter(applicationContext, media).callEvenIfRunning(true).start()
                EventFlow.postEvent(FlowEvent.PlayEvent(episode))
            }
            val updatedMediaItems = mediaItems.map { it.buildUpon().setUri(it.mediaId).build() }.toMutableList()
//            updatedMediaItems += mediaItemsInQueue
//            Logd(TAG, "MyMediaSessionCallback onAddMediaItems updatedMediaItems: ${updatedMediaItems.size} ")
            return Futures.immediateFuture(updatedMediaItems)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Logd(TAG, "Service created.")
        isRunning = true
        playbackService = this

        if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            registerReceiver(autoStateUpdated, IntentFilter("com.google.android.gms.car.media.STATUS"), RECEIVER_NOT_EXPORTED)
            registerReceiver(shutdownReceiver, IntentFilter(ACTION_SHUTDOWN_PLAYBACK_SERVICE), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(autoStateUpdated, IntentFilter("com.google.android.gms.car.media.STATUS"))
            registerReceiver(shutdownReceiver, IntentFilter(ACTION_SHUTDOWN_PLAYBACK_SERVICE))
        }

        registerReceiver(headsetDisconnected, IntentFilter(Intent.ACTION_HEADSET_PLUG))
        registerReceiver(bluetoothStateUpdated, IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED))
        registerReceiver(audioBecomingNoisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        procFlowEvents()
        taskManager = TaskManager(this, taskManagerCallback)

        recreateMediaSessionIfNeeded()

        castStateListener = object : CastStateListener(this) {
            override fun onSessionStartedOrEnded() {
                recreateMediaPlayer()
            }
        }
        EventFlow.postEvent(FlowEvent.PlaybackServiceEvent(FlowEvent.PlaybackServiceEvent.Action.SERVICE_STARTED))
    }

    fun recreateMediaSessionIfNeeded() {
        if (mediaSession != null) return
        Logd(TAG, "recreateMediaSessionIfNeeded")
        customMediaNotificationProvider = CustomMediaNotificationProvider(applicationContext)
        setMediaNotificationProvider(customMediaNotificationProvider)

        recreateMediaPlayer()
        if (LocalMediaPlayer.exoPlayer == null) LocalMediaPlayer.createStaticPlayer(applicationContext)
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, if (Build.VERSION.SDK_INT >= 23) FLAG_IMMUTABLE else FLAG_UPDATE_CURRENT)
        mediaSession = MediaLibrarySession.Builder(applicationContext, LocalMediaPlayer.exoPlayer!!, mediaLibrarySessionCK)
            .setId(packageName)
            .setSessionActivity(pendingIntent)
            .setCustomLayout(notificationCustomButtons)
            .build()
    }

    fun recreateMediaPlayer() {
        Logd(TAG, "recreateMediaPlayer")
        val media = curMedia
        var wasPlaying = false
        if (mPlayer != null) {
            wasPlaying = MediaPlayerBase.status == PlayerStatus.PLAYING || MediaPlayerBase.status == PlayerStatus.FALLBACK
            mPlayer!!.pause(abandonFocus = true, reinit = false)
            mPlayer!!.shutdown()
        }
        mPlayer = CastPsmp.getInstanceIfConnected(this, mediaPlayerCallback)
        if (mPlayer == null) mPlayer = LocalMediaPlayer(applicationContext, mediaPlayerCallback) // Cast not supported or not connected

        Logd(TAG, "recreateMediaPlayer wasPlaying: $wasPlaying")
        if (media != null) mPlayer!!.playMediaObject(media, !media.localFileAvailable(), wasPlaying, true)
        isCasting = mPlayer!!.isCasting()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Logd(TAG, "onTaskRemoved")
        val player = mediaSession?.player
        if (player != null) {
            if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == STATE_ENDED) {
                // Stop the service if not playing, continue playing in the background
                // otherwise.
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        Logd(TAG, "Service is about to be destroyed")

        playbackService = null
        isRunning = false
        currentMediaType = MediaType.UNKNOWN
        castStateListener.destroy()

        currentitem = null

        LocalMediaPlayer.cleanup()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        LocalMediaPlayer.exoPlayer =  null
        mPlayer?.shutdown()

        cancelFlowEvents()
        unregisterReceiver(autoStateUpdated)
        unregisterReceiver(headsetDisconnected)
        unregisterReceiver(shutdownReceiver)
        unregisterReceiver(bluetoothStateUpdated)
        unregisterReceiver(audioBecomingNoisy)
        taskManager.shutdown()

        super.onDestroy()
    }

    fun isServiceReady(): Boolean {
        return mediaSession?.player?.playbackState != STATE_IDLE && mediaSession?.player?.playbackState != STATE_ENDED
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

//    override fun onBind(intent: Intent?): IBinder? {
//        Logd(TAG, "Received onBind event")
//        return if (intent?.action != null && intent.action == SERVICE_INTERFACE) super.onBind(intent) else mBinder
//    }

//    override fun onUnbind(intent: Intent): Boolean {
//        Logd(TAG, "Received onUnbind event")
//        return super.onUnbind(intent)
//    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val keycode = intent?.getIntExtra(MediaButtonReceiver.EXTRA_KEYCODE, -1) ?: -1
        val customAction = intent?.getStringExtra(MediaButtonReceiver.EXTRA_CUSTOM_ACTION)
        val hardwareButton = intent?.getBooleanExtra(MediaButtonReceiver.EXTRA_HARDWAREBUTTON, false) ?: false
        val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU)
            intent?.getParcelableExtra(EXTRA_KEY_EVENT, KeyEvent::class.java)
        else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_KEY_EVENT)
        }
        val playable = curMedia

        Log.d(TAG, "onStartCommand flags=$flags startId=$startId keycode=$keycode keyEvent=$keyEvent customAction=$customAction hardwareButton=$hardwareButton action=${intent?.action.toString()} ${playable?.getEpisodeTitle()}")
        if (keycode == -1 && playable == null && customAction == null) {
            Log.e(TAG, "onStartCommand PlaybackService was started with no arguments, return")
            return START_NOT_STICKY
        }
        if ((flags and START_FLAG_REDELIVERY) != 0) {
            Logd(TAG, "onStartCommand is a redelivered intent, calling stopForeground now. return")
            return START_NOT_STICKY
        }
        if (keycode == -1 && playable != null && status == PlayerStatus.PLAYING && mPlayer?.prevMedia?.getIdentifier() == playable.getIdentifier()) {
//          pause button on notification also calls onStartCommand
            Logd(TAG, "onStartCommand playing same media: $status, return")
            return super.onStartCommand(intent, flags, startId)
        }

        when {
            keycode != -1 -> {
                Logd(TAG, "onStartCommand Received hardware button event: $hardwareButton")
                handleKeycode(keycode, !hardwareButton)
                return super.onStartCommand(intent, flags, startId)
            }
            keyEvent?.keyCode == KEYCODE_MEDIA_STOP -> {
                Logd(TAG, "onStartCommand Received button event: ${keyEvent.keyCode}")
                handleKeycode(keyEvent.keyCode, !hardwareButton)
                return super.onStartCommand(intent, flags, startId)
            }
            playable != null -> {
                recreateMediaSessionIfNeeded()
                Logd(TAG, "onStartCommand status: $status")
                val allowStreamThisTime = intent?.getBooleanExtra(EXTRA_ALLOW_STREAM_THIS_TIME, false) ?: false
                val allowStreamAlways = intent?.getBooleanExtra(EXTRA_ALLOW_STREAM_ALWAYS, false) ?: false
                sendNotificationBroadcast(NOTIFICATION_TYPE_RELOAD, 0)
                if (allowStreamAlways) isAllowMobileStreaming = true
                startPlaying(allowStreamThisTime)
//                    return super.onStartCommand(intent, flags, startId)
                return START_NOT_STICKY
            }
            else -> Logd(TAG, "onStartCommand case when not (keycode != -1 and playable != null)")
        }
//        return super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    private fun skipIntro(playable: Playable) {
        val item = (playable as? EpisodeMedia)?.episodeOrFetch() ?: currentitem ?: return
        val feed = item.feed
        val preferences = feed?.preferences

        val skipIntro = preferences?.introSkip ?: 0
        val skipIntroMS = skipIntro * 1000
        if (skipIntro > 0 && playable.getPosition() < skipIntroMS) {
            val duration = curDuration
            if (skipIntroMS < duration || duration <= 0) {
                Logd(TAG, "skipIntro " + playable.getEpisodeTitle())
                mPlayer?.seekTo(skipIntroMS)
                val skipIntroMesg = applicationContext.getString(R.string.pref_feed_skip_intro_toast, skipIntro)
                val toast = Toast.makeText(applicationContext, skipIntroMesg, Toast.LENGTH_LONG)
                toast.show()
            }
        }
    }

    @SuppressLint("LaunchActivityFromNotification")
    private fun displayStreamingNotAllowedNotification(originalIntent: Intent) {
//        TODO
//        if (EventBus.getDefault().hasSubscriberForEvent(FlowEvent.MessageEvent::class.java)) {
//            EventFlow.postEvent(FlowEvent.MessageEvent(getString(R.string.confirm_mobile_streaming_notification_message)))
//            return
//        }

        val intentAllowThisTime = Intent(originalIntent)
        intentAllowThisTime.setAction(EXTRA_ALLOW_STREAM_THIS_TIME)
        intentAllowThisTime.putExtra(EXTRA_ALLOW_STREAM_THIS_TIME, true)
        val pendingIntentAllowThisTime =
            if (Build.VERSION.SDK_INT >= VERSION_CODES.O) PendingIntent.getForegroundService(this, R.id.pending_intent_allow_stream_this_time,
                intentAllowThisTime, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
            else PendingIntent.getService(this, R.id.pending_intent_allow_stream_this_time, intentAllowThisTime,
                FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)

        val intentAlwaysAllow = Intent(intentAllowThisTime)
        intentAlwaysAllow.setAction(EXTRA_ALLOW_STREAM_ALWAYS)
        intentAlwaysAllow.putExtra(EXTRA_ALLOW_STREAM_ALWAYS, true)
        val pendingIntentAlwaysAllow =
            if (Build.VERSION.SDK_INT >= VERSION_CODES.O) PendingIntent.getForegroundService(this, R.id.pending_intent_allow_stream_always,
                intentAlwaysAllow, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
            else PendingIntent.getService(this, R.id.pending_intent_allow_stream_always, intentAlwaysAllow,
                FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, NotificationUtils.CHANNEL_ID.user_action.name)
            .setSmallIcon(R.drawable.ic_notification_stream)
            .setContentTitle(getString(R.string.confirm_mobile_streaming_notification_title))
            .setContentText(getString(R.string.confirm_mobile_streaming_notification_message))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.confirm_mobile_streaming_notification_message)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntentAllowThisTime)
            .addAction(R.drawable.ic_notification_stream, getString(R.string.confirm_mobile_streaming_button_once), pendingIntentAllowThisTime)
            .addAction(R.drawable.ic_notification_stream, getString(R.string.confirm_mobile_streaming_button_always), pendingIntentAlwaysAllow)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(5566, builder.build())
    }

    /**
     * Handles media button events. return: keycode was handled
     */
    private fun handleKeycode(keycode: Int, notificationButton: Boolean): Boolean {
        Log.d(TAG, "Handling keycode: $keycode")
        val info = mPlayer?.playerInfo
        val status = info?.playerStatus
        when (keycode) {
            KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                when {
                    status == PlayerStatus.PLAYING -> mPlayer?.pause(!isPersistNotify, false)
                    status == PlayerStatus.FALLBACK || status == PlayerStatus.PAUSED || status == PlayerStatus.PREPARED -> mPlayer?.resume()
                    status == PlayerStatus.PREPARING -> mPlayer?.startWhenPrepared?.set(!mPlayer!!.startWhenPrepared.get())
                    status == PlayerStatus.INITIALIZED -> {
                        mPlayer?.startWhenPrepared?.set(true)
                        mPlayer?.prepare()
                    }
                    curMedia == null -> startPlayingFromPreferences()
                    else -> return false
                }
                taskManager.restartSleepTimer()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                when {
                    status == PlayerStatus.PAUSED || status == PlayerStatus.PREPARED -> mPlayer?.resume()
                    status == PlayerStatus.INITIALIZED -> {
                        mPlayer?.startWhenPrepared?.set(true)
                        mPlayer?.prepare()
                    }
                    curMedia == null -> startPlayingFromPreferences()
                    else -> return false
                }
                taskManager.restartSleepTimer()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (status == PlayerStatus.PLAYING) {
                    mPlayer?.pause(!isPersistNotify, false)
                    return true
                }
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                when {
                    // Handle remapped button as notification button which is not remapped again.
                    !notificationButton -> return handleKeycode(hardwareForwardButton, true)
                    status == PlayerStatus.PLAYING || status == PlayerStatus.PAUSED -> {
                        mPlayer?.skip()
                        return true
                    }
                }
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (status == PlayerStatus.FALLBACK || status == PlayerStatus.PLAYING || status == PlayerStatus.PAUSED) {
                    mPlayer?.seekDelta(fastForwardSecs * 1000)
                    return true
                }
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                when {
                    // Handle remapped button as notification button which is not remapped again.
                    !notificationButton -> return handleKeycode(hardwarePreviousButton, true)
                    status == PlayerStatus.FALLBACK || status == PlayerStatus.PLAYING || status == PlayerStatus.PAUSED -> {
                        mPlayer?.seekTo(0)
                        return true
                    }
                }
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (status == PlayerStatus.FALLBACK || status == PlayerStatus.PLAYING || status == PlayerStatus.PAUSED) {
                    mPlayer?.seekDelta(-rewindSecs * 1000)
                    return true
                }
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                if (status == PlayerStatus.FALLBACK || status == PlayerStatus.PLAYING) mPlayer?.pause(abandonFocus = true, reinit = true)
                return true
            }
            else -> {
                Logd(TAG, "Unhandled key code: $keycode")
                if (info?.playable != null && info.playerStatus == PlayerStatus.PLAYING) {
                    // only notify the user about an unknown key event if it is actually doing something
                    val message = String.format(resources.getString(R.string.unknown_media_key), keycode)
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        return false
    }

    private fun startPlayingFromPreferences() {
        recreateMediaSessionIfNeeded()
        scope.launch {
            try {
                withContext(Dispatchers.IO) { loadPlayableFromPreferences() }
                withContext(Dispatchers.Main) { startPlaying(false) }
            } catch (e: Throwable) {
                Logd(TAG, "Playable was not loaded from preferences. Stopping service.")
                e.printStackTrace()
            }
        }
    }

    private fun startPlaying(allowStreamThisTime: Boolean) {
        Logd(TAG, "startPlaying called allowStreamThisTime: $allowStreamThisTime")
        val media = curMedia ?: return

        val localFeed = URLUtil.isContentUrl(media.getStreamUrl())
        val streaming = !media.localFileAvailable() || localFeed
        if (streaming && !localFeed && !isStreamingAllowed && !allowStreamThisTime) {
            displayStreamingNotAllowedNotification(PlaybackServiceStarter(this, media).intent)
            writeNoMediaPlaying()
            return
        }

//        TODO: this is redundant
//        if (media.getIdentifier() != curState.curMediaId) clearCurTempSpeed()

        mPlayer?.playMediaObject(media, streaming, startWhenPrepared = true, true)
//        recreateMediaSessionIfNeeded()
//        val episode = (media as? EpisodeMedia)?.episode
//        if (curMedia is EpisodeMedia && episode != null) addToQueue(true, episode)
    }

    fun clearCurTempSpeed() {
        curState = upsertBlk(curState) { it.curTempSpeed = FeedPreferences.SPEED_USE_GLOBAL }
    }

    private var eventSink: Job?     = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    private fun procFlowEvents() {
        if (eventSink != null) return
        eventSink = scope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.QueueEvent -> onQueueEvent(event)
                    is FlowEvent.PlayerErrorEvent -> onPlayerError(event)
                    is FlowEvent.BufferUpdateEvent -> onBufferUpdate(event)
                    is FlowEvent.SleepTimerUpdatedEvent -> onSleepTimerUpdate(event)
//                    is FlowEvent.VolumeAdaptionChangedEvent -> onVolumeAdaptionChanged(event)
                    is FlowEvent.FeedPrefsChangeEvent -> onFeedPrefsChanged(event)
//                    is FlowEvent.SkipIntroEndingChangedEvent -> skipIntroEndingPresetChanged(event)
                    is FlowEvent.PlayEvent -> currentitem = event.episode
                    is FlowEvent.EpisodeMediaEvent -> onEpisodeMediaEvent(event)
                    else -> {}
                }
            }
        }
    }

    private fun onEpisodeMediaEvent(event: FlowEvent.EpisodeMediaEvent) {
        if (event.action == FlowEvent.EpisodeMediaEvent.Action.REMOVED) {
            for (e in event.episodes) {
                if (e.id == curEpisode?.id) {
                    curEpisode = unmanaged(e)
                    curMedia = curEpisode!!.media
                    mPlayer?.endPlayback(hasEnded = false, wasSkipped = true, shouldContinue = true, toStoppedState = true)
                    break
                }
            }
        }
    }

    private fun onQueueEvent(event: FlowEvent.QueueEvent) {
        if (event.action == FlowEvent.QueueEvent.Action.REMOVED) {
            Logd(TAG, "onQueueEvent: ending playback curEpisode ${curEpisode?.title}")
            notifyCurQueueItemsChanged()
            for (e in event.episodes) {
                Logd(TAG, "onQueueEvent: ending playback event ${e.title}")
                if (e.id == curEpisode?.id) {
                    mPlayer?.endPlayback(hasEnded = false, wasSkipped = true, shouldContinue = true, toStoppedState = true)
                    break
                }
            }
        }
    }

    fun notifyCurQueueItemsChanged(range_: Int = -1) {
        val range = if (range_ > 0) range_ else curQueue.size()
        Logd(TAG, "notifyCurQueueItemsChanged curQueue: ${curQueue.id}")
        mediaSession?.notifyChildrenChanged("CurQueue", range, null)
    }

    //    private fun onVolumeAdaptionChanged(event: FlowEvent.VolumeAdaptionChangedEvent) {
//        if (mPlayer != null) updateVolumeIfNecessary(mPlayer!!, event.feedId, event.volumeAdaptionSetting)
//    }

    private fun onFeedPrefsChanged(event: FlowEvent.FeedPrefsChangeEvent) {
        val item = (curMedia as? EpisodeMedia)?.episodeOrFetch() ?: currentitem
        if (item?.feed?.id == event.feed.id) {
            item.feed = null
//            seems no need to pause??
//            if (MediaPlayerBase.status == PlayerStatus.PLAYING) {
//                mPlayer?.pause(abandonFocus = false, reinit = false)
//                mPlayer?.resume()
//            }
        }
    }

    private fun onPlayerError(event: FlowEvent.PlayerErrorEvent) {
        if (MediaPlayerBase.status == PlayerStatus.PLAYING || MediaPlayerBase.status == PlayerStatus.FALLBACK)
            mPlayer!!.pause(abandonFocus = true, reinit = false)
    }

    private fun onBufferUpdate(event: FlowEvent.BufferUpdateEvent) {
        if (event.hasEnded()) {
            if (curMedia is EpisodeMedia && curMedia!!.getDuration() <= 0 && (mPlayer?.getDuration()?:0) > 0) {
                // Playable is being streamed and does not have a duration specified in the feed
                curMedia!!.setDuration(mPlayer!!.getDuration())
//                DBWriter.persistEpisodeMedia(playable as EpisodeMedia)
            }
        }
    }

    private fun onSleepTimerUpdate(event: FlowEvent.SleepTimerUpdatedEvent) {
        when {
            event.isOver -> {
                mPlayer?.pause(abandonFocus = true, reinit = true)
                mPlayer?.setVolume(1.0f, 1.0f)
            }
            event.getTimeLeft() < TaskManager.NOTIFICATION_THRESHOLD -> {
                val multiplicators = floatArrayOf(0.1f, 0.2f, 0.3f, 0.3f, 0.3f, 0.4f, 0.4f, 0.4f, 0.6f, 0.8f)
                val multiplicator = multiplicators[max(0.0, (event.getTimeLeft().toInt() / 1000).toDouble()).toInt()]
                Logd(TAG, "onSleepTimerAlmostExpired: $multiplicator")
                mPlayer?.setVolume(multiplicator, multiplicator)
            }
            event.isCancelled -> mPlayer?.setVolume(1.0f, 1.0f)
        }
    }

    private fun sendNotificationBroadcast(type: Int, code: Int) {
       Logd(TAG, "sendNotificationBroadcast $type $code")
        val intent = Intent(ACTION_PLAYER_NOTIFICATION)
        intent.putExtra(EXTRA_NOTIFICATION_TYPE, type)
        intent.putExtra(EXTRA_NOTIFICATION_CODE, code)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun skipEndingIfNecessary() {
        val remainingTime = curDuration - curPosition
        val item = (curMedia as? EpisodeMedia)?.episodeOrFetch() ?: currentitem ?: return

        val skipEnd = item.feed?.preferences?.endingSkip?:0
        val skipEndMS = skipEnd * 1000
//        Log.d(TAG, "skipEndingIfNecessary: checking " + remainingTime + " " + skipEndMS + " speed " + currentPlaybackSpeed)
        if (skipEnd > 0 && skipEndMS < curDuration && (remainingTime - skipEndMS < 0)) {
            Logd(TAG, "skipEndingIfNecessary: Skipping the remaining $remainingTime $skipEndMS speed $curSpeed")
            val context = applicationContext
            val skipMesg = context.getString(R.string.pref_feed_skip_ending_toast, skipEnd)
            val toast = Toast.makeText(context, skipMesg, Toast.LENGTH_LONG)
            toast.show()
            autoSkippedFeedMediaId = item.identifyingValue
            mPlayer?.skip()
        }
    }

    @Synchronized
    private fun saveCurrentPosition(fromMediaPlayer: Boolean, playable: Playable?, position: Int) {
        var playable = playable
        var position = position
        val duration_: Int
        if (fromMediaPlayer) {
            position = curPosition
            duration_ = this.curDuration
            playable = curMedia
        } else duration_ = playable?.getDuration() ?: Playable.INVALID_TIME

        if (position != Playable.INVALID_TIME && duration_ != Playable.INVALID_TIME && playable != null) {
//            Log.d(TAG, "Saving current position to $position $duration")
            playable.setPosition(position)
            playable.setLastPlayedTime(System.currentTimeMillis())

            if (playable is EpisodeMedia) {
                var item = realm.query(Episode::class, "id == ${playable.id}").first().find()
                if (item != null) {
                    item = upsertBlk(item) {
                        val media = it.media
                        if (media != null) {
                            media.setPosition(position)
                            media.setLastPlayedTime(System.currentTimeMillis())
                            if (it.isNew) it.playState = Episode.PlayState.UNPLAYED.code
                            if (media.startPosition >= 0 && media.getPosition() > media.startPosition)
                                media.playedDuration = (media.playedDurationWhenStarted + media.getPosition() - media.startPosition)
                        }
                    }
                    EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item))
                }
            }
            prevPosition = position
        }
    }

    private fun bluetoothNotifyChange(info: MediaPlayerInfo?, whatChanged: String) {
        Logd(TAG, "bluetoothNotifyChange $whatChanged")
        var isPlaying = false
        if (info?.playerStatus == PlayerStatus.PLAYING || info?.playerStatus == PlayerStatus.FALLBACK) isPlaying = true
        if (info?.playable != null) {
            val i = Intent(whatChanged)
            i.putExtra("id", 1L)
            i.putExtra("artist", "")
            i.putExtra("album", info.playable!!.getFeedTitle())
            i.putExtra("track", info.playable!!.getEpisodeTitle())
            i.putExtra("playing", isPlaying)
            i.putExtra("duration", info.playable!!.getDuration().toLong())
            i.putExtra("position", info.playable!!.getPosition().toLong())
            sendBroadcast(i)
        }
    }

    private fun pauseIfPauseOnDisconnect() {
        Logd(TAG, "pauseIfPauseOnDisconnect()")
        transientPause = (MediaPlayerBase.status == PlayerStatus.PLAYING || MediaPlayerBase.status == PlayerStatus.FALLBACK)
        if (isPauseOnHeadsetDisconnect && !isCasting) mPlayer?.pause(!isPersistNotify, false)
    }

    /**
     * @param bluetooth true if the event for unpausing came from bluetooth
     */
    private fun unpauseIfPauseOnDisconnect(bluetooth: Boolean) {
        if (mPlayer != null && mPlayer!!.isAudioChannelInUse) {
            Logd(TAG, "unpauseIfPauseOnDisconnect() audio is in use")
            return
        }
        if (transientPause) {
            transientPause = false
            if (Build.VERSION.SDK_INT >= 31) return
            when {
                !bluetooth && isUnpauseOnHeadsetReconnect -> mPlayer?.resume()
                bluetooth && isUnpauseOnBluetoothReconnect -> {
                    // let the user know we've started playback again...
                    val v = applicationContext.getSystemService(VIBRATOR_SERVICE) as? Vibrator
                    v?.vibrate(500)
                    mPlayer?.resume()
                }
            }
        }
    }

//    inner class LocalBinder : Binder() {
//        val service: PlaybackService
//            get() = this@PlaybackService
//    }

    enum class NotificationCustomButton(val customAction: String, val commandButton: CommandButton) {
        SKIP(
            customAction = CUSTOM_COMMAND_SKIP_ACTION_ID,
            commandButton = CommandButton.Builder()
                .setDisplayName("Skip")
                .setSessionCommand(SessionCommand(CUSTOM_COMMAND_SKIP_ACTION_ID, Bundle()))
                .setIconResId(R.drawable.ic_notification_skip)
                .build(),
        ),
        REWIND(
            customAction = CUSTOM_COMMAND_REWIND_ACTION_ID,
            commandButton = CommandButton.Builder()
                .setDisplayName("Rewind")
                .setSessionCommand(SessionCommand(CUSTOM_COMMAND_REWIND_ACTION_ID, Bundle()))
                .setIconResId(R.drawable.ic_notification_fast_rewind)
                .build(),
        ),
        FORWARD(
            customAction = CUSTOM_COMMAND_FORWARD_ACTION_ID,
            commandButton = CommandButton.Builder()
                .setDisplayName("Forward")
                .setSessionCommand(SessionCommand(CUSTOM_COMMAND_FORWARD_ACTION_ID, Bundle()))
                .setIconResId(R.drawable.ic_notification_fast_forward)
                .build(),
        ),
    }

    @UnstableApi
    class CustomMediaNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {
        override fun addNotificationActions(mediaSession: MediaSession, mediaButtons: ImmutableList<CommandButton>,
                                            builder: NotificationCompat.Builder, actionFactory: MediaNotification.ActionFactory): IntArray {
            /* Retrieving notification default play/pause button from mediaButtons list. */
            val defaultPlayPauseButton = mediaButtons.getOrNull(1)
            val defaultRestartButton = mediaButtons.getOrNull(0)
            val notificationMediaButtons = if (defaultPlayPauseButton != null) {
                /* Overriding received mediaButtons list to ensure required buttons order: [rewind15, play/pause, forward15]. */
                ImmutableList.builder<CommandButton>().apply {
                    if (defaultRestartButton != null) add(defaultRestartButton)
                    add(NotificationCustomButton.REWIND.commandButton)
                    add(defaultPlayPauseButton)
                    add(NotificationCustomButton.FORWARD.commandButton)
                    add(NotificationCustomButton.SKIP.commandButton)
                }.build()
                /* Fallback option to handle nullability, in case retrieving default play/pause button fails for some reason (should never happen). */
            } else mediaButtons
            return super.addNotificationActions(mediaSession, notificationMediaButtons, builder, actionFactory)
        }
        override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence {
            return metadata.title ?: "No title"
        }
        override fun getNotificationContentText(metadata: MediaMetadata): CharSequence {
            return metadata.subtitle ?: "No text"
        }
    }

    companion object {
        private val TAG: String = PlaybackService::class.simpleName ?: "Anonymous"

        private const val CUSTOM_COMMAND_REWIND_ACTION_ID = "1_REWIND"
        private const val CUSTOM_COMMAND_FORWARD_ACTION_ID = "2_FAST_FWD"
        private const val CUSTOM_COMMAND_SKIP_ACTION_ID = "3_SKIP"

        const val EXTRA_ALLOW_STREAM_THIS_TIME: String = "extra.ac.mdiq.podcini.service.allowStream"
        const val EXTRA_ALLOW_STREAM_ALWAYS: String = "extra.ac.mdiq.podcini.service.allowStreamAlways"

        const val ACTION_PLAYER_NOTIFICATION: String = "action.ac.mdiq.podcini.service.playerNotification"
        const val EXTRA_NOTIFICATION_CODE: String = "extra.ac.mdiq.podcini.service.notificationCode"
        const val EXTRA_NOTIFICATION_TYPE: String = "extra.ac.mdiq.podcini.service.notificationType"
        const val NOTIFICATION_TYPE_PLAYBACK_END: Int = 7
        const val NOTIFICATION_TYPE_RELOAD: Int = 3

        const val ACTION_PLAYER_STATUS_CHANGED: String = "action.ac.mdiq.podcini.service.playerStatusChanged"
        private const val AVRCP_ACTION_PLAYER_STATUS_CHANGED = "com.android.music.playstatechanged"
        private const val AVRCP_ACTION_META_CHANGED = "com.android.music.metachanged"

        const val ACTION_SHUTDOWN_PLAYBACK_SERVICE: String = "action.ac.mdiq.podcini.service.actionShutdownPlaybackService"

        private const val EXTRA_CODE_AUDIO: Int = 1 // Used in NOTIFICATION_TYPE_RELOAD
        private const val EXTRA_CODE_VIDEO: Int = 2
        private const val EXTRA_CODE_CAST: Int = 3

        var playbackService: PlaybackService? = null
        var mediaBrowser: MediaBrowser? = null

        @JvmField
        var isRunning: Boolean = false

        /**
         * Is true if the service was running, but paused due to headphone disconnect
         */
        private var transientPause = false

        @JvmStatic
        @Volatile
        var isCasting: Boolean = false
            private set

        @Volatile
        var currentMediaType: MediaType? = MediaType.UNKNOWN
            private set

        /**
         * @return `true` if notifications are persistent, `false`  otherwise
         */
        val isPersistNotify: Boolean
            get() = appPrefs.getBoolean(UserPreferences.Prefs.prefPersistNotify.name, true)

        val isPauseOnHeadsetDisconnect: Boolean
            get() = appPrefs.getBoolean(UserPreferences.Prefs.prefPauseOnHeadsetDisconnect.name, true)

        val isUnpauseOnHeadsetReconnect: Boolean
            get() = appPrefs.getBoolean(UserPreferences.Prefs.prefUnpauseOnHeadsetReconnect.name, true)

        val isUnpauseOnBluetoothReconnect: Boolean
            get() = appPrefs.getBoolean(UserPreferences.Prefs.prefUnpauseOnBluetoothReconnect.name, false)

        val hardwareForwardButton: Int
            get() = appPrefs.getString(UserPreferences.Prefs.prefHardwareForwardButton.name, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD.toString())!!.toInt()

        val hardwarePreviousButton: Int
            get() = appPrefs.getString(UserPreferences.Prefs.prefHardwarePreviousButton.name, KeyEvent.KEYCODE_MEDIA_REWIND.toString())!!.toInt()

        /**
         * Set to true to enable Continuous Playback
         */
        @set:VisibleForTesting
        var isFollowQueue: Boolean
            get() = appPrefs.getBoolean(UserPreferences.Prefs.prefFollowQueue.name, true)
            set(value) {
                appPrefs.edit().putBoolean(UserPreferences.Prefs.prefFollowQueue.name, value).apply()
            }

        val curPositionFB: Int
            get() = playbackService?.curPosition ?: curMedia?.getPosition() ?: Playable.INVALID_TIME

        val curDurationFB: Int
            get() = playbackService?.curDuration ?: curMedia?.getDuration() ?: Playable.INVALID_TIME

        val curSpeedFB: Float
            get() = playbackService?.curSpeed ?: getCurrentPlaybackSpeed(curMedia)

        val isPlayingVideoLocally: Boolean
            get() = when {
                isCasting -> false
                playbackService != null -> currentMediaType == MediaType.VIDEO
                else -> curMedia?.getMediaType() == MediaType.VIDEO
            }

        var isStartWhenPrepared: Boolean
            get() = playbackService?.mPlayer?.startWhenPrepared?.get() ?: false
            set(s) {
                playbackService?.mPlayer?.startWhenPrepared?.set(s)
            }

        val mPlayerInfo: MediaPlayerInfo?
            get() = playbackService?.mPlayer?.playerInfo

        fun seekTo(time: Int) {
            playbackService?.mPlayer?.seekTo(time)
        }

        fun toggleFallbackSpeed(speed: Float) {
            if (playbackService != null) {
                when (MediaPlayerBase.status) {
                    PlayerStatus.PLAYING -> {
                        MediaPlayerBase.status = PlayerStatus.FALLBACK
                        setToFallbackSpeed(speed)
                    }
                    PlayerStatus.FALLBACK -> {
                        MediaPlayerBase.status = PlayerStatus.PLAYING
                        setToFallbackSpeed(speed)
                    }
                    else -> {}
                }
            }
        }

        private fun setToFallbackSpeed(speed: Float) {
            if (playbackService?.mPlayer == null || playbackService!!.isSpeedForward) return

            if (!playbackService!!.isFallbackSpeed) {
                playbackService!!.normalSpeed = playbackService!!.mPlayer!!.getPlaybackSpeed()
                playbackService!!.mPlayer!!.setPlaybackParams(speed, isSkipSilence)
            } else playbackService!!.mPlayer!!.setPlaybackParams(playbackService!!.normalSpeed, isSkipSilence)

            playbackService!!.isFallbackSpeed = !playbackService!!.isFallbackSpeed
        }

        fun isSleepTimerActive(): Boolean {
            return playbackService?.taskManager?.isSleepTimerActive ?: false
        }

        fun playPause() {
            when (MediaPlayerBase.status) {
                PlayerStatus.FALLBACK -> toggleFallbackSpeed(1.0f)
                PlayerStatus.PLAYING -> {
                    playbackService?.mPlayer?.pause(true, reinit = false)
                    playbackService?.isSpeedForward =  false
                    playbackService?.isFallbackSpeed = false
                }
                PlayerStatus.PAUSED, PlayerStatus.PREPARED -> {
                    playbackService?.mPlayer?.resume()
                    playbackService?.taskManager?.restartSleepTimer()
                }
                PlayerStatus.PREPARING -> isStartWhenPrepared = !isStartWhenPrepared
                PlayerStatus.INITIALIZED -> {
                    if (playbackService != null) isStartWhenPrepared = true
                    playbackService?.mPlayer?.prepare()
                    playbackService?.taskManager?.restartSleepTimer()
                }
                else -> Log.w(TAG, "Play/Pause button was pressed and PlaybackService state was unknown")
            }
        }

        fun updateVolumeIfNecessary(mediaPlayer: MediaPlayerBase, feedId: Long, volumeAdaptionSetting: VolumeAdaptionSetting) {
            val playable = curMedia
            if (playable is EpisodeMedia) {
                val item_ = playable.episodeOrFetch()
                if (item_?.feed?.id == feedId) {
                    item_.feed!!.preferences?.volumeAdaptionSetting = volumeAdaptionSetting
                    if (MediaPlayerBase.status == PlayerStatus.PLAYING) {
                        mediaPlayer.pause(abandonFocus = false, reinit = false)
                        mediaPlayer.resume()
                    }
                }
            }
        }

        /**
         * Returns an intent which starts an audio- or videoplayer, depending on the
         * type of media that is being played or the medaitype that is provided as an argument.
         * If the playbackservice is not running, the type of the last played media will be looked up.
         */
        @JvmStatic
        fun getPlayerActivityIntent(context: Context, mediaType_: MediaType? = null): Intent {
            val mediaType = mediaType_ ?: currentMediaType
            val showVideoPlayer = if (isRunning) mediaType == MediaType.VIDEO && !isCasting else curState.curIsVideo
            return if (showVideoPlayer) VideoPlayerActivityStarter(context).intent
            else MainActivityStarter(context).withOpenPlayer().getIntent()
        }
    }
}
