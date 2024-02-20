package ac.mdiq.podcini.core.service.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.VectorDrawable
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.view.KeyEvent
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import ac.mdiq.podcini.core.R
import ac.mdiq.podcini.core.feed.util.ImageResourceUtils
import ac.mdiq.podcini.core.receiver.MediaButtonReceiver
import ac.mdiq.podcini.core.util.Converter
import ac.mdiq.podcini.core.util.TimeSpeedConverter
import ac.mdiq.podcini.core.util.gui.NotificationUtils
import ac.mdiq.podcini.model.playback.Playable
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.storage.preferences.UserPreferences
import org.apache.commons.lang3.ArrayUtils
import java.util.concurrent.ExecutionException

@UnstableApi
class PlaybackServiceNotificationBuilder(private val context: Context) {
    private var playable: Playable? = null
    private var mediaSessionToken: MediaSessionCompat.Token? = null
    @JvmField
    var playerStatus: PlayerStatus? = null
    var cachedIcon: Bitmap? = null
        private set
    private var position: String? = null

    fun setPlayable(playable: Playable) {
        if (playable !== this.playable) {
            clearCache()
        }
        this.playable = playable
    }

    private fun clearCache() {
        this.cachedIcon = null
        this.position = null
    }

    fun updatePosition(position: Int, speed: Float) {
        val converter = TimeSpeedConverter(speed)
        this.position = Converter.getDurationStringLong(converter.convert(position))
    }

    val isIconCached: Boolean
        get() = cachedIcon != null

    fun loadIcon() {
        val iconSize = (128 * context.resources.displayMetrics.density).toInt()
        val options = RequestOptions().centerCrop()
        try {
            cachedIcon = Glide.with(context)
                .asBitmap()
                .load(playable!!.getImageLocation())
                .apply(options)
                .submit(iconSize, iconSize)
                .get()
        } catch (e: ExecutionException) {
            try {
                cachedIcon = Glide.with(context)
                    .asBitmap()
                    .load(ImageResourceUtils.getFallbackImageLocation(playable!!))
                    .apply(options)
                    .submit(iconSize, iconSize)
                    .get()
            } catch (ignore: InterruptedException) {
                Log.e(TAG, "Media icon loader was interrupted")
            } catch (tr: Throwable) {
                Log.e(TAG, "Error loading the media icon for the notification", tr)
            }
        } catch (ignore: InterruptedException) {
            Log.e(TAG, "Media icon loader was interrupted")
        } catch (tr: Throwable) {
            Log.e(TAG, "Error loading the media icon for the notification", tr)
        }
    }

    private val defaultIcon: Bitmap?
        get() {
            if (Companion.defaultIcon == null) {
                Companion.defaultIcon = getBitmap(
                    context, R.mipmap.ic_launcher)
            }
            return Companion.defaultIcon
        }

    fun build(): Notification {
        val notification = NotificationCompat.Builder(
            context,
            NotificationUtils.CHANNEL_ID_PLAYING)

        if (playable != null) {
            notification.setContentTitle(playable!!.getFeedTitle())
            notification.setContentText(playable!!.getEpisodeTitle())
            addActions(notification, mediaSessionToken, playerStatus)

            if (cachedIcon != null) {
                notification.setLargeIcon(cachedIcon)
            } else {
                notification.setLargeIcon(this.defaultIcon)
            }

            if (Build.VERSION.SDK_INT < 29) {
                notification.setSubText(position)
            }
        } else {
            notification.setContentTitle(context.getString(R.string.app_name))
            notification.setContentText("Loading. If this does not go away, play any episode and contact us.")
        }

        notification.setContentIntent(playerActivityPendingIntent)
        notification.setWhen(0)
        notification.setSmallIcon(R.drawable.ic_notification)
        notification.setOngoing(false)
        notification.setOnlyAlertOnce(true)
        notification.setShowWhen(false)
        notification.setPriority(UserPreferences.notifyPriority)
        notification.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        notification.setColor(NotificationCompat.COLOR_DEFAULT)
        return notification.build()
    }

    private val playerActivityPendingIntent: PendingIntent
        get() = PendingIntent.getActivity(context, R.id.pending_intent_player_activity,
            PlaybackService.getPlayerActivityIntent(context), PendingIntent.FLAG_UPDATE_CURRENT
                    or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))

    private fun addActions(notification: NotificationCompat.Builder, mediaSessionToken: MediaSessionCompat.Token?,
                           playerStatus: PlayerStatus?
    ) {
        val compactActionList = ArrayList<Int>()

        var numActions = 0 // we start and 0 and then increment by 1 for each call to addAction

        val rewindButtonPendingIntent = getPendingIntentForMediaAction(
            KeyEvent.KEYCODE_MEDIA_REWIND, numActions)
        notification.addAction(R.drawable.ic_notification_fast_rewind, context.getString(R.string.rewind_label),
            rewindButtonPendingIntent)
        compactActionList.add(numActions)
        numActions++

        if (playerStatus == PlayerStatus.PLAYING) {
            val pauseButtonPendingIntent = getPendingIntentForMediaAction(
                KeyEvent.KEYCODE_MEDIA_PAUSE, numActions)
            notification.addAction(R.drawable.ic_notification_pause,  //pause action
                context.getString(R.string.pause_label),
                pauseButtonPendingIntent)
        } else {
            val playButtonPendingIntent = getPendingIntentForMediaAction(
                KeyEvent.KEYCODE_MEDIA_PLAY, numActions)
            notification.addAction(R.drawable.ic_notification_play,  //play action
                context.getString(R.string.play_label),
                playButtonPendingIntent)
        }
        compactActionList.add(numActions++)

        // ff follows play, then we have skip (if it's present)
        val ffButtonPendingIntent = getPendingIntentForMediaAction(
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, numActions)
        notification.addAction(R.drawable.ic_notification_fast_forward, context.getString(R.string.fast_forward_label),
            ffButtonPendingIntent)
        compactActionList.add(numActions)
        numActions++

        if (UserPreferences.showNextChapterOnFullNotification() && playable?.getChapters() != null) {
            val nextChapterPendingIntent = getPendingIntentForCustomMediaAction(PlaybackService.CUSTOM_ACTION_NEXT_CHAPTER, numActions)
            notification.addAction(R.drawable.ic_notification_next_chapter, context.getString(R.string.next_chapter), nextChapterPendingIntent)
            numActions++
        }

        if (UserPreferences.showSkipOnFullNotification()) {
            val skipButtonPendingIntent = getPendingIntentForMediaAction(
                KeyEvent.KEYCODE_MEDIA_NEXT, numActions)
            notification.addAction(R.drawable.ic_notification_skip, context.getString(R.string.skip_episode_label),
                skipButtonPendingIntent)
            numActions++
        }

        val stopButtonPendingIntent = getPendingIntentForMediaAction(
            KeyEvent.KEYCODE_MEDIA_STOP, numActions)
        notification.setStyle(androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSessionToken)
            .setShowActionsInCompactView(*ArrayUtils.toPrimitive(compactActionList.toTypedArray<Int>()))
            .setShowCancelButton(true)
            .setCancelButtonIntent(stopButtonPendingIntent))
    }

    private fun getPendingIntentForMediaAction(keycodeValue: Int, requestCode: Int): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java)
        intent.setAction("MediaCode$keycodeValue")
        intent.putExtra(MediaButtonReceiver.EXTRA_KEYCODE, keycodeValue)

        return if (Build.VERSION.SDK_INT >= 26) {
            PendingIntent.getForegroundService(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT
                    or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        }
    }

    private fun getPendingIntentForCustomMediaAction(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java)
        intent.setAction("MediaAction$action")
        intent.putExtra(MediaButtonReceiver.EXTRA_CUSTOM_ACTION, action)

        return if (Build.VERSION.SDK_INT >= 26) {
            PendingIntent.getForegroundService(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT
                    or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        }
    }

    fun setMediaSessionToken(mediaSessionToken: MediaSessionCompat.Token?) {
        this.mediaSessionToken = mediaSessionToken
    }

    companion object {
        private const val TAG = "PlaybackSrvNotification"
        private var defaultIcon: Bitmap? = null

        private fun getBitmap(vectorDrawable: VectorDrawable): Bitmap {
            val bitmap = Bitmap.createBitmap(vectorDrawable.intrinsicWidth,
                vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
            vectorDrawable.draw(canvas)
            return bitmap
        }

        private fun getBitmap(context: Context, drawableId: Int): Bitmap? {
            return when (val drawable = AppCompatResources.getDrawable(context, drawableId)) {
                is BitmapDrawable -> {
                    drawable.bitmap
                }
                is VectorDrawable -> {
                    getBitmap(drawable)
                }
                else -> {
                    null
                }
            }
        }
    }
}
