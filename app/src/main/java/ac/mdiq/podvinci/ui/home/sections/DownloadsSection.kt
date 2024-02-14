package ac.mdiq.podvinci.ui.home.sections

import ac.mdiq.podvinci.activity.MainActivity
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.adapter.EpisodeItemListAdapter
import ac.mdiq.podvinci.core.event.DownloadLogEvent
import ac.mdiq.podvinci.core.menuhandler.MenuItemUtils
import ac.mdiq.podvinci.core.storage.DBReader
import ac.mdiq.podvinci.event.FeedItemEvent
import ac.mdiq.podvinci.event.PlayerStatusEvent
import ac.mdiq.podvinci.event.playback.PlaybackPositionEvent
import ac.mdiq.podvinci.fragment.CompletedDownloadsFragment
import ac.mdiq.podvinci.fragment.swipeactions.SwipeActions
import ac.mdiq.podvinci.model.feed.FeedItem
import ac.mdiq.podvinci.model.feed.FeedItemFilter
import ac.mdiq.podvinci.model.feed.SortOrder
import ac.mdiq.podvinci.storage.preferences.UserPreferences
import ac.mdiq.podvinci.ui.home.HomeSection
import ac.mdiq.podvinci.view.viewholder.EpisodeItemViewHolder
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class DownloadsSection : HomeSection() {
    private var adapter: EpisodeItemListAdapter? = null
    private var items: List<FeedItem>? = null
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
            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
                super.onCreateContextMenu(menu, v, menuInfo)
                MenuItemUtils.setOnClickListeners(menu
                ) { item: MenuItem ->
                    this@DownloadsSection.onContextItemSelected(item)
                }
            }
        }
        adapter?.setDummyViews(NUM_EPISODES)
        if (adapter != null) viewBinding?.recyclerView?.adapter = adapter

        val swipeActions = SwipeActions(this, CompletedDownloadsFragment.TAG)
        if (viewBinding != null) swipeActions.attachTo(viewBinding!!.recyclerView)
        swipeActions.setFilter(FeedItemFilter(FeedItemFilter.DOWNLOADED))
        return view
    }

    override fun onStart() {
        super.onStart()
        loadItems()
    }

    override fun handleMoreClick() {
        (requireActivity() as MainActivity).loadChildFragment(CompletedDownloadsFragment())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent?) {
        loadItems()
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        if (viewBinding == null || adapter == null) {
            return
        }
        for (i in 0 until adapter!!.itemCount) {
            val holder: EpisodeItemViewHolder? = viewBinding!!.recyclerView.findViewHolderForAdapterPosition(i) as? EpisodeItemViewHolder
            if (holder != null && holder.isCurrentlyPlayingItem) {
                holder.notifyPlaybackPositionUpdated(event)
                break
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownloadLogChanged(event: DownloadLogEvent?) {
        loadItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusChanged(event: PlayerStatusEvent?) {
        loadItems()
    }

    override val sectionTitle: String
        get() = getString(R.string.home_downloads_title)

    override val moreLinkTitle: String
        get() = getString(R.string.downloads_label)

    private fun loadItems() {
        disposable?.dispose()

        val sortOrder: SortOrder? = UserPreferences.downloadsSortedOrder
        disposable = Observable.fromCallable {
            DBReader.getEpisodes(0, Int.MAX_VALUE, FeedItemFilter(FeedItemFilter.DOWNLOADED), sortOrder)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ downloads: List<FeedItem> ->
                var downloads: List<FeedItem> = downloads
                if (downloads.size > NUM_EPISODES) {
                    downloads = downloads.subList(0, NUM_EPISODES)
                }
                items = downloads
                adapter?.setDummyViews(0)
                if (items != null) adapter?.updateItems(items!!)
            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    companion object {
        const val TAG: String = "DownloadsSection"
        private const val NUM_EPISODES = 2
    }
}
