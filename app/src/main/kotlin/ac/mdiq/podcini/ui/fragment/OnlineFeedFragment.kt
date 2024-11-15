package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.ComposeFragmentBinding
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.feed.FeedBuilder
import ac.mdiq.podcini.net.feed.FeedUrlNotFoundException
import ac.mdiq.podcini.net.feed.searcher.CombinedSearcher
import ac.mdiq.podcini.net.feed.searcher.PodcastSearcherRegistry
import ac.mdiq.podcini.net.utils.HtmlToPlainText
import ac.mdiq.podcini.preferences.UserPreferences.isEnableAutodownload
import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.database.Feeds.getFeedByTitleAndAuthor
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.Feeds.isSubscribed
import ac.mdiq.podcini.storage.database.Feeds.persistFeedPreferences
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.Rating.Companion.fromCode
import ac.mdiq.podcini.storage.model.SubscriptionLog.Companion.feedLogsMap
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter.formatAbbrev
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.collection.ArrayMap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.IOException
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

    private var _binding: ComposeFragmentBinding? = null
    private val binding get() = _binding!!

    private var displayUpArrow = false

    var feedSource: String = ""
    private var feedUrl: String = ""
    private var urlToLog: String = ""
    private lateinit var feedBuilder: FeedBuilder
    private var showYTChannelDialog by mutableStateOf(false)

    private var isShared: Boolean = false

    private var showFeedDisplay by mutableStateOf(false)
    private var showProgress by mutableStateOf(true)
    private var autoDownloadChecked by mutableStateOf(false)
    private var enableSubscribe by mutableStateOf(true)
    private var enableEpisodes by mutableStateOf(true)
    private var subButTextRes by mutableIntStateOf(R.string.subscribe_label)

    private val feedId: Long
        get() {
            if (feeds == null) return 0
            for (f in feeds!!) {
                if (f.downloadUrl == selectedDownloadUrl) return f.id
            }
            return 0
        }

    @Volatile
    private var feeds: List<Feed>? = null
    private var feed by mutableStateOf<Feed?>(null)
    private var selectedDownloadUrl: String? = null
//    private var downloader: Downloader? = null
    private var username: String? = null
    private var password: String? = null

    private var isPaused = false
    private var didPressSubscribe = false
    private var isFeedFoundBySearch = false

    private var dialog: Dialog? = null

     override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logd(TAG, "fragment onCreateView")
        _binding = ComposeFragmentBinding.inflate(layoutInflater)
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
        (activity as MainActivity).setupToolbarToggle(binding.toolbar, displayUpArrow)

        feedUrl = requireArguments().getString(ARG_FEEDURL) ?: ""
        isShared = requireArguments().getBoolean("isShared")
        Logd(TAG, "feedUrl: $feedUrl")
        feedBuilder = FeedBuilder(requireContext()) { message, details -> showErrorDialog(message, details) }

        binding.mainView.setContent {
            CustomTheme(requireContext()) {
                if (showYTChannelDialog) feedBuilder.ConfirmYTChannelTabsDialog(onDismissRequest = {showYTChannelDialog = false}) {feed, map ->  handleFeed(feed, map)}
                MainView()
            }
        }
        if (feedUrl.isEmpty()) {
            Log.e(TAG, "feedUrl is null.")
            showNoPodcastFoundError()
        } else {
            Logd(TAG, "Activity was started with url $feedUrl")
            showProgress = true
            // Remove subscribeonandroid.com from feed URL in order to subscribe to the actual feed URL
            if (feedUrl.contains("subscribeonandroid.com")) feedUrl = feedUrl.replaceFirst("((www.)?(subscribeonandroid.com/))".toRegex(), "")
            if (savedInstanceState != null) {
                username = savedInstanceState.getString("username")
                password = savedInstanceState.getString("password")
            }
            lookupUrlAndBuild(feedUrl)
        }
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        isPaused = false
        procFlowEvents()
    }

    override fun onStop() {
        super.onStop()
        isPaused = true
        cancelFlowEvents()
//        if (downloader != null && !downloader!!.isFinished) downloader!!.cancel()
        if (dialog != null && dialog!!.isShowing) dialog!!.dismiss()
    }

    override fun onDestroy() {
        _binding = null
        feeds = null
        super.onDestroy()
    }

     override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
        outState.putString("username", username)
        outState.putString("password", password)
    }

    private fun handleFeed(feed_: Feed, map: Map<String, String>) {
        selectedDownloadUrl = feedBuilder.selectedDownloadUrl
        feed = feed_
        if (isShared) {
            val log = realm.query(ShareLog::class).query("url == $0", urlToLog).first().find()
            if (log != null) upsertBlk(log) {
                it.title = feed_.title
                it.author = feed_.author
            }
        }
        showFeedInformation(feed_, map)
    }

    private fun lookupUrlAndBuild(url: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            urlToLog = url
            val urlString = PodcastSearcherRegistry.lookupUrl1(url)
            try {
                feeds = getFeedList()
                if (feedBuilder.isYoutube(urlString)) {
                    if (feedBuilder.isYoutubeChannel()) {
                        val nTabs = feedBuilder.youtubeChannelValidTabs()
                        if (nTabs > 1) showYTChannelDialog = true
                        else feedBuilder.buildYTChannel(0, "") { feed_, map -> handleFeed(feed_, map) }
                    } else feedBuilder.buildYTPlaylist { feed_, map -> handleFeed(feed_, map) }
                } else feedBuilder.buildPodcast(urlString, username, password) { feed_, map -> handleFeed(feed_, map) }
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
        lifecycleScope.launch(Dispatchers.IO) {
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
                urlToLog = url
                Logd(TAG, "Successfully retrieve feed url")
                isFeedFoundBySearch = true
                feeds = getFeedList()
                if (feedBuilder.isYoutube(url)) {
                    if (feedBuilder.isYoutubeChannel()) {
                        val nTabs = feedBuilder.youtubeChannelValidTabs()
                        if (nTabs > 1) showYTChannelDialog = true
                        else feedBuilder.buildYTChannel(0, "") { feed_, map -> handleFeed(feed_, map) }
                    } else feedBuilder.buildYTPlaylist { feed_, map -> handleFeed(feed_, map) }
                } else feedBuilder.buildPodcast(url, username, password) { feed_, map -> handleFeed(feed_, map) }
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
                    this@OnlineFeedFragment.feeds = feeds
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
        showProgress = false
//        binding.feedDisplayContainer.visibility = View.VISIBLE
        showFeedDisplay = true
        if (isFeedFoundBySearch) {
            val resId = R.string.no_feed_url_podcast_found_by_search
            Snackbar.make(binding.root, resId, Snackbar.LENGTH_LONG).show()
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
    fun MainView() {
        val textColor = MaterialTheme.colorScheme.onSurface
        val feedLogsMap_ = feedLogsMap!!
        ConstraintLayout(modifier = Modifier.fillMaxSize()) {
            val (progressBar, main) = createRefs()
            if (showProgress) CircularProgressIndicator(progress = { 0.6f },
                strokeWidth = 10.dp, modifier = Modifier.size(50.dp).constrainAs(progressBar) { centerTo(parent) })
            else Column(modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp).constrainAs(main) {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start)
            }) {
                if (showFeedDisplay) ConstraintLayout(modifier = Modifier.fillMaxWidth().height(120.dp).background(MaterialTheme.colorScheme.surface)) {
                    val (backgroundImage, coverImage, taColumn, buttons, closeButton) = createRefs()
//                    if (false) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_settings_white), contentDescription = "background",
//                        Modifier.fillMaxWidth().height(120.dp).constrainAs(backgroundImage) {
//                            top.linkTo(parent.top)
//                            bottom.linkTo(parent.bottom)
//                            start.linkTo(parent.start)
//                        })
                    AsyncImage(model = feed?.imageUrl?:"", contentDescription = "coverImage", error = painterResource(R.mipmap.ic_launcher),
                        modifier = Modifier.width(100.dp).height(100.dp).padding(start = 10.dp, end = 16.dp, bottom = 10.dp).constrainAs(coverImage) {
                            bottom.linkTo(parent.bottom)
                            start.linkTo(parent.start)
                        }.clickable(onClick = {}))
                    Column(Modifier.constrainAs(taColumn) {
                        top.linkTo(coverImage.top)
                        start.linkTo(coverImage.end) }) {
                        Text(feed?.title?:"No title", color = textColor, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(feed?.author?:"", color = textColor, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(Modifier.constrainAs(buttons) {
                        start.linkTo(coverImage.end)
                        top.linkTo(taColumn.bottom)
                        end.linkTo(parent.end)
                    }) {
                        Spacer(modifier = Modifier.weight(0.2f))
                        if (enableSubscribe) Button(onClick = {
                            if (feedInFeedlist() || isSubscribed(feed!!)) {
                                if (isShared) {
                                    val log = realm.query(ShareLog::class).query("url == $0", feedUrl).first().find()
                                    if (log != null) upsertBlk(log) { it.status = ShareLog.Status.EXISTING.ordinal }
                                }
                                val feed = getFeedByTitleAndAuthor(feed?.eigenTitle?:"", feed?.author?:"")
                                if (feed != null ) (activity as MainActivity).loadChildFragment(FeedInfoFragment.newInstance(feed))
//                                (activity as MainActivity).loadFeedFragmentById(feedId, null)
                            }
                            else {
                                enableSubscribe = false
                                enableEpisodes = false
                                CoroutineScope(Dispatchers.IO).launch {
                                    feedBuilder.subscribe(feed!!)
                                    if (isShared) {
                                        val log = realm.query(ShareLog::class).query("url == $0", feedUrl).first().find()
                                        if (log != null) upsertBlk(log) { it.status = ShareLog.Status.SUCCESS.ordinal }
                                    }
                                    withContext(Dispatchers.Main) {
                                        enableSubscribe = true
                                        didPressSubscribe = true
                                        handleUpdatedFeedStatus()
                                    }
                                }
                            }
                        }) { Text(stringResource(subButTextRes)) }
                        Spacer(modifier = Modifier.weight(0.1f))
                        if (enableEpisodes && feed != null) Button(onClick = { showEpisodes(feed!!.episodes) }) { Text(stringResource(R.string.episodes_label)) }
                        Spacer(modifier = Modifier.weight(0.2f))
                    }
//                    if (false) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_close_white), contentDescription = null, modifier = Modifier
//                        .constrainAs(closeButton) {
//                            top.linkTo(parent.top)
//                            end.linkTo(parent.end)
//                        })
                }
                Column {
//                    alternate_urls_spinner
                    if (feedSource != "VistaGuide" && isEnableAutodownload) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoDownloadChecked, onCheckedChange = { autoDownloadChecked = it })
                        Text(text = stringResource(R.string.auto_download_label),
                            style = MaterialTheme.typography.bodyMedium.merge(), color = textColor,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
                val scrollState = rememberScrollState()
                var numEpisodes by remember { mutableIntStateOf(feed?.episodes?.size?:0) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(1000)
                        numEpisodes = feed?.episodes?.size?:0
                    }
                }
                Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
                    Text("$numEpisodes episodes", color = textColor, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 5.dp, bottom = 10.dp))
                    Text(stringResource(R.string.description_label), color = textColor, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
                    Text(HtmlToPlainText.getPlainText(feed?.description ?: ""), color = textColor, style = MaterialTheme.typography.bodyMedium)
                    val sLog = remember {feedLogsMap_[feed?.downloadUrl?:""] }
                    if (sLog != null) {
                        val commentTextState by remember { mutableStateOf(TextFieldValue(sLog.comment)) }
                        val context = LocalContext.current
                        val cancelDate = remember { formatAbbrev(context, Date(sLog.cancelDate)) }
                        val ratingRes = remember { fromCode(sLog.rating).res }
                        if (commentTextState.text.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp)) {
                                Text(stringResource(R.string.my_opinion_label), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                                Icon(imageVector = ImageVector.vectorResource(ratingRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = null, modifier = Modifier.padding(start = 5.dp))
                            }
                            Text(commentTextState.text, color = textColor, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 15.dp, bottom = 10.dp))
                            Text(stringResource(R.string.cancelled_on_label) + ": " + cancelDate, color = textColor, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 15.dp, bottom = 10.dp))
                        }
                    }
                    Text(feed?.mostRecentItem?.title ?: "", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
                    Text("${feed?.language?:""} ${feed?.type ?: ""} ${feed?.lastUpdate ?: ""}", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
                    Text(feed?.link?:"", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
                    Text(feed?.downloadUrl?:"", color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 5.dp, bottom = 4.dp))
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
            episodes[i].media?.id = episodes[i].id
            episodes[i].isRemote.value = true
        }
        val fragment: Fragment = RemoteEpisodesFragment.newInstance(episodes)
        (activity as MainActivity).loadChildFragment(fragment)
    }

     private fun handleUpdatedFeedStatus() {
        val dli = DownloadServiceInterface.get()
        if (dli == null || selectedDownloadUrl == null) return

        when {
//            feedSource != "VistaGuide" -> {
//                binding.subscribeButton.isEnabled = false
//            }
            dli.isDownloadingEpisode(selectedDownloadUrl!!) -> {
                enableSubscribe = false
                subButTextRes = R.string.subscribe_label
            }
            feedInFeedlist() -> {
                enableSubscribe = true
                subButTextRes = R.string.open
                if (didPressSubscribe) {
                    didPressSubscribe = false
                    val feed1 = getFeed(feedId, true)?: return
                    if (feed1.preferences == null) feed1.preferences = FeedPreferences(feed1.id, false,
                        FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, "", "")
                    if (feedSource == "VistaGuide") {
                        feed1.preferences!!.prefStreamOverDownload = true
                        feed1.preferences!!.autoDownload = false
                    } else if (isEnableAutodownload) {
                        val autoDownload = autoDownloadChecked
                        feed1.preferences!!.autoDownload = autoDownload
                        val editor = prefs.edit()
                        editor?.putBoolean(PREF_LAST_AUTO_DOWNLOAD, autoDownload)
                        editor?.apply()
                    }
                    if (username != null) {
                        feed1.preferences!!.username = username
                        feed1.preferences!!.password = password
                    }
                    persistFeedPreferences(feed1)
//                    openFeed()
                }
            }
            else -> {
                enableSubscribe = true
                subButTextRes = R.string.subscribe_label
            }
        }
    }

    private fun feedInFeedlist(): Boolean {
        return feedId != 0L
    }

    @UiThread
    private fun showErrorDialog(errorMsg: String?, details: String) {
        if (!isRemoving && !isPaused) {
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
            if (dialog != null && dialog!!.isShowing) dialog!!.dismiss()
            dialog = builder.show()
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

    /**
     *
     * @return true if a FeedDiscoveryDialog is shown, false otherwise (e.g., due to no feed found).
     */
//    private fun showFeedDiscoveryDialog(feedFile: File, baseUrl: String): Boolean {
//        val fd = FeedDiscoverer()
//        val urlsMap: Map<String, String>
//        try {
//            urlsMap = fd.findLinks(feedFile, baseUrl)
//            if (urlsMap.isEmpty()) return false
//        } catch (e: IOException) {
//            e.printStackTrace()
//            return false
//        }
//
//        if (isRemoving || isPaused) return false
//        val titles: MutableList<String?> = ArrayList()
//        val urls: List<String> = ArrayList(urlsMap.keys)
//        for (url in urls) {
//            titles.add(urlsMap[url])
//        }
//        if (urls.size == 1) {
//            // Skip dialog and display the item directly
//            feeds = getFeedList()
//            subscribe.startFeedBuilding(urls[0]) {feed, map -> showFeedInformation(feed, map) }
//            return true
//        }
//        val adapter = ArrayAdapter(requireContext(), R.layout.ellipsize_start_listitem, R.id.txtvTitle, titles)
//        val onClickListener = DialogInterface.OnClickListener { dialog: DialogInterface, which: Int ->
//            val selectedUrl = urls[which]
//            dialog.dismiss()
//            feeds = getFeedList()
//            subscribe.startFeedBuilding(selectedUrl) {feed, map -> showFeedInformation(feed, map) }
//        }
//        val ab = MaterialAlertDialogBuilder(requireContext())
//            .setTitle(R.string.feeds_label)
//            .setCancelable(true)
//            .setOnCancelListener { _: DialogInterface? ->/*                finish() */ }
//            .setAdapter(adapter, onClickListener)
//        requireActivity().runOnUiThread {
//            if (dialog != null && dialog!!.isShowing) dialog!!.dismiss()
//            dialog = ab.show()
//        }
//        return true
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

//    private inner class FeedViewAuthenticationDialog(context: Context, titleRes: Int, private val feedUrl: String) :
//        AuthenticationDialog(context, titleRes, true, username, password) {
//        override fun onConfirmed(username: String, password: String) {
//            this@OnlineFeedFragment.username = username
//            this@OnlineFeedFragment.password = password
//            feeds = getFeedList()
//            subscribe.startFeedBuilding(feedUrl) {feed, map -> showFeedInformation(feed, map) }
//        }
//    }

    /**
     * Finds RSS/Atom URLs in a HTML document using the auto-discovery techniques described here:
     * http://www.rssboard.org/rss-autodiscovery
     * http://blog.whatwg.org/feed-autodiscovery
     */
    class FeedDiscoverer {
        /**
         * Discovers links to RSS and Atom feeds in the given File which must be a HTML document.
         * @return A map which contains the feed URLs as keys and titles as values (the feed URL is also used as a title if
         * a title cannot be found).
         */
        @Throws(IOException::class)
        fun findLinks(inVal: File, baseUrl: String): Map<String, String> {
            return findLinks(Jsoup.parse(inVal), baseUrl)
        }
        /**
         * Discovers links to RSS and Atom feeds in the given File which must be a HTML document.
         * @return A map which contains the feed URLs as keys and titles as values (the feed URL is also used as a title if
         * a title cannot be found).
         */
        fun findLinks(inVal: String, baseUrl: String): Map<String, String> {
            return findLinks(Jsoup.parse(inVal), baseUrl)
        }
        private fun findLinks(document: Document, baseUrl: String): Map<String, String> {
            val res: MutableMap<String, String> = ArrayMap()
            val links = document.head().getElementsByTag("link")
            for (link in links) {
                val rel = link.attr("rel")
                val href = link.attr("href")
                if (href.isNotEmpty() && (rel == "alternate" || rel == "feed")) {
                    val type = link.attr("type")
                    if (type == MIME_RSS || type == MIME_ATOM) {
                        val title = link.attr("title")
                        val processedUrl = processURL(baseUrl, href)
                        if (processedUrl != null) res[processedUrl] = title.ifEmpty { href }
                    }
                }
            }
            return res
        }
        private fun processURL(baseUrl: String, strUrl: String): String? {
            val uri = Uri.parse(strUrl)
            if (uri.isRelative) {
                val res = Uri.parse(baseUrl).buildUpon().path(strUrl).build()
                return res?.toString()
            } else return strUrl
        }
        companion object {
            private const val MIME_RSS = "application/rss+xml"
            private const val MIME_ATOM = "application/atom+xml"
        }
    }

    class RemoteEpisodesFragment : BaseEpisodesFragment() {
        private val episodeList: MutableList<Episode> = mutableListOf()

         override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val root = super.onCreateView(inflater, container, savedInstanceState)
            Logd(TAG, "fragment onCreateView")
            toolbar.inflateMenu(R.menu.episodes)
            toolbar.setTitle(R.string.episodes_label)
            updateToolbar()
            return root
        }
        override fun onDestroyView() {
            episodeList.clear()
            super.onDestroyView()
        }
        fun setEpisodes(episodeList_: MutableList<Episode>) {
            episodeList.clear()
            episodeList.addAll(episodeList_)
        }
        override fun loadData(): List<Episode> {
            if (episodeList.isEmpty()) return listOf()
            return episodeList
        }
        override fun loadTotalItemCount(): Int {
            return episodeList.size
        }
        override fun getPrefName(): String {
            return PREF_NAME
        }
        override fun updateToolbar() {
            binding.toolbar.menu.findItem(R.id.episodes_sort).isVisible = false
//        binding.toolbar.menu.findItem(R.id.refresh_item).setVisible(false)
            binding.toolbar.menu.findItem(R.id.action_search).isVisible = false
//            binding.toolbar.menu.findItem(R.id.action_favorites).setVisible(false)
            binding.toolbar.menu.findItem(R.id.filter_items).isVisible = false
            infoBarText.value = "${episodes.size} episodes"
        }
         override fun onMenuItemClick(item: MenuItem): Boolean {
            if (super.onOptionsItemSelected(item)) return true
            when (item.itemId) {
                else -> return false
            }
        }

        companion object {
            const val PREF_NAME: String = "EpisodesListFragment"

            fun newInstance(episodes: MutableList<Episode>): RemoteEpisodesFragment {
                val i = RemoteEpisodesFragment()
                i.setEpisodes(episodes)
                return i
            }
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

//        var prefs: SharedPreferences? = null
//        fun getSharedPrefs(context: Context) {
//            if (prefs == null) prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
//        }

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
