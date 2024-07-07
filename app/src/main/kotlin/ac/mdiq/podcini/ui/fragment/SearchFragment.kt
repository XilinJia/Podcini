package ac.mdiq.podcini.ui.fragment


import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.HorizontalFeedItemBinding
import ac.mdiq.podcini.databinding.MultiSelectSpeedDialBinding
import ac.mdiq.podcini.databinding.SearchFragmentBinding
import ac.mdiq.podcini.net.feed.discovery.CombinedSearcher
import ac.mdiq.podcini.playback.base.InTheatre.isCurMedia
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.EpisodeUtil
import ac.mdiq.podcini.ui.actions.EpisodeMultiSelectHandler
import ac.mdiq.podcini.ui.actions.menuhandler.EpisodeMenuHandler
import ac.mdiq.podcini.ui.actions.menuhandler.FeedMenuHandler
import ac.mdiq.podcini.ui.actions.menuhandler.MenuItemUtils
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.adapter.EpisodesAdapter
import ac.mdiq.podcini.ui.adapter.SelectableAdapter
import ac.mdiq.podcini.ui.utils.EmptyViewHandler
import ac.mdiq.podcini.ui.utils.LiftOnScrollListener
import ac.mdiq.podcini.ui.view.EpisodesRecyclerView
import ac.mdiq.podcini.ui.view.SquareImageView
import ac.mdiq.podcini.ui.view.viewholder.EpisodeViewHolder
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
import android.widget.Button
import android.widget.ProgressBar
import androidx.annotation.StringRes
import androidx.appcompat.widget.SearchView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.load
import coil.request.ImageRequest
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Performs a search operation on all feeds or one specific feed and displays the search result.
 */
@UnstableApi class SearchFragment : Fragment(), SelectableAdapter.OnSelectModeListener {
    private var _binding: SearchFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: EpisodesAdapter
    private lateinit var adapterFeeds: HorizontalFeedListAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyViewHandler: EmptyViewHandler
    private lateinit var recyclerView: EpisodesRecyclerView
    private lateinit var searchView: SearchView
    private lateinit var speedDialBinding: MultiSelectSpeedDialBinding
    private lateinit var chip: Chip
    private lateinit var automaticSearchDebouncer: Handler

    private var results: MutableList<Episode> = mutableListOf()
    private var curIndex = -1

    private var lastQueryChange: Long = 0
    private var isOtherViewInFoucus = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        automaticSearchDebouncer = Handler(Looper.getMainLooper())
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
        adapter = object : EpisodesAdapter(activity as MainActivity) {
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
            EpisodeMultiSelectHandler(activity as MainActivity, actionItem.id)
                .handleAction(adapter.selectedItems.filterIsInstance<Episode>())
            adapter.endSelectMode()
            true
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
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

        val selectedItem: Episode? = adapter.longPressedItem
        if (selectedItem != null) {
            if (adapter.onContextItemSelected(item)) return true
            if (EpisodeMenuHandler.onMenuItemClicked(this, item.itemId, selectedItem)) return true
        }
        return super.onContextItemSelected(item)
    }

    private var eventSink: Job?     = null
    private var eventStickySink: Job? = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
        eventStickySink?.cancel()
        eventStickySink = null
    }
    private fun procFlowEvents() {
        if (eventSink == null) eventSink = lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.FeedListEvent, is FlowEvent.EpisodePlayedEvent, is FlowEvent.PlayerSettingsEvent -> search()
                    is FlowEvent.EpisodeEvent -> onEpisodeEvent(event)
                    is FlowEvent.PlaybackPositionEvent -> onPlaybackPositionEvent(event)
                    else -> {}
                }
            }
        }
        if (eventStickySink == null) eventStickySink = lifecycleScope.launch {
            EventFlow.stickyEvents.collectLatest { event ->
                Logd(TAG, "Received sticky event: ${event.TAG}")
                when (event) {
                    is FlowEvent.EpisodeDownloadEvent -> onEpisodeDownloadEvent(event)
                    else -> {}
                }
            }
        }
    }

    private fun onEpisodeEvent(event: FlowEvent.EpisodeEvent) {
//        Logd(TAG, "onEventMainThread() called with ${event.TAG}")
        var i = 0
        val size: Int = event.episodes.size
        while (i < size) {
            val item: Episode = event.episodes[i]
            val pos: Int = EpisodeUtil.indexOfItemWithId(results, item.id)
            if (pos >= 0) {
                results.removeAt(pos)
                results.add(pos, item)
                adapter.notifyItemChangedCompat(pos)
            }
            i++
        }
    }

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
        for (downloadUrl in event.urls) {
            val pos: Int = EpisodeUtil.indexOfItemWithDownloadUrl(results, downloadUrl)
            if (pos >= 0) adapter.notifyItemChangedCompat(pos)
        }
    }

    private fun onPlaybackPositionEvent(event: FlowEvent.PlaybackPositionEvent) {
        val item = (event.media as? EpisodeMedia)?.episode ?: return
        val pos = if (curIndex in 0..<results.size && event.media.getIdentifier() == results[curIndex].media?.getIdentifier() && isCurMedia(results[curIndex].media))
            curIndex else EpisodeUtil.indexOfItemWithId(results, item.id)

        if (pos >= 0) {
            results[pos] = item
            curIndex = pos
            adapter.notifyItemChanged(pos, Bundle().apply { putString("PositionUpdate", "PlaybackPositionEvent") })
        }
    }


    @UnstableApi private fun searchWithProgressBar() {
        progressBar.visibility = View.VISIBLE
        emptyViewHandler.hide()
        search()
    }

    @UnstableApi private fun search() {
        adapterFeeds.setEndButton(R.string.search_online) { this.searchOnline() }
        chip.visibility = if ((requireArguments().getLong(ARG_FEED, 0) == 0L)) View.GONE else View.VISIBLE

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
                        if (results.second != null) adapterFeeds.updateData(results.second!!)
                    } else adapterFeeds.updateData(emptyList())

                    if (searchView.query.toString().isEmpty()) emptyViewHandler.setMessage(R.string.type_to_search)
                    else emptyViewHandler.setMessage(getString(R.string.no_results_for_query) + searchView.query)
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
    }

    @UnstableApi private fun performSearch(): Pair<List<Episode>, List<Feed>> {
        val query = searchView.query.toString()
        if (query.isEmpty()) return Pair<List<Episode>, List<Feed>>(emptyList(), emptyList())

        val feed = requireArguments().getLong(ARG_FEED)
        val items: List<Episode> = searchEpisodes(feed, query)
        val feeds: List<Feed> = searchFeeds(query)
        return Pair<List<Episode>, List<Feed>>(items, feeds)
    }

    private fun prepareFeedQueryString(query: String): String {
        val queryWords = query.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val sb = StringBuilder()
        for (i in queryWords.indices) {
            sb.append("(")
                .append("feedTitle TEXT ${queryWords[i]}")
                .append(" OR ")
                .append("customTitle TEXT ${queryWords[i]}")
                .append(" OR ")
                .append("author TEXT ${queryWords[i]}")
                .append(" OR ")
                .append("description TEXT ${queryWords[i]}")
                .append(") ")
            if (i != queryWords.size - 1) sb.append("AND ")
        }

        return sb.toString()
    }

    private fun searchFeeds(query: String): List<Feed> {
        Logd(TAG, "searchFeeds called")
        val queryString = prepareFeedQueryString(query)
        return realm.query(Feed::class).query(queryString).find()
    }

    private fun prepareEpisodeQueryString(query: String): String {
        val queryWords = query.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val sb = StringBuilder()
        for (i in queryWords.indices) {
            sb.append("(")
                .append("description TEXT ${queryWords[i]}")
                .append(" OR ")
                .append("title TEXT  ${queryWords[i]}" )
                .append(") ")
            if (i != queryWords.size - 1) sb.append("AND ")
        }
        return sb.toString()
    }

    /**
     * Searches the FeedItems of a specific Feed for a given string.
     * @param feedID  The id of the feed whose episodes should be searched.
     * @param query   The search string.
     * @return A FutureTask object that executes the search request
     * and returns the search result as a List of FeedItems.
     */
    private fun searchEpisodes(feedID: Long, query: String): List<Episode> {
        Logd(TAG, "searchEpisodes called")
        val queryString = prepareEpisodeQueryString(query)
        val idString = if (feedID != 0L) "(id = $feedID)" else ""
        return realm.query(Episode::class).query("$idString AND $queryString").find()
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

    open class HorizontalFeedListAdapter(mainActivity: MainActivity) :
        RecyclerView.Adapter<HorizontalFeedListAdapter.Holder>(), View.OnCreateContextMenuListener {

        private val mainActivityRef: WeakReference<MainActivity> = WeakReference<MainActivity>(mainActivity)
        private val data: MutableList<Feed> = ArrayList()
        private var dummyViews = 0
        var longPressedItem: Feed? = null

        @StringRes
        private var endButtonText = 0
        private var endButtonAction: Runnable? = null

        fun setDummyViews(dummyViews: Int) {
            this.dummyViews = dummyViews
        }

        fun updateData(newData: List<Feed>?) {
            data.clear()
            data.addAll(newData!!)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val convertView = View.inflate(mainActivityRef.get(), R.layout.horizontal_feed_item, null)
            return Holder(convertView)
        }

        @UnstableApi override fun onBindViewHolder(holder: Holder, position: Int) {
            if (position == itemCount - 1 && endButtonAction != null) {
                holder.cardView.visibility = View.GONE
                holder.actionButton.visibility = View.VISIBLE
                holder.actionButton.setText(endButtonText)
                holder.actionButton.setOnClickListener { endButtonAction!!.run() }
                return
            }
            holder.cardView.visibility = View.VISIBLE
            holder.actionButton.visibility = View.GONE
            if (position >= data.size) {
                holder.itemView.alpha = 0.1f
//            Glide.with(mainActivityRef.get()!!).clear(holder.imageView)
                val imageLoader = ImageLoader.Builder(mainActivityRef.get()!!).build()
                imageLoader.enqueue(ImageRequest.Builder(mainActivityRef.get()!!).data(null).target(holder.imageView).build())
                holder.imageView.setImageResource(R.color.medium_gray)
                return
            }

            holder.itemView.alpha = 1.0f
            val podcast: Feed = data[position]
            holder.imageView.setContentDescription(podcast.title)
            holder.imageView.setOnClickListener {
                mainActivityRef.get()?.loadChildFragment(FeedEpisodesFragment.newInstance(podcast.id))
            }

            holder.imageView.setOnCreateContextMenuListener(this)
            holder.imageView.setOnLongClickListener {
                val currentItemPosition = holder.bindingAdapterPosition
                longPressedItem = data[currentItemPosition]
                false
            }

            holder.imageView.load(podcast.imageUrl) {
                placeholder(R.color.light_gray)
                error(R.mipmap.ic_launcher)
            }
        }

        override fun getItemId(position: Int): Long {
            if (position >= data.size) return RecyclerView.NO_ID // Dummy views
            return data[position].id
        }

        override fun getItemCount(): Int {
            return dummyViews + data.size + (if ((endButtonAction == null)) 0 else 1)
        }

        override fun onCreateContextMenu(contextMenu: ContextMenu, view: View, contextMenuInfo: ContextMenu.ContextMenuInfo?) {
            val inflater: MenuInflater = mainActivityRef.get()!!.menuInflater
            if (longPressedItem == null) return
            inflater.inflate(R.menu.nav_feed_context, contextMenu)
            contextMenu.setHeaderTitle(longPressedItem!!.title)
        }

        fun setEndButton(@StringRes text: Int, action: Runnable?) {
            endButtonAction = action
            endButtonText = text
            notifyDataSetChanged()
        }

        class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val binding = HorizontalFeedItemBinding.bind(itemView)
            var imageView: SquareImageView = binding.discoveryCover
            var cardView: CardView
            var actionButton: Button

            init {
                imageView.setDirection(SquareImageView.DIRECTION_HEIGHT)
                actionButton = binding.actionButton
                cardView = binding.cardView
            }
        }
    }

    companion object {
        private val TAG: String = SearchFragment::class.simpleName ?: "Anonymous"
        private const val ARG_QUERY = "query"
        private const val ARG_FEED = "feed"
        private const val ARG_FEED_NAME = "feedName"
        private const val SEARCH_DEBOUNCE_INTERVAL = 1500

        /**
         * Create a new SearchFragment that searches all feeds.
         */
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
