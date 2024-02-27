package ac.mdiq.podcini.ui.fragment.preferences.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.DialogSwitchPreferenceBinding
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PreferenceSwitchDialog(private var context: Context, private val title: String, private val text: String) {
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
        val binding = DialogSwitchPreferenceBinding.bind(layout)
        val switchButton = binding.dialogSwitch
        switchButton.text = text
        builder.setView(layout)

        builder.setPositiveButton(R.string.confirm_label) { _: DialogInterface?, _: Int ->
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
