package ac.mdiq.podcini.dialog

import android.content.Context
import android.content.DialogInterface
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.R
import ac.mdiq.podcini.core.util.playback.PlaybackServiceStarter
import ac.mdiq.podcini.model.playback.Playable
import ac.mdiq.podcini.storage.preferences.UserPreferences.isAllowMobileStreaming

class StreamingConfirmationDialog(private val context: Context, private val playable: Playable) {
    @UnstableApi
    fun show() {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.stream_label)
            .setMessage(R.string.confirm_mobile_streaming_notification_message)
            .setPositiveButton(R.string.confirm_mobile_streaming_button_once) { dialog: DialogInterface?, which: Int -> stream() }
            .setNegativeButton(R.string.confirm_mobile_streaming_button_always) { dialog: DialogInterface?, which: Int ->
                isAllowMobileStreaming = true
                stream()
            }
            .setNeutralButton(R.string.cancel_label, null)
            .show()
    }

    @UnstableApi
    private fun stream() {
        PlaybackServiceStarter(context, playable)
            .callEvenIfRunning(true)
            .shouldStreamThisTime(true)
            .start()
    }
}
