package ac.mdiq.podcini.ui.fragment.swipeactions

import android.content.Context
import androidx.fragment.app.Fragment
import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.menuhandler.FeedItemMenuHandler.markReadWithUndo
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter

class TogglePlaybackStateSwipeAction : SwipeAction {
    override fun getId(): String {
        return SwipeAction.TOGGLE_PLAYED
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_mark_played
    }

    override fun getActionColor(): Int {
        return R.attr.icon_gray
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.toggle_played_label)
    }

    override fun performAction(item: FeedItem, fragment: Fragment, filter: FeedItemFilter) {
        val newState = if (item.playState == FeedItem.UNPLAYED) FeedItem.PLAYED else FeedItem.UNPLAYED
        markReadWithUndo(fragment, item, newState, willRemove(filter, item))
    }

    override fun willRemove(filter: FeedItemFilter, item: FeedItem): Boolean {
        return if (item.playState == FeedItem.NEW) {
            filter.showPlayed || filter.showNew
        } else {
            filter.showUnplayed || filter.showPlayed || filter.showNew
        }
    }
}
