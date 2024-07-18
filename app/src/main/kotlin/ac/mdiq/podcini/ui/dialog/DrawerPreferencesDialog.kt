package ac.mdiq.podcini.ui.dialog

import android.content.Context
import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.fragment.NavDrawerFragment
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.defaultPage
import ac.mdiq.podcini.preferences.UserPreferences.hiddenDrawerItems
import ac.mdiq.podcini.util.Logd
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
object DrawerPreferencesDialog {

    fun show(context: Context, callback: Runnable?) {
        val hiddenItems = hiddenDrawerItems.map { it.trim() }.toMutableSet()
        val navTitles = context.resources.getStringArray(R.array.nav_drawer_titles)
        val checked = BooleanArray(NavDrawerFragment.NAV_DRAWER_TAGS.size)
        for (i in NavDrawerFragment.NAV_DRAWER_TAGS.indices) {
            val tag = NavDrawerFragment.NAV_DRAWER_TAGS[i]
            if (!hiddenItems.contains(tag)) checked[i] = true
        }
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(R.string.drawer_preferences)
        builder.setMultiChoiceItems(navTitles, checked) { _: DialogInterface?, which: Int, isChecked: Boolean ->
            if (isChecked) hiddenItems.remove(NavDrawerFragment.NAV_DRAWER_TAGS[which])
            else hiddenItems.add((NavDrawerFragment.NAV_DRAWER_TAGS[which]).trim())
        }
        builder.setPositiveButton(R.string.confirm_label) { _: DialogInterface?, _: Int ->
            hiddenDrawerItems = hiddenItems.toList()
            if (hiddenItems.contains(defaultPage)) {
                for (tag in NavDrawerFragment.NAV_DRAWER_TAGS) {
                    if (!hiddenItems.contains(tag)) {
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
