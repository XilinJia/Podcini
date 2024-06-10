package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.MultiSelectSpeedDialBinding
import ac.mdiq.podcini.databinding.SimpleListFragmentBinding
import ac.mdiq.podcini.net.download.FeedUpdateManager
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter
import ac.mdiq.podcini.storage.model.feed.SortOrder
import ac.mdiq.podcini.ui.actions.EpisodeMultiSelectActionHandler
import ac.mdiq.podcini.ui.actions.actionbutton.DeleteActionButton
import ac.mdiq.podcini.ui.actions.menuhandler.FeedItemMenuHandler
import ac.mdiq.podcini.ui.actions.menuhandler.MenuItemUtils
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.adapter.EpisodeItemListAdapter
import ac.mdiq.podcini.ui.adapter.SelectableAdapter
import ac.mdiq.podcini.ui.dialog.ItemSortDialog
import ac.mdiq.podcini.ui.utils.EmptyViewHandler
import ac.mdiq.podcini.ui.view.EpisodeItemListRecyclerView
import ac.mdiq.podcini.ui.utils.LiftOnScrollListener
import ac.mdiq.podcini.ui.view.viewholder.EpisodeItemViewHolder
import ac.mdiq.podcini.util.FeedItemUtil
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Displays all completed downloads and provides a button to delete them.
 */
@UnstableApi class DownloadsFragment : Fragment(), SelectableAdapter.OnSelectModeListener, Toolbar.OnMenuItemClickListener {
    private var _binding: SimpleListFragmentBinding? = null
    private val binding get() = _binding!!

    private var runningDownloads: Set<String> = HashSet()
    private var items: MutableList<FeedItem> = mutableListOf()

    private lateinit var infoBar: TextView
    private lateinit var adapter: CompletedDownloadsListAdapter
    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: EpisodeItemListRecyclerView
    private lateinit var swipeActions: SwipeActions
    private lateinit var speedDialView: SpeedDialView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: EmptyViewHandler
    
//    private var disposable: Disposable? = null
    private var displayUpArrow = false
    private var currentPlaying: EpisodeItemViewHolder? = null

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SimpleListFragmentBinding.inflate(inflater)

        Logd(TAG, "fragment onCreateView")
        toolbar = binding.toolbar
        toolbar.setTitle(R.string.downloads_label)
        toolbar.inflateMenu(R.menu.downloads_completed)
        toolbar.setOnMenuItemClickListener(this)
        toolbar.setOnLongClickListener {
            recyclerView.scrollToPosition(5)
            recyclerView.post { recyclerView.smoothScrollToPosition(0) }
            false
        }
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

        (activity as MainActivity).setupToolbarToggle(toolbar, displayUpArrow)

        recyclerView = binding.recyclerView
        recyclerView.setRecycledViewPool((activity as MainActivity).recycledViewPool)
        adapter = CompletedDownloadsListAdapter(activity as MainActivity)
        adapter.setOnSelectModeListener(this)
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(LiftOnScrollListener(binding.appbar))

        swipeActions = SwipeActions(this, TAG).attachTo(recyclerView)
        lifecycle.addObserver(swipeActions)
        swipeActions.setFilter(FeedItemFilter(FeedItemFilter.DOWNLOADED))
        refreshSwipeTelltale()
        binding.leftActionIcon.setOnClickListener {
            swipeActions.showDialog()
        }
        binding.rightActionIcon.setOnClickListener {
            swipeActions.showDialog()
        }

        val animator: RecyclerView.ItemAnimator? = recyclerView.itemAnimator
        if (animator is SimpleItemAnimator) animator.supportsChangeAnimations = false

        progressBar = binding.progLoading
        progressBar.visibility = View.VISIBLE

        infoBar = binding.infoBar

        val multiSelectDial = MultiSelectSpeedDialBinding.bind(binding.root)
        speedDialView = multiSelectDial.fabSD
        speedDialView.overlayLayout = multiSelectDial.fabSDOverlay
        speedDialView.inflate(R.menu.episodes_apply_action_speeddial)
        speedDialView.removeActionItemById(R.id.download_batch)
//        speedDialView.removeActionItemById(R.id.mark_read_batch)
//        speedDialView.removeActionItemById(R.id.mark_unread_batch)
        speedDialView.removeActionItemById(R.id.remove_from_queue_batch)
        speedDialView.setOnChangeListener(object : SpeedDialView.OnChangeListener {
            override fun onMainActionSelected(): Boolean {
                return false
            }

            override fun onToggleChanged(open: Boolean) {
                if (open && adapter.selectedCount == 0) {
                    (activity as MainActivity).showSnackbarAbovePlayer(R.string.no_items_selected, Snackbar.LENGTH_SHORT)
                    speedDialView.close()
                }
            }
        })
        speedDialView.setOnActionSelectedListener { actionItem: SpeedDialActionItem ->
            adapter.selectedItems.let {
                EpisodeMultiSelectActionHandler((activity as MainActivity), actionItem.id)
                    .handleAction(it.filterIsInstance<FeedItem>())
            }
            adapter.endSelectMode()
            true
        }
        if (arguments != null && requireArguments().getBoolean(ARG_SHOW_LOGS, false))
            DownloadLogFragment().show(childFragmentManager, null)

        addEmptyView()

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
        loadItems()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        
        _binding = null
        adapter.endSelectMode()
        toolbar.setOnMenuItemClickListener(null)
        toolbar.setOnLongClickListener(null)
//        disposable?.dispose()

        super.onDestroyView()
    }

    @UnstableApi override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.refresh_item -> {
                FeedUpdateManager.runOnceOrAsk(requireContext())
                return true
            }
            R.id.action_download_logs -> {
                DownloadLogFragment().show(childFragmentManager, null)
                return true
            }
            R.id.action_search -> {
                (activity as MainActivity).loadChildFragment(SearchFragment.newInstance())
                return true
            }
            R.id.downloads_sort -> {
                DownloadsSortDialog().show(childFragmentManager, "SortDialog")
                return true
            }
            else -> return false
        }
    }

    fun onEventMainThread(event: FlowEvent.EpisodeDownloadEvent) {
        val newRunningDownloads: MutableSet<String> = HashSet()
        for (url in event.urls) {
            if (DownloadServiceInterface.get()?.isDownloadingEpisode(url) == true) newRunningDownloads.add(url)
        }
        if (newRunningDownloads != runningDownloads) {
            runningDownloads = newRunningDownloads
            loadItems()
            return  // Refreshed anyway
        }
        for (downloadUrl in event.urls) {
            val pos = FeedItemUtil.indexOfItemWithDownloadUrl(items.toList(), downloadUrl)
            if (pos >= 0) adapter.notifyItemChangedCompat(pos)
        }
    }

    private fun procFlowEvents() {
        lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: $event")
                when (event) {
                    is FlowEvent.FeedItemEvent -> onEventMainThread(event)
                    is FlowEvent.PlaybackPositionEvent -> onEventMainThread(event)
                    is FlowEvent.PlayerStatusEvent, is FlowEvent.DownloadLogEvent, is FlowEvent.UnreadItemsUpdateEvent -> loadItems()
                    is FlowEvent.SwipeActionsChangedEvent -> refreshSwipeTelltale()
                    else -> {}
                }
            }
        }
        lifecycleScope.launch {
            EventFlow.stickyEvents.collectLatest { event ->
                when (event) {
                    is FlowEvent.EpisodeDownloadEvent -> onEventMainThread(event)
                    else -> {}
                }
            }
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val selectedItem: FeedItem? = adapter.longPressedItem
        if (selectedItem == null) {
            Log.i(TAG, "Selected item at current position was null, ignoring selection")
            return super.onContextItemSelected(item)
        }
        if (adapter.onContextItemSelected(item)) return true

        return FeedItemMenuHandler.onMenuItemClicked(this, item.itemId, selectedItem)
    }

    private fun addEmptyView() {
        emptyView = EmptyViewHandler(requireContext())
        emptyView.setIcon(R.drawable.ic_download)
        emptyView.setTitle(R.string.no_comp_downloads_head_label)
        emptyView.setMessage(R.string.no_comp_downloads_label)
        emptyView.attachToRecyclerView(recyclerView)
    }

    fun onEventMainThread(event: FlowEvent.FeedItemEvent) {
        Logd(TAG, "onEventMainThread() called with: event = [$event]")

        var i = 0
        val size: Int = event.items.size
        while (i < size) {
            val item: FeedItem = event.items[i]
            val pos = FeedItemUtil.indexOfItemWithId(items.toList(), item.id)
            if (pos >= 0) {
                items.removeAt(pos)
                val media = item.media
                if (media != null && media.isDownloaded()) {
                    items.add(pos, item)
//                    adapter.notifyItemChangedCompat(pos)
                } else {
//                    adapter.notifyItemRemoved(pos)
                }
            }
            i++
        }
//        have to do this as adapter.notifyItemRemoved(pos) when pos == 0 causes crash
        if (size > 0) {
            adapter.setDummyViews(0)
            adapter.updateItems(items)
        }
        refreshInfoBar()
    }

    fun onEventMainThread(event: FlowEvent.PlaybackPositionEvent) {
//        Log.d(TAG, "onEventMainThread() called with PlaybackPositionEvent event = [$event]")
        if (currentPlaying != null && currentPlaying!!.isCurrentlyPlayingItem)
            currentPlaying!!.notifyPlaybackPositionUpdated(event)
        else {
            Logd(TAG, "onEventMainThread() search list")
            for (i in 0 until adapter.itemCount) {
                val holder: EpisodeItemViewHolder? = recyclerView.findViewHolderForAdapterPosition(i) as? EpisodeItemViewHolder
                if (holder != null && holder.isCurrentlyPlayingItem) {
                    currentPlaying = holder
                    holder.notifyPlaybackPositionUpdated(event)
                    break
                }
            }
        }
        refreshInfoBar()
    }

    private fun refreshSwipeTelltale() {
        if (swipeActions.actions?.left != null) binding.leftActionIcon.setImageResource(swipeActions.actions!!.left!!.getActionIcon())
        if (swipeActions.actions?.right != null) binding.rightActionIcon.setImageResource(swipeActions.actions!!.right!!.getActionIcon())
    }

    private fun loadItems() {
        emptyView.hide()
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val sortOrder: SortOrder? = UserPreferences.downloadsSortedOrder
                    val downloadedItems: List<FeedItem> = DBReader.getEpisodes(0, Int.MAX_VALUE, FeedItemFilter(FeedItemFilter.DOWNLOADED), sortOrder)
                    val mediaUrls: MutableList<String> = ArrayList()
                    if (runningDownloads.isEmpty()) {
                        downloadedItems
                    } else {
                        for (url in runningDownloads) {
                            if (FeedItemUtil.indexOfItemWithDownloadUrl(downloadedItems, url) != -1) continue
                            mediaUrls.add(url)
                        }
                        val currentDownloads: MutableList<FeedItem> = DBReader.getFeedItemsWithUrl(mediaUrls).toMutableList()
                        currentDownloads.addAll(downloadedItems)
                        currentDownloads
                    }
                }
                withContext(Dispatchers.Main) {
                    items = result.toMutableList()
                    adapter.setDummyViews(0)
                    progressBar.visibility = View.GONE
                    adapter.updateItems(result)
                    refreshInfoBar()
                }
            } catch (e: Throwable) {
                adapter.setDummyViews(0)
                adapter.updateItems(emptyList())
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
    }

    private fun refreshInfoBar() {
        var info = String.format(Locale.getDefault(), "%d%s", items.size, getString(R.string.episodes_suffix))
        if (items.isNotEmpty()) {
            var sizeMB: Long = 0
            for (item in items) {
                sizeMB += item.media?.size?:0
            }
            info += " • " + (sizeMB / 1000000) + " MB"
        }
        infoBar.text = info
    }

    override fun onStartSelectMode() {
        swipeActions.detach()
        speedDialView.visibility = View.VISIBLE
    }

    override fun onEndSelectMode() {
        speedDialView.close()
        speedDialView.visibility = View.GONE
        swipeActions.attachTo(recyclerView)
    }

    @UnstableApi private inner class CompletedDownloadsListAdapter(mainActivity: MainActivity) : EpisodeItemListAdapter(mainActivity) {
        @UnstableApi override fun afterBindViewHolder(holder: EpisodeItemViewHolder, pos: Int) {
            if (holder.feedItem != null && !inActionMode()) {
                if (holder.feedItem!!.isDownloaded) {
                    val item = getItem(pos) ?: return
                    val actionButton = DeleteActionButton(item)
                    actionButton.configure(holder.secondaryActionButton, holder.secondaryActionIcon, requireContext())
                }
            }
        }

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
            super.onCreateContextMenu(menu, v, menuInfo)
//            if (!inActionMode()) {
//                menu.findItem(R.id.multi_select).setVisible(true)
//            }
            MenuItemUtils.setOnClickListeners(menu) { item: MenuItem ->
                this@DownloadsFragment.onContextItemSelected(item)
            }
        }
    }

    class DownloadsSortDialog : ItemSortDialog() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            sortOrder = UserPreferences.downloadsSortedOrder
        }

        override fun onAddItem(title: Int, ascending: SortOrder, descending: SortOrder, ascendingIsDefault: Boolean) {
            if (ascending == SortOrder.DATE_OLD_NEW || ascending == SortOrder.PLAYED_DATE_OLD_NEW
                    || ascending == SortOrder.COMPLETED_DATE_OLD_NEW
                    || ascending == SortOrder.DURATION_SHORT_LONG || ascending == SortOrder.EPISODE_TITLE_A_Z
                    || ascending == SortOrder.SIZE_SMALL_LARGE || ascending == SortOrder.FEED_TITLE_A_Z) {
                super.onAddItem(title, ascending, descending, ascendingIsDefault)
            }
        }

        override fun onSelectionChanged() {
            super.onSelectionChanged()
            UserPreferences.downloadsSortedOrder = sortOrder
            EventFlow.postEvent(FlowEvent.DownloadLogEvent())
        }
    }

    companion object {
        const val TAG: String = "DownloadsFragment"
        const val ARG_SHOW_LOGS: String = "show_logs"
        private const val KEY_UP_ARROW = "up_arrow"
    }
}
