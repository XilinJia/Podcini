package ac.mdiq.podcini.preferences

import android.content.Context
import android.content.DialogInterface
import android.util.AttributeSet
import androidx.preference.MultiSelectListPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MaterialMultiSelectListPreference : MultiSelectListPreference {
    constructor(context: Context) : super(context!!)

    constructor(context: Context, attrs: AttributeSet?) : super(context!!, attrs)

    override fun onClick() {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(title)
        builder.setIcon(dialogIcon)
        builder.setNegativeButton(negativeButtonText, null)

        val selected = BooleanArray(entries.size)
        val values = entryValues
        for (i in values.indices) {
            selected[i] = getValues().contains(values[i].toString())
        }
        builder.setMultiChoiceItems(entries, selected
        ) { dialog: DialogInterface?, which: Int, isChecked: Boolean -> selected[which] = isChecked }
        builder.setPositiveButton("OK") { dialog: DialogInterface?, which: Int ->
            val selectedValues: MutableSet<String> = HashSet()
            for (i in values.indices) {
                if (selected[i]) {
                    selectedValues.add(entryValues[i].toString())
                }
            }
            setValues(selectedValues)
        }
        builder.show()
    }
}
