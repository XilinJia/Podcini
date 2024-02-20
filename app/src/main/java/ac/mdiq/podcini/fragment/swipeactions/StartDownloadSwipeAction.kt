package ac.mdiq.podcini.fragment.swipeactions

import android.content.Context
import androidx.fragment.app.Fragment
import ac.mdiq.podcini.R
import ac.mdiq.podcini.adapter.actionbutton.DownloadActionButton
import ac.mdiq.podcini.model.feed.FeedItem
import ac.mdiq.podcini.model.feed.FeedItemFilter

class StartDownloadSwipeAction : SwipeAction {
    override fun getId(): String {
        return SwipeAction.START_DOWNLOAD
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_download
    }

    override fun getActionColor(): Int {
        return R.attr.icon_green
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.download_label)
    }

    override fun performAction(item: FeedItem, fragment: Fragment, filter: FeedItemFilter) {
        if (!item.isDownloaded && !item.feed!!.isLocalFeed) {
            DownloadActionButton(item)
                .onClick(fragment.requireContext())
        }
    }

    override fun willRemove(filter: FeedItemFilter, item: FeedItem): Boolean {
        return false
    }
}
