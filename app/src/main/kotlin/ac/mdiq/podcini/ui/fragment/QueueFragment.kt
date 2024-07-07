package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.CheckboxDoNotShowAgainBinding
import ac.mdiq.podcini.databinding.MultiSelectSpeedDialBinding
import ac.mdiq.podcini.databinding.QueueFragmentBinding
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.InTheatre.isCurMedia
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.getCurrentPlaybackSpeed
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.database.Queues.clearQueue
import ac.mdiq.podcini.storage.database.Queues.moveInQueue
import ac.mdiq.podcini.storage.database.Queues.moveInQueueSync
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.utils.EpisodeFilter
import ac.mdiq.podcini.storage.utils.EpisodeUtil
import ac.mdiq.podcini.storage.utils.SortOrder
import ac.mdiq.podcini.ui.actions.EpisodeMultiSelectHandler
import ac.mdiq.podcini.ui.actions.menuhandler.EpisodeMenuHandler
import ac.mdiq.podcini.ui.actions.menuhandler.MenuItemUtils
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.adapter.EpisodesAdapter
import ac.mdiq.podcini.ui.adapter.SelectableAdapter
import ac.mdiq.podcini.ui.dialog.ConfirmationDialog
import ac.mdiq.podcini.ui.dialog.EpisodeSortDialog
import ac.mdiq.podcini.ui.dialog.SwitchQueueDialog
import ac.mdiq.podcini.ui.utils.EmptyViewHandler
import ac.mdiq.podcini.ui.utils.LiftOnScrollListener
import ac.mdiq.podcini.ui.view.EpisodesRecyclerView
import ac.mdiq.podcini.ui.view.viewholder.EpisodeViewHolder
import ac.mdiq.podcini.util.Converter
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import ac.mdiq.podcini.util.sorting.EpisodesPermutors.getPermutor
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.CheckBox
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*

/**
 * Shows all items in the queue.
 */
@UnstableApi class QueueFragment : Fragment(), Toolbar.OnMenuItemClickListener, SelectableAdapter.OnSelectModeListener {

    private var _binding: QueueFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var recyclerView: EpisodesRecyclerView
    private lateinit var emptyView: EmptyViewHandler
    private lateinit var toolbar: MaterialToolbar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var swipeActions: SwipeActions
    private lateinit var speedDialView: SpeedDialView

    private var displayUpArrow = false
    private var queueItems: MutableList<Episode> = mutableListOf()

    private var adapter: QueueRecyclerAdapter? = null
    private var curIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = QueueFragmentBinding.inflate(inflater)

        Logd(TAG, "fragment onCreateView")
        toolbar = binding.toolbar
        toolbar.setOnMenuItemClickListener(this)
        toolbar.setOnLongClickListener {
            recyclerView.scrollToPosition(5)
            recyclerView.post { recyclerView.smoothScrollToPosition(0) }
            false
        }
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

        (activity as MainActivity).setupToolbarToggle(toolbar, displayUpArrow)
        toolbar.inflateMenu(R.menu.queue)
        refreshToolbarState()
        binding.progressBar.visibility = View.VISIBLE

        recyclerView = binding.recyclerView
        val animator: RecyclerView.ItemAnimator? = recyclerView.itemAnimator
        if (animator != null && animator is SimpleItemAnimator) animator.supportsChangeAnimations = false

        recyclerView.setRecycledViewPool((activity as MainActivity).recycledViewPool)
        registerForContextMenu(recyclerView)
        recyclerView.addOnScrollListener(LiftOnScrollListener(binding.appbar))

        swipeActions = QueueSwipeActions()
        lifecycle.addObserver(swipeActions)
        swipeActions.setFilter(EpisodeFilter(EpisodeFilter.QUEUED))
        swipeActions.attachTo(recyclerView)
        refreshSwipeTelltale()
        binding.leftActionIcon.setOnClickListener { swipeActions.showDialog() }
        binding.rightActionIcon.setOnClickListener { swipeActions.showDialog() }

        adapter = QueueRecyclerAdapter()
        adapter?.setOnSelectModeListener(this)
        recyclerView.adapter = adapter

        swipeRefreshLayout = binding.swipeRefresh
        swipeRefreshLayout.setDistanceToTriggerSync(resources.getInteger(R.integer.swipe_refresh_distance))
        swipeRefreshLayout.setOnRefreshListener { FeedUpdateManager.runOnceOrAsk(requireContext()) }

        emptyView = EmptyViewHandler(requireContext())
        emptyView.attachToRecyclerView(recyclerView)
        emptyView.setIcon(R.drawable.ic_playlist_play)
        emptyView.setTitle(R.string.no_items_header_label)
        emptyView.setMessage(R.string.no_items_label)
        emptyView.updateAdapter(adapter)

        val multiSelectDial = MultiSelectSpeedDialBinding.bind(binding.root)
        speedDialView = multiSelectDial.fabSD
        speedDialView.overlayLayout = multiSelectDial.fabSDOverlay
        speedDialView.inflate(R.menu.episodes_apply_action_speeddial)
        speedDialView.removeActionItemById(R.id.add_to_queue_batch)
        speedDialView.setOnChangeListener(object : SpeedDialView.OnChangeListener {
            override fun onMainActionSelected(): Boolean {
                return false
            }
            override fun onToggleChanged(open: Boolean) {
                if (open && adapter!!.selectedCount == 0) {
                    (activity as MainActivity).showSnackbarAbovePlayer(R.string.no_items_selected, Snackbar.LENGTH_SHORT)
                    speedDialView.close()
                }
            }
        })
        speedDialView.setOnActionSelectedListener { actionItem: SpeedDialActionItem ->
            EpisodeMultiSelectHandler((activity as MainActivity), actionItem.id)
                .handleAction(adapter!!.selectedItems.filterIsInstance<Episode>())
            adapter?.endSelectMode()
            true
        }
        return binding.root
    }

    override fun onStart() {
        Logd(TAG, "onStart() called")
        super.onStart()
        loadItems(true)
        procFlowEvents()
        if (queueItems.isNotEmpty()) recyclerView.restoreScrollPosition(TAG)
    }

    override fun onStop() {
        Logd(TAG, "onStop() called")
        super.onStop()
        cancelFlowEvents()
    }

    override fun onPause() {
        Logd(TAG, "onPause() called")
        super.onPause()
        recyclerView.saveScrollPosition(TAG)
    }

    private var eventSink: Job?     = null
    private var eventStickySink: Job? = null
    private var eventKeySink: Job?     = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
        eventStickySink?.cancel()
        eventStickySink = null
        eventKeySink?.cancel()
        eventKeySink = null
    }
    private fun procFlowEvents() {
        if (eventSink == null) eventSink = lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.QueueEvent -> onQueueEvent(event)
                    is FlowEvent.EpisodeEvent -> onEpisodeEvent(event)
                    is FlowEvent.FavoritesEvent -> onFavoriteEvent(event)
                    is FlowEvent.PlayEvent -> onPlayEvent(event)
                    is FlowEvent.PlaybackPositionEvent -> onPlaybackPositionEvent(event)
                    is FlowEvent.PlayerSettingsEvent -> onPlayerStatusChanged(event)
                    is FlowEvent.FeedPrefsChangeEvent -> onFeedPrefsChanged(event)
                    is FlowEvent.EpisodePlayedEvent -> onEpisodePlayedEvent(event)
                    is FlowEvent.SwipeActionsChangedEvent -> refreshSwipeTelltale()
                    else -> {}
                }
            }
        }
        if (eventStickySink == null) eventStickySink = lifecycleScope.launch {
            EventFlow.stickyEvents.collectLatest { event ->
                Logd(TAG, "Received sticky event: ${event.TAG}")
                when (event) {
                    is FlowEvent.EpisodeDownloadEvent -> onEpisodeDownloadEvent(event)
                    is FlowEvent.FeedUpdatingEvent -> swipeRefreshLayout.isRefreshing = event.isRunning
                    else -> {}
                }
            }
        }
        if (eventKeySink == null) eventKeySink = lifecycleScope.launch {
            EventFlow.keyEvents.collectLatest { event ->
                Logd(TAG, "Received key event: $event")
                onKeyUp(event)
            }
        }
    }

    private fun onQueueEvent(event: FlowEvent.QueueEvent) {
        Logd(TAG, "onQueueEvent() called with ${event.action.name}")
        if (adapter == null) {
            loadItems(true)
            return
        }
        when (event.action) {
            FlowEvent.QueueEvent.Action.ADDED -> {
                if (event.episodes.isNotEmpty() && !curQueue.isInQueue(event.episodes[0])) queueItems.add(event.position, event.episodes[0])
                adapter?.notifyItemInserted(event.position)
            }
            FlowEvent.QueueEvent.Action.SET_QUEUE, FlowEvent.QueueEvent.Action.SORTED -> {
                queueItems.clear()
                queueItems.addAll(event.episodes)
                adapter?.updateItems(event.episodes)
            }
            FlowEvent.QueueEvent.Action.REMOVED, FlowEvent.QueueEvent.Action.IRREVERSIBLE_REMOVED -> {
                if (event.episodes.isNotEmpty()) {
                    for (e in event.episodes) {
                        val pos: Int = EpisodeUtil.indexOfItemWithId(queueItems, e.id)
                        if (pos >= 0) {
                            Logd(TAG, "removing episode $pos ${queueItems[pos]} $e")
                            queueItems.removeAt(pos)
                            adapter?.notifyItemRemoved(pos)
                        } else {
                            Log.e(TAG, "Trying to remove item non-existent from queue ${e.id} ${e.title}")
                            continue
                        }
                    }
                }
            }
            FlowEvent.QueueEvent.Action.SWITCH_QUEUE -> loadItems(false)
            FlowEvent.QueueEvent.Action.CLEARED -> {
                queueItems.clear()
                adapter?.updateItems(queueItems)
            }
            FlowEvent.QueueEvent.Action.MOVED, FlowEvent.QueueEvent.Action.DELETED_MEDIA -> return
        }
        adapter?.updateDragDropEnabled()
        refreshToolbarState()
        recyclerView.saveScrollPosition(TAG)
        refreshInfoBar()
    }

    private fun onFavoriteEvent(event: FlowEvent.FavoritesEvent) {
        var i = 0
        val item = event.episode
        val pos: Int = EpisodeUtil.indexOfItemWithId(queueItems, item.id)
        if (pos >= 0) {
            queueItems[pos].isFavorite = item.isFavorite
            adapter?.notifyItemChangedCompat(pos)
        }
    }

    private fun onEpisodeEvent(event: FlowEvent.EpisodeEvent) {
//        Logd(TAG, "onEventMainThread() called with ${event.TAG}")
        if (adapter == null) {
            loadItems(true)
            return
        }
        var i = 0
        val size: Int = event.episodes.size
        while (i < size) {
            val item: Episode = event.episodes[i]
            val pos: Int = EpisodeUtil.indexOfItemWithId(queueItems, item.id)
            if (pos >= 0) {
                queueItems.removeAt(pos)
                queueItems.add(pos, item)
                adapter?.notifyItemChangedCompat(pos)
                refreshInfoBar()
            }
            i++
        }
    }

    private fun onPlayEvent(event: FlowEvent.PlayEvent) {
        Logd(TAG, "onPlayEvent ${event.episode.title}")
        val pos: Int = EpisodeUtil.indexOfItemWithId(queueItems, event.episode.id)
        if (pos >= 0) adapter?.notifyItemChangedCompat(pos)
    }

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
//        Logd(TAG, "onEventMainThread() called with ${event.TAG}")
        for (downloadUrl in event.urls) {
            val pos: Int = EpisodeUtil.indexOfItemWithDownloadUrl(queueItems.toList(), downloadUrl)
            if (pos >= 0) {
                val item = queueItems[pos]
                item.media?.downloaded = true
                adapter?.notifyItemChangedCompat(pos)
            }
        }
    }

    private fun onPlaybackPositionEvent(event: FlowEvent.PlaybackPositionEvent) {
        val item = (event.media as? EpisodeMedia)?.episode ?: return
        val pos = if (curIndex in 0..<queueItems.size && event.media.getIdentifier() == queueItems[curIndex].media?.getIdentifier() && isCurMedia(queueItems[curIndex].media))
            curIndex else EpisodeUtil.indexOfItemWithId(queueItems, item.id)

        if (pos >= 0) {
            queueItems[pos] = item
            curIndex = pos
            adapter?.notifyItemChanged(pos, Bundle().apply { putString("PositionUpdate", "PlaybackPositionEvent") })
        }
    }

    private fun onPlayerStatusChanged(event: FlowEvent.PlayerSettingsEvent) {
//        Logd(TAG, "onPlayerStatusChanged() called with event = [$event]")
        loadItems(false)
        refreshToolbarState()
    }

    private fun onEpisodePlayedEvent(event: FlowEvent.EpisodePlayedEvent) {
        // Sent when playback position is reset
        Logd(TAG, "onUnreadItemsChanged() called with event = [$event]")
        if (event.episode == null) loadItems(false)
        else {
            val pos: Int = EpisodeUtil.indexOfItemWithId(queueItems, event.episode.id)
            if (pos >= 0) queueItems[pos].setPlayed(event.episode.isPlayed())
        }
        refreshToolbarState()
    }

    private fun onFeedPrefsChanged(event: FlowEvent.FeedPrefsChangeEvent) {
        Log.d(TAG,"speedPresetChanged called")
        for (item in queueItems) {
            if (item.feed?.id == event.feed.id) item.feed = null
        }
    }

    private fun refreshSwipeTelltale() {
        if (swipeActions.actions?.left != null) binding.leftActionIcon.setImageResource(swipeActions.actions!!.left!!.getActionIcon())
        if (swipeActions.actions?.right != null) binding.rightActionIcon.setImageResource(swipeActions.actions!!.right!!.getActionIcon())
    }

    private fun onKeyUp(event: KeyEvent) {
        if (!isAdded || !isVisible || !isMenuVisible) return

        when (event.keyCode) {
            KeyEvent.KEYCODE_T -> recyclerView.smoothScrollToPosition(0)
            KeyEvent.KEYCODE_B -> recyclerView.smoothScrollToPosition(adapter!!.itemCount - 1)
            else -> {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        adapter?.endSelectMode()
        adapter = null
        toolbar.setOnMenuItemClickListener(null)
        toolbar.setOnLongClickListener(null)
    }

    private fun refreshToolbarState() {
        val keepSorted: Boolean = UserPreferences.isQueueKeepSorted
        toolbar.menu?.findItem(R.id.queue_lock)?.setChecked(UserPreferences.isQueueLocked)
        toolbar.menu?.findItem(R.id.queue_lock)?.setVisible(!keepSorted)
    }

    @UnstableApi override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        when (itemId) {
            R.id.queue_lock -> toggleQueueLock()
            R.id.queue_sort -> QueueSortDialog().show(childFragmentManager.beginTransaction(), "SortDialog")
            R.id.refresh_item -> FeedUpdateManager.runOnceOrAsk(requireContext())
            R.id.clear_queue -> {
                // make sure the user really wants to clear the queue
                val conDialog: ConfirmationDialog = object : ConfirmationDialog(requireContext(), R.string.clear_queue_label, R.string.clear_queue_confirmation_msg) {
                    @UnstableApi override fun onConfirmButtonPressed(dialog: DialogInterface) {
                        dialog.dismiss()
                        clearQueue()
                    }
                }
                conDialog.createNewDialog().show()
            }
            R.id.action_search -> (activity as MainActivity).loadChildFragment(SearchFragment.newInstance())
            R.id.switch_queue -> SwitchQueueDialog(activity as MainActivity).show()
            else -> return false
        }
        return true
    }

    @UnstableApi private fun toggleQueueLock() {
        val isLocked: Boolean = UserPreferences.isQueueLocked
        if (isLocked) setQueueLocked(false)
        else {
            val shouldShowLockWarning: Boolean = prefs!!.getBoolean(PREF_SHOW_LOCK_WARNING, true)
            if (!shouldShowLockWarning) setQueueLocked(true)
            else {
                val builder = MaterialAlertDialogBuilder(requireContext())
                builder.setTitle(R.string.lock_queue)
                builder.setMessage(R.string.queue_lock_warning)

                val view = View.inflate(context, R.layout.checkbox_do_not_show_again, null)
                val binding_ = CheckboxDoNotShowAgainBinding.bind(view)
                val checkDoNotShowAgain: CheckBox = binding_.checkboxDoNotShowAgain
                builder.setView(view)

                builder.setPositiveButton(R.string.lock_queue) { _: DialogInterface?, _: Int ->
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
        adapter?.updateDragDropEnabled()

        if (queueItems.size == 0) {
            if (locked) (activity as MainActivity).showSnackbarAbovePlayer(R.string.queue_locked, Snackbar.LENGTH_SHORT)
            else (activity as MainActivity).showSnackbarAbovePlayer(R.string.queue_unlocked, Snackbar.LENGTH_SHORT)
        }
    }

    @UnstableApi override fun onContextItemSelected(item: MenuItem): Boolean {
        Logd(TAG, "onContextItemSelected() called with: item = [$item]")
        if (!isVisible || adapter == null) return false

        val selectedItem: Episode? = adapter!!.longPressedItem
        if (selectedItem == null) {
            Logd(TAG, "Selected item was null, ignoring selection")
            return super.onContextItemSelected(item)
        }
        if (adapter!!.onContextItemSelected(item)) return true

        val pos: Int = EpisodeUtil.indexOfItemWithId(queueItems.toList(), selectedItem.id)
        if (pos < 0) {
            Logd(TAG, "Selected item no longer exist, ignoring selection")
            return super.onContextItemSelected(item)
        }

        val itemId = item.itemId
        return when (itemId) {
            R.id.move_to_top_item -> {
                queueItems.add(0, queueItems.removeAt(pos))
                adapter?.notifyItemMoved(pos, 0)
                moveToQueueTop(selectedItem, true)
                true
            }
            R.id.move_to_bottom_item -> {
                queueItems.add(queueItems.size - 1, queueItems.removeAt(pos))
                adapter?.notifyItemMoved(pos, queueItems.size - 1)
                moveToQueueBottom(selectedItem, true)
                true
            }
            else -> EpisodeMenuHandler.onMenuItemClicked(this, item.itemId, selectedItem)
        }
    }

    private fun moveToQueueTop(episode: Episode, broadcastUpdate: Boolean) : Job {
        return runOnIOScope {
            val index = curQueue.episodes.indexOf(episode)
            if (index >= 0) moveInQueueSync(index, 0, broadcastUpdate)
            else Log.e(TAG, "moveQueueItemToTop: episode not found")
        }
    }

    private fun moveToQueueBottom(episode: Episode, broadcastUpdate: Boolean) : Job {
        return runOnIOScope {
            val index = curQueue.episodes.indexOf(episode)
            if (index >= 0) moveInQueueSync(index, curQueue.episodes.size - 1, broadcastUpdate)
            else Log.e(TAG, "moveQueueItemToBottom: episode not found")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    private fun refreshInfoBar() {
        var info = String.format(Locale.getDefault(), "%d%s", queueItems.size, getString(R.string.episodes_suffix))
        if (queueItems.isNotEmpty()) {
            var timeLeft: Long = 0
            for (item in queueItems) {
                var playbackSpeed = 1f
                if (UserPreferences.timeRespectsSpeed()) playbackSpeed = getCurrentPlaybackSpeed(item.media)

                if (item.media != null) {
                    val itemTimeLeft: Long = (item.media!!.getDuration() - item.media!!.getPosition()).toLong()
                    timeLeft = (timeLeft + itemTimeLeft / playbackSpeed).toLong()
                }
            }
            info += " • "
            info += Converter.getDurationStringLocalized(requireActivity(), timeLeft)
        }
        binding.infoBar.text = info
        toolbar.title = "${getString(R.string.queue_label)}: ${curQueue.name}"
    }

    private fun loadItems(restoreScrollPosition: Boolean) {
        Logd(TAG, "loadItems() called")
        while (curQueue.name.isEmpty()) runBlocking { delay(100) }
        curQueue.episodes.clear()
        curQueue.episodes.addAll(realm.copyFromRealm(realm.query(Episode::class, "id IN $0", curQueue.episodeIds)
            .find().sortedBy { curQueue.episodeIds.indexOf(it.id) }))

        if (queueItems.isEmpty()) emptyView.hide()

        queueItems.clear()
        queueItems.addAll(curQueue.episodes)
        binding.progressBar.visibility = View.GONE
//        adapter?.setDummyViews(0)
        adapter?.updateItems(queueItems)
        if (restoreScrollPosition) recyclerView.restoreScrollPosition(TAG)
        refreshInfoBar()
    }

    override fun onStartSelectMode() {
        swipeActions.detach()
        speedDialView.visibility = View.VISIBLE
        refreshToolbarState()
        binding.infoBar.visibility = View.GONE
    }

    override fun onEndSelectMode() {
        speedDialView.close()
        speedDialView.visibility = View.GONE
        binding.infoBar.visibility = View.VISIBLE
        swipeActions.attachTo(recyclerView)
    }

    class QueueSortDialog : EpisodeSortDialog() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            if (UserPreferences.isQueueKeepSorted) sortOrder = UserPreferences.queueKeepSortedOrder
            val view: View = super.onCreateView(inflater, container, savedInstanceState)!!
            binding.keepSortedCheckbox.visibility = View.VISIBLE
            binding.keepSortedCheckbox.setChecked(UserPreferences.isQueueKeepSorted)
            // Disable until something gets selected
            binding.keepSortedCheckbox.setEnabled(UserPreferences.isQueueKeepSorted)
            return view
        }
        override fun onAddItem(title: Int, ascending: SortOrder, descending: SortOrder, ascendingIsDefault: Boolean) {
            if (ascending != SortOrder.EPISODE_FILENAME_A_Z && ascending != SortOrder.SIZE_SMALL_LARGE)
                super.onAddItem(title, ascending, descending, ascendingIsDefault)
        }
        @UnstableApi override fun onSelectionChanged() {
            super.onSelectionChanged()
            binding.keepSortedCheckbox.setEnabled(sortOrder != SortOrder.RANDOM)
            if (sortOrder == SortOrder.RANDOM) binding.keepSortedCheckbox.setChecked(false)
            UserPreferences.isQueueKeepSorted = binding.keepSortedCheckbox.isChecked
            UserPreferences.queueKeepSortedOrder = sortOrder
            reorderQueue(sortOrder, true)
        }
        /**
         * Sort the episodes in the queue with the given the named sort order.
         * @param broadcastUpdate `true` if this operation should trigger a
         * QueueUpdateBroadcast. This option should be set to `false`
         * if the caller wants to avoid unexpected updates of the GUI.
         */
        private fun reorderQueue(sortOrder: SortOrder?, broadcastUpdate: Boolean) : Job {
            Logd(TAG, "reorderQueue called")
            if (sortOrder == null) {
                Logd(TAG, "reorderQueue() - sortOrder is null. Do nothing.")
                return Job()
            }
            val permutor = getPermutor(sortOrder)
            return runOnIOScope {
                permutor.reorder(curQueue.episodes)
                curQueue.update()
                curQueue.episodeIds.clear()
                for (e in curQueue.episodes) curQueue.episodeIds.add(e.id)
                upsert(curQueue) {}
                if (broadcastUpdate) EventFlow.postEvent(FlowEvent.QueueEvent.sorted(curQueue.episodes))
            }
        }
    }

    private inner class QueueSwipeActions
        : SwipeActions(ItemTouchHelper.UP or ItemTouchHelper.DOWN, this@QueueFragment, TAG) {
        // Position tracking whilst dragging
        var dragFrom: Int = -1
        var dragTo: Int = -1

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val fromPosition = viewHolder.bindingAdapterPosition
            val toPosition = target.bindingAdapterPosition

            // Update tracked position
            if (dragFrom == -1) dragFrom = fromPosition
            dragTo = toPosition

            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            Logd(TAG, "move($from, $to) in memory")
            if (from >= queueItems.size || to >= queueItems.size || from < 0 || to < 0) return false

            queueItems.add(to, queueItems.removeAt(from))
            adapter?.notifyItemMoved(from, to)
            return true
        }
        @UnstableApi override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            //SwipeActions
            super.onSwiped(viewHolder, direction)
        }
        override fun isLongPressDragEnabled(): Boolean {
            return false
        }
        @UnstableApi override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            // Check if drag finished
            if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) reallyMoved(dragFrom, dragTo)
            dragTo = -1
            dragFrom = dragTo
        }
        @UnstableApi private fun reallyMoved(from: Int, to: Int) {
            Logd(TAG, "Write to database move($from, $to)")
            moveInQueue(from, to, true)
        }
    }

    private inner class QueueRecyclerAdapter : EpisodesAdapter(activity as MainActivity) {
        private var dragDropEnabled: Boolean

        init {
            dragDropEnabled = !(UserPreferences.isQueueKeepSorted || UserPreferences.isQueueLocked)
        }
        fun updateDragDropEnabled() {
            dragDropEnabled = !(UserPreferences.isQueueKeepSorted || UserPreferences.isQueueLocked)
            notifyDataSetChanged()
        }
        @UnstableApi
        override fun afterBindViewHolder(holder: EpisodeViewHolder, pos: Int) {
            if (inActionMode() || !dragDropEnabled) {
                holder.dragHandle.setVisibility(View.GONE)
                holder.dragHandle.setOnTouchListener(null)
//            holder.coverHolder.setOnTouchListener(null)
            } else {
                holder.dragHandle.setVisibility(View.VISIBLE)
                holder.dragHandle.setOnTouchListener { _: View?, event: MotionEvent ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) swipeActions.startDrag(holder)
                    false
                }
                holder.coverHolder.setOnTouchListener { v1, event ->
                    if (!inActionMode() && event.actionMasked == MotionEvent.ACTION_DOWN) {
                        val isLtr = holder.itemView.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR
                        val factor = (if (isLtr) 1 else -1).toFloat()
                        if (factor * event.x < factor * 0.5 * v1.width) swipeActions.startDrag(holder)
                        else Logd(TAG, "Ignoring drag in right half of the image")
                    }
                    false
                }
            }
            if (inActionMode()) {
                holder.dragHandle.setOnTouchListener(null)
//            holder.coverHolder.setOnTouchListener(null)
            }

            holder.isInQueue.setVisibility(View.GONE)
        }
        @UnstableApi override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
            val inflater: MenuInflater = activity!!.getMenuInflater()
            inflater.inflate(R.menu.queue_context, menu)
            super.onCreateContextMenu(menu, v, menuInfo)

            if (!inActionMode()) {
//            menu.findItem(R.id.multi_select).setVisible(true)
                val keepSorted: Boolean = UserPreferences.isQueueKeepSorted
                if (getItem(0)?.id === longPressedItem?.id || keepSorted) menu.findItem(R.id.move_to_top_item).setVisible(false)
                if (getItem(itemCount - 1)?.id === longPressedItem?.id || keepSorted) menu.findItem(R.id.move_to_bottom_item).setVisible(false)
            } else {
                menu.findItem(R.id.move_to_top_item).setVisible(false)
                menu.findItem(R.id.move_to_bottom_item).setVisible(false)
            }
            MenuItemUtils.setOnClickListeners(menu) { item: MenuItem -> onContextItemSelected(item) }
        }
    }

    companion object {
        val TAG = QueueFragment::class.simpleName ?: "Anonymous"

        private const val KEY_UP_ARROW = "up_arrow"

        private const val PREFS = "QueueFragment"
        private const val PREF_SHOW_LOCK_WARNING = "show_lock_warning"

        private var prefs: SharedPreferences? = null
        fun getSharedPrefs(context: Context) {
            if (prefs == null) prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }
}
