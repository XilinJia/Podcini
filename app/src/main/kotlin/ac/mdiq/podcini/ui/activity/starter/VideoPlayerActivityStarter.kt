package ac.mdiq.podcini.ui.activity.starter

import ac.mdiq.podcini.R
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Launches the video player activity of the app with specific arguments.
 * Does not require a dependency on the actual implementation of the activity.
 */
 class VideoPlayerActivityStarter(private val context: Context) {
    val intent: Intent = Intent(INTENT)
    val pendingIntent: PendingIntent
        get() = PendingIntent.getActivity(context, R.id.pending_intent_video_player, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    init {
        intent.setPackage(context.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
    }

    fun start() {
        context.startActivity(intent)
    }

    companion object {
        const val INTENT: String = "ac.mdiq.podcini.intents.VIDEO_PLAYER"
    }
}
