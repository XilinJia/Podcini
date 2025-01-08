package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.feed.searcher.PodcastSearchResult
import ac.mdiq.podcini.net.feed.searcher.PodcastSearcher
import ac.mdiq.podcini.net.feed.searcher.PodcastSearcherRegistry
import ac.mdiq.podcini.net.utils.NetworkUtils.prepareUrl
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.model.SubscriptionLog.Companion.getFeedLogMap
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.compose.OnlineFeedItem
import ac.mdiq.podcini.ui.compose.SearchBarRow
import ac.mdiq.podcini.ui.utils.onlineSearchText
import ac.mdiq.podcini.ui.utils.onlineSearcherName
import ac.mdiq.podcini.util.Logd
import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*

class SearchResultsVM(val context: Context, val lcScope: CoroutineScope) {
    internal var searchProvider: PodcastSearcher? = null

    internal val feedLogs = getFeedLogMap()

    internal var defaultText by mutableStateOf("")
    internal var searchResults = mutableStateListOf<PodcastSearchResult>()
    internal var errorText by mutableStateOf("")
    internal var retryQerry by mutableStateOf("")
    internal var showProgress by mutableStateOf(true)
    internal var noResultText by mutableStateOf("")

    init {
        defaultText = onlineSearchText

    }
    private var searchJob: Job? = null
    @SuppressLint("StringFormatMatches")
    internal fun search(query: String) {
        if (query.isBlank()) return
        if (searchJob != null) {
            searchJob?.cancel()
            searchResults.clear()
        }
        showOnlyProgressBar()
        searchJob = lcScope.launch(Dispatchers.IO) {
            val feeds = getFeedList()
            fun feedId(r: PodcastSearchResult): Long {
                for (f in feeds) if (f.downloadUrl == r.feedUrl) return f.id
                return 0L
            }
            try {
                val result = searchProvider?.search(query) ?: listOf()
                for (r in result) r.feedId = feedId(r)
                searchResults.clear()
                searchResults.addAll(result)
                withContext(Dispatchers.Main) {
                    showProgress = false
                    noResultText = context.getString(R.string.no_results_for_query, query)
                }
            } catch (e: Exception) { handleSearchError(e, query) }
        }.apply { invokeOnCompletion { searchJob = null } }
    }

    private fun handleSearchError(e: Throwable, query: String) {
        Logd(TAG, "exception: ${e.message}")
        showProgress = false
        errorText = e.toString()
        retryQerry = query
    }

    private fun showOnlyProgressBar() {
        errorText = ""
        retryQerry = ""
        showProgress = true
    }
}

@Composable
fun SearchResultsScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vm = remember { SearchResultsVM(context, scope) }

//        val displayUpArrow by remember { derivedStateOf { navController.backQueue.size > 1 } }
//        var upArrowVisible by rememberSaveable { mutableStateOf(displayUpArrow) }
//        LaunchedEffect(navController.backQueue) { upArrowVisible = displayUpArrow }

    var displayUpArrow by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    Logd(TAG, "ON_CREATE")
                    for (info in PodcastSearcherRegistry.searchProviders) {
                        Logd(TAG, "searchProvider: $info")
                        if (info.searcher.javaClass.getName() == onlineSearcherName) {
                            vm.searchProvider = info.searcher
                            break
                        }
                    }
                    if (vm.searchProvider == null) Logd(TAG,"Podcast searcher not found")
                    vm.defaultText = onlineSearchText
                    vm.search(vm.defaultText)
                }
                Lifecycle.Event.ON_START -> {
                    Logd(TAG, "ON_START")
                }
                Lifecycle.Event.ON_RESUME -> {
                    Logd(TAG, "ON_RESUME")
                }
                Lifecycle.Event.ON_STOP -> {
                    Logd(TAG, "ON_STOP")
                }
                Lifecycle.Event.ON_DESTROY -> {
                    Logd(TAG, "ON_DESTROY")
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            vm.searchResults.clear()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        TopAppBar(title = { SearchBarRow(R.string.search_podcast_hint, defaultText = onlineSearchText) { queryText -> vm.search(queryText) }},
            navigationIcon = { IconButton(onClick = { if (mainNavController.previousBackStackEntry != null) mainNavController.popBackStack()
            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
        )
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        ConstraintLayout(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            val (gridView, progressBar, empty, txtvError, butRetry, powered) = createRefs()
            if (vm.showProgress) CircularProgressIndicator(progress = { 0.6f }, strokeWidth = 10.dp, modifier = Modifier.size(50.dp).constrainAs(progressBar) { centerTo(parent) })
            val lazyListState = rememberLazyListState()
            if (vm.searchResults.isNotEmpty()) LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
                .constrainAs(gridView) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                },
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.searchResults.size) { index ->
                    val result = vm.searchResults[index]
                    val urlPrepared by remember { mutableStateOf(prepareUrl(result.feedUrl!!)) }
                    val sLog = remember { mutableStateOf(vm.feedLogs[urlPrepared]) }
//                    Logd(TAG, "result: ${result.feedUrl} ${feedLogs[urlPrepared]}")
                    OnlineFeedItem(activity = context as MainActivity, result, sLog.value)
                }
            }
            if (vm.searchResults.isEmpty()) Text(vm.noResultText, color = textColor, modifier = Modifier.constrainAs(empty) { centerTo(parent) })
            if (vm.errorText.isNotEmpty()) Text(vm.errorText, color = textColor, modifier = Modifier.constrainAs(txtvError) { centerTo(parent) })
            if (vm.retryQerry.isNotEmpty()) Button(modifier = Modifier.padding(16.dp).constrainAs(butRetry) { top.linkTo(txtvError.bottom) }, onClick = { vm.search(vm.retryQerry) }) {
                Text(stringResource(id = R.string.retry_label))
            }
            Text(context.getString(R.string.search_powered_by, vm.searchProvider!!.name), color = Color.Black, style = MaterialTheme.typography.labelSmall, modifier = Modifier.background(Color.LightGray)
                .constrainAs(powered) {
                    bottom.linkTo(parent.bottom)
                    end.linkTo(parent.end)
                })
        }
    }
}

private const val TAG: String = "SearchResultsScreen"
private const val ARG_SEARCHER = "searcher"
private const val ARG_QUERY = "query"
