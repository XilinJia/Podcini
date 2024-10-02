package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.CheckboxDoNotShowAgainBinding
import ac.mdiq.podcini.databinding.QueueFragmentBinding
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.getCurrentPlaybackSpeed
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.mediaBrowser
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.database.Queues.clearQueue
import ac.mdiq.podcini.storage.database.Queues.isQueueKeepSorted
import ac.mdiq.podcini.storage.database.Queues.isQueueLocked
import ac.mdiq.podcini.storage.database.Queues.queueKeepSortedOrder
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.storage.utils.EpisodeUtil
import ac.mdiq.podcini.storage.utils.EpisodesPermutors.getPermutor
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeAction
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.compose.InforBar
import ac.mdiq.podcini.ui.compose.queueChanged
import ac.mdiq.podcini.ui.dialog.ConfirmationDialog
import ac.mdiq.podcini.ui.dialog.EpisodeSortDialog
import ac.mdiq.podcini.ui.utils.EmptyViewHandler
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.annotation.SuppressLint
import android.content.ComponentName
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.leinardi.android.speeddial.SpeedDialActionItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.math.max

/**
 * Shows all items in the queue.
 */
@UnstableApi class QueuesFragment : Fragment(), Toolbar.OnMenuItemClickListener {

    private var _binding: QueueFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var emptyViewHandler: EmptyViewHandler
    private lateinit var toolbar: MaterialToolbar
    private lateinit var swipeActions: SwipeActions

    private var infoTextUpdate = ""
    private var infoText = ""
    private var infoBarText = mutableStateOf("")
    private var leftActionState = mutableStateOf<SwipeAction?>(null)
    private var rightActionState = mutableStateOf<SwipeAction?>(null)

    private lateinit var spinnerLayout: View
    private lateinit var queueNames: Array<String>
    private lateinit var spinnerTexts: MutableList<String>
    private lateinit var queueSpinner: Spinner
    private lateinit var spinnerAdaptor: ArrayAdapter<String>
    private lateinit var queues: List<PlayQueue>

    private var displayUpArrow = false
    private val queueItems = mutableStateListOf<Episode>()

    private var showBin: Boolean = false
    private var addToQueueActionItem: SpeedDialActionItem? = null

    private var dragDropEnabled: Boolean = !(isQueueKeepSorted || isQueueLocked)

    private lateinit var browserFuture: ListenableFuture<MediaBrowser>

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
//        toolbar.setOnLongClickListener {
//            recyclerView.scrollToPosition(5)
//            recyclerView.post { recyclerView.smoothScrollToPosition(0) }
//            false
//        }
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

        queues = realm.query(PlayQueue::class).find()
        queueNames = queues.map { it.name }.toTypedArray()
        spinnerTexts = queues.map { "${it.name} : ${it.episodeIds.size}" }.toMutableList()
        spinnerLayout = inflater.inflate(R.layout.queue_title_spinner, toolbar, false)
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
                val prevQueueSize = curQueue.size()
                curQueue = upsertBlk(queues[position]) { it.update() }
                toolbar.menu?.findItem(R.id.rename_queue)?.setVisible(curQueue.name != "Default")
                loadCurQueue(true)
                playbackService?.notifyCurQueueItemsChanged(max(prevQueueSize, curQueue.size()))
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

//        recyclerView.setRecycledViewPool((activity as MainActivity).recycledViewPool)
//        registerForContextMenu(recyclerView)
//        recyclerView.addOnScrollListener(LiftOnScrollListener(binding.appbar))

        swipeActions = SwipeActions(this, TAG)
        swipeActions.setFilter(EpisodeFilter(EpisodeFilter.States.queued.name))
        binding.infobar.setContent {
            CustomTheme(requireContext()) {
                InforBar(infoBarText, leftAction = leftActionState, rightAction = rightActionState, actionConfig = {swipeActions.showDialog()})
            }
        }
        binding.lazyColumn.setContent {
            CustomTheme(requireContext()) {
                EpisodeLazyColumn(activity as MainActivity, episodes = queueItems,
                    leftSwipeCB = { if (leftActionState.value == null) swipeActions.showDialog() else leftActionState.value?.performAction(it, this, swipeActions.filter ?: EpisodeFilter())},
                    rightSwipeCB = { if (rightActionState.value == null) swipeActions.showDialog() else rightActionState.value?.performAction(it, this, swipeActions.filter ?: EpisodeFilter())}, )
            }
        }

        lifecycle.addObserver(swipeActions)
        refreshSwipeTelltale()

        emptyViewHandler = EmptyViewHandler(requireContext())
//        emptyViewHandler.attachToRecyclerView(recyclerView)
        emptyViewHandler.setIcon(R.drawable.ic_playlist_play)
        emptyViewHandler.setTitle(R.string.no_items_header_label)
        emptyViewHandler.setMessage(R.string.no_items_label)
//        emptyViewHandler.updateAdapter(adapter)

        return binding.root
    }

    override fun onStart() {
        Logd(TAG, "onStart() called")
        super.onStart()
        loadCurQueue(true)
        procFlowEvents()
        val sessionToken = SessionToken(requireContext(), ComponentName(requireContext(), PlaybackService::class.java))
        browserFuture = MediaBrowser.Builder(requireContext(), sessionToken).buildAsync()
        browserFuture.addListener({
            // here we can get the root of media items tree or we can get also the children if it is an album for example.
            mediaBrowser = browserFuture.get()
            mediaBrowser?.subscribe("CurQueue", null)
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        Logd(TAG, "onStop() called")
        super.onStop()
        cancelFlowEvents()
        mediaBrowser?.unsubscribe("CurQueue")
        mediaBrowser = null
        MediaBrowser.releaseFuture(browserFuture)
    }

    override fun onPause() {
        Logd(TAG, "onPause() called")
        super.onPause()
//        recyclerView.saveScrollPosition(TAG)
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
                    is FlowEvent.FeedUpdatingEvent -> {
                        infoTextUpdate = if (event.isRunning) "U" else ""
                        infoBarText.value = "$infoText $infoTextUpdate"
                    }
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

//    private fun refreshPosCallback(pos: Int, episode: Episode) {
////        Logd(TAG, "Queue refreshPosCallback: $pos ${episode.title}")
//        if (isAdded && activity != null) refreshInfoBar()
//    }

    private fun onQueueEvent(event: FlowEvent.QueueEvent) {
        Logd(TAG, "onQueueEvent() called with ${event.action.name}")
        if (showBin) return
        when (event.action) {
            FlowEvent.QueueEvent.Action.ADDED -> {
                if (event.episodes.isNotEmpty() && !curQueue.contains(event.episodes[0])) queueItems.addAll(event.episodes)
            }
            FlowEvent.QueueEvent.Action.SET_QUEUE, FlowEvent.QueueEvent.Action.SORTED -> {
                queueItems.clear()
                queueItems.addAll(event.episodes)
            }
            FlowEvent.QueueEvent.Action.REMOVED, FlowEvent.QueueEvent.Action.IRREVERSIBLE_REMOVED -> {
                if (event.episodes.isNotEmpty()) {
                    for (e in event.episodes) {
                        val pos: Int = EpisodeUtil.indexOfItemWithId(queueItems, e.id)
                        if (pos >= 0) {
                            Logd(TAG, "removing episode $pos ${queueItems[pos].title} $e")
                            queueItems[pos].stopMonitoring.value = true
                            queueItems.removeAt(pos)
                        } else {
                            Log.e(TAG, "Trying to remove item non-existent from queue ${e.id} ${e.title}")
                            continue
                        }
                    }
                }
            }
            FlowEvent.QueueEvent.Action.SWITCH_QUEUE -> {
                loadCurQueue(false)
                playbackService?.notifyCurQueueItemsChanged(event.episodes.size)
            }
            FlowEvent.QueueEvent.Action.CLEARED -> {
                queueItems.clear()
            }
            FlowEvent.QueueEvent.Action.MOVED, FlowEvent.QueueEvent.Action.DELETED_MEDIA -> return
        }
        queueChanged++
//        adapter?.updateDragDropEnabled()
        refreshMenuItems()
//        recyclerView.saveScrollPosition(TAG)
        refreshInfoBar()
    }

    private fun onPlayEvent(event: FlowEvent.PlayEvent) {
        val pos: Int = EpisodeUtil.indexOfItemWithId(queueItems, event.episode.id)
        Logd(TAG, "onPlayEvent action: ${event.action} pos: $pos ${event.episode.title}")
//        if (pos >= 0) queueItems[pos].isPlayingState.value = event.isPlaying()
    }

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
//        Logd(TAG, "onEventMainThread() called with ${event.TAG}")
        if (loadItemsRunning) return
        for (url in event.urls) {
//            if (!event.isCompleted(url)) continue
            val pos: Int = EpisodeUtil.indexOfItemWithDownloadUrl(queueItems.toList(), url)
            if (pos >= 0) queueItems[pos].downloadState.value = event.map[url]?.state ?: DownloadStatus.State.UNKNOWN.ordinal
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
        Logd(TAG,"speedPresetChanged called")
        for (item in queueItems) {
            if (item.feed?.id == event.feed.id) item.feed = null
        }
    }

    private fun refreshSwipeTelltale() {
        leftActionState.value = swipeActions.actions?.left
        rightActionState.value = swipeActions.actions?.right
    }

    @SuppressLint("RestrictedApi")
    private fun onKeyUp(event: KeyEvent) {
        if (!isAdded || !isVisible || !isMenuVisible) return
        when (event.keyCode) {
//            KeyEvent.KEYCODE_T -> recyclerView.smoothScrollToPosition(0)
//            KeyEvent.KEYCODE_B -> recyclerView.smoothScrollToPosition(adapter!!.itemCount - 1)
            else -> {}
        }
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        _binding = null
        queueItems.clear()
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
                if (showBin) {
                    toolbar.removeView(spinnerLayout)
                    toolbar.title = curQueue.name + " Bin"
                } else {
                    toolbar.title = ""
                    toolbar.addView(spinnerLayout)
                }
                refreshMenuItems()
                if (showBin) {
                    item.setIcon(R.drawable.playlist_play)
//                    speedDialView.addActionItem(addToQueueActionItem)
                } else {
                    item.setIcon(R.drawable.ic_history)
//                    speedDialView.removeActionItem(addToQueueActionItem)
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
            R.id.refresh_all -> FeedUpdateManager.runOnceOrAsk(requireContext())
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
                Card(modifier = Modifier
                    .wrapContentSize(align = Alignment.Center)
                    .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var newName by remember { mutableStateOf(curQueue.name) }
                        TextField(value = newName,
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
                Card(modifier = Modifier
                    .wrapContentSize(align = Alignment.Center)
                    .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var newName by remember { mutableStateOf("") }
                        TextField(value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Add queue (Unique name only)") }
                        )
                        Button(onClick = {
                            if (newName.isNotEmpty() && queueNames.indexOf(newName) < 0) {
                                val newQueue = PlayQueue()
                                newQueue.id = queueNames.size.toLong()
                                newQueue.name = newName
                                upsertBlk(newQueue) {}
                                queues = realm.query(PlayQueue::class).find()
                                queueNames = queues.map { it.name }.toTypedArray()
                                spinnerTexts.addAll(queues.map { "${it.name} : ${it.episodeIds.size}" })
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
//        adapter?.updateDragDropEnabled()

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
        infoText = String.format(Locale.getDefault(), "%d%s", queueItems.size, getString(R.string.episodes_suffix))
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
            infoText += " • "
            infoText += DurationConverter.getDurationStringLocalized(requireActivity(), timeLeft)
        }
        infoBarText.value = "$infoText $infoTextUpdate"
    }

    private var loadItemsRunning = false
    private fun loadCurQueue(restoreScrollPosition: Boolean) {
        if (!loadItemsRunning) {
            loadItemsRunning = true
//            adapter?.updateItems(mutableListOf())
            Logd(TAG, "loadCurQueue() called ${curQueue.name}")
            while (curQueue.name.isEmpty()) runBlocking { delay(100) }
            if (queueItems.isNotEmpty()) emptyViewHandler.hide()
            queueItems.clear()
            if (showBin) queueItems.addAll(realm.query(Episode::class, "id IN $0", curQueue.idsBinList)
                    .find().sortedByDescending { curQueue.idsBinList.indexOf(it.id) })
            else {
                curQueue.episodes.clear()
                queueItems.addAll(curQueue.episodes)
            }
            Logd(TAG, "loadCurQueue() curQueue.episodes: ${curQueue.episodes.size}")

//            if (restoreScrollPosition) recyclerView.restoreScrollPosition(TAG)
            refreshInfoBar()
//            playbackService?.notifyCurQueueItemsChanged()
            loadItemsRunning = false
        }
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

    companion object {
        val TAG = QueuesFragment::class.simpleName ?: "Anonymous"

        private const val KEY_UP_ARROW = "up_arrow"

        private const val PREFS = "QueueFragment"
        private const val PREF_SHOW_LOCK_WARNING = "show_lock_warning"

        private var prefs: SharedPreferences? = null
        fun getSharedPrefs(context: Context) {
            if (prefs == null) prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }
}
