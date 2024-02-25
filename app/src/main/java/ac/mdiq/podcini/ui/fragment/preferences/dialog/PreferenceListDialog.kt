package ac.mdiq.podcini.ui.fragment.preferences.dialog

import android.content.Context
import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.R

class PreferenceListDialog(private var context: Context, private val title: String) {
    private var onPreferenceChangedListener: OnPreferenceChangedListener? = null
    private var selectedPos = 0

    interface OnPreferenceChangedListener {
        /**
         * Notified when user confirms preference
         *
         * @param pos The index of the item that was selected
         */
        fun preferenceChanged(pos: Int)
    }

    fun openDialog(items: Array<String>?) {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(title)
        builder.setSingleChoiceItems(items, selectedPos) { _: DialogInterface?, which: Int ->
            selectedPos = which
        }
        builder.setPositiveButton(R.string.confirm_label) { _: DialogInterface?, _: Int ->
            if (onPreferenceChangedListener != null && selectedPos >= 0) {
                onPreferenceChangedListener!!.preferenceChanged(selectedPos)
            }
        }
        builder.setNegativeButton(R.string.cancel_label, null)
        builder.create().show()
    }

    fun setOnPreferenceChangedListener(onPreferenceChangedListener: OnPreferenceChangedListener?) {
        this.onPreferenceChangedListener = onPreferenceChangedListener
    }
}
