package de.danoeh.antennapod.core.service.playback

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
import de.danoeh.antennapod.core.R
import de.danoeh.antennapod.core.preferences.PlaybackPreferences
import de.danoeh.antennapod.core.preferences.PlaybackPreferences.Companion.clearCurrentlyPlayingTemporaryPlaybackSpeed
import de.danoeh.antennapod.core.preferences.PlaybackPreferences.Companion.createInstanceFromPreferences
import de.danoeh.antennapod.core.preferences.PlaybackPreferences.Companion.currentEpisodeIsVideo
import de.danoeh.antennapod.core.preferences.PlaybackPreferences.Companion.currentPlayerStatus
import de.danoeh.antennapod.core.preferences.PlaybackPreferences.Companion.currentlyPlayingFeedMediaId
import de.danoeh.antennapod.core.preferences.PlaybackPreferences.Companion.currentlyPlayingTemporaryPlaybackSpeed
import de.danoeh.antennapod.core.preferences.PlaybackPreferences.Companion.writeMediaPlaying
import de.danoeh.antennapod.core.preferences.PlaybackPreferences.Companion.writeNoMediaPlaying
import de.danoeh.antennapod.core.preferences.PlaybackPreferences.Companion.writePlayerStatus
import de.danoeh.antennapod.core.preferences.SleepTimerPreferences.autoEnable
import de.danoeh.antennapod.core.preferences.SleepTimerPreferences.autoEnableFrom
import de.danoeh.antennapod.core.preferences.SleepTimerPreferences.autoEnableTo
import de.danoeh.antennapod.core.preferences.SleepTimerPreferences.isInTimeRange
import de.danoeh.antennapod.core.preferences.SleepTimerPreferences.timerMillis
import de.danoeh.antennapod.core.receiver.MediaButtonReceiver
import de.danoeh.antennapod.core.service.QuickSettingsTileService
import de.danoeh.antennapod.core.service.playback.PlaybackServiceTaskManager.PSTMCallback
import de.danoeh.antennapod.core.storage.DBReader
import de.danoeh.antennapod.core.storage.DBWriter
import de.danoeh.antennapod.core.storage.FeedSearcher
import de.danoeh.antennapod.core.sync.queue.SynchronizationQueueSink
import de.danoeh.antennapod.core.util.ChapterUtils.getCurrentChapterIndex
import de.danoeh.antennapod.core.util.FeedItemUtil.hasAlmostEnded
import de.danoeh.antennapod.core.util.FeedUtil.shouldAutoDeleteItemsOnThatFeed
import de.danoeh.antennapod.core.util.IntentUtils.sendLocalBroadcast
import de.danoeh.antennapod.core.util.NetworkUtils.isStreamingAllowed
import de.danoeh.antennapod.core.util.gui.NotificationUtils
import de.danoeh.antennapod.core.util.playback.PlayableUtils.saveCurrentPosition
import de.danoeh.antennapod.core.util.playback.PlaybackServiceStarter
import de.danoeh.antennapod.core.widget.WidgetUpdater.WidgetState
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.event.PlayerErrorEvent
import de.danoeh.antennapod.event.playback.BufferUpdateEvent
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.event.playback.PlaybackServiceEvent
import de.danoeh.antennapod.event.playback.SleepTimerUpdatedEvent
import de.danoeh.antennapod.event.settings.SkipIntroEndingChangedEvent
import de.danoeh.antennapod.event.settings.SpeedPresetChangedEvent
import de.danoeh.antennapod.event.settings.VolumeAdaptionChangedEvent
import de.danoeh.antennapod.model.feed.*
import de.danoeh.antennapod.model.feed.FeedPreferences.AutoDeleteAction
import de.danoeh.antennapod.model.playback.MediaType
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer.PSMPCallback
import de.danoeh.antennapod.playback.base.PlaybackServiceMediaPlayer.PSMPInfo
import de.danoeh.antennapod.playback.base.PlayerStatus
import de.danoeh.antennapod.playback.cast.CastPsmp.getInstanceIfConnected
import de.danoeh.antennapod.playback.cast.CastStateListener
import de.danoeh.antennapod.storage.preferences.UserPreferences.allEpisodesSortOrder
import de.danoeh.antennapod.storage.preferences.UserPreferences.downloadsSortedOrder
import de.danoeh.antennapod.storage.preferences.UserPreferences.fastForwardSecs
import de.danoeh.antennapod.storage.preferences.UserPreferences.getPlaybackSpeed
import de.danoeh.antennapod.storage.preferences.UserPreferences.hardwareForwardButton
import de.danoeh.antennapod.storage.preferences.UserPreferences.hardwarePreviousButton
import de.danoeh.antennapod.storage.preferences.UserPreferences.isAllowMobileStreaming
import de.danoeh.antennapod.storage.preferences.UserPreferences.isFollowQueue
import de.danoeh.antennapod.storage.preferences.UserPreferences.isPauseOnHeadsetDisconnect
import de.danoeh.antennapod.storage.preferences.UserPreferences.isPersistNotify
import de.danoeh.antennapod.storage.preferences.UserPreferences.isSkipSilence
import de.danoeh.antennapod.storage.preferences.UserPreferences.isUnpauseOnBluetoothReconnect
import de.danoeh.antennapod.storage.preferences.UserPreferences.isUnpauseOnHeadsetReconnect
import de.danoeh.antennapod.storage.preferences.UserPreferences.playbackSpeedArray
import de.danoeh.antennapod.storage.preferences.UserPreferences.rewindSecs
import de.danoeh.antennapod.storage.preferences.UserPreferences.setPlaybackSpeed
import de.danoeh.antennapod.storage.preferences.UserPreferences.shouldFavoriteKeepEpisode
import de.danoeh.antennapod.storage.preferences.UserPreferences.shouldSkipKeepEpisode
import de.danoeh.antennapod.storage.preferences.UserPreferences.showNextChapterOnFullNotification
import de.danoeh.antennapod.storage.preferences.UserPreferences.showPlaybackSpeedOnFullNotification
import de.danoeh.antennapod.storage.preferences.UserPreferences.showSkipOnFullNotification
import de.danoeh.antennapod.storage.preferences.UserPreferences.videoPlaybackSpeed
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.appstartintent.VideoPlayerActivityStarter
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
    private var taskManager: PlaybackServiceTaskManager? = null
    private var stateManager: PlaybackServiceStateManager? = null
    private var positionEventTimer: Disposable? = null
    private var notificationBuilder: PlaybackServiceNotificationBuilder? = null
    private var castStateListener: CastStateListener? = null

    private var autoSkippedFeedMediaId: String? = null
    private var clickCount = 0
    private val clickHandler = Handler(Looper.getMainLooper())

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

        registerReceiver(autoStateUpdated, IntentFilter("com.google.android.gms.car.media.STATUS"))
        registerReceiver(headsetDisconnected, IntentFilter(Intent.ACTION_HEADSET_PLUG))
        registerReceiver(shutdownReceiver, IntentFilter(PlaybackServiceInterface.ACTION_SHUTDOWN_PLAYBACK_SERVICE))
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
            wasPlaying = mediaPlayer!!.playerStatus == PlayerStatus.PLAYING
            mediaPlayer!!.pause(true, false)
            mediaPlayer!!.shutdown()
        }
        mediaPlayer = getInstanceIfConnected(this, mediaPlayerCallback)
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

        if (notificationBuilder!!.playerStatus == PlayerStatus.PLAYING) {
            notificationBuilder!!.playerStatus = PlayerStatus.STOPPED
            val notificationManager = NotificationManagerCompat.from(this)
            if (ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            notificationManager.notify(R.id.notification_playing, notificationBuilder!!.build())
        }
        stateManager!!.stopForeground(!isPersistNotify)
        isRunning = false
        currentMediaType = MediaType.UNKNOWN
        castStateListener!!.destroy()

        cancelPositionObserver()
        if (mediaSession != null) {
            mediaSession!!.release()
            mediaSession = null
        }
        unregisterReceiver(autoStateUpdated)
        unregisterReceiver(headsetDisconnected)
        unregisterReceiver(shutdownReceiver)
        unregisterReceiver(bluetoothStateUpdated)
        unregisterReceiver(audioBecomingNoisy)
        mediaPlayer!!.shutdown()
        taskManager!!.shutdown()
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
        if (parentId == resources.getString(R.string.queue_label)) {
            feedItems = DBReader.getQueue()
        } else if (parentId == resources.getString(R.string.downloads_label)) {
            feedItems = DBReader.getEpisodes(0, MAX_ANDROID_AUTO_EPISODES_PER_FEED,
                FeedItemFilter(FeedItemFilter.DOWNLOADED), downloadsSortedOrder)
        } else if (parentId == resources.getString(R.string.episodes_label)) {
            feedItems = DBReader.getEpisodes(0, MAX_ANDROID_AUTO_EPISODES_PER_FEED,
                FeedItemFilter(FeedItemFilter.UNPLAYED), allEpisodesSortOrder)
        } else if (parentId.startsWith("FeedId:")) {
            val feedId = parentId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1].toLong()
            val feed = DBReader.getFeed(feedId)
            feedItems = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(), feed!!.sortOrder)
        } else if (parentId == getString(R.string.current_playing_episode)) {
            val playable = createInstanceFromPreferences(this)
            if (playable is FeedMedia) {
                feedItems = listOf(playable.getItem())
            } else {
                return null
            }
        } else {
            Log.e(TAG, "Parent ID not found: $parentId")
            return null
        }
        var count = 0
        for (feedItem in feedItems) {
            if (feedItem!!.media != null && feedItem.media!!.mediaItem != null) {
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
        return if (intent.action != null && TextUtils.equals(intent.action,
                    SERVICE_INTERFACE)) {
            super.onBind(intent)
        } else {
            mBinder
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "OnStartCommand called")

        stateManager!!.startForeground(R.id.notification_playing, notificationBuilder!!.build())
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.cancel(R.id.notification_streaming_confirmation)

        val keycode = intent.getIntExtra(MediaButtonReceiver.EXTRA_KEYCODE, -1)
        val customAction = intent.getStringExtra(MediaButtonReceiver.EXTRA_CUSTOM_ACTION)
        val hardwareButton = intent.getBooleanExtra(MediaButtonReceiver.EXTRA_HARDWAREBUTTON, false)
        val playable = intent.getParcelableExtra<Playable>(PlaybackServiceInterface.EXTRA_PLAYABLE)
        if (keycode == -1 && playable == null && customAction == null) {
            Log.e(TAG, "PlaybackService was started with no arguments")
            stateManager!!.stopService()
            return START_NOT_STICKY
        }

        if ((flags and START_FLAG_REDELIVERY) != 0) {
            Log.d(TAG, "onStartCommand is a redelivered intent, calling stopForeground now.")
            stateManager!!.stopForeground(true)
        } else {
            if (keycode != -1) {
                val notificationButton: Boolean
                if (hardwareButton) {
                    Log.d(TAG, "Received hardware button event")
                    notificationButton = false
                } else {
                    Log.d(TAG, "Received media button event")
                    notificationButton = true
                }
                val handled = handleKeycode(keycode, notificationButton)
                if (!handled && !stateManager!!.hasReceivedValidStartCommand()) {
                    stateManager!!.stopService()
                    return START_NOT_STICKY
                }
            } else if (playable != null) {
                stateManager!!.validStartCommandWasReceived()
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
                            stateManager!!.stopService()
                        })
                return START_NOT_STICKY
            } else {
                mediaSession!!.controller.transportControls.sendCustomAction(customAction, null)
            }
        }

        return START_NOT_STICKY
    }

    private fun skipIntro(playable: Playable) {
        if (playable !is FeedMedia) {
            return
        }

        val preferences = playable.getItem()!!.feed?.preferences
        val skipIntro = preferences?.feedSkipIntro ?: 0

        val context = applicationContext
        if (skipIntro > 0 && playable.getPosition() < skipIntro * 1000) {
            val duration = duration
            if (skipIntro * 1000 < duration || duration <= 0) {
                Log.d(TAG, "skipIntro " + playable.getEpisodeTitle())
                mediaPlayer!!.seekTo(skipIntro * 1000)
                val skipIntroMesg = context.getString(R.string.pref_feed_skip_intro_toast,
                    skipIntro)
                val toast = Toast.makeText(context, skipIntroMesg,
                    Toast.LENGTH_LONG)
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
        val pendingIntentAllowThisTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this,
                R.id.pending_intent_allow_stream_this_time, intentAllowThisTime,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(this,
                R.id.pending_intent_allow_stream_this_time, intentAllowThisTime, PendingIntent.FLAG_UPDATE_CURRENT
                        or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        }

        val intentAlwaysAllow = Intent(intentAllowThisTime)
        intentAlwaysAllow.setAction(PlaybackServiceInterface.EXTRA_ALLOW_STREAM_ALWAYS)
        intentAlwaysAllow.putExtra(PlaybackServiceInterface.EXTRA_ALLOW_STREAM_ALWAYS, true)
        val pendingIntentAlwaysAllow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this,
                R.id.pending_intent_allow_stream_always, intentAlwaysAllow,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(this,
                R.id.pending_intent_allow_stream_always, intentAlwaysAllow, PendingIntent.FLAG_UPDATE_CURRENT
                        or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
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
        if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
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
        val info = mediaPlayer!!.pSMPInfo
        val status = info.playerStatus
        when (keycode) {
            KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (status == PlayerStatus.PLAYING) {
                    mediaPlayer!!.pause(!isPersistNotify, false)
                } else if (status == PlayerStatus.PAUSED || status == PlayerStatus.PREPARED) {
                    mediaPlayer!!.resume()
                } else if (status == PlayerStatus.PREPARING) {
                    mediaPlayer!!.setStartWhenPrepared(!mediaPlayer!!.isStartWhenPrepared())
                } else if (status == PlayerStatus.INITIALIZED) {
                    mediaPlayer!!.setStartWhenPrepared(true)
                    mediaPlayer!!.prepare()
                } else if (mediaPlayer!!.getPlayable() == null) {
                    startPlayingFromPreferences()
                } else {
                    return false
                }
                taskManager!!.restartSleepTimer()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (status == PlayerStatus.PAUSED || status == PlayerStatus.PREPARED) {
                    mediaPlayer!!.resume()
                } else if (status == PlayerStatus.INITIALIZED) {
                    mediaPlayer!!.setStartWhenPrepared(true)
                    mediaPlayer!!.prepare()
                } else if (mediaPlayer!!.getPlayable() == null) {
                    startPlayingFromPreferences()
                } else {
                    return false
                }
                taskManager!!.restartSleepTimer()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (status == PlayerStatus.PLAYING) {
                    mediaPlayer!!.pause(!isPersistNotify, false)
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (!notificationButton) {
                    // Handle remapped button as notification button which is not remapped again.
                    return handleKeycode(hardwareForwardButton, true)
                } else if (this.status == PlayerStatus.PLAYING || this.status == PlayerStatus.PAUSED) {
                    mediaPlayer!!.skip()
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (this.status == PlayerStatus.PLAYING || this.status == PlayerStatus.PAUSED) {
                    mediaPlayer!!.seekDelta(fastForwardSecs * 1000)
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (!notificationButton) {
                    // Handle remapped button as notification button which is not remapped again.
                    return handleKeycode(hardwarePreviousButton, true)
                } else if (this.status == PlayerStatus.PLAYING || this.status == PlayerStatus.PAUSED) {
                    mediaPlayer!!.seekTo(0)
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (this.status == PlayerStatus.PLAYING || this.status == PlayerStatus.PAUSED) {
                    mediaPlayer!!.seekDelta(-rewindSecs * 1000)
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                if (status == PlayerStatus.PLAYING) {
                    mediaPlayer!!.pause(true, true)
                }

                stateManager!!.stopForeground(true) // gets rid of persistent notification
                return true
            }
            else -> {
                Log.d(TAG, "Unhandled key code: $keycode")
                if (info.playable != null && info.playerStatus == PlayerStatus.PLAYING) {   // only notify the user about an unknown key event if it is actually doing something
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
                    stateManager!!.stopService()
                })
    }

    private fun startPlaying(playable: Playable?, allowStreamThisTime: Boolean) {
        val localFeed = URLUtil.isContentUrl(playable!!.getStreamUrl())
        val stream = !playable.localFileAvailable() || localFeed
        if (stream && !localFeed && !isStreamingAllowed && !allowStreamThisTime) {
            displayStreamingNotAllowedNotification(
                PlaybackServiceStarter(this, playable)
                    .intent)
            writeNoMediaPlaying()
            stateManager!!.stopService()
            return
        }

        if (playable.getIdentifier() != currentlyPlayingFeedMediaId) {
            clearCurrentlyPlayingTemporaryPlaybackSpeed()
        }

        mediaPlayer!!.playMediaObject(playable, stream, true, true)
        stateManager!!.validStartCommandWasReceived()
        stateManager!!.startForeground(R.id.notification_playing, notificationBuilder!!.build())
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
        mediaPlayer!!.setVideoSurface(sh)
    }

    fun notifyVideoSurfaceAbandoned() {
        mediaPlayer!!.pause(true, false)
        mediaPlayer!!.resetVideoSurface()
        updateNotificationAndMediaSession(playable)
        stateManager!!.stopForeground(!isPersistNotify)
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
            updateMediaSession(mediaPlayer!!.playerStatus)
        }
    }

    private val mediaPlayerCallback: PSMPCallback = object : PSMPCallback {
        override fun statusChanged(newInfo: PSMPInfo?) {
            if (mediaPlayer != null) {
                currentMediaType = mediaPlayer!!.getCurrentMediaType()
            } else {
                currentMediaType = MediaType.UNKNOWN
            }

            updateMediaSession(newInfo!!.playerStatus)
            when (newInfo.playerStatus) {
                PlayerStatus.INITIALIZED -> {
                    if (mediaPlayer!!.pSMPInfo.playable != null) {
                        writeMediaPlaying(mediaPlayer!!.pSMPInfo.playable,
                            mediaPlayer!!.pSMPInfo.playerStatus)
                    }
                    updateNotificationAndMediaSession(newInfo.playable)
                }
                PlayerStatus.PREPARED -> {
                    if (mediaPlayer!!.pSMPInfo.playable != null) {
                        writeMediaPlaying(mediaPlayer!!.pSMPInfo.playable,
                            mediaPlayer!!.pSMPInfo.playerStatus)
                    }
                    taskManager!!.startChapterLoader(newInfo.playable!!)
                }
                PlayerStatus.PAUSED -> {
                    updateNotificationAndMediaSession(newInfo.playable)
                    if (!isCasting) {
                        stateManager!!.stopForeground(!isPersistNotify)
                    }
                    cancelPositionObserver()
                    writePlayerStatus(mediaPlayer!!.playerStatus)
                }
                PlayerStatus.STOPPED -> {}
                PlayerStatus.PLAYING -> {
                    writePlayerStatus(mediaPlayer!!.playerStatus)
                    saveCurrentPosition(true, null, Playable.INVALID_TIME)
                    recreateMediaSessionIfNeeded()
                    updateNotificationAndMediaSession(newInfo.playable)
                    setupPositionObserver()
                    stateManager!!.validStartCommandWasReceived()
                    stateManager!!.startForeground(R.id.notification_playing, notificationBuilder!!.build())
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
                        EventBus.getDefault().post(MessageEvent(getString(R.string.sleep_timer_enabled_label),
                            { ctx: Context? -> disableSleepTimer() }, getString(R.string.undo)))
                    }
                    loadQueueForMediaSession()
                }
                PlayerStatus.ERROR -> {
                    writeNoMediaPlaying()
                    stateManager!!.stopService()
                }
                else -> {}
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                TileService.requestListeningState(applicationContext,
                    ComponentName(applicationContext, QuickSettingsTileService::class.java))
            }

            sendLocalBroadcast(applicationContext, ACTION_PLAYER_STATUS_CHANGED)
            bluetoothNotifyChange(newInfo, AVRCP_ACTION_PLAYER_STATUS_CHANGED)
            bluetoothNotifyChange(newInfo, AVRCP_ACTION_META_CHANGED)
            taskManager!!.requestWidgetUpdate()
        }

        override fun shouldStop() {
            stateManager!!.stopForeground(!isPersistNotify)
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
            taskManager!!.startWidgetUpdater()
            if (position != Playable.INVALID_TIME) {
                playable.setPosition(position)
            } else {
                skipIntro(playable)
            }
            playable.onPlaybackStart()
            taskManager!!.startPositionSaver()
        }

        override fun onPlaybackPause(playable: Playable?, position: Int) {
            taskManager!!.cancelPositionSaver()
            cancelPositionObserver()
            saveCurrentPosition(position == Playable.INVALID_TIME || playable == null, playable, position)
            taskManager!!.cancelWidgetUpdater()
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
            if (media is FeedMedia && media.getItem() == null) {
                media.setItem(DBReader.getFeedItem(media.itemId))
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun playerError(event: PlayerErrorEvent?) {
        if (mediaPlayer!!.playerStatus == PlayerStatus.PLAYING) {
            mediaPlayer!!.pause(true, false)
        }
        stateManager!!.stopService()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun bufferUpdate(event: BufferUpdateEvent) {
        if (event.hasEnded()) {
            val playable = playable
            if (this.playable is FeedMedia && playable!!.getDuration() <= 0 && mediaPlayer!!.getDuration() > 0) {
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
        if (event.isOver) {
            mediaPlayer!!.pause(true, true)
            mediaPlayer!!.setVolume(1.0f, 1.0f)
        } else if (event.getTimeLeft() < PlaybackServiceTaskManager.NOTIFICATION_THRESHOLD) {
            val multiplicators = floatArrayOf(0.1f, 0.2f, 0.3f, 0.3f, 0.3f, 0.4f, 0.4f, 0.4f, 0.6f, 0.8f)
            val multiplicator = multiplicators[max(0.0, (event.getTimeLeft().toInt() / 1000).toDouble())
                .toInt()]
            Log.d(TAG, "onSleepTimerAlmostExpired: $multiplicator")
            mediaPlayer!!.setVolume(multiplicator, multiplicator)
        } else if (event.isCancelled) {
            mediaPlayer!!.setVolume(1.0f, 1.0f)
        }
    }

    private fun getNextInQueue(currentMedia: Playable?): Playable? {
        if (currentMedia !is FeedMedia) {
            Log.d(TAG, "getNextInQueue(), but playable not an instance of FeedMedia, so not proceeding")
            writeNoMediaPlaying()
            return null
        }
        Log.d(TAG, "getNextInQueue()")
        val media = currentMedia
        if (media.getItem() == null) {
            media.setItem(DBReader.getFeedItem(media.itemId))
        }
        val item = media.getItem()
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
            writeMediaPlaying(nextItem.media, PlayerStatus.STOPPED)
            updateNotificationAndMediaSession(nextItem.media)
            return null
        }

        if (!nextItem.media!!.localFileAvailable() && !isStreamingAllowed && isFollowQueue && nextItem.feed != null && !nextItem.feed!!.isLocalFeed) {
            displayStreamingNotAllowedNotification(PlaybackServiceStarter(this, nextItem.media!!).intent)
            writeNoMediaPlaying()
            stateManager!!.stopService()
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
            taskManager!!.cancelPositionSaver()
            cancelPositionObserver()
            if (!isCasting) {
                stateManager!!.stopForeground(true)
                stateManager!!.stopService()
            }
        }
        if (mediaType == null) {
            sendNotificationBroadcast(PlaybackServiceInterface.NOTIFICATION_TYPE_PLAYBACK_END, 0)
        } else {
            sendNotificationBroadcast(PlaybackServiceInterface.NOTIFICATION_TYPE_RELOAD,
                if (isCasting) PlaybackServiceInterface.EXTRA_CODE_CAST else if ((mediaType == MediaType.VIDEO)) PlaybackServiceInterface.EXTRA_CODE_VIDEO else PlaybackServiceInterface.EXTRA_CODE_AUDIO)
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
    private fun onPostPlayback(playable: Playable?, ended: Boolean, skipped: Boolean,
                               playingNext: Boolean
    ) {
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
            return
        }
        val media = playable
        val item = media.getItem()
        val smartMarkAsPlayed = hasAlmostEnded(media)
        if (!ended && smartMarkAsPlayed) {
            Log.d(TAG, "smart mark as played")
        }

        var autoSkipped = false
        if (autoSkippedFeedMediaId != null && autoSkippedFeedMediaId == item!!.identifyingValue) {
            autoSkippedFeedMediaId = null
            autoSkipped = true
        }

        if (ended || smartMarkAsPlayed) {
            SynchronizationQueueSink.enqueueEpisodePlayedIfSynchronizationIsActive(
                applicationContext, media, true)
            media.onPlaybackCompleted(applicationContext)
        } else {
            SynchronizationQueueSink.enqueueEpisodePlayedIfSynchronizationIsActive(
                applicationContext, media, false)
            media.onPlaybackPause(applicationContext)
        }

        if (item != null) {
            if (ended || smartMarkAsPlayed
                    || autoSkipped
                    || (skipped && !shouldSkipKeepEpisode())) {
                // only mark the item as played if we're not keeping it anyways
                DBWriter.markItemPlayed(item, FeedItem.PLAYED, ended || (skipped && smartMarkAsPlayed))
                // don't know if it actually matters to not autodownload when smart mark as played is triggered
                DBWriter.removeQueueItem(this@PlaybackService, ended, item)
                // Delete episode if enabled
                val action = item.feed?.preferences?.currentAutoDelete
                val shouldAutoDelete = (action == AutoDeleteAction.ALWAYS
                        || (action == AutoDeleteAction.GLOBAL && item.feed != null && shouldAutoDeleteItemsOnThatFeed(item.feed!!)))
                if (shouldAutoDelete && (!item.isTagged(FeedItem.TAG_FAVORITE)
                                || !shouldFavoriteKeepEpisode())) {
                    DBWriter.deleteFeedMediaOfItem(this@PlaybackService, media.id)
                    Log.d(TAG, "Episode Deleted")
                }
                notifyChildrenChanged(getString(R.string.queue_label))
            }
        }

        if (ended || skipped || playingNext) {
            DBWriter.addItemToPlaybackHistory(media)
        }
    }

    fun setSleepTimer(waitingTime: Long) {
        Log.d(TAG, "Setting sleep timer to $waitingTime milliseconds")
        taskManager!!.setSleepTimer(waitingTime)
    }

    fun disableSleepTimer() {
        taskManager!!.disableSleepTimer()
    }

    private fun sendNotificationBroadcast(type: Int, code: Int) {
        val intent = Intent(PlaybackServiceInterface.ACTION_PLAYER_NOTIFICATION)
        intent.putExtra(PlaybackServiceInterface.EXTRA_NOTIFICATION_TYPE, type)
        intent.putExtra(PlaybackServiceInterface.EXTRA_NOTIFICATION_CODE, code)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun skipEndingIfNecessary() {
        val playable = mediaPlayer!!.getPlayable() as? FeedMedia ?: return

        val duration = duration
        val remainingTime = duration - currentPosition

        val feedMedia = playable
        val preferences = feedMedia.getItem()?.feed?.preferences
        val skipEnd = preferences?.feedSkipEnding
        if (skipEnd != null && skipEnd > 0 && skipEnd * 1000 < this.duration && (remainingTime - (skipEnd * 1000) > 0)
                && ((remainingTime - skipEnd * 1000) < (currentPlaybackSpeed * 1000))) {
            Log.d(TAG,
                "skipEndingIfNecessary: Skipping the remaining " + remainingTime + " " + skipEnd * 1000 + " speed " + currentPlaybackSpeed)
            val context = applicationContext
            val skipMesg = context.getString(R.string.pref_feed_skip_ending_toast, skipEnd)
            val toast = Toast.makeText(context, skipMesg, Toast.LENGTH_LONG)
            toast.show()

            this.autoSkippedFeedMediaId = feedMedia.getItem()!!.identifyingValue
            mediaPlayer!!.skip()
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
                PlayerStatus.PREPARED, PlayerStatus.PAUSED -> PlaybackStateCompat.STATE_PAUSED
                PlayerStatus.STOPPED -> PlaybackStateCompat.STATE_STOPPED
                PlayerStatus.SEEKING -> PlaybackStateCompat.STATE_FAST_FORWARDING
                PlayerStatus.PREPARING, PlayerStatus.INITIALIZING -> PlaybackStateCompat.STATE_CONNECTING
                PlayerStatus.ERROR -> PlaybackStateCompat.STATE_ERROR
                PlayerStatus.INITIALIZED, PlayerStatus.INDETERMINATE -> PlaybackStateCompat.STATE_NONE
                else -> PlaybackStateCompat.STATE_NONE
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
            if (playable != null && playable!!.getChapters().isNotEmpty()) {
                sessionState.addCustomAction(
                    PlaybackStateCompat.CustomAction.Builder(
                        CUSTOM_ACTION_NEXT_CHAPTER,
                        getString(R.string.next_chapter), R.drawable.ic_notification_next_chapter)
                        .build())
            }
        }

        if (showSkipOnFullNotification()) {
            sessionState.addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    CUSTOM_ACTION_SKIP_TO_NEXT,
                    getString(R.string.skip_episode_label),
                    R.drawable.ic_notification_skip
                ).build()
            )
        }

        WearMediaSession.mediaSessionSetExtraForWear(mediaSession)

        mediaSession!!.setPlaybackState(sessionState.build())
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


        if (notificationBuilder!!.isIconCached) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, notificationBuilder!!.cachedIcon)
        } else {
            var iconUri = p.getImageLocation()
            if (p is FeedMedia) { // Don't use embedded cover etc, which Android can't load
                val m = p
                if (m.getItem() != null) {
                    val item = m.getItem()
                    if (item!!.imageUrl != null) {
                        iconUri = item.imageUrl
                    } else if (item.feed != null) {
                        iconUri = item.feed!!.imageUrl
                    }
                }
            }
            if (!TextUtils.isEmpty(iconUri)) {
                builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, iconUri)
            }
        }

        if (stateManager!!.hasReceivedValidStartCommand()) {
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
        if (playableIconLoaderThread != null) {
            playableIconLoaderThread!!.interrupt()
        }
        if (playable == null || mediaPlayer == null) {
            Log.d(TAG, "setupNotification: playable=$playable")
            Log.d(TAG, "setupNotification: mediaPlayer=$mediaPlayer")
            if (!stateManager!!.hasReceivedValidStartCommand()) {
                stateManager!!.stopService()
            }
            return
        }

        val playerStatus = mediaPlayer!!.playerStatus
        notificationBuilder!!.setPlayable(playable)
        notificationBuilder!!.setMediaSessionToken(mediaSession!!.sessionToken)
        notificationBuilder!!.playerStatus = playerStatus
        notificationBuilder!!.updatePosition(currentPosition, currentPlaybackSpeed)

        val notificationManager = NotificationManagerCompat.from(this)
        if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        notificationManager.notify(R.id.notification_playing, notificationBuilder!!.build())

        if (!notificationBuilder!!.isIconCached) {
            playableIconLoaderThread = Thread {
                Log.d(TAG, "Loading notification icon")
                notificationBuilder!!.loadIcon()
                if (!Thread.currentThread().isInterrupted) {
                    notificationManager.notify(R.id.notification_playing, notificationBuilder!!.build())
                    updateMediaSessionMetadata(playable)
                }
            }
            playableIconLoaderThread!!.start()
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
            playable = mediaPlayer!!.getPlayable()
        } else {
            duration = playable!!.getDuration()
        }
        if (position != Playable.INVALID_TIME && duration != Playable.INVALID_TIME && playable != null) {
            Log.d(TAG, "Saving current position to $position")
            saveCurrentPosition(playable, position, System.currentTimeMillis())
        }
    }

    fun sleepTimerActive(): Boolean {
        return taskManager!!.isSleepTimerActive
    }

    val sleepTimerTimeLeft: Long
        get() = taskManager!!.sleepTimerTimeLeft

    private fun bluetoothNotifyChange(info: PSMPInfo?, whatChanged: String) {
        var isPlaying = false

        if (info!!.playerStatus == PlayerStatus.PLAYING) {
            isPlaying = true
        }

        if (info.playable != null) {
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
                val playerStatus = mediaPlayer!!.playerStatus
                when (playerStatus) {
                    PlayerStatus.PAUSED, PlayerStatus.PREPARED -> {
                        mediaPlayer!!.resume()
                    }
                    PlayerStatus.PREPARING -> {
                        mediaPlayer!!.setStartWhenPrepared(!mediaPlayer!!.isStartWhenPrepared())
                    }
                    PlayerStatus.INITIALIZED -> {
                        mediaPlayer!!.setStartWhenPrepared(true)
                        mediaPlayer!!.prepare()
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
                    if (state == UNPLUGGED) {
                        Log.d(TAG, "Headset was unplugged during playback.")
                    } else if (state == PLUGGED) {
                        Log.d(TAG, "Headset was plugged in during playback.")
                        unpauseIfPauseOnDisconnect(false)
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
        transientPause = (mediaPlayer!!.playerStatus == PlayerStatus.PLAYING)
        if (isPauseOnHeadsetDisconnect && !isCasting) {
            mediaPlayer!!.pause(!isPersistNotify, false)
        }
    }

    /**
     * @param bluetooth true if the event for unpausing came from bluetooth
     */
    private fun unpauseIfPauseOnDisconnect(bluetooth: Boolean) {
        if (mediaPlayer!!.isAudioChannelInUse) {
            Log.d(TAG, "unpauseIfPauseOnDisconnect() audio is in use")
            return
        }
        if (transientPause) {
            transientPause = false
            if (Build.VERSION.SDK_INT >= 31) {
                stateManager!!.stopService()
                return
            }
            if (!bluetooth && isUnpauseOnHeadsetReconnect) {
                mediaPlayer!!.resume()
            } else if (bluetooth && isUnpauseOnBluetoothReconnect) {
                // let the user know we've started playback again...
                val v = applicationContext.getSystemService(VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(500)
                mediaPlayer!!.resume()
            }
        }
    }

    private val shutdownReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (TextUtils.equals(intent.action, PlaybackServiceInterface.ACTION_SHUTDOWN_PLAYBACK_SERVICE)) {
                EventBus.getDefault().post(PlaybackServiceEvent(PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN))
                stateManager!!.stopService()
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun volumeAdaptionChanged(event: VolumeAdaptionChangedEvent) {
        val playbackVolumeUpdater = PlaybackVolumeUpdater()
        playbackVolumeUpdater.updateVolumeIfNecessary(mediaPlayer!!, event.feedId, event.volumeAdaptionSetting)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun speedPresetChanged(event: SpeedPresetChangedEvent) {
        if (playable is FeedMedia) {
            if ((playable as FeedMedia).getItem()?.feed?.id == event.feedId) {
                if (event.speed == FeedPreferences.SPEED_USE_GLOBAL) {
                    playable?.let {setSpeed(getPlaybackSpeed(playable!!.getMediaType()))}
                } else {
                    setSpeed(event.speed)
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun skipIntroEndingPresetChanged(event: SkipIntroEndingChangedEvent) {
        if (playable is FeedMedia) {
            if ((playable as FeedMedia).getItem()?.feed?.id == event.feedId) {
                if (event.skipEnding != 0) {
                    val feedPreferences = (playable as FeedMedia).getItem()?.feed?.preferences
                    if (feedPreferences != null) {
                        feedPreferences.feedSkipIntro = event.skipIntro
                        feedPreferences.feedSkipEnding = event.skipEnding
                    }
                }
            }
        }
    }

    fun resume() {
        mediaPlayer!!.resume()
        taskManager!!.restartSleepTimer()
    }

    fun prepare() {
        mediaPlayer!!.prepare()
        taskManager!!.restartSleepTimer()
    }

    fun pause(abandonAudioFocus: Boolean, reinit: Boolean) {
        mediaPlayer!!.pause(abandonAudioFocus, reinit)
    }

    val pSMPInfo: PSMPInfo
        get() = mediaPlayer!!.pSMPInfo

    val status: PlayerStatus
        get() = mediaPlayer!!.playerStatus

    val playable: Playable?
        get() = mediaPlayer!!.getPlayable()

    fun setSpeed(speed: Float) {
        currentlyPlayingTemporaryPlaybackSpeed = speed
        if (currentMediaType == MediaType.VIDEO) {
            videoPlaybackSpeed = speed
        } else {
            setPlaybackSpeed(speed)
        }

        mediaPlayer!!.setPlaybackParams(speed, isSkipSilence)
    }

    fun skipSilence(skipSilence: Boolean) {
        mediaPlayer!!.setPlaybackParams(currentPlaybackSpeed, skipSilence)
    }

    val currentPlaybackSpeed: Float
        get() {
            if (mediaPlayer == null) {
                return 1.0f
            }
            return mediaPlayer!!.getPlaybackSpeed()
        }

    var isStartWhenPrepared: Boolean
        get() = mediaPlayer!!.isStartWhenPrepared()
        set(s) {
            mediaPlayer!!.setStartWhenPrepared(s)
        }

    fun seekTo(t: Int) {
        mediaPlayer!!.seekTo(t)
        EventBus.getDefault().post(PlaybackPositionEvent(t, duration))
    }

    private fun seekDelta(d: Int) {
        mediaPlayer!!.seekDelta(d)
    }

    val duration: Int
        /**
         * call getDuration() on mediaplayer or return INVALID_TIME if player is in
         * an invalid state.
         */
        get() {
            if (mediaPlayer == null) {
                return Playable.INVALID_TIME
            }
            return mediaPlayer!!.getDuration()
        }

    val currentPosition: Int
        /**
         * call getCurrentPosition() on mediaplayer or return INVALID_TIME if player
         * is in an invalid state.
         */
        get() {
            if (mediaPlayer == null) {
                return Playable.INVALID_TIME
            }
            return mediaPlayer!!.getPosition()
        }

    val audioTracks: List<String?>?
        get() {
            if (mediaPlayer == null) {
                return emptyList<String>()
            }
            return mediaPlayer!!.getAudioTracks()
        }

    val selectedAudioTrack: Int
        get() {
            if (mediaPlayer == null) {
                return -1
            }
            return mediaPlayer!!.getSelectedAudioTrack()
        }

    fun setAudioTrack(track: Int) {
        if (mediaPlayer != null) {
            mediaPlayer!!.setAudioTrack(track)
        }
    }

    val isStreaming: Boolean
        get() = mediaPlayer!!.isStreaming()

    val videoSize: Pair<Int, Int>?
        get() = mediaPlayer!!.getVideoSize()

    private fun setupPositionObserver() {
        if (positionEventTimer != null) {
            positionEventTimer!!.dispose()
        }

        Log.d(TAG, "Setting up position observer")
        positionEventTimer = Observable.interval(1, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { number: Long? ->
                EventBus.getDefault().post(PlaybackPositionEvent(currentPosition, duration))
                if (Build.VERSION.SDK_INT < 29) {
                    notificationBuilder!!.updatePosition(currentPosition, currentPlaybackSpeed)
                    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(R.id.notification_playing, notificationBuilder!!.build())
                }
                skipEndingIfNecessary()
            }
    }

    private fun cancelPositionObserver() {
        if (positionEventTimer != null) {
            positionEventTimer!!.dispose()
        }
    }

    private fun addPlayableToQueue(playable: Playable?) {
        if (playable is FeedMedia) {
            val itemId = playable.getItem()!!.id
            DBWriter.addQueueItem(this, false, true, itemId)
            notifyChildrenChanged(getString(R.string.queue_label))
        }
    }

    private val sessionCallback: MediaSessionCompat.Callback = object : MediaSessionCompat.Callback() {
        private val TAG = "MediaSessionCompat"

        override fun onPlay() {
            Log.d(TAG, "onPlay()")
            val status: PlayerStatus = this@PlaybackService.status
            if (status == PlayerStatus.PAUSED || status == PlayerStatus.PREPARED) {
                resume()
            } else if (status == PlayerStatus.INITIALIZED) {
                this@PlaybackService.isStartWhenPrepared = true
                prepare()
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
            mediaPlayer!!.stopPlayback(true)
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
            val chapters = mediaPlayer!!.getPlayable()!!.getChapters()
            if (chapters == null) {
                // No chapters, just fallback to next episode
                mediaPlayer!!.skip()
                return
            }

            val nextChapter = getCurrentChapterIndex(
                mediaPlayer!!.getPlayable(), mediaPlayer!!.getPosition()) + 1

            if (chapters.size < nextChapter + 1) {
                // We are on the last chapter, just fallback to the next episode
                mediaPlayer!!.skip()
                return
            }

            mediaPlayer!!.seekTo(chapters[nextChapter].start.toInt())
        }

        override fun onFastForward() {
            Log.d(TAG, "onFastForward()")
            seekDelta(fastForwardSecs * 1000)
        }

        override fun onSkipToNext() {
            Log.d(TAG, "onSkipToNext()")
            val uiModeManager = applicationContext
                .getSystemService(UI_MODE_SERVICE) as UiModeManager
            if (hardwareForwardButton == KeyEvent.KEYCODE_MEDIA_NEXT
                    || uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_CAR) {
                mediaPlayer!!.skip()
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
            if (mediaButton != null) {
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
            }
            return false
        }

        override fun onCustomAction(action: String, extra: Bundle) {
            Log.d(TAG, "onCustomAction($action)")
            if (CUSTOM_ACTION_FAST_FORWARD == action) {
                onFastForward()
            } else if (CUSTOM_ACTION_REWIND == action) {
                onRewind()
            } else if (CUSTOM_ACTION_SKIP_TO_NEXT == action) {
                mediaPlayer!!.skip()
            } else if (CUSTOM_ACTION_NEXT_CHAPTER == action) {
                onNextChapter()
            } else if (CUSTOM_ACTION_CHANGE_PLAYBACK_SPEED == action) {
                val selectedSpeeds = playbackSpeedArray

                // If the list has zero or one element, there's nothing we can do to change the playback speed.
                if (selectedSpeeds.size > 1) {
                    val speedPosition = selectedSpeeds.indexOf(mediaPlayer!!.getPlaybackSpeed())

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

    companion object {
        /**
         * Logging tag
         */
        private const val TAG = "PlaybackService"

        const val ACTION_PLAYER_STATUS_CHANGED: String = "action.de.danoeh.antennapod.core.service.playerStatusChanged"
        private const val AVRCP_ACTION_PLAYER_STATUS_CHANGED = "com.android.music.playstatechanged"
        private const val AVRCP_ACTION_META_CHANGED = "com.android.music.metachanged"

        /**
         * Custom actions used by Android Wear, Android Auto, and Android (API 33+ only)
         */
        private const val CUSTOM_ACTION_SKIP_TO_NEXT = "action.de.danoeh.antennapod.core.service.skipToNext"
        private const val CUSTOM_ACTION_FAST_FORWARD = "action.de.danoeh.antennapod.core.service.fastForward"
        private const val CUSTOM_ACTION_REWIND = "action.de.danoeh.antennapod.core.service.rewind"
        private const val CUSTOM_ACTION_CHANGE_PLAYBACK_SPEED =
            "action.de.danoeh.antennapod.core.service.changePlaybackSpeed"
        const val CUSTOM_ACTION_NEXT_CHAPTER: String = "action.de.danoeh.antennapod.core.service.next_chapter"

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
