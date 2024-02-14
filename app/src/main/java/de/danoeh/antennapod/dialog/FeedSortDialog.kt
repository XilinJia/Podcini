package de.danoeh.antennapod.dialog

import android.content.Context
import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.event.UnreadItemsUpdateEvent
import de.danoeh.antennapod.storage.preferences.UserPreferences.feedOrder
import de.danoeh.antennapod.storage.preferences.UserPreferences.setFeedOrder
import org.greenrobot.eventbus.EventBus

object FeedSortDialog {
    fun showDialog(context: Context) {
        val dialog = MaterialAlertDialogBuilder(context)
        dialog.setTitle(context.getString(R.string.pref_nav_drawer_feed_order_title))
        dialog.setNegativeButton(android.R.string.cancel) { d: DialogInterface, listener: Int -> d.dismiss() }

        val selected = feedOrder
        val entryValues =
            listOf(*context.resources.getStringArray(R.array.nav_drawer_feed_order_values))
        val selectedIndex = entryValues.indexOf("" + selected)

        val items = context.resources.getStringArray(R.array.nav_drawer_feed_order_options)
        dialog.setSingleChoiceItems(items, selectedIndex) { d: DialogInterface, which: Int ->
            if (selectedIndex != which) {
                setFeedOrder(entryValues[which])
                //Update subscriptions
                EventBus.getDefault().post(UnreadItemsUpdateEvent())
            }
            d.dismiss()
        }
        dialog.show()
    }
}
