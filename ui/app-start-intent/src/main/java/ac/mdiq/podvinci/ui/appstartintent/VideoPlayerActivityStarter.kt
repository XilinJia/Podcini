package ac.mdiq.podvinci.ui.appstartintent

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Launches the video player activity of the app with specific arguments.
 * Does not require a dependency on the actual implementation of the activity.
 */
class VideoPlayerActivityStarter(private val context: Context) {
    val intent: Intent

    init {
        intent = Intent(INTENT)
        intent.setPackage(context.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
    }

    val pendingIntent: PendingIntent
        get() = PendingIntent.getActivity(context, R.id.pending_intent_video_player, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))

    fun start() {
        context.startActivity(intent)
    }

    companion object {
        const val INTENT: String = "ac.mdiq.podvinci.intents.VIDEO_PLAYER"
    }
}
