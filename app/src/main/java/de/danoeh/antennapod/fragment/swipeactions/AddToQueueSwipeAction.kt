package de.danoeh.antennapod.fragment.swipeactions

import android.content.Context
import androidx.fragment.app.Fragment
import de.danoeh.antennapod.R
import de.danoeh.antennapod.core.storage.DBWriter
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter

class AddToQueueSwipeAction : SwipeAction {
    override fun getId(): String {
        return SwipeAction.ADD_TO_QUEUE
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_playlist_play
    }

    override fun getActionColor(): Int {
        return R.attr.colorAccent
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.add_to_queue_label)
    }

    override fun performAction(item: FeedItem, fragment: Fragment, filter: FeedItemFilter) {
        if (!item.isTagged(FeedItem.TAG_QUEUE)) {
            DBWriter.addQueueItem(fragment.requireContext(), item)
        } else {
            RemoveFromQueueSwipeAction().performAction(item, fragment, filter)
        }
    }

    override fun willRemove(filter: FeedItemFilter, item: FeedItem): Boolean {
        return filter.showQueued || filter.showNew
    }
}
