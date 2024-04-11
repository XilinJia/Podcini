package ac.mdiq.podcini.ui.activity.appstartintent


import ac.mdiq.podcini.R
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Launches the playback speed dialog activity of the app with specific arguments.
 * Does not require a dependency on the actual implementation of the activity.
 */
class PlaybackSpeedActivityStarter(private val context: Context) {
    val intent: Intent = Intent(INTENT)

    init {
        intent.setPackage(context.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
    }

    val pendingIntent: PendingIntent
        get() = PendingIntent.getActivity(context, R.id.pending_intent_playback_speed, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun start() {
        context.startActivity(intent)
    }

    companion object {
        const val INTENT: String = "ac.mdiq.podcini.intents.PLAYBACK_SPEED"
    }
}
