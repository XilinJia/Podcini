package ac.mdiq.podvinci.fragment.swipeactions

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.core.storage.DBWriter
import ac.mdiq.podvinci.model.feed.FeedItem
import ac.mdiq.podvinci.model.feed.FeedItemFilter
import ac.mdiq.podvinci.view.LocalDeleteModal.showLocalFeedDeleteWarningIfNecessary

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
        if (!item.isDownloaded && !item.feed!!.isLocalFeed) {
            return
        }
        showLocalFeedDeleteWarningIfNecessary(fragment.requireContext(), listOf(item)) {
            DBWriter.deleteFeedMediaOfItem(fragment.requireContext(), item.media!!.id) }
    }

    override fun willRemove(filter: FeedItemFilter, item: FeedItem): Boolean {
        return filter.showDownloaded && (item.isDownloaded || item.feed!!.isLocalFeed)
    }
}