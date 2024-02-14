package de.danoeh.antennapod.fragment.preferences.dialog

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R

class PreferenceSwitchDialog(protected var context: Context, private val title: String, private val text: String) {
    private var onPreferenceChangedListener: OnPreferenceChangedListener? = null

    interface OnPreferenceChangedListener {
        /**
         * Notified when user confirms preference
         *
         * @param enabled The preference
         */
        fun preferenceChanged(enabled: Boolean)
    }

    fun openDialog() {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(title)

        val inflater = LayoutInflater.from(this.context)
        val layout = inflater.inflate(R.layout.dialog_switch_preference, null, false)
        val switchButton = layout.findViewById<SwitchCompat>(R.id.dialogSwitch)
        switchButton.text = text
        builder.setView(layout)

        builder.setPositiveButton(R.string.confirm_label) { dialog: DialogInterface?, which: Int ->
            if (onPreferenceChangedListener != null) {
                onPreferenceChangedListener!!.preferenceChanged(switchButton.isChecked)
            }
        }
        builder.setNegativeButton(R.string.cancel_label, null)
        builder.create().show()
    }

    fun setOnPreferenceChangedListener(onPreferenceChangedListener: OnPreferenceChangedListener?) {
        this.onPreferenceChangedListener = onPreferenceChangedListener
    }
}
