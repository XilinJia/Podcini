package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.ComposeFragmentBinding
import ac.mdiq.podcini.databinding.EditTextDialogBinding
import ac.mdiq.podcini.databinding.SelectCountryDialogBinding
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.net.feed.searcher.*
import ac.mdiq.podcini.preferences.OpmlBackupAgent.Companion.isOPMLRestared
import ac.mdiq.podcini.preferences.OpmlBackupAgent.Companion.performRestore
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.Feeds.updateFeed
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.OpmlImportActivity
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.NonlazyGrid
import ac.mdiq.podcini.ui.compose.OnlineFeedItem
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
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.Throws

class OnlineSearchFragment : Fragment() {
    val prefs: SharedPreferences by lazy { requireActivity().getSharedPreferences(ItunesTopListLoader.PREFS, Context.MODE_PRIVATE) }

    private var _binding: ComposeFragmentBinding? = null
    private val binding get() = _binding!!

    private var mainAct: MainActivity? = null
    private var displayUpArrow = false

    private var showError by mutableStateOf(false)
    private var errorText by mutableStateOf("")
    private var showPowerBy by mutableStateOf(false)
    private var showRetry by mutableStateOf(false)
    private var retryTextRes by mutableIntStateOf(0)
    private var showGrid by mutableStateOf(false)

    private var numColumns by mutableIntStateOf(4)
    private val searchResult = mutableStateListOf<PodcastSearchResult>()

    private val chooseOpmlImportPathLauncher = registerForActivityResult<String, Uri>(ActivityResultContracts.GetContent()) { uri: Uri? ->
        this.chooseOpmlImportPathResult(uri) }

    private val addLocalFolderLauncher = registerForActivityResult<Uri?, Uri>(AddLocalFolder()) { uri: Uri? -> this.addLocalFolderResult(uri) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = ComposeFragmentBinding.inflate(inflater)
        mainAct = activity as? MainActivity
        Logd(TAG, "fragment onCreateView")
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
        mainAct?.setupToolbarToggle(binding.toolbar, displayUpArrow)

        val displayMetrics: DisplayMetrics = requireContext().resources.displayMetrics
        val screenWidthDp: Float = displayMetrics.widthPixels / displayMetrics.density
        if (screenWidthDp > 600) numColumns = 6

        // Fill with dummy elements to have a fixed height and
        // prevent the UI elements below from jumping on slow connections
        for (i in 0 until NUM_SUGGESTIONS) searchResult.add(PodcastSearchResult.dummy())
        binding.mainView.setContent { CustomTheme(requireContext()) { MainView() } }

        loadToplist()

        if (isOPMLRestared && feedCount == 0) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.restore_subscriptions_label)
                .setMessage(R.string.restore_subscriptions_summary)
                .setPositiveButton("Yes") { dialog, _ ->
                    performRestore(requireContext())
                    dialog.dismiss()
                    parentFragmentManager.popBackStack()
                }
                .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
                .show()
        }
        return binding.root
    }

    @Composable
    fun MainView() {
        val textColor = MaterialTheme.colorScheme.onSurface
        Column(Modifier.padding(horizontal = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                var queryText by remember { mutableStateOf("") }
                fun performSearch() {
                    if (queryText.matches("http[s]?://.*".toRegex())) addUrl(queryText)
                    else mainAct?.loadChildFragment(SearchResultsFragment.newInstance(CombinedSearcher::class.java, queryText))
                }
                TextField(value = queryText, onValueChange = { queryText = it }, label = { Text(stringResource(R.string.search_podcast_hint)) },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { performSearch() }), modifier = Modifier.weight(1f))
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), tint = textColor, contentDescription = "right_action_icon",
                    modifier = Modifier.width(40.dp).height(40.dp).padding(start = 5.dp).clickable(onClick = { performSearch() }))
            }
            QuickDiscoveryView()
            Text(stringResource(R.string.advanced), color = textColor, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.add_podcast_by_url), color = textColor, modifier = Modifier.clickable(onClick = { showAddViaUrlDialog() }))
            Text(stringResource(R.string.add_local_folder), color = textColor, modifier = Modifier.clickable(onClick = {
                try { addLocalFolderLauncher.launch(null)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                    mainAct?.showSnackbarAbovePlayer(R.string.unable_to_start_system_file_manager, Snackbar.LENGTH_LONG)
                }
            }))
            Text(stringResource(R.string.search_vistaguide_label), color = textColor, modifier = Modifier.clickable(onClick = { mainAct?.loadChildFragment(SearchResultsFragment.newInstance(VistaGuidePodcastSearcher::class.java)) }))
            Text(stringResource(R.string.search_itunes_label), color = textColor, modifier = Modifier.clickable(onClick = { mainAct?.loadChildFragment(SearchResultsFragment.newInstance(ItunesPodcastSearcher::class.java)) }))
            Text(stringResource(R.string.search_fyyd_label), color = textColor, modifier = Modifier.clickable(onClick = { mainAct?.loadChildFragment(SearchResultsFragment.newInstance(FyydPodcastSearcher::class.java)) }))
            Text(stringResource(R.string.gpodnet_search_hint), color = textColor, modifier = Modifier.clickable(onClick = { mainAct?.loadChildFragment(SearchResultsFragment.newInstance(GpodnetPodcastSearcher::class.java)) }))
            Text(stringResource(R.string.search_podcastindex_label), color = textColor, modifier = Modifier.clickable(onClick = { mainAct?.loadChildFragment(SearchResultsFragment.newInstance(PodcastIndexPodcastSearcher::class.java)) }))
            Text(stringResource(R.string.opml_add_podcast_label), color = textColor, modifier = Modifier.clickable(onClick = {
                try { chooseOpmlImportPathLauncher.launch("*/*")
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                    mainAct?.showSnackbarAbovePlayer(R.string.unable_to_start_system_file_manager, Snackbar.LENGTH_LONG)
                }
            }))
        }
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
            ConstraintLayout(modifier = Modifier.fillMaxWidth()) {
                val (grid, error) = createRefs()
                if (showGrid) NonlazyGrid(columns = numColumns, itemCount = searchResult.size, modifier = Modifier.fillMaxWidth().constrainAs(grid) { centerTo(parent) }) { index ->
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
                if (showError) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().constrainAs(error) { centerTo(parent) }) {
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

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        _binding = null
        super.onDestroyView()
    }

    private fun chooseOpmlImportPathResult(uri: Uri?) {
        if (uri == null) return

        val intent = Intent(context, OpmlImportActivity::class.java)
        intent.setData(uri)
        startActivity(intent)
    }

     private fun addLocalFolderResult(uri: Uri?) {
        if (uri == null) return
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            try {
                val feed = withContext(Dispatchers.IO) { addLocalFolder(uri) }
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

     private fun addLocalFolder(uri: Uri): Feed? {
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
        return fromDatabase
    }

    private class AddLocalFolder : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent {
            return super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    class ItunesTopListLoader(private val context: Context) {
        @Throws(JSONException::class, IOException::class)
        fun loadToplist(country: String, limit: Int, subscribed: List<Feed>): List<PodcastSearchResult> {
            val client = getHttpClient()
            val feedString: String
            var loadCountry = country
            if (COUNTRY_CODE_UNSET == country) loadCountry = Locale.getDefault().country
            feedString = try { getTopListFeed(client, loadCountry)
            } catch (e: IOException) { if (COUNTRY_CODE_UNSET == country) getTopListFeed(client, "US") else throw e }
            return removeSubscribed(parseFeed(feedString), subscribed, limit)
        }
        @Throws(IOException::class)
        private fun getTopListFeed(client: OkHttpClient?, country: String): String {
            val url = "https://itunes.apple.com/%s/rss/toppodcasts/limit=$NUM_LOADED/explicit=true/json"
            Logd(TAG, "Feed URL " + String.format(url, country))
            val httpReq: Request.Builder = Request.Builder()
                .cacheControl(CacheControl.Builder().maxStale(1, TimeUnit.DAYS).build())
                .url(String.format(url, country))
            client!!.newCall(httpReq.build()).execute().use { response ->
                if (response.isSuccessful) return response.body!!.string()
                if (response.code == 400) throw IOException("iTunes does not have data for the selected country.")
                val prefix = context.getString(R.string.error_msg_prefix)
                throw IOException(prefix + response)
            }
        }
        @Throws(JSONException::class)
        private fun parseFeed(jsonString: String): List<PodcastSearchResult> {
            val result = JSONObject(jsonString)
            val feed: JSONObject
            val entries: JSONArray
            try {
                feed = result.getJSONObject("feed")
                entries = feed.getJSONArray("entry")
            } catch (_: JSONException) { return ArrayList() }
            val results: MutableList<PodcastSearchResult> = ArrayList()
            for (i in 0 until entries.length()) {
                val json = entries.getJSONObject(i)
                results.add(PodcastSearchResult.fromItunesToplist(json))
            }
            return results
        }

        companion object {
            private val TAG: String = ItunesTopListLoader::class.simpleName ?: "Anonymous"
            const val PREF_KEY_COUNTRY_CODE: String = "country_code"
            const val PREF_KEY_HIDDEN_DISCOVERY_COUNTRY: String = "hidden_discovery_country"
            const val PREF_KEY_NEEDS_CONFIRM: String = "needs_confirm"
            const val PREFS: String = "CountryRegionPrefs"
            const val COUNTRY_CODE_UNSET: String = "99"
            private const val NUM_LOADED = 25

            private fun removeSubscribed(suggestedPodcasts: List<PodcastSearchResult>, subscribedFeeds: List<Feed>, limit: Int): List<PodcastSearchResult> {
                val subscribedPodcastsSet: MutableSet<String> = HashSet()
                for (subscribedFeed in subscribedFeeds) {
                    if (subscribedFeed.title != null && subscribedFeed.author != null)
                        subscribedPodcastsSet.add(subscribedFeed.title!!.trim { it <= ' ' } + " - " + subscribedFeed.author!!.trim { it <= ' ' })
                }
                val suggestedNotSubscribed: MutableList<PodcastSearchResult> = ArrayList()
                for (suggested in suggestedPodcasts) {
                    if (!subscribedPodcastsSet.contains(suggested.title.trim { it <= ' ' })) suggestedNotSubscribed.add(suggested)
                    if (suggestedNotSubscribed.size == limit) return suggestedNotSubscribed
                }
                return suggestedNotSubscribed
            }
        }
    }

    class DiscoveryFragment : Fragment(), Toolbar.OnMenuItemClickListener {
        val prefs: SharedPreferences by lazy { requireActivity().getSharedPreferences(ItunesTopListLoader.PREFS, Context.MODE_PRIVATE) }

        private var _binding: ComposeFragmentBinding? = null
        private val binding get() = _binding!!

        private lateinit var toolbar: MaterialToolbar

        private var topList: List<PodcastSearchResult>? = listOf()

        private var countryCode: String? = "US"
        private var hidden = false
        private var needsConfirm = false

        private var searchResults = mutableStateListOf<PodcastSearchResult>()
        private var errorText by mutableStateOf("")
        private var retryQerry by mutableStateOf("")
        private var showProgress by mutableStateOf(true)
        private var noResultText by mutableStateOf("")

        /**
         * Replace adapter data with provided search results from SearchTask.
         * @param result List of Podcast objects containing search results
         */
        private fun updateData(result: List<PodcastSearchResult>) {
            searchResults.clear()
            if (result.isNotEmpty()) {
                searchResults.addAll(result)
                noResultText = ""
            } else noResultText = getString(R.string.no_results_for_query)
            showProgress = false
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            countryCode = prefs.getString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, Locale.getDefault().country)
            hidden = prefs.getBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, false)
            needsConfirm = prefs.getBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, true)
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            // Inflate the layout for this fragment
            _binding = ComposeFragmentBinding.inflate(inflater)
            Logd(TAG, "fragment onCreateView")
            binding.mainView.setContent {
                CustomTheme(requireContext()) {
                    MainView()
                }
            }

            toolbar = binding.toolbar
            toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
            toolbar.inflateMenu(R.menu.countries_menu)
            val discoverHideItem = toolbar.menu.findItem(R.id.discover_hide_item)
            discoverHideItem.isChecked = hidden
            toolbar.setOnMenuItemClickListener(this)

            loadToplist(countryCode)
            return binding.root
        }

        @Composable
        fun MainView() {
            val textColor = MaterialTheme.colorScheme.onSurface
            ConstraintLayout(modifier = Modifier.fillMaxSize()) {
                val (gridView, progressBar, empty, txtvError, butRetry, powered) = createRefs()
                if (showProgress) CircularProgressIndicator(progress = {0.6f}, strokeWidth = 10.dp, modifier = Modifier.size(50.dp).constrainAs(progressBar) { centerTo(parent) })
                val lazyListState = rememberLazyListState()
                if (searchResults.isNotEmpty()) LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
                    .constrainAs(gridView) {
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        start.linkTo(parent.start)
                    },
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(searchResults.size) { index ->
                        OnlineFeedItem(activity = activity as MainActivity, searchResults[index])
                    }
                }
                if (searchResults.isEmpty()) Text(noResultText, color = textColor, modifier = Modifier.constrainAs(empty) { centerTo(parent) })
                if (errorText.isNotEmpty()) Text(errorText, color = textColor, modifier = Modifier.constrainAs(txtvError) { centerTo(parent) })
                if (retryQerry.isNotEmpty()) Button(modifier = Modifier.padding(16.dp).constrainAs(butRetry) { top.linkTo(txtvError.bottom)},
                    onClick = {
                        if (needsConfirm) {
                            prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, false).apply()
                            needsConfirm = false
                        }
                        loadToplist(countryCode)
                    }, ) {
                    Text(stringResource(id = R.string.retry_label))
                }
//                Text( getString(R.string.search_powered_by, searchProvider!!.name), color = Color.Black, style = MaterialTheme.typography.labelSmall, modifier = Modifier.background(
//                    Color.LightGray)
//                    .constrainAs(powered) {
//                        bottom.linkTo(parent.bottom)
//                        end.linkTo(parent.end)
//                    })
            }
        }

        override fun onDestroy() {
            _binding = null
            searchResults.clear()
            topList = null
            super.onDestroy()
        }

        private fun loadToplist(country: String?) {
            searchResults.clear()
            errorText = ""
            retryQerry = ""
            noResultText = ""
            showProgress = true
            if (hidden) {
                errorText = resources.getString(R.string.discover_is_hidden)
                showProgress = false
                return
            }
            if (BuildConfig.FLAVOR == "free" && needsConfirm) {
                errorText = ""
                retryQerry = resources.getString(R.string.discover_confirm)
                noResultText = ""
                showProgress = false
                return
            }

            val loader = ItunesTopListLoader(requireContext())
            lifecycleScope.launch {
                try {
                    val podcasts = withContext(Dispatchers.IO) { loader.loadToplist(country?:"", NUM_OF_TOP_PODCASTS, getFeedList()) }
                    withContext(Dispatchers.Main) {
                        showProgress = false
                        topList = podcasts
                        updateData(topList!!)
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, Log.getStackTraceString(e))
                    searchResults.clear()
                    errorText = e.message ?: "no error message"
                    retryQerry = " retry"
                }
            }
        }

        override fun onMenuItemClick(item: MenuItem): Boolean {
            if (super.onOptionsItemSelected(item)) return true

            val itemId = item.itemId
            when (itemId) {
                R.id.discover_hide_item -> {
                    item.isChecked = !item.isChecked
                    hidden = item.isChecked
                    prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, hidden).apply()

                    EventFlow.postEvent(FlowEvent.DiscoveryDefaultUpdateEvent())
                    loadToplist(countryCode)
                    return true
                }
                R.id.discover_countries_item -> {
                    val inflater = layoutInflater
                    val selectCountryDialogView = inflater.inflate(R.layout.select_country_dialog, null)
                    val builder = MaterialAlertDialogBuilder(requireContext())
                    builder.setView(selectCountryDialogView)

                    val countryCodeArray: List<String> = listOf(*Locale.getISOCountries())
                    val countryCodeNames: MutableMap<String?, String> = HashMap()
                    val countryNameCodes: MutableMap<String, String> = HashMap()
                    for (code in countryCodeArray) {
                        val locale = Locale("", code)
                        val countryName = locale.displayCountry
                        countryCodeNames[code] = countryName
                        countryNameCodes[countryName] = code
                    }

                    val countryNamesSort: MutableList<String> = ArrayList(countryCodeNames.values)
                    countryNamesSort.sort()

                    val dataAdapter = ArrayAdapter(this.requireContext(), android.R.layout.simple_list_item_1, countryNamesSort)
                    val scBinding = SelectCountryDialogBinding.bind(selectCountryDialogView)
                    val textInput = scBinding.countryTextInput
                    val editText = textInput.editText as? MaterialAutoCompleteTextView
                    editText!!.setAdapter(dataAdapter)
                    editText.setText(countryCodeNames[countryCode])
                    editText.setOnClickListener {
                        if (editText.text.isNotEmpty()) {
                            editText.setText("")
                            editText.postDelayed({ editText.showDropDown() }, 100)
                        }
                    }
                    editText.onFocusChangeListener = View.OnFocusChangeListener { _: View?, hasFocus: Boolean ->
                        if (hasFocus) {
                            editText.setText("")
                            editText.postDelayed({ editText.showDropDown() }, 100)
                        }
                    }

                    builder.setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                        val countryName = editText.text.toString()
                        if (countryNameCodes.containsKey(countryName)) {
                            countryCode = countryNameCodes[countryName]
                            val discoverHideItem = toolbar.menu.findItem(R.id.discover_hide_item)
                            discoverHideItem.isChecked = false
                            hidden = false
                        }

                        prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, hidden).apply()
                        prefs.edit().putString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, countryCode).apply()

                        EventFlow.postEvent(FlowEvent.DiscoveryDefaultUpdateEvent())
                        loadToplist(countryCode)
                    }
                    builder.setNegativeButton(R.string.cancel_label, null)
                    builder.show()
                    return true
                }
                else -> return false
            }
        }

        companion object {
            private const val NUM_OF_TOP_PODCASTS = 25
        }
    }

    companion object {
        val TAG = OnlineSearchFragment::class.simpleName ?: "Anonymous"
        private const val KEY_UP_ARROW = "up_arrow"

        private const val NUM_SUGGESTIONS = 12
    }
}
