package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.ui.activity.MainActivity
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.CheckboxDoNotShowAgainBinding
import ac.mdiq.podcini.databinding.MultiSelectSpeedDialBinding
import ac.mdiq.podcini.databinding.QueueFragmentBinding
import ac.mdiq.podcini.ui.adapter.QueueRecyclerAdapter
import ac.mdiq.podcini.ui.adapter.SelectableAdapter
import ac.mdiq.podcini.ui.dialog.ConfirmationDialog
import ac.mdiq.podcini.feed.util.PlaybackSpeedUtils
import ac.mdiq.podcini.ui.menuhandler.MenuItemUtils
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.util.FeedItemUtil
import ac.mdiq.podcini.net.download.FeedUpdateManager
import ac.mdiq.podcini.ui.dialog.ItemSortDialog
import ac.mdiq.podcini.util.event.*
import ac.mdiq.podcini.playback.event.PlaybackPositionEvent
import ac.mdiq.podcini.ui.fragment.actions.EpisodeMultiSelectActionHandler
import ac.mdiq.podcini.ui.fragment.swipeactions.SwipeActions
import ac.mdiq.podcini.ui.menuhandler.FeedItemMenuHandler
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter
import ac.mdiq.podcini.storage.model.feed.SortOrder
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.ui.dialog.SwipeActionsDialog
import ac.mdiq.podcini.ui.view.EmptyViewHandler
import ac.mdiq.podcini.ui.view.EpisodeItemListRecyclerView
import ac.mdiq.podcini.ui.view.LiftOnScrollListener
import ac.mdiq.podcini.ui.view.viewholder.EpisodeItemViewHolder
import ac.mdiq.podcini.util.Converter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*

/**
 * Shows all items in the queue.
 */
class QueueFragment : Fragment(), Toolbar.OnMenuItemClickListener, SelectableAdapter.OnSelectModeListener {

    private lateinit var binding: QueueFragmentBinding
    private lateinit var infoBar: TextView
    private lateinit var recyclerView: EpisodeItemListRecyclerView
    private lateinit var emptyView: EmptyViewHandler
    private lateinit var toolbar: MaterialToolbar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var swipeActions: SwipeActions
    private lateinit var prefs: SharedPreferences
    private lateinit var speedDialView: SpeedDialView
    private lateinit var progressBar: ProgressBar
    
    private var displayUpArrow = false
    private var queue: MutableList<FeedItem> = mutableListOf()

    private var recyclerAdapter: QueueRecyclerAdapter? = null
    private var disposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        prefs = requireActivity().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = QueueFragmentBinding.inflate(inflater)

        Log.d(TAG, "fragment onCreateView")
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
        toolbar.inflateMenu(R.menu.queue)
        refreshToolbarState()
        progressBar = binding.progressBar
        progressBar.visibility = View.VISIBLE

        infoBar = binding.infoBar
        recyclerView = binding.recyclerView
        val animator: RecyclerView.ItemAnimator? = recyclerView.itemAnimator
        if (animator != null && animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
        recyclerView.setRecycledViewPool((activity as MainActivity).recycledViewPool)
        registerForContextMenu(recyclerView)
        recyclerView.addOnScrollListener(LiftOnScrollListener(binding.appbar))

        swipeActions = QueueSwipeActions()
        swipeActions.setFilter(FeedItemFilter(FeedItemFilter.QUEUED))
        swipeActions.attachTo(recyclerView)
        refreshSwipeTelltale()
        binding.leftActionIcon.setOnClickListener({
            swipeActions.showDialog()
        })
        binding.rightActionIcon.setOnClickListener({
            swipeActions.showDialog()
        })

        recyclerAdapter = object : QueueRecyclerAdapter(activity as MainActivity, swipeActions) {
            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
                super.onCreateContextMenu(menu, v, menuInfo)
                MenuItemUtils.setOnClickListeners(menu
                ) { item: MenuItem -> this@QueueFragment.onContextItemSelected(item) }
            }
        }
        recyclerAdapter?.setOnSelectModeListener(this)
        recyclerView.adapter = recyclerAdapter

        swipeRefreshLayout = binding.swipeRefresh
        swipeRefreshLayout.setDistanceToTriggerSync(resources.getInteger(R.integer.swipe_refresh_distance))
        swipeRefreshLayout.setOnRefreshListener {
            FeedUpdateManager.runOnceOrAsk(requireContext())
        }

        emptyView = EmptyViewHandler(requireContext())
        emptyView.attachToRecyclerView(recyclerView)
        emptyView.setIcon(R.drawable.ic_playlist_play)
        emptyView.setTitle(R.string.no_items_header_label)
        emptyView.setMessage(R.string.no_items_label)
        emptyView.updateAdapter(recyclerAdapter)

        val multiSelectDial = MultiSelectSpeedDialBinding.bind(binding.root)
        speedDialView = multiSelectDial.fabSD
        speedDialView.overlayLayout = multiSelectDial.fabSDOverlay
        speedDialView.inflate(R.menu.episodes_apply_action_speeddial)
//        speedDialView.removeActionItemById(R.id.mark_read_batch)
//        speedDialView.removeActionItemById(R.id.mark_unread_batch)
        speedDialView.removeActionItemById(R.id.add_to_queue_batch)
//        speedDialView.removeActionItemById(R.id.remove_all_inbox_item)
        speedDialView.setOnChangeListener(object : SpeedDialView.OnChangeListener {
            override fun onMainActionSelected(): Boolean {
                return false
            }

            override fun onToggleChanged(open: Boolean) {
                if (open && recyclerAdapter!!.selectedCount == 0) {
                    (activity as MainActivity).showSnackbarAbovePlayer(R.string.no_items_selected,
                        Snackbar.LENGTH_SHORT)
                    speedDialView.close()
                }
            }
        })
        speedDialView.setOnActionSelectedListener { actionItem: SpeedDialActionItem ->
            EpisodeMultiSelectActionHandler((activity as MainActivity), actionItem.id)
                .handleAction(recyclerAdapter!!.selectedItems.filterIsInstance<FeedItem>())
            recyclerAdapter?.endSelectMode()
            true
        }
        loadItems(true)
        EventBus.getDefault().register(this)

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        if (queue.isNotEmpty()) recyclerView.restoreScrollPosition(TAG)
    }

    override fun onPause() {
        super.onPause()
        recyclerView.saveScrollPosition(TAG)
    }

    override fun onStop() {
        super.onStop()
        disposable?.dispose()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: QueueEvent) {
        Log.d(TAG, "onEventMainThread() called with: event = [$event]")
        if (recyclerAdapter == null) {
            loadItems(true)
            return
        }
        when (event.action) {
            QueueEvent.Action.ADDED -> {
                if (event.item != null) queue.add(event.position, event.item)
                recyclerAdapter?.notifyItemInserted(event.position)
            }
            QueueEvent.Action.SET_QUEUE, QueueEvent.Action.SORTED -> {
                queue = event.items.toMutableList()
                recyclerAdapter?.updateItems(event.items)
            }
            QueueEvent.Action.REMOVED, QueueEvent.Action.IRREVERSIBLE_REMOVED -> {
                if (event.item != null) {
                    val position: Int = FeedItemUtil.indexOfItemWithId(queue.toList(), event.item.id)
                    queue.removeAt(position)
                    recyclerAdapter?.notifyItemRemoved(position)
                }
            }
            QueueEvent.Action.CLEARED -> {
                queue.clear()
                recyclerAdapter?.updateItems(queue)
            }
            QueueEvent.Action.MOVED -> return
            QueueEvent.Action.ADDED_ITEMS -> return
            QueueEvent.Action.DELETED_MEDIA -> return
        }
        recyclerAdapter?.updateDragDropEnabled()
        refreshToolbarState()
        recyclerView.saveScrollPosition(TAG)
        refreshInfoBar()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        Log.d(TAG, "onEventMainThread() called with: event = [$event]")
        if (recyclerAdapter == null) {
            loadItems(true)
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
                recyclerAdapter?.notifyItemChangedCompat(pos)
                refreshInfoBar()
            }
            i++
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: EpisodeDownloadEvent) {
        for (downloadUrl in event.urls) {
            val pos: Int = FeedItemUtil.indexOfItemWithDownloadUrl(queue.toList(), downloadUrl)
            if (pos >= 0) {
                recyclerAdapter?.notifyItemChangedCompat(pos)
            }
        }
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        if (recyclerAdapter != null) {
            for (i in 0 until recyclerAdapter!!.itemCount) {
                val holder: EpisodeItemViewHolder? = recyclerView.findViewHolderForAdapterPosition(i) as? EpisodeItemViewHolder
                if (holder != null && holder.isCurrentlyPlayingItem) {
                    holder.notifyPlaybackPositionUpdated(event)
                    break
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusChanged(event: PlayerStatusEvent?) {
        loadItems(false)
        refreshToolbarState()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUnreadItemsChanged(event: UnreadItemsUpdateEvent?) {
        // Sent when playback position is reset
        loadItems(false)
        refreshToolbarState()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSwipeActionsChanged(event: SwipeActionsChangedEvent?) {
        refreshSwipeTelltale()
    }

    private fun refreshSwipeTelltale() {
        if (swipeActions.actions?.left != null) {
            binding.leftActionIcon.setImageResource(swipeActions.actions!!.left!!.getActionIcon())
        }
        if (swipeActions.actions?.right != null) {
            binding.rightActionIcon.setImageResource(swipeActions.actions!!.right!!.getActionIcon())
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onKeyUp(event: KeyEvent) {
        if (!isAdded || !isVisible || !isMenuVisible) {
            return
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_T -> recyclerView.smoothScrollToPosition(0)
            KeyEvent.KEYCODE_B -> recyclerView.smoothScrollToPosition(recyclerAdapter!!.itemCount - 1)
            else -> {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recyclerAdapter?.endSelectMode()
        recyclerAdapter = null
        EventBus.getDefault().unregister(this)

        toolbar.setOnMenuItemClickListener(null)
        toolbar.setOnLongClickListener(null)
    }

    private fun refreshToolbarState() {
        val keepSorted: Boolean = UserPreferences.isQueueKeepSorted
        toolbar.menu?.findItem(R.id.queue_lock)?.setChecked(UserPreferences.isQueueLocked)
        toolbar.menu?.findItem(R.id.queue_lock)?.setVisible(!keepSorted)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedUpdateRunningEvent) {
        swipeRefreshLayout.isRefreshing = event.isFeedUpdateRunning
    }

    @UnstableApi override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        when (itemId) {
            R.id.queue_lock -> {
                toggleQueueLock()
                return true
            }
            R.id.queue_sort -> {
                QueueSortDialog().show(childFragmentManager.beginTransaction(), "SortDialog")
                return true
            }
            R.id.refresh_item -> {
                FeedUpdateManager.runOnceOrAsk(requireContext())
                return true
            }
            R.id.clear_queue -> {
                // make sure the user really wants to clear the queue
                val conDialog: ConfirmationDialog = object : ConfirmationDialog(requireContext(),
                    R.string.clear_queue_label,
                    R.string.clear_queue_confirmation_msg) {
                    @UnstableApi override fun onConfirmButtonPressed(dialog: DialogInterface) {
                        dialog.dismiss()
                        DBWriter.clearQueue()
                    }
                }
                conDialog.createNewDialog().show()
                return true
            }
            R.id.action_search -> {
                (activity as MainActivity).loadChildFragment(SearchFragment.newInstance())
                return true
            }
            else -> return false
        }
    }

    @UnstableApi private fun toggleQueueLock() {
        val isLocked: Boolean = UserPreferences.isQueueLocked
        if (isLocked) {
            setQueueLocked(false)
        } else {
            val shouldShowLockWarning: Boolean = prefs.getBoolean(PREF_SHOW_LOCK_WARNING, true)
            if (!shouldShowLockWarning) {
                setQueueLocked(true)
            } else {
                val builder = MaterialAlertDialogBuilder(requireContext())
                builder.setTitle(R.string.lock_queue)
                builder.setMessage(R.string.queue_lock_warning)

                val view = View.inflate(context, R.layout.checkbox_do_not_show_again, null)
                val binding = CheckboxDoNotShowAgainBinding.bind(view)
                val checkDoNotShowAgain: CheckBox = binding.checkboxDoNotShowAgain
                builder.setView(view)

                builder.setPositiveButton(R.string.lock_queue
                ) { _: DialogInterface?, _: Int ->
                    prefs.edit().putBoolean(PREF_SHOW_LOCK_WARNING, !checkDoNotShowAgain.isChecked).apply()
                    setQueueLocked(true)
                }
                builder.setNegativeButton(R.string.cancel_label, null)
                builder.show()
            }
        }
    }

    @UnstableApi private fun setQueueLocked(locked: Boolean) {
        UserPreferences.isQueueLocked = locked
        refreshToolbarState()
        recyclerAdapter?.updateDragDropEnabled()

        if (queue.size == 0) {
            if (locked) {
                (activity as MainActivity).showSnackbarAbovePlayer(R.string.queue_locked, Snackbar.LENGTH_SHORT)
            } else {
                (activity as MainActivity).showSnackbarAbovePlayer(R.string.queue_unlocked, Snackbar.LENGTH_SHORT)
            }
        }
    }

    @UnstableApi override fun onContextItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "onContextItemSelected() called with: item = [$item]")
        if (!isVisible || recyclerAdapter == null) {
            return false
        }
        val selectedItem: FeedItem? = recyclerAdapter!!.longPressedItem
        if (selectedItem == null) {
            Log.i(TAG, "Selected item was null, ignoring selection")
            return super.onContextItemSelected(item)
        }
        if (recyclerAdapter!!.onContextItemSelected(item)) {
            return true
        }

        val position: Int = FeedItemUtil.indexOfItemWithId(queue.toList(), selectedItem.id)
        if (position < 0) {
            Log.i(TAG, "Selected item no longer exist, ignoring selection")
            return super.onContextItemSelected(item)
        }

        val itemId = item.itemId
        if (itemId == R.id.move_to_top_item) {
            queue.add(0, queue.removeAt(position))
            recyclerAdapter?.notifyItemMoved(position, 0)
            DBWriter.moveQueueItemToTop(selectedItem.id, true)
            return true
        } else if (itemId == R.id.move_to_bottom_item) {
            queue.add(queue.size - 1, queue.removeAt(position))
            recyclerAdapter?.notifyItemMoved(position, queue.size - 1)
            DBWriter.moveQueueItemToBottom(selectedItem.id, true)
            return true
        }

        return FeedItemMenuHandler.onMenuItemClicked(this, item.itemId, selectedItem)
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    private fun refreshInfoBar() {
        var info = String.format(Locale.getDefault(), "%d%s", queue.size, getString(R.string.episodes_suffix))
        if (queue.isNotEmpty()) {
            var timeLeft: Long = 0
            for (item in queue) {
                var playbackSpeed = 1f
                if (UserPreferences.timeRespectsSpeed()) {
                    playbackSpeed = PlaybackSpeedUtils.getCurrentPlaybackSpeed(item.media)
                }
                if (item.media != null) {
                    val itemTimeLeft: Long = (item.media!!.getDuration() - item.media!!.getPosition()).toLong()
                    timeLeft = (timeLeft + itemTimeLeft / playbackSpeed).toLong()
                }
            }
            info += " • "
            info += Converter.getDurationStringLocalized(requireActivity(), timeLeft)
        }
        infoBar.text = info
    }

    private fun loadItems(restoreScrollPosition: Boolean) {
        Log.d(TAG, "loadItems() called")
        disposable?.dispose()

        if (queue.isEmpty()) emptyView.hide()

        disposable = Observable.fromCallable { DBReader.getQueue().toMutableList() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ items: MutableList<FeedItem> ->
                queue = items
                progressBar.visibility = View.GONE
                recyclerAdapter?.setDummyViews(0)
                recyclerAdapter?.updateItems(queue)
                if (restoreScrollPosition) recyclerView.restoreScrollPosition(TAG)
                refreshInfoBar()
            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    override fun onStartSelectMode() {
        swipeActions.detach()
        speedDialView.visibility = View.VISIBLE
        refreshToolbarState()
        infoBar.visibility = View.GONE
    }

    override fun onEndSelectMode() {
        speedDialView.close()
        speedDialView.visibility = View.GONE
        infoBar.visibility = View.VISIBLE
        swipeActions.attachTo(recyclerView)
    }

    class QueueSortDialog : ItemSortDialog() {
        override fun onCreateView(inflater: LayoutInflater,
                                  container: ViewGroup?, savedInstanceState: Bundle?
        ): View {
            if (UserPreferences.isQueueKeepSorted) {
                sortOrder = UserPreferences.queueKeepSortedOrder
            }
            val view: View = super.onCreateView(inflater, container, savedInstanceState)!!
            binding.keepSortedCheckbox.visibility = View.VISIBLE
            binding.keepSortedCheckbox.setChecked(UserPreferences.isQueueKeepSorted)
            // Disable until something gets selected
            binding.keepSortedCheckbox.setEnabled(UserPreferences.isQueueKeepSorted)
            return view
        }

        override fun onAddItem(title: Int, ascending: SortOrder, descending: SortOrder, ascendingIsDefault: Boolean) {
            if (ascending != SortOrder.EPISODE_FILENAME_A_Z && ascending != SortOrder.SIZE_SMALL_LARGE) {
                super.onAddItem(title, ascending, descending, ascendingIsDefault)
            }
        }

        @UnstableApi override fun onSelectionChanged() {
            super.onSelectionChanged()
            binding.keepSortedCheckbox.setEnabled(sortOrder != SortOrder.RANDOM)
            if (sortOrder == SortOrder.RANDOM) {
                binding.keepSortedCheckbox.setChecked(false)
            }
            UserPreferences.isQueueKeepSorted = binding.keepSortedCheckbox.isChecked
            UserPreferences.queueKeepSortedOrder = sortOrder
            DBWriter.reorderQueue(sortOrder, true)
        }
    }

    private inner class QueueSwipeActions :
        SwipeActions(ItemTouchHelper.UP or ItemTouchHelper.DOWN, this@QueueFragment, TAG) {
        // Position tracking whilst dragging
        var dragFrom: Int = -1
        var dragTo: Int = -1

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPosition = viewHolder.bindingAdapterPosition
            val toPosition = target.bindingAdapterPosition

            // Update tracked position
            if (dragFrom == -1) {
                dragFrom = fromPosition
            }
            dragTo = toPosition

            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            Log.d(TAG, "move($from, $to) in memory")
            if (from >= queue.size || to >= queue.size || from < 0 || to < 0) {
                return false
            }
            queue.add(to, queue.removeAt(from))
            recyclerAdapter?.notifyItemMoved(from, to)
            return true
        }

        @UnstableApi override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            disposable?.dispose()

            //SwipeActions
            super.onSwiped(viewHolder, direction)
        }

        override fun isLongPressDragEnabled(): Boolean {
            return false
        }

        @UnstableApi override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            // Check if drag finished
            if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
                reallyMoved(dragFrom, dragTo)
            }

            dragTo = -1
            dragFrom = dragTo
        }

        @UnstableApi private fun reallyMoved(from: Int, to: Int) {
            // Write drag operation to database
            Log.d(TAG, "Write to database move($from, $to)")
            DBWriter.moveQueueItem(from, to, true)
        }
    }

    companion object {
        const val TAG: String = "QueueFragment"
        private const val KEY_UP_ARROW = "up_arrow"

        private const val PREFS = "QueueFragment"
        private const val PREF_SHOW_LOCK_WARNING = "show_lock_warning"
    }
}
