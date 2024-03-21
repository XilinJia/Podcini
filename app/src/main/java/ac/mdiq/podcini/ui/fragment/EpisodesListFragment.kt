package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.ui.activity.MainActivity
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.EpisodesListFragmentBinding
import ac.mdiq.podcini.databinding.MultiSelectSpeedDialBinding
import ac.mdiq.podcini.ui.adapter.EpisodeItemListAdapter
import ac.mdiq.podcini.ui.adapter.SelectableAdapter
import ac.mdiq.podcini.ui.dialog.ConfirmationDialog
import ac.mdiq.podcini.ui.menuhandler.MenuItemUtils
import ac.mdiq.podcini.util.FeedItemUtil
import ac.mdiq.podcini.net.download.FeedUpdateManager
import ac.mdiq.podcini.util.event.*
import ac.mdiq.podcini.playback.event.PlaybackPositionEvent
import ac.mdiq.podcini.ui.fragment.actions.EpisodeMultiSelectActionHandler
import ac.mdiq.podcini.ui.fragment.swipeactions.SwipeActions
import ac.mdiq.podcini.ui.menuhandler.FeedItemMenuHandler
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter
import ac.mdiq.podcini.ui.view.EmptyViewHandler
import ac.mdiq.podcini.ui.view.EpisodeItemListRecyclerView
import ac.mdiq.podcini.ui.view.LiftOnScrollListener
import ac.mdiq.podcini.ui.view.viewholder.EpisodeItemViewHolder
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


/**
 * Shows unread or recently published episodes
 */
abstract class EpisodesListFragment : Fragment(), SelectableAdapter.OnSelectModeListener, Toolbar.OnMenuItemClickListener {
    @JvmField
    protected var page: Int = 1
    protected var isLoadingMore: Boolean = false
    protected var hasMoreItems: Boolean = false
    private var displayUpArrow = false

    lateinit var binding: EpisodesListFragmentBinding
    lateinit var recyclerView: EpisodeItemListRecyclerView
    lateinit var emptyView: EmptyViewHandler
    lateinit var speedDialView: SpeedDialView
    lateinit var toolbar: MaterialToolbar
    lateinit var swipeRefreshLayout: SwipeRefreshLayout
    lateinit var swipeActions: SwipeActions
    private lateinit var progressBar: ProgressBar
    lateinit var listAdapter: EpisodeItemListAdapter

    @JvmField
    var episodes: MutableList<FeedItem> = ArrayList()

    protected var disposable: Disposable? = null
    protected lateinit var txtvInformation: TextView

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)

        binding = EpisodesListFragmentBinding.inflate(inflater)
        Log.d(TAG, "fragment onCreateView")

        txtvInformation = binding.txtvInformation
        toolbar = binding.toolbar
        toolbar.setOnMenuItemClickListener(this)
        toolbar.setOnLongClickListener {
            recyclerView.scrollToPosition(5)
            recyclerView.post { recyclerView.smoothScrollToPosition(0) }
            false
        }
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
        }
        (activity as MainActivity).setupToolbarToggle(toolbar, displayUpArrow)

        recyclerView = binding.recyclerView
        recyclerView.setRecycledViewPool((activity as MainActivity).recycledViewPool)
        setupLoadMoreScrollListener()
        recyclerView.addOnScrollListener(LiftOnScrollListener(binding.appbar))

        swipeActions = SwipeActions(this, getFragmentTag()).attachTo(recyclerView)
        swipeActions.setFilter(getFilter())

        if (swipeActions.actions?.left != null) {
            binding.leftActionIcon.setImageResource(swipeActions.actions!!.left!!.getActionIcon())
        }
        if (swipeActions.actions?.right != null) {
            binding.rightActionIcon.setImageResource(swipeActions.actions!!.right!!.getActionIcon())
        }

        val animator: RecyclerView.ItemAnimator? = recyclerView.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }

        swipeRefreshLayout = binding.swipeRefresh
        swipeRefreshLayout.setDistanceToTriggerSync(resources.getInteger(R.integer.swipe_refresh_distance))
        swipeRefreshLayout.setOnRefreshListener {
            FeedUpdateManager.runOnceOrAsk(requireContext())
        }

        listAdapter = object : EpisodeItemListAdapter(activity as MainActivity) {
            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
                super.onCreateContextMenu(menu, v, menuInfo)
//                if (!inActionMode()) {
//                    menu.findItem(R.id.multi_select).setVisible(true)
//                }
                MenuItemUtils.setOnClickListeners(menu
                ) { item: MenuItem ->
                    this@EpisodesListFragment.onContextItemSelected(item)
                }
            }
        }
        listAdapter.setOnSelectModeListener(this)
        recyclerView.adapter = listAdapter
        progressBar = binding.progressBar
        progressBar.visibility = View.VISIBLE

        emptyView = EmptyViewHandler(requireContext())
        emptyView.attachToRecyclerView(recyclerView)
        emptyView.setIcon(R.drawable.ic_feed)
        emptyView.setTitle(R.string.no_all_episodes_head_label)
        emptyView.setMessage(R.string.no_all_episodes_label)
        emptyView.updateAdapter(listAdapter)
        emptyView.hide()

        val multiSelectDial = MultiSelectSpeedDialBinding.bind(binding.root)
        speedDialView = multiSelectDial.fabSD
        speedDialView.overlayLayout = multiSelectDial.fabSDOverlay
        speedDialView.inflate(R.menu.episodes_apply_action_speeddial)
        speedDialView.setOnChangeListener(object : SpeedDialView.OnChangeListener {
            override fun onMainActionSelected(): Boolean {
                return false
            }

            override fun onToggleChanged(open: Boolean) {
                if (open && listAdapter.selectedCount == 0) {
                    (activity as MainActivity).showSnackbarAbovePlayer(R.string.no_items_selected,
                        Snackbar.LENGTH_SHORT)
                    speedDialView.close()
                }
            }
        })
        speedDialView.setOnActionSelectedListener { actionItem: SpeedDialActionItem ->
            var confirmationString = 0
            if (listAdapter.selectedItems.size >= 25 || listAdapter.shouldSelectLazyLoadedItems()) {
                // Should ask for confirmation
                if (actionItem.id == R.id.mark_read_batch) {
                    confirmationString = R.string.multi_select_mark_played_confirmation
                } else if (actionItem.id == R.id.mark_unread_batch) {
                    confirmationString = R.string.multi_select_mark_unplayed_confirmation
                }
            }
            if (confirmationString == 0) {
                performMultiSelectAction(actionItem.id)
            } else {
                object : ConfirmationDialog(activity as MainActivity, R.string.multi_select, confirmationString) {
                    override fun onConfirmButtonPressed(dialog: DialogInterface) {
                        performMultiSelectAction(actionItem.id)
                    }
                }.createNewDialog().show()
            }
            true
        }

        EventBus.getDefault().register(this)
        loadItems()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        registerForContextMenu(recyclerView)
    }

    override fun onPause() {
        super.onPause()
        recyclerView.saveScrollPosition(getPrefName())
        unregisterForContextMenu(recyclerView)
    }

    override fun onStop() {
        super.onStop()
        disposable?.dispose()
    }

    @UnstableApi override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (super.onOptionsItemSelected(item)) {
            return true
        }
        val itemId = item.itemId
        if (itemId == R.id.refresh_item) {
            FeedUpdateManager.runOnceOrAsk(requireContext())
            return true
        } else if (itemId == R.id.action_search) {
            (activity as MainActivity).loadChildFragment(SearchFragment.newInstance())
            return true
        }
        return false
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "onContextItemSelected() called with: item = [$item]")
        when {
            !userVisibleHint || !isVisible || !isMenuVisible -> {
                // The method is called on all fragments in a ViewPager, so this needs to be ignored in invisible ones.
                // Apparently, none of the visibility check method works reliably on its own, so we just use all.
                return false
            }
            listAdapter.longPressedItem == null -> {
                Log.i(TAG, "Selected item or listAdapter was null, ignoring selection")
                return super.onContextItemSelected(item)
            }
            listAdapter.onContextItemSelected(item) -> {
                return true
            }
            else -> {
                val selectedItem: FeedItem = listAdapter.longPressedItem ?: return false
                return FeedItemMenuHandler.onMenuItemClicked(this, item.itemId, selectedItem)
            }
        }
    }

    @UnstableApi private fun performMultiSelectAction(actionItemId: Int) {
        val handler = EpisodeMultiSelectActionHandler((activity as MainActivity), actionItemId)
        Completable.fromAction {
            handler.handleAction(listAdapter.selectedItems.filterIsInstance<FeedItem>())
            if (listAdapter.shouldSelectLazyLoadedItems()) {
                var applyPage = page + 1
                var nextPage: List<FeedItem>
                do {
                    nextPage = loadMoreData(applyPage)
                    handler.handleAction(nextPage)
                    applyPage++
                } while (nextPage.size == EPISODES_PER_PAGE)
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ listAdapter.endSelectMode() },
                { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    private fun setupLoadMoreScrollListener() {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(view: RecyclerView, deltaX: Int, deltaY: Int) {
                super.onScrolled(view, deltaX, deltaY)
                if (!isLoadingMore && hasMoreItems && recyclerView.isScrolledToBottom) {
                    /* The end of the list has been reached. Load more data. */
                    page++
                    loadMoreItems()
                    isLoadingMore = true
                }
            }
        })
    }

    private fun loadMoreItems() {
        disposable?.dispose()

        isLoadingMore = true
        listAdapter.setDummyViews(1)
        listAdapter.notifyItemInserted(listAdapter.itemCount - 1)
        disposable = Observable.fromCallable { loadMoreData(page) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { data: List<FeedItem> ->
                    if (data.size < EPISODES_PER_PAGE) {
                        hasMoreItems = false
                    }
                    episodes.addAll(data)
                    listAdapter.setDummyViews(0)
                    listAdapter.updateItems(episodes)
                    if (listAdapter.shouldSelectLazyLoadedItems()) {
                        listAdapter.setSelected(episodes.size - data.size, episodes.size, true)
                    }
                }, { error: Throwable? ->
                    listAdapter.setDummyViews(0)
                    listAdapter.updateItems(emptyList())
                    Log.e(TAG, Log.getStackTraceString(error))
                }, {
                    // Make sure to not always load 2 pages at once
                    recyclerView.post { isLoadingMore = false }
                })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        EventBus.getDefault().unregister(this)
        listAdapter.endSelectMode()
    }

    override fun onStartSelectMode() {
        speedDialView.visibility = View.VISIBLE
    }

    override fun onEndSelectMode() {
        speedDialView.close()
        speedDialView.visibility = View.GONE
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        Log.d(TAG, "onEventMainThread() called with: event = [$event]")
        for (item in event.items) {
            val pos: Int = FeedItemUtil.indexOfItemWithId(episodes, item.id)
            if (pos >= 0) {
                episodes.removeAt(pos)
                if (getFilter().matches(item)) {
                    episodes.add(pos, item)
                    listAdapter.notifyItemChangedCompat(pos)
                } else {
                    listAdapter.notifyItemRemoved(pos)
                }
            }
        }
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        for (i in 0 until listAdapter.itemCount) {
            val holder: EpisodeItemViewHolder? = recyclerView.findViewHolderForAdapterPosition(i) as? EpisodeItemViewHolder
            if (holder != null && holder.isCurrentlyPlayingItem) {
                holder.notifyPlaybackPositionUpdated(event)
                break
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onKeyUp(event: KeyEvent) {
        if (!isAdded || !isVisible || !isMenuVisible) {
            return
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_T -> recyclerView.smoothScrollToPosition(0)
            KeyEvent.KEYCODE_B -> recyclerView.smoothScrollToPosition(listAdapter.itemCount)
            else -> {}
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: EpisodeDownloadEvent) {
        for (downloadUrl in event.urls) {
            val pos: Int = FeedItemUtil.indexOfItemWithDownloadUrl(episodes, downloadUrl)
            if (pos >= 0) {
                listAdapter.notifyItemChangedCompat(pos)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusChanged(event: PlayerStatusEvent?) {
        loadItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUnreadItemsChanged(event: UnreadItemsUpdateEvent?) {
        loadItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: FeedListUpdateEvent?) {
        loadItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSwipeActionsChanged(event: SwipeActionsChangedEvent?) {
        if (swipeActions.actions?.left != null) {
            binding.leftActionIcon.setImageResource(swipeActions.actions!!.left!!.getActionIcon())
        }
        if (swipeActions.actions?.right != null) {
            binding.rightActionIcon.setImageResource(swipeActions.actions!!.right!!.getActionIcon())
        }
    }

    fun loadItems() {
        disposable?.dispose()

        disposable = Observable.fromCallable {
            Pair(loadData().toMutableList(),
                loadTotalItemCount())
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { data: Pair<MutableList<FeedItem>, Int> ->
                    val restoreScrollPosition = episodes.isEmpty()
                    episodes = data.first
                    hasMoreItems = !(page == 1 && episodes.size < EPISODES_PER_PAGE)
                    progressBar.visibility = View.GONE
                    listAdapter.setDummyViews(0)
                    listAdapter.updateItems(episodes)
                    listAdapter.setTotalNumberOfItems(data.second)
                    if (restoreScrollPosition) {
                        recyclerView.restoreScrollPosition(getPrefName())
                    }
                    updateToolbar()
                }, { error: Throwable? ->
                    listAdapter.setDummyViews(0)
                    listAdapter.updateItems(emptyList())
                    Log.e(TAG, Log.getStackTraceString(error))
                })
    }

    protected abstract fun loadData(): List<FeedItem>

    protected abstract fun loadMoreData(page: Int): List<FeedItem>

    protected abstract fun loadTotalItemCount(): Int

    protected abstract fun getFilter(): FeedItemFilter

    protected abstract fun getFragmentTag(): String

    protected abstract fun getPrefName(): String

    protected open fun updateToolbar() {
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedUpdateRunningEvent) {
        swipeRefreshLayout.isRefreshing = event.isFeedUpdateRunning
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val TAG: String = "EpisodesListFragment"
        private const val KEY_UP_ARROW = "up_arrow"
        const val EPISODES_PER_PAGE: Int = 150
    }
}
