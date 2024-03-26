package ac.mdiq.podcini.ui.widget

import ac.mdiq.podcini.R
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.RemoteViews
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions

import ac.mdiq.podcini.feed.util.ImageResourceUtils.getFallbackImageLocation
import ac.mdiq.podcini.receiver.MediaButtonReceiver.Companion.createPendingIntent
import ac.mdiq.podcini.receiver.PlayerWidget
import ac.mdiq.podcini.receiver.PlayerWidget.Companion.isEnabled
import ac.mdiq.podcini.util.Converter.getDurationStringLong
import ac.mdiq.podcini.util.TimeSpeedConverter
import ac.mdiq.podcini.storage.model.playback.MediaType
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.preferences.UserPreferences.shouldShowRemainingTime
import ac.mdiq.podcini.ui.appstartintent.MainActivityStarter
import ac.mdiq.podcini.ui.appstartintent.PlaybackSpeedActivityStarter
import ac.mdiq.podcini.ui.appstartintent.VideoPlayerActivityStarter
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Updates the state of the player widget.
 */
object WidgetUpdater {
    private const val TAG = "WidgetUpdater"

    /**
     * Update the widgets with the given parameters. Must be called in a background thread.
     */
    fun updateWidget(context: Context, widgetState: WidgetState?) {
        if (!isEnabled(context) || widgetState == null) {
            return
        }
        val startMediaPlayer = if (widgetState.media != null && widgetState.media.getMediaType() === MediaType.VIDEO) {
            VideoPlayerActivityStarter(context).pendingIntent
        } else {
            MainActivityStarter(context).withOpenPlayer().pendingIntent
        }

        val startPlaybackSpeedDialog = PlaybackSpeedActivityStarter(context).pendingIntent
        val views = RemoteViews(context.packageName, R.layout.player_widget)

        if (widgetState.media != null) {
            val icon: Bitmap?
            val iconSize = context.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
            views.setOnClickPendingIntent(R.id.layout_left, startMediaPlayer)
            views.setOnClickPendingIntent(R.id.imgvCover, startMediaPlayer)
            views.setOnClickPendingIntent(R.id.butPlaybackSpeed, startPlaybackSpeedDialog)

            val radius = context.resources.getDimensionPixelSize(R.dimen.widget_inner_radius)
            val options = RequestOptions()
                .dontAnimate()
                .transform(FitCenter(), RoundedCorners(radius))

            try {
                var imgLoc = widgetState.media.getImageLocation()
                if (imgLoc != null) {
                    icon = Glide.with(context)
                        .asBitmap()
                        .load(imgLoc)
                        .apply(options)
                        .submit(iconSize, iconSize)
                        .get(500, TimeUnit.MILLISECONDS)
                    views.setImageViewBitmap(R.id.imgvCover, icon)
                } else {
                    imgLoc = getFallbackImageLocation(widgetState.media)
                    if (imgLoc != null) {
                        icon = Glide.with(context)
                            .asBitmap()
                            .load(imgLoc)
                            .apply(options)
                            .submit(iconSize, iconSize)[500, TimeUnit.MILLISECONDS]
                        views.setImageViewBitmap(R.id.imgvCover, icon)
                    } else views.setImageViewResource(R.id.imgvCover, R.mipmap.ic_launcher)
                }
            } catch (tr1: Throwable) {
                Log.e(TAG, "Error loading the media icon for the widget", tr1)
            }

            views.setTextViewText(R.id.txtvTitle, widgetState.media.getEpisodeTitle())
            views.setViewVisibility(R.id.txtvTitle, View.VISIBLE)
            views.setViewVisibility(R.id.txtNoPlaying, View.GONE)

            val progressString = getProgressString(widgetState.position,
                widgetState.duration, widgetState.playbackSpeed)
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
            views.setOnClickPendingIntent(R.id.butPlay,
                createPendingIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.butPlayExtended,
                createPendingIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.butRew,
                createPendingIntent(context, KeyEvent.KEYCODE_MEDIA_REWIND))
            views.setOnClickPendingIntent(R.id.butFastForward,
                createPendingIntent(context, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
            views.setOnClickPendingIntent(R.id.butSkip,
                createPendingIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT))
        } else {
            // start the app if they click anything
            views.setOnClickPendingIntent(R.id.layout_left, startMediaPlayer)
            views.setOnClickPendingIntent(R.id.butPlay, startMediaPlayer)
            views.setOnClickPendingIntent(R.id.butPlayExtended,
                createPendingIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
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
            val options = manager.getAppWidgetOptions(id)
            val prefs = context.getSharedPreferences(PlayerWidget.PREFS_NAME, Context.MODE_PRIVATE)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val columns = getCellsForSize(minWidth)
            if (columns < 3) {
                views.setViewVisibility(R.id.layout_center, View.INVISIBLE)
            } else {
                views.setViewVisibility(R.id.layout_center, View.VISIBLE)
            }
            val showPlaybackSpeed = prefs.getBoolean(PlayerWidget.KEY_WIDGET_PLAYBACK_SPEED + id, false)
            val showRewind = prefs.getBoolean(PlayerWidget.KEY_WIDGET_REWIND + id, false)
            val showFastForward = prefs.getBoolean(PlayerWidget.KEY_WIDGET_FAST_FORWARD + id, false)
            val showSkip = prefs.getBoolean(PlayerWidget.KEY_WIDGET_SKIP + id, false)

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

            val backgroundColor = prefs.getInt(PlayerWidget.KEY_WIDGET_COLOR + id, PlayerWidget.DEFAULT_COLOR)
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
        if (position < 0 || duration <= 0) {
            return null
        }
        val converter = TimeSpeedConverter(speed)
        return if (shouldShowRemainingTime()) {
            (getDurationStringLong(converter.convert(position)) + " / -"
                    + getDurationStringLong(converter.convert(max(0.0,
                (duration - position).toDouble())
                .toInt())))
        } else {
            (getDurationStringLong(converter.convert(position)) + " / "
                    + getDurationStringLong(converter.convert(duration)))
        }
    }

    class WidgetState(val media: Playable?,
                      val status: PlayerStatus,
                      val position: Int,
                      val duration: Int,
                      val playbackSpeed: Float
    ) {
        constructor(status: PlayerStatus) : this(null, status, Playable.INVALID_TIME, Playable.INVALID_TIME, 1.0f)
    }
}
