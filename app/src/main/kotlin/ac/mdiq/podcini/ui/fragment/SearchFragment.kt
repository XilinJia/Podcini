package ac.mdiq.podcini.ui.fragment


import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.HorizontalFeedItemBinding
import ac.mdiq.podcini.databinding.SearchFragmentBinding
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.feed.discovery.CombinedSearcher
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.EpisodeUtil
import ac.mdiq.podcini.ui.actions.handler.MenuItemUtils
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.dialog.CustomFeedNameDialog
import ac.mdiq.podcini.ui.dialog.RemoveFeedDialog
import ac.mdiq.podcini.ui.dialog.TagSettingsDialog
import ac.mdiq.podcini.ui.utils.EmptyViewHandler
import ac.mdiq.podcini.ui.view.SquareImageView
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Pair
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import androidx.annotation.StringRes
import androidx.appcompat.widget.SearchView
import androidx.cardview.widget.CardView
import androidx.compose.runtime.mutableStateListOf
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Performs a search operation on all feeds or one specific feed and displays the search result.
 */
@UnstableApi
class SearchFragment : Fragment() {
    private var _binding: SearchFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapterFeeds: HorizontalFeedListAdapter
    private lateinit var emptyViewHandler: EmptyViewHandler
    private lateinit var searchView: SearchView
    private lateinit var chip: Chip
    private lateinit var automaticSearchDebouncer: Handler

    private val results = mutableStateListOf<Episode>()

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

        binding.lazyColumn.setContent {
            CustomTheme(requireContext()) {
                EpisodeLazyColumn(activity as MainActivity, episodes = results)
            }
        }

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
//        emptyViewHandler.attachToRecyclerView(recyclerView)
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
        Logd(TAG, "onDestroyView")
        _binding = null
        results.clear()
        super.onDestroyView()
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
                if (s.isEmpty() || s.endsWith(" ") || (lastQueryChange != 0L && System.currentTimeMillis() > lastQueryChange + SEARCH_DEBOUNCE_INTERVAL))
                    search()
                // Don't search instantly with first symbol after some pause
                else automaticSearchDebouncer.postDelayed({ search(); lastQueryChange = 0 }, (SEARCH_DEBOUNCE_INTERVAL / 2).toLong())
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
        if (selectedFeedItem != null && onMenuItemClicked(this, item.itemId, selectedFeedItem) {}) return true
        return super.onContextItemSelected(item)
    }

    private fun onMenuItemClicked(fragment: Fragment, menuItemId: Int, selectedFeed: Feed, callback: Runnable): Boolean {
        val context = fragment.requireContext()
        when (menuItemId) {
//            R.id.rename_folder_item -> CustomFeedNameDialog(fragment.activity as Activity, selectedFeed).show()
            R.id.edit_tags -> if (selectedFeed.preferences != null) TagSettingsDialog.newInstance(listOf(selectedFeed))
                .show(fragment.childFragmentManager, TagSettingsDialog.TAG)
            R.id.rename_item -> CustomFeedNameDialog(fragment.activity as Activity, selectedFeed).show()
            R.id.remove_feed -> RemoveFeedDialog.show(context, selectedFeed, null)
            else -> return false
        }
        return true
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

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
        for (url in event.urls) {
            val pos: Int = EpisodeUtil.indexOfItemWithDownloadUrl(results, url)
            if (pos >= 0) results[pos].downloadState.value = event.map[url]?.state ?: DownloadStatus.State.UNKNOWN.ordinal

        }
    }

    @UnstableApi private fun searchWithProgressBar() {
        emptyViewHandler.hide()
        search()
    }

    @SuppressLint("StringFormatMatches")
    @UnstableApi private fun search() {
        adapterFeeds.setEndButton(R.string.search_online) { this.searchOnline() }
        chip.visibility = if ((requireArguments().getLong(ARG_FEED, 0) == 0L)) View.GONE else View.VISIBLE

        lifecycleScope.launch {
            try {
                val results_ = withContext(Dispatchers.IO) { performSearch() }
                withContext(Dispatchers.Main) {
                    if (results_.first != null) {
                        val first_ = results_.first!!.toMutableList()
                        results.clear()
                        results.addAll(first_)
                    }
                    if (requireArguments().getLong(ARG_FEED, 0) == 0L) {
                        if (results_.second != null) adapterFeeds.updateData(results_.second!!)
                    } else adapterFeeds.updateData(emptyList())
                    if (searchView.query.toString().isEmpty()) emptyViewHandler.setMessage(R.string.type_to_search)
                    else emptyViewHandler.setMessage(getString(R.string.no_results_for_query, searchView.query))
                }
            } catch (e: Throwable) { Log.e(TAG, Log.getStackTraceString(e)) }
        }
    }

    @UnstableApi private fun performSearch(): Pair<List<Episode>, List<Feed>> {
        val query = searchView.query.toString()
        if (query.isEmpty()) return Pair<List<Episode>, List<Feed>>(emptyList(), emptyList())

        val feedID = requireArguments().getLong(ARG_FEED)
        val items: List<Episode> = searchEpisodes(feedID, query)
        val feeds: List<Feed> = searchFeeds(query)
        Logd(TAG, "performSearch items: ${items.size} feeds: ${feeds.size}")
        return Pair<List<Episode>, List<Feed>>(items, feeds)
    }

    private fun prepareFeedQueryString(query: String): String {
        val queryWords = query.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val sb = StringBuilder()
        for (i in queryWords.indices) {
            sb.append("(")
                .append("eigenTitle TEXT '${queryWords[i]}'")
                .append(" OR ")
                .append("customTitle TEXT '${queryWords[i]}'")
                .append(" OR ")
                .append("author TEXT '${queryWords[i]}'")
                .append(" OR ")
                .append("description TEXT '${queryWords[i]}'")
                .append(") ")
            if (i != queryWords.size - 1) sb.append("AND ")
        }
        return sb.toString()
    }

    private fun searchFeeds(query: String): List<Feed> {
        Logd(TAG, "searchFeeds called")
        val queryString = prepareFeedQueryString(query)
        Logd(TAG, "searchFeeds queryString: $queryString")
        return realm.query(Feed::class).query(queryString).find()
    }

    private fun prepareEpisodeQueryString(query: String): String {
        val queryWords = query.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val sb = StringBuilder()
        for (i in queryWords.indices) {
            sb.append("(")
                .append("description TEXT '${queryWords[i]}'")
                .append(" OR ")
                .append("title TEXT '${queryWords[i]}'" )
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
        var queryString = prepareEpisodeQueryString(query)
        if (feedID != 0L) queryString = "(feedId == $feedID) AND $queryString"
        Logd(TAG, "searchEpisodes queryString: $queryString")
        return realm.query(Episode::class).query(queryString).find()
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
            val fragment: Fragment = OnlineFeedFragment.newInstance(query)
            (activity as MainActivity).loadChildFragment(fragment)
            return
        }
        (activity as MainActivity).loadChildFragment(SearchResultsFragment.newInstance(CombinedSearcher::class.java, query))
    }

    open class HorizontalFeedListAdapter(mainActivity: MainActivity)
        : RecyclerView.Adapter<HorizontalFeedListAdapter.Holder>(), View.OnCreateContextMenuListener {

        private val mainActivityRef: WeakReference<MainActivity> = WeakReference<MainActivity>(mainActivity)
        private val data: MutableList<Feed> = ArrayList()
        private var dummyViews = 0
        var longPressedItem: Feed? = null
        @StringRes
        private var endButtonText = 0
        private var endButtonAction: Runnable? = null

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
            inflater.inflate(R.menu.feed_context, contextMenu)
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

        fun newInstance(query: String?): SearchFragment {
            val fragment = newInstance()
            fragment.requireArguments().putString(ARG_QUERY, query)
            return fragment
        }

        fun newInstance(feedId: Long, feedTitle: String?): SearchFragment {
            val fragment = newInstance()
            fragment.requireArguments().putLong(ARG_FEED, feedId)
            fragment.requireArguments().putString(ARG_FEED_NAME, feedTitle)
            return fragment
        }
    }
}
