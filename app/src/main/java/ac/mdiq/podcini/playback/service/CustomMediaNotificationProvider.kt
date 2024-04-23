package ac.mdiq.podcini.playback.service

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList

@UnstableApi
class CustomMediaNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {

    override fun addNotificationActions(mediaSession: MediaSession, mediaButtons: ImmutableList<CommandButton>, builder: NotificationCompat.Builder, actionFactory: MediaNotification.ActionFactory): IntArray {

        /* Retrieving notification default play/pause button from mediaButtons list. */
        val defaultPlayPauseCommandButton = mediaButtons.getOrNull(0)
        val notificationMediaButtons = if (defaultPlayPauseCommandButton != null) {
            /* Overriding received mediaButtons list to ensure required buttons order: [rewind15, play/pause, forward15]. */
            ImmutableList.builder<CommandButton>().apply {
                add(NotificationPlayerCustomCommandButton.REWIND.commandButton)
                add(defaultPlayPauseCommandButton)
                add(NotificationPlayerCustomCommandButton.FORWARD.commandButton)
            }.build()
        } else {
            /* Fallback option to handle nullability, in case retrieving default play/pause button fails for some reason (should never happen). */
            mediaButtons
        }
        return super.addNotificationActions(
            mediaSession,
            notificationMediaButtons,
            builder,
            actionFactory
        )
    }
}