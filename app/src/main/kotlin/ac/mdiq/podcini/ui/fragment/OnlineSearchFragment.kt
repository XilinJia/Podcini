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
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.model.PAFeed
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.*
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

    class OnlineSearchVM {
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
    }

    private val vm = OnlineSearchVM()

    private val chooseOpmlImportPathLauncher = registerForActivityResult<String, Uri>(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        OpmlTransporter.startImport(requireContext(), uri) { vm.readElements.addAll(it) }
        vm.showOpmlImportSelectionDialog = true
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
                        vm.mainAct?.loadChildFragment(fragment)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
                vm.mainAct?.showSnackbarAbovePlayer(e.localizedMessage?: "No messaage", Snackbar.LENGTH_LONG)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        vm.mainAct = activity as? MainActivity
        Logd(TAG, "fragment onCreateView")
        vm.displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) vm.displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

        val displayMetrics: DisplayMetrics = requireContext().resources.displayMetrics
        val screenWidthDp: Float = displayMetrics.widthPixels / displayMetrics.density
        if (screenWidthDp > 600) vm.numColumns = 6

        // Fill with dummy elements to have a fixed height and
        // prevent the UI elements below from jumping on slow connections
        for (i in 0 until NUM_SUGGESTIONS) vm.searchResult.add(PodcastSearchResult.dummy())
        val composeView = ComposeView(requireContext()).apply { setContent { CustomTheme(requireContext()) { OnlineSearchScreen() } } }

        val PAFeed = realm.query(PAFeed::class).find()
//        for (p in directory) {
//            Logd(TAG, "in directory: ${p.name}")
//        }
        Logd(TAG, "size of directory: ${PAFeed.size}")
        loadToplist()
        if (isOPMLRestored && feedCount == 0) vm.showOPMLRestoreDialog.value = true
        return composeView
    }

    @Composable
    fun OnlineSearchScreen() {
        val textColor = MaterialTheme.colorScheme.onSurface
        val actionColor = MaterialTheme.colorScheme.tertiary
        val scrollState = rememberScrollState()
        ComfirmDialog(R.string.restore_subscriptions_label, stringResource(R.string.restore_subscriptions_summary), vm.showOPMLRestoreDialog) {
            performRestore(requireContext())
            parentFragmentManager.popBackStack()
        }
        Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 10.dp).verticalScroll(scrollState)) {
                QuickDiscoveryView()
                Text(stringResource(R.string.advanced), color = textColor, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.add_podcast_by_url), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = { showAddViaUrlDialog() }))
                Text(stringResource(R.string.add_local_folder), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = {
                    try { addLocalFolderLauncher.launch(null)
                    } catch (e: ActivityNotFoundException) {
                        e.printStackTrace()
                        vm.mainAct?.showSnackbarAbovePlayer(R.string.unable_to_start_system_file_manager, Snackbar.LENGTH_LONG)
                    }
                }))
                Text(stringResource(R.string.search_vistaguide_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = { vm.mainAct?.loadChildFragment(SearchResultsFragment.newInstance(VistaGuidePodcastSearcher::class.java)) }))
                Text(stringResource(R.string.search_itunes_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = { vm.mainAct?.loadChildFragment(SearchResultsFragment.newInstance(ItunesPodcastSearcher::class.java)) }))
                Text(stringResource(R.string.search_fyyd_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = { vm.mainAct?.loadChildFragment(SearchResultsFragment.newInstance(FyydPodcastSearcher::class.java)) }))
//                Text(stringResource(R.string.gpodnet_search_hint), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = { mainAct?.loadChildFragment(SearchResultsFragment.newInstance(GpodnetPodcastSearcher::class.java)) }))
                Text(stringResource(R.string.search_podcastindex_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = { vm.mainAct?.loadChildFragment(SearchResultsFragment.newInstance(PodcastIndexPodcastSearcher::class.java)) }))
                if (vm.showOpmlImportSelectionDialog) OpmlImportSelectionDialog(vm.readElements) { vm.showOpmlImportSelectionDialog = false }
                Text(stringResource(R.string.opml_add_podcast_label), color = actionColor, modifier = Modifier.padding(start = 10.dp, top = 10.dp).clickable(onClick = {
                    try { chooseOpmlImportPathLauncher.launch("*/*")
                    } catch (e: ActivityNotFoundException) {
                        e.printStackTrace()
                        vm.mainAct?.showSnackbarAbovePlayer(R.string.unable_to_start_system_file_manager, Snackbar.LENGTH_LONG)
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
            else vm.mainAct?.loadChildFragment(SearchResultsFragment.newInstance(CombinedSearcher::class.java, queryText))
        } },
            navigationIcon = if (vm.displayUpArrow) {
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
        vm.cancelFlowEvents()
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
                Text(stringResource(R.string.discover_more), color = actionColor, modifier = Modifier.clickable(onClick = {(activity as MainActivity).loadChildFragment(DiscoveryFragment())}))
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
                                    val fragment: Fragment = OnlineFeedFragment.newInstance(podcast.feedUrl)
                                    (activity as MainActivity).loadChildFragment(fragment)
                                }
                            }))
                }
                if (vm.showError) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(vm.errorText, color = textColor)
                    if (vm.showRetry) Button(onClick = {
                        prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, false).apply()
                        loadToplist()
                    }) { Text(stringResource(vm.retryTextRes)) }
                }
            }
            Text(stringResource(R.string.discover_powered_by_itunes), color = textColor, modifier = Modifier.align(Alignment.End))
        }
    }

    private fun procFlowEvents() {
        if (vm.eventSink != null) return
        vm.eventSink = lifecycleScope.launch {
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
        vm.showError = false
        vm.showPowerBy = true
        vm.showRetry = false
        vm.retryTextRes = R.string.retry_label
        val loader = ItunesTopListLoader(requireContext())
        val countryCode: String = prefs.getString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, Locale.getDefault().country)!!
        if (prefs.getBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, false)) {
            vm.showError = true
            vm.errorText = requireContext().getString(R.string.discover_is_hidden)
            vm.showPowerBy = false
            vm.showRetry = false
            return
        }
        if (BuildConfig.FLAVOR == "free" && prefs.getBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, true) == true) {
            vm.showError = true
            vm.errorText = ""
            vm.showGrid = true
            vm.showRetry = true
            vm.retryTextRes = R.string.discover_confirm
            vm.showPowerBy = true
            return
        }

        lifecycleScope.launch {
            try {
                val searchResults_ = withContext(Dispatchers.IO) { loader.loadToplist(countryCode, NUM_SUGGESTIONS, getFeedList()) }
                withContext(Dispatchers.Main) {
                    vm.showError = false
                    if (searchResults_.isEmpty()) {
                        vm.errorText = requireContext().getString(R.string.search_status_no_results)
                        vm.showError = true
                        vm.showGrid = false
                    } else {
                        vm.showGrid = true
                        vm.searchResult.clear()
                        vm.searchResult.addAll(searchResults_)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
                vm.showError = true
                vm.showGrid = false
                vm.showRetry = true
                vm.errorText = e.localizedMessage ?: ""
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, vm.displayUpArrow)
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
        vm.mainAct?.loadChildFragment(fragment)
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
