package de.danoeh.antennapod.fragment.swipeactions

import de.danoeh.antennapod.activity.MainActivity
import android.content.Context
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import de.danoeh.antennapod.R
import de.danoeh.antennapod.core.storage.DBReader
import de.danoeh.antennapod.core.storage.DBWriter
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter

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

    override fun performAction(item: FeedItem, fragment: Fragment, filter: FeedItemFilter) {
        val position: Int = DBReader.getQueueIDList().indexOf(item.id)

        DBWriter.removeQueueItem(fragment.requireActivity(), true, item)

        if (willRemove(filter, item)) {
            (fragment.requireActivity() as MainActivity).showSnackbarAbovePlayer(
                fragment.resources.getQuantityString(R.plurals.removed_from_queue_batch_label, 1, 1),
                Snackbar.LENGTH_LONG)
                .setAction(fragment.getString(R.string.undo)) { v ->
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
