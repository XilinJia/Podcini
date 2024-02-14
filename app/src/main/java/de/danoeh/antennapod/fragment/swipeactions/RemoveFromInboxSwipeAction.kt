package de.danoeh.antennapod.fragment.swipeactions

import android.content.Context
import androidx.fragment.app.Fragment
import de.danoeh.antennapod.R
import de.danoeh.antennapod.menuhandler.FeedItemMenuHandler.markReadWithUndo
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter

class RemoveFromInboxSwipeAction : SwipeAction {
    override fun getId(): String {
        return SwipeAction.REMOVE_FROM_INBOX
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_check
    }

    override fun getActionColor(): Int {
        return R.attr.icon_purple
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.remove_inbox_label)
    }

    override fun performAction(item: FeedItem, fragment: Fragment, filter: FeedItemFilter) {
        if (item.isNew) {
            markReadWithUndo(fragment, item, FeedItem.UNPLAYED, willRemove(filter, item))
        }
    }

    override fun willRemove(filter: FeedItemFilter, item: FeedItem): Boolean {
        return filter.showNew
    }
}
