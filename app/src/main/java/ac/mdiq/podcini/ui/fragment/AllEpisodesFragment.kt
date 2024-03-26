package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.UserPreferences.allEpisodesSortOrder
import ac.mdiq.podcini.preferences.UserPreferences.prefFilterAllEpisodes
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter
import ac.mdiq.podcini.storage.model.feed.SortOrder
import ac.mdiq.podcini.ui.dialog.AllEpisodesFilterDialog
import ac.mdiq.podcini.ui.dialog.AllEpisodesFilterDialog.AllEpisodesFilterChangedEvent
import ac.mdiq.podcini.ui.dialog.ItemSortDialog
import ac.mdiq.podcini.util.event.FeedListUpdateEvent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import org.apache.commons.lang3.StringUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

/**
 * Shows all episodes (possibly filtered by user).
 */
class AllEpisodesFragment : BaseEpisodesListFragment() {
    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        Log.d(TAG, "fragment onCreateView")

        toolbar.inflateMenu(R.menu.episodes)
        toolbar.setTitle(R.string.episodes_label)
        updateToolbar()
        updateFilterUi()
        txtvInformation.setOnClickListener {
            AllEpisodesFilterDialog.newInstance(getFilter()).show(childFragmentManager, null)
        }
        return root
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
        if (super.onOptionsItemSelected(item)) {
            return true
        }
        when (item.itemId) {
            R.id.filter_items -> {
                AllEpisodesFilterDialog.newInstance(getFilter()).show(childFragmentManager, null)
                return true
            }
            R.id.action_favorites -> {
                val filter = ArrayList(getFilter().valuesList)
                if (filter.contains(FeedItemFilter.IS_FAVORITE)) {
                    filter.remove(FeedItemFilter.IS_FAVORITE)
                } else {
                    filter.add(FeedItemFilter.IS_FAVORITE)
                }
                onFilterChanged(AllEpisodesFilterChangedEvent(HashSet(filter)))
                return true
            }
            R.id.episodes_sort -> {
                AllEpisodesSortDialog().show(childFragmentManager.beginTransaction(), "SortDialog")
                return true
            }
            else -> return false
        }
    }

    @Subscribe
    fun onFilterChanged(event: AllEpisodesFilterChangedEvent) {
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
        toolbar.menu?.findItem(R.id.action_favorites)?.setIcon(
            if (getFilter().showIsFavorite) R.drawable.ic_star else R.drawable.ic_star_border)
    }

    class AllEpisodesSortDialog : ItemSortDialog() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            sortOrder = allEpisodesSortOrder
        }

        override fun onAddItem(title: Int, ascending: SortOrder, descending: SortOrder, ascendingIsDefault: Boolean) {
            if (ascending == SortOrder.DATE_OLD_NEW || ascending == SortOrder.DURATION_SHORT_LONG) {
                super.onAddItem(title, ascending, descending, ascendingIsDefault)
            }
        }

        override fun onSelectionChanged() {
            super.onSelectionChanged()
            allEpisodesSortOrder = sortOrder
            EventBus.getDefault().post(FeedListUpdateEvent(0))
        }
    }

    companion object {
        const val TAG: String = "EpisodesFragment"
        const val PREF_NAME: String = "PrefAllEpisodesFragment"
    }
}
