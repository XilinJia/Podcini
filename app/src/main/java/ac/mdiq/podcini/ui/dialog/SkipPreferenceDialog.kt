package ac.mdiq.podcini.ui.dialog

import android.content.Context
import android.content.DialogInterface
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.UserPreferences.fastForwardSecs
import ac.mdiq.podcini.preferences.UserPreferences.rewindSecs
import java.text.NumberFormat
import java.util.*

/**
 * Shows the dialog that allows setting the skip time.
 */
object SkipPreferenceDialog {
    fun showSkipPreference(context: Context, direction: SkipDirection, textView: TextView?) {
        var checked = 0
        val skipSecs = if (direction == SkipDirection.SKIP_FORWARD) fastForwardSecs else rewindSecs

        val values = context.resources.getIntArray(R.array.seek_delta_values)
        val choices = arrayOfNulls<String>(values.size)
        for (i in values.indices) {
            if (skipSecs == values[i]) checked = i

            choices[i] = String.format(Locale.getDefault(), "%d %s", values[i], context.getString(R.string.time_seconds))
        }

        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(if (direction == SkipDirection.SKIP_FORWARD) R.string.pref_fast_forward else R.string.pref_rewind)
        builder.setSingleChoiceItems(choices, checked) { dialog: DialogInterface, _: Int ->
            val choice = (dialog as AlertDialog).listView.checkedItemPosition
            if (choice < 0 || choice >= values.size) {
                System.err.printf("Choice in showSkipPreference is out of bounds %d", choice)
            } else {
                val seconds = values[choice]
                if (direction == SkipDirection.SKIP_FORWARD) fastForwardSecs = seconds
                else rewindSecs = seconds

                if (textView != null) textView.text = NumberFormat.getInstance().format(seconds.toLong())

                dialog.dismiss()
            }
        }
        builder.setNegativeButton(R.string.cancel_label, null)
        builder.show()
    }

    enum class SkipDirection {
        SKIP_FORWARD, SKIP_REWIND
    }
}
