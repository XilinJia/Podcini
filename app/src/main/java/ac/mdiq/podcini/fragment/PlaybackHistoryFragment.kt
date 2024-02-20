package ac.mdiq.podcini.fragment

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.R
import ac.mdiq.podcini.core.dialog.ConfirmationDialog
import ac.mdiq.podcini.core.storage.DBReader
import ac.mdiq.podcini.core.storage.DBWriter
import ac.mdiq.podcini.event.playback.PlaybackHistoryEvent
import ac.mdiq.podcini.model.feed.FeedItem
import ac.mdiq.podcini.model.feed.FeedItemFilter
import androidx.annotation.OptIn
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class PlaybackHistoryFragment : EpisodesListFragment() {
    override fun getFragmentTag(): String {
        return "PlaybackHistoryFragment"
    }
    override fun getPrefName(): String {
        return "PlaybackHistoryFragment"
    }

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        toolbar.inflateMenu(R.menu.playback_history)
        toolbar.setTitle(R.string.playback_history_label)
        updateToolbar()
        emptyView.setIcon(R.drawable.ic_history)
        emptyView.setTitle(R.string.no_history_head_label)
        emptyView.setMessage(R.string.no_history_label)
        return root
    }

    override fun getFilter(): FeedItemFilter {
        return FeedItemFilter.unfiltered()
    }
    @OptIn(UnstableApi::class) override fun onMenuItemClick(item: MenuItem): Boolean {
        if (super.onOptionsItemSelected(item)) {
            return true
        }
        if (item.itemId == R.id.clear_history_item) {
            val conDialog: ConfirmationDialog = object : ConfirmationDialog(
                requireContext(),
                R.string.clear_history_label,
                R.string.clear_playback_history_msg) {
                override fun onConfirmButtonPressed(dialog: DialogInterface) {
                    dialog.dismiss()
                    DBWriter.clearPlaybackHistory()
                }
            }
            conDialog.createNewDialog().show()

            return true
        }
        return false
    }

    override fun updateToolbar() {
        // Not calling super, as we do not have a refresh button that could be updated
        toolbar.menu.findItem(R.id.clear_history_item).setVisible(episodes.isNotEmpty())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onHistoryUpdated(event: PlaybackHistoryEvent?) {
        loadItems()
        updateToolbar()
    }

    override fun loadData(): List<FeedItem> {
        return DBReader.getPlaybackHistory(0, page * EPISODES_PER_PAGE)
    }

    override fun loadMoreData(page: Int): List<FeedItem> {
        return DBReader.getPlaybackHistory((page - 1) * EPISODES_PER_PAGE, EPISODES_PER_PAGE)
    }

    override fun loadTotalItemCount(): Int {
        return DBReader.getPlaybackHistoryLength().toInt()
    }

    companion object {
        const val TAG: String = "PlaybackHistoryFragment"
    }
}
