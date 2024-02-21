package ac.mdiq.podcini.ui.home.sections

import ac.mdiq.podcini.R
import ac.mdiq.podcini.activity.MainActivity
import ac.mdiq.podcini.adapter.HorizontalItemListAdapter
import ac.mdiq.podcini.core.menuhandler.MenuItemUtils
import ac.mdiq.podcini.core.storage.DBReader
import ac.mdiq.podcini.core.util.FeedItemUtil
import ac.mdiq.podcini.event.EpisodeDownloadEvent
import ac.mdiq.podcini.event.FeedItemEvent
import ac.mdiq.podcini.event.PlayerStatusEvent
import ac.mdiq.podcini.event.QueueEvent
import ac.mdiq.podcini.event.playback.PlaybackPositionEvent
import ac.mdiq.podcini.fragment.QueueFragment
import ac.mdiq.podcini.model.feed.FeedItem
import ac.mdiq.podcini.ui.home.HomeSection
import ac.mdiq.podcini.view.viewholder.HorizontalItemViewHolder
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class QueueSection : HomeSection() {
    private var disposable: Disposable? = null

    private lateinit var listAdapter: HorizontalItemListAdapter
    private var queue: MutableList<FeedItem> = ArrayList()

    @UnstableApi override fun onCreateView(inflater: LayoutInflater,
                                           container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view: View = super.onCreateView(inflater, container, savedInstanceState)
        listAdapter = object : HorizontalItemListAdapter(activity as MainActivity) {
            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
                super.onCreateContextMenu(menu, v, menuInfo)
                MenuItemUtils.setOnClickListeners(menu
                ) { item: MenuItem -> this@QueueSection.onContextItemSelected(item) }
            }
        }
        listAdapter.setDummyViews(NUM_EPISODES)
        viewBinding.recyclerView.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        viewBinding.recyclerView.adapter = listAdapter
        val paddingHorizontal: Int = (12 * resources.displayMetrics.density).toInt()
        viewBinding.recyclerView.setPadding(paddingHorizontal, 0, paddingHorizontal, 0)
        return view
    }

    override fun onStart() {
        super.onStart()
        loadItems()
    }

    @UnstableApi override fun handleMoreClick() {
        (requireActivity() as MainActivity).loadChildFragment(QueueFragment())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onQueueChanged(event: QueueEvent?) {
        loadItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusChanged(event: PlayerStatusEvent?) {
        loadItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        Log.d(TAG, "onEventMainThread() called with: event = [$event]")
        if (queue.isEmpty()) {
            return
        }
        var i = 0
        val size: Int = event.items.size
        while (i < size) {
            val item: FeedItem = event.items[i]
            val pos: Int = FeedItemUtil.indexOfItemWithId(queue, item.id)
            if (pos >= 0) {
                queue.removeAt(pos)
                queue.add(pos, item)
                listAdapter.notifyItemChangedCompat(pos)
            }
            i++
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: EpisodeDownloadEvent) {
        if (queue.isEmpty()) return
        for (downloadUrl in event.urls) {
            val pos: Int = FeedItemUtil.indexOfItemWithDownloadUrl(queue, downloadUrl)
            if (pos >= 0) {
                listAdapter.notifyItemChangedCompat(pos)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        var foundCurrentlyPlayingItem = false
        var currentlyPlayingItemIsFirst = true
        for (i in 0 until listAdapter.itemCount) {
            val holder: HorizontalItemViewHolder =
                viewBinding.recyclerView.findViewHolderForAdapterPosition(i) as? HorizontalItemViewHolder ?: continue
            if (holder.isCurrentlyPlayingItem) {
                holder.notifyPlaybackPositionUpdated(event)
                foundCurrentlyPlayingItem = true
                currentlyPlayingItemIsFirst = (i == 0)
                break
            }
        }
        if (!foundCurrentlyPlayingItem || !currentlyPlayingItemIsFirst) {
            loadItems()
        }
    }

    override val sectionTitle: String
        get() = getString(R.string.home_continue_title)

    override val moreLinkTitle: String
        get() = getString(R.string.queue_label)

    private fun loadItems() {
        disposable?.dispose()

        disposable = Observable.fromCallable {
            DBReader.getPausedQueue(NUM_EPISODES)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ queue: List<FeedItem> ->
                this.queue = queue.toMutableList()
                listAdapter.setDummyViews(0)
                listAdapter.updateData(queue)
            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    companion object {
        const val TAG: String = "QueueSection"
        private const val NUM_EPISODES = 8
    }
}
