package ac.mdiq.podcini.ui.fragment


import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.MultiSelectSpeedDialBinding
import ac.mdiq.podcini.databinding.SearchFragmentBinding
import ac.mdiq.podcini.net.discovery.CombinedSearcher
import ac.mdiq.podcini.storage.FeedSearcher
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.ui.actions.EpisodeMultiSelectActionHandler
import ac.mdiq.podcini.ui.actions.menuhandler.FeedItemMenuHandler
import ac.mdiq.podcini.ui.actions.menuhandler.FeedMenuHandler
import ac.mdiq.podcini.ui.actions.menuhandler.MenuItemUtils
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.adapter.EpisodeItemListAdapter
import ac.mdiq.podcini.ui.adapter.HorizontalFeedListAdapter
import ac.mdiq.podcini.ui.adapter.SelectableAdapter
import ac.mdiq.podcini.ui.utils.EmptyViewHandler
import ac.mdiq.podcini.ui.view.EpisodeItemListRecyclerView
import ac.mdiq.podcini.ui.utils.LiftOnScrollListener
import ac.mdiq.podcini.ui.view.viewholder.EpisodeItemViewHolder
import ac.mdiq.podcini.util.FeedItemUtil
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Pair
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.ProgressBar
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Performs a search operation on all feeds or one specific feed and displays the search result.
 */
@UnstableApi class SearchFragment : Fragment(), SelectableAdapter.OnSelectModeListener {
    private var _binding: SearchFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: EpisodeItemListAdapter
    private lateinit var adapterFeeds: HorizontalFeedListAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyViewHandler: EmptyViewHandler
    private lateinit var recyclerView: EpisodeItemListRecyclerView
    private lateinit var searchView: SearchView
    private lateinit var speedDialBinding: MultiSelectSpeedDialBinding
    private lateinit var chip: Chip
    private lateinit var automaticSearchDebouncer: Handler

    private var results: MutableList<FeedItem> = mutableListOf()
    private var currentPlaying: EpisodeItemViewHolder? = null

//    val scope = CoroutineScope(Dispatchers.Main)
//    private var disposable: Disposable? = null
    private var lastQueryChange: Long = 0
    private var isOtherViewInFoucus = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        automaticSearchDebouncer = Handler(Looper.getMainLooper())
    }

    override fun onStop() {
        super.onStop()
//        scope.cancel()
//        disposable?.dispose()
    }

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SearchFragmentBinding.inflate(inflater)

        Logd(TAG, "fragment onCreateView")
        setupToolbar(binding.toolbar)
        speedDialBinding = MultiSelectSpeedDialBinding.bind(binding.root)
        progressBar = binding.progressBar
        recyclerView = binding.recyclerView
        recyclerView.setRecycledViewPool((activity as MainActivity).recycledViewPool)
        registerForContextMenu(recyclerView)
        adapter = object : EpisodeItemListAdapter(activity as MainActivity) {
            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
                super.onCreateContextMenu(menu, v, menuInfo)
                if (!inActionMode()) menu.findItem(R.id.multi_select).setVisible(true)

                MenuItemUtils.setOnClickListeners(menu) { item: MenuItem -> this@SearchFragment.onContextItemSelected(item) }
            }
        }
        adapter.setOnSelectModeListener(this)
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(LiftOnScrollListener(binding.appbar))

        val recyclerViewFeeds = binding.recyclerViewFeeds
        val layoutManagerFeeds = LinearLayoutManager(activity)
        layoutManagerFeeds.orientation = RecyclerView.HORIZONTAL
        recyclerViewFeeds.layoutManager = layoutManagerFeeds
        adapterFeeds = object : HorizontalFeedListAdapter(activity as MainActivity) {
            override fun onCreateContextMenu(contextMenu: ContextMenu, view: View, contextMenuInfo: ContextMenu.ContextMenuInfo?) {
                super.onCreateContextMenu(contextMenu, view, contextMenuInfo)
                MenuItemUtils.setOnClickListeners(contextMenu) { item: MenuItem -> this@SearchFragment.onContextItemSelected(item) }
            }
        }
        recyclerViewFeeds.adapter = adapterFeeds

        emptyViewHandler = EmptyViewHandler(requireContext())
        emptyViewHandler.attachToRecyclerView(recyclerView)
        emptyViewHandler.setIcon(R.drawable.ic_search)
        emptyViewHandler.setTitle(R.string.search_status_no_results)
        emptyViewHandler.setMessage(R.string.type_to_search)

        chip = binding.feedTitleChip
        chip.setOnCloseIconClickListener {
            requireArguments().putLong(ARG_FEED, 0)
            searchWithProgressBar()
        }
        chip.visibility = if (requireArguments().getLong(ARG_FEED, 0) == 0L) View.GONE else View.VISIBLE
        chip.text = requireArguments().getString(ARG_FEED_NAME, "")
        if (requireArguments().getString(ARG_QUERY, null) != null) search()

        searchView.setOnQueryTextFocusChangeListener { view: View, hasFocus: Boolean ->
            if (hasFocus && !isOtherViewInFoucus) showInputMethod(view.findFocus())
        }
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    val imm = activity!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(recyclerView.windowToken, 0)
                }
            }
        })
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
            EpisodeMultiSelectActionHandler(activity as MainActivity, actionItem.id)
                .handleAction(adapter.selectedItems.filterIsInstance<FeedItem>())
            adapter.endSelectMode()
            true
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        
    }

    private fun setupToolbar(toolbar: MaterialToolbar) {
        toolbar.setTitle(R.string.search_label)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        toolbar.inflateMenu(R.menu.search)

        val item: MenuItem = toolbar.menu.findItem(R.id.action_search)
        item.expandActionView()
        searchView = item.actionView as SearchView
        searchView.queryHint = getString(R.string.search_label)
        searchView.setQuery(requireArguments().getString(ARG_QUERY), true)
        searchView.requestFocus()
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            @UnstableApi override fun onQueryTextSubmit(s: String): Boolean {
                searchView.clearFocus()
                searchWithProgressBar()
                return true
            }

            @UnstableApi override fun onQueryTextChange(s: String): Boolean {
                automaticSearchDebouncer.removeCallbacksAndMessages(null)
                if (s.isEmpty() || s.endsWith(" ") || (lastQueryChange != 0L && System.currentTimeMillis() > lastQueryChange + SEARCH_DEBOUNCE_INTERVAL)) {
                    search()
                } else {
                    // Don't search instantly with first symbol after some pause
                    automaticSearchDebouncer.postDelayed({ search(); lastQueryChange = 0 }, (SEARCH_DEBOUNCE_INTERVAL / 2).toLong())
                }
                lastQueryChange = System.currentTimeMillis()
                return false
            }
        })
        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                parentFragmentManager.popBackStack()
                return true
            }
        })
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val selectedFeedItem: Feed? = adapterFeeds.longPressedItem
        if (selectedFeedItem != null && FeedMenuHandler.onMenuItemClicked(this, item.itemId, selectedFeedItem) {}) return true

        val selectedItem: FeedItem? = adapter.longPressedItem
        if (selectedItem != null) {
            if (adapter.onContextItemSelected(item)) return true
            if (FeedItemMenuHandler.onMenuItemClicked(this, item.itemId, selectedItem)) return true
        }
        return super.onContextItemSelected(item)
    }

    private fun procFlowEvents() {
        lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: $event")
                when (event) {
                    is FlowEvent.FeedListUpdateEvent, is FlowEvent.UnreadItemsUpdateEvent, is FlowEvent.PlayerStatusEvent -> search()
                    is FlowEvent.FeedItemEvent -> onEventMainThread(event)
                    is FlowEvent.PlaybackPositionEvent -> onEventMainThread(event)
                    else -> {}
                }
            }
        }
        lifecycleScope.launch {
            EventFlow.stickyEvents.collectLatest { event ->
                when (event) {
                    is FlowEvent.EpisodeDownloadEvent -> onEventMainThread(event)
                    else -> {}
                }
            }
        }
    }

    fun onEventMainThread(event: FlowEvent.FeedItemEvent) {
        Logd(TAG, "onEventMainThread() called with: event = [$event]")

        var i = 0
        val size: Int = event.items.size
        while (i < size) {
            val item: FeedItem = event.items[i]
            val pos: Int = FeedItemUtil.indexOfItemWithId(results, item.id)
            if (pos >= 0) {
                results.removeAt(pos)
                results.add(pos, item)
                adapter.notifyItemChangedCompat(pos)
            }
            i++
        }
    }

    fun onEventMainThread(event: FlowEvent.EpisodeDownloadEvent) {
        for (downloadUrl in event.urls) {
            val pos: Int = FeedItemUtil.indexOfItemWithDownloadUrl(results, downloadUrl)
            if (pos >= 0) adapter.notifyItemChangedCompat(pos)
        }
    }

    fun onEventMainThread(event: FlowEvent.PlaybackPositionEvent) {
        if (currentPlaying != null && currentPlaying!!.isCurrentlyPlayingItem)
            currentPlaying!!.notifyPlaybackPositionUpdated(event)
        else {
            Logd(TAG, "onEventMainThread() search list")
            for (i in 0 until adapter.itemCount) {
                val holder: EpisodeItemViewHolder? = recyclerView.findViewHolderForAdapterPosition(i) as? EpisodeItemViewHolder
                if (holder != null && holder.isCurrentlyPlayingItem) {
                    currentPlaying = holder
                    holder.notifyPlaybackPositionUpdated(event)
                    break
                }
            }
        }
    }

    @UnstableApi private fun searchWithProgressBar() {
        progressBar.visibility = View.VISIBLE
        emptyViewHandler.hide()
        search()
    }

    @UnstableApi private fun search() {
//        disposable?.dispose()

        adapterFeeds.setEndButton(R.string.search_online) { this.searchOnline() }
        chip.visibility = if ((requireArguments().getLong(ARG_FEED, 0) == 0L)) View.GONE else View.VISIBLE
//        disposable = Observable.fromCallable { this.performSearch() }
//            .subscribeOn(Schedulers.io())
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribe({ results: Pair<List<FeedItem>?, List<Feed?>?> ->
//                progressBar.visibility = View.GONE
//                if (results.first != null) {
//                    this.results = results.first!!.toMutableList()
//                    adapter.updateItems(results.first!!)
//                }
//                if (requireArguments().getLong(ARG_FEED, 0) == 0L) {
//                    if (results.second != null) adapterFeeds.updateData(results.second!!.filterNotNull())
//                } else adapterFeeds.updateData(emptyList())
//
//                if (searchView.query.toString().isEmpty()) emptyViewHandler.setMessage(R.string.type_to_search)
//                else emptyViewHandler.setMessage(getString(R.string.no_results_for_query) + searchView.query)
//
//            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })

        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    performSearch()
                }
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (results.first != null) {
                        this@SearchFragment.results = results.first!!.toMutableList()
                        adapter.updateItems(results.first!!)
                    }
                    if (requireArguments().getLong(ARG_FEED, 0) == 0L) {
                        if (results.second != null) adapterFeeds.updateData(results.second!!.filterNotNull())
                    } else adapterFeeds.updateData(emptyList())

                    if (searchView.query.toString().isEmpty()) emptyViewHandler.setMessage(R.string.type_to_search)
                    else emptyViewHandler.setMessage(getString(R.string.no_results_for_query) + searchView.query)
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
    }

    @UnstableApi private fun performSearch(): Pair<List<FeedItem>?, List<Feed?>?> {
        val query = searchView.query.toString()
        if (query.isEmpty()) return Pair<List<FeedItem>?, List<Feed?>?>(emptyList(), emptyList<Feed>())

        val feed = requireArguments().getLong(ARG_FEED)
        val items: List<FeedItem> = FeedSearcher.searchFeedItems(query, feed)
        val feeds: List<Feed?> = FeedSearcher.searchFeeds(query)
        return Pair<List<FeedItem>?, List<Feed?>?>(items, feeds)
    }

    private fun showInputMethod(view: View) {
        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, 0)
    }

    @UnstableApi private fun searchOnline() {
        searchView.clearFocus()
        val inVal = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inVal.hideSoftInputFromWindow(searchView.windowToken, 0)
        val query = searchView.query.toString()
        if (query.matches("http[s]?://.*".toRegex())) {
            val fragment: Fragment = OnlineFeedViewFragment.newInstance(query)
            (activity as MainActivity).loadChildFragment(fragment)
            return
        }
        (activity as MainActivity).loadChildFragment(OnlineSearchFragment.newInstance(CombinedSearcher::class.java, query))
    }

    override fun onStartSelectMode() {
        searchViewFocusOff()
//        speedDialBinding.fabSD.removeActionItemById(R.id.remove_from_inbox_batch)
        speedDialBinding.fabSD.removeActionItemById(R.id.remove_from_queue_batch)
        speedDialBinding.fabSD.removeActionItemById(R.id.delete_batch)
        speedDialBinding.fabSD.visibility = View.VISIBLE
    }

    override fun onEndSelectMode() {
        speedDialBinding.fabSD.close()
        speedDialBinding.fabSD.visibility = View.GONE
        searchViewFocusOn()
    }

    private fun searchViewFocusOff() {
        isOtherViewInFoucus = true
        searchView.clearFocus()
    }

    private fun searchViewFocusOn() {
        isOtherViewInFoucus = false
        searchView.requestFocus()
    }

    companion object {
        private const val TAG = "SearchFragment"
        private const val ARG_QUERY = "query"
        private const val ARG_FEED = "feed"
        private const val ARG_FEED_NAME = "feedName"
        private const val SEARCH_DEBOUNCE_INTERVAL = 1500

        /**
         * Create a new SearchFragment that searches all feeds.
         */
        @JvmStatic
        fun newInstance(): SearchFragment {
            val fragment = SearchFragment()
            val args = Bundle()
            args.putLong(ARG_FEED, 0)
            fragment.arguments = args
            return fragment
        }

        /**
         * Create a new SearchFragment that searches all feeds with pre-defined query.
         */
        fun newInstance(query: String?): SearchFragment {
            val fragment = newInstance()
            fragment.requireArguments().putString(ARG_QUERY, query)
            return fragment
        }

        /**
         * Create a new SearchFragment that searches one specific feed.
         */
        fun newInstance(feed: Long, feedTitle: String?): SearchFragment {
            val fragment = newInstance()
            fragment.requireArguments().putLong(ARG_FEED, feed)
            fragment.requireArguments().putString(ARG_FEED_NAME, feedTitle)
            return fragment
        }
    }
}
