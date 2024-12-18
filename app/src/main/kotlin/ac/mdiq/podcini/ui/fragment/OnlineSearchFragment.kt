package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.EditTextDialogBinding
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.net.feed.searcher.*
import ac.mdiq.podcini.preferences.OpmlBackupAgent.Companion.isOPMLRestored
import ac.mdiq.podcini.preferences.OpmlBackupAgent.Companion.performRestore
import ac.mdiq.podcini.preferences.OpmlTransporter
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlElement
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.Feeds.updateFeed
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.NonlazyGrid
import ac.mdiq.podcini.ui.compose.OpmlImportSelectionDialog
import ac.mdiq.podcini.ui.compose.SearchBarRow
import ac.mdiq.podcini.ui.fragment.NavDrawerFragment.Companion.feedCount
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.*

class OnlineSearchFragment : Fragment() {
    val prefs: SharedPreferences by lazy { requireActivity().getSharedPreferences(ItunesTopListLoader.PREFS, Context.MODE_PRIVATE) }

    private var mainAct: MainActivity? = null
    private var displayUpArrow = false

    private var showError by mutableStateOf(false)
    private var errorText by mutableStateOf("")
    private var showPowerBy by mutableStateOf(false)
    private var showRetry by mutableStateOf(false)
    private var retryTextRes by mutableIntStateOf(0)
    private var showGrid by mutableStateOf(false)

    private val showOPMLRestoreDialog = mutableStateOf(false)
    private var numColumns by mutableIntStateOf(4)
    private val searchResult = mutableStateListOf<PodcastSearchResult>()

    private var showOpmlImportSelectionDialog by mutableStateOf(false)
    private val readElements = mutableStateListOf<OpmlElement>()
    private val chooseOpmlImportPathLauncher = registerForActivityResult<String, Uri>(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        OpmlTransporter.startImport(requireContext(), uri) { readElements.addAll(it) }
        showOpmlImportSelectionDialog = true
    }

    private val addLocalFolderLauncher = registerForActivityResult<Uri?, Uri>(AddLocalFolder()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            try {
                val feed = withContext(Dispatchers.IO) {
//                    addLocalFolder(uri)
                    requireActivity().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    val documentFile = DocumentFile.fromTreeUri(requireContext(), uri)
                    requireNotNull(documentFile) { "Unable to retrieve document tree" }
                    var title = documentFile.name
                    if (title == null) title = getString(R.string.local_folder)

                    val dirFeed = Feed(Feed.PREFIX_LOCAL_FOLDER + uri.toString(), null, title)
                    dirFeed.episodes.clear()
                    dirFeed.sortOrder = EpisodeSortOrder.EPISODE_TITLE_A_Z
                    val fromDatabase: Feed? = updateFeed(requireContext(), dirFeed, false)
                    FeedUpdateManager.runOnce(requireContext(), fromDatabase)
                    fromDatabase
                }
                withContext(Dispatchers.Main) {
                    if (feed != null) {
                        val fragment: Fragment = FeedEpisodesFragment.newInstance(feed.id)
                        mainAct?.loadChildFragment(fragment)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
                mainAct?.showSnackbarAbovePlayer(e.localizedMessage?: "No messaage", Snackbar.LENGTH_LONG)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        mainAct = activity as? MainActivity
        Logd(TAG, "fragment onCreateView")
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

        val displayMetrics: DisplayMetrics = requireContext().resources.displayMetrics
        val screenWidthDp: Float = displayMetrics.widthPixels / displayMetrics.density
        if (screenWidthDp > 600) numColumns = 6

        // Fill with dummy elements to have a fixed height and
        // prevent the UI elements below from jumping on slow connections
        for (i in 0 until NUM_SUGGESTIONS) searchResult.add(PodcastSearchResult.dummy())
        val composeView = ComposeView(requireContext()).apply { setContent { CustomTheme(requireContext()) { MainView() } } }

        loadToplist()
        if (isOPMLRestored && feedCount == 0) showOPMLRestoreDialog.value = true
        return composeView
    }

    @Composable
    fun MainView() {
        val textColor = MaterialTheme.colorScheme.onSurface
        val scrollState = rememberScrollState()
        ComfirmDialog(R.string.restore_subscriptions_label, stringResource(R.string.restore_subscriptions_summary), showOPMLRestoreDialog) {
            performRestore(requireContext())
            parentFragmentManager.popBackStack()
        }
        Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 10.dp).verticalScroll(scrollState)) {
                QuickDiscoveryView()
                Text(stringResource(R.string.advanced), color = textColor, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.add_podcast_by_url), color = textColor, modifier = Modifier.padding(start = 10.dp, top = 5.dp).clickable(onClick = { showAddViaUrlDialog() }))
                Text(stringResource(R.string.add_local_folder), color = textColor, modifier = Modifier.padding(start = 10.dp, top = 4.dp).clickable(onClick = {
                    try {
                        addLocalFolderLauncher.launch(null)
                    } catch (e: ActivityNotFoundException) {
                        e.printStackTrace()
                        mainAct?.showSnackbarAbovePlayer(R.string.unable_to_start_system_file_manager, Snackbar.LENGTH_LONG)
                    }
                }))
                Text(stringResource(R.string.search_vistaguide_label), color = textColor, modifier = Modifier.padding(start = 10.dp, top = 5.dp).clickable(onClick = { mainAct?.loadChildFragment(SearchResultsFragment.newInstance(VistaGuidePodcastSearcher::class.java)) }))
                Text(stringResource(R.string.search_itunes_label), color = textColor, modifier = Modifier.padding(start = 10.dp, top = 5.dp).clickable(onClick = { mainAct?.loadChildFragment(SearchResultsFragment.newInstance(ItunesPodcastSearcher::class.java)) }))
                Text(stringResource(R.string.search_fyyd_label), color = textColor, modifier = Modifier.padding(start = 10.dp, top = 5.dp).clickable(onClick = { mainAct?.loadChildFragment(SearchResultsFragment.newInstance(FyydPodcastSearcher::class.java)) }))
                Text(stringResource(R.string.gpodnet_search_hint), color = textColor, modifier = Modifier.padding(start = 10.dp, top = 5.dp).clickable(onClick = { mainAct?.loadChildFragment(SearchResultsFragment.newInstance(GpodnetPodcastSearcher::class.java)) }))
                Text(stringResource(R.string.search_podcastindex_label), color = textColor, modifier = Modifier.padding(start = 10.dp, top = 5.dp).clickable(onClick = { mainAct?.loadChildFragment(SearchResultsFragment.newInstance(PodcastIndexPodcastSearcher::class.java)) }))
                if (showOpmlImportSelectionDialog) OpmlImportSelectionDialog(readElements) { showOpmlImportSelectionDialog = false }
                Text(stringResource(R.string.opml_add_podcast_label), color = textColor, modifier = Modifier.padding(start = 10.dp, top = 5.dp).clickable(onClick = {
                    try {
                        chooseOpmlImportPathLauncher.launch("*/*")
                    } catch (e: ActivityNotFoundException) {
                        e.printStackTrace()
                        mainAct?.showSnackbarAbovePlayer(R.string.unable_to_start_system_file_manager, Snackbar.LENGTH_LONG)
                    }
                }))
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        TopAppBar(title = { SearchBarRow(R.string.search_podcast_hint) { queryText ->
            if (queryText.isBlank()) return@SearchBarRow
            if (queryText.matches("http[s]?://.*".toRegex())) addUrl(queryText)
            else mainAct?.loadChildFragment(SearchResultsFragment.newInstance(CombinedSearcher::class.java, queryText))
        } },
            navigationIcon = if (displayUpArrow) {
                { IconButton(onClick = { parentFragmentManager.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            } else {
                { IconButton(onClick = { (activity as? MainActivity)?.openDrawer() }) { Icon(Icons.Filled.Menu, contentDescription = "Open Drawer") } }
            }
        )
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
    }

    @Composable
    fun QuickDiscoveryView() {
        val textColor = MaterialTheme.colorScheme.onSurface
        val context = LocalContext.current
        Column(modifier = Modifier.padding(vertical = 5.dp)) {
            Row {
                Text(stringResource(R.string.discover), color = textColor, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.discover_more), color = textColor, modifier = Modifier.clickable(onClick = {(activity as MainActivity).loadChildFragment(DiscoveryFragment())}))
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                if (showGrid) NonlazyGrid(columns = numColumns, itemCount = searchResult.size, modifier = Modifier.fillMaxWidth()) { index ->
                    AsyncImage(model = ImageRequest.Builder(context).data(searchResult[index].imageUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(),
                        contentDescription = "imgvCover", modifier = Modifier.padding(top = 8.dp)
                            .clickable(onClick = {
                                Logd(TAG, "icon clicked!")
                                val podcast: PodcastSearchResult? = searchResult[index]
                                if (!podcast?.feedUrl.isNullOrEmpty()) {
                                    val fragment: Fragment = OnlineFeedFragment.newInstance(podcast.feedUrl)
                                    (activity as MainActivity).loadChildFragment(fragment)
                                }
                            }))
                }
                if (showError) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(errorText, color = textColor)
                    if (showRetry) Button(onClick = {
                        prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, false).apply()
                        loadToplist()
                    }) { Text(stringResource(retryTextRes)) }
                }
            }
            Text(stringResource(R.string.discover_powered_by_itunes), color = textColor, modifier = Modifier.align(Alignment.End))
        }
    }

    private var eventSink: Job?     = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    private fun procFlowEvents() {
        if (eventSink != null) return
        eventSink = lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.DiscoveryDefaultUpdateEvent -> loadToplist()
                    else -> {}
                }
            }
        }
    }

    private fun loadToplist() {
        showError = false
        showPowerBy = true
        showRetry = false
        retryTextRes = R.string.retry_label
        val loader = ItunesTopListLoader(requireContext())
        val countryCode: String = prefs.getString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, Locale.getDefault().country)!!
        if (prefs.getBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, false)) {
            showError = true
            errorText = requireContext().getString(R.string.discover_is_hidden)
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

        lifecycleScope.launch {
            try {
                val searchResults_ = withContext(Dispatchers.IO) { loader.loadToplist(countryCode, NUM_SUGGESTIONS, getFeedList()) }
                withContext(Dispatchers.Main) {
                    showError = false
                    if (searchResults_.isEmpty()) {
                        errorText = requireContext().getString(R.string.search_status_no_results)
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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    private fun showAddViaUrlDialog() {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(R.string.add_podcast_by_url)
        val dialogBinding = EditTextDialogBinding.inflate(layoutInflater)
        dialogBinding.editText.setHint(R.string.add_podcast_by_url_hint)

        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData: ClipData? = clipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0 && clipData.getItemAt(0).text != null) {
            val clipboardContent: String = clipData.getItemAt(0).text.toString()
            if (clipboardContent.trim { it <= ' ' }.startsWith("http")) dialogBinding.editText.setText(clipboardContent.trim { it <= ' ' })
        }
        builder.setView(dialogBinding.root)
        builder.setPositiveButton(R.string.confirm_label) { _: DialogInterface?, _: Int -> addUrl(dialogBinding.editText.text.toString()) }
        builder.setNegativeButton(R.string.cancel_label, null)
        builder.show()
    }

    private fun addUrl(url: String) {
        val fragment: Fragment = OnlineFeedFragment.newInstance(url)
        mainAct?.loadChildFragment(fragment)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }
    
    private class AddLocalFolder : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent {
            return super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {
        val TAG = OnlineSearchFragment::class.simpleName ?: "Anonymous"
        private const val KEY_UP_ARROW = "up_arrow"

        private const val NUM_SUGGESTIONS = 12
    }
}
