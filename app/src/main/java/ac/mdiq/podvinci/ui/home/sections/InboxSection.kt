package ac.mdiq.podvinci.ui.home.sections

import ac.mdiq.podvinci.activity.MainActivity
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.core.util.Pair
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.adapter.EpisodeItemListAdapter
import ac.mdiq.podvinci.core.menuhandler.MenuItemUtils
import ac.mdiq.podvinci.core.storage.DBReader
import ac.mdiq.podvinci.core.util.FeedItemUtil
import ac.mdiq.podvinci.event.EpisodeDownloadEvent
import ac.mdiq.podvinci.event.FeedItemEvent
import ac.mdiq.podvinci.event.FeedListUpdateEvent
import ac.mdiq.podvinci.event.UnreadItemsUpdateEvent
import ac.mdiq.podvinci.fragment.InboxFragment
import ac.mdiq.podvinci.fragment.swipeactions.SwipeActions
import ac.mdiq.podvinci.model.feed.FeedItem
import ac.mdiq.podvinci.model.feed.FeedItemFilter
import ac.mdiq.podvinci.storage.preferences.UserPreferences
import ac.mdiq.podvinci.ui.home.HomeSection
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*

class InboxSection : HomeSection() {
    private var adapter: EpisodeItemListAdapter? = null
    private var items: List<FeedItem> = ArrayList<FeedItem>()
    private var disposable: Disposable? = null

    @UnstableApi override fun onCreateView(inflater: LayoutInflater,
                                           container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view: View = super.onCreateView(inflater, container, savedInstanceState)
        viewBinding?.recyclerView?.setPadding(0, 0, 0, 0)
        viewBinding?.recyclerView?.setOverScrollMode(RecyclerView.OVER_SCROLL_NEVER)
        viewBinding?.recyclerView?.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        viewBinding?.recyclerView?.setRecycledViewPool((requireActivity() as MainActivity).recycledViewPool)
        adapter = object : EpisodeItemListAdapter(requireActivity() as MainActivity) {
            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
                super.onCreateContextMenu(menu, v, menuInfo)
                MenuItemUtils.setOnClickListeners(menu
                ) { item: MenuItem -> this@InboxSection.onContextItemSelected(item) }
            }
        }
        adapter?.setDummyViews(NUM_EPISODES)
        if (adapter != null) viewBinding?.recyclerView?.adapter = adapter

        val swipeActions = SwipeActions(this, InboxFragment.TAG)
        if (viewBinding != null) swipeActions.attachTo(viewBinding!!.recyclerView)
        swipeActions.setFilter(FeedItemFilter(FeedItemFilter.NEW))
        return view
    }

    override fun onStart() {
        super.onStart()
        loadItems()
    }

    override fun handleMoreClick() {
        (requireActivity() as MainActivity).loadChildFragment(InboxFragment())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUnreadItemsChanged(event: UnreadItemsUpdateEvent?) {
        loadItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent?) {
        loadItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: FeedListUpdateEvent?) {
        loadItems()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: EpisodeDownloadEvent) {
        for (downloadUrl in event.urls) {
            val pos: Int = FeedItemUtil.indexOfItemWithDownloadUrl(items, downloadUrl)
            if (pos >= 0) {
                adapter?.notifyItemChangedCompat(pos)
            }
        }
    }

    override val sectionTitle: String
        get() = getString(R.string.home_new_title)

    override val moreLinkTitle: String
        get() = getString(R.string.inbox_label)

    private fun loadItems() {
        disposable?.dispose()

        disposable = Observable.fromCallable {
            Pair(DBReader.getEpisodes(0, NUM_EPISODES,
                FeedItemFilter(FeedItemFilter.NEW), UserPreferences.inboxSortedOrder),
                DBReader.getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.NEW)))
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ data: Pair<List<FeedItem>, Int> ->
                items = data.first
                adapter?.setDummyViews(0)
                adapter?.updateItems(items)
                viewBinding?.numNewItemsLabel?.visibility = View.VISIBLE
                if (data.second >= 100) {
                    viewBinding?.numNewItemsLabel?.text = String.format(Locale.getDefault(), "%d+", 99)
                } else {
                    viewBinding?.numNewItemsLabel?.text = String.format(Locale.getDefault(), "%d", data.second)
                }
            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    companion object {
        const val TAG: String = "InboxSection"
        private const val NUM_EPISODES = 2
    }
}
