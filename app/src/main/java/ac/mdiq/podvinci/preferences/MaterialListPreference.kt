package ac.mdiq.podvinci.preferences

import android.content.Context
import android.content.DialogInterface
import android.util.AttributeSet
import androidx.preference.ListPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MaterialListPreference : ListPreference {
    constructor(context: Context?) : super(context!!)

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs)

    override fun onClick() {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(title)
        builder.setIcon(dialogIcon)
        builder.setNegativeButton(negativeButtonText, null)

        val values = entryValues
        var selected = -1
        for (i in values.indices) {
            if (values[i].toString() == value) {
                selected = i
            }
        }
        builder.setSingleChoiceItems(entries, selected) { dialog: DialogInterface, which: Int ->
            dialog.dismiss()
            if (which >= 0 && entryValues != null) {
                val value = entryValues[which].toString()
                if (callChangeListener(value)) {
                    setValue(value)
                }
            }
        }
        builder.show()
    }
}
