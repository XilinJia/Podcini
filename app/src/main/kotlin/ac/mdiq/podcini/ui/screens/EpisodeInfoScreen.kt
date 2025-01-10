package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.net.utils.NetworkUtils.isEpisodeHeadDownloadAllowed
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.seekTo
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.UsageStatistics
import ac.mdiq.podcini.storage.database.Queues.addToQueue
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.unmanaged
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.PlayState
import ac.mdiq.podcini.storage.model.Rating
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.ui.actions.*
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.activity.MainActivity.Screens
import ac.mdiq.podcini.ui.compose.*
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.utils.episodeOnDisplay
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.view.ShownotesWebView
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.IntentUtils
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter.formatDateTimeFlex
import ac.mdiq.podcini.util.MiscFormatter.fullDateTimeString
import android.content.Context
import android.content.ContextWrapper
import android.text.format.Formatter.formatShortFileSize
import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ShareCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import okhttp3.Request.Builder
import java.io.File
import java.util.*

class EpisodeInfoVM(val context: Context, val lcScope: CoroutineScope) {
    internal val actMain: MainActivity? = generateSequence(context) { if (it is ContextWrapper) it.baseContext else null }.filterIsInstance<MainActivity>().firstOrNull()

    internal lateinit var shownotesCleaner: ShownotesCleaner

    internal var itemLoaded = false
    internal var episode: Episode? = null    // managed

    internal var txtvPodcast by mutableStateOf("")
    internal var txtvTitle by mutableStateOf("")
    internal var txtvPublished by mutableStateOf("")
    internal var txtvSize by mutableStateOf("")
    internal var txtvDuration by mutableStateOf("")
    internal var itemLink by mutableStateOf("")
    internal var hasMedia by mutableStateOf(true)
    var rating by mutableStateOf(episode?.rating ?: Rating.UNRATED.code)
    internal var inQueue by mutableStateOf(if (episode != null) curQueue.contains(episode!!) else false)
    var isPlayed by mutableIntStateOf(episode?.playState ?: PlayState.UNSPECIFIED.code)

    var showShareDialog by mutableStateOf(false)

    internal var webviewData by mutableStateOf("")
    internal var showHomeScreen by mutableStateOf(false)
    internal var actionButton1 by mutableStateOf<EpisodeActionButton?>(null)
    internal var actionButton2 by mutableStateOf<EpisodeActionButton?>(null)

    init {
        episode = episodeOnDisplay
    }

    private var eventSink: Job?     = null
    private var eventStickySink: Job? = null
    internal fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
        eventStickySink?.cancel()
        eventStickySink = null
    }
    internal fun procFlowEvents() {
        if (eventSink == null) eventSink = lcScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.QueueEvent -> onQueueEvent(event)
                    is FlowEvent.RatingEvent -> onRatingEvent(event)
                    is FlowEvent.EpisodeEvent -> onEpisodeEvent(event)
//                    is FlowEvent.PlayerSettingsEvent -> updateButtons()
                    is FlowEvent.EpisodePlayedEvent -> load()
                    else -> {}
                }
            }
        }
        if (eventStickySink == null) eventStickySink = lcScope.launch {
            EventFlow.stickyEvents.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.EpisodeDownloadEvent -> onEpisodeDownloadEvent(event)
                    else -> {}
                }
            }
        }
    }

    internal fun updateAppearance() {
        if (episode == null) {
            Logd(TAG, "updateAppearance item is null")
            return
        }

        if (episode!!.feed != null) txtvPodcast = episode!!.feed!!.title ?: ""
        txtvTitle = episode!!.title ?:""
        itemLink = episode!!.link?: ""

        if (episode?.pubDate != null) txtvPublished = formatDateTimeFlex(Date(episode!!.pubDate))

        val media = episode
        when {
            media == null -> txtvSize = ""
            media.size > 0 -> txtvSize = formatShortFileSize(context, media.size)
            isEpisodeHeadDownloadAllowed && !media.checkedOnSizeButUnknown() -> {
                txtvSize = "{faw_spinner}"
                lcScope.launch {
                    val sizeValue = getMediaSize(episode)
                    txtvSize = if (sizeValue <= 0) "" else formatShortFileSize(context, sizeValue)
                }
            }
            else -> txtvSize = ""
        }
        updateButtons()
    }

    private fun updateButtons() {
        val dls = DownloadServiceInterface.get()

        val media: Episode? = episode
        if (media == null) {
            // TODO: what's this?
            if (episode != null) {
//                actionButton1 = VisitWebsiteActionButton(episode!!)
//                butAction1.visibility = View.INVISIBLE
                actionButton2 = VisitWebsiteActionButton(episode!!)
            }
            hasMedia = false
        } else {
            hasMedia = true
            if (media.duration > 0) txtvDuration = DurationConverter.getDurationStringLong(media.duration)
            if (episode != null) {
                actionButton1 = when {
//                        media.getMediaType() == MediaType.FLASH -> VisitWebsiteActionButton(episode!!)
                    InTheatre.isCurrentlyPlaying(media) -> PauseActionButton(episode!!)
                    episode!!.feed != null && episode!!.feed!!.isLocalFeed -> PlayLocalActionButton(episode!!)
                    media.downloaded -> PlayActionButton(episode!!)
                    else -> StreamActionButton(episode!!)
                }
                actionButton2 = when {
                    episode!!.feed?.type == Feed.FeedType.YOUTUBE.name -> VisitWebsiteActionButton(episode!!)
                    dls != null && media.downloadUrl != null && dls.isDownloadingEpisode(media.downloadUrl!!) -> CancelDownloadActionButton(episode!!)
                    !media.downloaded -> DownloadActionButton(episode!!)
                    else -> DeleteActionButton(episode!!)
                }
//                if (actionButton2 != null && media.getMediaType() == MediaType.FLASH) actionButton2!!.visibility = View.GONE
            }
        }
    }

    internal fun openPodcast() {
        if (episode?.feedId == null) return
        feedOnDisplay = episode?.feed ?: Feed()
        mainNavController.navigate(Screens.FeedEpisodes.name)
    }


    private fun onRatingEvent(event: FlowEvent.RatingEvent) {
        if (episode?.id == event.episode.id) {
            episode = unmanaged(episode!!)
            episode!!.rating = event.rating
            rating = episode!!.rating
        }
    }

    private fun onQueueEvent(event: FlowEvent.QueueEvent) {
        if (episode == null) return
        var i = 0
        val size: Int = event.episodes.size
        while (i < size) {
            val item_ = event.episodes[i]
            if (item_.id == episode?.id) {
                inQueue = curQueue.contains(episode!!)
                break
            }
            i++
        }
    }

    private fun onEpisodeEvent(event: FlowEvent.EpisodeEvent) {
//        Logd(TAG, "onEventMainThread() called with ${event.TAG}")
        if (this.episode == null) return
        for (item in event.episodes) {
            if (this.episode!!.id == item.id) {
                load()
                return
            }
        }
    }

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
        if (episode == null) return
        if (!event.urls.contains(episode!!.downloadUrl)) return
        if (itemLoaded) updateButtons()
    }

    private var loadItemsRunning = false
    internal fun load() {
        Logd(TAG, "load() called")
        if (!loadItemsRunning) {
            loadItemsRunning = true
            lcScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        if (episode != null && !episode!!.isRemote.value) episode = realm.query(Episode::class).query("id == $0", episode!!.id).first().find()
                        if (episode != null) {
                            val duration = episode?.duration ?: Int.MAX_VALUE
                            Logd(TAG, "description: ${episode?.description}")
                            val url = episode?.downloadUrl
                            if (url?.contains("youtube.com") == true && episode!!.description?.startsWith("Short:") == true) {
                                Logd(TAG, "getting extended description: ${episode!!.title}")
                                try {
                                    val info = episode!!.streamInfo
                                    if (info?.description?.content != null) {
                                        episode = upsert(episode!!) { it.description = info.description?.content }
                                        webviewData = shownotesCleaner.processShownotes(info.description!!.content, duration)
                                    } else webviewData = shownotesCleaner.processShownotes(episode!!.description ?: "", duration)
                                } catch (e: Exception) { Logd(TAG, "StreamInfo error: ${e.message}") }
                            } else webviewData = shownotesCleaner.processShownotes(episode!!.description ?: "", duration)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Logd(TAG, "chapters: ${episode?.chapters?.size}")
                        Logd(TAG, "files: [${episode?.feed?.fileUrl}] [${episode?.fileUrl}]")
                        if (episode != null) {
                            rating = episode!!.rating
                            inQueue = curQueue.contains(episode!!)
                            isPlayed = episode!!.playState
                        }
                        updateAppearance()
                        itemLoaded = true
                    }
                } catch (e: Throwable) { Log.e(TAG, Log.getStackTraceString(e))
                } finally { loadItemsRunning = false }
            }
        }
    }
}

@Composable
fun EpisodeInfoScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vm = remember(episodeOnDisplay.id) { EpisodeInfoVM(context, scope) }

    //        val displayUpArrow by remember { derivedStateOf { navController.backQueue.size > 1 } }
//        var upArrowVisible by rememberSaveable { mutableStateOf(displayUpArrow) }
//        LaunchedEffect(navController.backQueue) { upArrowVisible = displayUpArrow }

    var displayUpArrow by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    Logd(TAG, "ON_CREATE")
                    vm.shownotesCleaner = ShownotesCleaner(context)
                    vm.updateAppearance()
                    vm.load()
                }
                Lifecycle.Event.ON_START -> {
                    Logd(TAG, "ON_START")
                    vm.procFlowEvents()
                }
                Lifecycle.Event.ON_RESUME -> {
                    Logd(TAG, "ON_RESUME")
                    if (vm.itemLoaded) vm.updateAppearance()
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
            vm.episode = null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val textColor = MaterialTheme.colorScheme.onSurface

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        val context = LocalContext.current
        var expanded by remember { mutableStateOf(false) }
        TopAppBar(title = { Text("") },
            navigationIcon = { IconButton(onClick = { if (mainNavController.previousBackStackEntry != null) mainNavController.popBackStack()
            }) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "") } },
            actions = {
                if (!vm.episode?.link.isNullOrEmpty()) IconButton(onClick = {
                    vm.showHomeScreen = true
                    episodeOnDisplay = vm.episode!!
                    mainNavController.navigate(Screens.EpisodeHome.name)
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_home_work_24), contentDescription = "home") }
                IconButton(onClick = {
                    val url = vm.episode?.getLinkWithFallback()
                    if (url != null) IntentUtils.openInBrowser(context, url)
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_web), contentDescription = "web") }
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (vm.episode != null) DropdownMenuItem(text = { Text(stringResource(R.string.share_notes_label)) }, onClick = {
                        val notes = vm.episode!!.description
                        if (!notes.isNullOrEmpty()) {
                            val shareText = HtmlCompat.fromHtml(notes, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                            val context = context
                            val intent = ShareCompat.IntentBuilder(context)
                                .setType("text/plain")
                                .setText(shareText)
                                .setChooserTitle(R.string.share_notes_label)
                                .createChooserIntent()
                            context.startActivity(intent)
                        }
                        expanded = false
                    })
                    if (vm.episode != null) DropdownMenuItem(text = { Text(stringResource(R.string.share_label)) }, onClick = {
                        vm.showShareDialog = true
                        expanded = false
                    })
                }
            }
        )
    }

    var offerStreaming by remember { mutableStateOf(false) }
    @Composable
    fun OnDemandConfigDialog(onDismiss: () -> Unit) {
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = onDismiss, title = { },
            text = {
                Text(stringResource(if (offerStreaming) R.string.on_demand_config_stream_text else R.string.on_demand_config_download_text))
            },
            confirmButton = {
                TextButton(onClick = {
                    AppPreferences.isStreamOverDownload = offerStreaming
                    // Update all visible lists to reflect new streaming action button
                    //            TODO: need another event type?
                    EventFlow.postEvent(FlowEvent.EpisodePlayedEvent())
                    //        (vm.context as MainActivity).showSnackbarAbovePlayer(R.string.on_demand_config_setting_changed, Snackbar.LENGTH_SHORT)
                    onDismiss()
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = {
                UsageStatistics.doNotAskAgain(UsageStatistics.ACTION_STREAM)
                onDismiss()
            }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    var showOnDemandConfigDialog by remember { mutableStateOf(false) }
    if (showOnDemandConfigDialog) OnDemandConfigDialog { showOnDemandConfigDialog = false }

    var showEditComment by remember { mutableStateOf(false) }
    val localTime = remember { System.currentTimeMillis() }
    var editCommentText by remember { mutableStateOf(TextFieldValue((if (vm.episode?.comment.isNullOrBlank()) "" else vm.episode!!.comment + "\n") + fullDateTimeString(localTime) + ":\n")) }
    var commentTextState by remember { mutableStateOf(TextFieldValue(vm.episode?.comment?:"")) }
    if (showEditComment) LargeTextEditingDialog(textState = editCommentText, onTextChange = { editCommentText = it }, onDismissRequest = {showEditComment = false},
        onSave = {
            runOnIOScope { if (vm.episode != null) vm.episode = upsert(vm.episode!!) {
                it.comment = editCommentText.text
                it.commentTime = localTime
            } }
        })

    var showChooseRatingDialog by remember { mutableStateOf(false) }
    if (showChooseRatingDialog) ChooseRatingDialog(listOf(vm.episode!!)) { showChooseRatingDialog = false }

    var showChaptersDialog by remember { mutableStateOf(false) }
    if (showChaptersDialog && vm.episode != null) ChaptersDialog(media = vm.episode!!, onDismissRequest = {showChaptersDialog = false})

    var showPlayStateDialog by remember { mutableStateOf(false) }
    if (showPlayStateDialog) PlayStateDialog(listOf(vm.episode!!)) { showPlayStateDialog = false }

    if (vm.showShareDialog && vm.episode != null && vm.actMain != null) ShareDialog(vm.episode!!, vm.actMain) { vm.showShareDialog = false }

    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                val imgLoc = vm.episode?.getEpisodeListImageLocation()
                AsyncImage(model = imgLoc, contentDescription = "imgvCover", error = painterResource(R.mipmap.ic_launcher), modifier = Modifier.width(56.dp).height(56.dp).clickable(onClick = { vm.openPodcast() }))
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(vm.txtvPodcast, color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable { vm.openPodcast() })
                    Text(vm.txtvTitle, color = textColor, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth(), maxLines = 5, overflow = TextOverflow.Ellipsis)
                    Text("${vm.txtvPublished} · ${vm.txtvDuration} · ${vm.txtvSize}", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(0.4f))
                val playedIconRes = PlayState.fromCode(vm.isPlayed).res
                Icon(imageVector = ImageVector.vectorResource(playedIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "isPlayed",
                    modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(24.dp).height(24.dp).clickable(onClick = { showPlayStateDialog = true }))
                if (vm.episode != null && !vm.inQueue) {
                    Spacer(modifier = Modifier.weight(0.2f))
                    val inQueueIconRes = R.drawable.ic_playlist_remove
                    Icon(imageVector = ImageVector.vectorResource(inQueueIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "inQueue",
                        modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(24.dp).height(24.dp).clickable(onClick = { addToQueue(vm.episode!!) }))
                }
                Spacer(modifier = Modifier.weight(0.2f))
                Logd(TAG, "ratingIconRes rating: ${vm.rating}")
                val ratingIconRes = Rating.fromCode(vm.rating).res
                Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                    modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(24.dp).height(24.dp).clickable(onClick = { showChooseRatingDialog = true }))
                Spacer(modifier = Modifier.weight(0.2f))
                if (vm.hasMedia) Icon(imageVector = ImageVector.vectorResource(vm.actionButton1?.drawable ?: R.drawable.ic_questionmark), tint = textColor, contentDescription = "butAction1",
                    modifier = Modifier.width(24.dp).height(24.dp).clickable(onClick = {
                        when {
                            vm.actionButton1 is StreamActionButton && !AppPreferences.isStreamOverDownload
                                    && UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_STREAM) -> {
                                offerStreaming = true
                                showOnDemandConfigDialog = true
//                                showOnDemandConfigBalloon(true)
                                return@clickable
                            }
                            vm.actionButton1 == null -> return@clickable  // Not loaded yet
                            else -> vm.actionButton1?.onClick(context)
                        }
                    }))
                Spacer(modifier = Modifier.weight(0.2f))
                Box(modifier = Modifier.width(40.dp).height(40.dp).align(Alignment.CenterVertically), contentAlignment = Alignment.Center) {
                    Icon(imageVector = ImageVector.vectorResource(vm.actionButton2?.drawable ?: R.drawable.ic_questionmark), tint = textColor, contentDescription = "butAction2", modifier = Modifier.width(24.dp).height(24.dp).clickable {
                        when {
                            vm.actionButton2 is DownloadActionButton && AppPreferences.isStreamOverDownload
                                    && UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_DOWNLOAD) -> {
                                offerStreaming = false
                                showOnDemandConfigDialog = true
//                                showOnDemandConfigBalloon(false)
                                return@clickable
                            }
                            vm.actionButton2 == null -> return@clickable  // Not loaded yet
                            else -> vm.actionButton2?.onClick(context)
                        }
                    })
                }
                Spacer(modifier = Modifier.weight(0.4f))
            }
            if (!vm.hasMedia) Text("noMediaLabel", color = textColor, style = MaterialTheme.typography.bodyMedium)
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                    ShownotesWebView(context).apply {
                        setTimecodeSelectedListener { time: Int -> seekTo(time) }
                        setPageFinishedListener {
                            // Restoring the scroll position might not always work
                            postDelayed({ }, 50)
                        }
                    }
                }, update = {
                    it.loadDataWithBaseURL("https://127.0.0.1", vm.webviewData, "text/html", "utf-8", "about:blank")
                })
                if (!vm.episode?.chapters.isNullOrEmpty()) Text(stringResource(id = R.string.chapters_label), color = textColor, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable(onClick = { showChaptersDialog = true }))
                Text(stringResource(R.string.my_opinion_label) + if (commentTextState.text.isEmpty()) " (Add)" else "",
                    color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom,
                    modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable { showEditComment = true })
                Text(commentTextState.text, color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 15.dp, bottom = 10.dp))
                Text(vm.itemLink, color = textColor, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private const val TAG: String = "EpisodeInfoScreen"

private suspend fun getMediaSize(episode: Episode?) : Long {
    return withContext(Dispatchers.IO) {
        if (!isEpisodeHeadDownloadAllowed) return@withContext -1
        val media = episode ?: return@withContext -1

        var size = Int.MIN_VALUE.toLong()
        when {
            media.downloaded -> {
                val url = media.fileUrl
                if (!url.isNullOrEmpty()) {
                    val mediaFile = File(url)
                    if (mediaFile.exists()) size = mediaFile.length()
                }
            }
            !media.checkedOnSizeButUnknown() -> {
                // only query the network if we haven't already checked

                val url = media.downloadUrl
                if (url.isNullOrEmpty()) return@withContext -1

                val client = getHttpClient()
                val httpReq: Builder = Builder().url(url).header("Accept-Encoding", "identity").head()
                try {
                    val response = client.newCall(httpReq.build()).execute()
                    if (response.isSuccessful) {
                        val contentLength = response.header("Content-Length")?:"0"
                        try {
                            size = contentLength.toInt().toLong()
                        } catch (e: NumberFormatException) {
                            Log.e(TAG, Log.getStackTraceString(e))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, Log.getStackTraceString(e))
                    return@withContext -1  // better luck next time
                }
            }
        }
        // they didn't tell us the size, but we don't want to keep querying on it
        upsert(episode) {
            if (size <= 0) it.setCheckedOnSizeButUnknown()
            else it.size = size
        }
        size
    }
}
