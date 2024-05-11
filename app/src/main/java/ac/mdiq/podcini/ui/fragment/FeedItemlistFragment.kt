package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.FeedItemListFragmentBinding
import ac.mdiq.podcini.databinding.MultiSelectSpeedDialBinding
import ac.mdiq.podcini.feed.FeedEvent
import ac.mdiq.podcini.net.download.FeedUpdateManager
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.model.download.DownloadResult
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter
import ac.mdiq.podcini.storage.model.feed.SortOrder
import ac.mdiq.podcini.ui.actions.EpisodeMultiSelectActionHandler
import ac.mdiq.podcini.ui.actions.menuhandler.FeedItemMenuHandler
import ac.mdiq.podcini.ui.actions.menuhandler.MenuItemUtils
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.adapter.EpisodeItemListAdapter
import ac.mdiq.podcini.ui.adapter.SelectableAdapter
import ac.mdiq.podcini.ui.dialog.*
import ac.mdiq.podcini.ui.utils.MoreContentListFooterUtil
import ac.mdiq.podcini.ui.view.ToolbarIconTintManager
import ac.mdiq.podcini.ui.view.viewholder.EpisodeItemViewHolder
import ac.mdiq.podcini.util.*
import ac.mdiq.podcini.util.event.*
import ac.mdiq.podcini.util.event.playback.PlaybackPositionEvent
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.LightingColorFilter
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.snackbar.Snackbar
import com.joanzapata.iconify.Iconify
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.lang3.StringUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Semaphore

/**
 * Displays a list of FeedItems.
 */
class FeedItemlistFragment : Fragment(), AdapterView.OnItemClickListener, Toolbar.OnMenuItemClickListener,
    SelectableAdapter.OnSelectModeListener {

    private var _binding: FeedItemListFragmentBinding? = null
    private val binding get() = _binding!!
    private var _speedDialBinding: MultiSelectSpeedDialBinding? = null
    private val speedDialBinding get() = _speedDialBinding!!

    private lateinit var adapter: FeedItemListAdapter
    private lateinit var swipeActions: SwipeActions
    private lateinit var nextPageLoader: MoreContentListFooterUtil

    private var currentPlaying: EpisodeItemViewHolder? = null

    private var displayUpArrow = false
    private var headerCreated = false
    private var feedID: Long = 0
    private var feed: Feed? = null
    private var disposable: Disposable? = null

    private val ioScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args: Bundle? = arguments
        if (args != null) feedID = args.getLong(ARGUMENT_FEED_ID)
    }

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logd(TAG, "fragment onCreateView")

        _binding = FeedItemListFragmentBinding.inflate(inflater)
        _speedDialBinding = MultiSelectSpeedDialBinding.bind(binding.root)

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
        adapter = FeedItemListAdapter(activity as MainActivity)
        adapter.setOnSelectModeListener(this)
        binding.recyclerView.adapter = adapter

        swipeActions = SwipeActions(this, TAG).attachTo(binding.recyclerView)
        refreshSwipeTelltale()
        binding.header.leftActionIcon.setOnClickListener {
            swipeActions.showDialog()
        }
        binding.header.rightActionIcon.setOnClickListener {
            swipeActions.showDialog()
        }

        binding.progressBar.visibility = View.VISIBLE

        val iconTintManager: ToolbarIconTintManager = object : ToolbarIconTintManager(
            requireContext(), binding.toolbar, binding.collapsingToolbar) {
            override fun doTint(themedContext: Context) {
                binding.toolbar.menu.findItem(R.id.refresh_item)
                    .setIcon(AppCompatResources.getDrawable(themedContext, R.drawable.ic_refresh))
                binding.toolbar.menu.findItem(R.id.action_search)
                    .setIcon(AppCompatResources.getDrawable(themedContext, R.drawable.ic_search))
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

        EventBus.getDefault().register(this)

        binding.swipeRefresh.setDistanceToTriggerSync(resources.getInteger(R.integer.swipe_refresh_distance))
        binding.swipeRefresh.setOnRefreshListener {
            FeedUpdateManager.runOnceOrAsk(requireContext(), feed)
        }

        loadItems()

        // Init action UI (via a FAB Speed Dial)
        speedDialBinding.fabSD.overlayLayout = speedDialBinding.fabSDOverlay
        speedDialBinding.fabSD.inflate(R.menu.episodes_apply_action_speeddial)
        speedDialBinding.fabSD.setOnChangeListener(object : SpeedDialView.OnChangeListener {
            override fun onMainActionSelected(): Boolean {
                return false
            }

            override fun onToggleChanged(open: Boolean) {
                if (open && adapter.selectedCount == 0) {
                    (activity as MainActivity).showSnackbarAbovePlayer(R.string.no_items_selected, Snackbar.LENGTH_SHORT)
                    speedDialBinding.fabSD.close()
                }
            }
        })
        speedDialBinding.fabSD.setOnActionSelectedListener { actionItem: SpeedDialActionItem ->
            EpisodeMultiSelectActionHandler((activity as MainActivity), actionItem.id)
                .handleAction(adapter.selectedItems.filterIsInstance<FeedItem>())
            adapter.endSelectMode()
            true
        }
        return binding.root
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
        super.onDestroyView()
        _binding = null
        _speedDialBinding = null
        EventBus.getDefault().unregister(this)
        disposable?.dispose()
        adapter.endSelectMode()

        tts?.stop()
        tts?.shutdown()
        ttsWorking = false
        ttsReady = false
        tts = null
    }

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
                    feed!!.nextPageLink = feed!!.download_url
                    feed!!.pageNr = 0
                    try {
                        DBWriter.resetPagedFeedPage(feed).get()
                        FeedUpdateManager.runOnce(requireContext(), feed)
                    } catch (e: ExecutionException) {
                        throw RuntimeException(e)
                    } catch (e: InterruptedException) {
                        throw RuntimeException(e)
                    }
                }.start()
            }
            R.id.sort_items -> SingleFeedSortDialog.newInstance(feed!!).show(childFragmentManager, "SortDialog")
            R.id.rename_item -> RenameItemDialog(activity as Activity, feed).show()
            R.id.remove_feed -> {
                RemoveFeedDialog.show(requireContext(), feed!!) {
                    (activity as MainActivity).loadFragment(UserPreferences.defaultPage, null)
                    // Make sure fragment is hidden before actually starting to delete
                    requireActivity().supportFragmentManager.executePendingTransactions()
                }
            }
            R.id.action_search -> (activity as MainActivity).loadChildFragment(SearchFragment.newInstance(feed!!.id, feed!!.title))
            else -> return false
        }
        return true
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val selectedItem: FeedItem? = adapter.longPressedItem
        if (selectedItem == null) {
            Log.i(TAG, "Selected item at current position was null, ignoring selection")
            return super.onContextItemSelected(item)
        }
        if (adapter.onContextItemSelected(item)) return true

        return FeedItemMenuHandler.onMenuItemClicked(this, item.itemId, selectedItem)
    }

    @UnstableApi override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
        val activity: MainActivity = activity as MainActivity
        if (feed != null) {
//            val ids: LongArray = FeedItemUtil.getIds(feed!!.items)
            activity.loadChildFragment(EpisodeInfoFragment.newInstance(feed!!.items[position]))
        }
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: FeedEvent) {
        Logd(TAG, "onEvent() called with: event = [$event]")
        if (event.feedId == feedID) loadItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        Logd(TAG, "onEventMainThread() called with FeedItemEvent event = [$event]")
        if (feed == null || feed!!.items.isEmpty()) return

        var i = 0
        val size: Int = event.items.size
        while (i < size) {
            val item: FeedItem = event.items[i]
            val pos: Int = FeedItemUtil.indexOfItemWithId(feed!!.items, item.id)
            if (pos >= 0) {
                feed?.items?.removeAt(pos)
                feed?.items?.add(pos, item)
                adapter.notifyItemChangedCompat(pos)
            }
            i++
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: EpisodeDownloadEvent) {
        Logd(TAG, "onEventMainThread() called with EpisodeDownloadEvent event = [$event]")
        if (feed == null || feed!!.items.isEmpty()) return

        for (downloadUrl in event.urls) {
            val pos: Int = FeedItemUtil.indexOfItemWithDownloadUrl(feed!!.items, downloadUrl)
            if (pos >= 0) adapter.notifyItemChangedCompat(pos)
        }
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
//        Log.d(TAG, "onEventMainThread() called with PlaybackPositionEvent event = [$event]")
        if (currentPlaying != null && currentPlaying!!.isCurrentlyPlayingItem) currentPlaying!!.notifyPlaybackPositionUpdated(event)
        else {
            Logd(TAG, "onEventMainThread() search list")
            for (i in 0 until adapter.itemCount) {
                val holder: EpisodeItemViewHolder? = binding.recyclerView.findViewHolderForAdapterPosition(i) as? EpisodeItemViewHolder
                if (holder != null && holder.isCurrentlyPlayingItem) {
                    currentPlaying = holder
                    holder.notifyPlaybackPositionUpdated(event)
                    break
                }
            }
        }
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun favoritesChanged(event: FavoritesEvent?) {
        Logd(TAG, "favoritesChanged called")
        updateUi()
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onQueueChanged(event: QueueEvent?) {
        Logd(TAG, "onQueueChanged called")
        updateUi()
    }

    override fun onStartSelectMode() {
        swipeActions.detach()
        if (feed != null && feed!!.isLocalFeed) speedDialBinding.fabSD.removeActionItemById(R.id.download_batch)

        if (feed?.link?.contains("youtube.com") == true) {
            speedDialBinding.fabSD.removeActionItemById(R.id.download_batch)
            speedDialBinding.fabSD.removeActionItemById(R.id.delete_batch)
            speedDialBinding.fabSD.removeActionItemById(R.id.add_to_queue_batch)
            speedDialBinding.fabSD.removeActionItemById(R.id.remove_from_queue_batch)
        }
        speedDialBinding.fabSD.visibility = View.VISIBLE
        updateToolbar()
    }

    override fun onEndSelectMode() {
        speedDialBinding.fabSD.close()
        speedDialBinding.fabSD.visibility = View.GONE
        swipeActions.attachTo(binding.recyclerView)
    }

    @UnstableApi private fun updateUi() {
        loadItems()
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusChanged(event: PlayerStatusEvent?) {
        Logd(TAG, "onPlayerStatusChanged called")
        updateUi()
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUnreadItemsChanged(event: UnreadItemsUpdateEvent?) {
        Logd(TAG, "onUnreadItemsChanged called")
        updateUi()
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: FeedListUpdateEvent) {
        if (feed != null && event.contains(feed!!)) {
            Logd(TAG, "onFeedListChanged called")
            updateUi()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSwipeActionsChanged(event: SwipeActionsChangedEvent?) {
        refreshSwipeTelltale()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedUpdateRunningEvent) {
        nextPageLoader.setLoadingState(event.isFeedUpdateRunning)
        if (!event.isFeedUpdateRunning) nextPageLoader.root.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = event.isFeedUpdateRunning
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
        if (feed!!.hasLastUpdateFailed()) binding.header.txtvFailure.visibility = View.VISIBLE
        else binding.header.txtvFailure.visibility = View.GONE

        if (feed!!.preferences != null && !feed!!.preferences!!.keepUpdated) {
            binding.header.txtvUpdatesDisabled.text = ("{md-pause-circle-outline} ${this.getString(R.string.updates_disabled_label)}")
            Iconify.addIcons(binding.header.txtvUpdatesDisabled)
            binding.header.txtvUpdatesDisabled.visibility = View.VISIBLE
        } else binding.header.txtvUpdatesDisabled.visibility = View.GONE

        binding.header.txtvTitle.text = feed!!.title
        binding.header.txtvAuthor.text = feed!!.author
        if (feed != null && feed!!.itemFilter != null) {
            val filter: FeedItemFilter? = feed!!.itemFilter
            if (filter != null && filter.values.isNotEmpty()) {
                binding.header.txtvInformation.text = ("{md-info-outline} " + this.getString(R.string.filtered_label))
                Iconify.addIcons(binding.header.txtvInformation)
                binding.header.txtvInformation.setOnClickListener {
                    FeedItemFilterDialog.newInstance(feed!!).show(childFragmentManager, null)
                }
                binding.header.txtvInformation.visibility = View.VISIBLE
            } else binding.header.txtvInformation.visibility = View.GONE
        } else binding.header.txtvInformation.visibility = View.GONE
    }

    @UnstableApi private fun setupHeaderView() {
        if (feed == null || headerCreated) return

        // https://github.com/bumptech/glide/issues/529
        binding.imgvBackground.colorFilter = LightingColorFilter(-0x99999a, 0x000000)
        binding.header.imgvCover.setOnClickListener { showFeedInfo() }
        binding.header.butShowSettings.setOnClickListener {
            if (feed != null) {
                val fragment = FeedSettingsFragment.newInstance(feed!!)
                (activity as MainActivity).loadChildFragment(fragment, TransitionEffect.SLIDE)
            }
        }
        binding.header.butFilter.setOnClickListener {
            if (feed != null) FeedItemFilterDialog.newInstance(feed!!).show(childFragmentManager, null)
        }
        binding.header.txtvFailure.setOnClickListener { showErrorDetails() }
        binding.header.counts.text = adapter.itemCount.toString()
        headerCreated = true
    }

    private fun showErrorDetails() {
        Maybe.fromCallable<DownloadResult>(
            Callable {
                val feedDownloadLog: List<DownloadResult> = DBReader.getFeedDownloadLog(feedID)
                if (feedDownloadLog.isEmpty() || feedDownloadLog[0].isSuccessful) return@Callable null
                feedDownloadLog[0]
            })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { downloadStatus: DownloadResult ->
                    DownloadLogDetailsDialog(requireContext(), downloadStatus).show()
                },
                { error: Throwable -> error.printStackTrace() },
                { DownloadLogFragment().show(childFragmentManager, null) })
    }

    @UnstableApi private fun showFeedInfo() {
        if (feed != null) {
            val fragment = FeedInfoFragment.newInstance(feed!!)
            (activity as MainActivity).loadChildFragment(fragment, TransitionEffect.SLIDE)
        }
    }

    private fun loadFeedImage() {
        if (!feed?.imageUrl.isNullOrBlank()) {
//            binding.imgvBackground.load(feed!!.imageUrl) {
//                placeholder(R.color.image_readability_tint)
//                error(R.color.image_readability_tint)
//            }
//            Glide.with(this)
//                .load(feed!!.imageUrl)
//                .apply(RequestOptions()
//                    .placeholder(R.color.image_readability_tint)
//                    .error(R.color.image_readability_tint)
//                    .transform(FastBlurTransformation())
//                    .dontAnimate())
//                .into(binding.imgvBackground)

            binding.header.imgvCover.load(feed!!.imageUrl) {
                placeholder(R.color.light_gray)
                error(R.mipmap.ic_launcher)
            }
        }
    }

    @UnstableApi private fun loadItems() {
        disposable?.dispose()
        disposable = Observable.fromCallable<Feed?> { this.loadData() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result: Feed? ->
                    feed = result
                    Logd(TAG, "loadItems subscribe called ${feed?.title}")
                    if (feed != null) {
                        var hasNonMediaItems = false
                        for (item in feed!!.items) {
                            if (item.media == null) {
                                hasNonMediaItems = true
                                break
                            }
                        }
                        if (hasNonMediaItems) {
                            ioScope.launch {
                                if (!ttsReady) {
                                    initializeTTS(requireContext())
                                    semaphore.acquire()
                                }
                            }
                        }
                    }
                    swipeActions.setFilter(feed?.itemFilter)
                    refreshHeaderView()
                    binding.progressBar.visibility = View.GONE
                    adapter.setDummyViews(0)
                    if (feed != null) adapter.updateItems(feed!!.items)
                    binding.header.counts.text = (feed?.items?.size?:0).toString()
                    updateToolbar()
                }, { error: Throwable? ->
                    feed = null
                    refreshHeaderView()
                    adapter.setDummyViews(0)
                    adapter.updateItems(emptyList())
                    updateToolbar()
                    Log.e(TAG, Log.getStackTraceString(error))
                })
    }

    private fun loadData(): Feed? {
        val feed: Feed = DBReader.getFeed(feedID, true) ?: return null
//        Log.d(TAG, "loadData got feed ${feed.title} with items: ${feed.items.size} ${if (feed.items.isNotEmpty()) feed.items[0].getPubDate() else ""}")
        if (feed.items.isNotEmpty()) {
            DBReader.loadAdditionalFeedItemListData(feed.items)
            if (feed.sortOrder != null) {
                val feedItems: MutableList<FeedItem> = feed.items
                FeedItemPermutors.getPermutor(feed.sortOrder!!).reorder(feedItems)
                feed.items = feedItems
            }
        }
        return feed
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onKeyUp(event: KeyEvent) {
        if (!isAdded || !isVisible || !isMenuVisible) return

        when (event.keyCode) {
            KeyEvent.KEYCODE_T -> binding.recyclerView.smoothScrollToPosition(0)
            KeyEvent.KEYCODE_B -> binding.recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
            else -> {}
        }
    }

    private inner class FeedItemListAdapter(mainActivity: MainActivity) : EpisodeItemListAdapter(mainActivity) {
        @UnstableApi override fun beforeBindViewHolder(holder: EpisodeItemViewHolder, pos: Int) {
//            holder.coverHolder.visibility = View.GONE
        }

        @UnstableApi override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
            super.onCreateContextMenu(menu, v, menuInfo)

            MenuItemUtils.setOnClickListeners(menu) { item: MenuItem ->
                this@FeedItemlistFragment.onContextItemSelected(item)
            }
        }
    }

    class SingleFeedSortDialog : ItemSortDialog() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            sortOrder = SortOrder.fromCodeString(arguments?.getString(ARG_SORT_ORDER))
        }

        override fun onAddItem(title: Int, ascending: SortOrder, descending: SortOrder, ascendingIsDefault: Boolean) {
            if (ascending == SortOrder.DATE_OLD_NEW || ascending == SortOrder.DURATION_SHORT_LONG || ascending == SortOrder.RANDOM
                    || ascending == SortOrder.EPISODE_TITLE_A_Z
                    || (requireArguments().getBoolean(ARG_FEED_IS_LOCAL) && ascending == SortOrder.EPISODE_FILENAME_A_Z)) {
                super.onAddItem(title, ascending, descending, ascendingIsDefault)
            }
        }

        @UnstableApi override fun onSelectionChanged() {
            super.onSelectionChanged()
            DBWriter.persistFeedItemSortOrder(requireArguments().getLong(ARG_FEED_ID), sortOrder)
        }

        companion object {
            private const val ARG_FEED_ID = "feedId"
            private const val ARG_FEED_IS_LOCAL = "isLocal"
            private const val ARG_SORT_ORDER = "sortOrder"

            fun newInstance(feed: Feed): SingleFeedSortDialog {
                val bundle = Bundle()
                bundle.putLong(ARG_FEED_ID, feed.id)
                bundle.putBoolean(ARG_FEED_IS_LOCAL, feed.isLocalFeed)
                if (feed.sortOrder == null) bundle.putString(ARG_SORT_ORDER, SortOrder.DATE_NEW_OLD.code.toString())
                else bundle.putString(ARG_SORT_ORDER, feed.sortOrder!!.code.toString())

                val dialog = SingleFeedSortDialog()
                dialog.arguments = bundle
                return dialog
            }
        }
    }

    companion object {
        const val TAG: String = "FeedItemlistFragment"
        private const val ARGUMENT_FEED_ID = "argument.ac.mdiq.podcini.feed_id"
        private const val KEY_UP_ARROW = "up_arrow"

        /**
         * Creates new ItemlistFragment which shows the Feeditems of a specific
         * feed. Sets 'showFeedtitle' to false
         *
         * @param feedId The id of the feed to show
         * @return the newly created instance of an ItemlistFragment
         */
        @JvmStatic
        fun newInstance(feedId: Long): FeedItemlistFragment {
            val i = FeedItemlistFragment()
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
