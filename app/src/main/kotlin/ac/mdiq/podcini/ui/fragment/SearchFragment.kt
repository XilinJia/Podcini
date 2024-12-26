package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.feed.searcher.CombinedSearcher
import ac.mdiq.podcini.storage.database.Episodes
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.PAFeed
import ac.mdiq.podcini.storage.model.Rating
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.ui.actions.SwipeAction
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.actions.SwipeActions.Companion.SwipeActionsSettingDialog
import ac.mdiq.podcini.ui.actions.SwipeActions.NoActionSwipeAction
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.*
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import kotlin.math.min

class SearchFragment : Fragment() {
    private lateinit var automaticSearchDebouncer: Handler

    private val pafeeds = mutableStateListOf<PAFeed>()

    private val feeds = mutableStateListOf<Feed>()
    private val episodes = mutableListOf<Episode>()
    private val vms = mutableStateListOf<EpisodeVM>()
    private var infoBarText = mutableStateOf("")
    private var searchInFeed by mutableStateOf(false)
    private var feedName by mutableStateOf("")
    private var queryText by mutableStateOf("")

    private var showSwipeActionsDialog by mutableStateOf(false)
    private lateinit var swipeActions: SwipeActions
    private var leftActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
    private var rightActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        automaticSearchDebouncer = Handler(Looper.getMainLooper())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logd(TAG, "fragment onCreateView")
        swipeActions = SwipeActions(this, TAG)
        leftActionState.value = swipeActions.actions.left[0]
        rightActionState.value = swipeActions.actions.right[0]
        lifecycle.addObserver(swipeActions)

        if (requireArguments().getLong(ARG_FEED, 0) > 0L) {
            searchInFeed = true
            feedName = requireArguments().getString(ARG_FEED_NAME, "")
        }
        val composeView = ComposeView(requireContext()).apply {
            setContent {
                CustomTheme(requireContext()) {
                    if (showSwipeActionsDialog) SwipeActionsSettingDialog(swipeActions, onDismissRequest = { showSwipeActionsDialog = false }) { actions ->
                        swipeActions.actions = actions
                        refreshSwipeTelltale()
                    }
                    swipeActions.ActionOptionsDialog()
                    val tabTitles = listOf(R.string.episodes_label, R.string.feeds, R.string.pafeeds)
                    val tabCounts = listOf<Int>(episodes.size, feeds.size, pafeeds.size)
                    val selectedTabIndex = remember { mutableIntStateOf(0) }
                    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
                        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                            if (searchInFeed) FilterChip(onClick = { }, label = { Text(feedName) }, selected = searchInFeed,
                                trailingIcon = {
                                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Close icon", modifier = Modifier.size(FilterChipDefaults.IconSize).clickable(onClick = {
                                        requireArguments().putLong(ARG_FEED, 0)
                                        searchInFeed = false
                                    }))
                                }
                            )
                            CriteriaList()
                            TabRow(modifier = Modifier.fillMaxWidth(), selectedTabIndex = selectedTabIndex.value, divider = {}, indicator = { tabPositions ->
                                Box(modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex.value]).height(4.dp).background(Color.Blue))
                            }) {
                                tabTitles.forEachIndexed { index, titleRes ->
                                    Tab(text = { Text(stringResource(titleRes)+"(${tabCounts[index]})") }, selected = selectedTabIndex.value == index, onClick = { selectedTabIndex.value = index })
                                }
                            }
                            when (selectedTabIndex.value) {
                                0 -> {
                                    InforBar(infoBarText, leftAction = leftActionState, rightAction = rightActionState, actionConfig = { showSwipeActionsDialog = true })
                                    EpisodeLazyColumn(activity as MainActivity, vms = vms, buildMoreItems = { buildMoreItems(it) },
                                        leftSwipeCB = {
                                            if (leftActionState.value is NoActionSwipeAction) showSwipeActionsDialog = true
                                            else leftActionState.value.performAction(it)
                                        },
                                        rightSwipeCB = {
                                            if (rightActionState.value is NoActionSwipeAction) showSwipeActionsDialog = true
                                            else rightActionState.value.performAction(it)
                                        },
                                    )
                                }
                                1 -> FeedsColumn()
                                2 -> PAFeedsColumn()
                            }
                        }
                    }
                }
            }
        }
        refreshSwipeTelltale()
        val arg = requireArguments().getString(ARG_QUERY, "")
        if (arg.isNotBlank()) search(arg)
        return composeView
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
        episodes.clear()
        feeds.clear()
        stopMonitor(vms)
        vms.clear()
        super.onDestroyView()
    }

    fun buildMoreItems(vms: MutableList<EpisodeVM>) {
        val nextItems = (vms.size until min(vms.size + 100, episodes.size)).map { EpisodeVM(episodes[it], TAG) }
        if (nextItems.isNotEmpty()) vms.addAll(nextItems)
    }

    private fun refreshSwipeTelltale() {
        leftActionState.value = swipeActions.actions.left[0]
        rightActionState.value = swipeActions.actions.right[0]
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        TopAppBar(title = { SearchBarRow(R.string.search_label) {
            queryText = it
            search(queryText)
        }},
            navigationIcon = { IconButton(onClick = { parentFragmentManager.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
        )
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
                    is FlowEvent.FeedListEvent, is FlowEvent.EpisodePlayedEvent -> search(queryText)
//                    is FlowEvent.SwipeActionsChangedEvent -> refreshSwipeTelltale()
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
            val pos: Int = Episodes.indexOfItemWithDownloadUrl(episodes, url)
            if (pos >= 0) vms[pos].downloadState = event.map[url]?.state ?: DownloadStatus.State.UNKNOWN.ordinal
        }
    }

    data class Triplet(val episodes: List<Episode>, val feeds: List<Feed>, val pafeeds: List<PAFeed>)

    private var searchJob: Job? = null
    @SuppressLint("StringFormatMatches")
    private fun search(query: String) {
        if (query.isBlank()) return
        if (searchJob != null) {
            searchJob?.cancel()
            stopMonitor(vms)
            vms.clear()
        }
        searchJob = lifecycleScope.launch {
            try {
                val results_ = withContext(Dispatchers.IO) {
                    if (query.isEmpty()) Triplet(listOf(), listOf(), listOf())
                    else {
                        val feedID = requireArguments().getLong(ARG_FEED)
                        val items: List<Episode> = searchEpisodes(feedID, query)
                        val feeds: List<Feed> = searchFeeds(query)
                        val pafeeds = searchPAFeeds(query)
                        Logd(TAG, "performSearch items: ${items.size} feeds: ${feeds.size} pafeeds: ${pafeeds.size}")
                        Triplet(items, feeds, pafeeds)
                    }
                }
                withContext(Dispatchers.Main) {
                    val first_ = results_.episodes
                    episodes.clear()
                    stopMonitor(vms)
                    vms.clear()
                    if (first_.isNotEmpty()) {
                        episodes.addAll(first_)
                        buildMoreItems(vms)
                    }
                    infoBarText.value = "${episodes.size} episodes"
                    if (requireArguments().getLong(ARG_FEED, 0) == 0L) {
                        feeds.clear()
                        if (results_.feeds.isNotEmpty()) feeds.addAll(results_.feeds)
                    } else feeds.clear()
                    pafeeds.clear()
                    if (results_.pafeeds.isNotEmpty()) pafeeds.addAll(results_.pafeeds)
                }
            } catch (e: Throwable) { Log.e(TAG, Log.getStackTraceString(e)) }
        }.apply { invokeOnCompletion { searchJob = null } }
    }

    enum class SearchBy(val nameRes: Int, var selected: Boolean = true) {
        TITLE(R.string.title),
        AUTHOR(R.string.author),
        DESCRIPTION(R.string.description_label),
        COMMENT(R.string.my_opinion_label),
    }

    @Composable
    fun CriteriaList() {
        val textColor = MaterialTheme.colorScheme.onSurface
        var showGrid by remember { mutableStateOf(false) }
        Column {
            Row {
                Button(onClick = {showGrid = !showGrid}) { Text(stringResource(R.string.show_criteria)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = { searchOnline() }) { Text(stringResource(R.string.search_online)) }
            }
            if (showGrid) NonlazyGrid(columns = 2, itemCount = SearchBy.entries.size) { index ->
                val c = SearchBy.entries[index]
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 10.dp)) {
                    var isChecked by remember { mutableStateOf(true) }
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { newValue ->
                            c.selected = newValue
                            isChecked = newValue
                        }
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(stringResource(c.nameRes), color = textColor)
                }
            }
        }
    }

    @Composable
    fun FeedsColumn() {
        val context = LocalContext.current
        val lazyListState = rememberLazyListState()
        LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(feeds, key = { _, feed -> feed.id }) { index, feed ->
                Row(Modifier.background(MaterialTheme.colorScheme.surface)) {
                    val imgLoc = remember(feed) { feed.imageUrl }
                    AsyncImage(model = ImageRequest.Builder(context).data(imgLoc)
                        .memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(),
                        contentDescription = "imgvCover",
                        placeholder = painterResource(R.mipmap.ic_launcher),
                        error = painterResource(R.mipmap.ic_launcher),
                        modifier = Modifier.width(80.dp).height(80.dp).clickable(onClick = {
                            Logd(TAG, "icon clicked!")
                            if (!feed.isBuilding) (activity as MainActivity).loadChildFragment(FeedInfoFragment.newInstance(feed))
                        })
                    )
                    val textColor = MaterialTheme.colorScheme.onSurface
                    Column(Modifier.weight(1f).padding(start = 10.dp).clickable(onClick = {
                        Logd(TAG, "clicked: ${feed.title}")
                        if (!feed.isBuilding) (activity as MainActivity).loadChildFragment(FeedEpisodesFragment.newInstance(feed.id))
                    })) {
                        Row {
                            if (feed.rating != Rating.UNRATED.code)
                                Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(feed.rating).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                                    modifier = Modifier.width(20.dp).height(20.dp).background(MaterialTheme.colorScheme.tertiaryContainer))
                            Text(feed.title ?: "No title", color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        Text(feed.author ?: "No author", color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.padding(top = 5.dp)) {
                            val measureString = remember {
                                NumberFormat.getInstance().format(feed.episodes.size.toLong()) + " : " +
                                        DurationConverter.shortLocalizedDuration(requireActivity(), feed.totleDuration / 1000)
                            }
                            Text(measureString, color = textColor, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            var feedSortInfo by remember { mutableStateOf(feed.sortInfo) }
                            Text(feedSortInfo, color = textColor, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    //                                TODO: need to use state
                    if (feed.lastUpdateFailed) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_error), tint = Color.Red, contentDescription = "error")
                }
            }
        }
    }

    @Composable
    fun PAFeedsColumn() {
        val context = LocalContext.current
        val lazyListState = rememberLazyListState()
        LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(pafeeds, key = { _, feed -> feed.id }) { index, feed ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val imgLoc = remember(feed) { feed.imageUrl }
                    AsyncImage(model = ImageRequest.Builder(context).data(imgLoc)
                        .memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(),
                        contentDescription = "imgvCover",
                        placeholder = painterResource(R.mipmap.ic_launcher),
                        error = painterResource(R.mipmap.ic_launcher),
                        modifier = Modifier.width(60.dp).height(60.dp).clickable(onClick = {
                            Logd(TAG, "feedUrl: ${feed.name} [${feed.feedUrl}] [$]")
                            if (feed.feedUrl.isNotBlank()) (activity as MainActivity).loadChildFragment(OnlineFeedFragment.newInstance(feed.feedUrl))
                        })
                    )
                    val textColor = MaterialTheme.colorScheme.onSurface
                    Column(Modifier.weight(1f).padding(start = 10.dp).clickable(onClick = {
                        Logd(TAG, "feedUrl: ${feed.name} [${feed.feedUrl}]")
                        if (feed.feedUrl.isNotBlank()) (activity as MainActivity).loadChildFragment(OnlineFeedFragment.newInstance(feed.feedUrl))
                    })) {
                        Text(feed.name, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text(feed.author, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text(feed.category.joinToString(","), color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Episodes: ${feed.episodesNb} Average duration: ${feed.aveDuration} minutes", color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(MiscFormatter.formatLargeInteger(feed.subscribers) + " subscribers", color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    private fun searchFeeds(query: String): List<Feed> {
        Logd(TAG, "searchFeeds called ${SearchBy.AUTHOR.selected}")
        val queryWords = query.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val sb = StringBuilder()
        for (i in queryWords.indices) {
            var isStart = true
            val sb1 = StringBuilder()
            if (SearchBy.TITLE.selected) {
                sb1.append("eigenTitle contains[c] '${queryWords[i]}'")
                sb1.append(" OR ")
                sb1.append("customTitle contains[c] '${queryWords[i]}'")
                isStart = false
            }
            if (SearchBy.AUTHOR.selected) {
                if (!isStart) sb1.append(" OR ")
                sb1.append("author contains[c] '${queryWords[i]}'")
                isStart = false
            }
            if (SearchBy.DESCRIPTION.selected) {
                if (!isStart) sb1.append(" OR ")
                sb1.append("description contains[c] '${queryWords[i]}'")
                isStart = false
            }
            if (SearchBy.COMMENT.selected) {
                if (!isStart) sb1.append(" OR ")
                sb1.append("comment contains[c] '${queryWords[i]}'")
            }
            if (sb1.isEmpty()) continue
            sb.append("(")
            sb.append(sb1)
            sb.append(") ")
            if (i != queryWords.size - 1) sb.append("AND ")
        }
        if (sb.isEmpty()) return listOf()
        val queryString = sb.toString()
        Logd(TAG, "searchFeeds queryString: $queryString")
        return realm.query(Feed::class).query(queryString).find()
    }

    private fun searchPAFeeds(query: String): List<PAFeed> {
        Logd(TAG, "searchFeeds called ${SearchBy.AUTHOR.selected}")
        val queryWords = query.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val sb = StringBuilder()
        for (i in queryWords.indices) {
            var isStart = true
            val sb1 = StringBuilder()
            if (SearchBy.TITLE.selected) {
                sb1.append("name contains[c] '${queryWords[i]}'")
                isStart = false
            }
            if (SearchBy.AUTHOR.selected) {
                if (!isStart) sb1.append(" OR ")
                sb1.append("author contains[c] '${queryWords[i]}'")
                isStart = false
            }
            if (SearchBy.DESCRIPTION.selected) {
                if (!isStart) sb1.append(" OR ")
                sb1.append("description contains[c] '${queryWords[i]}'")
                isStart = false
            }
//            if (SearchBy.COMMENT.selected) {
//                if (!isStart) sb1.append(" OR ")
//                sb1.append("comment contains[c] '${queryWords[i]}'")
//            }
            if (sb1.isEmpty()) continue
            sb.append("(")
            sb.append(sb1)
            sb.append(") ")
            if (i != queryWords.size - 1) sb.append("AND ")
        }
        if (sb.isEmpty()) return listOf()
        val queryString = sb.toString()
        Logd(TAG, "searchFeeds queryString: $queryString")
        return realm.query(PAFeed::class).query(queryString).find()
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
        val queryWords = query.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val sb = StringBuilder()
        for (i in queryWords.indices) {
            val sb1 = StringBuilder()
            var isStart = true
            if (SearchBy.TITLE.selected) {
                sb1.append("title contains[c] '${queryWords[i]}'" )
                isStart = false
            }
            if (SearchBy.DESCRIPTION.selected) {
                if (!isStart) sb1.append(" OR ")
                sb1.append("description contains[c] '${queryWords[i]}'")
                sb1.append(" OR ")
                sb1.append("transcript contains[c] '${queryWords[i]}'")
                isStart = false
            }
            if (SearchBy.COMMENT.selected) {
                if (!isStart) sb1.append(" OR ")
                sb1.append("comment contains[c] '${queryWords[i]}'")
            }
            if (sb1.isEmpty()) continue
            sb.append("(")
            sb.append(sb1)
            sb.append(") ")
            if (i != queryWords.size - 1) sb.append("AND ")
        }
        if (sb.isEmpty()) return listOf()

        var queryString = sb.toString()
        if (feedID != 0L) queryString = "(feedId == $feedID) AND $queryString"
        Logd(TAG, "searchEpisodes queryString: $queryString")
        return realm.query(Episode::class).query(queryString).find()
    }

//    private fun showInputMethod(view: View) {
//        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
//        imm.showSoftInput(view, 0)
//    }

    private fun searchOnline() {
        val query = queryText
        if (query.matches("http[s]?://.*".toRegex())) {
            val fragment: Fragment = OnlineFeedFragment.newInstance(query)
            (activity as MainActivity).loadChildFragment(fragment)
            return
        }
        (activity as MainActivity).loadChildFragment(SearchResultsFragment.newInstance(CombinedSearcher::class.java, query))
    }

    companion object {
        val TAG: String = SearchFragment::class.simpleName ?: "Anonymous"

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
