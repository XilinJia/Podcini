package de.danoeh.antennapod.fragment

import de.danoeh.antennapod.activity.MainActivity
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
import de.danoeh.antennapod.R
import de.danoeh.antennapod.adapter.QueueRecyclerAdapter
import de.danoeh.antennapod.adapter.SelectableAdapter
import de.danoeh.antennapod.core.dialog.ConfirmationDialog
import de.danoeh.antennapod.core.feed.util.PlaybackSpeedUtils
import de.danoeh.antennapod.core.menuhandler.MenuItemUtils
import de.danoeh.antennapod.core.storage.DBReader
import de.danoeh.antennapod.core.storage.DBWriter
import de.danoeh.antennapod.core.util.Converter
import de.danoeh.antennapod.core.util.FeedItemUtil
import de.danoeh.antennapod.core.util.download.FeedUpdateManager
import de.danoeh.antennapod.dialog.ItemSortDialog
import de.danoeh.antennapod.event.*
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.fragment.actions.EpisodeMultiSelectActionHandler
import de.danoeh.antennapod.fragment.swipeactions.SwipeActions
import de.danoeh.antennapod.menuhandler.FeedItemMenuHandler
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.view.EmptyViewHandler
import de.danoeh.antennapod.view.EpisodeItemListRecyclerView
import de.danoeh.antennapod.view.LiftOnScrollListener
import de.danoeh.antennapod.view.viewholder.EpisodeItemViewHolder
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
    private var infoBar: TextView? = null
    private var recyclerView: EpisodeItemListRecyclerView? = null
    private var recyclerAdapter: QueueRecyclerAdapter? = null
    private var emptyView: EmptyViewHandler? = null
    private var toolbar: MaterialToolbar? = null
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var displayUpArrow = false

    private var queue: MutableList<FeedItem>? = null

    private var disposable: Disposable? = null
    private var swipeActions: SwipeActions? = null
    private var prefs: SharedPreferences? = null

    private var speedDialView: SpeedDialView? = null
    private var progressBar: ProgressBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        prefs = requireActivity().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    override fun onStart() {
        super.onStart()
        if (queue != null) {
            recyclerView?.restoreScrollPosition(TAG)
        }
        loadItems(true)
        EventBus.getDefault().register(this)
    }

    override fun onPause() {
        super.onPause()
        recyclerView?.saveScrollPosition(TAG)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        disposable?.dispose()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: QueueEvent) {
        Log.d(TAG, "onEventMainThread() called with: event = [$event]")
        if (queue == null) {
            return
        } else if (recyclerAdapter == null) {
            loadItems(true)
            return
        }
        when (event.action) {
            QueueEvent.Action.ADDED -> {
                if (event.item != null) queue!!.add(event.position, event.item!!)
                recyclerAdapter?.notifyItemInserted(event.position)
            }
            QueueEvent.Action.SET_QUEUE, QueueEvent.Action.SORTED -> {
                if (event.items != null) {
                    queue = event.items!!.toMutableList()
                    recyclerAdapter?.updateItems(event.items!!)
                }
            }
            QueueEvent.Action.REMOVED, QueueEvent.Action.IRREVERSIBLE_REMOVED -> {
                if (event.item != null) {
                    val position: Int = FeedItemUtil.indexOfItemWithId(queue!!.toList(), event.item!!.id)
                    queue!!.removeAt(position)
                    recyclerAdapter?.notifyItemRemoved(position)
                }
            }
            QueueEvent.Action.CLEARED -> {
                queue!!.clear()
                recyclerAdapter?.updateItems(queue!!)
            }
            QueueEvent.Action.MOVED -> return
            QueueEvent.Action.ADDED_ITEMS -> return
            QueueEvent.Action.DELETED_MEDIA -> return
        }
        recyclerAdapter?.updateDragDropEnabled()
        refreshToolbarState()
        recyclerView?.saveScrollPosition(TAG)
        refreshInfoBar()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        Log.d(TAG, "onEventMainThread() called with: event = [$event]")
        if (queue == null) {
            return
        } else if (recyclerAdapter == null) {
            loadItems(true)
            return
        }
        var i = 0
        val size: Int = event.items.size
        while (i < size) {
            val item: FeedItem = event.items[i]
            val pos: Int = FeedItemUtil.indexOfItemWithId(queue!!, item.id)
            if (pos >= 0) {
                queue!!.removeAt(pos)
                queue!!.add(pos, item)
                recyclerAdapter?.notifyItemChangedCompat(pos)
                refreshInfoBar()
            }
            i++
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: EpisodeDownloadEvent) {
        if (queue == null) {
            return
        }
        for (downloadUrl in event.urls) {
            val pos: Int = FeedItemUtil.indexOfItemWithDownloadUrl(queue!!.toList(), downloadUrl)
            if (pos >= 0) {
                recyclerAdapter?.notifyItemChangedCompat(pos)
            }
        }
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        if (recyclerAdapter != null) {
            for (i in 0 until recyclerAdapter!!.itemCount) {
                val holder: EpisodeItemViewHolder? = recyclerView?.findViewHolderForAdapterPosition(i) as? EpisodeItemViewHolder
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
    fun onKeyUp(event: KeyEvent) {
        if (!isAdded || !isVisible || !isMenuVisible) {
            return
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_T -> recyclerView!!.smoothScrollToPosition(0)
            KeyEvent.KEYCODE_B -> recyclerView!!.smoothScrollToPosition(recyclerAdapter!!.itemCount - 1)
            else -> {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (recyclerAdapter != null) {
            recyclerAdapter?.endSelectMode()
        }
        recyclerAdapter = null
        if (toolbar != null) {
            toolbar?.setOnMenuItemClickListener(null)
            toolbar?.setOnLongClickListener(null)
        }
    }

    private fun refreshToolbarState() {
        val keepSorted: Boolean = UserPreferences.isQueueKeepSorted
        toolbar?.menu?.findItem(R.id.queue_lock)?.setChecked(UserPreferences.isQueueLocked)
        toolbar?.menu?.findItem(R.id.queue_lock)?.setVisible(!keepSorted)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedUpdateRunningEvent) {
        swipeRefreshLayout?.isRefreshing = event.isFeedUpdateRunning
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
                    @UnstableApi override fun onConfirmButtonPressed(
                            dialog: DialogInterface
                    ) {
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
            val shouldShowLockWarning: Boolean = prefs!!.getBoolean(PREF_SHOW_LOCK_WARNING, true)
            if (!shouldShowLockWarning) {
                setQueueLocked(true)
            } else {
                val builder = MaterialAlertDialogBuilder(requireContext())
                builder.setTitle(R.string.lock_queue)
                builder.setMessage(R.string.queue_lock_warning)

                val view = View.inflate(context, R.layout.checkbox_do_not_show_again, null)
                val checkDoNotShowAgain: CheckBox = view.findViewById(R.id.checkbox_do_not_show_again)
                builder.setView(view)

                builder.setPositiveButton(R.string.lock_queue
                ) { dialog: DialogInterface?, which: Int ->
                    prefs!!.edit().putBoolean(PREF_SHOW_LOCK_WARNING, !checkDoNotShowAgain.isChecked).apply()
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
        if (recyclerAdapter != null) {
            recyclerAdapter?.updateDragDropEnabled()
        }
        if (queue!!.size == 0) {
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

        if (queue != null) {
            val position: Int = FeedItemUtil.indexOfItemWithId(queue!!.toList(), selectedItem.id)
            if (position < 0) {
                Log.i(TAG, "Selected item no longer exist, ignoring selection")
                return super.onContextItemSelected(item)
            }

            val itemId = item.itemId
            if (itemId == R.id.move_to_top_item) {
                queue!!.add(0, queue!!.removeAt(position))
                recyclerAdapter?.notifyItemMoved(position, 0)
                DBWriter.moveQueueItemToTop(selectedItem.id, true)
                return true
            } else if (itemId == R.id.move_to_bottom_item) {
                queue!!.add(queue!!.size - 1, queue!!.removeAt(position))
                recyclerAdapter?.notifyItemMoved(position, queue!!.size - 1)
                DBWriter.moveQueueItemToBottom(selectedItem.id, true)
                return true
            }
        }
        return FeedItemMenuHandler.onMenuItemClicked(this, item.itemId, selectedItem)
    }

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        val root: View = inflater.inflate(R.layout.queue_fragment, container, false)
        toolbar = root.findViewById(R.id.toolbar)
        toolbar?.setOnMenuItemClickListener(this)
        toolbar?.setOnLongClickListener { v: View? ->
            recyclerView?.scrollToPosition(5)
            recyclerView?.post { recyclerView?.smoothScrollToPosition(0) }
            false
        }
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
        }
        if (toolbar != null) (activity as MainActivity).setupToolbarToggle(toolbar!!, displayUpArrow)
        toolbar?.inflateMenu(R.menu.queue)
        refreshToolbarState()
        progressBar = root.findViewById(R.id.progressBar)
        progressBar?.visibility = View.VISIBLE

        infoBar = root.findViewById(R.id.info_bar)
        recyclerView = root.findViewById(R.id.recyclerView)
        val animator: RecyclerView.ItemAnimator? = recyclerView!!.itemAnimator
        if (animator != null && animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
        recyclerView?.setRecycledViewPool((activity as MainActivity).recycledViewPool)
        registerForContextMenu(recyclerView!!)
        recyclerView?.addOnScrollListener(LiftOnScrollListener(root.findViewById(R.id.appbar)))

        swipeActions = QueueSwipeActions()
        swipeActions?.setFilter(FeedItemFilter(FeedItemFilter.QUEUED))
        swipeActions?.attachTo(recyclerView)

        recyclerAdapter = object : QueueRecyclerAdapter(activity as MainActivity, swipeActions!!) {
            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
                super.onCreateContextMenu(menu, v, menuInfo)
                MenuItemUtils.setOnClickListeners(menu
                ) { item: MenuItem -> this@QueueFragment.onContextItemSelected(item) }
            }
        }
        recyclerAdapter?.setOnSelectModeListener(this)
        recyclerView?.adapter = recyclerAdapter

        swipeRefreshLayout = root.findViewById(R.id.swipeRefresh)
        swipeRefreshLayout?.setDistanceToTriggerSync(resources.getInteger(R.integer.swipe_refresh_distance))
        swipeRefreshLayout?.setOnRefreshListener {
            FeedUpdateManager.runOnceOrAsk(requireContext())
        }

        emptyView = EmptyViewHandler(context)
        emptyView?.attachToRecyclerView(recyclerView!!)
        emptyView?.setIcon(R.drawable.ic_playlist_play)
        emptyView?.setTitle(R.string.no_items_header_label)
        emptyView?.setMessage(R.string.no_items_label)
        emptyView?.updateAdapter(recyclerAdapter)

        speedDialView = root.findViewById(R.id.fabSD)
        speedDialView?.overlayLayout = root.findViewById(R.id.fabSDOverlay)
        speedDialView?.inflate(R.menu.episodes_apply_action_speeddial)
        speedDialView?.removeActionItemById(R.id.mark_read_batch)
        speedDialView?.removeActionItemById(R.id.mark_unread_batch)
        speedDialView?.removeActionItemById(R.id.add_to_queue_batch)
        speedDialView?.removeActionItemById(R.id.remove_all_inbox_item)
        speedDialView?.setOnChangeListener(object : SpeedDialView.OnChangeListener {
            override fun onMainActionSelected(): Boolean {
                return false
            }

            override fun onToggleChanged(open: Boolean) {
                if (open && recyclerAdapter!!.selectedCount == 0) {
                    (activity as MainActivity).showSnackbarAbovePlayer(R.string.no_items_selected,
                        Snackbar.LENGTH_SHORT)
                    speedDialView?.close()
                }
            }
        })
        speedDialView?.setOnActionSelectedListener { actionItem: SpeedDialActionItem ->
            EpisodeMultiSelectActionHandler((activity as MainActivity), actionItem.id)
                .handleAction(recyclerAdapter!!.selectedItems.filterIsInstance<FeedItem>())
            recyclerAdapter?.endSelectMode()
            true
        }
        return root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    private fun refreshInfoBar() {
        if (queue == null) return
        var info = String.format(Locale.getDefault(), "%d%s", queue!!.size, getString(R.string.episodes_suffix))
        if (queue!!.size > 0) {
            var timeLeft: Long = 0
            for (item in queue!!) {
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
            info += getString(R.string.time_left_label)
            info += Converter.getDurationStringLocalized(requireActivity(), timeLeft)
        }
        infoBar?.text = info
    }

    private fun loadItems(restoreScrollPosition: Boolean) {
        Log.d(TAG, "loadItems()")
        disposable?.dispose()

        if (queue == null) {
            emptyView?.hide()
        }
        disposable =
            Observable.fromCallable { DBReader.getQueue().toMutableList() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ items: MutableList<FeedItem>? ->
                    queue = items
                    progressBar?.visibility = View.GONE
                    recyclerAdapter?.setDummyViews(0)
                    if (queue != null) recyclerAdapter?.updateItems(queue!!)
                    if (restoreScrollPosition) {
                        recyclerView?.restoreScrollPosition(TAG)
                    }
                    refreshInfoBar()
                }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    override fun onStartSelectMode() {
        swipeActions?.detach()
        speedDialView?.visibility = View.VISIBLE
        refreshToolbarState()
        infoBar?.visibility = View.GONE
    }

    override fun onEndSelectMode() {
        speedDialView?.close()
        speedDialView?.visibility = View.GONE
        infoBar?.visibility = View.VISIBLE
        swipeActions?.attachTo(recyclerView)
    }

    class QueueSortDialog : ItemSortDialog() {
        override fun onCreateView(inflater: LayoutInflater,
                                  container: ViewGroup?, savedInstanceState: Bundle?
        ): View {
            if (UserPreferences.isQueueKeepSorted) {
                sortOrder = UserPreferences.queueKeepSortedOrder
            }
            val view: View = super.onCreateView(inflater, container, savedInstanceState)!!
            viewBinding?.keepSortedCheckbox?.visibility = View.VISIBLE
            viewBinding?.keepSortedCheckbox?.setChecked(UserPreferences.isQueueKeepSorted)
            // Disable until something gets selected
            viewBinding?.keepSortedCheckbox?.setEnabled(UserPreferences.isQueueKeepSorted)
            return view
        }

        override fun onAddItem(title: Int,
                                         ascending: SortOrder,
                                         descending: SortOrder,
                                         ascendingIsDefault: Boolean
        ) {
            if (ascending != SortOrder.EPISODE_FILENAME_A_Z && ascending != SortOrder.SIZE_SMALL_LARGE) {
                super.onAddItem(title, ascending, descending, ascendingIsDefault)
            }
        }

        @UnstableApi override fun onSelectionChanged() {
            super.onSelectionChanged()
            viewBinding?.keepSortedCheckbox?.setEnabled(sortOrder != SortOrder.RANDOM)
            if (sortOrder == SortOrder.RANDOM) {
                viewBinding?.keepSortedCheckbox?.setChecked(false)
            }
            if (viewBinding != null) UserPreferences.isQueueKeepSorted = viewBinding!!.keepSortedCheckbox.isChecked
            UserPreferences.queueKeepSortedOrder = sortOrder
            DBWriter.reorderQueue(sortOrder, true)
        }
    }

    private inner class QueueSwipeActions :
        SwipeActions(ItemTouchHelper.UP or ItemTouchHelper.DOWN, this@QueueFragment, TAG) {
        // Position tracking whilst dragging
        var dragFrom: Int = -1
        var dragTo: Int = -1

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                            target: RecyclerView.ViewHolder
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
            if (queue == null || from >= queue!!.size || to >= queue!!.size || from < 0 || to < 0) {
                return false
            }
            queue!!.add(to, queue!!.removeAt(from))
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
