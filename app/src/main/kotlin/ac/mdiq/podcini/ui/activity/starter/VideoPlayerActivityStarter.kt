package ac.mdiq.podcini.ui.activity.starter


import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.Companion.VIDEO_MODE
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.VideoMode
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

/**
 * Launches the video player activity of the app with specific arguments.
 * Does not require a dependency on the actual implementation of the activity.
 */
@OptIn(UnstableApi::class) class VideoPlayerActivityStarter(private val context: Context, mode: VideoMode = VideoMode.None) {
    val intent: Intent = Intent(INTENT)

    init {
        intent.setPackage(context.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        if (mode != VideoMode.None) intent.putExtra(VIDEO_MODE, mode)
    }

    val pendingIntent: PendingIntent
        get() = PendingIntent.getActivity(context, R.id.pending_intent_video_player, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun start() {
        context.startActivity(intent)
    }

    companion object {
        const val INTENT: String = "ac.mdiq.podcini.intents.VIDEO_PLAYER"
    }
}
