package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.getCurrentPlaybackSpeed
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.mediaBrowser
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.storage.database.Episodes
import ac.mdiq.podcini.storage.database.Queues.clearQueue
import ac.mdiq.podcini.storage.database.Queues.isQueueKeepSorted
import ac.mdiq.podcini.storage.database.Queues.moveInQueueSync
import ac.mdiq.podcini.storage.database.Queues.queueKeepSortedOrder
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.EpisodeSortOrder.Companion.getPermutor
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.ui.actions.SwipeAction
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.actions.SwipeActions.Companion.SwipeActionsSettingDialog
import ac.mdiq.podcini.ui.actions.SwipeActions.NoActionSwipeAction
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.*
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

class QueuesFragment : Fragment() {
    val prefs: SharedPreferences by lazy { requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private lateinit var swipeActions: SwipeActions
    private lateinit var swipeActionsBin: SwipeActions
    private var leftActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
    private var rightActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
    private var leftActionStateBin = mutableStateOf<SwipeAction>(NoActionSwipeAction())
    private var rightActionStateBin = mutableStateOf<SwipeAction>(NoActionSwipeAction())

    private var infoTextUpdate = ""
    private var infoText = ""
    private var infoBarText = mutableStateOf("")

    private var showSwipeActionsDialog by mutableStateOf(false)

    private var isQueueLocked by mutableStateOf(getPref(AppPreferences.AppPrefs.prefQueueLocked, true))

    private var queueNames = mutableStateListOf<String>()
    private val spinnerTexts = mutableStateListOf<String>()
    private var curIndex by mutableIntStateOf(0)
    private lateinit var queues: List<PlayQueue>

    private var displayUpArrow = false
    private val queueItems = mutableListOf<Episode>()
    private val vms = mutableStateListOf<EpisodeVM>()
    private var feedsAssociated = listOf<Feed>()

    private var showBin by mutableStateOf(false)
    private var showFeeds by mutableStateOf(false)
    private var dragDropEnabled by mutableStateOf(!(isQueueKeepSorted || isQueueLocked))
    var showSortDialog by mutableStateOf(false)
    var sortOrder by mutableStateOf(EpisodeSortOrder.DATE_NEW_OLD)

    private val showClearQueueDialog = mutableStateOf(false)
    private var shouldShowLockWarningDiwload by mutableStateOf(false)
    private val showRenameQueueDialog = mutableStateOf(false)
    private val showAddQueueDialog = mutableStateOf(false)

    private lateinit var browserFuture: ListenableFuture<MediaBrowser>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        Logd(TAG, "fragment onCreateView")

        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
        if (isQueueKeepSorted) sortOrder = queueKeepSortedOrder ?: EpisodeSortOrder.DATE_NEW_OLD

        queues = realm.query(PlayQueue::class).find()
        queueNames = queues.map { it.name }.toMutableStateList()
        spinnerTexts.clear()
        spinnerTexts.addAll(queues.map { "${it.name} : ${it.size()}" })
//        curIndex = queues.indexOf(curQueue)

        swipeActions = SwipeActions(this, TAG)
        leftActionState.value = swipeActions.actions.left[0]
        rightActionState.value = swipeActions.actions.right[0]
        swipeActionsBin = SwipeActions(this, "$TAG.Bin")
        leftActionStateBin.value = swipeActions.actions.left[0]
        rightActionStateBin.value = swipeActions.actions.right[0]
        lifecycle.addObserver(swipeActions)
        lifecycle.addObserver(swipeActionsBin)

        val composeView = ComposeView(requireContext()).apply {
            setContent {
                CustomTheme(requireContext()) {
                    if (showSwipeActionsDialog) SwipeActionsSettingDialog(if (showBin) swipeActionsBin else swipeActions, onDismissRequest = { showSwipeActionsDialog = false }) { actions ->
                        swipeActions.actions = actions
                        refreshSwipeTelltale()
                    }
                    ComfirmDialog(titleRes = R.string.clear_queue_label, message = stringResource(R.string.clear_queue_confirmation_msg), showDialog = showClearQueueDialog) { clearQueue() }
                    if (shouldShowLockWarningDiwload) ShowLockWarning { shouldShowLockWarningDiwload = false }
                    RenameQueueDialog(showDialog = showRenameQueueDialog.value, onDismiss = { showRenameQueueDialog.value = false })
                    AddQueueDialog(showDialog = showAddQueueDialog.value, onDismiss = { showAddQueueDialog.value = false })
                    swipeActions.ActionOptionsDialog()
                    swipeActionsBin.ActionOptionsDialog()

                    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
                        if (showBin) {
                            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                                InforBar(infoBarText, leftAction = leftActionStateBin, rightAction = rightActionStateBin, actionConfig = { showSwipeActionsDialog = true })
                                val leftCB = { episode: Episode ->
                                    if (leftActionStateBin.value is NoActionSwipeAction) showSwipeActionsDialog = true
                                    else leftActionStateBin.value.performAction(episode)
                                }
                                val rightCB = { episode: Episode ->
                                    if (rightActionStateBin.value is NoActionSwipeAction) showSwipeActionsDialog = true
                                    else rightActionStateBin.value.performAction(episode)
                                }
                                EpisodeLazyColumn(activity as MainActivity, vms = vms, leftSwipeCB = { leftCB(it) }, rightSwipeCB = { rightCB(it) })
                            }
                        } else {
                            if (showFeeds) Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) { FeedsGrid() }
                            else {
                                Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                                    if (showSortDialog) EpisodeSortDialog(initOrder = sortOrder, showKeepSorted = true, onDismissRequest = { showSortDialog = false }) { sortOrder, keep ->
                                        if (sortOrder != EpisodeSortOrder.RANDOM && sortOrder != EpisodeSortOrder.RANDOM1) isQueueKeepSorted = keep
                                        queueKeepSortedOrder = sortOrder
                                        reorderQueue(sortOrder, true)
                                    }

                                    InforBar(infoBarText, leftAction = leftActionState, rightAction = rightActionState, actionConfig = { showSwipeActionsDialog = true })
                                    val leftCB = { episode: Episode ->
                                        if (leftActionState.value is NoActionSwipeAction) showSwipeActionsDialog = true
                                        else leftActionState.value.performAction(episode)
                                    }
                                    val rightCB = { episode: Episode ->
                                        if (rightActionState.value is NoActionSwipeAction) showSwipeActionsDialog = true
                                        else rightActionState.value.performAction(episode)
                                    }
                                    EpisodeLazyColumn(activity as MainActivity, vms = vms,
                                        isDraggable = dragDropEnabled, dragCB = { iFrom, iTo -> runOnIOScope { moveInQueueSync(iFrom, iTo, true) } },
                                        leftSwipeCB = { leftCB(it) }, rightSwipeCB = { rightCB(it) })
                                }
                            }
                        }
                    }
                }
            }
        }

        lifecycle.addObserver(swipeActions)
        refreshSwipeTelltale()
        return composeView
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
                    val (coverImage, episodeCount, rating, _) = createRefs()
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
//                    is FlowEvent.PlayerSettingsEvent -> onPlayerSettingsEvent(event)
                    is FlowEvent.FeedChangeEvent -> onFeedPrefsChanged(event)
                    is FlowEvent.EpisodePlayedEvent -> onEpisodePlayedEvent(event)
//                    is FlowEvent.SwipeActionsChangedEvent -> refreshSwipeTelltale()
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
                Logd(TAG, "Received key event: $event, Ignored!")
//                onKeyUp(event)
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
                    for (e in event.episodes) vms.add(EpisodeVM(e, TAG))
                }
            }
            FlowEvent.QueueEvent.Action.SET_QUEUE, FlowEvent.QueueEvent.Action.SORTED -> {
                queueItems.clear()
                queueItems.addAll(event.episodes)
                stopMonitor(vms)
                vms.clear()
                for (e in event.episodes) vms.add(EpisodeVM(e, TAG))
            }
            FlowEvent.QueueEvent.Action.REMOVED, FlowEvent.QueueEvent.Action.IRREVERSIBLE_REMOVED -> {
                if (event.episodes.isNotEmpty()) {
                    for (e in event.episodes) {
                        val pos: Int = Episodes.indexOfItemWithId(queueItems, e.id)
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
                stopMonitor(vms)
                vms.clear()
            }
            FlowEvent.QueueEvent.Action.MOVED, FlowEvent.QueueEvent.Action.DELETED_MEDIA -> return
        }
        queues = realm.query(PlayQueue::class).find()
        queueNames = queues.map { it.name }.toMutableStateList()
        curIndex = queues.indexOfFirst { it.id == curQueue.id }
        spinnerTexts.clear()
        spinnerTexts.addAll(queues.map { "${it.name} : ${it.size()}" })
        refreshInfoBar()
    }

    private fun onPlayEvent(event: FlowEvent.PlayEvent) {
        val pos: Int = Episodes.indexOfItemWithId(queueItems, event.episode.id)
        Logd(TAG, "onPlayEvent action: ${event.action} pos: $pos ${event.episode.title}")
        if (pos >= 0) vms[pos].isPlayingState = event.isPlaying()
    }

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
//        Logd(TAG, "onEventMainThread() called with ${event.TAG}")
        if (loadItemsRunning) return
        for (url in event.urls) {
//            if (!event.isCompleted(url)) continue
            val pos: Int = Episodes.indexOfItemWithDownloadUrl(queueItems.toList(), url)
            if (pos >= 0) vms[pos].downloadState = event.map[url]?.state ?: DownloadStatus.State.UNKNOWN.ordinal

        }
    }

//    private fun onPlayerSettingsEvent(event: FlowEvent.PlayerSettingsEvent) {
//        if (showBin) return
//        //        Logd(TAG, "onPlayerStatusChanged() called with event = [$event]")
//        loadCurQueue(false)
//    }

    private fun onEpisodePlayedEvent(event: FlowEvent.EpisodePlayedEvent) {
        // Sent when playback position is reset
        Logd(TAG, "onUnreadItemsChanged() called with event = [$event]")
        if (event.episode == null && !showBin) loadCurQueue(false)
    }

    private fun onFeedPrefsChanged(event: FlowEvent.FeedChangeEvent) {
        Logd(TAG,"speedPresetChanged called")
        for (item in queueItems) if (item.feed?.id == event.feed.id) item.feed = null
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

//    @SuppressLint("RestrictedApi")
//    private fun onKeyUp(event: KeyEvent) {
//        if (!isAdded || !isVisible || !isMenuVisible) return
//        when (event.keyCode) {
////            KeyEvent.KEYCODE_T -> recyclerView.smoothScrollToPosition(0)
////            KeyEvent.KEYCODE_B -> recyclerView.smoothScrollToPosition(adapter!!.itemCount - 1)
//            else -> {}
//        }
//    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        queueItems.clear()
        stopMonitor(vms)
        vms.clear()
        super.onDestroyView()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        var showSpinner by remember { mutableStateOf(!showBin) }
        var title by remember { mutableStateOf(if (showBin) curQueue.name + " Bin" else "") }
        var showRename by remember { mutableStateOf(curQueue.name != "Default") }
        TopAppBar(title = {
            if (showSpinner) SpinnerExternalSet(items = spinnerTexts, selectedIndex = curIndex) { index: Int ->
                Logd(TAG, "Queue selected: $queues[index].name")
                val prevQueueSize = curQueue.size()
                curQueue = upsertBlk(queues[index]) { it.update() }
                showRename = curQueue.name != "Default"
                loadCurQueue(true)
                playbackService?.notifyCurQueueItemsChanged(max(prevQueueSize, curQueue.size()))
            } else Text(title) },
            navigationIcon = if (displayUpArrow) {
                { IconButton(onClick = { parentFragmentManager.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            } else {
                { IconButton(onClick = { (activity as? MainActivity)?.openDrawer() }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_playlist_play), contentDescription = "Open Drawer") } }
            },
            actions = {
                val binIconRes by remember(showBin) { derivedStateOf { if (showBin) R.drawable.playlist_play else R.drawable.ic_history } }
//                var binIconRes by remember { mutableIntStateOf( if (showBin) R.drawable.playlist_play else R.drawable.ic_history) }
//                var feedsIconRes = if (showFeeds) R.drawable.playlist_play else R.drawable.baseline_dynamic_feed_24)
                val feedsIconRes by remember(showFeeds) { derivedStateOf { if (showFeeds) R.drawable.playlist_play else R.drawable.baseline_dynamic_feed_24 } }
                IconButton(onClick = {
                    showBin = !showBin
                    showSpinner = !showBin
                    title = if (showBin) curQueue.name + " Bin" else ""
                    refreshSwipeTelltale()
//                    binIconRes = if (showBin) R.drawable.playlist_play else R.drawable.ic_history
                    loadCurQueue(false)
                }) { Icon(imageVector = ImageVector.vectorResource(binIconRes), contentDescription = "bin") }
                IconButton(onClick = { showFeeds = !showFeeds }) { Icon(imageVector = ImageVector.vectorResource(feedsIconRes), contentDescription = "feeds") }
                if (!showBin) IconButton(onClick = { (activity as MainActivity).loadChildFragment(SearchFragment.newInstance())
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.clear_bin_label)) }, onClick = {
                        curQueue = upsertBlk(curQueue) {
                            it.idsBinList.clear()
                            it.update()
                        }
                        if (showBin) loadCurQueue(false)
                        expanded = false
                    })
                    if (!showBin) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.sort)) }, onClick = {
                            showSortDialog = true
                            expanded = false
                        })
                        if (showRename) DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = {
                            showRenameQueueDialog.value = true
                            expanded = false
                        })
                        if (queueNames.size < 9) DropdownMenuItem(text = { Text(stringResource(R.string.add_queue)) }, onClick = {
                            showAddQueueDialog.value = true
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.clear_queue_label)) }, onClick = {
                            showClearQueueDialog.value = true
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.refresh_label)) }, onClick = {
                            FeedUpdateManager.runOnceOrAsk(requireContext())
                            expanded = false
                        })
                        if (!isQueueKeepSorted) DropdownMenuItem(text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.lock_queue))
                                Checkbox(checked = isQueueLocked, onCheckedChange = {  })
                            }
                        }, onClick = {
                            toggleQueueLock()
                            expanded = false
                        })
                    }
                }
            }
        )
    }

    @Composable
    fun RenameQueueDialog(showDialog: Boolean, onDismiss: () -> Unit) {
        if (showDialog) {
            Dialog(onDismissRequest = onDismiss) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
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
                        }) { Text(stringResource(R.string.confirm_label)) }
                    }
                }
            }
        }
    }

    @Composable
    fun AddQueueDialog(showDialog: Boolean, onDismiss: () -> Unit) {
        if (showDialog) {
            Dialog(onDismissRequest = onDismiss) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
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
                                queueNames = queues.map { it.name }.toMutableStateList()
                                curIndex = queues.indexOfFirst { it.id == curQueue.id }
                                spinnerTexts.clear()
                                spinnerTexts.addAll(queues.map { "${it.name} : ${it.episodeIds.size}" })
                                onDismiss()
                            }
                        }) { Text(stringResource(R.string.confirm_label)) }
                    }
                }
            }
        }
    }

     private fun toggleQueueLock() {
        if (isQueueLocked) setQueueLock(false)
        else {
            val shouldShowLockWarning = mutableStateOf(prefs.getBoolean(PREF_SHOW_LOCK_WARNING, true))
            if (!shouldShowLockWarning.value) setQueueLock(true)
            else shouldShowLockWarningDiwload = true
        }
    }

    @Composable
    private fun ShowLockWarning(onDismiss: () -> Unit) {
        var dontAskAgain by remember { mutableStateOf(false) }
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = onDismiss, title = { Text(stringResource(R.string.lock_queue)) },
            text = {
                Column {
                    Text(stringResource(R.string.queue_lock_warning))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = dontAskAgain, onCheckedChange = { dontAskAgain = it })
                        Text(stringResource(R.string.checkbox_do_not_show_again))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean(PREF_SHOW_LOCK_WARNING, !dontAskAgain).apply()
                    onDismiss()
                }) { Text(stringResource(R.string.lock_queue)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    private fun setQueueLock(locked: Boolean) {
        isQueueLocked = locked
        putPref(AppPreferences.AppPrefs.prefQueueLocked, locked)
        dragDropEnabled = !(isQueueKeepSorted || isQueueLocked)
        if (queueItems.isEmpty()) {
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
                if (getPref(AppPrefs.prefPlaybackTimeRespectsSpeed, false)) playbackSpeed = getCurrentPlaybackSpeed(item)
                val itemTimeLeft: Long = (item.duration - item.position).toLong()
                timeLeft = (timeLeft + itemTimeLeft / playbackSpeed).toLong()
            }
            infoText += " • "
            infoText += DurationConverter.getDurationStringLocalized(timeLeft)
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
            feedsAssociated = realm.query(Feed::class).query("queueId == ${curQueue.id}").find()
            queueItems.clear()
            stopMonitor(vms)
            vms.clear()
            if (showBin) queueItems.addAll(realm.query(Episode::class, "id IN $0", curQueue.idsBinList)
                    .find().sortedByDescending { curQueue.idsBinList.indexOf(it.id) })
            else {
                curQueue.episodes.clear()
                queueItems.addAll(curQueue.episodes)
            }
            for (e in queueItems) vms.add(EpisodeVM(e, TAG))
            Logd(TAG, "loadCurQueue() curQueue.episodes: ${curQueue.episodes.size}")
            queues = realm.query(PlayQueue::class).find()
            curIndex = queues.indexOfFirst { it.id == curQueue.id }
            spinnerTexts.clear()
            spinnerTexts.addAll(queues.map { "${it.name} : ${it.size()}" })
            refreshInfoBar()
            loadItemsRunning = false
        }
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

    companion object {
        val TAG = QueuesFragment::class.simpleName ?: "Anonymous"

        private const val KEY_UP_ARROW = "up_arrow"
        private const val PREFS = "QueueFragment"
        private const val PREF_SHOW_LOCK_WARNING = "show_lock_warning"
    }
}
