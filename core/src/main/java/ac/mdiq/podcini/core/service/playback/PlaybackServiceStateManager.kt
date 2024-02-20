package ac.mdiq.podcini.core.service.playback

import android.annotation.SuppressLint
import android.app.Notification
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import kotlin.concurrent.Volatile

internal class PlaybackServiceStateManager(private val playbackService: PlaybackService) {
    @Volatile
    private var isInForeground = false

    @Volatile
    private var hasReceivedValidStartCommand = false

    fun startForeground(notificationId: Int, notification: Notification) {
        Log.d(TAG, "startForeground")
        if (Build.VERSION.SDK_INT >= 29) {
            playbackService.startForeground(notificationId, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            playbackService.startForeground(notificationId, notification)
        }
        isInForeground = true
    }

    fun stopService() {
        Log.d(TAG, "stopService")
        stopForeground(true)
        playbackService.stopSelf()
        hasReceivedValidStartCommand = false
    }

    fun stopForeground(removeNotification: Boolean) {
        Log.d(TAG, "stopForeground")
        if (isInForeground) {
            if (removeNotification) {
                ServiceCompat.stopForeground(playbackService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            } else {
                ServiceCompat.stopForeground(playbackService, ServiceCompat.STOP_FOREGROUND_DETACH)
            }
        }
        isInForeground = false
    }

    fun hasReceivedValidStartCommand(): Boolean {
        return hasReceivedValidStartCommand
    }

    fun validStartCommandWasReceived() {
        this.hasReceivedValidStartCommand = true
    }

    companion object {
        private const val TAG = "PlaybackSrvState"
    }
}
