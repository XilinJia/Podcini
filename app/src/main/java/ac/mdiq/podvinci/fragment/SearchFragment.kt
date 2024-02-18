package ac.mdiq.podvinci.fragment


import ac.mdiq.podvinci.activity.MainActivity
import android.content.Context
import android.content.Intent
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
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.activity.OnlineFeedViewActivity
import ac.mdiq.podvinci.adapter.EpisodeItemListAdapter
import ac.mdiq.podvinci.adapter.HorizontalFeedListAdapter
import ac.mdiq.podvinci.adapter.SelectableAdapter
import ac.mdiq.podvinci.core.menuhandler.MenuItemUtils
import ac.mdiq.podvinci.core.storage.FeedSearcher
import ac.mdiq.podvinci.core.util.FeedItemUtil
import ac.mdiq.podvinci.databinding.MultiSelectSpeedDialBinding
import ac.mdiq.podvinci.event.*
import ac.mdiq.podvinci.event.playback.PlaybackPositionEvent
import ac.mdiq.podvinci.fragment.actions.EpisodeMultiSelectActionHandler
import ac.mdiq.podvinci.menuhandler.FeedItemMenuHandler
import ac.mdiq.podvinci.menuhandler.FeedMenuHandler
import ac.mdiq.podvinci.model.feed.Feed
import ac.mdiq.podvinci.model.feed.FeedItem
import ac.mdiq.podvinci.net.discovery.CombinedSearcher
import ac.mdiq.podvinci.view.EmptyViewHandler
import ac.mdiq.podvinci.view.EpisodeItemListRecyclerView
import ac.mdiq.podvinci.view.LiftOnScrollListener
import ac.mdiq.podvinci.view.viewholder.EpisodeItemViewHolder
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Performs a search operation on all feeds or one specific feed and displays the search result.
 */
class SearchFragment : Fragment(), SelectableAdapter.OnSelectModeListener {
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

    private var disposable: Disposable? = null
    private var lastQueryChange: Long = 0
    private var isOtherViewInFoucus = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        automaticSearchDebouncer = Handler(Looper.getMainLooper())
    }

    override fun onStop() {
        super.onStop()
        disposable?.dispose()
    }

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                           savedInstanceState: Bundle?
    ): View {
        val layout: View = inflater.inflate(R.layout.search_fragment, container, false)
        setupToolbar(layout.findViewById(R.id.toolbar))
        speedDialBinding = MultiSelectSpeedDialBinding.bind(layout)
        progressBar = layout.findViewById(R.id.progressBar)
        recyclerView = layout.findViewById(R.id.recyclerView)
        recyclerView.setRecycledViewPool((activity as MainActivity).recycledViewPool)
        registerForContextMenu(recyclerView)
        adapter = object : EpisodeItemListAdapter(activity as MainActivity) {
            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
                super.onCreateContextMenu(menu, v, menuInfo)
                if (!inActionMode()) {
                    menu.findItem(R.id.multi_select).setVisible(true)
                }
                MenuItemUtils.setOnClickListeners(menu
                ) { item: MenuItem -> this@SearchFragment.onContextItemSelected(item) }
            }
        }
        adapter.setOnSelectModeListener(this)
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(LiftOnScrollListener(layout.findViewById(R.id.appbar)))

        val recyclerViewFeeds = layout.findViewById<RecyclerView>(R.id.recyclerViewFeeds)
        val layoutManagerFeeds = LinearLayoutManager(activity)
        layoutManagerFeeds.orientation = RecyclerView.HORIZONTAL
        recyclerViewFeeds.layoutManager = layoutManagerFeeds
        adapterFeeds = object : HorizontalFeedListAdapter(activity as MainActivity) {
            override fun onCreateContextMenu(contextMenu: ContextMenu, view: View,
                                             contextMenuInfo: ContextMenu.ContextMenuInfo?
            ) {
                super.onCreateContextMenu(contextMenu, view, contextMenuInfo)
                MenuItemUtils.setOnClickListeners(contextMenu
                ) { item: MenuItem -> this@SearchFragment.onContextItemSelected(item) }
            }
        }
        recyclerViewFeeds.adapter = adapterFeeds

        emptyViewHandler = EmptyViewHandler(context)
        emptyViewHandler.attachToRecyclerView(recyclerView)
        emptyViewHandler.setIcon(R.drawable.ic_search)
        emptyViewHandler.setTitle(R.string.search_status_no_results)
        emptyViewHandler.setMessage(R.string.type_to_search)
        EventBus.getDefault().register(this)

        chip = layout.findViewById(R.id.feed_title_chip)
        chip.setOnCloseIconClickListener { v: View? ->
            requireArguments().putLong(ARG_FEED, 0)
            searchWithProgressBar()
        }
        chip.visibility = if ((requireArguments().getLong(ARG_FEED, 0) == 0L)) View.GONE else View.VISIBLE
        chip.text = requireArguments().getString(ARG_FEED_NAME, "")
        if (requireArguments().getString(ARG_QUERY, null) != null) {
            search()
        }
        searchView.setOnQueryTextFocusChangeListener { view: View, hasFocus: Boolean ->
            if (hasFocus && !isOtherViewInFoucus) {
                showInputMethod(view.findFocus())
            }
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

        return layout
    }

    override fun onDestroyView() {
        super.onDestroyView()
        EventBus.getDefault().unregister(this)
    }

    private fun setupToolbar(toolbar: MaterialToolbar) {
        toolbar.setTitle(R.string.search_label)
        toolbar.setNavigationOnClickListener { v: View? -> parentFragmentManager.popBackStack() }
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
                if (s.isEmpty() || s.endsWith(" ") || (lastQueryChange != 0L
                                && System.currentTimeMillis() > lastQueryChange + SEARCH_DEBOUNCE_INTERVAL)) {
                    search()
                } else {
                    automaticSearchDebouncer.postDelayed({
                        search()
                        lastQueryChange = 0 // Don't search instantly with first symbol after some pause
                    }, (SEARCH_DEBOUNCE_INTERVAL / 2).toLong())
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
        if (selectedFeedItem != null
                && FeedMenuHandler.onMenuItemClicked(this, item.itemId, selectedFeedItem) {}) {
            return true
        }
        val selectedItem: FeedItem? = adapter.longPressedItem
        if (selectedItem != null) {
            if (adapter.onContextItemSelected(item)) {
                return true
            }
            if (FeedItemMenuHandler.onMenuItemClicked(this, item.itemId, selectedItem)) {
                return true
            }
        }
        return super.onContextItemSelected(item)
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFeedListChanged(event: FeedListUpdateEvent?) {
        search()
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUnreadItemsChanged(event: UnreadItemsUpdateEvent?) {
        search()
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        Log.d(TAG, "onEventMainThread() called with: event = [$event]")

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

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: EpisodeDownloadEvent) {
        for (downloadUrl in event.urls) {
            val pos: Int = FeedItemUtil.indexOfItemWithDownloadUrl(results, downloadUrl)
            if (pos >= 0) {
                adapter.notifyItemChangedCompat(pos)
            }
        }
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        for (i in 0 until adapter.itemCount) {
            val holder: EpisodeItemViewHolder? =
                recyclerView.findViewHolderForAdapterPosition(i) as? EpisodeItemViewHolder
            if (holder != null && holder.isCurrentlyPlayingItem) {
                holder.notifyPlaybackPositionUpdated(event)
                break
            }
        }
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusChanged(event: PlayerStatusEvent?) {
        search()
    }

    @UnstableApi private fun searchWithProgressBar() {
        progressBar.visibility = View.VISIBLE
        emptyViewHandler.hide()
        search()
    }

    @UnstableApi private fun search() {
        disposable?.dispose()

        adapterFeeds.setEndButton(R.string.search_online) { this.searchOnline() }
        chip.visibility = if ((requireArguments().getLong(ARG_FEED, 0) == 0L)) View.GONE else View.VISIBLE
        disposable = Observable.fromCallable { this.performSearch() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ results: Pair<List<FeedItem>?, List<Feed?>?> ->
                progressBar.visibility = View.GONE
                if (results.first != null) {
                    this.results = results.first!!.toMutableList()
                    adapter.updateItems(results.first!!)
                }
                if (requireArguments().getLong(ARG_FEED, 0) == 0L) {
                    if (results.second != null) adapterFeeds.updateData(results.second!!.filterNotNull())
                } else {
                    adapterFeeds.updateData(emptyList())
                }
                if (searchView.query.toString().isEmpty()) {
                    emptyViewHandler.setMessage(R.string.type_to_search)
                } else {
                    emptyViewHandler.setMessage(getString(R.string.no_results_for_query) + searchView.query)
                }
            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    @UnstableApi private fun performSearch(): Pair<List<FeedItem>?, List<Feed?>?> {
        val query = searchView.query.toString()
        if (query.isEmpty()) {
            return Pair<List<FeedItem>?, List<Feed?>?>(emptyList(), emptyList<Feed>())
        }
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
            val intent = Intent(activity, OnlineFeedViewActivity::class.java)
            intent.putExtra(OnlineFeedViewActivity.ARG_FEEDURL, query)
            startActivity(intent)
            return
        }
        (activity as MainActivity).loadChildFragment(
            OnlineSearchFragment.newInstance(CombinedSearcher::class.java, query))
    }

    override fun onStartSelectMode() {
        searchViewFocusOff()
        speedDialBinding.fabSD.removeActionItemById(R.id.remove_from_inbox_batch)
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
