package de.danoeh.antennapod.fragment.swipeactions

import android.content.Context
import androidx.fragment.app.Fragment
import de.danoeh.antennapod.R
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter

class ShowFirstSwipeDialogAction : SwipeAction {
    override fun getId(): String {
        return "SHOW_FIRST_SWIPE_DIALOG"
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_settings
    }

    override fun getActionColor(): Int {
        return R.attr.icon_gray
    }

    override fun getTitle(context: Context): String {
        return ""
    }

    override fun performAction(item: FeedItem, fragment: Fragment, filter: FeedItemFilter) {
        //handled in SwipeActions
    }

    override fun willRemove(filter: FeedItemFilter, item: FeedItem): Boolean {
        return false
    }
}
