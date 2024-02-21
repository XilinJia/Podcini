package ac.mdiq.podcini.ui.home.sections

import ac.mdiq.podcini.activity.MainActivity
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ac.mdiq.podcini.R
import ac.mdiq.podcini.adapter.EpisodeItemListAdapter
import ac.mdiq.podcini.core.event.DownloadLogEvent
import ac.mdiq.podcini.core.menuhandler.MenuItemUtils
import ac.mdiq.podcini.core.storage.DBReader
import ac.mdiq.podcini.event.FeedItemEvent
import ac.mdiq.podcini.event.PlayerStatusEvent
import ac.mdiq.podcini.event.playback.PlaybackPositionEvent
import ac.mdiq.podcini.fragment.CompletedDownloadsFragment
import ac.mdiq.podcini.fragment.swipeactions.SwipeActions
import ac.mdiq.podcini.model.feed.FeedItem
import ac.mdiq.podcini.model.feed.FeedItemFilter
import ac.mdiq.podcini.model.feed.SortOrder
import ac.mdiq.podcini.storage.preferences.UserPreferences
import ac.mdiq.podcini.ui.home.HomeSection
import ac.mdiq.podcini.view.viewholder.EpisodeItemViewHolder
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class DownloadsSection : HomeSection() {
    private lateinit var adapter: EpisodeItemListAdapter
    
    private var disposable: Disposable? = null

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view: View = super.onCreateView(inflater, container, savedInstanceState)
        viewBinding.recyclerView.setPadding(0, 0, 0, 0)
        viewBinding.recyclerView.setOverScrollMode(RecyclerView.OVER_SCROLL_NEVER)
        viewBinding.recyclerView.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        viewBinding.recyclerView.setRecycledViewPool((requireActivity() as MainActivity).recycledViewPool)
        adapter = object : EpisodeItemListAdapter(requireActivity() as MainActivity) {
            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
                super.onCreateContextMenu(menu, v, menuInfo)
                MenuItemUtils.setOnClickListeners(menu
                ) { item: MenuItem ->
                    this@DownloadsSection.onContextItemSelected(item)
                }
            }
        }
        adapter.setDummyViews(NUM_EPISODES)
        viewBinding.recyclerView.adapter = adapter

        val swipeActions = SwipeActions(this, CompletedDownloadsFragment.TAG)
        swipeActions.attachTo(viewBinding.recyclerView)
        swipeActions.setFilter(FeedItemFilter(FeedItemFilter.DOWNLOADED))
        return view
    }

    override fun onStart() {
        super.onStart()
        loadItems()
    }

    @UnstableApi override fun handleMoreClick() {
        (requireActivity() as MainActivity).loadChildFragment(CompletedDownloadsFragment())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent?) {
        loadItems()
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        for (i in 0 until adapter.itemCount) {
            val holder: EpisodeItemViewHolder? = viewBinding.recyclerView.findViewHolderForAdapterPosition(i) as? EpisodeItemViewHolder
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
                adapter.setDummyViews(0)
                adapter.updateItems(downloads)
            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    companion object {
        const val TAG: String = "DownloadsSection"
        private const val NUM_EPISODES = 2
    }
}
