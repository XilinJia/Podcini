package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.UserPreferences.allEpisodesSortOrder
import ac.mdiq.podcini.preferences.UserPreferences.prefFilterAllEpisodes
import ac.mdiq.podcini.storage.database.Episodes.getEpisodes
import ac.mdiq.podcini.storage.database.Episodes.getEpisodesCount
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.utils.EpisodeFilter
import ac.mdiq.podcini.storage.utils.SortOrder
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.dialog.EpisodeFilterDialog
import ac.mdiq.podcini.ui.dialog.EpisodeSortDialog
import ac.mdiq.podcini.ui.dialog.SwitchQueueDialog
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.apache.commons.lang3.StringUtils

/**
 * Shows all episodes (possibly filtered by user).
 */
@UnstableApi class AllEpisodesFragment : BaseEpisodesFragment() {

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

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
    }

    override fun loadData(): List<Episode> {
        return getEpisodes(0, page * EPISODES_PER_PAGE, getFilter(), allEpisodesSortOrder)
    }

    override fun loadMoreData(page: Int): List<Episode> {
        return getEpisodes((page - 1) * EPISODES_PER_PAGE, EPISODES_PER_PAGE, getFilter(), allEpisodesSortOrder)
    }

    override fun loadTotalItemCount(): Int {
        return getEpisodesCount(getFilter())
    }

    override fun getFilter(): EpisodeFilter {
        return EpisodeFilter(prefFilterAllEpisodes)
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
            R.id.filter_items -> AllEpisodesFilterDialog.newInstance(getFilter()).show(childFragmentManager, null)
            R.id.action_favorites -> {
                val filter = ArrayList(getFilter().valuesList)
                if (filter.contains(EpisodeFilter.IS_FAVORITE)) filter.remove(EpisodeFilter.IS_FAVORITE)
                else filter.add(EpisodeFilter.IS_FAVORITE)
                onFilterChanged(FlowEvent.AllEpisodesFilterEvent(HashSet(filter)))
            }
            R.id.episodes_sort -> AllEpisodesSortDialog().show(childFragmentManager.beginTransaction(), "SortDialog")
            R.id.switch_queue -> SwitchQueueDialog(activity as MainActivity).show()
            else -> return false
        }
        return true
    }

    private var eventSink: Job?     = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    private fun procFlowEvents() {
        if (eventSink != null) return
        eventSink = lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.AllEpisodesFilterEvent -> onFilterChanged(event)
                    else -> {}
                }
            }
        }
    }

    private fun onFilterChanged(event: FlowEvent.AllEpisodesFilterEvent) {
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

    class AllEpisodesSortDialog : EpisodeSortDialog() {
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

    class AllEpisodesFilterDialog : EpisodeFilterDialog() {
        override fun onFilterChanged(newFilterValues: Set<String>) {
            EventFlow.postEvent(FlowEvent.AllEpisodesFilterEvent(newFilterValues))
        }
        companion object {
            fun newInstance(filter: EpisodeFilter?): AllEpisodesFilterDialog {
                val dialog = AllEpisodesFilterDialog()
                dialog.filter = filter
                return dialog
            }
        }
    }

    companion object {
        val TAG = AllEpisodesFragment::class.simpleName ?: "Anonymous"
        const val PREF_NAME: String = "PrefAllEpisodesFragment"
    }
}
