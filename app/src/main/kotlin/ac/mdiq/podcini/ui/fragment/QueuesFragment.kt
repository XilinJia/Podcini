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

    class QueuesVM {
        internal lateinit var swipeActions: SwipeActions
        internal lateinit var swipeActionsBin: SwipeActions
        internal var leftActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
        internal var rightActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
        internal var leftActionStateBin = mutableStateOf<SwipeAction>(NoActionSwipeAction())
        internal var rightActionStateBin = mutableStateOf<SwipeAction>(NoActionSwipeAction())

        internal var infoTextUpdate = ""
        internal var infoText = ""
        internal var infoBarText = mutableStateOf("")

        internal var showSwipeActionsDialog by mutableStateOf(false)

        internal var isQueueLocked by mutableStateOf(getPref(AppPrefs.prefQueueLocked, true))

        internal var queueNames = mutableStateListOf<String>()
        internal val spinnerTexts = mutableStateListOf<String>()
        internal var curIndex by mutableIntStateOf(0)
        internal lateinit var queues: List<PlayQueue>

        internal var displayUpArrow = false
        internal val queueItems = mutableListOf<Episode>()
        internal val vms = mutableStateListOf<EpisodeVM>()
        internal var feedsAssociated = listOf<Feed>()

        internal var showBin by mutableStateOf(false)
        internal var showFeeds by mutableStateOf(false)
        internal var dragDropEnabled by mutableStateOf(!(isQueueKeepSorted || isQueueLocked))
        var showSortDialog by mutableStateOf(false)
        var sortOrder by mutableStateOf(EpisodeSortOrder.DATE_NEW_OLD)

        internal val showClearQueueDialog = mutableStateOf(false)
        internal var shouldShowLockWarningDiwload by mutableStateOf(false)
        internal val showRenameQueueDialog = mutableStateOf(false)
        internal val showAddQueueDialog = mutableStateOf(false)

        internal lateinit var browserFuture: ListenableFuture<MediaBrowser>
    }

    private val vm = QueuesVM()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        Logd(TAG, "fragment onCreateView")

        vm.displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) vm.displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
        if (isQueueKeepSorted) vm.sortOrder = queueKeepSortedOrder ?: EpisodeSortOrder.DATE_NEW_OLD

        vm.queues = realm.query(PlayQueue::class).find()
        vm.queueNames = vm.queues.map { it.name }.toMutableStateList()
        vm.spinnerTexts.clear()
        vm.spinnerTexts.addAll(vm.queues.map { "${it.name} : ${it.size()}" })
//        curIndex = queues.indexOf(curQueue)

        vm.swipeActions = SwipeActions(this, TAG)
        vm.leftActionState.value = vm.swipeActions.actions.left[0]
        vm.rightActionState.value = vm.swipeActions.actions.right[0]
        vm.swipeActionsBin = SwipeActions(this, "$TAG.Bin")
        vm.leftActionStateBin.value = vm.swipeActions.actions.left[0]
        vm.rightActionStateBin.value = vm.swipeActions.actions.right[0]
        lifecycle.addObserver(vm.swipeActions)
        lifecycle.addObserver(vm.swipeActionsBin)

        val composeView = ComposeView(requireContext()).apply {
            setContent {
                CustomTheme(requireContext()) {
                    if (vm.showSwipeActionsDialog) SwipeActionsSettingDialog(if (vm.showBin) vm.swipeActionsBin else vm.swipeActions, onDismissRequest = { vm.showSwipeActionsDialog = false }) { actions ->
                        vm.swipeActions.actions = actions
                        refreshSwipeTelltale()
                    }
                    ComfirmDialog(titleRes = R.string.clear_queue_label, message = stringResource(R.string.clear_queue_confirmation_msg), showDialog = vm.showClearQueueDialog) { clearQueue() }
                    if (vm.shouldShowLockWarningDiwload) ShowLockWarning { vm.shouldShowLockWarningDiwload = false }
                    RenameQueueDialog(showDialog = vm.showRenameQueueDialog.value, onDismiss = { vm.showRenameQueueDialog.value = false })
                    AddQueueDialog(showDialog = vm.showAddQueueDialog.value, onDismiss = { vm.showAddQueueDialog.value = false })
                    vm.swipeActions.ActionOptionsDialog()
                    vm.swipeActionsBin.ActionOptionsDialog()

                    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
                        if (vm.showBin) {
                            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                                InforBar(vm.infoBarText, leftAction = vm.leftActionStateBin, rightAction = vm.rightActionStateBin, actionConfig = { vm.showSwipeActionsDialog = true })
                                val leftCB = { episode: Episode ->
                                    if (vm.leftActionStateBin.value is NoActionSwipeAction) vm.showSwipeActionsDialog = true
                                    else vm.leftActionStateBin.value.performAction(episode)
                                }
                                val rightCB = { episode: Episode ->
                                    if (vm.rightActionStateBin.value is NoActionSwipeAction) vm.showSwipeActionsDialog = true
                                    else vm.rightActionStateBin.value.performAction(episode)
                                }
                                EpisodeLazyColumn(activity as MainActivity, vms = vm.vms, leftSwipeCB = { leftCB(it) }, rightSwipeCB = { rightCB(it) })
                            }
                        } else {
                            if (vm.showFeeds) Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) { FeedsGrid() }
                            else {
                                Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                                    if (vm.showSortDialog) EpisodeSortDialog(initOrder = vm.sortOrder, showKeepSorted = true, onDismissRequest = { vm.showSortDialog = false }) { sortOrder, keep ->
                                        if (sortOrder != EpisodeSortOrder.RANDOM && sortOrder != EpisodeSortOrder.RANDOM1) isQueueKeepSorted = keep
                                        queueKeepSortedOrder = sortOrder
                                        reorderQueue(sortOrder, true)
                                    }

                                    InforBar(vm.infoBarText, leftAction = vm.leftActionState, rightAction = vm.rightActionState, actionConfig = { vm.showSwipeActionsDialog = true })
                                    val leftCB = { episode: Episode ->
                                        if (vm.leftActionState.value is NoActionSwipeAction) vm.showSwipeActionsDialog = true
                                        else vm.leftActionState.value.performAction(episode)
                                    }
                                    val rightCB = { episode: Episode ->
                                        if (vm.rightActionState.value is NoActionSwipeAction) vm.showSwipeActionsDialog = true
                                        else vm.rightActionState.value.performAction(episode)
                                    }
                                    EpisodeLazyColumn(activity as MainActivity, vms = vm.vms,
                                        isDraggable = vm.dragDropEnabled, dragCB = { iFrom, iTo -> runOnIOScope { moveInQueueSync(iFrom, iTo, true) } },
                                        leftSwipeCB = { leftCB(it) }, rightSwipeCB = { rightCB(it) })
                                }
                            }
                        }
                    }
                }
            }
        }

        lifecycle.addObserver(vm.swipeActions)
        refreshSwipeTelltale()
        return composeView
    }

    override fun onStart() {
        Logd(TAG, "onStart() called")
        super.onStart()
        loadCurQueue(true)
        procFlowEvents()
        val sessionToken = SessionToken(requireContext(), ComponentName(requireContext(), PlaybackService::class.java))
        vm.browserFuture = MediaBrowser.Builder(requireContext(), sessionToken).buildAsync()
        vm.browserFuture.addListener({
            // here we can get the root of media items tree or we can get also the children if it is an album for example.
            mediaBrowser = vm.browserFuture.get()
            mediaBrowser?.subscribe("CurQueue", null)
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        Logd(TAG, "onStop() called")
        super.onStop()
        cancelFlowEvents()
        mediaBrowser?.unsubscribe("CurQueue")
        mediaBrowser = null
        MediaBrowser.releaseFuture(vm.browserFuture)
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun FeedsGrid() {
        val context = LocalContext.current
        val lazyGridState = rememberLazyGridState()
        LazyVerticalGrid(state = lazyGridState, columns = GridCells.Adaptive(80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 16.dp)) {
            items(vm.feedsAssociated.size, key = {index -> vm.feedsAssociated[index].id}) { index ->
                val feed by remember { mutableStateOf(vm.feedsAssociated[index]) }
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
                        vm.infoTextUpdate = if (event.isRunning) "U" else ""
                        vm.infoBarText.value = "${vm.infoText} ${vm.infoTextUpdate}"
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
        if (vm.showBin) return
        when (event.action) {
            FlowEvent.QueueEvent.Action.ADDED -> {
                if (event.episodes.isNotEmpty() && !curQueue.contains(event.episodes[0])) {
                    vm.queueItems.addAll(event.episodes)
                    for (e in event.episodes) vm.vms.add(EpisodeVM(e, TAG))
                }
            }
            FlowEvent.QueueEvent.Action.SET_QUEUE, FlowEvent.QueueEvent.Action.SORTED -> {
                vm.queueItems.clear()
                vm.queueItems.addAll(event.episodes)
                stopMonitor(vm.vms)
                vm.vms.clear()
                for (e in event.episodes) vm.vms.add(EpisodeVM(e, TAG))
            }
            FlowEvent.QueueEvent.Action.REMOVED, FlowEvent.QueueEvent.Action.IRREVERSIBLE_REMOVED -> {
                if (event.episodes.isNotEmpty()) {
                    for (e in event.episodes) {
                        val pos: Int = Episodes.indexOfItemWithId(vm.queueItems, e.id)
                        if (pos >= 0) {
                            Logd(TAG, "removing episode $pos ${vm.queueItems[pos].title} $e")
//                            queueItems[pos].stopMonitoring.value = true
                            vm.queueItems.removeAt(pos)
                            vm.vms[pos].stopMonitoring()
                            vm.vms.removeAt(pos)
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
                vm.queueItems.clear()
                stopMonitor(vm.vms)
                vm.vms.clear()
            }
            FlowEvent.QueueEvent.Action.MOVED, FlowEvent.QueueEvent.Action.DELETED_MEDIA -> return
        }
        vm.queues = realm.query(PlayQueue::class).find()
        vm.queueNames = vm.queues.map { it.name }.toMutableStateList()
        vm.curIndex = vm.queues.indexOfFirst { it.id == curQueue.id }
        vm.spinnerTexts.clear()
        vm.spinnerTexts.addAll(vm.queues.map { "${it.name} : ${it.size()}" })
        refreshInfoBar()
    }

    private fun onPlayEvent(event: FlowEvent.PlayEvent) {
        val pos: Int = Episodes.indexOfItemWithId(vm.queueItems, event.episode.id)
        Logd(TAG, "onPlayEvent action: ${event.action} pos: $pos ${event.episode.title}")
        if (pos >= 0) vm.vms[pos].isPlayingState = event.isPlaying()
    }

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
//        Logd(TAG, "onEventMainThread() called with ${event.TAG}")
        if (loadItemsRunning) return
        for (url in event.urls) {
//            if (!event.isCompleted(url)) continue
            val pos: Int = Episodes.indexOfItemWithDownloadUrl(vm.queueItems.toList(), url)
            if (pos >= 0) vm.vms[pos].downloadState = event.map[url]?.state ?: DownloadStatus.State.UNKNOWN.ordinal

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
        if (event.episode == null && !vm.showBin) loadCurQueue(false)
    }

    private fun onFeedPrefsChanged(event: FlowEvent.FeedChangeEvent) {
        Logd(TAG,"speedPresetChanged called")
        for (item in vm.queueItems) if (item.feed?.id == event.feed.id) item.feed = null
    }

    private fun refreshSwipeTelltale() {
        if (vm.showBin) {
            vm.leftActionStateBin.value = vm.swipeActionsBin.actions.left[0]
            vm.rightActionStateBin.value = vm.swipeActionsBin.actions.right[0]
        } else {
            vm.leftActionState.value = vm.swipeActions.actions.left[0]
            vm.rightActionState.value = vm.swipeActions.actions.right[0]
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
        vm.queueItems.clear()
        stopMonitor(vm.vms)
        vm.vms.clear()
        super.onDestroyView()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        var showSpinner by remember { mutableStateOf(!vm.showBin) }
        var title by remember { mutableStateOf(if (vm.showBin) curQueue.name + " Bin" else "") }
        var showRename by remember { mutableStateOf(curQueue.name != "Default") }
        TopAppBar(title = {
            if (showSpinner) SpinnerExternalSet(items = vm.spinnerTexts, selectedIndex = vm.curIndex) { index: Int ->
                Logd(TAG, "Queue selected: ${vm.queues[index].name}")
                val prevQueueSize = curQueue.size()
                curQueue = upsertBlk(vm.queues[index]) { it.update() }
                showRename = curQueue.name != "Default"
                loadCurQueue(true)
                playbackService?.notifyCurQueueItemsChanged(max(prevQueueSize, curQueue.size()))
            } else Text(title) },
            navigationIcon = if (vm.displayUpArrow) {
                { IconButton(onClick = { parentFragmentManager.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            } else {
                { IconButton(onClick = { (activity as? MainActivity)?.openDrawer() }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_playlist_play), contentDescription = "Open Drawer") } }
            },
            actions = {
                val binIconRes by remember(vm.showBin) { derivedStateOf { if (vm.showBin) R.drawable.playlist_play else R.drawable.ic_history } }
//                var binIconRes by remember { mutableIntStateOf( if (showBin) R.drawable.playlist_play else R.drawable.ic_history) }
//                var feedsIconRes = if (showFeeds) R.drawable.playlist_play else R.drawable.baseline_dynamic_feed_24)
                val feedsIconRes by remember(vm.showFeeds) { derivedStateOf { if (vm.showFeeds) R.drawable.playlist_play else R.drawable.baseline_dynamic_feed_24 } }
                IconButton(onClick = {
                    vm.showBin = !vm.showBin
                    showSpinner = !vm.showBin
                    title = if (vm.showBin) curQueue.name + " Bin" else ""
                    refreshSwipeTelltale()
//                    binIconRes = if (showBin) R.drawable.playlist_play else R.drawable.ic_history
                    loadCurQueue(false)
                }) { Icon(imageVector = ImageVector.vectorResource(binIconRes), contentDescription = "bin") }
                IconButton(onClick = { vm.showFeeds = !vm.showFeeds }) { Icon(imageVector = ImageVector.vectorResource(feedsIconRes), contentDescription = "feeds") }
                if (!vm.showBin) IconButton(onClick = { (activity as MainActivity).loadChildFragment(SearchFragment.newInstance())
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.clear_bin_label)) }, onClick = {
                        curQueue = upsertBlk(curQueue) {
                            it.idsBinList.clear()
                            it.update()
                        }
                        if (vm.showBin) loadCurQueue(false)
                        expanded = false
                    })
                    if (!vm.showBin) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.sort)) }, onClick = {
                            vm.showSortDialog = true
                            expanded = false
                        })
                        if (showRename) DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = {
                            vm.showRenameQueueDialog.value = true
                            expanded = false
                        })
                        if (vm.queueNames.size < 9) DropdownMenuItem(text = { Text(stringResource(R.string.add_queue)) }, onClick = {
                            vm.showAddQueueDialog.value = true
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.clear_queue_label)) }, onClick = {
                            vm.showClearQueueDialog.value = true
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.refresh_label)) }, onClick = {
                            FeedUpdateManager.runOnceOrAsk(requireContext())
                            expanded = false
                        })
                        if (!isQueueKeepSorted) DropdownMenuItem(text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.lock_queue))
                                Checkbox(checked = vm.isQueueLocked, onCheckedChange = {  })
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
                            if (newName.isNotEmpty() && curQueue.name != newName && vm.queueNames.indexOf(newName) < 0) {
                                val oldName = curQueue.name
                                curQueue = upsertBlk(curQueue) { it.name = newName }
                                val index_ = vm.queueNames.indexOf(oldName)
                                vm.queueNames[index_] = newName
                                vm.spinnerTexts[index_] = newName + " : " + curQueue.episodeIds.size
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
                            if (newName.isNotEmpty() && vm.queueNames.indexOf(newName) < 0) {
                                val newQueue = PlayQueue()
                                newQueue.id = vm.queueNames.size.toLong()
                                newQueue.name = newName
                                upsertBlk(newQueue) {}
                                vm.queues = realm.query(PlayQueue::class).find()
                                vm.queueNames = vm.queues.map { it.name }.toMutableStateList()
                                vm.curIndex = vm.queues.indexOfFirst { it.id == curQueue.id }
                                vm.spinnerTexts.clear()
                                vm.spinnerTexts.addAll(vm.queues.map { "${it.name} : ${it.episodeIds.size}" })
                                onDismiss()
                            }
                        }) { Text(stringResource(R.string.confirm_label)) }
                    }
                }
            }
        }
    }

     private fun toggleQueueLock() {
        if (vm.isQueueLocked) setQueueLock(false)
        else {
            val shouldShowLockWarning = mutableStateOf(prefs.getBoolean(PREF_SHOW_LOCK_WARNING, true))
            if (!shouldShowLockWarning.value) setQueueLock(true)
            else vm.shouldShowLockWarningDiwload = true
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
        vm.isQueueLocked = locked
        putPref(AppPrefs.prefQueueLocked, locked)
        vm.dragDropEnabled = !(isQueueKeepSorted || vm.isQueueLocked)
        if (vm.queueItems.isEmpty()) {
            if (locked) (activity as MainActivity).showSnackbarAbovePlayer(R.string.queue_locked, Snackbar.LENGTH_SHORT)
            else (activity as MainActivity).showSnackbarAbovePlayer(R.string.queue_unlocked, Snackbar.LENGTH_SHORT)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, vm.displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    private fun refreshInfoBar() {
        vm.infoText = String.format(Locale.getDefault(), "%d%s", vm.queueItems.size, getString(R.string.episodes_suffix))
        if (vm.queueItems.isNotEmpty()) {
            var timeLeft: Long = 0
            for (item in vm.queueItems) {
                var playbackSpeed = 1f
                if (getPref(AppPrefs.prefPlaybackTimeRespectsSpeed, false)) playbackSpeed = getCurrentPlaybackSpeed(item)
                val itemTimeLeft: Long = (item.duration - item.position).toLong()
                timeLeft = (timeLeft + itemTimeLeft / playbackSpeed).toLong()
            }
            vm.infoText += " • "
            vm.infoText += DurationConverter.getDurationStringLocalized(timeLeft)
        }
        vm.infoBarText.value = "${vm.infoText} ${vm.infoTextUpdate}"
    }

    private var loadItemsRunning = false
    private fun loadCurQueue(restoreScrollPosition: Boolean) {
        if (!loadItemsRunning) {
            loadItemsRunning = true
            Logd(TAG, "loadCurQueue() called ${curQueue.name}")
            while (curQueue.name.isEmpty()) runBlocking { delay(100) }
//            if (queueItems.isNotEmpty()) emptyViewHandler.hide()
            vm.feedsAssociated = realm.query(Feed::class).query("queueId == ${curQueue.id}").find()
            vm.queueItems.clear()
            stopMonitor(vm.vms)
            vm.vms.clear()
            if (vm.showBin) vm.queueItems.addAll(realm.query(Episode::class, "id IN $0", curQueue.idsBinList)
                    .find().sortedByDescending { curQueue.idsBinList.indexOf(it.id) })
            else {
                curQueue.episodes.clear()
                vm.queueItems.addAll(curQueue.episodes)
            }
            for (e in vm.queueItems) vm.vms.add(EpisodeVM(e, TAG))
            Logd(TAG, "loadCurQueue() curQueue.episodes: ${curQueue.episodes.size}")
            vm.queues = realm.query(PlayQueue::class).find()
            vm.curIndex = vm.queues.indexOfFirst { it.id == curQueue.id }
            vm.spinnerTexts.clear()
            vm.spinnerTexts.addAll(vm.queues.map { "${it.name} : ${it.size()}" })
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
