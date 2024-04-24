package ac.mdiq.podcini.playback.service

import ac.mdiq.podcini.R
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand

private const val CUSTOM_COMMAND_REWIND_ACTION_ID = "1_REWIND"
private const val CUSTOM_COMMAND_FORWARD_ACTION_ID = "2_FAST_FWD"
private const val CUSTOM_COMMAND_SKIP_ACTION_ID = "3_SKIP"

enum class NotificationCustomButton(val customAction: String, val commandButton: CommandButton) {
    SKIP(
        customAction = CUSTOM_COMMAND_SKIP_ACTION_ID,
        commandButton = CommandButton.Builder()
            .setDisplayName("Skip")
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_SKIP_ACTION_ID, Bundle()))
            .setIconResId(R.drawable.ic_notification_skip)
            .build(),
    ),
    REWIND(
        customAction = CUSTOM_COMMAND_REWIND_ACTION_ID,
        commandButton = CommandButton.Builder()
            .setDisplayName("Rewind")
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_REWIND_ACTION_ID, Bundle()))
            .setIconResId(R.drawable.ic_notification_fast_rewind)
            .build(),
    ),
    FORWARD(
        customAction = CUSTOM_COMMAND_FORWARD_ACTION_ID,
        commandButton = CommandButton.Builder()
            .setDisplayName("Forward")
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_FORWARD_ACTION_ID, Bundle()))
            .setIconResId(R.drawable.ic_notification_fast_forward)
            .build(),
    ),
}