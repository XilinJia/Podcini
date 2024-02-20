package ac.mdiq.podcini.ui.home

import android.content.Context
import android.content.DialogInterface
import android.text.TextUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.home.HomeFragment.Companion.getHiddenSections

object HomeSectionsSettingsDialog {
    fun open(context: Context, onSettingsChanged: DialogInterface.OnClickListener) {
        val hiddenSections: MutableList<String> = getHiddenSections(context).toMutableList()
        val sectionLabels = context.resources.getStringArray(R.array.home_section_titles)
        val sectionTags = context.resources.getStringArray(R.array.home_section_tags)
        val checked = BooleanArray(sectionLabels.size)
        for (i in sectionLabels.indices) {
            val tag = sectionTags[i]
            if (!hiddenSections.contains(tag)) {
                checked[i] = true
            }
        }

        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(R.string.configure_home)
        builder.setMultiChoiceItems(sectionLabels, checked) { dialog: DialogInterface?, which: Int, isChecked: Boolean ->
            if (isChecked) {
                hiddenSections.remove(sectionTags[which])
            } else {
                hiddenSections.add(sectionTags[which])
            }
        }
        builder.setPositiveButton(R.string.confirm_label) { dialog: DialogInterface?, which: Int ->
            val prefs = context.getSharedPreferences(HomeFragment.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(HomeFragment.PREF_HIDDEN_SECTIONS, TextUtils.join(",", hiddenSections)).apply()
            onSettingsChanged.onClick(dialog, which)
        }
        builder.setNegativeButton(R.string.cancel_label, null)
        builder.create().show()
    }
}
