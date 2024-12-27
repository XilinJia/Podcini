package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.net.utils.NetworkUtils.isEpisodeHeadDownloadAllowed
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.seekTo
import ac.mdiq.podcini.preferences.UsageStatistics
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.database.Queues.addToQueue
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.unmanaged
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.ui.actions.*
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.*
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.utils.ThemeUtils
import ac.mdiq.podcini.ui.view.ShownotesWebView
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.IntentUtils
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter.formatDateTimeFlex
import ac.mdiq.podcini.util.MiscFormatter.fullDateTimeString
import android.os.Bundle
import android.text.TextUtils
import android.text.format.Formatter.formatShortFileSize
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.google.android.material.snackbar.Snackbar
import com.skydoves.balloon.ArrowOrientation
import com.skydoves.balloon.ArrowOrientationRules
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request.Builder
import java.io.File
import java.util.*

class EpisodeInfoFragment : Fragment() {
    private var homeFragment: EpisodeHomeFragment? = null
    private lateinit var shownotesCleaner: ShownotesCleaner

    private var itemLoaded = false
    private var episode: Episode? = null    // managed

    private var txtvPodcast by mutableStateOf("")
    private var txtvTitle by mutableStateOf("")
    private var txtvPublished by mutableStateOf("")
    private var txtvSize by mutableStateOf("")
    private var txtvDuration by mutableStateOf("")
    private var itemLink by mutableStateOf("")
    private var hasMedia by mutableStateOf(true)
    var rating by mutableStateOf(episode?.rating ?: Rating.UNRATED.code)
    private var inQueue by mutableStateOf(if (episode != null) curQueue.contains(episode!!) else false)
    var isPlayed by mutableIntStateOf(episode?.playState ?: PlayState.UNSPECIFIED.code)

    var showShareDialog by mutableStateOf(false)

    private var webviewData by mutableStateOf("")
    private var showHomeScreen by mutableStateOf(false)
    private var actionButton1 by mutableStateOf<EpisodeActionButton?>(null)
    private var actionButton2 by mutableStateOf<EpisodeActionButton?>(null)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        Logd(TAG, "fragment onCreateView")

        val composeView = ComposeView(requireContext()).apply { setContent { CustomTheme(requireContext()) { MainView() } } }
        shownotesCleaner = ShownotesCleaner(requireContext())
        updateAppearance()
        load()
        return composeView
    }

    @Composable
    fun MainView() {
        val textColor = MaterialTheme.colorScheme.onSurface

        var showEditComment by remember { mutableStateOf(false) }
        val localTime = remember { System.currentTimeMillis() }
        var editCommentText by remember { mutableStateOf(TextFieldValue((if (episode?.comment.isNullOrBlank()) "" else episode!!.comment + "\n") + fullDateTimeString(localTime) + ":\n")) }
        var commentTextState by remember { mutableStateOf(TextFieldValue(episode?.comment?:"")) }
        if (showEditComment) LargeTextEditingDialog(textState = editCommentText, onTextChange = { editCommentText = it }, onDismissRequest = {showEditComment = false},
            onSave = {
                runOnIOScope { if (episode != null) episode = upsert(episode!!) {
                    it.comment = editCommentText.text
                    it.commentTime = localTime
                } }
            })

        var showChooseRatingDialog by remember { mutableStateOf(false) }
        if (showChooseRatingDialog) ChooseRatingDialog(listOf(episode!!)) { showChooseRatingDialog = false }

        var showChaptersDialog by remember { mutableStateOf(false) }
        if (showChaptersDialog && episode != null) ChaptersDialog(media = episode!!, onDismissRequest = {showChaptersDialog = false})

        var showPlayStateDialog by remember { mutableStateOf(false) }
        if (showPlayStateDialog) PlayStateDialog(listOf(episode!!)) { showPlayStateDialog = false }

        if (showShareDialog && episode != null) ShareDialog(episode!!, requireActivity()) { showShareDialog = false }

        Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val imgLoc = episode?.getEpisodeListImageLocation()
                    AsyncImage(model = imgLoc, contentDescription = "imgvCover", error = painterResource(R.mipmap.ic_launcher), modifier = Modifier.width(56.dp).height(56.dp).clickable(onClick = { openPodcast() }))
                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        Text(txtvPodcast, color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().clickable { openPodcast() })
                        Text(txtvTitle, color = textColor, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth(), maxLines = 5, overflow = TextOverflow.Ellipsis)
                        Text("$txtvPublished · $txtvDuration · $txtvSize", color = textColor, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.weight(0.4f))
                    val playedIconRes = PlayState.fromCode(isPlayed).res
                    Icon(imageVector = ImageVector.vectorResource(playedIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "isPlayed",
                        modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(24.dp).height(24.dp).clickable(onClick = { showPlayStateDialog = true }))
                    if (episode != null && !inQueue) {
                        Spacer(modifier = Modifier.weight(0.2f))
                        val inQueueIconRes = R.drawable.ic_playlist_remove
                        Icon(imageVector = ImageVector.vectorResource(inQueueIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "inQueue",
                            modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(24.dp).height(24.dp).clickable(onClick = { addToQueue(episode!!) }))
                    }
                    Spacer(modifier = Modifier.weight(0.2f))
                    Logd(TAG, "ratingIconRes rating: $rating")
                    val ratingIconRes = Rating.fromCode(rating).res
                    Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                        modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(24.dp).height(24.dp).clickable(onClick = { showChooseRatingDialog = true }))
                    Spacer(modifier = Modifier.weight(0.2f))
                    if (hasMedia) Icon(imageVector = ImageVector.vectorResource(actionButton1?.getDrawable() ?: R.drawable.ic_questionmark), tint = textColor, contentDescription = "butAction1",
                        modifier = Modifier.width(24.dp).height(24.dp).clickable(onClick = {
                            when {
                                actionButton1 is StreamActionButton && !UserPreferences.isStreamOverDownload
                                        && UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_STREAM) -> {
                                    showOnDemandConfigBalloon(true)
                                    return@clickable
                                }
                                actionButton1 == null -> return@clickable  // Not loaded yet
                                else -> actionButton1?.onClick(requireContext())
                            }
                        }))
                    Spacer(modifier = Modifier.weight(0.2f))
                    Box(modifier = Modifier.width(40.dp).height(40.dp).align(Alignment.CenterVertically), contentAlignment = Alignment.Center) {
                        Icon(imageVector = ImageVector.vectorResource(actionButton2?.getDrawable() ?: R.drawable.ic_questionmark), tint = textColor, contentDescription = "butAction2", modifier = Modifier.width(24.dp).height(24.dp).clickable {
                            when {
                                actionButton2 is DownloadActionButton && UserPreferences.isStreamOverDownload
                                        && UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_DOWNLOAD) -> {
                                    showOnDemandConfigBalloon(false)
                                    return@clickable
                                }
                                actionButton2 == null -> return@clickable  // Not loaded yet
                                else -> actionButton2?.onClick(requireContext())
                            }
                        })
                    }
                    Spacer(modifier = Modifier.weight(0.4f))
                }
                if (!hasMedia) Text("noMediaLabel", color = textColor, style = MaterialTheme.typography.bodyMedium)
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
                        it.loadDataWithBaseURL("https://127.0.0.1", webviewData, "text/html", "utf-8", "about:blank")
                    })
                    if (!episode?.chapters.isNullOrEmpty()) Text(stringResource(id = R.string.chapters_label), color = textColor, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable(onClick = { showChaptersDialog = true }))
                    Text(stringResource(R.string.my_opinion_label) + if (commentTextState.text.isEmpty()) " (Add)" else "",
                        color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom,
                        modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable { showEditComment = true })
                    Text(commentTextState.text, color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 15.dp, bottom = 10.dp))
                    Text(itemLink, color = textColor, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        TopAppBar(title = { Text("") },
            navigationIcon = { IconButton(onClick = { parentFragmentManager.popBackStack() }) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "") } },
            actions = {
                if (!episode?.link.isNullOrEmpty()) IconButton(onClick = {
                    showHomeScreen = true
                    homeFragment = EpisodeHomeFragment.newInstance(episode!!)
                    (activity as MainActivity).loadChildFragment(homeFragment!!)
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_home_work_24), contentDescription = "home") }
                IconButton(onClick = {
                    val url = episode?.getLinkWithFallback()
                    if (url != null) IntentUtils.openInBrowser(requireContext(), url)
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_web), contentDescription = "web") }
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (episode != null) DropdownMenuItem(text = { Text(stringResource(R.string.share_notes_label)) }, onClick = {
                        val notes = episode!!.description
                        if (!notes.isNullOrEmpty()) {
                            val shareText = HtmlCompat.fromHtml(notes, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                            val context = requireContext()
                            val intent = ShareCompat.IntentBuilder(context)
                                .setType("text/plain")
                                .setText(shareText)
                                .setChooserTitle(R.string.share_notes_label)
                                .createChooserIntent()
                            context.startActivity(intent)
                        }
                        expanded = false
                    })
                    if (episode != null) DropdownMenuItem(text = { Text(stringResource(R.string.share_label)) }, onClick = {
                        showShareDialog = true
                        expanded = false
                    })
                }
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

    private fun showOnDemandConfigBalloon(offerStreaming: Boolean) {
        val isLocaleRtl = (TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL)
        val balloon: Balloon = Balloon.Builder(requireContext())
            .setArrowOrientation(ArrowOrientation.TOP)
            .setArrowOrientationRules(ArrowOrientationRules.ALIGN_FIXED)
            .setArrowPosition(0.25f + (if (isLocaleRtl xor offerStreaming) 0f else 0.5f))
            .setWidthRatio(1.0f)
            .setMarginLeft(8)
            .setMarginRight(8)
            .setBackgroundColor(ThemeUtils.getColorFromAttr(requireContext(), com.google.android.material.R.attr.colorSecondary))
            .setBalloonAnimation(BalloonAnimation.OVERSHOOT)
            .setLayout(R.layout.popup_bubble_view)
            .setDismissWhenTouchOutside(true)
            .setLifecycleOwner(this)
            .build()
        val ballonView = balloon.getContentView()
        val positiveButton: Button = ballonView.findViewById(R.id.balloon_button_positive)
        val negativeButton: Button = ballonView.findViewById(R.id.balloon_button_negative)
        val message: TextView = ballonView.findViewById(R.id.balloon_message)
        message.setText(if (offerStreaming) R.string.on_demand_config_stream_text
        else R.string.on_demand_config_download_text)
        positiveButton.setOnClickListener {
            UserPreferences.isStreamOverDownload = offerStreaming
            // Update all visible lists to reflect new streaming action button
            //            TODO: need another event type?
            EventFlow.postEvent(FlowEvent.EpisodePlayedEvent())
            (activity as MainActivity).showSnackbarAbovePlayer(R.string.on_demand_config_setting_changed, Snackbar.LENGTH_SHORT)
            balloon.dismiss()
        }
        negativeButton.setOnClickListener {
            UsageStatistics.doNotAskAgain(UsageStatistics.ACTION_STREAM) // Type does not matter. Both are silenced.
            balloon.dismiss()
        }
//        balloon.showAlignBottom(butAction1, 0, (-12 * resources.displayMetrics.density).toInt())
    }

    override fun onResume() {
        super.onResume()
        if (itemLoaded) updateAppearance()
    }

     override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        episode = null
        super.onDestroyView()
    }

    private fun updateAppearance() {
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
            media.size > 0 -> txtvSize = formatShortFileSize(activity, media.size)
            isEpisodeHeadDownloadAllowed && !media.checkedOnSizeButUnknown() -> {
                txtvSize = "{faw_spinner}"
                lifecycleScope.launch {
                    val sizeValue = getMediaSize(episode)
                    txtvSize = if (sizeValue <= 0) "" else formatShortFileSize(activity, sizeValue)
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

     private fun openPodcast() {
        if (episode?.feedId == null) return
        val fragment: Fragment = FeedEpisodesFragment.newInstance(episode!!.feedId!!)
        (activity as MainActivity).loadChildFragment(fragment)
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
                    is FlowEvent.QueueEvent -> onQueueEvent(event)
                    is FlowEvent.RatingEvent -> onRatingEvent(event)
                    is FlowEvent.EpisodeEvent -> onEpisodeEvent(event)
//                    is FlowEvent.PlayerSettingsEvent -> updateButtons()
                    is FlowEvent.EpisodePlayedEvent -> load()
                    else -> {}
                }
            }
        }
        if (eventStickySink == null) eventStickySink = lifecycleScope.launch {
            EventFlow.stickyEvents.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.EpisodeDownloadEvent -> onEpisodeDownloadEvent(event)
                    else -> {}
                }
            }
        }
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
        if (itemLoaded && activity != null) updateButtons()
    }

    private var loadItemsRunning = false
    private fun load() {
        Logd(TAG, "load() called")
        if (!loadItemsRunning) {
            loadItemsRunning = true
            lifecycleScope.launch {
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

    fun setItem(item_: Episode) {
        episode = realm.query(Episode::class).query("id == ${item_.id}").first().find()
    }

    companion object {
        private val TAG: String = EpisodeInfoFragment::class.simpleName ?: "Anonymous"

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

        fun newInstance(item: Episode): EpisodeInfoFragment {
            val fragment = EpisodeInfoFragment()
            fragment.setItem(item)
            return fragment
        }
    }
}
