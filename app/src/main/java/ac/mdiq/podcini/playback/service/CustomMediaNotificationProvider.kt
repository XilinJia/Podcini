package ac.mdiq.podcini.playback.service

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.*
import com.google.common.collect.ImmutableList

@UnstableApi
class CustomMediaNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {

    override fun addNotificationActions(mediaSession: MediaSession, mediaButtons: ImmutableList<CommandButton>, builder: NotificationCompat.Builder, actionFactory: MediaNotification.ActionFactory): IntArray {

        /* Retrieving notification default play/pause button from mediaButtons list. */
        val defaultPlayPauseButton = mediaButtons.getOrNull(1)
        val defaultRestartButton = mediaButtons.getOrNull(0)
        val notificationMediaButtons = if (defaultPlayPauseButton != null) {
            /* Overriding received mediaButtons list to ensure required buttons order: [rewind15, play/pause, forward15]. */
            ImmutableList.builder<CommandButton>().apply {
                if (defaultRestartButton != null) add(defaultRestartButton)
                add(NotificationCustomButton.REWIND.commandButton)
                add(defaultPlayPauseButton)
                add(NotificationCustomButton.FORWARD.commandButton)
                add(NotificationCustomButton.SKIP.commandButton)
            }.build()
        } else {
            /* Fallback option to handle nullability, in case retrieving default play/pause button fails for some reason (should never happen). */
            mediaButtons
        }
        return super.addNotificationActions(mediaSession, notificationMediaButtons, builder, actionFactory)
    }

}