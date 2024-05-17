package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter
import ac.mdiq.podcini.storage.model.feed.SortOrder
import ac.mdiq.podcini.ui.actions.menuhandler.MenuItemUtils
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.adapter.EpisodeItemListAdapter
import ac.mdiq.podcini.ui.dialog.ConfirmationDialog
import ac.mdiq.podcini.ui.dialog.ItemSortDialog
import ac.mdiq.podcini.ui.statistics.StatisticsFragment
import ac.mdiq.podcini.ui.statistics.subscriptions.DatesFilterDialog
import ac.mdiq.podcini.ui.view.viewholder.EpisodeItemViewHolder
import ac.mdiq.podcini.util.DateFormatter
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.content.DialogInterface
import android.os.Bundle
import android.view.*
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

@UnstableApi class PlaybackHistoryFragment : BaseEpisodesListFragment() {

    private var sortOrder : SortOrder = SortOrder.PLAYED_DATE_NEW_OLD
    private var startDate : Long = 0L
    private var endDate : Long = Date().time

    override fun getFragmentTag(): String {
        return "PlaybackHistoryFragment"
    }

    override fun getPrefName(): String {
        return "PlaybackHistoryFragment"
    }

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)

        Logd(TAG, "fragment onCreateView")
        toolbar.inflateMenu(R.menu.playback_history)
        toolbar.setTitle(R.string.playback_history_label)
        updateToolbar()
        emptyView.setIcon(R.drawable.ic_history)
        emptyView.setTitle(R.string.no_history_head_label)
        emptyView.setMessage(R.string.no_history_label)

        return root
    }

    override fun createListAdaptor() {
        listAdapter = object : EpisodeItemListAdapter(activity as MainActivity) {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeItemViewHolder {
                return object: EpisodeItemViewHolder(mainActivityRef.get()!!, parent) {
                    override fun setPubDate(item: FeedItem) {
                        val playDate = Date(item.media?.getLastPlayedTime()?:0L)
                        pubDate.text = DateFormatter.formatAbbrev(activity, playDate)
                        pubDate.setContentDescription(DateFormatter.formatForAccessibility(playDate))
                    }
                }
            }
            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
                super.onCreateContextMenu(menu, v, menuInfo)
                MenuItemUtils.setOnClickListeners(menu) { item: MenuItem -> this@PlaybackHistoryFragment.onContextItemSelected(item) }
            }
        }
        listAdapter.setOnSelectModeListener(this)
        recyclerView.adapter = listAdapter
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
    }

    override fun getFilter(): FeedItemFilter {
        return FeedItemFilter.unfiltered()
    }

    @OptIn(UnstableApi::class) override fun onMenuItemClick(item: MenuItem): Boolean {
        if (super.onOptionsItemSelected(item)) return true
        when (item.itemId) {
            R.id.episodes_sort -> {
                HistorySortDialog().show(childFragmentManager.beginTransaction(), "SortDialog")
                return true
            }
            R.id.filter_items -> {
                val dialog = object: DatesFilterDialog(requireContext(), 0L) {
                    override fun initParams() {
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.YEAR, -1) // subtract 1 year
                        timeFilterFrom = calendar.timeInMillis
                        showMarkPlayed = false
                    }
                    override fun callback(timeFilterFrom: Long, timeFilterTo: Long, includeMarkedAsPlayed: Boolean) {
                        EventFlow.postEvent(FlowEvent.HistoryEvent(sortOrder, timeFilterFrom, timeFilterTo))
                    }
                }
                dialog.show()
                return true
            }
            R.id.clear_history_item -> {
                val conDialog: ConfirmationDialog = object : ConfirmationDialog(requireContext(), R.string.clear_history_label, R.string.clear_playback_history_msg) {
                    override fun onConfirmButtonPressed(dialog: DialogInterface) {
                        dialog.dismiss()
                        DBWriter.clearPlaybackHistory()
                    }
                }
                conDialog.createNewDialog().show()
                return true
            }
        }
        return false
    }

    override fun updateToolbar() {
        // Not calling super, as we do not have a refresh button that could be updated
        toolbar.menu.findItem(R.id.episodes_sort).setVisible(episodes.isNotEmpty())
        toolbar.menu.findItem(R.id.filter_items).setVisible(episodes.isNotEmpty())
        toolbar.menu.findItem(R.id.clear_history_item).setVisible(episodes.isNotEmpty())
    }

    private fun procFlowEvents() {
        lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                when (event) {
                    is FlowEvent.HistoryEvent -> {
                        sortOrder = event.sortOrder
                        if (event.startDate > 0) startDate = event.startDate
                        endDate = event.endDate
                        loadItems()
                        updateToolbar()
                    }
                    else -> {}
                }
            }
        }
    }

    override fun loadData(): List<FeedItem> {
        val hList = DBReader.getPlaybackHistory(0, page * EPISODES_PER_PAGE, startDate, endDate, sortOrder).toMutableList()
//        FeedItemPermutors.getPermutor(sortOrder).reorder(hList)
        return hList
    }

    override fun loadMoreData(page: Int): List<FeedItem> {
        val hList = DBReader.getPlaybackHistory((page - 1) * EPISODES_PER_PAGE, EPISODES_PER_PAGE, startDate, endDate, sortOrder).toMutableList()
//        FeedItemPermutors.getPermutor(sortOrder).reorder(hList)
        return hList
    }

    override fun loadTotalItemCount(): Int {
        return DBReader.getPlaybackHistoryLength().toInt()
    }

    class HistorySortDialog : ItemSortDialog() {
        override fun onAddItem(title: Int, ascending: SortOrder, descending: SortOrder, ascendingIsDefault: Boolean) {
            if (ascending == SortOrder.DATE_OLD_NEW || ascending == SortOrder.PLAYED_DATE_OLD_NEW
                    || ascending == SortOrder.COMPLETED_DATE_OLD_NEW
                    || ascending == SortOrder.DURATION_SHORT_LONG || ascending == SortOrder.EPISODE_TITLE_A_Z
                    || ascending == SortOrder.SIZE_SMALL_LARGE || ascending == SortOrder.FEED_TITLE_A_Z) {
                super.onAddItem(title, ascending, descending, ascendingIsDefault)
            }
        }
        override fun onSelectionChanged() {
            super.onSelectionChanged()
            EventFlow.postEvent(FlowEvent.HistoryEvent(sortOrder?: SortOrder.PLAYED_DATE_NEW_OLD))
        }
    }

    companion object {
        const val TAG: String = "PlaybackHistoryFragment"
    }
}
