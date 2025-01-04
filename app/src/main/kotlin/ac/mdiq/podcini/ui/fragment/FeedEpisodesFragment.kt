package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.preferences.AppPreferences
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
import ac.mdiq.podcini.ui.compose.*
import ac.mdiq.podcini.ui.utils.TransitionEffect
import ac.mdiq.podcini.util.*
import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.Semaphore
import kotlin.math.min

class FeedEpisodesFragment : Fragment() {

    class FeedEpisodesVM {
        internal lateinit var swipeActions: SwipeActions
        internal var leftActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
        internal var rightActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
        internal var showSwipeActionsDialog by mutableStateOf(false)

        internal var infoBarText = mutableStateOf("")
        internal var infoTextFiltered = ""
        internal var infoTextUpdate = ""
        internal var displayUpArrow by mutableStateOf(false)
        internal var headerCreated = false
        internal var feedID: Long = 0
        internal var feed by mutableStateOf<Feed?>(null)
        var rating by mutableStateOf(Rating.UNRATED.code)

        internal val episodes = mutableStateListOf<Episode>()
        internal val vms = mutableStateListOf<EpisodeVM>()

        internal var ieMap: Map<Long, Int> = mapOf()
        internal var ueMap: Map<String, Int> = mapOf()

        internal var enableFilter: Boolean = true
        internal var filterButtonColor = mutableStateOf(Color.White)

        internal var showRemoveFeedDialog by mutableStateOf(false)
        internal var showFilterDialog by mutableStateOf(false)
        internal var showRenameDialog by mutableStateOf(false)
        var showSortDialog by mutableStateOf(false)
        var sortOrder by mutableStateOf(EpisodeSortOrder.DATE_NEW_OLD)
        var layoutModeIndex by mutableIntStateOf(0)

        internal var onInit: Boolean = true
    }

    private val vm = FeedEpisodesVM()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args: Bundle? = arguments
        if (args != null) vm.feedID = args.getLong(ARGUMENT_FEED_ID)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logd(TAG, "fragment onCreateView")

        vm.sortOrder = vm.feed?.sortOrder ?: EpisodeSortOrder.DATE_NEW_OLD
        vm.displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) vm.displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
        NavDrawerFragment.saveLastNavFragment(TAG, vm.feedID.toString())
        vm.swipeActions = SwipeActions(this, TAG)
        vm.leftActionState.value = vm.swipeActions.actions.left[0]
        vm.rightActionState.value = vm.swipeActions.actions.right[0]
        lifecycle.addObserver(vm.swipeActions)

        var filterJob: Job? = null
        fun filterLongClick() {
            if (vm.feed == null) return
            vm.enableFilter = !vm.enableFilter
            if (filterJob != null) {
                filterJob?.cancel()
                stopMonitor(vm.vms)
                vm.vms.clear()
            }
            filterJob = lifecycleScope.launch {
                val eListTmp = mutableListOf<Episode>()
                withContext(Dispatchers.IO) {
                    if (vm.enableFilter) {
                        vm.filterButtonColor.value = Color.White
                        val episodes_ = realm.query(Episode::class).query("feedId == ${vm.feed!!.id}").query(vm.feed!!.episodeFilter.queryString()).find()
                        eListTmp.addAll(episodes_)
                    } else {
                        vm.filterButtonColor.value = Color.Red
                        eListTmp.addAll(vm.feed!!.episodes)
                    }
                    getPermutor(fromCode(vm.feed?.sortOrderCode ?: 0)).reorder(eListTmp)
                    vm.episodes.clear()
                    vm.episodes.addAll(eListTmp)
                    vm.ieMap = vm.episodes.withIndex().associate { (index, episode) -> episode.id to index }
                    vm.ueMap = vm.episodes.mapIndexedNotNull { index, episode -> episode.downloadUrl?.let { it to index } }.toMap()
                }
                withContext(Dispatchers.Main) {
                    stopMonitor(vm.vms)
                    vm.vms.clear()
                    for (e in eListTmp) vm.vms.add(EpisodeVM(e, TAG))
                }
            }.apply { invokeOnCompletion { filterJob = null } }
        }

        vm.layoutModeIndex = if (vm.feed?.useWideLayout == true) 1 else 0

        val composeView = ComposeView(requireContext()).apply {
            setContent {
                CustomTheme(requireContext()) {
                    if (vm.showRemoveFeedDialog) RemoveFeedDialog(listOf(vm.feed!!), onDismissRequest = { vm.showRemoveFeedDialog = false }) {
                        (activity as MainActivity).loadFragment(AppPreferences.defaultPage, null)
                        // Make sure fragment is hidden before actually starting to delete
                        requireActivity().supportFragmentManager.executePendingTransactions()
                    }
                    if (vm.showFilterDialog) EpisodesFilterDialog(filter = vm.feed!!.episodeFilter,
//                    filtersDisabled = mutableSetOf(EpisodeFilter.EpisodesFilterGroup.DOWNLOADED, EpisodeFilter.EpisodesFilterGroup.MEDIA),
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
                        refreshSwipeTelltale()
                    }
                    vm.swipeActions.ActionOptionsDialog()
                    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
                        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                            FeedEpisodesHeader(activity = (activity as MainActivity), filterButColor = vm.filterButtonColor.value, filterClickCB = {
                                if (vm.enableFilter && vm.feed != null) vm.showFilterDialog = true
                            }, filterLongClickCB = { filterLongClick() })
                            InforBar(vm.infoBarText, leftAction = vm.leftActionState, rightAction = vm.rightActionState, actionConfig = { vm.showSwipeActionsDialog = true  })
                            EpisodeLazyColumn(activity as MainActivity, vms = vm.vms, feed = vm.feed, layoutMode = vm.layoutModeIndex,
                                buildMoreItems = { buildMoreItems() },
                                refreshCB = { FeedUpdateManager.runOnceOrAsk(requireContext(), vm.feed) },
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
            }
        }

        lifecycle.addObserver(vm.swipeActions)
        refreshSwipeTelltale()
        return composeView
    }

    override fun onStart() {
        Logd(TAG, "onStart() called")
        super.onStart()
        loadFeed()
        procFlowEvents()
    }

    override fun onStop() {
        Logd(TAG, "onStop() called")
        super.onStop()
        cancelFlowEvents()
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun FeedEpisodesHeader(activity: MainActivity, filterButColor: Color, filterClickCB: ()->Unit, filterLongClickCB: ()->Unit) {
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
                            val fragment = FeedSettingsFragment.newInstance(vm.feed!!)
                            activity.loadChildFragment(fragment, TransitionEffect.SLIDE)
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
                            val fragment = FeedInfoFragment.newInstance(vm.feed!!)
                            activity.loadChildFragment(fragment, TransitionEffect.SLIDE)
                        }
                    }))
                Column(Modifier.padding(top = 10.dp)) {
                    Text(vm.feed?.title ?: "", color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(vm.feed?.author ?: "", color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    private val semaphore = Semaphore(0)
    private fun initializeTTS(context: Context) {
        Logd(TAG, "starting TTS")
        if (tts == null) {
            tts = TextToSpeech(context) { status: Int ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    semaphore.release()
                    Logd(TAG, "TTS init success")
                } else {
                    Log.w(TAG, "TTS init failed")
                    requireActivity().runOnUiThread { Toast.makeText(context, R.string.tts_init_failed, Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
//        ioScope.cancel()

        vm.feed = null
        vm.ieMap = mapOf()
        vm.ueMap = mapOf()
        vm.episodes.clear()
        stopMonitor(vm.vms)
        vm.vms.clear()
        tts?.stop()
        tts?.shutdown()
        ttsWorking = false
        ttsReady = false
        tts = null

        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, vm.displayUpArrow)
        super.onSaveInstanceState(outState)
    }

//    override fun onConfigurationChanged(newConfig: Configuration) {
//        super.onConfigurationChanged(newConfig)
//        val horizontalSpacing = resources.getDimension(R.dimen.additional_horizontal_spacing).toInt()
////        binding.header.headerContainer.setPadding(horizontalSpacing, binding.header.headerContainer.paddingTop,
////            horizontalSpacing, binding.header.headerContainer.paddingBottom)
//    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        TopAppBar(title = { Text("") },
            navigationIcon = if (vm.displayUpArrow) {
                { IconButton(onClick = { parentFragmentManager.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            } else {
                { IconButton(onClick = { (activity as? MainActivity)?.openDrawer() }) { Icon(Icons.Filled.Menu, contentDescription = "Open Drawer") } }
            },
            actions = {
                IconButton(onClick = {
                    val qFrag = QueuesFragment()
                    (activity as MainActivity).loadChildFragment(qFrag)
                    (activity as MainActivity).bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.playlist_play), contentDescription = "queue") }
                if (vm.feed != null) IconButton(onClick = { (activity as MainActivity).loadChildFragment(SearchFragment.newInstance(vm.feed!!.id, vm.feed!!.title))
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                if (!vm.feed?.link.isNullOrBlank()) IconButton(onClick = {
                    IntentUtils.openInBrowser(requireContext(), vm.feed!!.link!!)
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_web), contentDescription = "web") }
                if (vm.feed != null) {
                    IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.refresh_label)) }, onClick = {
                            FeedUpdateManager.runOnceOrAsk(requireContext(), vm.feed)
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
                                        FeedUpdateManager.runOnce(requireContext(), feed_)
                                    }
                                } catch (e: ExecutionException) { throw RuntimeException(e)
                                } catch (e: InterruptedException) { throw RuntimeException(e) }
                            }.start()
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.share_label)) }, onClick = {
                            ShareUtils.shareFeedLinkNew(requireContext(), vm.feed!!)
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

    private fun onPlayEvent(event: FlowEvent.PlayEvent) {
        if (vm.feed != null) {
            val pos: Int = vm.ieMap[event.episode.id] ?: -1
            if (pos >= 0) {
                // TODO: vm.vms[pos] may be out of bound
                if (!isFilteredOut(event.episode)) vm.vms[pos].isPlayingState = event.isPlaying()
                if (event.isPlaying()) upsertBlk(vm.feed!!) { it.lastPlayed = Date().time }
            }
        }
    }

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
//        Logd(TAG, "onEpisodeDownloadEvent() called with ${event.TAG}")
        if (vm.feed == null || vm.episodes.isEmpty()) return
        for (url in event.urls) {
//            if (!event.isCompleted(url)) continue
            val pos: Int = vm.ueMap[url] ?: -1
            if (pos >= 0) {
                Logd(TAG, "onEpisodeDownloadEvent $pos ${event.map[url]?.state} ${vm.episodes[pos].downloaded} ${vm.episodes[pos].title}")
                vm.vms[pos].downloadState = event.map[url]?.state ?: DownloadStatus.State.UNKNOWN.ordinal
            }
        }
    }

    private var eventSink: Job? = null
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
                    is FlowEvent.PlayEvent -> onPlayEvent(event)
                    is FlowEvent.FeedChangeEvent -> if (vm.feed?.id == event.feed.id) loadFeed()
                    is FlowEvent.FeedListEvent -> if (vm.feed != null && event.contains(vm.feed!!)) loadFeed()
                    else -> {}
                }
            }
        }
        if (eventStickySink == null) eventStickySink = lifecycleScope.launch {
            EventFlow.stickyEvents.collectLatest { event ->
                Logd(TAG, "Received sticky event: ${event.TAG}")
                when (event) {
                    is FlowEvent.EpisodeDownloadEvent -> onEpisodeDownloadEvent(event)
                    is FlowEvent.FeedUpdatingEvent -> onFeedUpdateRunningEvent(event)
                    else -> {}
                }
            }
        }
        if (eventKeySink == null) eventKeySink = lifecycleScope.launch {
            EventFlow.keyEvents.collectLatest { event ->
                Logd(TAG, "Received key event: $event, ignored")
//                onKeyUp(event)
            }
        }
    }

    private fun onFeedUpdateRunningEvent(event: FlowEvent.FeedUpdatingEvent) {
        vm.infoTextUpdate = if (event.isRunning) getString(R.string.refreshing_label) else ""
        vm.infoBarText.value = "${vm.infoTextFiltered} ${vm.infoTextUpdate}"
        if (!event.isRunning) loadFeed()
    }

    private fun refreshSwipeTelltale() {
        vm.leftActionState.value = vm.swipeActions.actions.left[0]
        vm.rightActionState.value = vm.swipeActions.actions.right[0]
    }

    private fun refreshHeaderView() {
        if (vm.feed == null) {
            Log.e(TAG, "Unable to refresh header view")
            return
        }
        if (!vm.headerCreated) vm.headerCreated = true
        vm.infoTextFiltered = ""
        if (!vm.feed?.filterString.isNullOrEmpty()) {
            val filter: EpisodeFilter = vm.feed!!.episodeFilter
            if (filter.properties.isNotEmpty()) vm.infoTextFiltered = this.getString(R.string.filtered_label)
        }
        vm.infoBarText.value = "${vm.infoTextFiltered} ${vm.infoTextUpdate}"
    }

    private fun isFilteredOut(episode: Episode): Boolean {
        if (vm.enableFilter && !vm.feed?.filterString.isNullOrEmpty()) {
            val episodes_ = realm.query(Episode::class).query("feedId == ${vm.feed!!.id}").query(vm.feed!!.episodeFilter.queryString()).find()
            if (!episodes_.contains(episode)) {
                vm.episodes.remove(episode)
                vm.ieMap = vm.episodes.withIndex().associate { (index, episode) -> episode.id to index }
                vm.ueMap = vm.episodes.mapIndexedNotNull { index, episode_ -> episode_.downloadUrl?.let { it to index } }.toMap()
                return true
            }
            return false
        }
        return false
    }

    private var loadJob: Job? = null
    private fun loadFeed() {
        Logd(TAG, "loadFeed called")
        if (loadJob != null) {
            loadJob?.cancel()
            stopMonitor(vm.vms)
            vm.vms.clear()
        }
        loadJob = lifecycleScope.launch {
            try {
                vm.feed = withContext(Dispatchers.IO) {
                    val feed_ = getFeed(vm.feedID)
                    if (feed_ != null) {
                        Logd(TAG, "loadItems feed_.episodes.size: ${feed_.episodes.size}")
                        val eListTmp = mutableListOf<Episode>()
                        if (vm.enableFilter && feed_.filterString.isNotEmpty()) {
                            Logd(TAG, "episodeFilter: ${feed_.episodeFilter.queryString()}")
                            val episodes_ = realm.query(Episode::class).query("feedId == ${feed_.id}").query(feed_.episodeFilter.queryString()).find()
                            eListTmp.addAll(episodes_)
                        } else eListTmp.addAll(feed_.episodes)
                        vm.sortOrder = feed_.sortOrder ?: EpisodeSortOrder.DATE_NEW_OLD
                        getPermutor(vm.sortOrder).reorder(eListTmp)
                        vm.episodes.clear()
                        vm.episodes.addAll(eListTmp)
                        vm.ieMap = vm.episodes.withIndex().associate { (index, episode) -> episode.id to index }
                        vm.ueMap = vm.episodes.mapIndexedNotNull { index, episode -> episode.downloadUrl?.let { it to index } }.toMap()
                        withContext(Dispatchers.Main) {
                            vm.layoutModeIndex = if (feed_.useWideLayout == true) 1 else 0
                            stopMonitor(vm.vms)
                            vm.vms.clear()
                            buildMoreItems()
                        }
                        if (vm.onInit) {
                            var hasNonMediaItems = false
                            // TODO: ensure
//                            for (item in vm.episodes) {
//                                if (item.media == null) {
//                                    hasNonMediaItems = true
//                                    break
//                                }
//                            }
                            if (hasNonMediaItems) {
                                lifecycleScope.launch {
                                    withContext(Dispatchers.IO) {
                                        if (!ttsReady) {
                                            initializeTTS(requireContext())
                                            semaphore.acquire()
                                        }
                                    }
                                }
                            }
                            vm.onInit = false
                        }
                    }
                    feed_
                }
                withContext(Dispatchers.Main) {
                    Logd(TAG, "loadItems subscribe called ${vm.feed?.title}")
                    vm.rating = vm.feed?.rating ?: Rating.UNRATED.code
                    refreshHeaderView()
                }
            } catch (e: Throwable) {
                vm.feed = null
                refreshHeaderView()
                Log.e(TAG, Log.getStackTraceString(e))
            } catch (e: Exception) { Log.e(TAG, Log.getStackTraceString(e)) }
        }.apply { invokeOnCompletion { loadJob = null } }
    }

    fun buildMoreItems() {
        val nextItems = (vm.vms.size until min(vm.vms.size + VMS_CHUNK_SIZE, vm.episodes.size)).map { EpisodeVM(vm.episodes[it], TAG) }
        if (nextItems.isNotEmpty()) vm.vms.addAll(nextItems)
    }

//    private fun onKeyUp(event: KeyEvent) {
//        if (!isAdded || !isVisible) return
//        when (event.keyCode) {
////            KeyEvent.KEYCODE_T -> binding.recyclerView.smoothScrollToPosition(0)
////            KeyEvent.KEYCODE_B -> binding.recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
//            else -> {}
//        }
//    }

    companion object {
        val TAG = FeedEpisodesFragment::class.simpleName ?: "Anonymous"
        const val ARGUMENT_FEED_ID = "argument.ac.mdiq.podcini.feed_id"
        private const val KEY_UP_ARROW = "up_arrow"

        var tts: TextToSpeech? = null
        var ttsReady = false
        var ttsWorking = false

        fun newInstance(feedId: Long): FeedEpisodesFragment {
            val i = FeedEpisodesFragment()
            val b = Bundle()
            b.putLong(ARGUMENT_FEED_ID, feedId)
            i.arguments = b
            return i
        }
    }
}
