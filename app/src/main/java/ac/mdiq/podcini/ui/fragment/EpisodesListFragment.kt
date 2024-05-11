package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter
import ac.mdiq.podcini.ui.dialog.AllEpisodesFilterDialog.AllEpisodesFilterChangedEvent
import ac.mdiq.podcini.util.Logd
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import org.greenrobot.eventbus.Subscribe
import kotlin.math.min

/**
 * Shows all episodes (possibly filtered by user).
 */
class EpisodesListFragment : BaseEpisodesListFragment() {

    private val episodeList: MutableList<FeedItem> = mutableListOf()

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        Logd(TAG, "fragment onCreateView")

//        val episodes_ = requireArguments().getSerializable(EXTRA_EPISODES) as? ArrayList<FeedItem>
//        if (episodes_ != null) episodeList.addAll(episodes_)

        toolbar.inflateMenu(R.menu.episodes)
        toolbar.setTitle(R.string.episodes_label)
        updateToolbar()
        listAdapter.setOnSelectModeListener(null)
//        updateFilterUi()
//        txtvInformation.setOnClickListener {
//            AllEpisodesFilterDialog.newInstance(getFilter()).show(childFragmentManager, null)
//        }
        return root
    }

    fun setEpisodes(episodeList_: MutableList<FeedItem>) {
        episodeList.clear()
        episodeList.addAll(episodeList_)
    }

    override fun loadData(): List<FeedItem> {
        if (episodeList.isEmpty()) return listOf()
        return episodeList.subList(0, min(episodeList.size-1, page * EPISODES_PER_PAGE))
    }

    override fun loadMoreData(page: Int): List<FeedItem> {
        return episodeList.subList((page - 1) * EPISODES_PER_PAGE, min(episodeList.size, page * EPISODES_PER_PAGE))
    }

    override fun loadTotalItemCount(): Int {
        return episodeList.size
    }

    override fun getFilter(): FeedItemFilter {
        return FeedItemFilter.unfiltered()
    }

    override fun getFragmentTag(): String {
        return TAG
    }

    override fun getPrefName(): String {
        return PREF_NAME
    }

    override fun updateToolbar() {
        binding.toolbar.menu.findItem(R.id.episodes_sort).setVisible(false)
        binding.toolbar.menu.findItem(R.id.refresh_item).setVisible(false)
        binding.toolbar.menu.findItem(R.id.action_search).setVisible(false)
        binding.toolbar.menu.findItem(R.id.action_favorites).setVisible(false)
        binding.toolbar.menu.findItem(R.id.filter_items).setVisible(false)
    }

    @OptIn(UnstableApi::class) override fun onMenuItemClick(item: MenuItem): Boolean {
        if (super.onOptionsItemSelected(item)) return true

        when (item.itemId) {
//            R.id.filter_items -> {
//                AllEpisodesFilterDialog.newInstance(getFilter()).show(childFragmentManager, null)
//                return true
//            }
//            R.id.episodes_sort -> {
//                AllEpisodesSortDialog().show(childFragmentManager.beginTransaction(), "SortDialog")
//                return true
//            }
            else -> return false
        }
    }

    @Subscribe
    fun onFilterChanged(event: AllEpisodesFilterChangedEvent) {
//        prefFilterAllEpisodes = StringUtils.join(event.filterValues, ",")
//        updateFilterUi()
        page = 1
//        loadItems()
    }

    private fun updateFilterUi() {
//        swipeActions.setFilter(getFilter())
//        when {
//            getFilter().values.isNotEmpty() -> {
//                txtvInformation.visibility = View.VISIBLE
//                emptyView.setMessage(R.string.no_all_episodes_filtered_label)
//            }
//            else -> {
//                txtvInformation.visibility = View.GONE
//                emptyView.setMessage(R.string.no_all_episodes_label)
//            }
//        }
//        toolbar.menu?.findItem(R.id.action_favorites)?.setIcon(
//            if (getFilter().showIsFavorite) R.drawable.ic_star else R.drawable.ic_star_border)
    }

//    class AllEpisodesSortDialog : ItemSortDialog() {
//        override fun onCreate(savedInstanceState: Bundle?) {
//            super.onCreate(savedInstanceState)
//            sortOrder = allEpisodesSortOrder
//        }
//
//        override fun onAddItem(title: Int, ascending: SortOrder, descending: SortOrder, ascendingIsDefault: Boolean) {
//            if (ascending == SortOrder.DATE_OLD_NEW || ascending == SortOrder.DURATION_SHORT_LONG) {
//                super.onAddItem(title, ascending, descending, ascendingIsDefault)
//            }
//        }
//
//        override fun onSelectionChanged() {
//            super.onSelectionChanged()
//            allEpisodesSortOrder = sortOrder
//            EventBus.getDefault().post(FeedListUpdateEvent(0))
//        }
//    }

    companion object {
        const val TAG: String = "EpisodesListFragment"
        const val PREF_NAME: String = "EpisodesListFragment"
        const val EXTRA_EPISODES: String = "episodes_list"

        @JvmStatic
        fun newInstance(episodes: MutableList<FeedItem>): EpisodesListFragment {
            val i = EpisodesListFragment()
            i.setEpisodes(episodes)
            return i
        }

    }
}
