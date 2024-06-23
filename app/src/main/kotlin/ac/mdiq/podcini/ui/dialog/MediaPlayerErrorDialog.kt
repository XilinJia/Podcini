package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.ui.activity.MainActivity
import android.app.Activity
import android.content.DialogInterface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.R
import ac.mdiq.podcini.util.event.FlowEvent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
object MediaPlayerErrorDialog {
    fun show(activity: Activity, event: FlowEvent.PlayerErrorEvent) {
        val errorDialog = MaterialAlertDialogBuilder(activity)
        errorDialog.setTitle(R.string.error_label)

        val genericMessage: String = activity.getString(R.string.playback_error_generic)
        val errorMessage = SpannableString("""
    $genericMessage
    
    ${event.message}
    """.trimIndent())
        errorMessage.setSpan(ForegroundColorSpan(-0x77777778),
            genericMessage.length, errorMessage.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        errorDialog.setMessage(errorMessage)
        errorDialog.setPositiveButton("OK"
        ) { _: DialogInterface?, _: Int ->
            if (activity is MainActivity) {
                activity.bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        errorDialog.create().show()
    }
}
