package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.FeedItemListFragmentBinding
import ac.mdiq.podcini.databinding.MoreContentListFooterBinding
import ac.mdiq.podcini.databinding.MultiSelectSpeedDialBinding
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.database.LogsAndStats.getFeedDownloadLog
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.EpisodeSortOrder.Companion.fromCode
import ac.mdiq.podcini.storage.utils.EpisodeUtil
import ac.mdiq.podcini.storage.utils.EpisodesPermutors.getPermutor
import ac.mdiq.podcini.ui.actions.EpisodeMultiSelectHandler
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.adapter.EpisodesAdapter
import ac.mdiq.podcini.ui.adapter.SelectableAdapter
import ac.mdiq.podcini.ui.dialog.*
import ac.mdiq.podcini.ui.utils.ToolbarIconTintManager
import ac.mdiq.podcini.ui.utils.TransitionEffect
import ac.mdiq.podcini.util.IntentUtils
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.ShareUtils
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.apache.commons.lang3.StringUtils
import java.util.concurrent.ExecutionException
import java.util.concurrent.Semaphore

/**
 * Displays a list of FeedItems.
 */
@UnstableApi class FeedEpisodesFragment : Fragment(), Toolbar.OnMenuItemClickListener, SelectableAdapter.OnSelectModeListener {

    private var _binding: FeedItemListFragmentBinding? = null
    private val binding get() = _binding!!
    private var _dialBinding: MultiSelectSpeedDialBinding? = null
    private val dialBinding get() = _dialBinding!!

    private lateinit var adapter: EpisodesAdapter
    private lateinit var swipeActions: SwipeActions
    private lateinit var nextPageLoader: MoreContentListFooterUtil

    private var displayUpArrow = false
    private var headerCreated = false
    private var feedID: Long = 0
    private var feed: Feed? = null
    private var episodes: MutableList<Episode> = mutableListOf()

    private var enableFilter: Boolean = true

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
        _dialBinding = MultiSelectSpeedDialBinding.bind(binding.root)

        binding.toolbar.inflateMenu(R.menu.feedlist)
        binding.toolbar.setOnMenuItemClickListener(this)
        binding.toolbar.setOnLongClickListener {
            binding.recyclerView.scrollToPosition(5)
            binding.recyclerView.post { binding.recyclerView.smoothScrollToPosition(0) }
            binding.appBar.setExpanded(true)
            false
        }
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

        (activity as MainActivity).setupToolbarToggle(binding.toolbar, displayUpArrow)
        updateToolbar()

        binding.recyclerView.setRecycledViewPool((activity as MainActivity).recycledViewPool)
        adapter = EpisodesAdapter(activity as MainActivity) { pos, episode ->
            Logd(TAG, "FeedEpisode refreshPosCallback: $pos ${episode.title}")
//        if (pos >= 0 && pos < episodes.size) episodes[pos] = episode
            redoFilter()
//        adapter.notifyDataSetChanged()
        }
        adapter.setOnSelectModeListener(this)
        binding.recyclerView.adapter = adapter

        swipeActions = SwipeActions(this, TAG).attachTo(binding.recyclerView)
        lifecycle.addObserver(swipeActions)
        refreshSwipeTelltale()
        binding.header.leftActionIcon.setOnClickListener { swipeActions.showDialog() }
        binding.header.rightActionIcon.setOnClickListener { swipeActions.showDialog() }

        binding.progressBar.visibility = View.VISIBLE

        val iconTintManager: ToolbarIconTintManager = object : ToolbarIconTintManager(
            requireContext(), binding.toolbar, binding.collapsingToolbar) {
            override fun doTint(themedContext: Context) {
                binding.toolbar.menu.findItem(R.id.refresh_item).setIcon(AppCompatResources.getDrawable(themedContext, R.drawable.ic_refresh))
                binding.toolbar.menu.findItem(R.id.action_search).setIcon(AppCompatResources.getDrawable(themedContext, R.drawable.ic_search))
            }
        }
        iconTintManager.updateTint()
        binding.appBar.addOnOffsetChangedListener(iconTintManager)

        nextPageLoader = MoreContentListFooterUtil(binding.moreContent.moreContentListFooter)
        nextPageLoader.setClickListener(object : MoreContentListFooterUtil.Listener {
            override fun onClick() {
                if (feed != null) FeedUpdateManager.runOnce(requireContext(), feed, true)
            }
        })
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(view: RecyclerView, deltaX: Int, deltaY: Int) {
                super.onScrolled(view, deltaX, deltaY)
                val hasMorePages = feed != null && feed!!.isPaged && feed!!.nextPageLink != null
                val pageLoaderVisible = binding.recyclerView.isScrolledToBottom && hasMorePages
                nextPageLoader.root.visibility = if (pageLoaderVisible) View.VISIBLE else View.GONE
                binding.recyclerView.setPadding(binding.recyclerView.paddingLeft, 0, binding.recyclerView.paddingRight,
                    if (pageLoaderVisible) nextPageLoader.root.measuredHeight else 0)
            }
        })

        binding.swipeRefresh.setDistanceToTriggerSync(resources.getInteger(R.integer.swipe_refresh_distance))
        binding.swipeRefresh.setOnRefreshListener {
            FeedUpdateManager.runOnceOrAsk(requireContext(), feed)
        }

        // Init action UI (via a FAB Speed Dial)
        dialBinding.fabSD.overlayLayout = dialBinding.fabSDOverlay
        dialBinding.fabSD.inflate(R.menu.episodes_apply_action_speeddial)
        dialBinding.fabSD.setOnChangeListener(object : SpeedDialView.OnChangeListener {
            override fun onMainActionSelected(): Boolean {
                return false
            }
            override fun onToggleChanged(open: Boolean) {
                if (open && adapter.selectedCount == 0) {
                    (activity as MainActivity).showSnackbarAbovePlayer(R.string.no_items_selected, Snackbar.LENGTH_SHORT)
                    dialBinding.fabSD.close()
                }
            }
        })
        dialBinding.fabSD.setOnActionSelectedListener { actionItem: SpeedDialActionItem ->
            EpisodeMultiSelectHandler((activity as MainActivity), actionItem.id).handleAction(adapter.selectedItems.filterIsInstance<Episode>())
            adapter.endSelectMode()
            true
        }
//        loadItems()
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
//        adapter.refreshFragPosCallback = null
        cancelFlowEvents()
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
        _dialBinding = null

        ioScope.cancel()
        adapter.endSelectMode()
        adapter.clearData()

        feed = null
        episodes = mutableListOf()

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
        if (feed!!.isLocalFeed) binding.toolbar.menu.findItem(R.id.share_item).setVisible(false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val horizontalSpacing = resources.getDimension(R.dimen.additional_horizontal_spacing).toInt()
        binding.header.headerContainer.setPadding(horizontalSpacing, binding.header.headerContainer.paddingTop,
            horizontalSpacing, binding.header.headerContainer.paddingBottom)
    }

    @UnstableApi override fun onMenuItemClick(item: MenuItem): Boolean {
        if (feed == null) {
            (activity as MainActivity).showSnackbarAbovePlayer(R.string.please_wait_for_data, Toast.LENGTH_LONG)
            return true
        }
        when (item.itemId) {
            R.id.visit_website_item -> if (feed!!.link != null) IntentUtils.openInBrowser(requireContext(), feed!!.link!!)
            R.id.share_item -> ShareUtils.shareFeedLink(requireContext(), feed!!)
            R.id.refresh_item -> FeedUpdateManager.runOnceOrAsk(requireContext(), feed)
            R.id.refresh_complete_item -> {
                Thread {
                    try {
                        if (feed != null) {
                            val feed_ = upsertBlk(feed!!) {
                                it.nextPageLink = it.downloadUrl
                                it.pageNr = 0
                            }
//                            val feed_ = unmanaged(feed!!)
//                            feed_.nextPageLink = feed_.downloadUrl
//                            feed_.pageNr = 0
//                            upsertBlk(feed_) {}
                            FeedUpdateManager.runOnce(requireContext(), feed_)
                        }
                    } catch (e: ExecutionException) {
                        throw RuntimeException(e)
                    } catch (e: InterruptedException) {
                        throw RuntimeException(e)
                    }
                }.start()
            }
            R.id.sort_items -> SingleFeedSortDialog(feed).show(childFragmentManager, "SortDialog")
            R.id.rename_item -> CustomFeedNameDialog(activity as Activity, feed!!).show()
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
            adapter.notifyDataSetChanged()
            break
        }
    }

    private fun onPlayEvent(event: FlowEvent.PlayEvent) {
        Logd(TAG, "onPlayEvent ${event.episode.title}")
        if (feed != null) {
            val pos: Int = EpisodeUtil.indexOfItemWithId(episodes, event.episode.id)
            if (pos >= 0) {
                if (filterOutEpisode(event.episode)) adapter.updateItems(episodes)
                else adapter.notifyItemChangedCompat(pos)
            }
        }
    }

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
//        Logd(TAG, "onEpisodeDownloadEvent() called with ${event.TAG}")
        if (loadItemsRunning) return
        if (feed == null || episodes.isEmpty()) return
        for (downloadUrl in event.urls) {
            val pos: Int = EpisodeUtil.indexOfItemWithDownloadUrl(episodes, downloadUrl)
            if (pos >= 0) {
                Logd(TAG, "onEpisodeDownloadEvent $pos ${episodes[pos].title}")
                adapter.notifyItemChangedCompat(pos)
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

    override fun onStartSelectMode() {
        swipeActions.detach()
        if (feed != null && feed!!.isLocalFeed) dialBinding.fabSD.removeActionItemById(R.id.download_batch)

        if (feed?.link?.contains("youtube.com") == true) {
            dialBinding.fabSD.removeActionItemById(R.id.download_batch)
            dialBinding.fabSD.removeActionItemById(R.id.delete_batch)
            dialBinding.fabSD.removeActionItemById(R.id.add_to_queue_batch)
            dialBinding.fabSD.removeActionItemById(R.id.remove_from_queue_batch)
        }
        dialBinding.fabSD.visibility = View.VISIBLE
        updateToolbar()
    }

    override fun onEndSelectMode() {
        dialBinding.fabSD.close()
        dialBinding.fabSD.visibility = View.GONE
        swipeActions.attachTo(binding.recyclerView)
    }

    private fun onFeedUpdateRunningEvent(event: FlowEvent.FeedUpdatingEvent) {
        nextPageLoader.setLoadingState(event.isRunning)
        if (!event.isRunning) nextPageLoader.root.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = event.isRunning
    }

    private fun refreshSwipeTelltale() {
        if (swipeActions.actions?.left != null) binding.header.leftActionIcon.setImageResource(swipeActions.actions!!.left!!.getActionIcon())
        if (swipeActions.actions?.right != null) binding.header.rightActionIcon.setImageResource(swipeActions.actions!!.right!!.getActionIcon())
    }

    @UnstableApi private fun refreshHeaderView() {
        setupHeaderView()
        if (feed == null) {
            Log.e(TAG, "Unable to refresh header view")
            return
        }
        loadFeedImage()
        if (feed!!.lastUpdateFailed) binding.header.txtvFailure.visibility = View.VISIBLE
        else binding.header.txtvFailure.visibility = View.GONE

        if (feed!!.preferences != null && !feed!!.preferences!!.keepUpdated) {
            binding.header.txtvUpdatesDisabled.text = ("{gmo-pause_circle_outline} ${this.getString(R.string.updates_disabled_label)}")
            binding.header.txtvUpdatesDisabled.visibility = View.VISIBLE
        } else binding.header.txtvUpdatesDisabled.visibility = View.GONE

        binding.header.txtvTitle.text = feed!!.title
        binding.header.txtvAuthor.text = feed!!.author
        if (!feed?.preferences?.filterString.isNullOrEmpty()) {
            val filter: EpisodeFilter = feed!!.episodeFilter
            if (filter.values.isNotEmpty()) {
                binding.header.txtvInformation.text = ("{gmo-info} " + this.getString(R.string.filtered_label))
                binding.header.txtvInformation.setOnClickListener {
                    val dialog = FeedEpisodeFilterDialog(feed)
                    dialog.filter = feed!!.episodeFilter
                    dialog.show(childFragmentManager, null)
                }
                binding.header.txtvInformation.visibility = View.VISIBLE
            } else binding.header.txtvInformation.visibility = View.GONE
        } else binding.header.txtvInformation.visibility = View.GONE
    }

    @UnstableApi private fun setupHeaderView() {
        if (feed == null || headerCreated) return

//        binding.imgvBackground.colorFilter = LightingColorFilter(-0x99999a, 0x000000)
        binding.header.imgvCover.setOnClickListener { showFeedInfo() }
        binding.header.butShowSettings.setOnClickListener {
            if (feed != null) {
                val fragment = FeedSettingsFragment.newInstance(feed!!)
                (activity as MainActivity).loadChildFragment(fragment, TransitionEffect.SLIDE)
            }
        }
        binding.header.butFilter.setOnClickListener {
            if (enableFilter && feed != null) {
                val dialog = FeedEpisodeFilterDialog(feed)
                dialog.filter = feed!!.episodeFilter
                dialog.show(childFragmentManager, null)
            }
        }
        binding.header.butFilter.setOnLongClickListener {
            if (feed != null) {
                enableFilter = !enableFilter
                waitForLoading()
                loadItemsRunning = true
                episodes.clear()
                if (enableFilter) {
                    binding.header.butFilter.setColorFilter(Color.WHITE)
                    val episodes_ = feed!!.episodes.filter { feed!!.episodeFilter.matches(it) }
                    episodes.addAll(episodes_)
                } else {
                    binding.header.butFilter.setColorFilter(Color.RED)
                    episodes.addAll(feed!!.episodes)
                }
                val sortOrder = fromCode(feed!!.preferences?.sortOrderCode ?: 0)
                if (sortOrder != null) getPermutor(sortOrder).reorder(episodes)
                binding.header.counts.text = episodes.size.toString()
                adapter.updateItems(episodes, feed)
                loadItemsRunning = false
            }
            true
        }
        binding.header.txtvFailure.setOnClickListener { showErrorDetails() }
        binding.header.counts.text = adapter.itemCount.toString()
        headerCreated = true
    }

    private fun showErrorDetails() {
        lifecycleScope.launch {
            val downloadResult = withContext(Dispatchers.IO) {
                val feedDownloadLog: List<DownloadResult> = getFeedDownloadLog(feedID)
                if (feedDownloadLog.isEmpty() || feedDownloadLog[0].isSuccessful) null else feedDownloadLog[0]
            }
            withContext(Dispatchers.Main) {
                if (downloadResult != null) DownloadLogDetailsDialog(requireContext(), downloadResult).show()
                else DownloadLogFragment().show(childFragmentManager, null)
            }
        }.invokeOnCompletion { throwable ->
            throwable?.printStackTrace()
        }
    }

    @UnstableApi private fun showFeedInfo() {
        if (feed != null) {
            val fragment = FeedInfoFragment.newInstance(feed!!)
            (activity as MainActivity).loadChildFragment(fragment, TransitionEffect.SLIDE)
        }
    }

    private fun loadFeedImage() {
        if (!feed?.imageUrl.isNullOrBlank()) {
            binding.header.imgvCover.load(feed!!.imageUrl) {
                placeholder(R.color.light_gray)
                error(R.mipmap.ic_launcher)
            }
        }
    }

    private var loadItemsRunning = false
    private fun waitForLoading() {
        while (loadItemsRunning) Thread.sleep(50)
    }

    private fun filterOutEpisode(episode: Episode): Boolean {
        if (enableFilter && !feed?.preferences?.filterString.isNullOrEmpty() && !feed!!.episodeFilter.matches(episode)) {
            episodes.remove(episode)
            return true
        }
        return false
    }

    private fun redoFilter(list: List<Episode>? = null) {
        if (enableFilter && !feed?.preferences?.filterString.isNullOrEmpty()) {
            val episodes_ = list ?: episodes.toList()
            episodes.clear()
            episodes.addAll(episodes_.filter { feed!!.episodeFilter.matches(it) })
        }
    }

    @UnstableApi
    private fun loadFeed() {
        if (!loadItemsRunning) {
            loadItemsRunning = true
            adapter.updateItems(mutableListOf())
            lifecycleScope.launch {
                try {
                    feed = withContext(Dispatchers.IO) {
                        val feed_ = getFeed(feedID)
                        if (feed_ != null) {
                            Logd(TAG, "loadItems feed_.episodes.size: ${feed_.episodes.size}")
                            episodes.clear()
                            if (enableFilter && !feed_.preferences?.filterString.isNullOrEmpty()) {
                                val episodes_ = feed_.episodes.filter { feed_.episodeFilter.matches(it) }
                                episodes.addAll(episodes_)
                            } else episodes.addAll(feed_.episodes)
                            val sortOrder = feed_.sortOrder
                            if (sortOrder != null) getPermutor(sortOrder).reorder(episodes)
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
                        binding.progressBar.visibility = View.GONE
//                    adapter.setDummyViews(0)
                        if (feed != null) {
                            adapter.updateItems(episodes, feed)
                            binding.header.counts.text = episodes.size.toString()
                        }
                        updateToolbar()
                    }
                } catch (e: Throwable) {
                    feed = null
                    refreshHeaderView()
//                adapter.setDummyViews(0)
                    adapter.updateItems(mutableListOf())
                    updateToolbar()
                    Log.e(TAG, Log.getStackTraceString(e))
                } catch (e: Exception) {
                    Log.e(TAG, Log.getStackTraceString(e))
                } finally {
                    loadItemsRunning = false
                }
            }
        }
    }

    private fun onKeyUp(event: KeyEvent) {
        if (!isAdded || !isVisible || !isMenuVisible) return
        when (event.keyCode) {
            KeyEvent.KEYCODE_T -> binding.recyclerView.smoothScrollToPosition(0)
            KeyEvent.KEYCODE_B -> binding.recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
            else -> {}
        }
    }

//    private inner class FeedEpisodesAdapter(mainActivity: MainActivity) : EpisodesAdapter(mainActivity, ::refreshPosCallback) {
//        @UnstableApi override fun beforeBindViewHolder(holder: EpisodeViewHolder, pos: Int) {
////            holder.coverHolder.visibility = View.GONE
//        }
//    }

    class FeedEpisodeFilterDialog(val feed: Feed?) : EpisodeFilterDialog() {
        @OptIn(UnstableApi::class) override fun onFilterChanged(newFilterValues: Set<String>) {
            if (feed != null) {
                Logd(TAG, "persist Episode Filter(): feedId = [$feed.id], filterValues = [$newFilterValues]")
                runOnIOScope {
                    val feed_ = realm.query(Feed::class, "id == ${feed.id}").first().find()
                    if (feed_ != null) {
                        upsert(feed_) {
                            it.preferences?.filterString = newFilterValues.joinToString()
                        }
                    }
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
                    if (feed_ != null) {
                        upsert(feed_) {
                            it.sortOrder = sortOrder
                        }
                    }
                }
            }
        }
    }

    /**
     * Utility methods for the more_content_list_footer layout.
     */
    private class MoreContentListFooterUtil(val root: View) {
        private var loading = false
        private var listener: Listener? = null

        init {
            root.setOnClickListener {
                if (!loading) listener?.onClick()
            }
        }
        fun setLoadingState(newState: Boolean) {
            val binding = MoreContentListFooterBinding.bind(root)
            val imageView = binding.imgExpand
            val progressBar = binding.progBar
            if (newState) {
                imageView.visibility = View.GONE
                progressBar.visibility = View.VISIBLE
            } else {
                imageView.visibility = View.VISIBLE
                progressBar.visibility = View.GONE
            }
            loading = newState
        }
        fun setClickListener(l: Listener?) {
            listener = l
        }
        interface Listener {
            fun onClick()
        }
    }

    companion object {
        val TAG = FeedEpisodesFragment::class.simpleName ?: "Anonymous"

        private const val ARGUMENT_FEED_ID = "argument.ac.mdiq.podcini.feed_id"
        private const val KEY_UP_ARROW = "up_arrow"

        fun newInstance(feedId: Long): FeedEpisodesFragment {
            val i = FeedEpisodesFragment()
            val b = Bundle()
            b.putLong(ARGUMENT_FEED_ID, feedId)
            i.arguments = b
            return i
        }

        var tts: TextToSpeech? = null
        var ttsReady = false
        var ttsWorking = false
    }
}
