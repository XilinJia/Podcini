package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.storage.database.Episodes.getEpisodes
import ac.mdiq.podcini.storage.database.Episodes.getEpisodesCount
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.dialog.EpisodeFilterDialog
import ac.mdiq.podcini.ui.dialog.EpisodeSortDialog
import ac.mdiq.podcini.ui.dialog.SwitchQueueDialog
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
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
import kotlin.math.min

/**
 * Shows all episodes (possibly filtered by user).
 */
@UnstableApi class AllEpisodesFragment : BaseEpisodesFragment() {

    private var allEpisodes: List<Episode> = listOf()

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        Logd(TAG, "fragment onCreateView")

        toolbar.inflateMenu(R.menu.episodes)
        toolbar.setTitle(R.string.episodes_label)
        updateToolbar()
        txtvInformation.visibility = View.VISIBLE
        txtvInformation.setOnClickListener {
            AllEpisodesFilterDialog.newInstance(getFilter()).show(childFragmentManager, null)
        }
        return root
    }

    override fun onDestroyView() {
        allEpisodes = listOf()
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
    }

    private var loadItemsRunning = false
    override fun loadData(): List<Episode> {
        val filter = getFilter()
        if (!loadItemsRunning) {
            loadItemsRunning = true
            allEpisodes = getEpisodes(0, Int.MAX_VALUE, filter, allEpisodesSortOrder, false)
            Logd(TAG, "loadData ${allEpisodes.size}")
            loadItemsRunning = false
        }
        if (allEpisodes.isEmpty()) return listOf()
        allEpisodes = allEpisodes.filter { filter.matchesForQueues(it) }
        return allEpisodes.subList(0, min(allEpisodes.size-1, page * EPISODES_PER_PAGE))
    }

    override fun loadMoreData(page: Int): List<Episode> {
        val offset = (page - 1) * EPISODES_PER_PAGE
        if (offset >= allEpisodes.size) return listOf()
        val toIndex = offset + EPISODES_PER_PAGE
        return allEpisodes.subList(offset, min(allEpisodes.size, toIndex))
    }

    override fun loadTotalItemCount(): Int {
        return getEpisodesCount(getFilter())
    }

    override fun getFilter(): EpisodeFilter {
        return EpisodeFilter(prefFilterAllEpisodes)
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
                if (filter.contains(EpisodeFilter.States.is_favorite.name)) filter.remove(EpisodeFilter.States.is_favorite.name)
                else filter.add(EpisodeFilter.States.is_favorite.name)
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
                    is FlowEvent.AllEpisodesSortEvent -> {
                        page = 1
                        loadItems()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun onFilterChanged(event: FlowEvent.AllEpisodesFilterEvent) {
        prefFilterAllEpisodes = StringUtils.join(event.filterValues, ",")
        page = 1
        loadItems()
    }

    override fun updateToolbar() {
        swipeActions.setFilter(getFilter())
        if (getFilter().values.isNotEmpty()) {
            txtvInformation.text = "${adapter.totalNumberOfItems} episodes - ${getString(R.string.filtered_label)}"
            emptyView.setMessage(R.string.no_all_episodes_filtered_label)
        } else {
            txtvInformation.text = "${adapter.totalNumberOfItems} episodes"
            emptyView.setMessage(R.string.no_all_episodes_label)
        }
        toolbar.menu?.findItem(R.id.action_favorites)?.setIcon(if (getFilter().showIsFavorite) R.drawable.ic_star else R.drawable.ic_star_border)
    }

    class AllEpisodesSortDialog : EpisodeSortDialog() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            sortOrder = allEpisodesSortOrder
        }
        override fun onAddItem(title: Int, ascending: EpisodeSortOrder, descending: EpisodeSortOrder, ascendingIsDefault: Boolean) {
            if (ascending == EpisodeSortOrder.DATE_OLD_NEW
                    || ascending == EpisodeSortOrder.DURATION_SHORT_LONG
                    || ascending == EpisodeSortOrder.PLAYED_DATE_OLD_NEW
                    || ascending == EpisodeSortOrder.DOWNLOAD_DATE_OLD_NEW
                    || ascending == EpisodeSortOrder.COMPLETED_DATE_OLD_NEW
                    || ascending == EpisodeSortOrder.EPISODE_TITLE_A_Z)
                super.onAddItem(title, ascending, descending, ascendingIsDefault)
        }
        override fun onSelectionChanged() {
            super.onSelectionChanged()
            allEpisodesSortOrder = sortOrder
            EventFlow.postEvent(FlowEvent.AllEpisodesSortEvent())
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
        var allEpisodesSortOrder: EpisodeSortOrder?
            get() = EpisodeSortOrder.fromCodeString(appPrefs.getString(UserPreferences.Prefs.prefEpisodesSort.name, "" + EpisodeSortOrder.DATE_NEW_OLD.code))
            set(s) {
                appPrefs.edit().putString(UserPreferences.Prefs.prefEpisodesSort.name, "" + s!!.code).apply()
            }
        var prefFilterAllEpisodes: String
            get() = appPrefs.getString(UserPreferences.Prefs.prefEpisodesFilter.name, "")?:""
            set(filter) {
                appPrefs.edit().putString(UserPreferences.Prefs.prefEpisodesFilter.name, filter).apply()
            }
    }
}
