package de.danoeh.antennapod.dialog

import de.danoeh.antennapod.activity.MainActivity
import android.app.Activity
import android.content.DialogInterface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.event.PlayerErrorEvent

object MediaPlayerErrorDialog {
    fun show(activity: Activity, event: PlayerErrorEvent) {
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
        ) { dialog: DialogInterface?, which: Int ->
            if (activity is MainActivity) {
                activity.bottomSheet?.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        errorDialog.create().show()
    }
}
