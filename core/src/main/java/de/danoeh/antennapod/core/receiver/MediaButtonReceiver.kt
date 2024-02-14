package de.danoeh.antennapod.core.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import de.danoeh.antennapod.core.ClientConfigurator

/**
 * Receives media button events.
 */
class MediaButtonReceiver : BroadcastReceiver() {
    @UnstableApi
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received intent")
        if (intent?.extras == null) {
            return
        }
        val event = intent.extras!![Intent.EXTRA_KEY_EVENT] as KeyEvent?
        if (event != null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            ClientConfigurator.initialize(context)
            val serviceIntent = Intent(PLAYBACK_SERVICE_INTENT)
            serviceIntent.setPackage(context.packageName)
            serviceIntent.putExtra(EXTRA_KEYCODE, event.keyCode)
            serviceIntent.putExtra(EXTRA_SOURCE, event.source)
            serviceIntent.putExtra(EXTRA_HARDWAREBUTTON, event.eventTime > 0 || event.downTime > 0)
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        private const val TAG = "MediaButtonReceiver"
        const val EXTRA_KEYCODE: String = "de.danoeh.antennapod.core.service.extra.MediaButtonReceiver.KEYCODE"
        const val EXTRA_CUSTOM_ACTION: String =
            "de.danoeh.antennapod.core.service.extra.MediaButtonReceiver.CUSTOM_ACTION"
        const val EXTRA_SOURCE: String = "de.danoeh.antennapod.core.service.extra.MediaButtonReceiver.SOURCE"
        const val EXTRA_HARDWAREBUTTON
                : String = "de.danoeh.antennapod.core.service.extra.MediaButtonReceiver.HARDWAREBUTTON"
        const val NOTIFY_BUTTON_RECEIVER: String = "de.danoeh.antennapod.NOTIFY_BUTTON_RECEIVER"
        const val PLAYBACK_SERVICE_INTENT: String = "de.danoeh.antennapod.intents.PLAYBACK_SERVICE"

        @JvmStatic
        fun createIntent(context: Context?, eventCode: Int): Intent {
            val event = KeyEvent(KeyEvent.ACTION_DOWN, eventCode)
            val startingIntent = Intent(context, MediaButtonReceiver::class.java)
            startingIntent.setAction(NOTIFY_BUTTON_RECEIVER)
            startingIntent.putExtra(Intent.EXTRA_KEY_EVENT, event)
            return startingIntent
        }

        @JvmStatic
        fun createPendingIntent(context: Context?, eventCode: Int): PendingIntent {
            return PendingIntent.getBroadcast(context, eventCode, createIntent(context, eventCode),
                (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        }
    }
}
