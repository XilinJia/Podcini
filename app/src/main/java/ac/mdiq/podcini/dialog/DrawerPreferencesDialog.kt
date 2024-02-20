package ac.mdiq.podcini.dialog

import android.content.Context
import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.R
import ac.mdiq.podcini.fragment.NavDrawerFragment
import ac.mdiq.podcini.storage.preferences.UserPreferences
import ac.mdiq.podcini.storage.preferences.UserPreferences.defaultPage
import ac.mdiq.podcini.storage.preferences.UserPreferences.hiddenDrawerItems

object DrawerPreferencesDialog {
    fun show(context: Context, callback: Runnable?) {
        val hiddenDrawerItems = hiddenDrawerItems?.toMutableList()?: mutableListOf()
        val navTitles = context.resources.getStringArray(R.array.nav_drawer_titles)
        val checked = BooleanArray(NavDrawerFragment.NAV_DRAWER_TAGS.size)
        for (i in NavDrawerFragment.NAV_DRAWER_TAGS.indices) {
            val tag = NavDrawerFragment.NAV_DRAWER_TAGS[i]
            if (!hiddenDrawerItems.contains(tag)) {
                checked[i] = true
            }
        }
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(R.string.drawer_preferences)
        builder.setMultiChoiceItems(navTitles, checked) { dialog: DialogInterface?, which: Int, isChecked: Boolean ->
            if (isChecked) {
                hiddenDrawerItems.remove(NavDrawerFragment.NAV_DRAWER_TAGS[which])
            } else {
                hiddenDrawerItems.add(NavDrawerFragment.NAV_DRAWER_TAGS[which])
            }
        }
        builder.setPositiveButton(R.string.confirm_label) { dialog: DialogInterface?, which: Int ->
            UserPreferences.hiddenDrawerItems = hiddenDrawerItems
            if (hiddenDrawerItems.contains(defaultPage)) {
                for (tag in NavDrawerFragment.NAV_DRAWER_TAGS) {
                    if (!hiddenDrawerItems.contains(tag)) {
                        defaultPage = tag
                        break
                    }
                }
            }
            callback?.run()
        }
        builder.setNegativeButton(R.string.cancel_label, null)
        builder.create().show()
    }
}
