package ac.mdiq.podcini.ui.fragment

//import ac.mdiq.podcini.databinding.MultiSelectSpeedDialBinding
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.FeedItemListFragmentBinding
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
import ac.mdiq.podcini.storage.utils.EpisodesPermutors.getPermutor
import ac.mdiq.podcini.ui.actions.SwipeAction
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.*
import ac.mdiq.podcini.ui.dialog.*
import ac.mdiq.podcini.ui.utils.TransitionEffect
import ac.mdiq.podcini.util.*
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.apache.commons.lang3.StringUtils
import java.util.concurrent.ExecutionException
import java.util.concurrent.Semaphore

/**
 * Displays a list of FeedItems.
 */
@UnstableApi class FeedEpisodesFragment : Fragment(), Toolbar.OnMenuItemClickListener {

    private var _binding: FeedItemListFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var swipeActions: SwipeActions

    private var infoBarText = mutableStateOf("")
    private var leftActionState = mutableStateOf<SwipeAction?>(null)
    private var rightActionState = mutableStateOf<SwipeAction?>(null)

    private var infoTextFiltered = ""
    private var infoTextUpdate = ""
    private var displayUpArrow = false
    private var headerCreated = false
    private var feedID: Long = 0
    private var feed by mutableStateOf<Feed?>(null)

    private val episodes = mutableListOf<Episode>()
    private val vms = mutableStateListOf<EpisodeVM>()

    private var ieMap: Map<Long, Int> = mapOf()
    private var ueMap: Map<String, Int> = mapOf()

    private var enableFilter: Boolean = true
    private var filterButColor = mutableStateOf(Color.White)

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var onInit: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args: Bundle? = arguments
        if (args != null) feedID = args.getLong(ARGUMENT_FEED_ID)
    }

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logd(TAG, "fragment onCreateView")

        _binding = FeedItemListFragmentBinding.inflate(inflater)

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
                val dialog = FeedEpisodeFilterDialog(feed)
                dialog.filter = feed!!.episodeFilter
                dialog.show(childFragmentManager, null)
            }
        }
        fun filterLongClick() {
            if (feed != null) {
                enableFilter = !enableFilter
                waitForLoading()
                loadItemsRunning = true
                val etmp = mutableListOf<Episode>()
                if (enableFilter) {
                    filterButColor.value = Color.White
                    val episodes_ = feed!!.episodes.filter { feed!!.episodeFilter.matches(it) }
                    etmp.addAll(episodes_)
                } else {
                    filterButColor.value = Color.Red
                    etmp.addAll(feed!!.episodes)
                }
                val sortOrder = fromCode(feed!!.preferences?.sortOrderCode ?: 0)
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
        binding.header.setContent {
            CustomTheme(requireContext()) {
                FeedEpisodesHeader(activity = (activity as MainActivity), feed = feed, filterButColor = filterButColor.value, filterClickCB = {filterClick()}, filterLongClickCB = {filterLongClick()})
            }
        }
        binding.lazyColumn.setContent {
            Column {
                InforBar(infoBarText, leftAction = leftActionState, rightAction = rightActionState, actionConfig = {swipeActions.showDialog()})
                CustomTheme(requireContext()) {
                    EpisodeLazyColumn(activity as MainActivity, vms = vms,
                        refreshCB = { FeedUpdateManager.runOnceOrAsk(requireContext(), feed) },
                        leftSwipeCB = {
                            if (leftActionState.value == null) swipeActions.showDialog()
                            else leftActionState.value?.performAction(it, this@FeedEpisodesFragment, swipeActions.filter ?: EpisodeFilter())
                        },
                        rightSwipeCB = {
                            if (rightActionState.value == null) swipeActions.showDialog()
                            else rightActionState.value?.performAction(it, this@FeedEpisodesFragment, swipeActions.filter ?: EpisodeFilter())
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

    @kotlin.OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun FeedEpisodesHeader(activity: MainActivity, feed: Feed?, filterButColor: Color, filterClickCB: ()->Unit, filterLongClickCB: ()->Unit) {
        val textColor = MaterialTheme.colorScheme.onSurface
        ConstraintLayout(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val (controlRow, image1, image2, imgvCover, taColumn) = createRefs()
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).background(colorResource(id = R.color.image_readability_tint))
                .constrainAs(controlRow) {
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                }, verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(1f))
                Image(painter = painterResource(R.drawable.ic_filter_white), colorFilter = ColorFilter.tint(filterButColor), contentDescription = "butFilter",
                    modifier = Modifier.width(40.dp).height(40.dp).padding(3.dp).combinedClickable(onClick = filterClickCB, onLongClick = filterLongClickCB))
                Spacer(modifier = Modifier.width(15.dp))
                Image(painter = painterResource(R.drawable.ic_settings_white), contentDescription = "butShowSettings",
                    Modifier.width(40.dp).height(40.dp).padding(3.dp).clickable(onClick = {
                        if (feed != null) {
                            val fragment = FeedSettingsFragment.newInstance(feed)
                            activity.loadChildFragment(fragment, TransitionEffect.SLIDE)
                        }
                    }))
                Spacer(modifier = Modifier.weight(1f))
                Text(feed?.episodes?.size?.toString()?:"", textAlign = TextAlign.Center, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            }
            Image(painter = painterResource(R.drawable.ic_rounded_corner_left), contentDescription = "left_corner",
                Modifier.width(12.dp).height(12.dp).constrainAs(image1) {
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                })
            Image(painter = painterResource(R.drawable.ic_rounded_corner_right), contentDescription = "right_corner",
                Modifier.width(12.dp).height(12.dp).constrainAs(image2) {
                    bottom.linkTo(parent.bottom)
                    end.linkTo(parent.end)
                })
            AsyncImage(model = feed?.imageUrl?:"", contentDescription = "imgvCover",
                Modifier.width(120.dp).height(120.dp).padding(start = 16.dp, end = 16.dp, bottom = 12.dp).constrainAs(imgvCover) {
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                }.clickable(onClick = {
                    if (feed != null) {
                        val fragment = FeedInfoFragment.newInstance(feed)
                        activity.loadChildFragment(fragment, TransitionEffect.SLIDE)
                    }
                }))
            Column(Modifier.constrainAs(taColumn) {
                top.linkTo(imgvCover.top)
                start.linkTo(imgvCover.end) }) {
                Text(feed?.title?:"", color = textColor, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(feed?.author?:"", color = textColor, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        requireActivity().runOnUiThread {
                        Toast.makeText(context, R.string.tts_init_failed, Toast.LENGTH_LONG).show()
                    }
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

//    @UnstableApi
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

        binding.toolbar.menu.findItem(R.id.visit_website_item).setVisible(feed!!.link != null)
        binding.toolbar.menu.findItem(R.id.refresh_complete_item).setVisible(feed!!.isPaged)
        if (StringUtils.isBlank(feed!!.link)) binding.toolbar.menu.findItem(R.id.visit_website_item).setVisible(false)
        if (feed!!.isLocalFeed) binding.toolbar.menu.findItem(R.id.share_feed).setVisible(false)
    }

//    override fun onConfigurationChanged(newConfig: Configuration) {
//        super.onConfigurationChanged(newConfig)
//        val horizontalSpacing = resources.getDimension(R.dimen.additional_horizontal_spacing).toInt()
////        binding.header.headerContainer.setPadding(horizontalSpacing, binding.header.headerContainer.paddingTop,
////            horizontalSpacing, binding.header.headerContainer.paddingBottom)
//    }

    @UnstableApi override fun onMenuItemClick(item: MenuItem): Boolean {
        if (feed == null) {
            (activity as MainActivity).showSnackbarAbovePlayer(R.string.please_wait_for_data, Toast.LENGTH_LONG)
            return true
        }
        when (item.itemId) {
            R.id.visit_website_item -> if (feed!!.link != null) IntentUtils.openInBrowser(requireContext(), feed!!.link!!)
            R.id.share_feed -> ShareUtils.shareFeedLink(requireContext(), feed!!)
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
            R.id.sort_items -> SingleFeedSortDialog(feed).show(childFragmentManager, "SortDialog")
            R.id.rename_feed -> CustomFeedNameDialog(activity as Activity, feed!!).show()
            R.id.remove_feed -> {
                RemoveFeedDialog.show(requireContext(), feed!!) {
                    (activity as MainActivity).loadFragment(UserPreferences.defaultPage, null)
                    // Make sure fragment is hidden before actually starting to delete
                    requireActivity().supportFragmentManager.executePendingTransactions()
                }
            }
            R.id.action_search -> (activity as MainActivity).loadChildFragment(SearchFragment.newInstance(feed!!.id, feed!!.title))
            R.id.switch_queue -> SwitchQueueDialog(activity as MainActivity).show()
            R.id.open_queue -> {
                val qFrag = QueuesFragment()
                (activity as MainActivity).loadChildFragment(qFrag)
                (activity as MainActivity).bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
            }
            else -> return false
        }
        return true
    }

    private fun onQueueEvent(event: FlowEvent.QueueEvent) {
        if (feed == null || episodes.isEmpty()) return
        var i = 0
        val size: Int = event.episodes.size
        while (i < size) {
            val item = event.episodes[i++]
            if (item.feedId != feed!!.id) continue
            val pos: Int = ieMap[item.id] ?: -1
            if (pos >= 0) {
//                episodes[pos].inQueueState.value = event.inQueue()
                queueChanged++
            }
            break
        }
    }

    private fun onPlayEvent(event: FlowEvent.PlayEvent) {
//        Logd(TAG, "onPlayEvent ${event.episode.title}")
        if (feed != null) {
            val pos: Int = ieMap[event.episode.id] ?: -1
            if (pos >= 0) {
                if (!filterOutEpisode(event.episode)) vms[pos].isPlayingState = event.isPlaying()
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
                    is FlowEvent.QueueEvent -> onQueueEvent(event)
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
//        binding.swipeRefresh.isRefreshing = event.isRunning
    }

    private fun refreshSwipeTelltale() {
        leftActionState.value = swipeActions.actions?.left
        rightActionState.value = swipeActions.actions?.right
    }

    @UnstableApi private fun refreshHeaderView() {
        setupHeaderView()
        if (feed == null) {
            Log.e(TAG, "Unable to refresh header view")
            return
        }
//        loadFeedImage()
//        if (feed!!.lastUpdateFailed) binding.header.txtvFailure.visibility = View.VISIBLE
//        else binding.header.txtvFailure.visibility = View.GONE

//        if (feed!!.preferences != null && !feed!!.preferences!!.keepUpdated) {
//            binding.header.txtvUpdatesDisabled.text = ("{gmo-pause_circle_outline} ${this.getString(R.string.updates_disabled_label)}")
//            binding.header.txtvUpdatesDisabled.visibility = View.VISIBLE
//        } else binding.header.txtvUpdatesDisabled.visibility = View.GONE

        infoTextFiltered = ""
        if (!feed?.preferences?.filterString.isNullOrEmpty()) {
            val filter: EpisodeFilter = feed!!.episodeFilter
            if (filter.values.isNotEmpty()) {
                infoTextFiltered = this.getString(R.string.filtered_label)
//                binding.header.txtvInformation.setOnClickListener {
//                    val dialog = FeedEpisodeFilterDialog(feed)
//                    dialog.filter = feed!!.episodeFilter
//                    dialog.show(childFragmentManager, null)
//                }
            }
        }
        infoBarText.value = "$infoTextFiltered $infoTextUpdate"
    }

    @UnstableApi private fun setupHeaderView() {
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

//    @UnstableApi private fun showFeedInfo() {
//        if (feed != null) {
//            val fragment = FeedInfoFragment.newInstance(feed!!)
//            (activity as MainActivity).loadChildFragment(fragment, TransitionEffect.SLIDE)
//        }
//    }

    private var loadItemsRunning = false
    private fun waitForLoading() {
        while (loadItemsRunning) Thread.sleep(50)
    }

    private fun filterOutEpisode(episode: Episode): Boolean {
        if (enableFilter && !feed?.preferences?.filterString.isNullOrEmpty() && !feed!!.episodeFilter.matches(episode)) {
            episodes.remove(episode)
            ieMap = episodes.withIndex().associate { (index, episode) -> episode.id to index }
            ueMap = episodes.mapIndexedNotNull { index, episode_ -> episode_.media?.downloadUrl?.let { it to index } }.toMap()
            return true
        }
        return false
    }

//    private fun redoFilter(list: List<Episode>? = null) {
//        if (enableFilter && !feed?.preferences?.filterString.isNullOrEmpty()) {
//            val episodes_ = list ?: episodes.toList()
//            episodes.clear()
//            episodes.addAll(episodes_.filter { feed!!.episodeFilter.matches(it) })
//            ieMap = episodes.withIndex().associate { (index, episode) -> episode.id to index }
//            ueMap = episodes.mapIndexedNotNull { index, episode -> episode.media?.downloadUrl?.let { it to index } }.toMap()
//        }
//    }

    @UnstableApi
    private fun loadFeed() {
        if (!loadItemsRunning) {
            loadItemsRunning = true
            lifecycleScope.launch {
                try {
                    feed = withContext(Dispatchers.IO) {
                        val feed_ = getFeed(feedID)
                        if (feed_ != null) {
                            Logd(TAG, "loadItems feed_.episodes.size: ${feed_.episodes.size}")
                            val etmp = mutableListOf<Episode>()
                            if (enableFilter && !feed_.preferences?.filterString.isNullOrEmpty()) {
                                val episodes_ = feed_.episodes.filter { feed_.episodeFilter.matches(it) }
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

    private fun onKeyUp(event: KeyEvent) {
        if (!isAdded || !isVisible || !isMenuVisible) return
        when (event.keyCode) {
//            KeyEvent.KEYCODE_T -> binding.recyclerView.smoothScrollToPosition(0)
//            KeyEvent.KEYCODE_B -> binding.recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
            else -> {}
        }
    }

    class FeedEpisodeFilterDialog(val feed: Feed?) : EpisodeFilterDialog() {
        @OptIn(UnstableApi::class) override fun onFilterChanged(newFilterValues: Set<String>) {
            if (feed != null) {
                Logd(TAG, "persist Episode Filter(): feedId = [$feed.id], filterValues = [$newFilterValues]")
                runOnIOScope {
                    val feed_ = realm.query(Feed::class, "id == ${feed.id}").first().find()
                    if (feed_ != null) upsert(feed_) { it.preferences?.filterString = newFilterValues.joinToString() }
                }
            }
        }
    }

    class SingleFeedSortDialog(val feed: Feed?) : EpisodeSortDialog() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            sortOrder = feed?.sortOrder ?: EpisodeSortOrder.DATE_NEW_OLD
        }
        override fun onAddItem(title: Int, ascending: EpisodeSortOrder, descending: EpisodeSortOrder, ascendingIsDefault: Boolean) {
            if (ascending == EpisodeSortOrder.DATE_OLD_NEW
                    || ascending == EpisodeSortOrder.PLAYED_DATE_OLD_NEW
                    || ascending == EpisodeSortOrder.DOWNLOAD_DATE_OLD_NEW
                    || ascending == EpisodeSortOrder.COMPLETED_DATE_OLD_NEW
                    || ascending == EpisodeSortOrder.DURATION_SHORT_LONG
                    || ascending == EpisodeSortOrder.RANDOM
                    || ascending == EpisodeSortOrder.EPISODE_TITLE_A_Z
                    || (feed?.isLocalFeed == true && ascending == EpisodeSortOrder.EPISODE_FILENAME_A_Z)) {
                super.onAddItem(title, ascending, descending, ascendingIsDefault)
            }
        }
        @UnstableApi override fun onSelectionChanged() {
            super.onSelectionChanged()
            if (feed != null) {
                Logd(TAG, "persist Episode SortOrder")
                runOnIOScope {
                    val feed_ = realm.query(Feed::class, "id == ${feed.id}").first().find()
                    if (feed_ != null) upsert(feed_) { it.sortOrder = sortOrder }
                }
            }
        }
    }

    companion object {
        val TAG = FeedEpisodesFragment::class.simpleName ?: "Anonymous"

        private const val ARGUMENT_FEED_ID = "argument.ac.mdiq.podcini.feed_id"
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
