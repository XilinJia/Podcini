package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.CheckboxDoNotShowAgainBinding
import ac.mdiq.podcini.databinding.ComposeFragmentBinding
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.getCurrentPlaybackSpeed
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.mediaBrowser
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.storage.database.Queues.clearQueue
import ac.mdiq.podcini.storage.database.Queues.isQueueKeepSorted
import ac.mdiq.podcini.storage.database.Queues.moveInQueueSync
import ac.mdiq.podcini.storage.database.Queues.queueKeepSortedOrder
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.storage.utils.EpisodeUtil
import ac.mdiq.podcini.storage.utils.EpisodesPermutors.getPermutor
import ac.mdiq.podcini.ui.actions.SwipeAction
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.actions.SwipeActions.NoActionSwipeAction
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.*
import ac.mdiq.podcini.ui.dialog.ConfirmationDialog
import ac.mdiq.podcini.ui.dialog.EpisodeSortDialog
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
import android.widget.CheckBox
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.NumberFormat
import java.util.*
import kotlin.math.max

class QueuesFragment : Fragment(), Toolbar.OnMenuItemClickListener {

    private var _binding: ComposeFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var toolbar: MaterialToolbar
    private lateinit var swipeActions: SwipeActions
    private lateinit var swipeActionsBin: SwipeActions

    private var infoTextUpdate = ""
    private var infoText = ""
    private var infoBarText = mutableStateOf("")

    private var leftActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
    private var rightActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
    private var leftActionStateBin = mutableStateOf<SwipeAction>(NoActionSwipeAction())
    private var rightActionStateBin = mutableStateOf<SwipeAction>(NoActionSwipeAction())

    private var isQueueLocked = appPrefs.getBoolean(UserPreferences.Prefs.prefQueueLocked.name, true)

    private lateinit var queueNames: Array<String>
    private val spinnerTexts = mutableStateListOf<String>()
    private lateinit var queues: List<PlayQueue>
    private lateinit var spinnerView:  ComposeView

    private var displayUpArrow = false
    private val queueItems = mutableListOf<Episode>()
    private val vms = mutableStateListOf<EpisodeVM>()
    private var feedsAssociated = listOf<Feed>()

    private var showBin by mutableStateOf(false)
    private var showFeeds by mutableStateOf(false)
    private var dragDropEnabled by mutableStateOf(!(isQueueKeepSorted || isQueueLocked))

    private lateinit var browserFuture: ListenableFuture<MediaBrowser>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = ComposeFragmentBinding.inflate(inflater)

        Logd(TAG, "fragment onCreateView")
        toolbar = binding.toolbar
        toolbar.setOnMenuItemClickListener(this)
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

        queues = realm.query(PlayQueue::class).find()
        queueNames = queues.map { it.name }.toTypedArray()
        spinnerTexts.clear()
        spinnerTexts.addAll(queues.map { "${it.name} : ${it.size()}" })
        var curIndex = queues.indexOf(curQueue)

        (activity as MainActivity).setupToolbarToggle(toolbar, displayUpArrow)
        toolbar.inflateMenu(R.menu.queue)
        refreshMenuItems()

        spinnerView = ComposeView(requireContext()).apply {
            setContent {
                CustomTheme(requireContext()) {
                    Spinner(items = spinnerTexts, selectedIndex = curIndex) { index: Int ->
                        Logd(TAG, "Queue selected: $queues[index].name")
                        val prevQueueSize = curQueue.size()
                        curQueue = upsertBlk(queues[index]) { it.update() }
                        toolbar.menu?.findItem(R.id.rename_queue)?.setVisible(curQueue.name != "Default")
                        loadCurQueue(true)
                        playbackService?.notifyCurQueueItemsChanged(max(prevQueueSize, curQueue.size()))
                    }
                }
            }
        }
        toolbar.addView(spinnerView)

        swipeActions = SwipeActions(this, TAG)
        swipeActions.setFilter(EpisodeFilter(EpisodeFilter.States.queued.name))
        swipeActionsBin = SwipeActions(this, "$TAG.Bin")
        swipeActionsBin.setFilter(EpisodeFilter(EpisodeFilter.States.queued.name))

        binding.mainView.setContent {
            CustomTheme(requireContext()) {
                if (showBin) {
                    Column {
                        InforBar(infoBarText, leftAction = leftActionStateBin, rightAction = rightActionStateBin, actionConfig = { swipeActionsBin.showDialog() })
                        val leftCB = { episode: Episode ->
                            if (leftActionStateBin.value is NoActionSwipeAction) swipeActionsBin.showDialog()
                            else leftActionStateBin.value.performAction(episode, this@QueuesFragment, swipeActionsBin.filter ?: EpisodeFilter())
                        }
                        val rightCB = { episode: Episode ->
                            if (rightActionStateBin.value is NoActionSwipeAction) swipeActionsBin.showDialog()
                            else rightActionStateBin.value.performAction(episode, this@QueuesFragment, swipeActionsBin.filter ?: EpisodeFilter())
                        }
                        EpisodeLazyColumn(activity as MainActivity, vms = vms, leftSwipeCB = { leftCB(it) }, rightSwipeCB = { rightCB(it) })
                    }
                } else {
                    if (showFeeds) FeedsGrid()
                    else {
                        Column {
                            InforBar(infoBarText, leftAction = leftActionState, rightAction = rightActionState, actionConfig = { swipeActions.showDialog() })
                            val leftCB = { episode: Episode ->
                                if (leftActionState.value is NoActionSwipeAction) swipeActions.showDialog()
                                else leftActionState.value.performAction(episode, this@QueuesFragment, swipeActions.filter ?: EpisodeFilter())
                            }
                            val rightCB = { episode: Episode ->
                                if (rightActionState.value is NoActionSwipeAction) swipeActions.showDialog()
                                else rightActionState.value.performAction(episode, this@QueuesFragment, swipeActions.filter ?: EpisodeFilter())
                            }
                            EpisodeLazyColumn(activity as MainActivity, vms = vms,
                                isDraggable = dragDropEnabled, dragCB = { iFrom, iTo -> runOnIOScope { moveInQueueSync(iFrom, iTo, true) } },
                                leftSwipeCB = { leftCB(it) }, rightSwipeCB = { rightCB(it) })
                        }
                    }
                }
            }
        }

        lifecycle.addObserver(swipeActions)
        refreshSwipeTelltale()
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

     @OptIn(ExperimentalFoundationApi::class)
     @Composable
     fun FeedsGrid() {
         val context = LocalContext.current
         val lazyGridState = rememberLazyGridState()
         LazyVerticalGrid(state = lazyGridState, columns = GridCells.Adaptive(80.dp),
             verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
             contentPadding = PaddingValues(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 16.dp)) {
             items(feedsAssociated.size, key = {index -> feedsAssociated[index].id}) { index ->
                 val feed by remember { mutableStateOf(feedsAssociated[index]) }
                 ConstraintLayout {
                     val (coverImage, episodeCount, rating, error) = createRefs()
                     val imgLoc = remember(feed) { feed.imageUrl }
                     AsyncImage(model = ImageRequest.Builder(context).data(imgLoc)
                         .memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(),
                         contentDescription = "coverImage",
                         modifier = Modifier.height(100.dp).aspectRatio(1f)
                             .constrainAs(coverImage) {
                                 top.linkTo(parent.top)
                                 bottom.linkTo(parent.bottom)
                                 start.linkTo(parent.start)
                             }.combinedClickable(onClick = {
                                 Logd(SubscriptionsFragment.TAG, "clicked: ${feed.title}")
                                 (activity as MainActivity).loadChildFragment(FeedEpisodesFragment.newInstance(feed.id))
                             }, onLongClick = {
                                 Logd(SubscriptionsFragment.TAG, "long clicked: ${feed.title}")
//                                val inflater: MenuInflater = (activity as MainActivity).menuInflater
//                                inflater.inflate(R.menu.feed_context, contextMenu)
//                                contextMenu.setHeaderTitle(feed.title)
                             })
                     )
                     Text(NumberFormat.getInstance().format(feed.episodes.size.toLong()), color = Color.Green,
                         modifier = Modifier.background(Color.Gray).constrainAs(episodeCount) {
                             end.linkTo(parent.end)
                             top.linkTo(coverImage.top)
                         })
                     if (feed.rating != Rating.UNRATED.code)
                         Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(feed.rating).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                             modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).constrainAs(rating) {
                                 start.linkTo(parent.start)
                                 centerVerticallyTo(coverImage)
                             })
                 }
             }
         }
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

    private fun onQueueEvent(event: FlowEvent.QueueEvent) {
        Logd(TAG, "onQueueEvent() called with ${event.action.name}")
        if (showBin) return
        when (event.action) {
            FlowEvent.QueueEvent.Action.ADDED -> {
                if (event.episodes.isNotEmpty() && !curQueue.contains(event.episodes[0])) {
                    queueItems.addAll(event.episodes)
                    for (e in event.episodes) vms.add(EpisodeVM(e))
                }
            }
            FlowEvent.QueueEvent.Action.SET_QUEUE, FlowEvent.QueueEvent.Action.SORTED -> {
                queueItems.clear()
                queueItems.addAll(event.episodes)
                vms.clear()
                for (e in event.episodes) vms.add(EpisodeVM(e))
            }
            FlowEvent.QueueEvent.Action.REMOVED, FlowEvent.QueueEvent.Action.IRREVERSIBLE_REMOVED -> {
                if (event.episodes.isNotEmpty()) {
                    for (e in event.episodes) {
                        val pos: Int = EpisodeUtil.indexOfItemWithId(queueItems, e.id)
                        if (pos >= 0) {
                            Logd(TAG, "removing episode $pos ${queueItems[pos].title} $e")
//                            queueItems[pos].stopMonitoring.value = true
                            queueItems.removeAt(pos)
                            vms[pos].stopMonitoring()
                            vms.removeAt(pos)
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
                vms.clear()
            }
            FlowEvent.QueueEvent.Action.MOVED, FlowEvent.QueueEvent.Action.DELETED_MEDIA -> return
        }
        queues = realm.query(PlayQueue::class).find()
        queueNames = queues.map { it.name }.toTypedArray()
        spinnerTexts.clear()
        spinnerTexts.addAll(queues.map { "${it.name} : ${it.size()}" })
        refreshMenuItems()
        refreshInfoBar()
    }

    private fun onPlayEvent(event: FlowEvent.PlayEvent) {
        val pos: Int = EpisodeUtil.indexOfItemWithId(queueItems, event.episode.id)
        Logd(TAG, "onPlayEvent action: ${event.action} pos: $pos ${event.episode.title}")
        if (pos >= 0) vms[pos].isPlayingState = event.isPlaying()
    }

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
//        Logd(TAG, "onEventMainThread() called with ${event.TAG}")
        if (loadItemsRunning) return
        for (url in event.urls) {
//            if (!event.isCompleted(url)) continue
            val pos: Int = EpisodeUtil.indexOfItemWithDownloadUrl(queueItems.toList(), url)
            if (pos >= 0) vms[pos].downloadState = event.map[url]?.state ?: DownloadStatus.State.UNKNOWN.ordinal

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
        if (event.episode == null && !showBin) loadCurQueue(false)
        refreshMenuItems()
    }

    private fun onFeedPrefsChanged(event: FlowEvent.FeedPrefsChangeEvent) {
        Logd(TAG,"speedPresetChanged called")
        for (item in queueItems) {
            if (item.feed?.id == event.feed.id) item.feed = null
        }
    }

    private fun refreshSwipeTelltale() {
        if (showBin) {
            leftActionStateBin.value = swipeActionsBin.actions.left[0]
            rightActionStateBin.value = swipeActionsBin.actions.right[0]
        } else {
            leftActionState.value = swipeActions.actions.left[0]
            rightActionState.value = swipeActions.actions.right[0]
        }
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
        vms.clear()
        toolbar.setOnMenuItemClickListener(null)
        toolbar.setOnLongClickListener(null)
        super.onDestroyView()
    }

    private fun refreshMenuItems() {
        if (showBin) {
            toolbar.menu?.findItem(R.id.queue_sort)?.setVisible(false)
            toolbar.menu?.findItem(R.id.rename_queue)?.setVisible(false)
            toolbar.menu?.findItem(R.id.associated_feed)?.setVisible(false)
            toolbar.menu?.findItem(R.id.add_queue)?.setVisible(false)
            toolbar.menu?.findItem(R.id.queue_lock)?.setVisible(false)
            toolbar.menu?.findItem(R.id.action_search)?.setVisible(false)
        } else {
            toolbar.menu?.findItem(R.id.action_search)?.setVisible(true)
            toolbar.menu?.findItem(R.id.queue_sort)?.setVisible(true)
            toolbar.menu?.findItem(R.id.associated_feed)?.setVisible(true)
            toolbar.menu?.findItem(R.id.queue_lock)?.setChecked(isQueueLocked)
            toolbar.menu?.findItem(R.id.queue_lock)?.setVisible(!isQueueKeepSorted)
            toolbar.menu?.findItem(R.id.rename_queue)?.setVisible(curQueue.name != "Default")
            toolbar.menu?.findItem(R.id.add_queue)?.setVisible(queueNames.size < 9)
        }
    }

     override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        when (itemId) {
            R.id.show_bin -> {
                showBin = !showBin
                if (showBin) {
                    toolbar.removeView(spinnerView)
                    toolbar.title = curQueue.name + " Bin"
                } else {
                    toolbar.title = ""
                    toolbar.addView(spinnerView)
                }
                refreshMenuItems()
                refreshSwipeTelltale()
                item.setIcon(if (showBin) R.drawable.playlist_play else R.drawable.ic_history)
                loadCurQueue(false)
            }
            R.id.associated_feed -> showFeeds = !showFeeds
            R.id.queue_lock -> toggleQueueLock()
            R.id.queue_sort -> QueueSortDialog().show(childFragmentManager.beginTransaction(), "SortDialog")
            R.id.rename_queue -> renameQueue()
            R.id.add_queue -> addQueue()
            R.id.clear_queue -> {
                // make sure the user really wants to clear the queue
                val conDialog: ConfirmationDialog = object : ConfirmationDialog(requireContext(), R.string.clear_queue_label, R.string.clear_queue_confirmation_msg) {
                     override fun onConfirmButtonPressed(dialog: DialogInterface) {
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
                    RenameQueueDialog(showDialog = showDialog.value, onDismiss = {
                        showDialog.value = false
                        (view as? ViewGroup)?.removeView(this@apply)
                    })
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
                    AddQueueDialog(showDialog = showDialog.value, onDismiss = {
                        showDialog.value = false
                        (view as? ViewGroup)?.removeView(this@apply)
                    })
                }
            }
        }
        (view as? ViewGroup)?.addView(composeView)
    }

    @Composable
    fun RenameQueueDialog(showDialog: Boolean, onDismiss: () -> Unit) {
        if (showDialog) {
            Dialog(onDismissRequest = onDismiss) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.Yellow)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        var newName by remember { mutableStateOf(curQueue.name) }
                        TextField(value = newName, onValueChange = { newName = it }, label = { Text("Rename (Unique name only)") })
                        Button(onClick = {
                            if (newName.isNotEmpty() && curQueue.name != newName && queueNames.indexOf(newName) < 0) {
                                val oldName = curQueue.name
                                curQueue = upsertBlk(curQueue) { it.name = newName }
                                val index_ = queueNames.indexOf(oldName)
                                queueNames[index_] = newName
                                spinnerTexts[index_] = newName + " : " + curQueue.episodeIds.size
                                onDismiss()
                            }
                        }) { Text("Confirm") }
                    }
                }
            }
        }
    }

    @Composable
    fun AddQueueDialog(showDialog: Boolean, onDismiss: () -> Unit) {
        if (showDialog) {
            Dialog(onDismissRequest = onDismiss) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.Yellow)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        var newName by remember { mutableStateOf("") }
                        TextField(value = newName, onValueChange = { newName = it }, label = { Text("Add queue (Unique name only)") })
                        Button(onClick = {
                            if (newName.isNotEmpty() && queueNames.indexOf(newName) < 0) {
                                val newQueue = PlayQueue()
                                newQueue.id = queueNames.size.toLong()
                                newQueue.name = newName
                                upsertBlk(newQueue) {}
                                queues = realm.query(PlayQueue::class).find()
                                queueNames = queues.map { it.name }.toTypedArray()
                                spinnerTexts.clear()
                                spinnerTexts.addAll(queues.map { "${it.name} : ${it.episodeIds.size}" })
                                onDismiss()
                            }
                        }) { Text("Confirm") }
                    }
                }
            }
        }
    }

     private fun toggleQueueLock() {
//        val isLocked: Boolean = isQueueLocked
        if (isQueueLocked) setQueueLock(false)
        else {
            val shouldShowLockWarning: Boolean = prefs?.getBoolean(PREF_SHOW_LOCK_WARNING, true) ?: true
            if (!shouldShowLockWarning) setQueueLock(true)
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
                    setQueueLock(true)
                }
                builder.setNegativeButton(R.string.cancel_label, null)
                builder.show()
            }
        }
    }

     private fun setQueueLock(locked: Boolean) {
        isQueueLocked = locked
        appPrefs.edit().putBoolean(UserPreferences.Prefs.prefQueueLocked.name, locked).apply()
        dragDropEnabled = !(isQueueKeepSorted || isQueueLocked)
        refreshMenuItems()
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
            Logd(TAG, "loadCurQueue() called ${curQueue.name}")
            while (curQueue.name.isEmpty()) runBlocking { delay(100) }
//            if (queueItems.isNotEmpty()) emptyViewHandler.hide()
            feedsAssociated = realm.query(Feed::class).query("preferences.queueId == ${curQueue.id}").find()
            queueItems.clear()
            vms.clear()
            if (showBin) queueItems.addAll(realm.query(Episode::class, "id IN $0", curQueue.idsBinList)
                    .find().sortedByDescending { curQueue.idsBinList.indexOf(it.id) })
            else {
                curQueue.episodes.clear()
                queueItems.addAll(curQueue.episodes)
            }
            for (e in queueItems) vms.add(EpisodeVM(e))
            Logd(TAG, "loadCurQueue() curQueue.episodes: ${curQueue.episodes.size}")
            queues = realm.query(PlayQueue::class).find()
            spinnerTexts.clear()
            spinnerTexts.addAll(queues.map { "${it.name} : ${it.size()}" })
            refreshInfoBar()
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
         override fun onSelectionChanged() {
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
