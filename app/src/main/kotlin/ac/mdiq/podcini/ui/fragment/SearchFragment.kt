package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.feed.searcher.CombinedSearcher
import ac.mdiq.podcini.storage.database.Episodes
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Rating
import ac.mdiq.podcini.ui.actions.SwipeAction
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.actions.SwipeActions.Companion.SwipeActionsSettingDialog
import ac.mdiq.podcini.ui.actions.SwipeActions.NoActionSwipeAction
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.*
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
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

class SearchFragment : Fragment() {
    private lateinit var automaticSearchDebouncer: Handler

    private val resultFeeds = mutableStateListOf<Feed>()
    private val results = mutableListOf<Episode>()
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
                            FeedsRow()
                            InforBar(infoBarText, leftAction = leftActionState, rightAction = rightActionState, actionConfig = { showSwipeActionsDialog = true })
                            EpisodeLazyColumn(activity as MainActivity, vms = vms,
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
        results.clear()
        resultFeeds.clear()
        stopMonitor(vms)
        vms.clear()
        super.onDestroyView()
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
            val pos: Int = Episodes.indexOfItemWithDownloadUrl(results, url)
            if (pos >= 0) vms[pos].downloadState = event.map[url]?.state ?: DownloadStatus.State.UNKNOWN.ordinal
        }
    }

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
                    if (query.isEmpty()) Pair<List<Episode>, List<Feed>>(emptyList(), emptyList())
                    else {
                        val feedID = requireArguments().getLong(ARG_FEED)
                        val items: List<Episode> = searchEpisodes(feedID, query)
                        val feeds: List<Feed> = searchFeeds(query)
                        Logd(TAG, "performSearch items: ${items.size} feeds: ${feeds.size}")
                        Pair<List<Episode>, List<Feed>>(items, feeds)
                    }
                }
                withContext(Dispatchers.Main) {
                    if (results_.first != null) {
                        val first_ = results_.first!!.toMutableList()
                        results.clear()
                        infoBarText.value = "${results.size} episodes"
                        stopMonitor(vms)
                        vms.clear()
                        if (first_.isNotEmpty()) {
                            results.addAll(first_)
                            for (e in first_) { vms.add(EpisodeVM(e, TAG)) }
                        }
                    }
                    if (requireArguments().getLong(ARG_FEED, 0) == 0L) {
                        if (results_.second != null) {
                            resultFeeds.clear()
                            if (results_.second.isNotEmpty()) resultFeeds.addAll(results_.second!!)
                        }
                    } else resultFeeds.clear()
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

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun FeedsRow() {
        val context = LocalContext.current
        val lazyGridState = rememberLazyListState()
        LazyRow (state = lazyGridState, horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 16.dp))  {
            items(resultFeeds.size, key = {index -> resultFeeds[index].id}) { index ->
                val feed by remember { mutableStateOf(resultFeeds[index]) }
                ConstraintLayout {
                    val (coverImage, episodeCount, rating, error) = createRefs()
                    val imgLoc = remember(feed) { feed.imageUrl }
                    AsyncImage(model = ImageRequest.Builder(context).data(imgLoc)
                        .memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(),
                        contentDescription = "coverImage",
                        modifier = Modifier.height(100.dp).aspectRatio(1f)
                            .constrainAs(coverImage) {
                                top.linkTo(parent.top)
                                bottom.linkTo(parent.bottom)
                                start.linkTo(parent.start)
                            }.combinedClickable(onClick = {
                                Logd(SubscriptionsFragment.TAG, "clicked: ${feed.title}")
                                (activity as MainActivity).loadChildFragment(FeedEpisodesFragment.newInstance(feed.id))
                            }, onLongClick = {
                                Logd(SubscriptionsFragment.TAG, "long clicked: ${feed.title}")
//                                val inflater: MenuInflater = (activity as MainActivity).menuInflater
//                                inflater.inflate(R.menu.feed_context, contextMenu)
//                                contextMenu.setHeaderTitle(feed.title)
                            })
                    )
                    Text(NumberFormat.getInstance().format(feed.episodes.size.toLong()), color = Color.Green,
                        modifier = Modifier.background(Color.Gray).constrainAs(episodeCount) {
                            end.linkTo(parent.end)
                            top.linkTo(coverImage.top)
                        })
                    if (feed.rating != Rating.UNRATED.code)
                        Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(feed.rating).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                            modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).constrainAs(rating) {
                                start.linkTo(parent.start)
                                centerVerticallyTo(coverImage)
                            })
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
                sb1.append("eigenTitle TEXT '${queryWords[i]}'")
                sb1.append(" OR ")
                sb1.append("customTitle TEXT '${queryWords[i]}'")
                isStart = false
            }
            if (SearchBy.AUTHOR.selected) {
                if (!isStart) sb1.append(" OR ")
                sb1.append("author TEXT '${queryWords[i]}'")
                isStart = false
            }
            if (SearchBy.DESCRIPTION.selected) {
                if (!isStart) sb1.append(" OR ")
                sb1.append("description TEXT '${queryWords[i]}'")
                isStart = false
            }
            if (SearchBy.COMMENT.selected) {
                if (!isStart) sb1.append(" OR ")
                sb1.append("comment TEXT '${queryWords[i]}'")
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
                sb1.append("title TEXT '${queryWords[i]}'" )
                isStart = false
            }
            if (SearchBy.DESCRIPTION.selected) {
                if (!isStart) sb1.append(" OR ")
                sb1.append("description TEXT '${queryWords[i]}'")
                sb1.append(" OR ")
                sb1.append("transcript TEXT '${queryWords[i]}'")
                isStart = false
            }
            if (SearchBy.COMMENT.selected) {
                if (!isStart) sb1.append(" OR ")
                sb1.append("comment TEXT '${queryWords[i]}'")
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
