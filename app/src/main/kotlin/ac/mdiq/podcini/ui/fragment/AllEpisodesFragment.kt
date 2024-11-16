package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.storage.database.Episodes.getEpisodes
import ac.mdiq.podcini.storage.database.Episodes.getEpisodesCount
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.util.Logd
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import org.apache.commons.lang3.StringUtils


class AllEpisodesFragment : BaseEpisodesFragment() {
    private var allEpisodes: List<Episode> = listOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        Logd(TAG, "fragment onCreateView")

        toolbar.inflateMenu(R.menu.episodes)
        toolbar.setTitle(R.string.episodes_label)
        sortOrder = allEpisodesSortOrder ?: EpisodeSortOrder.DATE_NEW_OLD
        updateToolbar()
//        txtvInformation.setOnClickListener {
//            AllEpisodesFilterDialog.newInstance(getFilter()).show(childFragmentManager, null)
//        }
        return root
    }

    override fun onDestroyView() {
        allEpisodes = listOf()
        super.onDestroyView()
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
//        allEpisodes = allEpisodes.filter { filter.matchesForQueues(it) }
        return allEpisodes
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

     override fun onMenuItemClick(item: MenuItem): Boolean {
        if (super.onOptionsItemSelected(item)) return true

        when (item.itemId) {
            R.id.filter_items -> showFilterDialog = true
            R.id.episodes_sort -> showSortDialog = true
            else -> return false
        }
        return true
    }

    override fun updateToolbar() {
        swipeActions.setFilter(getFilter())
        var info = "${episodes.size} episodes"
        if (getFilter().properties.isNotEmpty()) info += " - ${getString(R.string.filtered_label)}"
        infoBarText.value = info
    }

    override fun onFilterChanged(filterValues: Set<String>) {
        prefFilterAllEpisodes = StringUtils.join(filterValues, ",")
        page = 1
        loadItems()
    }

    override fun onSort(order: EpisodeSortOrder) {
        allEpisodesSortOrder = order
        page = 1
        loadItems()
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
