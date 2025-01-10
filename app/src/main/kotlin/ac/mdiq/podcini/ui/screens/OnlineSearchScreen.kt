package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.net.feed.searcher.*
import ac.mdiq.podcini.preferences.OpmlBackupAgent.Companion.isOPMLRestored
import ac.mdiq.podcini.preferences.OpmlBackupAgent.Companion.performRestore
import ac.mdiq.podcini.preferences.OpmlTransporter
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlElement
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.Feeds.updateFeed
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.PAFeed
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.activity.MainActivity.Screens
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.NonlazyGrid
import ac.mdiq.podcini.ui.compose.OpmlImportSelectionDialog
import ac.mdiq.podcini.ui.compose.SearchBarRow
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.setOnlineFeedUrl
import ac.mdiq.podcini.ui.utils.setOnlineSearchTerms
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.DisplayMetrics
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.*


class OnlineSearchVM(val context: Context, val lcScope: CoroutineScope) {
    val prefs: SharedPreferences by lazy { context.getSharedPreferences(ItunesTopListLoader.PREFS, Context.MODE_PRIVATE) }

    internal var mainAct: MainActivity? = null
    internal var displayUpArrow = false

    internal var showError by mutableStateOf(false)
    internal var errorText by mutableStateOf("")
    internal var showPowerBy by mutableStateOf(false)
    internal var showRetry by mutableStateOf(false)
    internal var retryTextRes by mutableIntStateOf(0)
    internal var showGrid by mutableStateOf(false)

    internal val showOPMLRestoreDialog = mutableStateOf(false)
    internal var numColumns by mutableIntStateOf(4)
    internal val searchResult = mutableStateListOf<PodcastSearchResult>()

    internal var showOpmlImportSelectionDialog by mutableStateOf(false)
    internal val readElements = mutableStateListOf<OpmlElement>()

    internal var eventSink: Job?     = null
    internal fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    internal fun procFlowEvents() {
        if (eventSink != null) return
        eventSink = lcScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.DiscoveryDefaultUpdateEvent -> loadToplist()
                    else -> {}
                }
            }
        }
    }

    internal fun loadToplist() {
        showError = false
        showPowerBy = true
        showRetry = false
        retryTextRes = R.string.retry_label
        val loader = ItunesTopListLoader(context)
        val countryCode: String = prefs.getString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, Locale.getDefault().country)!!
        if (prefs.getBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, false)) {
            showError = true
            errorText = context.getString(R.string.discover_is_hidden)
            showPowerBy = false
            showRetry = false
            return
        }
        if (BuildConfig.FLAVOR == "free" && prefs.getBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, true) == true) {
            showError = true
            errorText = ""
            showGrid = true
            showRetry = true
            retryTextRes = R.string.discover_confirm
            showPowerBy = true
            return
        }

        lcScope.launch {
            try {
                val searchResults_ = withContext(Dispatchers.IO) { loader.loadToplist(countryCode, NUM_SUGGESTIONS, getFeedList()) }
                withContext(Dispatchers.Main) {
                    showError = false
                    if (searchResults_.isEmpty()) {
                        errorText = context.getString(R.string.search_status_no_results)
                        showError = true
                        showGrid = false
                    } else {
                        showGrid = true
                        searchResult.clear()
                        searchResult.addAll(searchResults_)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
                showError = true
                showGrid = false
                showRetry = true
                errorText = e.localizedMessage ?: ""
            }
        }
    }
    internal fun showAddViaUrlDialog() {
//            val builder = MaterialAlertDialogBuilder(context)
//            builder.setTitle(R.string.add_podcast_by_url)
//            val dialogBinding = EditTextDialogBinding.inflate(layoutInflater)
//            dialogBinding.editText.setHint(R.string.add_podcast_by_url_hint)
//
//            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
//            val clipData: ClipData? = clipboard.primaryClip
//            if (clipData != null && clipData.itemCount > 0 && clipData.getItemAt(0).text != null) {
//                val clipboardContent: String = clipData.getItemAt(0).text.toString()
//                if (clipboardContent.trim { it <= ' ' }.startsWith("http")) dialogBinding.editText.setText(clipboardContent.trim { it <= ' ' })
//            }
//            builder.setView(dialogBinding.root)
//            builder.setPositiveButton(R.string.confirm_label) { _: DialogInterface?, _: Int -> addUrl(dialogBinding.editText.text.toString()) }
//            builder.setNegativeButton(R.string.cancel_label, null)
//            builder.show()
    }

    internal fun addUrl(url: String) {
        setOnlineFeedUrl(url)
        mainNavController.navigate(Screens.OnlineFeed.name)
    }
}

@Composable
fun OnlineSearchScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vm = remember { OnlineSearchVM(context, scope) }

//        val displayUpArrow by remember { derivedStateOf { navController.backQueue.size > 1 } }
//        var upArrowVisible by rememberSaveable { mutableStateOf(displayUpArrow) }
//        LaunchedEffect(navController.backQueue) { upArrowVisible = displayUpArrow }

    var displayUpArrow by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    Logd(TAG, "ON_CREATE")
                    vm.mainAct = context as? MainActivity
                    Logd(TAG, "fragment onCreateView")
//                        vm.displayUpArrow = parentFragmentManager.backStackEntryCount != 0
//                        if (savedInstanceState != null) vm.displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

                    val displayMetrics: DisplayMetrics = context.resources.displayMetrics
                    val screenWidthDp: Float = displayMetrics.widthPixels / displayMetrics.density
                    if (screenWidthDp > 600) vm.numColumns = 6

                    // Fill with dummy elements to have a fixed height and
                    // prevent the UI elements below from jumping on slow connections
                    for (i in 0 until NUM_SUGGESTIONS) vm.searchResult.add(PodcastSearchResult.dummy())
                    val PAFeed = realm.query(PAFeed::class).find()
                    Logd(TAG, "size of directory: ${PAFeed.size}")
                    vm.loadToplist()
                    if (isOPMLRestored && feedCount == 0) vm.showOPMLRestoreDialog.value = true
                }
                Lifecycle.Event.ON_START -> {
                    Logd(TAG, "ON_START")
                    vm.procFlowEvents()
                }
                Lifecycle.Event.ON_RESUME -> {
                    Logd(TAG, "ON_RESUME")
                }
                Lifecycle.Event.ON_STOP -> {
                    Logd(TAG, "ON_STOP")
                    vm.cancelFlowEvents()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    Logd(TAG, "ON_DESTROY")
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    val actionColor = MaterialTheme.colorScheme.tertiary
    val scrollState = rememberScrollState()
    ComfirmDialog(R.string.restore_subscriptions_label, stringResource(R.string.restore_subscriptions_summary), vm.showOPMLRestoreDialog) {
        performRestore(context)
//        parentFragmentManager.popBackStack()
    }
    val chooseOpmlImportPathLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        OpmlTransporter.startImport(context, uri) { vm.readElements.addAll(it) }
        vm.showOpmlImportSelectionDialog = true
    }
    val addLocalFolderLauncher = rememberLauncherForActivityResult(AddLocalFolder()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            try {
                val feed = withContext(Dispatchers.IO) {
//                    addLocalFolder(uri)
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    val documentFile = DocumentFile.fromTreeUri(context, uri)
                    requireNotNull(documentFile) { "Unable to retrieve document tree" }
                    var title = documentFile.name
                    if (title == null) title = context.getString(R.string.local_folder)

                    val dirFeed = Feed(Feed.PREFIX_LOCAL_FOLDER + uri.toString(), null, title)
                    dirFeed.episodes.clear()
                    dirFeed.sortOrder = EpisodeSortOrder.EPISODE_TITLE_A_Z
                    val fromDatabase: Feed? = updateFeed(context, dirFeed, false)
                    FeedUpdateManager.runOnce(context, fromDatabase)
                    fromDatabase
                }
                withContext(Dispatchers.Main) {
                    if (feed != null) {
                        feedOnDisplay = feed
                        mainNavController.navigate(Screens.FeedEpisodes.name)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
//                vm.mainAct?.showSnackbarAbovePlayer(e.localizedMessage?: "No messaage", Snackbar.LENGTH_LONG)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
     fun MyTopAppBar() {
        TopAppBar(title = { SearchBarRow(R.string.search_podcast_hint) { queryText ->
            if (queryText.isBlank()) return@SearchBarRow
            if (queryText.matches("http[s]?://.*".toRegex())) vm.addUrl(queryText)
            else {
                setOnlineSearchTerms(CombinedSearcher::class.java, queryText)
                mainNavController.navigate(Screens.SearchResults.name)
            }
        } },
            navigationIcon = if (vm.displayUpArrow) {
                { IconButton(onClick = { if (mainNavController.previousBackStackEntry != null) mainNavController.popBackStack()
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            } else {
                { IconButton(onClick = { MainActivity.openDrawer() }) { Icon(Icons.Filled.Menu, contentDescription = "Open Drawer") } }
            }
        )
    }

    @Composable
     fun QuickDiscoveryView() {
        val textColor = MaterialTheme.colorScheme.onSurface
        val context = LocalContext.current
        val actionColor = MaterialTheme.colorScheme.tertiary
        Column(modifier = Modifier.padding(vertical = 5.dp)) {
            Row(modifier = Modifier.padding(vertical = 10.dp)) {
                Text(stringResource(R.string.discover), color = textColor, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.discover_more), color = actionColor, modifier = Modifier.clickable(onClick = { mainNavController.navigate(Screens.Discovery.name) }))
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                if (vm.showGrid) NonlazyGrid(columns = vm.numColumns, itemCount = vm.searchResult.size, modifier = Modifier.fillMaxWidth()) { index ->
                    AsyncImage(model = ImageRequest.Builder(context).data(vm.searchResult[index].imageUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(),
                        contentDescription = "imgvCover", modifier = Modifier.padding(top = 8.dp)
                            .clickable(onClick = {
                                Logd(TAG, "icon clicked!")
                                val podcast: PodcastSearchResult? = vm.searchResult[index]
                                if (!podcast?.feedUrl.isNullOrEmpty()) {
                                    setOnlineFeedUrl(podcast.feedUrl)
                                    mainNavController.navigate(Screens.OnlineFeed.name)
                                }
                            }))
                }
                if (vm.showError) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(vm.errorText, color = textColor)
                    if (vm.showRetry) Button(onClick = {
                        vm.prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, false).apply()
                        vm.loadToplist()
                    }) { Text(stringResource(vm.retryTextRes)) }
                }
            }
            Text(stringResource(R.string.discover_powered_by_itunes), color = textColor, modifier = Modifier.align(Alignment.End))
        }
    }

    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 10.dp).verticalScroll(scrollState)) {
            QuickDiscoveryView()
            Text(stringResource(R.string.advanced), color = textColor, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.add_podcast_by_url), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = { vm.showAddViaUrlDialog() }))
            Text(stringResource(R.string.add_local_folder), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = {
                try { addLocalFolderLauncher.launch(null)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
//                    vm.mainAct?.showSnackbarAbovePlayer(R.string.unable_to_start_system_file_manager, Snackbar.LENGTH_LONG)
                }
            }))
            Text(stringResource(R.string.search_vistaguide_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = {
                setOnlineSearchTerms(VistaGuidePodcastSearcher::class.java)
                mainNavController.navigate(Screens.SearchResults.name)
            }))
            Text(stringResource(R.string.search_itunes_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = {
                setOnlineSearchTerms(ItunesPodcastSearcher::class.java)
                mainNavController.navigate(Screens.SearchResults.name)
            }))
            Text(stringResource(R.string.search_fyyd_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = {
                setOnlineSearchTerms(FyydPodcastSearcher::class.java)
                mainNavController.navigate(Screens.SearchResults.name)
            }))
            Text(stringResource(R.string.search_podcastindex_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = {
                setOnlineSearchTerms(PodcastIndexPodcastSearcher::class.java)
                mainNavController.navigate(Screens.SearchResults.name)
            }))
            if (vm.showOpmlImportSelectionDialog) OpmlImportSelectionDialog(vm.readElements) { vm.showOpmlImportSelectionDialog = false }
            Text(stringResource(R.string.opml_add_podcast_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = {
                try { chooseOpmlImportPathLauncher.launch("*/*")
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
//                    vm.mainAct?.showSnackbarAbovePlayer(R.string.unable_to_start_system_file_manager, Snackbar.LENGTH_LONG)
                }
            }))
        }
    }
}

class AddLocalFolder : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent {
        return super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private const val TAG = "OnlineSearchScreen"
private const val KEY_UP_ARROW = "up_arrow"

private const val NUM_SUGGESTIONS = 12


