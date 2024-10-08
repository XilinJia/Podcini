package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.EpisodeHomeFragmentBinding
import ac.mdiq.podcini.databinding.EpisodeInfoFragmentBinding
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.net.utils.NetworkUtils.fetchHtmlSource
import ac.mdiq.podcini.net.utils.NetworkUtils.isEpisodeHeadDownloadAllowed
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.seekTo
import ac.mdiq.podcini.preferences.UsageStatistics
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.unmanaged
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.storage.utils.ImageResourceUtils
import ac.mdiq.podcini.ui.actions.*
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.utils.ThemeUtils
import ac.mdiq.podcini.ui.view.ShownotesWebView
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter.formatAbbrev
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.TextUtils
import android.text.format.Formatter.formatShortFileSize
import android.util.Log
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ShareCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.skydoves.balloon.ArrowOrientation
import com.skydoves.balloon.ArrowOrientationRules
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import net.dankito.readability4j.extended.Readability4JExtended
import okhttp3.Request.Builder
import java.io.File
import java.util.*

/**
 * Displays information about an Episode (FeedItem) and actions.
 */
@UnstableApi
class EpisodeInfoFragment : Fragment(), Toolbar.OnMenuItemClickListener {
    private var _binding: EpisodeInfoFragmentBinding? = null
    private val binding get() = _binding!!

    private var homeFragment: EpisodeHomeFragment? = null

    private var itemLoaded = false
    private var episode: Episode? = null    // managed

    private var webviewData by mutableStateOf("")

    private lateinit var shownotesCleaner: ShownotesCleaner
    private lateinit var toolbar: MaterialToolbar
//    private lateinit var webvDescription: ShownotesWebView
//    private lateinit var imgvCover: ImageView

//    private lateinit var butAction1: ImageView
//    private lateinit var butAction2: ImageView

    private var actionButton1 by mutableStateOf<EpisodeActionButton?>(null)
    private var actionButton2 by mutableStateOf<EpisodeActionButton?>(null)

    @UnstableApi
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)

        _binding = EpisodeInfoFragmentBinding.inflate(inflater, container, false)
        Logd(TAG, "fragment onCreateView")

        toolbar = binding.toolbar
        toolbar.title = ""
        toolbar.inflateMenu(R.menu.episode_info)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        toolbar.setOnMenuItemClickListener(this)

        binding.composeView.setContent{
            CustomTheme(requireContext()) {
                MainView()
            }
        }

//        webvDescription = binding.webvDescription
//        webvDescription.setTimecodeSelectedListener { time: Int? ->
//            val cMedia = curMedia
//            if (episode?.media?.getIdentifier() == cMedia?.getIdentifier()) seekTo(time ?: 0)
//            else (activity as MainActivity).showSnackbarAbovePlayer(R.string.play_this_to_seek_position, Snackbar.LENGTH_LONG)
//        }
//        registerForContextMenu(webvDescription)

        shownotesCleaner = ShownotesCleaner(requireContext())
        onFragmentLoaded()
        load()
        return binding.root
    }

    @Composable
    fun MainView() {
        val textColor = MaterialTheme.colorScheme.onSurface
        var showEditComment by remember { mutableStateOf(false) }
        @Composable
        fun LargeTextEditingDialog(textState: TextFieldValue, onTextChange: (TextFieldValue) -> Unit, onDismissRequest: () -> Unit, onSave: (String) -> Unit) {
            Dialog(onDismissRequest = { onDismissRequest() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.medium) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Add comment", color = textColor, style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        BasicTextField(value = textState, onValueChange = { onTextChange(it) }, textStyle = TextStyle(fontSize = 16.sp, color = textColor),
                            modifier = Modifier.fillMaxWidth().height(300.dp).padding(10.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { onDismissRequest() }) {
                                Text("Cancel")
                            }
                            TextButton(onClick = {
                                onSave(textState.text)
                                onDismissRequest()
                            }) {
                                Text("Save")
                            }
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(10000)
                        onSave(textState.text)
                    }
                }
            }
        }
        var commentTextState by remember { mutableStateOf(TextFieldValue(episode?.comment?:"")) }
        if (showEditComment) LargeTextEditingDialog(textState = commentTextState, onTextChange = { commentTextState = it }, onDismissRequest = {showEditComment = false},
            onSave = {
                runOnIOScope { if (episode != null) episode = upsert(episode!!) { it.comment = commentTextState.text } }
            })

        Column {
            Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                val imgLoc = if (episode != null) ImageResourceUtils.getEpisodeListImageLocation(episode!!) else null
                AsyncImage(model = imgLoc, contentDescription = "imgvCover", Modifier.width(56.dp).height(56.dp).clickable(onClick = { openPodcast() }))
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(txtvPodcast, color = textColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.clickable { openPodcast() })
                    Text(txtvTitle, color = textColor, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), maxLines = 5, overflow = TextOverflow.Ellipsis)
                    Text("$txtvPublished · $txtvDuration · $txtvSize", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(1f))
                if (hasMedia) Icon(painter = painterResource(actionButton1?.getDrawable()?: R.drawable.ic_questionmark), tint = textColor, contentDescription = "butAction1",
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
                Icon(painter = painterResource(R.drawable.baseline_home_work_24), tint = textColor, contentDescription = "homeButton",
                    modifier = Modifier.width(24.dp).height(24.dp).clickable(onClick = {
                        if (!episode?.link.isNullOrEmpty()) {
                            homeFragment = EpisodeHomeFragment.newInstance(episode!!)
                            (activity as MainActivity).loadChildFragment(homeFragment!!)
                        } else Toast.makeText(context, "Episode link is not valid ${episode?.link}", Toast.LENGTH_LONG).show()
                    }))
                Spacer(modifier = Modifier.weight(0.2f))
                Box(modifier = Modifier.width(40.dp).height(40.dp).align(Alignment.CenterVertically), contentAlignment = Alignment.Center) {
                    Icon(painter = painterResource(actionButton2?.getDrawable()?: R.drawable.ic_questionmark), tint = textColor, contentDescription = "butAction2", modifier = Modifier.width(24.dp).height(24.dp).clickable {
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
//                        if (cond) CircularProgressIndicator(progress = 0.01f * dlPercent, strokeWidth = 4.dp, color = textColor)
                }
                Spacer(modifier = Modifier.weight(1f))
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
                Text(stringResource(R.string.my_opinion_label) + if (commentTextState.text.isEmpty()) " (Add)" else "",
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable { showEditComment = true })
                Text(commentTextState.text, color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 15.dp, bottom = 10.dp))
                Text(itemLink, color = textColor, style = MaterialTheme.typography.bodySmall)
            }
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

    @OptIn(UnstableApi::class)
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

    @UnstableApi
    override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.share_notes -> {
                if (episode == null) return false
                val notes = episode!!.description
                if (!notes.isNullOrEmpty()) {
                    val shareText = if (Build.VERSION.SDK_INT >= 24) HtmlCompat.fromHtml(notes, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                    else HtmlCompat.fromHtml(notes, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                    val context = requireContext()
                    val intent = ShareCompat.IntentBuilder(context)
                        .setType("text/plain")
                        .setText(shareText)
                        .setChooserTitle(R.string.share_notes_label)
                        .createChooserIntent()
                    context.startActivity(intent)
                }
                return true
            }
            else -> {
                if (episode == null) return false
                return EpisodeMenuHandler.onMenuItemClicked(this, menuItem.itemId, episode!!)
            }
        }
    }

    @UnstableApi
    override fun onResume() {
        super.onResume()
        if (itemLoaded) {
//            binding.progbarLoading.visibility = View.GONE
            updateAppearance()
        }
    }

    @OptIn(UnstableApi::class) override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        episode = null
//        webvDescription.clearHistory()
//        webvDescription.clearCache(true)
//        webvDescription.clearView()
//        webvDescription.destroy()
        _binding = null
        super.onDestroyView()
    }

    @UnstableApi
    private fun onFragmentLoaded() {
//        if (!itemLoaded)
//            webvDescription.loadDataWithBaseURL("https://127.0.0.1", webviewData, "text/html", "utf-8", "about:blank")
//        if (item?.link != null) binding.webView.loadUrl(item!!.link!!)
        updateAppearance()
    }

    private fun prepareMenu() {
        if (episode!!.media != null) EpisodeMenuHandler.onPrepareMenu(toolbar.menu, episode, R.id.open_podcast)
        // these are already available via button1 and button2
        else EpisodeMenuHandler.onPrepareMenu(toolbar.menu, episode, R.id.open_podcast, R.id.mark_read_item, R.id.visit_website_item)
    }

    private var txtvPodcast by mutableStateOf("")
    private var txtvTitle by mutableStateOf("")
    private var txtvPublished by mutableStateOf("")
    private var txtvSize by mutableStateOf("")
    private var txtvDuration by mutableStateOf("")
    private var itemLink by mutableStateOf("")
    @UnstableApi
    private fun updateAppearance() {
        if (episode == null) {
            Logd(TAG, "updateAppearance item is null")
            return
        }
        prepareMenu()

        if (episode!!.feed != null) txtvPodcast = episode!!.feed!!.title ?: ""
        txtvTitle = episode!!.title ?:""
        itemLink = episode!!.link?: ""

        if (episode?.pubDate != null) {
            val pubDateStr = formatAbbrev(context, Date(episode!!.pubDate))
            txtvPublished = pubDateStr
//            binding.txtvPublished.setContentDescription(formatForAccessibility(Date(episode!!.pubDate)))
        }

        val media = episode?.media
        when {
            media == null -> txtvSize = ""
            media.size > 0 -> txtvSize = formatShortFileSize(activity, media.size)
            isEpisodeHeadDownloadAllowed && !media.checkedOnSizeButUnknown() -> {
                txtvSize = "{faw_spinner}"
//                Iconify.addIcons(size)
                lifecycleScope.launch {
                    val sizeValue = getMediaSize(episode)
                    txtvSize = if (sizeValue <= 0) "" else formatShortFileSize(activity, sizeValue)
                }
            }
            else -> txtvSize = ""
        }

//        val imgLocFB = ImageResourceUtils.getFallbackImageLocation(episode!!)
//        val imageLoader = imgvCover.context.imageLoader
//        val imageRequest = ImageRequest.Builder(requireContext())
//            .data(episode!!.imageLocation)
//            .placeholder(R.color.light_gray)
//            .listener(object : ImageRequest.Listener {
//                override fun onError(request: ImageRequest, result: ErrorResult) {
//                    val fallbackImageRequest = ImageRequest.Builder(requireContext())
//                        .data(imgLocFB)
//                        .setHeader("User-Agent", "Mozilla/5.0")
//                        .error(R.mipmap.ic_launcher)
//                        .target(imgvCover)
//                        .build()
//                    imageLoader.enqueue(fallbackImageRequest)
//                }
//            })
//            .target(imgvCover)
//            .build()
//        imageLoader.enqueue(imageRequest)

        updateButtons()
    }

    var hasMedia by mutableStateOf(true)
    @UnstableApi
    private fun updateButtons() {
//        binding.circularProgressBar.visibility = View.GONE
        val dls = DownloadServiceInterface.get()
        if (episode != null && episode!!.media != null && episode!!.media!!.downloadUrl != null) {
            val url = episode!!.media!!.downloadUrl!!
//            if (dls != null && dls.isDownloadingEpisode(url)) {
//                binding.circularProgressBar.visibility = View.VISIBLE
//                binding.circularProgressBar.setPercentage(0.01f * max(1.0, dls.getProgress(url).toDouble()).toFloat(), episode)
//                binding.circularProgressBar.setIndeterminate(dls.isEpisodeQueued(url))
//            }
        }

        val media: EpisodeMedia? = episode?.media
        if (media == null) {
            if (episode != null) {
//                actionButton1 = VisitWebsiteActionButton(episode!!)
//                butAction1.visibility = View.INVISIBLE
                actionButton2 = VisitWebsiteActionButton(episode!!)
            }
            hasMedia = false
        } else {
            hasMedia = true
            if (media.getDuration() > 0) {
                txtvDuration = DurationConverter.getDurationStringLong(media.getDuration())
//                binding.txtvDuration.setContentDescription(DurationConverter.getDurationStringLocalized(requireContext(), media.getDuration().toLong()))
            }
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

//    override fun onContextItemSelected(item: MenuItem): Boolean {
//        return webvDescription.onContextItemSelected(item)
//    }

    @OptIn(UnstableApi::class) private fun openPodcast() {
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
                    is FlowEvent.RatingEvent -> onFavoriteEvent(event)
                    is FlowEvent.EpisodeEvent -> onEpisodeEvent(event)
                    is FlowEvent.PlayerSettingsEvent -> updateButtons()
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

    private fun onFavoriteEvent(event: FlowEvent.RatingEvent) {
        if (episode?.id == event.episode.id) {
            episode = unmanaged(episode!!)
            episode!!.rating = if (event.episode.isFavorite) Episode.Rating.FAVORITE.code else Episode.Rating.NEUTRAL.code
//            episode = event.episode
            prepareMenu()
        }
    }

    private fun onQueueEvent(event: FlowEvent.QueueEvent) {
        var i = 0
        val size: Int = event.episodes.size
        while (i < size) {
            val item_ = event.episodes[i]
            if (item_.id == episode?.id) {
                prepareMenu()
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
        if (episode == null || episode!!.media == null) return
        if (!event.urls.contains(episode!!.media!!.downloadUrl)) return
        if (itemLoaded && activity != null) updateButtons()
    }

    private var loadItemsRunning = false
    @UnstableApi
    private fun load() {
//        if (!itemLoaded) binding.progbarLoading.visibility = View.VISIBLE
        Logd(TAG, "load() called")
        if (!loadItemsRunning) {
            loadItemsRunning = true
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        if (episode != null) {
                            val duration = episode!!.media?.getDuration() ?: Int.MAX_VALUE
                            Logd(TAG, "description: ${episode?.description}")
                            val url = episode!!.media?.downloadUrl
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
//                        binding.progbarLoading.visibility = View.GONE
                        onFragmentLoaded()
                        itemLoaded = true
                    }
                } catch (e: Throwable) { Log.e(TAG, Log.getStackTraceString(e))
                } finally { loadItemsRunning = false }
            }
        }
    }

    fun setItem(item_: Episode) {
        episode = item_
    }

    /**
     * Displays information about an Episode (FeedItem) and actions.
     */
    class EpisodeHomeFragment : Fragment() {
        private var _binding: EpisodeHomeFragmentBinding? = null
        private val binding get() = _binding!!

        private var startIndex = 0
        private var ttsSpeed = 1.0f

        private lateinit var toolbar: MaterialToolbar

        private var readerText: String? = null
        private var cleanedNotes: String? = null
        private var readerhtml: String? = null
        private var readMode = true
        private var ttsPlaying = false
        private var jsEnabled = false

        private var tts: TextToSpeech? = null
        private var ttsReady = false

        @UnstableApi
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            super.onCreateView(inflater, container, savedInstanceState)
            _binding = EpisodeHomeFragmentBinding.inflate(inflater, container, false)
            Logd(TAG, "fragment onCreateView")
            toolbar = binding.toolbar
            toolbar.title = ""
            toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
            toolbar.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

            if (!episode?.link.isNullOrEmpty()) showContent()
            else {
                Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack()
            }
            binding.webView.apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val isEmpty = view?.title.isNullOrEmpty() && view?.contentDescription.isNullOrEmpty()
                        if (isEmpty) Logd(TAG, "content is empty")
                    }
                }
            }
            updateAppearance()
            return binding.root
        }

        @OptIn(UnstableApi::class) private fun switchMode() {
            readMode = !readMode
            showContent()
            updateAppearance()
        }

        @OptIn(UnstableApi::class) private fun showReaderContent() {
            runOnIOScope {
                if (!episode?.link.isNullOrEmpty()) {
                    if (cleanedNotes == null) {
                        if (episode?.transcript == null) {
                            val url = episode!!.link!!
                            val htmlSource = fetchHtmlSource(url)
                            val article = Readability4JExtended(episode?.link!!, htmlSource).parse()
                            readerText = article.textContent
//                    Log.d(TAG, "readability4J: ${article.textContent}")
                            readerhtml = article.contentWithDocumentsCharsetOrUtf8
                        } else {
                            readerhtml = episode!!.transcript
                            readerText = HtmlCompat.fromHtml(readerhtml!!, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                        }
                        if (!readerhtml.isNullOrEmpty()) {
                            val shownotesCleaner = ShownotesCleaner(requireContext())
                            cleanedNotes = shownotesCleaner.processShownotes(readerhtml!!, 0)
                            episode = upsertBlk(episode!!) {
                                it.setTranscriptIfLonger(readerhtml)
                            }
//                        persistEpisode(episode)
                        }
                    }
                }
                if (!cleanedNotes.isNullOrEmpty()) {
                    if (!ttsReady) initializeTTS(requireContext())
                    withContext(Dispatchers.Main) {
                        binding.readerView.loadDataWithBaseURL("https://127.0.0.1", cleanedNotes ?: "No notes",
                            "text/html", "UTF-8", null)
                        binding.readerView.visibility = View.VISIBLE
                        binding.webView.visibility = View.GONE
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        private fun initializeTTS(context: Context) {
            Logd(TAG, "starting TTS")
            if (tts == null) {
                tts = TextToSpeech(context) { status: Int ->
                    if (status == TextToSpeech.SUCCESS) {
                        if (episode?.feed?.language != null) {
                            val result = tts?.setLanguage(Locale(episode!!.feed!!.language!!))
                            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                Log.w(TAG, "TTS language not supported ${episode?.feed?.language}")
                                requireActivity().runOnUiThread {
                                    Toast.makeText(context, getString(R.string.language_not_supported_by_tts) + " ${episode?.feed?.language}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        ttsReady = true
//                    semaphore.release()
                        Logd(TAG, "TTS init success")
                    } else {
                        Log.w(TAG, "TTS init failed")
                        requireActivity().runOnUiThread { Toast.makeText(context, R.string.tts_init_failed, Toast.LENGTH_LONG).show() }
                    }
                }
            }
        }

        private fun showWebContent() {
            if (!episode?.link.isNullOrEmpty()) {
                binding.webView.settings.javaScriptEnabled = jsEnabled
                Logd(TAG, "currentItem!!.link ${episode!!.link}")
                binding.webView.loadUrl(episode!!.link!!)
                binding.readerView.visibility = View.GONE
                binding.webView.visibility = View.VISIBLE
            } else Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show()
        }

        private fun showContent() {
            if (readMode) showReaderContent()
            else showWebContent()
        }

        private val menuProvider = object: MenuProvider {
            override fun onPrepareMenu(menu: Menu) {
//            super.onPrepareMenu(menu)
                Logd(TAG, "onPrepareMenu called")
                val textSpeech = menu.findItem(R.id.text_speech)
                textSpeech?.isVisible = readMode && tts != null
                if (textSpeech?.isVisible == true) {
                    if (ttsPlaying) textSpeech.setIcon(R.drawable.ic_pause) else textSpeech.setIcon(R.drawable.ic_play_24dp)
                }
                menu.findItem(R.id.share_notes)?.setVisible(readMode)
                menu.findItem(R.id.switchJS)?.setVisible(!readMode)
                val btn = menu.findItem(R.id.switch_home)
                if (readMode) btn?.setIcon(R.drawable.baseline_home_24)
                else btn?.setIcon(R.drawable.outline_home_24)
            }

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.episode_home, menu)
                onPrepareMenu(menu)
            }

            @OptIn(UnstableApi::class) override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when (menuItem.itemId) {
                    R.id.switch_home -> {
                        switchMode()
                        return true
                    }
                    R.id.switchJS -> {
                        jsEnabled = !jsEnabled
                        showWebContent()
                        return true
                    }
                    R.id.text_speech -> {
                        Logd(TAG, "text_speech selected: $readerText")
                        if (tts != null) {
                            if (tts!!.isSpeaking) tts?.stop()
                            if (!ttsPlaying) {
                                ttsPlaying = true
                                if (!readerText.isNullOrEmpty()) {
                                    ttsSpeed = episode?.feed?.preferences?.playSpeed ?: 1.0f
                                    tts?.setSpeechRate(ttsSpeed)
                                    while (startIndex < readerText!!.length) {
                                        val endIndex = minOf(startIndex + MAX_CHUNK_LENGTH, readerText!!.length)
                                        val chunk = readerText!!.substring(startIndex, endIndex)
                                        tts?.speak(chunk, TextToSpeech.QUEUE_ADD, null, null)
                                        startIndex += MAX_CHUNK_LENGTH
                                    }
                                }
                            } else ttsPlaying = false
                            updateAppearance()
                        } else Toast.makeText(context, R.string.tts_not_available, Toast.LENGTH_LONG).show()

                        return true
                    }
                    R.id.share_notes -> {
                        val notes = readerhtml
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
                        return true
                    }
                    else -> {
                        return episode != null
                    }
                }
            }
        }

        @UnstableApi
        override fun onResume() {
            super.onResume()
            updateAppearance()
        }

        private fun cleatWebview(webview: WebView) {
            binding.root.removeView(webview)
            webview.clearHistory()
            webview.clearCache(true)
            webview.clearView()
            webview.destroy()
        }

        @OptIn(UnstableApi::class) override fun onDestroyView() {
            Logd(TAG, "onDestroyView")
            cleatWebview(binding.webView)
            cleatWebview(binding.readerView)
            _binding = null
            tts?.stop()
            tts?.shutdown()
            tts = null
            super.onDestroyView()
        }

        @UnstableApi
        private fun updateAppearance() {
            if (episode == null) {
                Logd(TAG, "updateAppearance currentItem is null")
                return
            }
//        onPrepareOptionsMenu(toolbar.menu)
            toolbar.invalidateMenu()
//        menuProvider.onPrepareMenu(toolbar.menu)
        }

        companion object {
            private val TAG: String = EpisodeHomeFragment::class.simpleName ?: "Anonymous"
            private const val MAX_CHUNK_LENGTH = 2000

            var episode: Episode? = null    // unmanged

            fun newInstance(item: Episode): EpisodeHomeFragment {
                val fragment = EpisodeHomeFragment()
                Logd(TAG, "item.identifyingValue ${item.identifyingValue}")
                if (item.identifyingValue != episode?.identifyingValue) episode = item
                return fragment
            }
        }
    }

    companion object {
        private val TAG: String = EpisodeInfoFragment::class.simpleName ?: "Anonymous"

        private suspend fun getMediaSize(episode: Episode?) : Long {
            return withContext(Dispatchers.IO) {
                if (!isEpisodeHeadDownloadAllowed) return@withContext -1
                val media = episode?.media ?: return@withContext -1

                var size = Int.MIN_VALUE.toLong()
                when {
                    media.downloaded -> {
                        val url = media.getLocalMediaUrl()
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
                    if (size <= 0) it.media?.setCheckedOnSizeButUnknown()
                    else it.media?.size = size
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
