package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.CheckboxDoNotShowAgainBinding
import ac.mdiq.podcini.databinding.MultiSelectSpeedDialBinding
import ac.mdiq.podcini.databinding.QueueFragmentBinding
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.getCurrentPlaybackSpeed
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.database.Queues.clearQueue
import ac.mdiq.podcini.storage.database.Queues.isQueueKeepSorted
import ac.mdiq.podcini.storage.database.Queues.isQueueLocked
import ac.mdiq.podcini.storage.database.Queues.moveInQueue
import ac.mdiq.podcini.storage.database.Queues.queueKeepSortedOrder
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.unmanaged
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.storage.utils.EpisodeUtil
import ac.mdiq.podcini.storage.utils.EpisodesPermutors.getPermutor
import ac.mdiq.podcini.ui.actions.EpisodeMultiSelectHandler
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.adapter.EpisodesAdapter
import ac.mdiq.podcini.ui.adapter.SelectableAdapter
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.dialog.ConfirmationDialog
import ac.mdiq.podcini.ui.dialog.EpisodeSortDialog
import ac.mdiq.podcini.ui.utils.EmptyViewHandler
import ac.mdiq.podcini.ui.utils.LiftOnScrollListener
import ac.mdiq.podcini.ui.view.EpisodeViewHolder
import ac.mdiq.podcini.ui.view.EpisodesRecyclerView
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
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
    private lateinit var swipeActions: SwipeActions
    private lateinit var speedDialView: SpeedDialView

    private lateinit var queueNames: Array<String>
    private lateinit var spinnerTexts: MutableList<String>
    private lateinit var queueSpinner: Spinner
    private lateinit var spinnerAdaptor: ArrayAdapter<String>

    private var displayUpArrow = false
    private var queueItems: MutableList<Episode> = mutableListOf()

    private var adapter: QueueRecyclerAdapter? = null

    private var showBin: Boolean = false
    private var addToQueueActionItem: SpeedDialActionItem? = null

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

        val queues = realm.query(PlayQueue::class).find()
        queueNames = queues.map { it.name }.toTypedArray()
        spinnerTexts = queues.map { "${it.name} : ${it.episodeIds.size}" }.toMutableList()
        val spinnerLayout = inflater.inflate(R.layout.queue_title_spinner, toolbar, false)
        queueSpinner = spinnerLayout.findViewById(R.id.queue_spinner)
        val params = Toolbar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.gravity = Gravity.CENTER_VERTICAL
        toolbar.addView(spinnerLayout)
        spinnerAdaptor = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, spinnerTexts)
        spinnerAdaptor.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        queueSpinner.adapter = spinnerAdaptor
        queueSpinner.setSelection(queueNames.indexOf(curQueue.name))
        queueSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                curQueue = upsertBlk(queues[position]) { it.update() }
                toolbar.menu?.findItem(R.id.rename_queue)?.setVisible(curQueue.name != "Default")
                loadCurQueue(true)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        queueSpinner.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                Logd(TAG, "Spinner is opening")
                val queues = realm.query(PlayQueue::class).find()
                spinnerTexts.clear()
                spinnerTexts.addAll(queues.map { "${it.name} : ${it.episodeIds.size}" })
                spinnerAdaptor.notifyDataSetChanged()
            }
            false
        }
        (activity as MainActivity).setupToolbarToggle(toolbar, displayUpArrow)
        toolbar.inflateMenu(R.menu.queue)
        refreshMenuItems()
        binding.progressBar.visibility = View.VISIBLE

        recyclerView = binding.recyclerView
        val animator: RecyclerView.ItemAnimator? = recyclerView.itemAnimator
        if (animator != null && animator is SimpleItemAnimator) animator.supportsChangeAnimations = false

        recyclerView.setRecycledViewPool((activity as MainActivity).recycledViewPool)
        registerForContextMenu(recyclerView)
        recyclerView.addOnScrollListener(LiftOnScrollListener(binding.appbar))

        swipeActions = QueueSwipeActions()
        lifecycle.addObserver(swipeActions)
        swipeActions.setFilter(EpisodeFilter(EpisodeFilter.States.queued.name))
        swipeActions.attachTo(recyclerView)
        refreshSwipeTelltale()
        binding.leftActionIcon.setOnClickListener { swipeActions.showDialog() }
        binding.rightActionIcon.setOnClickListener { swipeActions.showDialog() }

        adapter = QueueRecyclerAdapter()
        adapter?.setOnSelectModeListener(this)
        recyclerView.adapter = adapter

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
        addToQueueActionItem = speedDialView.removeActionItemById(R.id.add_to_queue_batch)
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
            EpisodeMultiSelectHandler((activity as MainActivity), actionItem.id).handleAction(adapter!!.selectedItems.filterIsInstance<Episode>())
            adapter?.endSelectMode()
            true
        }
        return binding.root
    }

    override fun onStart() {
        Logd(TAG, "onStart() called")
        super.onStart()
        adapter?.refreshFragPosCallback = ::refreshPosCallback
        loadCurQueue(true)
        procFlowEvents()
//        if (queueItems.isNotEmpty()) recyclerView.restoreScrollPosition(TAG)
    }

    override fun onStop() {
        Logd(TAG, "onStop() called")
        super.onStop()
        adapter?.refreshFragPosCallback = null
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
                    is FlowEvent.PlayEvent -> onPlayEvent(event)
                    is FlowEvent.PlayerSettingsEvent -> onPlayerSettingsEvent(event)
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
//                    is FlowEvent.FeedUpdatingEvent -> swipeRefreshLayout.isRefreshing = event.isRunning
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

    private fun refreshPosCallback(pos: Int, episode: Episode) {
        Logd(TAG, "Queue refreshPosCallback: $pos ${episode.title}")
        if (isAdded && activity != null) refreshInfoBar()
    }

    private fun onQueueEvent(event: FlowEvent.QueueEvent) {
        Logd(TAG, "onQueueEvent() called with ${event.action.name}")
        if (showBin) return
        if (adapter == null) {
            loadCurQueue(true)
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
                adapter?.updateItems(queueItems)
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
            FlowEvent.QueueEvent.Action.SWITCH_QUEUE -> loadCurQueue(false)
            FlowEvent.QueueEvent.Action.CLEARED -> {
                queueItems.clear()
                adapter?.updateItems(queueItems)
            }
            FlowEvent.QueueEvent.Action.MOVED, FlowEvent.QueueEvent.Action.DELETED_MEDIA -> return
        }
        adapter?.updateDragDropEnabled()
        refreshMenuItems()
        recyclerView.saveScrollPosition(TAG)
        refreshInfoBar()
    }

    private fun onPlayEvent(event: FlowEvent.PlayEvent) {
        Logd(TAG, "onPlayEvent ${event.episode.title}")
        val pos: Int = EpisodeUtil.indexOfItemWithId(queueItems, event.episode.id)
        if (pos >= 0) adapter?.notifyItemChangedCompat(pos)
    }

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
//        Logd(TAG, "onEventMainThread() called with ${event.TAG}")
        if (loadItemsRunning) return
        for (downloadUrl in event.urls) {
            val pos: Int = EpisodeUtil.indexOfItemWithDownloadUrl(queueItems.toList(), downloadUrl)
            if (pos >= 0) {
                adapter?.notifyItemChangedCompat(pos)
            }
        }
    }

    private fun onPlayerSettingsEvent(event: FlowEvent.PlayerSettingsEvent) {
        if (showBin) return
        //        Logd(TAG, "onPlayerStatusChanged() called with event = [$event]")
        loadCurQueue(false)
        refreshMenuItems()
    }

    private fun onEpisodePlayedEvent(event: FlowEvent.EpisodePlayedEvent) {
        // Sent when playback position is reset
        Logd(TAG, "onUnreadItemsChanged() called with event = [$event]")
        if (event.episode == null) {
            if (!showBin) loadCurQueue(false)
        }
        refreshMenuItems()
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
        Logd(TAG, "onDestroyView")
        _binding = null
        queueItems = mutableListOf()
        adapter?.endSelectMode()
        adapter?.clearData()
        adapter = null
        toolbar.setOnMenuItemClickListener(null)
        toolbar.setOnLongClickListener(null)
        super.onDestroyView()
    }

    private fun refreshMenuItems() {
        if (showBin) {
            toolbar.menu?.findItem(R.id.queue_sort)?.setVisible(false)
            toolbar.menu?.findItem(R.id.rename_queue)?.setVisible(false)
            toolbar.menu?.findItem(R.id.add_queue)?.setVisible(false)
            toolbar.menu?.findItem(R.id.queue_lock)?.setVisible(false)
            toolbar.menu?.findItem(R.id.action_search)?.setVisible(false)
        } else {
            toolbar.menu?.findItem(R.id.action_search)?.setVisible(true)
            toolbar.menu?.findItem(R.id.queue_sort)?.setVisible(true)
            toolbar.menu?.findItem(R.id.queue_lock)?.setChecked(isQueueLocked)
            toolbar.menu?.findItem(R.id.queue_lock)?.setVisible(!isQueueKeepSorted)
            toolbar.menu?.findItem(R.id.rename_queue)?.setVisible(curQueue.name != "Default")
            toolbar.menu?.findItem(R.id.add_queue)?.setVisible(queueNames.size < 9)
        }
    }

    @UnstableApi override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        when (itemId) {
            R.id.show_bin -> {
                showBin = !showBin
                refreshMenuItems()
                if (showBin) {
                    item.setIcon(R.drawable.playlist_play)
                    speedDialView.addActionItem(addToQueueActionItem)
                } else {
                    item.setIcon(R.drawable.ic_history)
                    speedDialView.removeActionItem(addToQueueActionItem)
                }
                loadCurQueue(false)
            }
            R.id.queue_lock -> toggleQueueLock()
            R.id.queue_sort -> QueueSortDialog().show(childFragmentManager.beginTransaction(), "SortDialog")
            R.id.rename_queue -> renameQueue()
            R.id.add_queue -> addQueue()
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
            R.id.clear_bin -> {
                curQueue = upsertBlk(curQueue) {
                    it.idsBinList.clear()
                    it.update()
                }
                if (showBin) loadCurQueue(false)
            }
            R.id.action_search -> (activity as MainActivity).loadChildFragment(SearchFragment.newInstance())
            else -> return false
        }
        return true
    }

    private fun renameQueue() {
        val composeView = ComposeView(requireContext()).apply {
            setContent {
                val showDialog = remember { mutableStateOf(true) }
                CustomTheme(requireContext()) {
                    RenameQueueDialog(showDialog = showDialog.value, onDismiss = { showDialog.value = false })
                }
            }
        }
        (view as? ViewGroup)?.addView(composeView)
    }

    private fun addQueue() {
        val composeView = ComposeView(requireContext()).apply {
            setContent {
                val showDialog = remember { mutableStateOf(true) }
                CustomTheme(requireContext()) {
                    AddQueueDialog(showDialog = showDialog.value, onDismiss = { showDialog.value = false })
                }
            }
        }
        (view as? ViewGroup)?.addView(composeView)
    }

    @Composable
    fun RenameQueueDialog(showDialog: Boolean, onDismiss: () -> Unit) {
        if (showDialog) {
            Dialog(onDismissRequest = onDismiss) {
                Card(
                    modifier = Modifier
                        .wrapContentSize(align = Alignment.Center)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var newName by remember { mutableStateOf(curQueue.name) }
                        TextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Rename (Unique name only)") }
                        )
                        Button(onClick = {
                            if (newName.isNotEmpty() && curQueue.name != newName && queueNames.indexOf(newName) < 0) {
                                val oldName = curQueue.name
                                runOnIOScope {
                                    curQueue = upsertBlk(curQueue) {
                                        it.name = newName
                                    }
                                }
                                val index_ = queueNames.indexOf(oldName)
                                queueNames[index_] = newName
                                spinnerTexts[index_] = newName + " : " + curQueue.episodeIds.size
                                spinnerAdaptor.notifyDataSetChanged()
                                onDismiss()
                            }
                        }) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun AddQueueDialog(showDialog: Boolean, onDismiss: () -> Unit) {
        if (showDialog) {
            Dialog(onDismissRequest = onDismiss) {
                Card(
                    modifier = Modifier
                        .wrapContentSize(align = Alignment.Center)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var newName by remember { mutableStateOf("") }
                        TextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Add queue (Unique name only)") }
                        )
                        Button(onClick = {
                            if (newName.isNotEmpty() && queueNames.indexOf(newName) < 0) {
                                val newQueue = PlayQueue()
                                newQueue.id = queueNames.size.toLong()
                                newQueue.name = newName
                                upsertBlk(newQueue) {}
                                val queues = realm.query(PlayQueue::class).find()
                                queueNames = queues.map { it.name }.toTypedArray()
                                spinnerTexts.addAll(queues.map { "${it.name} : ${it.episodeIds.size}" })
//                                spinnerAdaptor = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, spinnerTexts)
//                                spinnerAdaptor.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                                spinnerAdaptor.notifyDataSetChanged()
                                queueSpinner.adapter = spinnerAdaptor
                                queueSpinner.setSelection(spinnerAdaptor.getPosition(curQueue.name))
                                onDismiss()
                            }
                        }) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }

    @UnstableApi private fun toggleQueueLock() {
        val isLocked: Boolean = isQueueLocked
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
        isQueueLocked = locked
        refreshMenuItems()
        adapter?.updateDragDropEnabled()

        if (queueItems.size == 0) {
            if (locked) (activity as MainActivity).showSnackbarAbovePlayer(R.string.queue_locked, Snackbar.LENGTH_SHORT)
            else (activity as MainActivity).showSnackbarAbovePlayer(R.string.queue_unlocked, Snackbar.LENGTH_SHORT)
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
            info += DurationConverter.getDurationStringLocalized(requireActivity(), timeLeft)
        }
        binding.infoBar.text = info
//        toolbar.title = "${getString(R.string.queue_label)}: ${curQueue.name}"
    }

    private var loadItemsRunning = false
    private fun loadCurQueue(restoreScrollPosition: Boolean) {
        if (!loadItemsRunning) {
            loadItemsRunning = true
            adapter?.updateItems(mutableListOf())
            Logd(TAG, "loadCurQueue() called ${curQueue.name}")
            while (curQueue.name.isEmpty()) runBlocking { delay(100) }
            if (queueItems.isEmpty()) emptyView.hide()
            queueItems.clear()
            Logd(TAG, "loadCurQueue() showBin: $showBin")
            if (showBin) {
                queueItems.addAll(realm.query(Episode::class, "id IN $0", curQueue.idsBinList)
                    .find().sortedByDescending { curQueue.idsBinList.indexOf(it.id) })
            } else {
                curQueue.episodes.clear()
//                curQueue.episodes.addAll(realm.query(Episode::class, "id IN $0", curQueue.episodeIds)
//                    .find().sortedBy { curQueue.episodeIds.indexOf(it.id) })
                queueItems.addAll(curQueue.episodes)
            }
            Logd(TAG, "loadCurQueue() curQueue.episodes: ${curQueue.episodes.size}")

            binding.progressBar.visibility = View.GONE
//        adapter?.setDummyViews(0)
            adapter?.updateItems(queueItems)
            if (restoreScrollPosition) recyclerView.restoreScrollPosition(TAG)
            refreshInfoBar()
            loadItemsRunning = false
        }
    }

    override fun onStartSelectMode() {
        swipeActions.detach()
        speedDialView.visibility = View.VISIBLE
        refreshMenuItems()
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
            if (isQueueKeepSorted) sortOrder = queueKeepSortedOrder
            val view: View = super.onCreateView(inflater, container, savedInstanceState)!!
            binding.keepSortedCheckbox.visibility = View.VISIBLE
            binding.keepSortedCheckbox.setChecked(isQueueKeepSorted)
            // Disable until something gets selected
            binding.keepSortedCheckbox.setEnabled(isQueueKeepSorted)
            return view
        }
        override fun onAddItem(title: Int, ascending: EpisodeSortOrder, descending: EpisodeSortOrder, ascendingIsDefault: Boolean) {
            if (ascending != EpisodeSortOrder.EPISODE_FILENAME_A_Z && ascending != EpisodeSortOrder.SIZE_SMALL_LARGE)
                super.onAddItem(title, ascending, descending, ascendingIsDefault)
        }
        @UnstableApi override fun onSelectionChanged() {
            super.onSelectionChanged()
            binding.keepSortedCheckbox.setEnabled(sortOrder != EpisodeSortOrder.RANDOM)
            if (sortOrder == EpisodeSortOrder.RANDOM) binding.keepSortedCheckbox.setChecked(false)
            isQueueKeepSorted = binding.keepSortedCheckbox.isChecked
            queueKeepSortedOrder = sortOrder
            reorderQueue(sortOrder, true)
        }
        /**
         * Sort the episodes in the queue with the given the named sort order.
         * @param broadcastUpdate `true` if this operation should trigger a
         * QueueUpdateBroadcast. This option should be set to `false`
         * if the caller wants to avoid unexpected updates of the GUI.
         */
        private fun reorderQueue(sortOrder: EpisodeSortOrder?, broadcastUpdate: Boolean) : Job {
            Logd(TAG, "reorderQueue called")
            if (sortOrder == null) {
                Logd(TAG, "reorderQueue() - sortOrder is null. Do nothing.")
                return Job()
            }
            val permutor = getPermutor(sortOrder)
            return runOnIOScope {
                permutor.reorder(curQueue.episodes)
                val episodes_ = curQueue.episodes.toMutableList()
                curQueue = upsert(curQueue) {
                    it.episodeIds.clear()
                    for (e in episodes_) it.episodeIds.add(e.id)
                    it.update()
                }
                if (broadcastUpdate) EventFlow.postEvent(FlowEvent.QueueEvent.sorted(curQueue.episodes))
            }
        }
    }

    private inner class QueueSwipeActions : SwipeActions(ItemTouchHelper.UP or ItemTouchHelper.DOWN, this@QueueFragment, TAG) {
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
            dragDropEnabled = !(isQueueKeepSorted || isQueueLocked)
        }
        fun updateDragDropEnabled() {
            dragDropEnabled = !(isQueueKeepSorted || isQueueLocked)
            notifyDataSetChanged()
        }
        @UnstableApi
        override fun afterBindViewHolder(holder: EpisodeViewHolder, pos: Int) {
            if (inActionMode() || !dragDropEnabled) {
                holder.dragHandle.visibility = View.GONE
                holder.dragHandle.setOnTouchListener(null)
//            holder.coverHolder.setOnTouchListener(null)
            } else {
                holder.dragHandle.visibility = View.VISIBLE
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
