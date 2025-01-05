package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.feed.FeedBuilder
import ac.mdiq.podcini.net.feed.FeedUrlNotFoundException
import ac.mdiq.podcini.net.feed.searcher.CombinedSearcher
import ac.mdiq.podcini.net.feed.searcher.PodcastSearcherRegistry
import ac.mdiq.podcini.net.utils.HtmlToPlainText
import ac.mdiq.podcini.net.utils.NetworkUtils.getFinalRedirectedUrl
import ac.mdiq.podcini.preferences.AppPreferences.isEnableAutodownload
import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.database.Feeds.getFeedByTitleAndAuthor
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.Feeds.isSubscribed
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Rating.Companion.fromCode
import ac.mdiq.podcini.storage.model.ShareLog
import ac.mdiq.podcini.storage.model.SubscriptionLog.Companion.feedLogsMap
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter.formatAbbrev
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.*

/**
 * Downloads a feed from a feed URL and parses it. Subclasses can display the
 * feed object that was parsed. This activity MUST be started with a given URL
 * or an Exception will be thrown.
 * If the feed cannot be downloaded or parsed, an error dialog will be displayed
 * and the activity will finish as soon as the error dialog is closed.
 */
class OnlineFeedFragment : Fragment() {
    val prefs: SharedPreferences by lazy { requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var feedSource: String = ""

    class OnlineFeedVM {
        internal var displayUpArrow = false

        internal var feedUrl: String = ""
        internal var urlToLog: String = ""
        internal lateinit var feedBuilder: FeedBuilder
        internal var showYTChannelDialog by mutableStateOf(false)

        internal var isShared: Boolean = false

        internal var showFeedDisplay by mutableStateOf(false)
        internal var showProgress by mutableStateOf(true)
        internal var autoDownloadChecked by mutableStateOf(false)
        internal var enableSubscribe by mutableStateOf(true)
        internal var enableEpisodes by mutableStateOf(true)
        internal var subButTextRes by mutableIntStateOf(R.string.subscribe_label)

        internal val feedId: Long
            get() {
                if (feeds == null) return 0
                for (f in feeds!!) if (f.downloadUrl == selectedDownloadUrl) return f.id
                return 0
            }

        @Volatile
        internal var feeds: List<Feed>? = null
        internal var feed by mutableStateOf<Feed?>(null)
        internal var selectedDownloadUrl: String? = null
        //    private var downloader: Downloader? = null
        internal var username: String? = null
        internal var password: String? = null

        internal var isPaused = false
        internal var didPressSubscribe = false
        internal var isFeedFoundBySearch = false

        internal var dialog: Dialog? = null
    }

    private val vm = OnlineFeedVM()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logd(TAG, "fragment onCreateView")
        vm.displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) vm.displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

        vm.feedUrl = requireArguments().getString(ARG_FEEDURL) ?: ""
        vm.isShared = requireArguments().getBoolean("isShared")
        Logd(TAG, "feedUrl: ${vm.feedUrl}")
        vm.feedBuilder = FeedBuilder(requireContext()) { message, details -> showErrorDialog(message, details) }

        val composeView = ComposeView(requireContext()).apply { setContent { CustomTheme(requireContext()) { OnlineFeedScreen() } } }

        if (vm.feedUrl.isEmpty()) {
            Log.e(TAG, "feedUrl is null.")
            showNoPodcastFoundError()
        } else {
            Logd(TAG, "Activity was started with url ${vm.feedUrl}")
            vm.showProgress = true
            // Remove subscribeonandroid.com from feed URL in order to subscribe to the actual feed URL
            if (vm.feedUrl.contains("subscribeonandroid.com")) vm.feedUrl = vm.feedUrl.replaceFirst("((www.)?(subscribeonandroid.com/))".toRegex(), "")
            if (savedInstanceState != null) {
                vm.username = savedInstanceState.getString("username")
                vm.password = savedInstanceState.getString("password")
            }
            lookupUrlAndBuild(vm.feedUrl)
        }
        return composeView
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        TopAppBar(title = { Text(text = "") },
            navigationIcon = if (vm.displayUpArrow) {
                { IconButton(onClick = { parentFragmentManager.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            } else {
                { IconButton(onClick = { (activity as? MainActivity)?.openDrawer() }) { Icon(Icons.Filled.Menu, contentDescription = "Open Drawer") } }
            }
        )
    }

    override fun onStart() {
        super.onStart()
        vm.isPaused = false
        procFlowEvents()
    }

    override fun onStop() {
        super.onStop()
        vm.isPaused = true
        cancelFlowEvents()
//        if (downloader != null && !downloader!!.isFinished) downloader!!.cancel()
        if (vm.dialog != null && vm.dialog!!.isShowing) vm.dialog!!.dismiss()
    }

    override fun onDestroy() {
        vm.feeds = null
        super.onDestroy()
    }

     override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, vm.displayUpArrow)
        super.onSaveInstanceState(outState)
        outState.putString("username", vm.username)
        outState.putString("password", vm.password)
    }

    private fun handleFeed(feed_: Feed, map: Map<String, String>) {
        vm.selectedDownloadUrl = vm.feedBuilder.selectedDownloadUrl
        vm.feed = feed_
        if (vm.isShared) {
            val log = realm.query(ShareLog::class).query("url == $0", vm.urlToLog).first().find()
            if (log != null) upsertBlk(log) {
                it.title = feed_.title
                it.author = feed_.author
            }
        }
        showFeedInformation(feed_, map)
    }

    private fun lookupUrlAndBuild(url: String) {
        CoroutineScope(Dispatchers.IO).launch {
            vm.urlToLog = url
            val urlString = PodcastSearcherRegistry.lookupUrl1(url)
            Logd(TAG, "lookupUrlAndBuild: urlString: ${urlString}")
            try {
                vm.feeds = getFeedList()
                if (vm.feedBuilder.isYoutube(urlString)) {
                    if (vm.feedBuilder.isYoutubeChannel()) {
                        val nTabs = vm.feedBuilder.youtubeChannelValidTabs()
                        if (nTabs > 1) vm.showYTChannelDialog = true
                        else vm.feedBuilder.buildYTChannel(0, "") { feed_, map -> handleFeed(feed_, map) }
                    } else vm.feedBuilder.buildYTPlaylist { feed_, map -> handleFeed(feed_, map) }
                } else {
                    val urlFinal = getFinalRedirectedUrl(urlString)?:""
                    Logd(TAG, "lookupUrlAndBuild: urlFinal: ${urlFinal}")
                    vm.feedBuilder.buildPodcast(urlFinal, vm.username, vm.password) { feed_, map -> handleFeed(feed_, map) }
                }
            } catch (e: FeedUrlNotFoundException) { tryToRetrieveFeedUrlBySearch(e)
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
                withContext(Dispatchers.Main) { showNoPodcastFoundError() }
            }
        }
    }

    private fun tryToRetrieveFeedUrlBySearch(error: FeedUrlNotFoundException) {
        Logd(TAG, "Unable to retrieve feed url, trying to retrieve feed url from search")
//        val url = searchFeedUrlByTrackName(error.trackName, error.artistName)
        CoroutineScope(Dispatchers.IO).launch {
            var url: String? = null
            val searcher = CombinedSearcher()
            val query = "${error.trackName} ${error.artistName}"
            val results = searcher.search(query)
            if (results.isEmpty()) return@launch
            for (result in results) {
                if (result.feedUrl != null && result.author != null && result.author.equals(error.artistName, ignoreCase = true)
                        && result.title.equals(error.trackName, ignoreCase = true)) {
                    url = result.feedUrl
                    break
                }
            }
            if (url != null) {
                vm.urlToLog = url
                Logd(TAG, "Successfully retrieve feed url")
                vm.isFeedFoundBySearch = true
                vm.feeds = getFeedList()
                if (vm.feedBuilder.isYoutube(url)) {
                    if (vm.feedBuilder.isYoutubeChannel()) {
                        val nTabs = vm.feedBuilder.youtubeChannelValidTabs()
                        if (nTabs > 1) vm.showYTChannelDialog = true
                        else vm.feedBuilder.buildYTChannel(0, "") { feed_, map -> handleFeed(feed_, map) }
                    } else vm.feedBuilder.buildYTPlaylist { feed_, map -> handleFeed(feed_, map) }
                } else vm.feedBuilder.buildPodcast(url, vm.username, vm.password) { feed_, map -> handleFeed(feed_, map) }
            } else {
                showNoPodcastFoundError()
                Logd(TAG, "Failed to retrieve feed url")
            }
        }
    }

//    private fun searchFeedUrlByTrackName(trackName: String, artistName: String): String? {
//        val searcher = CombinedSearcher()
//        val query = "$trackName $artistName"
//        val results = searcher.search(query).blockingGet()
//        if (results.isNullOrEmpty()) return null
//        for (result in results) {
//            if (result?.feedUrl != null && result.author != null && result.author.equals(artistName, ignoreCase = true)
//                    && result.title.equals(trackName, ignoreCase = true)) return result.feedUrl
//        }
//        return null
//    }

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
                    is FlowEvent.FeedListEvent -> onFeedListChanged(event)
                    else -> {}
                }
            }
        }
        if (eventStickySink == null) eventStickySink = lifecycleScope.launch {
            EventFlow.stickyEvents.collectLatest { event ->
                Logd(TAG, "Received sticky event: ${event.TAG}")
                when (event) {
                    is FlowEvent.EpisodeDownloadEvent -> handleUpdatedFeedStatus()
                    else -> {}
                }
            }
        }
    }

    private fun onFeedListChanged(event: FlowEvent.FeedListEvent) {
        lifecycleScope.launch {
            try {
                val feeds = withContext(Dispatchers.IO) { getFeedList() }
                withContext(Dispatchers.Main) {
                    this@OnlineFeedFragment.vm.feeds = feeds
                    handleUpdatedFeedStatus()
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
                withContext(Dispatchers.Main) { showErrorDialog(e.message, "") }
            }
        }
    }

    /**
     * Called when feed parsed successfully.
     * This method is executed on the GUI thread.
     */
     private fun showFeedInformation(feed: Feed, alternateFeedUrls: Map<String, String>) {
        vm.showProgress = false
        vm.showFeedDisplay = true
        if (vm.isFeedFoundBySearch) {
            val resId = R.string.no_feed_url_podcast_found_by_search
            Snackbar.make(requireView(), resId, Snackbar.LENGTH_LONG).show()
        }

//        if (alternateFeedUrls.isEmpty()) binding.alternateUrlsSpinner.visibility = View.GONE
//        else {
//            binding.alternateUrlsSpinner.visibility = View.VISIBLE
//            val alternateUrlsList: MutableList<String> = ArrayList()
//            val alternateUrlsTitleList: MutableList<String?> = ArrayList()
//            if (feed.downloadUrl != null) alternateUrlsList.add(feed.downloadUrl!!)
//            alternateUrlsTitleList.add(feed.title)
//            alternateUrlsList.addAll(alternateFeedUrls.keys)
//            for (url in alternateFeedUrls.keys) {
//                alternateUrlsTitleList.add(alternateFeedUrls[url])
//            }
//            val adapter: ArrayAdapter<String> = object : ArrayAdapter<String>(requireContext(),
//                R.layout.alternate_urls_item, alternateUrlsTitleList) {
//                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
//                    // reusing the old view causes a visual bug on Android <= 10
//                    return super.getDropDownView(position, null, parent)
//                }
//            }
//            adapter.setDropDownViewResource(R.layout.alternate_urls_dropdown_item)
//            binding.alternateUrlsSpinner.adapter = adapter
//            binding.alternateUrlsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
//                override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
//                    selectedDownloadUrl = alternateUrlsList[position]
//                }
//                override fun onNothingSelected(parent: AdapterView<*>?) {}
//            }
//        }
        handleUpdatedFeedStatus()
    }

    @Composable
    fun OnlineFeedScreen() {
        val textColor = MaterialTheme.colorScheme.onSurface
        if (vm.showYTChannelDialog) vm.feedBuilder.ConfirmYTChannelTabsDialog(onDismissRequest = { vm.showYTChannelDialog = false }) { feed, map -> handleFeed(feed, map) }
        val feedLogsMap_ = feedLogsMap!!
        Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
            if (vm.showProgress) Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                CircularProgressIndicator(progress = {0.6f}, strokeWidth = 10.dp, color = textColor, modifier = Modifier.size(50.dp).align(Alignment.Center))
            }
            else Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(start = 10.dp, end = 10.dp)) {
                if (vm.showFeedDisplay) ConstraintLayout(modifier = Modifier.fillMaxWidth().height(100.dp).background(MaterialTheme.colorScheme.surface)) {
                    val (coverImage, taColumn, buttons) = createRefs()
                    AsyncImage(model = vm.feed?.imageUrl ?: "", contentDescription = "coverImage", error = painterResource(R.mipmap.ic_launcher),
                        modifier = Modifier.width(100.dp).height(100.dp).padding(start = 10.dp, end = 16.dp, bottom = 10.dp).constrainAs(coverImage) {
                            bottom.linkTo(parent.bottom)
                            start.linkTo(parent.start)
                        })
                    Column(Modifier.constrainAs(taColumn) {
                        top.linkTo(coverImage.top)
                        start.linkTo(coverImage.end)
                    }) {
                        Text(vm.feed?.title ?: "No title", color = textColor, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(vm.feed?.author ?: "", color = textColor, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(Modifier.constrainAs(buttons) {
                        start.linkTo(coverImage.end)
                        top.linkTo(taColumn.bottom)
                        end.linkTo(parent.end)
                    }) {
                        Spacer(modifier = Modifier.weight(0.2f))
                        if (vm.enableSubscribe) Button(onClick = {
                            if (feedInFeedlist() || isSubscribed(vm.feed!!)) {
                                if (vm.isShared) {
                                    val log = realm.query(ShareLog::class).query("url == $0", vm.feedUrl).first().find()
                                    if (log != null) upsertBlk(log) { it.status = ShareLog.Status.EXISTING.ordinal }
                                }
                                val feed = getFeedByTitleAndAuthor(vm.feed?.eigenTitle ?: "", vm.feed?.author ?: "")
                                if (feed != null) (activity as MainActivity).loadChildFragment(FeedInfoFragment.newInstance(feed))
                            } else {
                                vm.enableSubscribe = false
                                vm.enableEpisodes = false
                                CoroutineScope(Dispatchers.IO).launch {
                                    vm.feedBuilder.subscribe(vm.feed!!)
                                    if (vm.isShared) {
                                        val log = realm.query(ShareLog::class).query("url == $0", vm.feedUrl).first().find()
                                        if (log != null) upsertBlk(log) { it.status = ShareLog.Status.SUCCESS.ordinal }
                                    }
                                    withContext(Dispatchers.Main) {
                                        vm.enableSubscribe = true
                                        vm.didPressSubscribe = true
                                        handleUpdatedFeedStatus()
                                    }
                                }
                            }
                        }) { Text(stringResource(vm.subButTextRes)) }
                        Spacer(modifier = Modifier.weight(0.1f))
                        if (vm.enableEpisodes && vm.feed != null) Button(onClick = { showEpisodes(vm.feed!!.episodes) }) { Text(stringResource(R.string.episodes_label)) }
                        Spacer(modifier = Modifier.weight(0.2f))
                    }
                }
                Column {
//                    alternate_urls_spinner
                    if (feedSource != "VistaGuide" && isEnableAutodownload) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = vm.autoDownloadChecked, onCheckedChange = { vm.autoDownloadChecked = it })
                        Text(text = stringResource(R.string.auto_download_label),
                            style = MaterialTheme.typography.bodyMedium.merge(), color = textColor,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
                val scrollState = rememberScrollState()
                var numEpisodes by remember { mutableIntStateOf(vm.feed?.episodes?.size ?: 0) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(1000)
                        numEpisodes = vm.feed?.episodes?.size ?: 0
                    }
                }
                Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
                    Text("$numEpisodes episodes", color = textColor, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 5.dp, bottom = 10.dp))
                    Text(stringResource(R.string.description_label), color = textColor, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
                    Text(HtmlToPlainText.getPlainText(vm.feed?.description ?: ""), color = textColor, style = MaterialTheme.typography.bodyMedium)
                    val sLog = remember { feedLogsMap_[vm.feed?.downloadUrl ?: ""] }
                    if (sLog != null) {
                        val commentTextState by remember { mutableStateOf(TextFieldValue(sLog.comment)) }
                        val context = LocalContext.current
                        val cancelDate = remember { formatAbbrev(context, Date(sLog.cancelDate)) }
                        val ratingRes = remember { fromCode(sLog.rating).res }
                        if (commentTextState.text.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp)) {
                                Text(stringResource(R.string.my_opinion_label), color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom)
                                Icon(imageVector = ImageVector.vectorResource(ratingRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = null, modifier = Modifier.padding(start = 5.dp))
                            }
                            Text(commentTextState.text, color = textColor, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 15.dp, bottom = 10.dp))
                            Text(stringResource(R.string.cancelled_on_label) + ": " + cancelDate, color = textColor, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 15.dp, bottom = 10.dp))
                        }
                    }
                    Text(vm.feed?.mostRecentItem?.title ?: "", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
                    Text("${vm.feed?.language ?: ""} ${vm.feed?.type ?: ""} ${vm.feed?.lastUpdate ?: ""}", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
                    Text(vm.feed?.link ?: "", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
                    Text(vm.feed?.downloadUrl ?: "", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
                }
            }
        }
    }

     private fun showEpisodes(episodes: MutableList<Episode>) {
        Logd(TAG, "showEpisodes ${episodes.size}")
        if (episodes.isEmpty()) return
        episodes.sortByDescending { it.pubDate }
        var id_ = Feed.newId()
        for (i in 0..<episodes.size) {
            episodes[i].id = id_++
            episodes[i].isRemote.value = true
        }
        val fragment: Fragment = OnlineEpisodesFragment.newInstance(episodes)
        (activity as MainActivity).loadChildFragment(fragment)
    }

     private fun handleUpdatedFeedStatus() {
        val dli = DownloadServiceInterface.get()
        if (dli == null || vm.selectedDownloadUrl == null) return

        when {
//            feedSource != "VistaGuide" -> {
//                binding.subscribeButton.isEnabled = false
//            }
            dli.isDownloadingEpisode(vm.selectedDownloadUrl!!) -> {
                vm.enableSubscribe = false
                vm.subButTextRes = R.string.subscribe_label
            }
            feedInFeedlist() -> {
                vm.enableSubscribe = true
                vm.subButTextRes = R.string.open
                if (vm.didPressSubscribe) {
                    vm.didPressSubscribe = false
                    val feed1 = getFeed(vm.feedId, true)?: return
//                    if (feed1.preferences == null) feed1.preferences = FeedPreferences(feed1.id, false,
//                        FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, "", "")
                    if (feedSource == "VistaGuide") {
                        feed1.prefStreamOverDownload = true
                        feed1.autoDownload = false
                    } else if (isEnableAutodownload) {
                        val autoDownload = vm.autoDownloadChecked
                        feed1.autoDownload = autoDownload
                        val editor = prefs.edit()
                        editor?.putBoolean(PREF_LAST_AUTO_DOWNLOAD, autoDownload)
                        editor?.apply()
                    }
                    if (vm.username != null) {
                        feed1.username = vm.username
                        feed1.password = vm.password
                    }
                    runOnIOScope { upsert(feed1) {} }
//                    openFeed()
                }
            }
            else -> {
                vm.enableSubscribe = true
                vm.subButTextRes = R.string.subscribe_label
            }
        }
    }

    private fun feedInFeedlist(): Boolean {
        return vm.feedId != 0L
    }

    @UiThread
    private fun showErrorDialog(errorMsg: String?, details: String) {
        if (!isRemoving && !vm.isPaused) {
            val builder = MaterialAlertDialogBuilder(requireContext())
            builder.setTitle(R.string.error_label)
            if (errorMsg != null) {
                val total = """
                    $errorMsg
                    
                    $details
                    """.trimIndent()
                val errorMessage = SpannableString(total)
                errorMessage.setSpan(ForegroundColorSpan(-0x77777778), errorMsg.length, total.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setMessage(errorMessage)
            } else builder.setMessage(R.string.download_error_error_unknown)

            builder.setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int -> dialog.cancel() }
//            if (intent.getBooleanExtra(ARG_WAS_MANUAL_URL, false)) {
//                builder.setNeutralButton(R.string.edit_url_menu) { _: DialogInterface?, _: Int -> editUrl() }
//            }
            builder.setOnCancelListener {
//                setResult(RESULT_ERROR)
//                finish()
            }
            if (vm.dialog != null && vm.dialog!!.isShowing) vm.dialog!!.dismiss()
            vm.dialog = builder.show()
        }
    }

//    private fun editUrl() {
//        val builder = MaterialAlertDialogBuilder(requireContext())
//        builder.setTitle(R.string.edit_url_menu)
//        val dialogBinding = EditTextDialogBinding.inflate(layoutInflater)
//        if (downloader != null) dialogBinding.editText.setText(downloader!!.downloadRequest.source)
//
//        builder.setView(dialogBinding.root)
//        builder.setPositiveButton(R.string.confirm_label) { _: DialogInterface?, _: Int ->
//            setLoadingLayout()
//            lookupUrlAndBuild(dialogBinding.editText.text.toString())
//        }
//        builder.setNegativeButton(R.string.cancel_label) { dialog1: DialogInterface, _: Int -> dialog1.cancel() }
//        builder.setOnCancelListener {}
//        builder.show()
//    }


    private fun showNoPodcastFoundError() {
        requireActivity().runOnUiThread {
            MaterialAlertDialogBuilder(requireContext())
                .setNeutralButton(android.R.string.ok) { _: DialogInterface?, _: Int -> }
                .setTitle(R.string.error_label)
                .setMessage(R.string.null_value_podcast_error)
                .setOnDismissListener {}
                .show()
        }
    }

    companion object {
        const val ARG_FEEDURL: String = "arg.feedurl"
        const val ARG_WAS_MANUAL_URL: String = "manual_url"
        private const val RESULT_ERROR = 2
        private val TAG: String = OnlineFeedFragment::class.simpleName ?: "Anonymous"
        private const val PREFS = "OnlineFeedViewFragmentPreferences"
        private const val PREF_LAST_AUTO_DOWNLOAD = "lastAutoDownload"
        private const val KEY_UP_ARROW = "up_arrow"

        fun newInstance(feedUrl: String, isShared: Boolean = false): OnlineFeedFragment {
            val fragment = OnlineFeedFragment()
            val b = Bundle()
            b.putString(ARG_FEEDURL, feedUrl)
            b.putBoolean("isShared", isShared)
            fragment.arguments = b
            return fragment
        }
    }
}
