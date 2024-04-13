package ac.mdiq.podcini.playback.service

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.playback.PlayableUtils.saveCurrentPosition
import ac.mdiq.podcini.playback.PlaybackServiceStarter
import ac.mdiq.podcini.playback.base.PlaybackServiceMediaPlayer
import ac.mdiq.podcini.playback.base.PlaybackServiceMediaPlayer.PSMPCallback
import ac.mdiq.podcini.playback.base.PlaybackServiceMediaPlayer.PSMPInfo
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.cast.CastPsmp
import ac.mdiq.podcini.playback.cast.CastStateListener
import ac.mdiq.podcini.playback.service.PlaybackServiceTaskManager.PSTMCallback
import ac.mdiq.podcini.preferences.PlaybackPreferences
import ac.mdiq.podcini.preferences.PlaybackPreferences.Companion.clearCurrentlyPlayingTemporaryPlaybackSpeed
import ac.mdiq.podcini.preferences.PlaybackPreferences.Companion.createInstanceFromPreferences
import ac.mdiq.podcini.preferences.PlaybackPreferences.Companion.currentEpisodeIsVideo
import ac.mdiq.podcini.preferences.PlaybackPreferences.Companion.currentPlayerStatus
import ac.mdiq.podcini.preferences.PlaybackPreferences.Companion.currentlyPlayingFeedMediaId
import ac.mdiq.podcini.preferences.PlaybackPreferences.Companion.currentlyPlayingTemporaryPlaybackSpeed
import ac.mdiq.podcini.preferences.PlaybackPreferences.Companion.writeMediaPlaying
import ac.mdiq.podcini.preferences.PlaybackPreferences.Companion.writeNoMediaPlaying
import ac.mdiq.podcini.preferences.PlaybackPreferences.Companion.writePlayerStatus
import ac.mdiq.podcini.preferences.SleepTimerPreferences.autoEnable
import ac.mdiq.podcini.preferences.SleepTimerPreferences.autoEnableFrom
import ac.mdiq.podcini.preferences.SleepTimerPreferences.autoEnableTo
import ac.mdiq.podcini.preferences.SleepTimerPreferences.isInTimeRange
import ac.mdiq.podcini.preferences.SleepTimerPreferences.timerMillis
import ac.mdiq.podcini.preferences.UserPreferences.allEpisodesSortOrder
import ac.mdiq.podcini.preferences.UserPreferences.downloadsSortedOrder
import ac.mdiq.podcini.preferences.UserPreferences.fastForwardSecs
import ac.mdiq.podcini.preferences.UserPreferences.getPlaybackSpeed
import ac.mdiq.podcini.preferences.UserPreferences.hardwareForwardButton
import ac.mdiq.podcini.preferences.UserPreferences.hardwarePreviousButton
import ac.mdiq.podcini.preferences.UserPreferences.isAllowMobileStreaming
import ac.mdiq.podcini.preferences.UserPreferences.isFollowQueue
import ac.mdiq.podcini.preferences.UserPreferences.isPauseOnHeadsetDisconnect
import ac.mdiq.podcini.preferences.UserPreferences.isPersistNotify
import ac.mdiq.podcini.preferences.UserPreferences.isSkipSilence
import ac.mdiq.podcini.preferences.UserPreferences.isUnpauseOnBluetoothReconnect
import ac.mdiq.podcini.preferences.UserPreferences.isUnpauseOnHeadsetReconnect
import ac.mdiq.podcini.preferences.UserPreferences.playbackSpeedArray
import ac.mdiq.podcini.preferences.UserPreferences.rewindSecs
import ac.mdiq.podcini.preferences.UserPreferences.setPlaybackSpeed
import ac.mdiq.podcini.preferences.UserPreferences.shouldFavoriteKeepEpisode
import ac.mdiq.podcini.preferences.UserPreferences.shouldSkipKeepEpisode
import ac.mdiq.podcini.preferences.UserPreferences.showNextChapterOnFullNotification
import ac.mdiq.podcini.preferences.UserPreferences.showPlaybackSpeedOnFullNotification
import ac.mdiq.podcini.preferences.UserPreferences.showSkipOnFullNotification
import ac.mdiq.podcini.preferences.UserPreferences.videoPlaybackSpeed
import ac.mdiq.podcini.receiver.MediaButtonReceiver
import ac.mdiq.podcini.service.playback.WearMediaSession
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.FeedSearcher
import ac.mdiq.podcini.storage.model.feed.*
import ac.mdiq.podcini.storage.model.feed.FeedPreferences.AutoDeleteAction
import ac.mdiq.podcini.storage.model.playback.MediaType
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.ui.activity.appstartintent.MainActivityStarter
import ac.mdiq.podcini.ui.activity.appstartintent.VideoPlayerActivityStarter
import ac.mdiq.podcini.ui.utils.NotificationUtils
import ac.mdiq.podcini.ui.widget.WidgetUpdater.WidgetState
import ac.mdiq.podcini.util.ChapterUtils.getCurrentChapterIndex
import ac.mdiq.podcini.util.FeedItemUtil.hasAlmostEnded
import ac.mdiq.podcini.util.FeedUtil.shouldAutoDeleteItemsOnThatFeed
import ac.mdiq.podcini.util.IntentUtils.sendLocalBroadcast
import ac.mdiq.podcini.util.NetworkUtils.isStreamingAllowed
import ac.mdiq.podcini.util.event.MessageEvent
import ac.mdiq.podcini.util.event.PlayerErrorEvent
import ac.mdiq.podcini.util.event.playback.*
import ac.mdiq.podcini.util.event.settings.SkipIntroEndingChangedEvent
import ac.mdiq.podcini.util.event.settings.SpeedPresetChangedEvent
import ac.mdiq.podcini.util.event.settings.VolumeAdaptionChangedEvent
import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.UiModeManager
import android.bluetooth.BluetoothA2dp
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.os.Build.VERSION_CODES
import android.service.quicksettings.TileService
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import android.util.Log
import android.util.Pair
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.ViewConfiguration
import android.webkit.URLUtil
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media3.common.util.UnstableApi
import io.reactivex.*
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile
import kotlin.math.max

/**
 * Controls the MediaPlayer that plays a FeedMedia-file
 */
@UnstableApi
class PlaybackService : MediaBrowserServiceCompat() {
    private var mediaPlayer: PlaybackServiceMediaPlayer? = null
    private var positionEventTimer: Disposable? = null

    private lateinit var taskManager: PlaybackServiceTaskManager
    private lateinit var stateManager: PlaybackServiceStateManager
    private lateinit var notificationBuilder: PlaybackServiceNotificationBuilder
    private lateinit var castStateListener: CastStateListener

    private var autoSkippedFeedMediaId: String? = null
    private var clickCount = 0
    private val clickHandler = Handler(Looper.getMainLooper())

    private var isSpeedForward = false
    private var normalSpeed = 1.0f
    private var isFallbackSpeed = false

    private var currentitem: FeedItem? = null

    /**
     * Used for Lollipop notifications, Android Wear, and Android Auto.
     */
    private var mediaSession: MediaSessionCompat? = null

    private val mBinder: IBinder = LocalBinder()

    inner class LocalBinder : Binder() {
        val service: PlaybackService
            get() = this@PlaybackService
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.d(TAG, "Received onUnbind event")
        return super.onUnbind(intent)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created.")
        isRunning = true

        stateManager = PlaybackServiceStateManager(this)
        notificationBuilder = PlaybackServiceNotificationBuilder(this)

        if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            registerReceiver(autoStateUpdated, IntentFilter("com.google.android.gms.car.media.STATUS"), RECEIVER_NOT_EXPORTED)
            registerReceiver(shutdownReceiver, IntentFilter(PlaybackServiceInterface.ACTION_SHUTDOWN_PLAYBACK_SERVICE), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(autoStateUpdated, IntentFilter("com.google.android.gms.car.media.STATUS"))
            registerReceiver(shutdownReceiver, IntentFilter(PlaybackServiceInterface.ACTION_SHUTDOWN_PLAYBACK_SERVICE))
        }

        registerReceiver(headsetDisconnected, IntentFilter(Intent.ACTION_HEADSET_PLUG))
        registerReceiver(bluetoothStateUpdated, IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED))
        registerReceiver(audioBecomingNoisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        EventBus.getDefault().register(this)
        taskManager = PlaybackServiceTaskManager(this, taskManagerCallback)

        recreateMediaSessionIfNeeded()
        castStateListener = object : CastStateListener(this) {
            override fun onSessionStartedOrEnded() {
                recreateMediaPlayer()
            }
        }
        EventBus.getDefault().post(PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_STARTED))
    }

    fun recreateMediaSessionIfNeeded() {
        if (mediaSession != null) {
            // Media session was not destroyed, so we can re-use it.
            if (!mediaSession!!.isActive) {
                mediaSession!!.isActive = true
            }
            return
        }
        val eventReceiver = ComponentName(applicationContext, MediaButtonReceiver::class.java)
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        mediaButtonIntent.setComponent(eventReceiver)
        val buttonReceiverIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))

        mediaSession = MediaSessionCompat(applicationContext, TAG, eventReceiver, buttonReceiverIntent)
        sessionToken = mediaSession!!.sessionToken

        try {
            mediaSession!!.setCallback(sessionCallback)
            mediaSession!!.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        } catch (npe: NullPointerException) {
            // on some devices (Huawei) setting active can cause a NullPointerException
            // even with correct use of the api.
            // See http://stackoverflow.com/questions/31556679/android-huawei-mediassessioncompat
            // and https://plus.google.com/+IanLake/posts/YgdTkKFxz7d
            Log.e(TAG, "NullPointerException while setting up MediaSession")
            npe.printStackTrace()
        }

        recreateMediaPlayer()
        mediaSession!!.isActive = true
    }

    fun recreateMediaPlayer() {
        var media: Playable? = null
        var wasPlaying = false
        if (mediaPlayer != null) {
            media = mediaPlayer!!.getPlayable()
            wasPlaying = mediaPlayer!!.playerStatus == PlayerStatus.PLAYING || mediaPlayer!!.playerStatus == PlayerStatus.FALLBACK
            mediaPlayer!!.pause(true, false)
            mediaPlayer!!.shutdown()
        }
        mediaPlayer = CastPsmp.getInstanceIfConnected(this, mediaPlayerCallback)
        if (mediaPlayer == null) {
            mediaPlayer = LocalPSMP(this, mediaPlayerCallback) // Cast not supported or not connected
        }
        if (media != null) {
            mediaPlayer!!.playMediaObject(media, !media.localFileAvailable(), wasPlaying, true)
        }
        isCasting = mediaPlayer!!.isCasting()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service is about to be destroyed")

        if (notificationBuilder.playerStatus == PlayerStatus.PLAYING || notificationBuilder.playerStatus == PlayerStatus.FALLBACK) {
            notificationBuilder.playerStatus = PlayerStatus.STOPPED
            val notificationManager = NotificationManagerCompat.from(this)
            if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
//                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                Log.e(TAG, "onDestroy: require POST_NOTIFICATIONS permission")
                Toast.makeText(applicationContext, R.string.notification_permission_text, Toast.LENGTH_LONG).show()
                return
            }
            notificationManager.notify(R.id.notification_playing, notificationBuilder.build())
        }
        stateManager.stopForeground(!isPersistNotify)
        isRunning = false
        currentMediaType = MediaType.UNKNOWN
        castStateListener.destroy()

        cancelPositionObserver()
        mediaSession?.release()
        mediaSession = null
        mediaPlayer?.shutdown()

        unregisterReceiver(autoStateUpdated)
        unregisterReceiver(headsetDisconnected)
        unregisterReceiver(shutdownReceiver)
        unregisterReceiver(bluetoothStateUpdated)
        unregisterReceiver(audioBecomingNoisy)
        taskManager.shutdown()
        EventBus.getDefault().unregister(this)
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        Log.d(TAG, "OnGetRoot: clientPackageName=" + clientPackageName +
                "; clientUid=" + clientUid + " ; rootHints=" + rootHints)
        if (rootHints != null && rootHints.getBoolean(BrowserRoot.EXTRA_RECENT)) {
            val extras = Bundle()
            extras.putBoolean(BrowserRoot.EXTRA_RECENT, true)
            Log.d(TAG, "OnGetRoot: Returning BrowserRoot " + R.string.current_playing_episode)
            return BrowserRoot(resources.getString(R.string.current_playing_episode), extras)
        }

        // Name visible in Android Auto
        return BrowserRoot(resources.getString(R.string.app_name), null)
    }

    private fun loadQueueForMediaSession() {
        Single.create { emitter: SingleEmitter<List<MediaSessionCompat.QueueItem>?> ->
            val queueItems: MutableList<MediaSessionCompat.QueueItem> = ArrayList()
            for (feedItem in DBReader.getQueue()) {
                if (feedItem.media != null) {
                    val mediaDescription = feedItem.media!!.mediaItem.description
                    queueItems.add(MediaSessionCompat.QueueItem(mediaDescription, feedItem.id))
                }
            }
            emitter.onSuccess(queueItems)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ queueItems: List<MediaSessionCompat.QueueItem>? -> mediaSession?.setQueue(queueItems) },
                { obj: Throwable -> obj.printStackTrace() })
    }

    private fun createBrowsableMediaItem(
            @StringRes title: Int, @DrawableRes icon: Int, numEpisodes: Int
    ): MediaBrowserCompat.MediaItem {
        val uri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(resources.getResourcePackageName(icon))
            .appendPath(resources.getResourceTypeName(icon))
            .appendPath(resources.getResourceEntryName(icon))
            .build()

        val description = MediaDescriptionCompat.Builder()
            .setIconUri(uri)
            .setMediaId(resources.getString(title))
            .setTitle(resources.getString(title))
            .setSubtitle(resources.getQuantityString(R.plurals.num_episodes, numEpisodes, numEpisodes))
            .build()
        return MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    private fun createBrowsableMediaItemForFeed(feed: Feed): MediaBrowserCompat.MediaItem {
        val builder = MediaDescriptionCompat.Builder()
            .setMediaId("FeedId:" + feed.id)
            .setTitle(feed.title)
            .setDescription(feed.description)
            .setSubtitle(feed.getCustomTitle())
        if (feed.imageUrl != null) {
            builder.setIconUri(Uri.parse(feed.imageUrl))
        }
        if (feed.link != null) {
            builder.setMediaUri(Uri.parse(feed.link))
        }
        val description = builder.build()
        return MediaBrowserCompat.MediaItem(description,
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    override fun onLoadChildren(parentId: String,
                                result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        Log.d(TAG, "OnLoadChildren: parentMediaId=$parentId")
        result.detach()

        Completable.create { emitter: CompletableEmitter ->
            result.sendResult(loadChildrenSynchronous(parentId))
            emitter.onComplete()
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {}, { e: Throwable ->
                    e.printStackTrace()
                    result.sendResult(null)
                })
    }

    private fun loadChildrenSynchronous(parentId: String): List<MediaBrowserCompat.MediaItem>? {
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        if (parentId == resources.getString(R.string.app_name)) {
            val currentlyPlaying = currentPlayerStatus.toLong()
            if (currentlyPlaying == PlaybackPreferences.PLAYER_STATUS_PLAYING.toLong()
                    || currentlyPlaying == PlaybackPreferences.PLAYER_STATUS_PAUSED.toLong()) {
                mediaItems.add(createBrowsableMediaItem(R.string.current_playing_episode, R.drawable.ic_play_48dp, 1))
            }
            mediaItems.add(createBrowsableMediaItem(R.string.queue_label, R.drawable.ic_playlist_play_black,
                DBReader.getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.QUEUED))))
            mediaItems.add(createBrowsableMediaItem(R.string.downloads_label, R.drawable.ic_download_black,
                DBReader.getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.DOWNLOADED))))
            mediaItems.add(createBrowsableMediaItem(R.string.episodes_label, R.drawable.ic_feed_black,
                DBReader.getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.UNPLAYED))))
            val feeds = DBReader.getFeedList()
            for (feed in feeds) {
                mediaItems.add(createBrowsableMediaItemForFeed(feed))
            }
            return mediaItems
        }

        val feedItems: List<FeedItem?>
        when {
            parentId == resources.getString(R.string.queue_label) -> {
                feedItems = DBReader.getQueue()
            }
            parentId == resources.getString(R.string.downloads_label) -> {
                feedItems = DBReader.getEpisodes(0, MAX_ANDROID_AUTO_EPISODES_PER_FEED,
                    FeedItemFilter(FeedItemFilter.DOWNLOADED), downloadsSortedOrder)
            }
            parentId == resources.getString(R.string.episodes_label) -> {
                feedItems = DBReader.getEpisodes(0, MAX_ANDROID_AUTO_EPISODES_PER_FEED,
                    FeedItemFilter(FeedItemFilter.UNPLAYED), allEpisodesSortOrder)
            }
            parentId.startsWith("FeedId:") -> {
                val feedId = parentId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1].toLong()
                val feed = DBReader.getFeed(feedId)
                feedItems = if (feed != null) DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(), feed.sortOrder) else listOf()
            }
            parentId == getString(R.string.current_playing_episode) -> {
                val playable = createInstanceFromPreferences(this)
                if (playable is FeedMedia) {
                    feedItems = listOf(playable.item)
                } else {
                    return null
                }
            }
            else -> {
                Log.e(TAG, "Parent ID not found: $parentId")
                return null
            }
        }
        var count = 0
        for (feedItem in feedItems) {
            if (feedItem?.media != null) {
                mediaItems.add(feedItem.media!!.mediaItem)
                if (++count >= MAX_ANDROID_AUTO_EPISODES_PER_FEED) {
                    break
                }
            }
        }
        return mediaItems
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.d(TAG, "Received onBind event")
        return if (intent.action != null && TextUtils.equals(intent.action, SERVICE_INTERFACE)) {
            super.onBind(intent)
        } else {
            mBinder
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "OnStartCommand called")

        stateManager.startForeground(R.id.notification_playing, notificationBuilder.build())
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.cancel(R.id.notification_streaming_confirmation)

        val keycode = intent.getIntExtra(MediaButtonReceiver.EXTRA_KEYCODE, -1)
        val customAction = intent.getStringExtra(MediaButtonReceiver.EXTRA_CUSTOM_ACTION)
        val hardwareButton = intent.getBooleanExtra(MediaButtonReceiver.EXTRA_HARDWAREBUTTON, false)
        val playable = intent.getParcelableExtra<Playable>(PlaybackServiceInterface.EXTRA_PLAYABLE)
        if (keycode == -1 && playable == null && customAction == null) {
            Log.e(TAG, "PlaybackService was started with no arguments")
            stateManager.stopService()
            return START_NOT_STICKY
        }

        if ((flags and START_FLAG_REDELIVERY) != 0) {
            Log.d(TAG, "onStartCommand is a redelivered intent, calling stopForeground now.")
            stateManager.stopForeground(true)
        } else {
            when {
                keycode != -1 -> {
                    val notificationButton: Boolean
                    if (hardwareButton) {
                        Log.d(TAG, "Received hardware button event")
                        notificationButton = false
                    } else {
                        Log.d(TAG, "Received media button event")
                        notificationButton = true
                    }
                    val handled = handleKeycode(keycode, notificationButton)
                    if (!handled && !stateManager.hasReceivedValidStartCommand()) {
                        stateManager.stopService()
                        return START_NOT_STICKY
                    }
                }
                playable != null -> {
                    stateManager.validStartCommandWasReceived()
                    val allowStreamThisTime = intent.getBooleanExtra(
                        PlaybackServiceInterface.EXTRA_ALLOW_STREAM_THIS_TIME, false)
                    val allowStreamAlways = intent.getBooleanExtra(
                        PlaybackServiceInterface.EXTRA_ALLOW_STREAM_ALWAYS, false)
                    sendNotificationBroadcast(PlaybackServiceInterface.NOTIFICATION_TYPE_RELOAD, 0)
                    if (allowStreamAlways) {
                        isAllowMobileStreaming = true
                    }
                    Observable.fromCallable {
                        if (playable is FeedMedia) {
                            return@fromCallable DBReader.getFeedMedia(playable.id)
                        } else {
                            return@fromCallable playable
                        }
                    }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            { loadedPlayable: Playable? -> startPlaying(loadedPlayable, allowStreamThisTime) },
                            { error: Throwable ->
                                Log.d(TAG, "Playable was not found. Stopping service.")
                                error.printStackTrace()
                                stateManager.stopService()
                            })
                    return START_NOT_STICKY
                }
                else -> {
                    mediaSession?.controller?.transportControls?.sendCustomAction(customAction, null)
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun skipIntro(playable: Playable) {
        val item = (playable as? FeedMedia)?.item ?: currentitem ?: return
//        val item = currentitem ?: (playable as? FeedMedia)?.item ?: return
        val feed = item.feed ?: DBReader.getFeed(item.feedId)
        val preferences = feed?.preferences

        val skipIntro = preferences?.feedSkipIntro ?: 0
        val skipIntroMS = skipIntro * 1000
        if (skipIntro > 0 && playable.getPosition() < skipIntroMS) {
            val duration = duration
            if (skipIntroMS < duration || duration <= 0) {
                Log.d(TAG, "skipIntro " + playable.getEpisodeTitle())
                mediaPlayer?.seekTo(skipIntroMS)
                val skipIntroMesg = applicationContext.getString(R.string.pref_feed_skip_intro_toast, skipIntro)
                val toast = Toast.makeText(applicationContext, skipIntroMesg, Toast.LENGTH_LONG)
                toast.show()
            }
        }
    }

    @SuppressLint("LaunchActivityFromNotification")
    private fun displayStreamingNotAllowedNotification(originalIntent: Intent) {
        if (EventBus.getDefault().hasSubscriberForEvent(MessageEvent::class.java)) {
            EventBus.getDefault().post(MessageEvent(
                getString(R.string.confirm_mobile_streaming_notification_message)))
            return
        }

        val intentAllowThisTime = Intent(originalIntent)
        intentAllowThisTime.setAction(PlaybackServiceInterface.EXTRA_ALLOW_STREAM_THIS_TIME)
        intentAllowThisTime.putExtra(PlaybackServiceInterface.EXTRA_ALLOW_STREAM_THIS_TIME, true)
        val pendingIntentAllowThisTime = if (Build.VERSION.SDK_INT >= VERSION_CODES.O) {
            PendingIntent.getForegroundService(this,
                R.id.pending_intent_allow_stream_this_time, intentAllowThisTime,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(this,
                R.id.pending_intent_allow_stream_this_time, intentAllowThisTime, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val intentAlwaysAllow = Intent(intentAllowThisTime)
        intentAlwaysAllow.setAction(PlaybackServiceInterface.EXTRA_ALLOW_STREAM_ALWAYS)
        intentAlwaysAllow.putExtra(PlaybackServiceInterface.EXTRA_ALLOW_STREAM_ALWAYS, true)
        val pendingIntentAlwaysAllow = if (Build.VERSION.SDK_INT >= VERSION_CODES.O) {
            PendingIntent.getForegroundService(this,
                R.id.pending_intent_allow_stream_always, intentAlwaysAllow,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(this,
                R.id.pending_intent_allow_stream_always, intentAlwaysAllow, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val builder = NotificationCompat.Builder(this,
            NotificationUtils.CHANNEL_ID_USER_ACTION)
            .setSmallIcon(R.drawable.ic_notification_stream)
            .setContentTitle(getString(R.string.confirm_mobile_streaming_notification_title))
            .setContentText(getString(R.string.confirm_mobile_streaming_notification_message))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(getString(R.string.confirm_mobile_streaming_notification_message)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntentAllowThisTime)
            .addAction(R.drawable.ic_notification_stream,
                getString(R.string.confirm_mobile_streaming_button_once),
                pendingIntentAllowThisTime)
            .addAction(R.drawable.ic_notification_stream,
                getString(R.string.confirm_mobile_streaming_button_always),
                pendingIntentAlwaysAllow)
            .setAutoCancel(true)
        val notificationManager = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
//            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Log.e(TAG, "displayStreamingNotAllowedNotification: require POST_NOTIFICATIONS permission")
            Toast.makeText(applicationContext, R.string.notification_permission_text, Toast.LENGTH_LONG).show()
            return
        }
        notificationManager.notify(R.id.notification_streaming_confirmation, builder.build())
    }

    /**
     * Handles media button events
     * return: keycode was handled
     */
    private fun handleKeycode(keycode: Int, notificationButton: Boolean): Boolean {
        Log.d(TAG, "Handling keycode: $keycode")
        val info = mediaPlayer?.pSMPInfo
        val status = info?.playerStatus
        when (keycode) {
            KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                when {
                    status == PlayerStatus.PLAYING -> {
                        mediaPlayer?.pause(!isPersistNotify, false)
                    }
                    status == PlayerStatus.FALLBACK || status == PlayerStatus.PAUSED || status == PlayerStatus.PREPARED -> {
                        mediaPlayer?.resume()
                    }
                    status == PlayerStatus.PREPARING -> {
                        mediaPlayer?.setStartWhenPrepared(!mediaPlayer!!.isStartWhenPrepared())
                    }
                    status == PlayerStatus.INITIALIZED -> {
                        mediaPlayer?.setStartWhenPrepared(true)
                        mediaPlayer?.prepare()
                    }
                    mediaPlayer?.getPlayable() == null -> {
                        startPlayingFromPreferences()
                    }
                    else -> {
                        return false
                    }
                }
                taskManager.restartSleepTimer()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                when {
                    status == PlayerStatus.PAUSED || status == PlayerStatus.PREPARED -> {
                        mediaPlayer?.resume()
                    }
                    status == PlayerStatus.INITIALIZED -> {
                        mediaPlayer?.setStartWhenPrepared(true)
                        mediaPlayer?.prepare()
                    }
                    mediaPlayer?.getPlayable() == null -> {
                        startPlayingFromPreferences()
                    }
                    else -> {
                        return false
                    }
                }
                taskManager.restartSleepTimer()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (status == PlayerStatus.PLAYING) {
                    mediaPlayer?.pause(!isPersistNotify, false)
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                when {
                    !notificationButton -> {
                        // Handle remapped button as notification button which is not remapped again.
                        return handleKeycode(hardwareForwardButton, true)
                    }
                    this.status == PlayerStatus.PLAYING || this.status == PlayerStatus.PAUSED -> {
                        mediaPlayer?.skip()
                        return true
                    }
                    else -> return false
                }
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (this.status == PlayerStatus.FALLBACK || this.status == PlayerStatus.PLAYING || this.status == PlayerStatus.PAUSED) {
                    mediaPlayer?.seekDelta(fastForwardSecs * 1000)
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                when {
                    !notificationButton -> {
                        // Handle remapped button as notification button which is not remapped again.
                        return handleKeycode(hardwarePreviousButton, true)
                    }
                    this.status == PlayerStatus.FALLBACK || this.status == PlayerStatus.PLAYING || this.status == PlayerStatus.PAUSED -> {
                        mediaPlayer?.seekTo(0)
                        return true
                    }
                    else -> return false
                }
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (this.status == PlayerStatus.FALLBACK || this.status == PlayerStatus.PLAYING || this.status == PlayerStatus.PAUSED) {
                    mediaPlayer?.seekDelta(-rewindSecs * 1000)
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                if (this.status == PlayerStatus.FALLBACK || status == PlayerStatus.PLAYING) {
                    mediaPlayer?.pause(true, true)
                }
                stateManager.stopForeground(true) // gets rid of persistent notification
                return true
            }
            else -> {
                Log.d(TAG, "Unhandled key code: $keycode")
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
        Observable.fromCallable {
            createInstanceFromPreferences(
                applicationContext)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { playable: Playable? -> startPlaying(playable, false) },
                { error: Throwable ->
                    Log.d(TAG, "Playable was not loaded from preferences. Stopping service.")
                    error.printStackTrace()
                    stateManager.stopService()
                })
    }

    private fun startPlaying(playable: Playable?, allowStreamThisTime: Boolean) {
        if (playable == null) return

        val localFeed = URLUtil.isContentUrl(playable.getStreamUrl())
        val stream = !playable.localFileAvailable() || localFeed
        if (stream && !localFeed && !isStreamingAllowed && !allowStreamThisTime) {
            displayStreamingNotAllowedNotification(PlaybackServiceStarter(this, playable).intent)
            writeNoMediaPlaying()
            stateManager.stopService()
            return
        }

        if (playable.getIdentifier() != currentlyPlayingFeedMediaId) {
            clearCurrentlyPlayingTemporaryPlaybackSpeed()
        }

        mediaPlayer?.playMediaObject(playable, stream, true, true)
        stateManager.validStartCommandWasReceived()
        stateManager.startForeground(R.id.notification_playing, notificationBuilder.build())
        recreateMediaSessionIfNeeded()
        updateNotificationAndMediaSession(playable)
        addPlayableToQueue(playable)
    }

    /**
     * Called by a mediaplayer Activity as soon as it has prepared its
     * mediaplayer.
     */
    fun setVideoSurface(sh: SurfaceHolder?) {
        Log.d(TAG, "Setting display")
        mediaPlayer?.setVideoSurface(sh)
    }

    fun notifyVideoSurfaceAbandoned() {
        mediaPlayer?.pause(true, false)
        mediaPlayer?.resetVideoSurface()
        updateNotificationAndMediaSession(playable)
        stateManager.stopForeground(!isPersistNotify)
    }

    private val taskManagerCallback: PSTMCallback = object : PSTMCallback {
        override fun positionSaverTick() {
            saveCurrentPosition(true, null, Playable.INVALID_TIME)
        }

        override fun requestWidgetState(): WidgetState {
            return WidgetState(this@PlaybackService.playable, this@PlaybackService.status,
                this@PlaybackService.currentPosition, this@PlaybackService.duration, this@PlaybackService.currentPlaybackSpeed)
        }

        override fun onChapterLoaded(media: Playable?) {
            sendNotificationBroadcast(PlaybackServiceInterface.NOTIFICATION_TYPE_RELOAD, 0)
            updateMediaSession(mediaPlayer?.playerStatus)
        }
    }

    private val mediaPlayerCallback: PSMPCallback = object : PSMPCallback {
        override fun statusChanged(newInfo: PSMPInfo?) {
            currentMediaType = mediaPlayer?.getCurrentMediaType() ?: MediaType.UNKNOWN
            Log.d(TAG, "statusChanged called")
            updateMediaSession(newInfo?.playerStatus)
            if (newInfo != null) {
                when (newInfo.playerStatus) {
                    PlayerStatus.INITIALIZED -> {
                        if (mediaPlayer != null) {
                            writeMediaPlaying(mediaPlayer!!.pSMPInfo.playable, mediaPlayer!!.pSMPInfo.playerStatus, currentitem)
                        }
                        updateNotificationAndMediaSession(newInfo.playable)
                    }
                    PlayerStatus.PREPARED -> {
                        if (mediaPlayer != null) {
                            writeMediaPlaying(mediaPlayer!!.pSMPInfo.playable, mediaPlayer!!.pSMPInfo.playerStatus, currentitem)
                        }
                        taskManager.startChapterLoader(newInfo.playable!!)
                    }
                    PlayerStatus.PAUSED -> {
                        updateNotificationAndMediaSession(newInfo.playable)
                        if (!isCasting) {
                            stateManager.stopForeground(!isPersistNotify)
                        }
                        cancelPositionObserver()
                        if (mediaPlayer != null) writePlayerStatus(mediaPlayer!!.playerStatus)
                    }
                    PlayerStatus.STOPPED -> {}
                    PlayerStatus.PLAYING -> {
                        if (mediaPlayer != null) writePlayerStatus(mediaPlayer!!.playerStatus)
                        saveCurrentPosition(true, null, Playable.INVALID_TIME)
                        recreateMediaSessionIfNeeded()
                        updateNotificationAndMediaSession(newInfo.playable)
                        setupPositionObserver()
                        stateManager.validStartCommandWasReceived()
                        stateManager.startForeground(R.id.notification_playing, notificationBuilder.build())
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

                        if (newInfo.oldPlayerStatus != null && newInfo.oldPlayerStatus != PlayerStatus.SEEKING && autoEnable() && autoEnableByTime && !sleepTimerActive()) {
                            setSleepTimer(timerMillis())
                            EventBus.getDefault().post(MessageEvent(getString(R.string.sleep_timer_enabled_label), { disableSleepTimer() }, getString(R.string.undo)))
                        }
                        loadQueueForMediaSession()
                    }
                    PlayerStatus.ERROR -> {
                        writeNoMediaPlaying()
                        stateManager.stopService()
                    }
                    else -> {}
                }
            }
            if (Build.VERSION.SDK_INT >= VERSION_CODES.N) {
                TileService.requestListeningState(applicationContext,
                    ComponentName(applicationContext, QuickSettingsTileService::class.java))
            }

            sendLocalBroadcast(applicationContext, ACTION_PLAYER_STATUS_CHANGED)
            bluetoothNotifyChange(newInfo, AVRCP_ACTION_PLAYER_STATUS_CHANGED)
            bluetoothNotifyChange(newInfo, AVRCP_ACTION_META_CHANGED)
            taskManager.requestWidgetUpdate()
        }

        override fun shouldStop() {
            stateManager.stopForeground(!isPersistNotify)
        }

        override fun onMediaChanged(reloadUI: Boolean) {
            Log.d(TAG, "reloadUI callback reached")
            if (reloadUI) {
                sendNotificationBroadcast(PlaybackServiceInterface.NOTIFICATION_TYPE_RELOAD, 0)
            }
            updateNotificationAndMediaSession(this@PlaybackService.playable)
        }

        override fun onPostPlayback(media: Playable, ended: Boolean, skipped: Boolean,
                                    playingNext: Boolean
        ) {
            this@PlaybackService.onPostPlayback(media, ended, skipped, playingNext)
        }

        override fun onPlaybackStart(playable: Playable, position: Int) {
            taskManager.startWidgetUpdater()
            if (position != Playable.INVALID_TIME) {
                playable.setPosition(position)
            } else {
                skipIntro(playable)
            }
            playable.onPlaybackStart()
            taskManager.startPositionSaver()
        }

        override fun onPlaybackPause(playable: Playable?, position: Int) {
            taskManager.cancelPositionSaver()
            cancelPositionObserver()
            saveCurrentPosition(position == Playable.INVALID_TIME || playable == null, playable, position)
            taskManager.cancelWidgetUpdater()
            if (playable != null) {
                if (playable is FeedMedia) {
                    SynchronizationQueueSink.enqueueEpisodePlayedIfSynchronizationIsActive(applicationContext, playable, false)
                }
                playable.onPlaybackPause(applicationContext)
            }
        }

        override fun getNextInQueue(currentMedia: Playable?): Playable? {
            return this@PlaybackService.getNextInQueue(currentMedia)
        }

        override fun findMedia(url: String): Playable? {
            val item = DBReader.getFeedItemByGuidOrEpisodeUrl(null, url)
            return item?.media
        }

        override fun onPlaybackEnded(mediaType: MediaType?, stopPlaying: Boolean) {
            this@PlaybackService.onPlaybackEnded(mediaType, stopPlaying)
        }

        override fun ensureMediaInfoLoaded(media: Playable) {
            if (media is FeedMedia && media.item == null) {
                media.setItem(DBReader.getFeedItem(media.itemId))
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun playerError(event: PlayerErrorEvent?) {
        if (mediaPlayer?.playerStatus == PlayerStatus.PLAYING || mediaPlayer?.playerStatus == PlayerStatus.FALLBACK) {
            mediaPlayer!!.pause(true, false)
        }
        stateManager.stopService()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun bufferUpdate(event: BufferUpdateEvent) {
        if (event.hasEnded()) {
            val playable = playable
            if (this.playable is FeedMedia && playable!!.getDuration() <= 0 && (mediaPlayer?.getDuration()?:0) > 0) {
                // Playable is being streamed and does not have a duration specified in the feed
                playable.setDuration(mediaPlayer!!.getDuration())
                DBWriter.setFeedMedia(playable as FeedMedia?)
                updateNotificationAndMediaSession(playable)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun sleepTimerUpdate(event: SleepTimerUpdatedEvent) {
        when {
            event.isOver -> {
                mediaPlayer?.pause(true, true)
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
            event.getTimeLeft() < PlaybackServiceTaskManager.NOTIFICATION_THRESHOLD -> {
                val multiplicators = floatArrayOf(0.1f, 0.2f, 0.3f, 0.3f, 0.3f, 0.4f, 0.4f, 0.4f, 0.6f, 0.8f)
                val multiplicator = multiplicators[max(0.0, (event.getTimeLeft().toInt() / 1000).toDouble())
                    .toInt()]
                Log.d(TAG, "onSleepTimerAlmostExpired: $multiplicator")
                mediaPlayer?.setVolume(multiplicator, multiplicator)
            }
            event.isCancelled -> {
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
        }
    }

    private fun getNextInQueue(currentMedia: Playable?): Playable? {
        if (currentMedia !is FeedMedia) {
            Log.d(TAG, "getNextInQueue(), but playable not an instance of FeedMedia, so not proceeding")
            writeNoMediaPlaying()
            return null
        }
        Log.d(TAG, "getNextInQueue()")
        if (currentMedia.item == null) {
            currentMedia.setItem(DBReader.getFeedItem(currentMedia.itemId))
        }
        val item = currentMedia.item
        if (item == null) {
            Log.w(TAG, "getNextInQueue() with FeedMedia object whose FeedItem is null")
            writeNoMediaPlaying()
            return null
        }
        val nextItem = DBReader.getNextInQueue(item)

        if (nextItem?.media == null) {
            writeNoMediaPlaying()
            return null
        }

        if (!isFollowQueue) {
            Log.d(TAG, "getNextInQueue(), but follow queue is not enabled.")
            writeMediaPlaying(nextItem.media, PlayerStatus.STOPPED, currentitem)
            updateNotificationAndMediaSession(nextItem.media)
            return null
        }

        if (!nextItem.media!!.localFileAvailable() && !isStreamingAllowed && isFollowQueue && nextItem.feed != null && !nextItem.feed!!.isLocalFeed) {
            displayStreamingNotAllowedNotification(PlaybackServiceStarter(this, nextItem.media!!).intent)
            writeNoMediaPlaying()
            stateManager.stopService()
            return null
        }
        return nextItem.media
    }

    /**
     * Set of instructions to be performed when playback ends.
     */
    private fun onPlaybackEnded(mediaType: MediaType?, stopPlaying: Boolean) {
        Log.d(TAG, "Playback ended")
        clearCurrentlyPlayingTemporaryPlaybackSpeed()
        if (stopPlaying) {
            taskManager.cancelPositionSaver()
            cancelPositionObserver()
            if (!isCasting) {
                stateManager.stopForeground(true)
                stateManager.stopService()
            }
        }
        if (mediaType == null) {
            sendNotificationBroadcast(PlaybackServiceInterface.NOTIFICATION_TYPE_PLAYBACK_END, 0)
        } else {
            sendNotificationBroadcast(PlaybackServiceInterface.NOTIFICATION_TYPE_RELOAD,
                when {
                    isCasting -> PlaybackServiceInterface.EXTRA_CODE_CAST
                    mediaType == MediaType.VIDEO -> PlaybackServiceInterface.EXTRA_CODE_VIDEO
                    else -> PlaybackServiceInterface.EXTRA_CODE_AUDIO
                })
        }
    }

    /**
     * This method processes the media object after its playback ended, either because it completed
     * or because a different media object was selected for playback.
     *
     *
     * Even though these tasks aren't supposed to be resource intensive, a good practice is to
     * usually call this method on a background thread.
     *
     * @param playable    the media object that was playing. It is assumed that its position
     * property was updated before this method was called.
     * @param ended       if true, it signals that {@param playable} was played until its end.
     * In such case, the position property of the media becomes irrelevant for
     * most of the tasks (although it's still a good practice to keep it
     * accurate).
     * @param skipped     if the user pressed a skip >| button.
     * @param playingNext if true, it means another media object is being loaded in place of this
     * one.
     * Instances when we'd set it to false would be when we're not following the
     * queue or when the queue has ended.
     */
    private fun onPostPlayback(playable: Playable?, ended: Boolean, skipped: Boolean, playingNext: Boolean) {
        if (playable == null) {
            Log.e(TAG, "Cannot do post-playback processing: media was null")
            return
        }
        Log.d(TAG, "onPostPlayback(): media=" + playable.getEpisodeTitle())

        if (playable !is FeedMedia) {
            Log.d(TAG, "Not doing post-playback processing: media not of type FeedMedia")
            if (ended) {
                playable.onPlaybackCompleted(applicationContext)
            } else {
                playable.onPlaybackPause(applicationContext)
            }
//            return
        }
        val media = playable
        val item = (media as? FeedMedia)?.item ?: currentitem
        val smartMarkAsPlayed = hasAlmostEnded(media)
        if (!ended && smartMarkAsPlayed) {
            Log.d(TAG, "smart mark as played")
        }

        var autoSkipped = false
        if (autoSkippedFeedMediaId != null && autoSkippedFeedMediaId == item?.identifyingValue) {
            autoSkippedFeedMediaId = null
            autoSkipped = true
        }

        if (media is FeedMedia) {
            if (ended || smartMarkAsPlayed) {
                SynchronizationQueueSink.enqueueEpisodePlayedIfSynchronizationIsActive(
                    applicationContext, media, true)
                media.onPlaybackCompleted(applicationContext)
            } else {
                SynchronizationQueueSink.enqueueEpisodePlayedIfSynchronizationIsActive(
                    applicationContext, media, false)
                media.onPlaybackPause(applicationContext)
            }
        }

        if (item != null) {
            if (ended || smartMarkAsPlayed || autoSkipped || (skipped && !shouldSkipKeepEpisode())) {
                // only mark the item as played if we're not keeping it anyways
                DBWriter.markItemPlayed(item, FeedItem.PLAYED, ended || (skipped && smartMarkAsPlayed))
                // don't know if it actually matters to not autodownload when smart mark as played is triggered
                DBWriter.removeQueueItem(this@PlaybackService, ended, item)
                // Delete episode if enabled
                val action = item.feed?.preferences?.currentAutoDelete
                val shouldAutoDelete = (action == AutoDeleteAction.ALWAYS
                        || (action == AutoDeleteAction.GLOBAL && item.feed != null && shouldAutoDeleteItemsOnThatFeed(item.feed!!)))
                if (media is FeedMedia && shouldAutoDelete &&
                        (!item.isTagged(FeedItem.TAG_FAVORITE) || !shouldFavoriteKeepEpisode())) {
                    DBWriter.deleteFeedMediaOfItem(this@PlaybackService, media.id)
                    Log.d(TAG, "Episode Deleted")
                }
                notifyChildrenChanged(getString(R.string.queue_label))
            }
        }

        if (media is FeedMedia && (ended || skipped || playingNext)) {
            DBWriter.addItemToPlaybackHistory(media)
        }
    }

    fun setSleepTimer(waitingTime: Long) {
        Log.d(TAG, "Setting sleep timer to $waitingTime milliseconds")
        taskManager.setSleepTimer(waitingTime)
    }

    fun disableSleepTimer() {
        taskManager.disableSleepTimer()
    }

    private fun sendNotificationBroadcast(type: Int, code: Int) {
        val intent = Intent(PlaybackServiceInterface.ACTION_PLAYER_NOTIFICATION)
        intent.putExtra(PlaybackServiceInterface.EXTRA_NOTIFICATION_TYPE, type)
        intent.putExtra(PlaybackServiceInterface.EXTRA_NOTIFICATION_CODE, code)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun skipEndingIfNecessary() {
        val playable = mediaPlayer?.getPlayable() as? FeedMedia

        val duration = duration
        val remainingTime = duration - currentPosition

        val item = playable?.item ?: currentitem ?: return
        val feed = item.feed ?: DBReader.getFeed(item.feedId)
        val preferences = feed?.preferences

        val skipEnd = preferences?.feedSkipEnding?:0
        val skipEndMS = skipEnd * 1000
//        Log.d(TAG, "skipEndingIfNecessary: checking " + remainingTime + " " + skipEndMS + " speed " + currentPlaybackSpeed)
        if (skipEnd > 0 && skipEndMS < this.duration && (remainingTime - skipEndMS < 0)) {
            Log.d(TAG, "skipEndingIfNecessary: Skipping the remaining " + remainingTime + " " + skipEndMS + " speed " + currentPlaybackSpeed)
            val context = applicationContext
            val skipMesg = context.getString(R.string.pref_feed_skip_ending_toast, skipEnd)
            val toast = Toast.makeText(context, skipMesg, Toast.LENGTH_LONG)
            toast.show()

            this.autoSkippedFeedMediaId = item?.identifyingValue
            mediaPlayer?.skip()
        }
    }

    /**
     * Updates the Media Session for the corresponding status.
     *
     * @param playerStatus the current [PlayerStatus]
     */
    private fun updateMediaSession(playerStatus: PlayerStatus?) {
        val sessionState = PlaybackStateCompat.Builder()
        val state = if (playerStatus != null) {
            when (playerStatus) {
                PlayerStatus.PLAYING -> PlaybackStateCompat.STATE_PLAYING
                PlayerStatus.FALLBACK -> PlaybackStateCompat.STATE_PLAYING
                PlayerStatus.PREPARED, PlayerStatus.PAUSED -> PlaybackStateCompat.STATE_PAUSED
                PlayerStatus.STOPPED -> PlaybackStateCompat.STATE_STOPPED
                PlayerStatus.SEEKING -> PlaybackStateCompat.STATE_FAST_FORWARDING
                PlayerStatus.PREPARING, PlayerStatus.INITIALIZING -> PlaybackStateCompat.STATE_CONNECTING
                PlayerStatus.ERROR -> PlaybackStateCompat.STATE_ERROR
                PlayerStatus.INITIALIZED, PlayerStatus.INDETERMINATE -> PlaybackStateCompat.STATE_NONE
            }
        } else {
            PlaybackStateCompat.STATE_NONE
        }

        sessionState.setState(state, currentPosition.toLong(), currentPlaybackSpeed)
        val capabilities = (PlaybackStateCompat.ACTION_PLAY
                or PlaybackStateCompat.ACTION_PLAY_PAUSE
                or PlaybackStateCompat.ACTION_REWIND
                or PlaybackStateCompat.ACTION_PAUSE
                or PlaybackStateCompat.ACTION_FAST_FORWARD
                or PlaybackStateCompat.ACTION_SEEK_TO
                or PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED)

        sessionState.setActions(capabilities)

        // On Android Auto, custom actions are added in the following order around the play button, if no default
        // actions are present: Near left, near right, far left, far right, additional actions panel
        val rewindBuilder = PlaybackStateCompat.CustomAction.Builder(
            CUSTOM_ACTION_REWIND,
            getString(R.string.rewind_label),
            R.drawable.ic_notification_fast_rewind
        )
        WearMediaSession.addWearExtrasToAction(rewindBuilder)
        sessionState.addCustomAction(rewindBuilder.build())

        val fastForwardBuilder = PlaybackStateCompat.CustomAction.Builder(
            CUSTOM_ACTION_FAST_FORWARD,
            getString(R.string.fast_forward_label),
            R.drawable.ic_notification_fast_forward
        )
        WearMediaSession.addWearExtrasToAction(fastForwardBuilder)
        sessionState.addCustomAction(fastForwardBuilder.build())

        if (showPlaybackSpeedOnFullNotification()) {
            sessionState.addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    CUSTOM_ACTION_CHANGE_PLAYBACK_SPEED,
                    getString(R.string.playback_speed),
                    R.drawable.ic_notification_playback_speed
                ).build()
            )
        }

        if (showNextChapterOnFullNotification()) {
            if (!playable?.getChapters().isNullOrEmpty()) {
                sessionState.addCustomAction(
                    PlaybackStateCompat.CustomAction.Builder(
                        CUSTOM_ACTION_NEXT_CHAPTER,
                        getString(R.string.next_chapter), R.drawable.ic_notification_next_chapter)
                        .build())
            }
        }

        if (showSkipOnFullNotification()) {
            sessionState.addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_SKIP_TO_NEXT, getString(R.string.skip_episode_label), R.drawable.ic_notification_skip).build()
            )
        }

        if (mediaSession != null) {
            WearMediaSession.mediaSessionSetExtraForWear(mediaSession!!)
            mediaSession!!.setPlaybackState(sessionState.build())
        }
    }

    private fun updateNotificationAndMediaSession(p: Playable?) {
        setupNotification(p)
        updateMediaSessionMetadata(p)
    }

    private fun updateMediaSessionMetadata(p: Playable?) {
        if (p == null || mediaSession == null) {
            return
        }

        val builder = MediaMetadataCompat.Builder()
        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, p.getFeedTitle())
        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, p.getEpisodeTitle())
        builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, p.getFeedTitle())
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, p.getDuration().toLong())
        builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, p.getEpisodeTitle())
        builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, p.getFeedTitle())


        if (notificationBuilder.isIconCached) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, notificationBuilder.cachedIcon)
        } else {
            var iconUri = p.getImageLocation()
            // Don't use embedded cover etc, which Android can't load
            if (p is FeedMedia) {
                val m = p
                if (m.item != null) {
                    val item = m.item!!
                    when {
                        item.imageUrl != null -> {
                            iconUri = item.imageUrl
                        }
                        item.feed != null -> {
                            iconUri = item.feed!!.imageUrl
                        }
                    }
                }
            }
            if (!iconUri.isNullOrEmpty()) {
                builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, iconUri)
            }
        }

        if (stateManager.hasReceivedValidStartCommand()) {
            mediaSession!!.setSessionActivity(PendingIntent.getActivity(this, R.id.pending_intent_player_activity,
                getPlayerActivityIntent(this), PendingIntent.FLAG_UPDATE_CURRENT
                        or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)))
            try {
                mediaSession!!.setMetadata(builder.build())
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Setting media session metadata", e)
                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, null)
                mediaSession!!.setMetadata(builder.build())
            }
        }
    }

    /**
     * Used by setupNotification to load notification data in another thread.
     */
    private var playableIconLoaderThread: Thread? = null

    /**
     * Prepares notification and starts the service in the foreground.
     */
    @Synchronized
    private fun setupNotification(playable: Playable?) {
        Log.d(TAG, "setupNotification")
        playableIconLoaderThread?.interrupt()

        if (playable == null || mediaPlayer == null) {
            Log.d(TAG, "setupNotification: playable=$playable mediaPlayer=$mediaPlayer")
            if (!stateManager.hasReceivedValidStartCommand()) {
                stateManager.stopService()
            }
            return
        }

        val playerStatus = mediaPlayer!!.playerStatus
        notificationBuilder.setPlayable(playable)
        if (mediaSession != null) notificationBuilder.setMediaSessionToken(mediaSession!!.sessionToken)
        notificationBuilder.playerStatus = playerStatus
        notificationBuilder.updatePosition(currentPosition, currentPlaybackSpeed)

        val notificationManager = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
//            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Log.e(TAG, "setupNotification: require POST_NOTIFICATIONS permission")
            Toast.makeText(applicationContext, R.string.notification_permission_text, Toast.LENGTH_LONG).show()
            return
        }
        notificationManager.notify(R.id.notification_playing, notificationBuilder.build())

        if (!notificationBuilder.isIconCached) {
            playableIconLoaderThread = Thread {
                Log.d(TAG, "Loading notification icon")
                notificationBuilder.loadIcon()
                if (!Thread.currentThread().isInterrupted) {
                    notificationManager.notify(R.id.notification_playing, notificationBuilder.build())
                    updateMediaSessionMetadata(playable)
                }
            }
            playableIconLoaderThread?.start()
        }
    }

    /**
     * Persists the current position and last played time of the media file.
     *
     * @param fromMediaPlayer if true, the information is gathered from the current Media Player
     * and {@param playable} and {@param position} become irrelevant.
     * @param playable        the playable for which the current position should be saved, unless
     * {@param fromMediaPlayer} is true.
     * @param position        the position that should be saved, unless {@param fromMediaPlayer} is true.
     */
    @Synchronized
    private fun saveCurrentPosition(fromMediaPlayer: Boolean, playable: Playable?, position: Int) {
        var playable = playable
        var position = position
        val duration: Int
        if (fromMediaPlayer) {
            position = currentPosition
            duration = this.duration
            playable = mediaPlayer?.getPlayable()
        } else {
            duration = playable?.getDuration() ?: Playable.INVALID_TIME
        }
        if (position != Playable.INVALID_TIME && duration != Playable.INVALID_TIME && playable != null) {
//            Log.d(TAG, "Saving current position to $position $duration")
            saveCurrentPosition(playable, position, System.currentTimeMillis())
        }
    }

    fun sleepTimerActive(): Boolean {
        return taskManager.isSleepTimerActive
    }

    val sleepTimerTimeLeft: Long
        get() = taskManager.sleepTimerTimeLeft

    private fun bluetoothNotifyChange(info: PSMPInfo?, whatChanged: String) {
        var isPlaying = false

        if (info?.playerStatus == PlayerStatus.PLAYING || info?.playerStatus == PlayerStatus.FALLBACK) {
            isPlaying = true
        }

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

    private val autoStateUpdated: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getStringExtra("media_connection_status")
            val isConnectedToCar = "media_connected" == status
            Log.d(TAG, "Received Auto Connection update: $status")
            if (!isConnectedToCar) {
                Log.d(TAG, "Car was unplugged during playback.")
            } else {
                val playerStatus = mediaPlayer?.playerStatus
                when (playerStatus) {
                    PlayerStatus.PAUSED, PlayerStatus.PREPARED -> {
                        mediaPlayer?.resume()
                    }
                    PlayerStatus.PREPARING -> {
                        mediaPlayer?.setStartWhenPrepared(!mediaPlayer!!.isStartWhenPrepared())
                    }
                    PlayerStatus.INITIALIZED -> {
                        mediaPlayer?.setStartWhenPrepared(true)
                        mediaPlayer?.prepare()
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Pauses playback when the headset is disconnected and the preference is
     * set
     */
    private val headsetDisconnected: BroadcastReceiver = object : BroadcastReceiver() {
        private val TAG = "headsetDisconnected"
        private val UNPLUGGED = 0
        private val PLUGGED = 1

        override fun onReceive(context: Context, intent: Intent) {
            if (isInitialStickyBroadcast) {
                // Don't pause playback after we just started, just because the receiver
                // delivers the current headset state (instead of a change)
                return
            }

            if (TextUtils.equals(intent.action, Intent.ACTION_HEADSET_PLUG)) {
                val state = intent.getIntExtra("state", -1)
                Log.d(TAG, "Headset plug event. State is $state")
                if (state != -1) {
                    when (state) {
                        UNPLUGGED -> {
                            Log.d(TAG, "Headset was unplugged during playback.")
                        }
                        PLUGGED -> {
                            Log.d(TAG, "Headset was plugged in during playback.")
                            unpauseIfPauseOnDisconnect(false)
                        }
                    }
                } else {
                    Log.e(TAG, "Received invalid ACTION_HEADSET_PLUG intent")
                }
            }
        }
    }

    private val bluetoothStateUpdated: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (TextUtils.equals(intent.action, BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)) {
                val state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1)
                if (state == BluetoothA2dp.STATE_CONNECTED) {
                    Log.d(TAG, "Received bluetooth connection intent")
                    unpauseIfPauseOnDisconnect(true)
                }
            }
        }
    }

    private val audioBecomingNoisy: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // sound is about to change, eg. bluetooth -> speaker
            Log.d(TAG, "Pausing playback because audio is becoming noisy")
            pauseIfPauseOnDisconnect()
        }
    }

    /**
     * Pauses playback if PREF_PAUSE_ON_HEADSET_DISCONNECT was set to true.
     */
    private fun pauseIfPauseOnDisconnect() {
        Log.d(TAG, "pauseIfPauseOnDisconnect()")
        transientPause = (mediaPlayer?.playerStatus == PlayerStatus.PLAYING || mediaPlayer?.playerStatus == PlayerStatus.FALLBACK)
        if (isPauseOnHeadsetDisconnect && !isCasting) {
            mediaPlayer?.pause(!isPersistNotify, false)
        }
    }

    /**
     * @param bluetooth true if the event for unpausing came from bluetooth
     */
    private fun unpauseIfPauseOnDisconnect(bluetooth: Boolean) {
        if (mediaPlayer != null && mediaPlayer!!.isAudioChannelInUse) {
            Log.d(TAG, "unpauseIfPauseOnDisconnect() audio is in use")
            return
        }
        if (transientPause) {
            transientPause = false
            if (Build.VERSION.SDK_INT >= 31) {
                stateManager.stopService()
                return
            }
            when {
                !bluetooth && isUnpauseOnHeadsetReconnect -> {
                    mediaPlayer?.resume()
                }
                bluetooth && isUnpauseOnBluetoothReconnect -> {
                    // let the user know we've started playback again...
                    val v = applicationContext.getSystemService(VIBRATOR_SERVICE) as? Vibrator
                    v?.vibrate(500)
                    mediaPlayer?.resume()
                }
            }
        }
    }

    private val shutdownReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (TextUtils.equals(intent.action, PlaybackServiceInterface.ACTION_SHUTDOWN_PLAYBACK_SERVICE)) {
                EventBus.getDefault().post(PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN))
                stateManager.stopService()
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun volumeAdaptionChanged(event: VolumeAdaptionChangedEvent) {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()
        if (mediaPlayer != null) playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, event.feedId, event.volumeAdaptionSetting)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun speedPresetChanged(event: SpeedPresetChangedEvent) {
//        TODO: speed
        val item = (playable as? FeedMedia)?.item ?: currentitem
//        if (playable is FeedMedia) {
        if (item?.feed?.id == event.feedId) {
            if (event.speed == FeedPreferences.SPEED_USE_GLOBAL) {
                setSpeed(getPlaybackSpeed(playable!!.getMediaType()))
            } else {
                setSpeed(event.speed)
            }
        }
//        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun skipIntroEndingPresetChanged(event: SkipIntroEndingChangedEvent) {
        val item = (playable as? FeedMedia)?.item ?: currentitem
//        if (playable is FeedMedia) {
        if (item?.feed?.id == event.feedId) {
            val feedPreferences = item.feed?.preferences
            if (feedPreferences != null) {
                Log.d(TAG, "skipIntroEndingPresetChanged ${event.skipIntro} ${event.skipEnding}")
                feedPreferences.feedSkipIntro = event.skipIntro
                feedPreferences.feedSkipEnding = event.skipEnding
            }
        }
//        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvenStartPlay(event: StartPlayEvent) {
        Log.d(TAG, "onEvenStartPlay ${event.item.title}")
        currentitem = event.item
    }


    fun resume() {
        mediaPlayer?.resume()
        taskManager.restartSleepTimer()
    }

    fun prepare() {
        mediaPlayer?.prepare()
        taskManager.restartSleepTimer()
    }

    fun pause(abandonAudioFocus: Boolean, reinit: Boolean) {
        mediaPlayer?.pause(abandonAudioFocus, reinit)
        isSpeedForward =  false
        isFallbackSpeed = false
    }

    val pSMPInfo: PSMPInfo
        get() = mediaPlayer!!.pSMPInfo

    val status: PlayerStatus
        get() = mediaPlayer!!.playerStatus

    val playable: Playable?
        get() = mediaPlayer?.getPlayable()

    fun setSpeed(speed: Float, codeArray: BooleanArray? = null) {
        isSpeedForward =  false
        isFallbackSpeed = false

        if (currentMediaType == MediaType.VIDEO) {
            currentlyPlayingTemporaryPlaybackSpeed = speed
            videoPlaybackSpeed = speed
            mediaPlayer?.setPlaybackParams(speed, isSkipSilence)
        } else {
            if (codeArray != null && codeArray.size == 3) {
                Log.d(TAG, "setSpeed codeArray: ${codeArray[0]} ${codeArray[1]} ${codeArray[2]}")
                if (codeArray[2]) setPlaybackSpeed(speed)
                if (codeArray[1]) {
                    var item = (playable as? FeedMedia)?.item ?: currentitem
//                    var item = (playable as FeedMedia).item
                    if (item == null) {
                        val itemId = (playable as? FeedMedia)?.itemId
                        if (itemId != null) item = DBReader.getFeedItem(itemId)
                    }
                    if (item != null) {
                        var feed = item.feed
                        if (feed == null) {
                            feed = DBReader.getFeed(item.feedId)
                        }
                        if (feed != null) {
                            val feedPreferences = feed.preferences
                            if (feedPreferences != null) {
                                feedPreferences.feedPlaybackSpeed = speed
                                Log.d(TAG, "setSpeed ${feed.title} $speed")
                                DBWriter.setFeedPreferences(feedPreferences)
                                EventBus.getDefault().post(
                                    SpeedPresetChangedEvent(feedPreferences.feedPlaybackSpeed, feed.id))
                            }
                        }
                    }
                }
                if (codeArray[0]) {
                    currentlyPlayingTemporaryPlaybackSpeed = speed
                    mediaPlayer?.setPlaybackParams(speed, isSkipSilence)
                }
            } else {
                currentlyPlayingTemporaryPlaybackSpeed = speed
                mediaPlayer?.setPlaybackParams(speed, isSkipSilence)
            }
        }
    }

    fun speedForward(speed: Float) {
        if (mediaPlayer == null || isFallbackSpeed) return

        if (!isSpeedForward) {
            normalSpeed = mediaPlayer!!.getPlaybackSpeed()
            mediaPlayer!!.setPlaybackParams(speed, isSkipSilence)
        } else {
            mediaPlayer!!.setPlaybackParams(normalSpeed, isSkipSilence)
        }
        isSpeedForward = !isSpeedForward
    }

    fun fallbackSpeed(speed: Float) {
        if (mediaPlayer == null || isSpeedForward) return

        if (!isFallbackSpeed) {
            normalSpeed = mediaPlayer!!.getPlaybackSpeed()
            mediaPlayer!!.setPlaybackParams(speed, isSkipSilence)
        } else {
            mediaPlayer!!.setPlaybackParams(normalSpeed, isSkipSilence)
        }
        isFallbackSpeed = !isFallbackSpeed
    }

    fun skipSilence(skipSilence: Boolean) {
        mediaPlayer?.setPlaybackParams(currentPlaybackSpeed, skipSilence)
    }

    val currentPlaybackSpeed: Float
        get() {
            return mediaPlayer?.getPlaybackSpeed() ?: 1.0f
        }

    var isStartWhenPrepared: Boolean
        get() = mediaPlayer?.isStartWhenPrepared() ?: false
        set(s) {
            mediaPlayer?.setStartWhenPrepared(s)
        }

    fun seekTo(t: Int) {
        mediaPlayer?.seekTo(t)
        EventBus.getDefault().post(PlaybackPositionEvent(t, duration))
    }

    private fun seekDelta(d: Int) {
        mediaPlayer?.seekDelta(d)
    }

    val duration: Int
        /**
         * call getDuration() on mediaplayer or return INVALID_TIME if player is in
         * an invalid state.
         */
        get() {
            return mediaPlayer?.getDuration() ?: Playable.INVALID_TIME
        }

    val currentPosition: Int
        /**
         * call getCurrentPosition() on mediaplayer or return INVALID_TIME if player
         * is in an invalid state.
         */
        get() {
            return mediaPlayer?.getPosition() ?: Playable.INVALID_TIME
        }

    val audioTracks: List<String?>
        get() {
            return mediaPlayer?.getAudioTracks() ?: listOf()
        }

    val selectedAudioTrack: Int
        get() {
            return mediaPlayer?.getSelectedAudioTrack() ?: -1
        }

    fun setAudioTrack(track: Int) {
        mediaPlayer?.setAudioTrack(track)
    }

    val isStreaming: Boolean
        get() = mediaPlayer?.isStreaming() ?: false

    val videoSize: Pair<Int, Int>?
        get() = mediaPlayer?.getVideoSize()

    private fun setupPositionObserver() {
        positionEventTimer?.dispose()

        Log.d(TAG, "Setting up position observer")
        positionEventTimer = Observable.interval(POSITION_EVENT_INTERVAL, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                Log.d(TAG, "notificationBuilder.updatePosition currentPosition: $currentPosition, currentPlaybackSpeed: $currentPlaybackSpeed")
                EventBus.getDefault().post(PlaybackPositionEvent(currentPosition, duration))
//                TODO: why set SDK_INT < 29
                if (Build.VERSION.SDK_INT < 29) {
                    notificationBuilder.updatePosition(currentPosition, currentPlaybackSpeed)
                    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
                    notificationManager?.notify(R.id.notification_playing, notificationBuilder.build())
                }
                skipEndingIfNecessary()
            }
    }

    private fun cancelPositionObserver() {
        positionEventTimer?.dispose()
    }

    private fun addPlayableToQueue(playable: Playable?) {
        if (playable is FeedMedia) {
            val itemId = playable.item?.id ?: return
            DBWriter.addQueueItem(this, false, true, itemId)
            notifyChildrenChanged(getString(R.string.queue_label))
        }
    }

    private val sessionCallback: MediaSessionCompat.Callback = object : MediaSessionCompat.Callback() {
        private val TAG = "MediaSessionCompat"

        override fun onPlay() {
            Log.d(TAG, "onPlay()")
            val status: PlayerStatus = this@PlaybackService.status
            when (status) {
                PlayerStatus.PAUSED, PlayerStatus.PREPARED -> {
                    resume()
                }
                PlayerStatus.INITIALIZED -> {
                    this@PlaybackService.isStartWhenPrepared = true
                    prepare()
                }
                else -> {}
            }
        }

        override fun onPlayFromMediaId(mediaId: String, extras: Bundle) {
            Log.d(TAG, "onPlayFromMediaId: mediaId: $mediaId extras: $extras")
            val p = DBReader.getFeedMedia(mediaId.toLong())
            if (p != null) {
                startPlaying(p, false)
            }
        }

        override fun onPlayFromSearch(query: String, extras: Bundle) {
            Log.d(TAG, "onPlayFromSearch  query=$query extras=$extras")

            if (query == "") {
                Log.d(TAG, "onPlayFromSearch called with empty query, resuming from the last position")
                startPlayingFromPreferences()
                return
            }

            val results = FeedSearcher.searchFeedItems(query, 0)
            if (results.isNotEmpty() && results[0].media != null) {
                val media = results[0].media
                startPlaying(media, false)
                return
            }
            onPlay()
        }

        override fun onPause() {
            Log.d(TAG, "onPause()")
            if (this@PlaybackService.status == PlayerStatus.PLAYING) {
                pause(!isPersistNotify, false)
            }
        }

        override fun onStop() {
            Log.d(TAG, "onStop()")
            mediaPlayer?.stopPlayback(true)
        }

        override fun onSkipToPrevious() {
            Log.d(TAG, "onSkipToPrevious()")
            seekDelta(-rewindSecs * 1000)
        }

        override fun onRewind() {
            Log.d(TAG, "onRewind()")
            seekDelta(-rewindSecs * 1000)
        }

        fun onNextChapter() {
            val chapters = mediaPlayer?.getPlayable()?.getChapters() ?: listOf()
            if (chapters.isEmpty()) {
                // No chapters, just fallback to next episode
                mediaPlayer?.skip()
                return
            }

            val nextChapter = getCurrentChapterIndex(mediaPlayer?.getPlayable(), (mediaPlayer?.getPosition()?:0)) + 1

            if (chapters.size < nextChapter + 1) {
                // We are on the last chapter, just fallback to the next episode
                mediaPlayer?.skip()
                return
            }

            mediaPlayer?.seekTo(chapters[nextChapter].start.toInt())
        }

        override fun onFastForward() {
            Log.d(TAG, "onFastForward()")
//            speedForward(2.5f)
            seekDelta(fastForwardSecs * 1000)
        }

        override fun onSkipToNext() {
            Log.d(TAG, "onSkipToNext()")
            val uiModeManager = applicationContext.getSystemService(UI_MODE_SERVICE) as UiModeManager
            if (hardwareForwardButton == KeyEvent.KEYCODE_MEDIA_NEXT
                    || uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_CAR) {
                mediaPlayer?.skip()
            } else {
                seekDelta(fastForwardSecs * 1000)
            }
        }


        override fun onSeekTo(pos: Long) {
            Log.d(TAG, "onSeekTo()")
            seekTo(pos.toInt())
        }

        override fun onSetPlaybackSpeed(speed: Float) {
            Log.d(TAG, "onSetPlaybackSpeed()")
            setSpeed(speed)
        }

        override fun onMediaButtonEvent(mediaButton: Intent): Boolean {
            Log.d(TAG, "onMediaButtonEvent($mediaButton)")
            val keyEvent = mediaButton.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) {
                val keyCode = keyEvent.keyCode
                if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                    clickCount++
                    clickHandler.removeCallbacksAndMessages(null)
                    clickHandler.postDelayed({
                        when (clickCount) {
                            1 -> {
                                handleKeycode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, false)
                            }
                            2 -> {
                                onFastForward()
                            }
                            3 -> {
                                onRewind()
                            }
                        }
                        clickCount = 0
                    }, ViewConfiguration.getDoubleTapTimeout().toLong())
                    return true
                } else {
                    return handleKeycode(keyCode, false)
                }
            }
            return false
        }

        override fun onCustomAction(action: String, extra: Bundle) {
            Log.d(TAG, "onCustomAction($action)")
            when (action) {
                CUSTOM_ACTION_FAST_FORWARD -> {
                    onFastForward()
                }
                CUSTOM_ACTION_REWIND -> {
                    onRewind()
                }
                CUSTOM_ACTION_SKIP_TO_NEXT -> {
                    mediaPlayer?.skip()
                }
                CUSTOM_ACTION_NEXT_CHAPTER -> {
                    onNextChapter()
                }
                CUSTOM_ACTION_CHANGE_PLAYBACK_SPEED -> {
                    val selectedSpeeds = playbackSpeedArray

                    // If the list has zero or one element, there's nothing we can do to change the playback speed.
                    if (selectedSpeeds.size > 1) {
                        val speedPosition = selectedSpeeds.indexOf(mediaPlayer?.getPlaybackSpeed()?:0f)

                        val newSpeed = if (speedPosition == selectedSpeeds.size - 1) {
                            // This is the last element. Wrap instead of going over the size of the list.
                            selectedSpeeds[0]
                        } else {
                            // If speedPosition is still -1 (the user isn't using a preset), use the first preset in the
                            // list.
                            selectedSpeeds[speedPosition + 1]
                        }
                        onSetPlaybackSpeed(newSpeed)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "PlaybackService"

//        TODO: need to experiment this value
        private const val POSITION_EVENT_INTERVAL = 5L

        const val ACTION_PLAYER_STATUS_CHANGED: String = "action.ac.mdiq.podcini.service.playerStatusChanged"
        private const val AVRCP_ACTION_PLAYER_STATUS_CHANGED = "com.android.music.playstatechanged"
        private const val AVRCP_ACTION_META_CHANGED = "com.android.music.metachanged"

        /**
         * Custom actions used by Android Wear, Android Auto, and Android (API 33+ only)
         */
        private const val CUSTOM_ACTION_SKIP_TO_NEXT = "action.ac.mdiq.podcini.service.skipToNext"
        private const val CUSTOM_ACTION_FAST_FORWARD = "action.ac.mdiq.podcini.service.fastForward"
        private const val CUSTOM_ACTION_REWIND = "action.ac.mdiq.podcini.service.rewind"
        private const val CUSTOM_ACTION_CHANGE_PLAYBACK_SPEED =
            "action.ac.mdiq.podcini.service.changePlaybackSpeed"
        const val CUSTOM_ACTION_NEXT_CHAPTER: String = "action.ac.mdiq.podcini.service.next_chapter"

        /**
         * Set a max number of episodes to load for Android Auto, otherwise there could be performance issues
         */
        const val MAX_ANDROID_AUTO_EPISODES_PER_FEED: Int = 100

        /**
         * Is true if service is running.
         */
        @JvmField
        var isRunning: Boolean = false

        /**
         * Is true if the service was running, but paused due to headphone disconnect
         */
        private var transientPause = false

        /**
         * Is true if a Cast Device is connected to the service.
         */
        @JvmStatic
        @Volatile
        var isCasting: Boolean = false
            private set

        @Volatile
        var currentMediaType: MediaType? = MediaType.UNKNOWN
            private set

        /**
         * Returns an intent which starts an audio- or videoplayer, depending on the
         * type of media that is being played. If the playbackservice is not
         * running, the type of the last played media will be looked up.
         */
        @JvmStatic
        fun getPlayerActivityIntent(context: Context): Intent {
            val showVideoPlayer = if (isRunning) {
                currentMediaType == MediaType.VIDEO && !isCasting
            } else {
                currentEpisodeIsVideo
            }

            return if (showVideoPlayer) {
                VideoPlayerActivityStarter(context).intent
            } else {
                MainActivityStarter(context).withOpenPlayer().getIntent()
            }
        }

        /**
         * Same as [.getPlayerActivityIntent], but here the type of activity
         * depends on the FeedMedia that is provided as an argument.
         */
        @JvmStatic
        fun getPlayerActivityIntent(context: Context, media: Playable): Intent {
            return if (media.getMediaType() == MediaType.VIDEO && !isCasting) {
                VideoPlayerActivityStarter(context).intent
            } else {
                MainActivityStarter(context).withOpenPlayer().getIntent()
            }
        }
    }
}
