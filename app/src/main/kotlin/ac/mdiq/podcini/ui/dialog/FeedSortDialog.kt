package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.UserPreferences.feedOrder
import ac.mdiq.podcini.preferences.UserPreferences.setFeedOrder
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.content.Context
import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object FeedSortDialog {
    fun showDialog(context: Context) {
        val dialog = MaterialAlertDialogBuilder(context)
        dialog.setTitle(context.getString(R.string.pref_nav_drawer_feed_order_title))
        dialog.setNegativeButton(android.R.string.cancel) { d: DialogInterface, _: Int -> d.dismiss() }

        val selected = feedOrder
        val entryValues = listOf(*context.resources.getStringArray(R.array.nav_drawer_feed_order_values))
        val selectedIndex = entryValues.indexOf("" + selected)

        val items = context.resources.getStringArray(R.array.nav_drawer_feed_order_options)
        dialog.setSingleChoiceItems(items, selectedIndex) { d: DialogInterface, which: Int ->
            if (selectedIndex != which) {
                setFeedOrder(entryValues[which])
                //Update subscriptions
                EventFlow.postEvent(FlowEvent.FeedsSortedEvent())
            }
            d.dismiss()
        }
        dialog.show()
    }
}
