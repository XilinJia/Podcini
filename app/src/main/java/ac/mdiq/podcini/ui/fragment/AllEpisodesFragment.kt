package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.UserPreferences.allEpisodesSortOrder
import ac.mdiq.podcini.preferences.UserPreferences.prefFilterAllEpisodes
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter
import ac.mdiq.podcini.storage.model.feed.SortOrder
import ac.mdiq.podcini.ui.dialog.AllEpisodesFilterDialog
import ac.mdiq.podcini.ui.dialog.ItemSortDialog
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.apache.commons.lang3.StringUtils

/**
 * Shows all episodes (possibly filtered by user).
 */
@UnstableApi class AllEpisodesFragment : BaseEpisodesListFragment() {

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        Logd(TAG, "fragment onCreateView")

        toolbar.inflateMenu(R.menu.episodes)
        toolbar.setTitle(R.string.episodes_label)
        updateToolbar()
        updateFilterUi()
        txtvInformation.setOnClickListener {
            AllEpisodesFilterDialog.newInstance(getFilter()).show(childFragmentManager, null)
        }
        return root
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
    }

    override fun loadData(): List<FeedItem> {
        return DBReader.getEpisodes(0, page * EPISODES_PER_PAGE, getFilter(), allEpisodesSortOrder)
    }

    override fun loadMoreData(page: Int): List<FeedItem> {
        return DBReader.getEpisodes((page - 1) * EPISODES_PER_PAGE, EPISODES_PER_PAGE, getFilter(), allEpisodesSortOrder)
    }

    override fun loadTotalItemCount(): Int {
        return DBReader.getTotalEpisodeCount(getFilter())
    }

    override fun getFilter(): FeedItemFilter {
        return FeedItemFilter(prefFilterAllEpisodes)
    }

    override fun getFragmentTag(): String {
        return TAG
    }

    override fun getPrefName(): String {
        return PREF_NAME
    }

    @OptIn(UnstableApi::class) override fun onMenuItemClick(item: MenuItem): Boolean {
        if (super.onOptionsItemSelected(item)) return true

        when (item.itemId) {
            R.id.filter_items -> {
                AllEpisodesFilterDialog.newInstance(getFilter()).show(childFragmentManager, null)
                return true
            }
            R.id.action_favorites -> {
                val filter = ArrayList(getFilter().valuesList)
                if (filter.contains(FeedItemFilter.IS_FAVORITE)) filter.remove(FeedItemFilter.IS_FAVORITE)
                else filter.add(FeedItemFilter.IS_FAVORITE)

                onFilterChanged(FlowEvent.AllEpisodesFilterChangedEvent(HashSet(filter)))
                return true
            }
            R.id.episodes_sort -> {
                AllEpisodesSortDialog().show(childFragmentManager.beginTransaction(), "SortDialog")
                return true
            }
            else -> return false
        }
    }

    private fun procFlowEvents() {
        lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                when (event) {
                    is FlowEvent.AllEpisodesFilterChangedEvent -> onFilterChanged(event)
                    else -> {}
                }
            }
        }
    }

    fun onFilterChanged(event: FlowEvent.AllEpisodesFilterChangedEvent) {
        prefFilterAllEpisodes = StringUtils.join(event.filterValues, ",")
        updateFilterUi()
        page = 1
        loadItems()
    }

    private fun updateFilterUi() {
        swipeActions.setFilter(getFilter())
        if (getFilter().values.isNotEmpty()) {
            txtvInformation.visibility = View.VISIBLE
            emptyView.setMessage(R.string.no_all_episodes_filtered_label)
        } else {
            txtvInformation.visibility = View.GONE
            emptyView.setMessage(R.string.no_all_episodes_label)
        }
        toolbar.menu?.findItem(R.id.action_favorites)?.setIcon(if (getFilter().showIsFavorite) R.drawable.ic_star else R.drawable.ic_star_border)
    }

    class AllEpisodesSortDialog : ItemSortDialog() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            sortOrder = allEpisodesSortOrder
        }

        override fun onAddItem(title: Int, ascending: SortOrder, descending: SortOrder, ascendingIsDefault: Boolean) {
            if (ascending == SortOrder.DATE_OLD_NEW || ascending == SortOrder.DURATION_SHORT_LONG
                    || ascending == SortOrder.PLAYED_DATE_OLD_NEW || ascending == SortOrder.COMPLETED_DATE_OLD_NEW)
                super.onAddItem(title, ascending, descending, ascendingIsDefault)
        }

        override fun onSelectionChanged() {
            super.onSelectionChanged()
            allEpisodesSortOrder = sortOrder
            EventFlow.postEvent(FlowEvent.FeedListUpdateEvent(0))
        }
    }

    companion object {
        const val TAG: String = "AllEpisodesFragment"
        const val PREF_NAME: String = "PrefAllEpisodesFragment"
    }
}
