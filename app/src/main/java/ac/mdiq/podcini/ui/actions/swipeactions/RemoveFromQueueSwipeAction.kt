package ac.mdiq.podcini.ui.actions.swipeactions

import ac.mdiq.podcini.ui.activity.MainActivity
import android.content.Context
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

class RemoveFromQueueSwipeAction : SwipeAction {
    override fun getId(): String {
        return SwipeAction.REMOVE_FROM_QUEUE
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_playlist_remove
    }

    override fun getActionColor(): Int {
        return R.attr.colorAccent
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.remove_from_queue_label)
    }

    @OptIn(UnstableApi::class) override fun performAction(item: FeedItem, fragment: Fragment, filter: FeedItemFilter) {
        val position: Int = DBReader.getQueueIDList().indexOf(item.id)

        DBWriter.removeQueueItem(fragment.requireActivity(), true, item)

        if (willRemove(filter, item)) {
            (fragment.requireActivity() as MainActivity).showSnackbarAbovePlayer(
                fragment.resources.getQuantityString(R.plurals.removed_from_queue_batch_label, 1, 1),
                Snackbar.LENGTH_LONG)
                .setAction(fragment.getString(R.string.undo)) {
                    DBWriter.addQueueItemAt(fragment.requireActivity(),
                        item.id,
                        position,
                        false)
                }
        }
    }

    override fun willRemove(filter: FeedItemFilter, item: FeedItem): Boolean {
        return filter.showQueued || filter.showNotQueued
    }
}
