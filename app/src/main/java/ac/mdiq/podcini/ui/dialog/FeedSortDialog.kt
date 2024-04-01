package ac.mdiq.podcini.ui.dialog

import android.content.Context
import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.R
import ac.mdiq.podcini.util.event.UnreadItemsUpdateEvent
import ac.mdiq.podcini.preferences.UserPreferences.feedOrder
import ac.mdiq.podcini.preferences.UserPreferences.setFeedOrder
import org.greenrobot.eventbus.EventBus

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
                EventBus.getDefault().post(UnreadItemsUpdateEvent())
            }
            d.dismiss()
        }
        dialog.show()
    }
}
