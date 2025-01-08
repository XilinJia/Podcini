package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.EpisodeSortOrder.Companion.fromCode
import ac.mdiq.podcini.storage.model.EpisodeSortOrder.Companion.getPermutor
import ac.mdiq.podcini.ui.actions.SwipeAction
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.actions.SwipeActions.Companion.SwipeActionsSettingDialog
import ac.mdiq.podcini.ui.actions.SwipeActions.NoActionSwipeAction
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.activity.MainActivity.Screens
import ac.mdiq.podcini.ui.compose.*
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.setSearchTerms
import ac.mdiq.podcini.util.*
import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.Semaphore
import kotlin.math.min


class FeedEpisodesVM(val context: Context, val lcScope: CoroutineScope) {
    internal var swipeActions: SwipeActions
    internal var leftActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
    internal var rightActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
    internal var showSwipeActionsDialog by mutableStateOf(false)

    internal var infoBarText = mutableStateOf("")
    private var infoTextFiltered = ""
    private var infoTextUpdate = ""
    //        internal var displayUpArrow by mutableStateOf(false)
    private var headerCreated = false
    internal var feedID: Long = 0
    internal var feed by mutableStateOf<Feed?>(null)
    internal var rating by mutableStateOf(Rating.UNRATED.code)

    internal val episodes = mutableStateListOf<Episode>()
    internal val vms = mutableStateListOf<EpisodeVM>()

    internal var ieMap: Map<Long, Int> = mapOf()
    internal var ueMap: Map<String, Int> = mapOf()

    internal var enableFilter: Boolean = true
    internal var filterButtonColor = mutableStateOf(Color.White)

    internal var showRemoveFeedDialog by mutableStateOf(false)
    internal var showFilterDialog by mutableStateOf(false)
    internal var showRenameDialog by mutableStateOf(false)
    internal var showSortDialog by mutableStateOf(false)
    internal var sortOrder by mutableStateOf(EpisodeSortOrder.DATE_NEW_OLD)
    internal var layoutModeIndex by mutableIntStateOf(0)

    private var onInit: Boolean = true
    private var filterJob: Job? = null

    init {
        sortOrder = feed?.sortOrder ?: EpisodeSortOrder.DATE_NEW_OLD
        swipeActions = SwipeActions(context, TAG)
        leftActionState.value = swipeActions.actions.left[0]
        rightActionState.value = swipeActions.actions.right[0]
        layoutModeIndex = if (feed?.useWideLayout == true) 1 else 0
    }
    private var eventSink: Job? = null
    private var eventStickySink: Job? = null
    private var eventKeySink: Job?     = null
    internal fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
        eventStickySink?.cancel()
        eventStickySink = null
        eventKeySink?.cancel()
        eventKeySink = null
    }
    private fun onPlayEvent(event: FlowEvent.PlayEvent) {
        if (feed != null) {
            val pos: Int = ieMap[event.episode.id] ?: -1
            if (pos >= 0) {
                // TODO: vms[pos] may be out of bound
                if (!isFilteredOut(event.episode)) vms[pos].isPlayingState = event.isPlaying()
                if (event.isPlaying()) upsertBlk(feed!!) { it.lastPlayed = Date().time }
            }
        }
    }

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
//        Logd(TAG, "onEpisodeDownloadEvent() called with ${event.TAG}")
        if (feed == null || episodes.isEmpty()) return
        for (url in event.urls) {
//            if (!event.isCompleted(url)) continue
            val pos: Int = ueMap[url] ?: -1
            if (pos >= 0) {
                Logd(TAG, "onEpisodeDownloadEvent $pos ${event.map[url]?.state} ${episodes[pos].downloaded} ${episodes[pos].title}")
                vms[pos].downloadState = event.map[url]?.state ?: DownloadStatus.State.UNKNOWN.ordinal
            }
        }
    }

    internal fun procFlowEvents() {
        if (eventSink == null) eventSink = lcScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.PlayEvent -> onPlayEvent(event)
                    is FlowEvent.FeedChangeEvent -> if (feed?.id == event.feed.id) loadFeed()
                    is FlowEvent.FeedListEvent -> if (feed != null && event.contains(feed!!)) loadFeed()
                    else -> {}
                }
            }
        }
        if (eventStickySink == null) eventStickySink = lcScope.launch {
            EventFlow.stickyEvents.collectLatest { event ->
                Logd(TAG, "Received sticky event: ${event.TAG}")
                when (event) {
                    is FlowEvent.EpisodeDownloadEvent -> onEpisodeDownloadEvent(event)
                    is FlowEvent.FeedUpdatingEvent -> onFeedUpdateRunningEvent(event)
                    else -> {}
                }
            }
        }
//        if (eventKeySink == null) eventKeySink = lifecycleScope.launch {
//            EventFlow.keyEvents.collectLatest { event ->
//                Logd(TAG, "Received key event: $event, ignored")
////                onKeyUp(event)
//            }
//        }
    }

    private fun onFeedUpdateRunningEvent(event: FlowEvent.FeedUpdatingEvent) {
        infoTextUpdate = if (event.isRunning) context.getString(R.string.refreshing_label) else ""
        infoBarText.value = "$infoTextFiltered $infoTextUpdate"
        if (!event.isRunning) loadFeed()
    }

    internal fun refreshSwipeTelltale() {
        leftActionState.value = swipeActions.actions.left[0]
        rightActionState.value = swipeActions.actions.right[0]
    }

    private fun refreshHeaderView() {
        if (feed == null) {
            Log.e(TAG, "Unable to refresh header view")
            return
        }
        if (!headerCreated) headerCreated = true
        infoTextFiltered = ""
        if (!feed?.filterString.isNullOrEmpty()) {
            val filter: EpisodeFilter = feed!!.episodeFilter
            if (filter.properties.isNotEmpty()) infoTextFiltered = context.getString(R.string.filtered_label)
        }
        infoBarText.value = "$infoTextFiltered $infoTextUpdate"
    }

    private fun isFilteredOut(episode: Episode): Boolean {
        if (enableFilter && !feed?.filterString.isNullOrEmpty()) {
            val episodes_ = realm.query(Episode::class).query("feedId == ${feed!!.id}").query(feed!!.episodeFilter.queryString()).find()
            if (!episodes_.contains(episode)) {
                episodes.remove(episode)
                ieMap = episodes.withIndex().associate { (index, episode) -> episode.id to index }
                ueMap = episodes.mapIndexedNotNull { index, episode_ -> episode_.downloadUrl?.let { it to index } }.toMap()
                return true
            }
            return false
        }
        return false
    }

    private var loadJob: Job? = null
    internal fun loadFeed() {
        Logd(TAG, "loadFeed called $feedID")
        if (loadJob != null) {
            loadJob?.cancel()
            stopMonitor(vms)
            vms.clear()
        }
        loadJob = lcScope.launch {
            try {
                feed = withContext(Dispatchers.IO) {
                    val feed_ = getFeed(feedID)
                    if (feed_ != null) {
                        Logd(TAG, "loadItems feed_.episodes.size: ${feed_.episodes.size}")
                        val eListTmp = mutableListOf<Episode>()
                        if (enableFilter && feed_.filterString.isNotEmpty()) {
                            Logd(TAG, "episodeFilter: ${feed_.episodeFilter.queryString()}")
                            val episodes_ = realm.query(Episode::class).query("feedId == ${feed_.id}").query(feed_.episodeFilter.queryString()).find()
                            eListTmp.addAll(episodes_)
                        } else eListTmp.addAll(feed_.episodes)
                        sortOrder = feed_.sortOrder ?: EpisodeSortOrder.DATE_NEW_OLD
                        getPermutor(sortOrder).reorder(eListTmp)
                        episodes.clear()
                        episodes.addAll(eListTmp)
                        ieMap = episodes.withIndex().associate { (index, episode) -> episode.id to index }
                        ueMap = episodes.mapIndexedNotNull { index, episode -> episode.downloadUrl?.let { it to index } }.toMap()
                        withContext(Dispatchers.Main) {
                            layoutModeIndex = if (feed_.useWideLayout == true) 1 else 0
                            stopMonitor(vms)
                            vms.clear()
                            buildMoreItems()
                        }
                        if (onInit) {
                            var hasNonMediaItems = false
                            // TODO: ensure
//                            for (item in episodes) {
//                                if (item.media == null) {
//                                    hasNonMediaItems = true
//                                    break
//                                }
//                            }
                            if (hasNonMediaItems) {
                                lcScope.launch {
                                    withContext(Dispatchers.IO) {
                                        if (!FEObj.ttsReady) {
                                            initializeTTS(context)
                                            semaphore.acquire()
                                        }
                                    }
                                }
                            }
                            onInit = false
                        }
                    }
                    feed_
                }
                withContext(Dispatchers.Main) {
                    Logd(TAG, "loadItems subscribe called ${feed?.title}")
                    rating = feed?.rating ?: Rating.UNRATED.code
                    refreshHeaderView()
                }
            } catch (e: Throwable) {
                feed = null
                refreshHeaderView()
                Log.e(TAG, Log.getStackTraceString(e))
            } catch (e: Exception) { Log.e(TAG, Log.getStackTraceString(e)) }
        }.apply { invokeOnCompletion { loadJob = null } }
    }
    fun buildMoreItems() {
        val nextItems = (vms.size until min(vms.size + VMS_CHUNK_SIZE, episodes.size)).map { EpisodeVM(episodes[it], TAG) }
        if (nextItems.isNotEmpty()) vms.addAll(nextItems)
    }
    private val semaphore = Semaphore(0)
    private fun initializeTTS(context: Context) {
        Logd(TAG, "starting TTS")
        if (FEObj.tts == null) {
            FEObj.tts = TextToSpeech(context) { status: Int ->
                if (status == TextToSpeech.SUCCESS) {
                    FEObj.ttsReady = true
                    semaphore.release()
                    Logd(TAG, "TTS init success")
                } else {
                    Log.w(TAG, "TTS init failed")
                    lcScope.launch(Dispatchers.Main) { Toast.makeText(context, R.string.tts_init_failed, Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    internal fun filterLongClick() {
        if (feed == null) return
        enableFilter = !enableFilter
        if (filterJob != null) {
            Logd(TAG, "filterLongClick")
            filterJob?.cancel()
            stopMonitor(vms)
            vms.clear()
        }
        filterJob = lcScope.launch {
            val eListTmp = mutableListOf<Episode>()
            withContext(Dispatchers.IO) {
                if (enableFilter) {
                    filterButtonColor.value = Color.White
                    val episodes_ = realm.query(Episode::class).query("feedId == ${feed!!.id}").query(feed!!.episodeFilter.queryString()).find()
                    eListTmp.addAll(episodes_)
                } else {
                    filterButtonColor.value = Color.Red
                    eListTmp.addAll(feed!!.episodes)
                }
                getPermutor(fromCode(feed?.sortOrderCode ?: 0)).reorder(eListTmp)
                episodes.clear()
                episodes.addAll(eListTmp)
                ieMap = episodes.withIndex().associate { (index, episode) -> episode.id to index }
                ueMap = episodes.mapIndexedNotNull { index, episode -> episode.downloadUrl?.let { it to index } }.toMap()
            }
            withContext(Dispatchers.Main) {
                stopMonitor(vms)
                vms.clear()
                for (e in eListTmp) vms.add(EpisodeVM(e, TAG))
            }
        }.apply { invokeOnCompletion { filterJob = null } }
    }
}

//private var vm_: FeedEpisodesVM? = null
//private val vm: FeedEpisodesVM
//    get() = vm_ ?: throw IllegalStateException("$TAG vm is not initialized")

@Composable
fun FeedEpisodesScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
//    if (vm_ == null) vm_ = FeedEpisodesVM(context, scope)
    val vm = remember(feedOnDisplay.id) { FeedEpisodesVM(context, scope) }
//    vm_ = vm0

    //        val displayUpArrow by remember { derivedStateOf { navController.backQueue.size > 1 } }
//        var upArrowVisible by rememberSaveable { mutableStateOf(displayUpArrow) }
//        LaunchedEffect(navController.backQueue) { upArrowVisible = displayUpArrow }

    var displayUpArrow by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    Logd(TAG, "ON_CREATE")
//                    displayUpArrow = parentFragmentManager.backStackEntryCount != 0
//                        if (savedInstanceState != null) vm.displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
                    vm.feed = feedOnDisplay
                    vm.feedID = vm.feed?.id ?: 0
                    saveLastNavScreen(TAG, vm.feedID.toString())
                    lifecycleOwner.lifecycle.addObserver(vm.swipeActions)
                    vm.refreshSwipeTelltale()
                }
                Lifecycle.Event.ON_START -> {
                    Logd(TAG, "ON_START")
                    vm.loadFeed()
                    vm.procFlowEvents()
                }
                Lifecycle.Event.ON_RESUME -> {
                    Logd(TAG, "ON_RESUME")
                }
                Lifecycle.Event.ON_STOP -> {
                    Logd(TAG, "ON_STOP")
                    vm.cancelFlowEvents()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    Logd(TAG, "ON_DESTROY")
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            vm.feed = null
            vm.ieMap = mapOf()
            vm.ueMap = mapOf()
            vm.episodes.clear()
            stopMonitor(vm.vms)
            vm.vms.clear()
            FEObj.tts?.stop()
            FEObj.tts?.shutdown()
            FEObj.ttsWorking = false
            FEObj.ttsReady = false
            FEObj.tts = null
//            vm_ = null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    BackHandler { mainNavController.popBackStack() }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun FeedEpisodesHeader(filterButColor: Color, filterClickCB: ()->Unit, filterLongClickCB: ()->Unit) {
        val textColor = MaterialTheme.colorScheme.onSurface
        var showChooseRatingDialog by remember { mutableStateOf(false) }
        if (showChooseRatingDialog) ChooseRatingDialog(listOf(vm.feed!!)) {
            showChooseRatingDialog = false
            vm.feed = realm.query(Feed::class).query("id == $0", vm.feed!!.id).first().find()!!
            vm.rating = vm.feed!!.rating
        }
        ConstraintLayout(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val (bgImage, bgColor, controlRow, imgvCover) = createRefs()
            AsyncImage(model = vm.feed?.imageUrl?:"", contentDescription = "bgImage", contentScale = ContentScale.FillBounds, error = painterResource(R.drawable.teaser),
                modifier = Modifier.fillMaxSize().blur(radiusX = 15.dp, radiusY = 15.dp).constrainAs(bgImage) {
                    bottom.linkTo(parent.bottom)
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end) })
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)).constrainAs(bgColor) {
                bottom.linkTo(parent.bottom)
                top.linkTo(parent.top)
                start.linkTo(parent.start)
                end.linkTo(parent.end) })
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).constrainAs(controlRow) {
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start)
                width = Dimension.fillToConstraints
            }) {
                Spacer(modifier = Modifier.weight(0.7f))
                val ratingIconRes = Rating.fromCode(vm.rating).res
                Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                    modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(30.dp).height(30.dp).clickable(onClick = { showChooseRatingDialog = true }))
                Spacer(modifier = Modifier.weight(0.2f))
                Icon(imageVector = ImageVector.vectorResource(R.drawable.arrows_sort), tint = textColor, contentDescription = "butSort",
                    modifier = Modifier.width(40.dp).height(40.dp).padding(3.dp).clickable(onClick = { vm.showSortDialog = true }))
                Spacer(modifier = Modifier.width(15.dp))
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter_white), tint = if (filterButColor == Color.White) textColor else filterButColor, contentDescription = "butFilter",
                    modifier = Modifier.width(40.dp).height(40.dp).padding(3.dp).combinedClickable(onClick = filterClickCB, onLongClick = filterLongClickCB))
                Spacer(modifier = Modifier.width(15.dp))
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_settings_white), tint = textColor, contentDescription = "butShowSettings",
                    modifier = Modifier.width(40.dp).height(40.dp).padding(3.dp).clickable(onClick = {
                        if (vm.feed != null) {
                            feedOnDisplay = vm.feed!!
                            mainNavController.navigate(Screens.FeedSettings.name)
                        }
                    }))
                Spacer(modifier = Modifier.weight(0.4f))
                Text(vm.episodes.size.toString() + " / " + vm.feed?.episodes?.size?.toString(), textAlign = TextAlign.End, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            }
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).constrainAs(imgvCover) {
                top.linkTo(parent.top)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
                width = Dimension.fillToConstraints
            }) {
                AsyncImage(model = vm.feed?.imageUrl ?: "", contentDescription = "imgvCover", error = painterResource(R.mipmap.ic_launcher),
                    modifier = Modifier.width(100.dp).height(100.dp).padding(start = 16.dp, end = 16.dp).clickable(onClick = {
                        if (vm.feed != null) {
                            feedOnDisplay = vm.feed!!
                            mainNavController.navigate(Screens.FeedInfo.name)
                        }
                    }))
                Column(Modifier.padding(top = 10.dp)) {
                    Text(vm.feed?.title ?: "", color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(vm.feed?.author ?: "", color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar(displayUpArrow: Boolean) {
//    val navController = LocalNavController.current
        val context = LocalContext.current
        var expanded by remember { mutableStateOf(false) }
        TopAppBar(title = { Text("") },
            navigationIcon = if (displayUpArrow) {
                { IconButton(onClick = { if (mainNavController.previousBackStackEntry != null) mainNavController.popBackStack()
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            } else {
                { IconButton(onClick = { MainActivity.openDrawer() }) { Icon(Icons.Filled.Menu, contentDescription = "Open Drawer") } }
            },
            actions = {
                IconButton(onClick = {
                    mainNavController.navigate(Screens.Queues.name)
//                (context as MainActivity).bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.playlist_play), contentDescription = "queue") }
                if (vm.feed != null) IconButton(onClick = {
                    setSearchTerms("", vm.feed)
                    mainNavController.navigate(Screens.Search.name)
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                if (!vm.feed?.link.isNullOrBlank()) IconButton(onClick = { IntentUtils.openInBrowser(context, vm.feed!!.link!!)
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_web), contentDescription = "web") }
                if (vm.feed != null) {
                    IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.refresh_label)) }, onClick = {
                            FeedUpdateManager.runOnceOrAsk(context, vm.feed)
                            expanded = false
                        })
                        if (vm.feed?.isPaged == true) DropdownMenuItem(text = { Text(stringResource(R.string.load_complete_feed)) }, onClick = {
                            Thread {
                                try {
                                    if (vm.feed != null) {
                                        val feed_ = upsertBlk(vm.feed!!) {
                                            it.nextPageLink = it.downloadUrl
                                            it.pageNr = 0
                                        }
                                        FeedUpdateManager.runOnce(context, feed_)
                                    }
                                } catch (e: ExecutionException) { throw RuntimeException(e)
                                } catch (e: InterruptedException) { throw RuntimeException(e) }
                            }.start()
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.share_label)) }, onClick = {
                            ShareUtils.shareFeedLinkNew(context, vm.feed!!)
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.rename_feed_label)) }, onClick = {
                            vm.showRenameDialog = true
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.remove_feed_label)) }, onClick = {
                            vm.showRemoveFeedDialog = true
                            expanded = false
                        })
                    }
                }
            }
        )
    }

    if (vm.showRemoveFeedDialog) RemoveFeedDialog(listOf(vm.feed!!), onDismissRequest = { vm.showRemoveFeedDialog = false }) {
        mainNavController.navigate("DefaultPage")
//        (context as MainActivity).loadFragment(AppPreferences.defaultPage, null)
//        // Make sure fragment is hidden before actually starting to delete
//        context.supportFragmentManager.executePendingTransactions()
    }
    if (vm.showFilterDialog) EpisodesFilterDialog(filter = vm.feed!!.episodeFilter,
        onDismissRequest = { vm.showFilterDialog = false }) { filterValues ->
        if (vm.feed != null) {
            Logd(TAG, "persist Episode Filter(): feedId = [${vm.feed?.id}], filterValues = [$filterValues]")
            runOnIOScope {
                val feed_ = realm.query(Feed::class, "id == ${vm.feed!!.id}").first().find()
                if (feed_ != null) {
                    vm.feed = upsert(feed_) { it.filterString = filterValues.joinToString() }
//                                loadFeed()
                }
            }
        }
    }
    if (vm.showRenameDialog) RenameOrCreateSyntheticFeed(vm.feed) { vm.showRenameDialog = false }
    if (vm.showSortDialog) EpisodeSortDialog(initOrder = vm.sortOrder, onDismissRequest = { vm.showSortDialog = false }) { sortOrder_, _ ->
        if (vm.feed != null) {
            Logd(TAG, "persist Episode SortOrder_")
            vm.sortOrder = sortOrder_
            runOnIOScope {
                val feed_ = realm.query(Feed::class, "id == ${vm.feed!!.id}").first().find()
                if (feed_ != null) vm.feed = upsert(feed_) { it.sortOrder = sortOrder_ }
            }
        }
    }
    if (vm.showSwipeActionsDialog) SwipeActionsSettingDialog(vm.swipeActions, onDismissRequest = { vm.showSwipeActionsDialog = false }) { actions ->
        vm.swipeActions.actions = actions
        vm.refreshSwipeTelltale()
    }
    vm.swipeActions.ActionOptionsDialog()
    Scaffold(topBar = { MyTopAppBar(displayUpArrow) }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            FeedEpisodesHeader(filterButColor = vm.filterButtonColor.value, filterClickCB = {
                if (vm.enableFilter && vm.feed != null) vm.showFilterDialog = true
            }, filterLongClickCB = { vm.filterLongClick() })
            InforBar(vm.infoBarText, leftAction = vm.leftActionState, rightAction = vm.rightActionState, actionConfig = { vm.showSwipeActionsDialog = true  })
            EpisodeLazyColumn(context, vms = vm.vms, feed = vm.feed, layoutMode = vm.layoutModeIndex,
                buildMoreItems = { vm.buildMoreItems() },
                refreshCB = { FeedUpdateManager.runOnceOrAsk(context, vm.feed) },
                leftSwipeCB = {
                    if (vm.leftActionState.value is NoActionSwipeAction) vm.showSwipeActionsDialog = true
                    else vm.leftActionState.value.performAction(it)
                },
                rightSwipeCB = {
                    Logd(TAG, "vm.rightActionState: ${vm.rightActionState.value.getId()}")
                    if (vm.rightActionState.value is NoActionSwipeAction) vm.showSwipeActionsDialog = true
                    else vm.rightActionState.value.performAction(it)
                },
            )
        }
    }
}

private const val TAG = "FeedEpisodesScreen"
const val ARGUMENT_FEED_ID = "argument.ac.mdiq.podcini.feed_id"
private const val KEY_UP_ARROW = "up_arrow"

object FEObj {
    var tts: TextToSpeech? = null
    var ttsReady = false
    var ttsWorking = false
}