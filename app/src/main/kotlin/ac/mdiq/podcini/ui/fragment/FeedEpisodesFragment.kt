package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.preferences.UserPreferences
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
import androidx.compose.material.icons.filled.ArrowBack
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

class FeedEpisodesFragment : Fragment() {

    private lateinit var swipeActions: SwipeActions

    private var infoBarText = mutableStateOf("")
    private var leftActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
    private var rightActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())

    private var infoTextFiltered = ""
    private var infoTextUpdate = ""
    private var displayUpArrow by mutableStateOf(false)
    private var headerCreated = false
    private var feedID: Long = 0
    private var feed by mutableStateOf<Feed?>(null)
    var rating by mutableStateOf(Rating.UNRATED.code)

    private val episodes = mutableStateListOf<Episode>()
    private val vms = mutableStateListOf<EpisodeVM>()

    private var ieMap: Map<Long, Int> = mapOf()
    private var ueMap: Map<String, Int> = mapOf()

    private var enableFilter: Boolean = true
    private var filterButtonColor = mutableStateOf(Color.White)

    private var showRemoveFeedDialog by mutableStateOf(false)
    private var showFilterDialog by mutableStateOf(false)
    private var showRenameDialog by mutableStateOf(false)
     var showSortDialog by mutableStateOf(false)
    var sortOrder by mutableStateOf(EpisodeSortOrder.DATE_NEW_OLD)
    var layoutMode by mutableIntStateOf(0)

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var onInit: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args: Bundle? = arguments
        if (args != null) feedID = args.getLong(ARGUMENT_FEED_ID)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logd(TAG, "fragment onCreateView")

        sortOrder = feed?.sortOrder ?: EpisodeSortOrder.DATE_NEW_OLD
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
        NavDrawerFragment.saveLastNavFragment(TAG, feedID.toString())

        swipeActions = SwipeActions(this, TAG)

        fun filterLongClick() {
            if (feed != null) {
                enableFilter = !enableFilter
                while (loadItemsRunning) Thread.sleep(50)
                loadItemsRunning = true
                val etmp = mutableListOf<Episode>()
                if (enableFilter) {
                    filterButtonColor.value = Color.White
                    val episodes_ = realm.query(Episode::class).query("feedId == ${feed!!.id}").query(feed!!.episodeFilter.queryString()).find()
                    etmp.addAll(episodes_)
                } else {
                    filterButtonColor.value = Color.Red
                    etmp.addAll(feed!!.episodes)
                }
                getPermutor(fromCode(feed?.preferences?.sortOrderCode ?: 0)).reorder(etmp)
                episodes.clear()
                episodes.addAll(etmp)
                ieMap = episodes.withIndex().associate { (index, episode) -> episode.id to index }
                ueMap = episodes.mapIndexedNotNull { index, episode -> episode.media?.downloadUrl?.let { it to index } }.toMap()
                vms.clear()
                for (e in etmp) { vms.add(EpisodeVM(e)) }
                loadItemsRunning = false
            }
        }
        layoutMode = if (feed?.preferences?.useWideLayout == true) 1 else 0

        val composeView = ComposeView(requireContext()).apply {
            setContent {
                CustomTheme(requireContext()) {
                    if (showRemoveFeedDialog) RemoveFeedDialog(listOf(feed!!), onDismissRequest = { showRemoveFeedDialog = false }) {
                        (activity as MainActivity).loadFragment(UserPreferences.defaultPage, null)
                        // Make sure fragment is hidden before actually starting to delete
                        requireActivity().supportFragmentManager.executePendingTransactions()
                    }
                    if (showFilterDialog) EpisodesFilterDialog(filter = feed!!.episodeFilter,
//                    filtersDisabled = mutableSetOf(EpisodeFilter.EpisodesFilterGroup.DOWNLOADED, EpisodeFilter.EpisodesFilterGroup.MEDIA),
                        onDismissRequest = { showFilterDialog = false }) { filterValues ->
                        if (feed != null) {
                            Logd(TAG, "persist Episode Filter(): feedId = [${feed?.id}], filterValues = [$filterValues]")
                            runOnIOScope {
                                val feed_ = realm.query(Feed::class, "id == ${feed!!.id}").first().find()
                                if (feed_ != null) {
                                    feed = upsert(feed_) { it.preferences?.filterString = filterValues.joinToString() }
//                                loadFeed()
                                }
                            }
                        }
                    }
                    if (showRenameDialog) RenameOrCreateSyntheticFeed(feed) { showRenameDialog = false }
                    if (showSortDialog) EpisodeSortDialog(initOrder = sortOrder, onDismissRequest = { showSortDialog = false }) { sortOrder_, _ ->
                        if (feed != null) {
                            Logd(TAG, "persist Episode SortOrder_")
                            sortOrder = sortOrder_
                            runOnIOScope {
                                val feed_ = realm.query(Feed::class, "id == ${feed!!.id}").first().find()
                                if (feed_ != null) feed = upsert(feed_) { it.sortOrder = sortOrder_ }
                            }
                        }
                    }
                    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
                        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                            FeedEpisodesHeader(activity = (activity as MainActivity), filterButColor = filterButtonColor.value, filterClickCB = {
                                if (enableFilter && feed != null) showFilterDialog = true
                            }, filterLongClickCB = { filterLongClick() })
                            InforBar(infoBarText, leftAction = leftActionState, rightAction = rightActionState, actionConfig = { swipeActions.showDialog() })
                            EpisodeLazyColumn(
                                activity as MainActivity, vms = vms, feed = feed, layoutMode = layoutMode,
                                refreshCB = { FeedUpdateManager.runOnceOrAsk(requireContext(), feed) },
                                leftSwipeCB = {
                                    if (leftActionState.value is NoActionSwipeAction) swipeActions.showDialog()
                                    else leftActionState.value.performAction(it, this@FeedEpisodesFragment)
                                },
                                rightSwipeCB = {
                                    Logd(TAG, "rightActionState: ${rightActionState.value.getId()}")
                                    if (rightActionState.value is NoActionSwipeAction) swipeActions.showDialog()
                                    else rightActionState.value.performAction(it, this@FeedEpisodesFragment)
                                },
                            )
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
        if (showChooseRatingDialog) ChooseRatingDialog(listOf(feed!!)) {
            showChooseRatingDialog = false
            feed = realm.query(Feed::class).query("id == $0", feed!!.id).first().find()!!
            rating = feed!!.rating
        }
        ConstraintLayout(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val (bgImage, bgColor, controlRow, imgvCover) = createRefs()
            AsyncImage(model = feed?.imageUrl?:"", contentDescription = "bgImage", contentScale = ContentScale.FillBounds, error = painterResource(R.drawable.teaser),
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
                val ratingIconRes = Rating.fromCode(rating).res
                Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                    modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(30.dp).height(30.dp).clickable(onClick = { showChooseRatingDialog = true }))
                Spacer(modifier = Modifier.weight(0.2f))
                Icon(imageVector = ImageVector.vectorResource(R.drawable.arrows_sort), tint = textColor, contentDescription = "butSort",
                    modifier = Modifier.width(40.dp).height(40.dp).padding(3.dp).clickable(onClick = { showSortDialog = true }))
                Spacer(modifier = Modifier.width(15.dp))
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter_white), tint = if (filterButColor == Color.White) textColor else filterButColor, contentDescription = "butFilter",
                    modifier = Modifier.width(40.dp).height(40.dp).padding(3.dp).combinedClickable(onClick = filterClickCB, onLongClick = filterLongClickCB))
                Spacer(modifier = Modifier.width(15.dp))
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_settings_white), tint = textColor, contentDescription = "butShowSettings",
                    modifier = Modifier.width(40.dp).height(40.dp).padding(3.dp).clickable(onClick = {
                        if (feed != null) {
                            val fragment = FeedSettingsFragment.newInstance(feed!!)
                            activity.loadChildFragment(fragment, TransitionEffect.SLIDE)
                        }
                    }))
                Spacer(modifier = Modifier.weight(0.4f))
                Text(episodes.size.toString() + " / " + feed?.episodes?.size?.toString(), textAlign = TextAlign.End, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            }
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).constrainAs(imgvCover) {
                top.linkTo(parent.top)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
                width = Dimension.fillToConstraints
            }) {
                AsyncImage(model = feed?.imageUrl ?: "", contentDescription = "imgvCover", error = painterResource(R.mipmap.ic_launcher),
                    modifier = Modifier.width(100.dp).height(100.dp).padding(start = 16.dp, end = 16.dp).clickable(onClick = {
                        if (feed != null) {
                            val fragment = FeedInfoFragment.newInstance(feed!!)
                            activity.loadChildFragment(fragment, TransitionEffect.SLIDE)
                        }
                    }))
                Column(Modifier.padding(top = 10.dp)) {
                    Text(feed?.title ?: "", color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(feed?.author ?: "", color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        ioScope.cancel()

        feed = null
        ieMap = mapOf()
        ueMap = mapOf()
        episodes.clear()
        vms.clear()
        tts?.stop()
        tts?.shutdown()
        ttsWorking = false
        ttsReady = false
        tts = null

        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
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
            navigationIcon = if (displayUpArrow) {
                { IconButton(onClick = { parentFragmentManager.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            } else {
                { IconButton(onClick = { (activity as? MainActivity)?.openDrawer() }) { Icon(Icons.Filled.Menu, contentDescription = "Open Drawer") } }
            },
            actions = {
                IconButton(onClick = {
                    val qFrag = QueuesFragment()
                    (activity as MainActivity).loadChildFragment(qFrag)
                    (activity as MainActivity).bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.playlist_play), contentDescription = "web") }
                if (feed != null) IconButton(onClick = { (activity as MainActivity).loadChildFragment(SearchFragment.newInstance(feed!!.id, feed!!.title))
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "web") }
                if (!feed?.link.isNullOrBlank()) IconButton(onClick = {
                    IntentUtils.openInBrowser(requireContext(), feed!!.link!!)
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_web), contentDescription = "web") }
                if (feed != null) {
                    IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.refresh_label)) }, onClick = {
                            FeedUpdateManager.runOnceOrAsk(requireContext(), feed)
                            expanded = false
                        })
                        if (feed?.isPaged == true) DropdownMenuItem(text = { Text(stringResource(R.string.load_complete_feed)) }, onClick = {
                            Thread {
                                try {
                                    if (feed != null) {
                                        val feed_ = upsertBlk(feed!!) {
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
                            ShareUtils.shareFeedLinkNew(requireContext(), feed!!)
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.rename_feed_label)) }, onClick = {
                            showRenameDialog = true
                            expanded = false
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.remove_feed_label)) }, onClick = {
                            showRemoveFeedDialog = true
                            expanded = false
                        })
                    }
                }
            }
        )
    }

    private fun onPlayEvent(event: FlowEvent.PlayEvent) {
        if (feed != null) {
            val pos: Int = ieMap[event.episode.id] ?: -1
            if (pos >= 0) {
                if (!isFilteredOut(event.episode)) vms[pos].isPlayingState = event.isPlaying()
                if (event.isPlaying()) upsertBlk(feed!!) { it.lastPlayed = Date().time }
            }
        }
    }

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
//        Logd(TAG, "onEpisodeDownloadEvent() called with ${event.TAG}")
        if (loadItemsRunning) return
        if (feed == null || episodes.isEmpty()) return
        for (url in event.urls) {
//            if (!event.isCompleted(url)) continue
            val pos: Int = ueMap[url] ?: -1
            if (pos >= 0) {
                Logd(TAG, "onEpisodeDownloadEvent $pos ${event.map[url]?.state} ${episodes[pos].media?.downloaded} ${episodes[pos].title}")
                vms[pos].downloadState = event.map[url]?.state ?: DownloadStatus.State.UNKNOWN.ordinal
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
                    is FlowEvent.FeedPrefsChangeEvent -> if (feed?.id == event.feed.id) loadFeed()
                    is FlowEvent.PlayerSettingsEvent -> loadFeed()
                    is FlowEvent.FeedListEvent -> if (feed != null && event.contains(feed!!)) loadFeed()
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
        infoTextUpdate = if (event.isRunning) getString(R.string.refreshing_label) else ""
        infoBarText.value = "$infoTextFiltered $infoTextUpdate"
        if (!event.isRunning) loadFeed()
    }

    private fun refreshSwipeTelltale() {
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
        if (!feed?.preferences?.filterString.isNullOrEmpty()) {
            val filter: EpisodeFilter = feed!!.episodeFilter
            if (filter.properties.isNotEmpty()) infoTextFiltered = this.getString(R.string.filtered_label)
        }
        infoBarText.value = "$infoTextFiltered $infoTextUpdate"
    }

    private fun isFilteredOut(episode: Episode): Boolean {
        if (enableFilter && !feed?.preferences?.filterString.isNullOrEmpty()) {
            val episodes_ = realm.query(Episode::class).query("feedId == ${feed!!.id}").query(feed!!.episodeFilter.queryString()).find()
            if (!episodes_.contains(episode)) {
                episodes.remove(episode)
                ieMap = episodes.withIndex().associate { (index, episode) -> episode.id to index }
                ueMap = episodes.mapIndexedNotNull { index, episode_ -> episode_.media?.downloadUrl?.let { it to index } }.toMap()
                return true
            }
            return false
        }
        return false
    }

    private var loadItemsRunning = false
    private fun loadFeed() {
        if (!loadItemsRunning) {
            loadItemsRunning = true
            lifecycleScope.launch {
                try {
                    feed = withContext(Dispatchers.IO) {
                        val feed_ = getFeed(feedID)
                        if (feed_ != null) {
                            layoutMode = if (feed_.preferences?.useWideLayout == true) 1 else 0
                            Logd(TAG, "loadItems feed_.episodes.size: ${feed_.episodes.size}")
                            val etmp = mutableListOf<Episode>()
                            if (enableFilter && !feed_.preferences?.filterString.isNullOrEmpty()) {
                                Logd(TAG, "episodeFilter: ${feed_.episodeFilter.queryString()}")
                                val episodes_ = realm.query(Episode::class).query("feedId == ${feed_.id}").query(feed_.episodeFilter.queryString()).find()
                                etmp.addAll(episodes_)
                            } else etmp.addAll(feed_.episodes)
                            sortOrder = feed_.sortOrder ?: EpisodeSortOrder.DATE_NEW_OLD
                            getPermutor(sortOrder).reorder(etmp)
                            episodes.clear()
                            episodes.addAll(etmp)
                            ieMap = episodes.withIndex().associate { (index, episode) -> episode.id to index }
                            ueMap = episodes.mapIndexedNotNull { index, episode -> episode.media?.downloadUrl?.let { it to index } }.toMap()
                            withContext(Dispatchers.Main) {
                                vms.clear()
                                for (e in etmp) { vms.add(EpisodeVM(e)) }
                            }
                            if (onInit) {
                                var hasNonMediaItems = false
                                for (item in episodes) {
                                    if (item.media == null) {
                                        hasNonMediaItems = true
                                        break
                                    }
                                }
                                if (hasNonMediaItems) {
                                    ioScope.launch {
                                        withContext(Dispatchers.IO) {
                                            if (!ttsReady) {
                                                initializeTTS(requireContext())
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
                } catch (e: Exception) { Log.e(TAG, Log.getStackTraceString(e))
                } finally { loadItemsRunning = false }
            }
        }
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
