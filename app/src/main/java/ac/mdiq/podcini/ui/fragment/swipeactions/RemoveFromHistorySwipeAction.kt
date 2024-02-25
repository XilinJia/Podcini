package ac.mdiq.podcini.ui.fragment.swipeactions

import ac.mdiq.podcini.ui.activity.MainActivity
import android.content.Context
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import java.util.*

class RemoveFromHistorySwipeAction : SwipeAction {
    override fun getId(): String {
        return SwipeAction.REMOVE_FROM_HISTORY
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_history_remove
    }

    override fun getActionColor(): Int {
        return R.attr.icon_purple
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.remove_history_label)
    }

    @OptIn(UnstableApi::class) override fun performAction(item: FeedItem, fragment: Fragment, filter: FeedItemFilter) {
        val playbackCompletionDate: Date? = item.media?.getPlaybackCompletionDate()

        DBWriter.deleteFromPlaybackHistory(item)

        (fragment.requireActivity() as MainActivity)
            .showSnackbarAbovePlayer(R.string.removed_history_label, Snackbar.LENGTH_LONG)
            .setAction(fragment.getString(R.string.undo)
            ) { if (playbackCompletionDate != null) DBWriter.addItemToPlaybackHistory(item.media, playbackCompletionDate) }
    }

    override fun willRemove(filter: FeedItemFilter, item: FeedItem): Boolean {
        return true
    }

    companion object {
        const val TAG: String = "RemoveFromHistorySwipeAction"
    }
}