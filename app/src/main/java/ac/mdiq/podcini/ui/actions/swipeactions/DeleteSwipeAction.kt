package ac.mdiq.podcini.ui.actions.swipeactions

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter
import ac.mdiq.podcini.ui.utils.LocalDeleteModal.showLocalFeedDeleteWarningIfNecessary

class DeleteSwipeAction : SwipeAction {
    override fun getId(): String {
        return SwipeAction.DELETE
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_delete
    }

    override fun getActionColor(): Int {
        return R.attr.icon_red
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.delete_episode_label)
    }

    @UnstableApi override fun performAction(item: FeedItem, fragment: Fragment, filter: FeedItemFilter) {
        if (!item.isDownloaded && item.feed?.isLocalFeed != true) return

        showLocalFeedDeleteWarningIfNecessary(fragment.requireContext(), listOf(item)) {
            if (item.media != null) DBWriter.deleteFeedMediaOfItem(fragment.requireContext(), item.media!!.id)
        }
    }

    override fun willRemove(filter: FeedItemFilter, item: FeedItem): Boolean {
        return filter.showDownloaded && (item.isDownloaded || item.feed!!.isLocalFeed)
    }
}
