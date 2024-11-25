package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.ComposeFragmentBinding
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.storage.model.EpisodeSortOrder.Companion.fromCode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Rating
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
import android.view.*
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import org.apache.commons.lang3.StringUtils
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.Semaphore

class FeedEpisodesFragment : Fragment(), Toolbar.OnMenuItemClickListener {

    private var _binding: ComposeFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var swipeActions: SwipeActions

    private var infoBarText = mutableStateOf("")
    private var leftActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
    private var rightActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())

    private var infoTextFiltered = ""
    private var infoTextUpdate = ""
    private var displayUpArrow = false
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
    private var showNewSynthetic by mutableStateOf(false)
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
        _binding = ComposeFragmentBinding.inflate(inflater)

        sortOrder = feed?.sortOrder ?: EpisodeSortOrder.DATE_NEW_OLD
        binding.toolbar.inflateMenu(R.menu.feed_episodes)
        binding.toolbar.setOnMenuItemClickListener(this)
//        binding.toolbar.setOnLongClickListener {
//            binding.recyclerView.scrollToPosition(5)
//            binding.recyclerView.post { binding.recyclerView.smoothScrollToPosition(0) }
//            binding.appBar.setExpanded(true)
//            false
//        }
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

        (activity as MainActivity).setupToolbarToggle(binding.toolbar, displayUpArrow)
        updateToolbar()

        swipeActions = SwipeActions(this, TAG)
        fun filterClick() {
            if (enableFilter && feed != null) {
                showFilterDialog = true
//                val dialog = FeedEpisodeFilterDialog(feed)
//                dialog.filter = feed!!.episodeFilter
//                dialog.show(childFragmentManager, null)
            }
        }
        fun filterLongClick() {
            if (feed != null) {
                enableFilter = !enableFilter
                waitForLoading()
                loadItemsRunning = true
                val etmp = mutableListOf<Episode>()
                if (enableFilter) {
                    filterButtonColor.value = Color.White
                    val episodes_ = realm.query(Episode::class).query("feedId == ${feed!!.id}").query(feed!!.episodeFilter.queryString()).find()
//                    val episodes_ = feed!!.episodes.filter { feed!!.episodeFilter.matches(it) }
                    etmp.addAll(episodes_)
                } else {
                    filterButtonColor.value = Color.Red
                    etmp.addAll(feed!!.episodes)
                }
                val sortOrder = fromCode(feed?.preferences?.sortOrderCode ?: 0)
                if (sortOrder != null) getPermutor(sortOrder).reorder(etmp)
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
        binding.mainView.setContent {
            CustomTheme(requireContext()) {
                if (showRemoveFeedDialog) RemoveFeedDialog(listOf(feed!!), onDismissRequest = {showRemoveFeedDialog = false}) {
                    (activity as MainActivity).loadFragment(UserPreferences.defaultPage, null)
                    // Make sure fragment is hidden before actually starting to delete
                    requireActivity().supportFragmentManager.executePendingTransactions()
                }
                if (showFilterDialog) EpisodesFilterDialog(filter = feed!!.episodeFilter,
//                    filtersDisabled = mutableSetOf(EpisodeFilter.EpisodesFilterGroup.DOWNLOADED, EpisodeFilter.EpisodesFilterGroup.MEDIA),
                    onDismissRequest = { showFilterDialog = false } ) { filterValues ->
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
                if (showNewSynthetic) RenameOrCreateSyntheticFeed(feed) {showNewSynthetic = false}
                if (showSortDialog) EpisodeSortDialog(initOrder = sortOrder, onDismissRequest = {showSortDialog = false}) { sortOrder, _ ->
                    if (feed != null) {
                        Logd(TAG, "persist Episode SortOrder")
                        runOnIOScope {
                            val feed_ = realm.query(Feed::class, "id == ${feed!!.id}").first().find()
                            if (feed_ != null) upsert(feed_) { it.sortOrder = sortOrder }
                        }
                    }
                }
                Column {
                    FeedEpisodesHeader(activity = (activity as MainActivity), filterButColor = filterButtonColor.value, filterClickCB = {filterClick()}, filterLongClickCB = {filterLongClick()})
                    InforBar(infoBarText, leftAction = leftActionState, rightAction = rightActionState, actionConfig = { swipeActions.showDialog() })
                    EpisodeLazyColumn(activity as MainActivity, vms = vms, feed = feed, layoutMode = layoutMode,
                        refreshCB = { FeedUpdateManager.runOnceOrAsk(requireContext(), feed) },
                        leftSwipeCB = {
                            if (leftActionState.value is NoActionSwipeAction) swipeActions.showDialog()
                            else leftActionState.value.performAction(it, this@FeedEpisodesFragment, swipeActions.filter ?: EpisodeFilter())
                        },
                        rightSwipeCB = {
                            Logd(TAG, "rightActionState: ${rightActionState.value.getId()}")
                            if (rightActionState.value is NoActionSwipeAction) swipeActions.showDialog()
                            else rightActionState.value.performAction(it, this@FeedEpisodesFragment, swipeActions.filter ?: EpisodeFilter())
                        },
                    )
                }
            }
        }

        lifecycle.addObserver(swipeActions)
        refreshSwipeTelltale()

//        val iconTintManager: ToolbarIconTintManager = object : ToolbarIconTintManager(requireContext(), binding.toolbar, binding.collapsingToolbar) {
//            override fun doTint(themedContext: Context) {
//                binding.toolbar.menu.findItem(R.id.refresh_feed).setIcon(AppCompatResources.getDrawable(themedContext, R.drawable.ic_refresh))
//                binding.toolbar.menu.findItem(R.id.action_search).setIcon(AppCompatResources.getDrawable(themedContext, R.drawable.ic_search))
//            }
//        }
//        iconTintManager.updateTint()
//        binding.appBar.addOnOffsetChangedListener(iconTintManager)

//        binding.swipeRefresh.setDistanceToTriggerSync(resources.getInteger(R.integer.swipe_refresh_distance))
//        binding.swipeRefresh.setProgressViewEndTarget(false, 0)
//        binding.swipeRefresh.setOnRefreshListener {
//            FeedUpdateManager.runOnceOrAsk(requireContext(), feed)
//        }

        return binding.root
    }

    override fun onStart() {
        Logd(TAG, "onStart() called")
        super.onStart()
//        adapter.refreshFragPosCallback = ::refreshPosCallback
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
            val (bgImage, bgColor, controlRow, image1, image2, imgvCover, taColumn) = createRefs()
            AsyncImage(model = feed?.imageUrl?:"", contentDescription = "bgImage", contentScale = ContentScale.FillBounds,
                error = painterResource(R.drawable.teaser),
                modifier = Modifier.fillMaxSize().blur(radiusX = 15.dp, radiusY = 15.dp)
                    .constrainAs(bgImage) {
                    bottom.linkTo(parent.bottom)
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                })
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                .constrainAs(bgColor) {
                    bottom.linkTo(parent.bottom)
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                })
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                .constrainAs(controlRow) {
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                    width = Dimension.fillToConstraints
                }, verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(0.7f))
                val ratingIconRes = Rating.fromCode(rating).res
                Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                    modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(30.dp).height(30.dp).clickable(onClick = {
                        showChooseRatingDialog = true
                    }))
                Spacer(modifier = Modifier.weight(0.2f))
                Icon(imageVector = ImageVector.vectorResource(R.drawable.arrows_sort), tint = textColor, contentDescription = "butSort",
                    modifier = Modifier.width(40.dp).height(40.dp).padding(3.dp).clickable(onClick = {
                        showSortDialog = true
//                        SingleFeedSortDialog(feed).show(childFragmentManager, "SortDialog")
                    }))
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
//            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_rounded_corner_left), contentDescription = "left_corner",
//                Modifier.width(12.dp).height(12.dp).constrainAs(image1) {
//                    bottom.linkTo(parent.bottom)
//                    start.linkTo(parent.start)
//                })
//            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_rounded_corner_right), contentDescription = "right_corner",
//                Modifier.width(12.dp).height(12.dp).constrainAs(image2) {
//                    bottom.linkTo(parent.bottom)
//                    end.linkTo(parent.end)
//                })
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
        binding.toolbar.setOnMenuItemClickListener(null)
        binding.toolbar.setOnLongClickListener(null)
        _binding = null

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

//    
//    private fun refreshPosCallback(pos: Int, episode: Episode) {
//        Logd(TAG, "FeedEpisode refreshPosCallback: $pos ${episode.title}")
////        if (pos >= 0 && pos < episodes.size) episodes[pos] = episode
//        redoFilter()
////        adapter.notifyDataSetChanged()
//    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    private fun updateToolbar() {
        if (feed == null) return

        binding.toolbar.menu.findItem(R.id.visit_website_item).isVisible = feed!!.link != null
        binding.toolbar.menu.findItem(R.id.refresh_complete_item).isVisible = feed!!.isPaged
        if (StringUtils.isBlank(feed!!.link)) binding.toolbar.menu.findItem(R.id.visit_website_item).isVisible = false
        if (feed!!.isLocalFeed) binding.toolbar.menu.findItem(R.id.share_feed).isVisible = false
    }

//    override fun onConfigurationChanged(newConfig: Configuration) {
//        super.onConfigurationChanged(newConfig)
//        val horizontalSpacing = resources.getDimension(R.dimen.additional_horizontal_spacing).toInt()
////        binding.header.headerContainer.setPadding(horizontalSpacing, binding.header.headerContainer.paddingTop,
////            horizontalSpacing, binding.header.headerContainer.paddingBottom)
//    }

     override fun onMenuItemClick(item: MenuItem): Boolean {
        if (feed == null) {
            (activity as MainActivity).showSnackbarAbovePlayer(R.string.please_wait_for_data, Toast.LENGTH_LONG)
            return true
        }
        when (item.itemId) {
            R.id.visit_website_item -> if (feed!!.link != null) IntentUtils.openInBrowser(requireContext(), feed!!.link!!)
            R.id.share_feed -> ShareUtils.shareFeedLinkNew(requireContext(), feed!!)
            R.id.refresh_feed -> FeedUpdateManager.runOnceOrAsk(requireContext(), feed)
            R.id.refresh_complete_item -> {
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
            }
            R.id.rename_feed -> showNewSynthetic = true
            R.id.remove_feed -> showRemoveFeedDialog = true
            R.id.action_search -> (activity as MainActivity).loadChildFragment(SearchFragment.newInstance(feed!!.id, feed!!.title))
            R.id.open_queue -> {
                val qFrag = QueuesFragment()
                (activity as MainActivity).loadChildFragment(qFrag)
                (activity as MainActivity).bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
            }
//            R.id.init_tts -> initTTS()
            else -> return false
        }
        return true
    }

    private fun onPlayEvent(event: FlowEvent.PlayEvent) {
//        Logd(TAG, "onPlayEvent ${event.episode.title}")
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
//                episodes[pos].downloadState.value = event.map[url]?.state ?: DownloadStatus.State.UNKNOWN.ordinal
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
                Logd(TAG, "Received key event: $event")
                onKeyUp(event)
            }
        }
    }

    private fun onFeedUpdateRunningEvent(event: FlowEvent.FeedUpdatingEvent) {
//        nextPageLoader.setLoadingState(event.isRunning)
//        if (!event.isRunning) nextPageLoader.root.visibility = View.GONE
        infoTextUpdate = if (event.isRunning) getString(R.string.refreshing_label) else ""
        infoBarText.value = "$infoTextFiltered $infoTextUpdate"
        if (!event.isRunning) loadFeed()
//        binding.swipeRefresh.isRefreshing = event.isRunning
    }

    private fun refreshSwipeTelltale() {
        leftActionState.value = swipeActions.actions.left[0]
        rightActionState.value = swipeActions.actions.right[0]
    }

     private fun refreshHeaderView() {
        setupHeaderView()
        if (feed == null) {
            Log.e(TAG, "Unable to refresh header view")
            return
        }
//        loadFeedImage()
//        if (feed!!.lastUpdateFailed) binding.header.txtvFailure.visibility = View.VISIBLE
//        else binding.header.txtvFailure.visibility = View.GONE

        infoTextFiltered = ""
        if (!feed?.preferences?.filterString.isNullOrEmpty()) {
            val filter: EpisodeFilter = feed!!.episodeFilter
            if (filter.properties.isNotEmpty()) infoTextFiltered = this.getString(R.string.filtered_label)
        }
        infoBarText.value = "$infoTextFiltered $infoTextUpdate"
    }

     private fun setupHeaderView() {
        if (feed == null || headerCreated) return

//        binding.imgvBackground.colorFilter = LightingColorFilter(-0x99999a, 0x000000)
//        binding.header.imgvCover.setOnClickListener { showFeedInfo() }

//        binding.header.txtvFailure.setOnClickListener { showErrorDetails() }
        headerCreated = true
    }

//    private fun showErrorDetails() {
//        lifecycleScope.launch {
//            val downloadResult = withContext(Dispatchers.IO) {
//                val feedDownloadLog: List<DownloadResult> = getFeedDownloadLog(feedID)
//                if (feedDownloadLog.isEmpty() || feedDownloadLog[0].isSuccessful) null else feedDownloadLog[0]
//            }
//            withContext(Dispatchers.Main) {
//                if (downloadResult != null) DownloadLogDetailsDialog(requireContext(), downloadResult).show()
//                else DownloadLogFragment().show(childFragmentManager, null)
//            }
//        }.invokeOnCompletion { throwable ->
//            throwable?.printStackTrace()
//        }
//    }

    private var loadItemsRunning = false
    private fun waitForLoading() {
        while (loadItemsRunning) Thread.sleep(50)
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
                            val sortOrder = feed_.sortOrder
                            if (sortOrder != null) getPermutor(sortOrder).reorder(etmp)
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
                                if (hasNonMediaItems) initTTS()
                                onInit = false
                            }
                        }
                        feed_
                    }
                    withContext(Dispatchers.Main) {
                        Logd(TAG, "loadItems subscribe called ${feed?.title}")
                        rating = feed?.rating ?: Rating.UNRATED.code
                        swipeActions.setFilter(feed?.episodeFilter)
                        refreshHeaderView()
//                        if (feed != null) {
//                            adapter.updateItems(episodes, feed)
//                            binding.header.counts.text = episodes.size.toString()
//                        }
                        updateToolbar()
                    }
                } catch (e: Throwable) {
                    feed = null
                    refreshHeaderView()
                    updateToolbar()
                    Log.e(TAG, Log.getStackTraceString(e))
                } catch (e: Exception) { Log.e(TAG, Log.getStackTraceString(e))
                } finally { loadItemsRunning = false }
            }
        }
    }

    private fun initTTS() {
        ioScope.launch {
            withContext(Dispatchers.IO) {
                if (!ttsReady) {
                    initializeTTS(requireContext())
                    semaphore.acquire()
                }
            }
        }
    }

    private fun onKeyUp(event: KeyEvent) {
        if (!isAdded || !isVisible || !isMenuVisible) return
        when (event.keyCode) {
//            KeyEvent.KEYCODE_T -> binding.recyclerView.smoothScrollToPosition(0)
//            KeyEvent.KEYCODE_B -> binding.recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
            else -> {}
        }
    }

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
