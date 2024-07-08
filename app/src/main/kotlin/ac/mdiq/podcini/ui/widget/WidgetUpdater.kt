package ac.mdiq.podcini.ui.widget

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.utils.ImageResourceUtils.getFallbackImageLocation
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.preferences.UserPreferences.shouldShowRemainingTime
import ac.mdiq.podcini.receiver.MediaButtonReceiver.Companion.createPendingIntent
import ac.mdiq.podcini.receiver.PlayerWidget
import ac.mdiq.podcini.receiver.PlayerWidget.Companion.isEnabled
import ac.mdiq.podcini.receiver.PlayerWidget.Companion.prefs
import ac.mdiq.podcini.storage.model.MediaType
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.ui.activity.starter.MainActivityStarter
import ac.mdiq.podcini.ui.activity.starter.PlaybackSpeedActivityStarter
import ac.mdiq.podcini.ui.activity.starter.VideoPlayerActivityStarter
import ac.mdiq.podcini.util.Converter.getDurationStringLong
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.TimeSpeedConverter
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.RemoteViews
import coil.imageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max


/**
 * Updates the state of the player widget.
 */
object WidgetUpdater {
    private val TAG: String = WidgetUpdater::class.simpleName ?: "Anonymous"

    /**
     * Update the widgets with the given parameters. Must be called in a background thread.
     */
    fun updateWidget(context: Context, widgetState: WidgetState?) {
        if (!isEnabled() || widgetState == null) return
        Logd(TAG, "in updateWidget")

        val startMediaPlayer =
            if (widgetState.media != null && widgetState.media.getMediaType() === MediaType.VIDEO) VideoPlayerActivityStarter(context).pendingIntent
            else MainActivityStarter(context).withOpenPlayer().pendingIntent

        val startPlaybackSpeedDialog = PlaybackSpeedActivityStarter(context).pendingIntent
        val views = RemoteViews(context.packageName, R.layout.player_widget)

        if (widgetState.media != null) {
            var icon: Bitmap? = null
            val iconSize = context.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
            views.setOnClickPendingIntent(R.id.layout_left, startMediaPlayer)
            views.setOnClickPendingIntent(R.id.imgvCover, startMediaPlayer)
            views.setOnClickPendingIntent(R.id.butPlaybackSpeed, startPlaybackSpeedDialog)

            try {
                val imgLoc = widgetState.media.getImageLocation()
                val imgLoc1 = getFallbackImageLocation(widgetState.media)
                CoroutineScope(Dispatchers.IO).launch {
                    val request = ImageRequest.Builder(context)
                        .data(imgLoc)
                        .setHeader("User-Agent", "Mozilla/5.0")
                        .placeholder(R.color.light_gray)
                        .listener(object : ImageRequest.Listener {
                            override fun onError(request: ImageRequest, throwable: ErrorResult) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    val fallbackImageRequest = ImageRequest.Builder(context)
                                        .data(imgLoc1)
                                        .setHeader("User-Agent", "Mozilla/5.0")
                                        .error(R.mipmap.ic_launcher)
                                        .size(iconSize, iconSize)
                                        .build()
                                    val result = (context.imageLoader.execute(fallbackImageRequest) as SuccessResult).drawable
                                    icon = (result as BitmapDrawable).bitmap
                                }
                            }
                        })
                        .size(iconSize, iconSize)
                        .build()
                    withContext(Dispatchers.Main) {
                        val result = (context.imageLoader.execute(request) as SuccessResult).drawable
                        icon = (result as BitmapDrawable).bitmap
                        try {
                            if (icon != null) views.setImageViewBitmap(R.id.imgvCover, icon)
                            else views.setImageViewResource(R.id.imgvCover, R.mipmap.ic_launcher)
                        } catch(e: Exception) {
                            Log.e(TAG, e.message?:"")
                            e.printStackTrace()
                        }
                    }
                }
            } catch (tr1: Throwable) {
                Log.e(TAG, "Error loading the media icon for the widget", tr1)
            }

            views.setTextViewText(R.id.txtvTitle, widgetState.media.getEpisodeTitle())
            views.setViewVisibility(R.id.txtvTitle, View.VISIBLE)
            views.setViewVisibility(R.id.txtNoPlaying, View.GONE)

            val progressString = getProgressString(widgetState.position, widgetState.duration, widgetState.playbackSpeed)
            if (progressString != null) {
                views.setViewVisibility(R.id.txtvProgress, View.VISIBLE)
                views.setTextViewText(R.id.txtvProgress, progressString)
            }

            if (widgetState.status == PlayerStatus.PLAYING) {
                views.setImageViewResource(R.id.butPlay, R.drawable.ic_widget_pause)
                views.setContentDescription(R.id.butPlay, context.getString(R.string.pause_label))
                views.setImageViewResource(R.id.butPlayExtended, R.drawable.ic_widget_pause)
                views.setContentDescription(R.id.butPlayExtended, context.getString(R.string.pause_label))
            } else {
                views.setImageViewResource(R.id.butPlay, R.drawable.ic_widget_play)
                views.setContentDescription(R.id.butPlay, context.getString(R.string.play_label))
                views.setImageViewResource(R.id.butPlayExtended, R.drawable.ic_widget_play)
                views.setContentDescription(R.id.butPlayExtended, context.getString(R.string.play_label))
            }
            views.setOnClickPendingIntent(R.id.butPlay, createPendingIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.butPlayExtended, createPendingIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.butRew, createPendingIntent(context, KeyEvent.KEYCODE_MEDIA_REWIND))
            views.setOnClickPendingIntent(R.id.butFastForward, createPendingIntent(context, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
            views.setOnClickPendingIntent(R.id.butSkip, createPendingIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT))
        } else {
            // start the app if they click anything
            views.setOnClickPendingIntent(R.id.layout_left, startMediaPlayer)
            views.setOnClickPendingIntent(R.id.butPlay, startMediaPlayer)
            views.setOnClickPendingIntent(R.id.butPlayExtended, createPendingIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            views.setViewVisibility(R.id.txtvProgress, View.GONE)
            views.setViewVisibility(R.id.txtvTitle, View.GONE)
            views.setViewVisibility(R.id.txtNoPlaying, View.VISIBLE)
            views.setImageViewResource(R.id.imgvCover, R.mipmap.ic_launcher)
            views.setImageViewResource(R.id.butPlay, R.drawable.ic_widget_play)
            views.setImageViewResource(R.id.butPlayExtended, R.drawable.ic_widget_play)
        }

        val playerWidget = ComponentName(context, PlayerWidget::class.java)
        val manager = AppWidgetManager.getInstance(context)
        val widgetIds = manager.getAppWidgetIds(playerWidget)

        for (id in widgetIds) {
            Logd(TAG, "updating widget $id")
            val options = manager.getAppWidgetOptions(id)
//            val prefs = context.getSharedPreferences(PlayerWidget.PREFS_NAME, Context.MODE_PRIVATE)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val columns = getCellsForSize(minWidth)
            if (columns < 3) views.setViewVisibility(R.id.layout_center, View.INVISIBLE)
            else views.setViewVisibility(R.id.layout_center, View.VISIBLE)

            val showPlaybackSpeed = prefs!!.getBoolean(PlayerWidget.KEY_WIDGET_PLAYBACK_SPEED + id, true)
            val showRewind = prefs!!.getBoolean(PlayerWidget.KEY_WIDGET_REWIND + id, true)
            val showFastForward = prefs!!.getBoolean(PlayerWidget.KEY_WIDGET_FAST_FORWARD + id, true)
            val showSkip = prefs!!.getBoolean(PlayerWidget.KEY_WIDGET_SKIP + id, true)

            if (showPlaybackSpeed || showRewind || showSkip || showFastForward) {
                views.setInt(R.id.extendedButtonsContainer, "setVisibility", View.VISIBLE)
                views.setInt(R.id.butPlay, "setVisibility", View.GONE)
                views.setInt(R.id.butPlaybackSpeed, "setVisibility", if (showPlaybackSpeed) View.VISIBLE else View.GONE)
                views.setInt(R.id.butRew, "setVisibility", if (showRewind) View.VISIBLE else View.GONE)
                views.setInt(R.id.butFastForward, "setVisibility", if (showFastForward) View.VISIBLE else View.GONE)
                views.setInt(R.id.butSkip, "setVisibility", if (showSkip) View.VISIBLE else View.GONE)
            } else {
                views.setInt(R.id.extendedButtonsContainer, "setVisibility", View.GONE)
                views.setInt(R.id.butPlay, "setVisibility", View.VISIBLE)
            }

            val backgroundColor = prefs!!.getInt(PlayerWidget.KEY_WIDGET_COLOR + id, PlayerWidget.DEFAULT_COLOR)
            views.setInt(R.id.widgetLayout, "setBackgroundColor", backgroundColor)

            manager.updateAppWidget(id, views)
        }
    }

    /**
     * Returns number of cells needed for given size of the widget.
     *
     * @param size Widget size in dp.
     * @return Size in number of cells.
     */
    private fun getCellsForSize(size: Int): Int {
        var n = 2
        while (70 * n - 30 < size) {
            ++n
        }
        return n - 1
    }

    private fun getProgressString(position: Int, duration: Int, speed: Float): String? {
        if (position < 0 || duration <= 0) return null

        val converter = TimeSpeedConverter(speed)
        return if (shouldShowRemainingTime())
            ("${getDurationStringLong(converter.convert(position))} / -${getDurationStringLong(converter.convert(max(0.0, (duration - position).toDouble()).toInt()))}")
        else (getDurationStringLong(converter.convert(position)) + " / " + getDurationStringLong(converter.convert(duration)))

    }

    class WidgetState(val media: Playable?, val status: PlayerStatus, val position: Int, val duration: Int, val playbackSpeed: Float) {
        constructor(status: PlayerStatus) : this(null, status, Playable.INVALID_TIME, Playable.INVALID_TIME, 1.0f)
    }
}
