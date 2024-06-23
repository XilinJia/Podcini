package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.EpisodeInfoFragmentBinding
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.storage.utils.ImageResourceUtils
import ac.mdiq.podcini.net.utils.NetworkUtils.isEpisodeHeadDownloadAllowed
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.PlaybackController.Companion.seekTo
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.preferences.UsageStatistics
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.utils.MediaType
import ac.mdiq.podcini.ui.actions.actionbutton.*
import ac.mdiq.podcini.ui.actions.menuhandler.EpisodeMenuHandler
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.utils.ThemeUtils
import ac.mdiq.podcini.ui.view.CircularProgressBar
import ac.mdiq.podcini.ui.view.ShownotesWebView
import ac.mdiq.podcini.util.Converter
import ac.mdiq.podcini.util.DateFormatter
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.text.TextUtils
import android.text.format.Formatter
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.OptIn
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ShareCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import com.google.android.material.appbar.MaterialToolbar
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
import kotlin.math.max

/**
 * Displays information about an Episode (FeedItem) and actions.
 */
@UnstableApi class EpisodeInfoFragment : Fragment(), Toolbar.OnMenuItemClickListener {
    private var _binding: EpisodeInfoFragmentBinding? = null
    private val binding get() = _binding!!

    private var homeFragment: EpisodeHomeFragment? = null

    private var itemLoaded = false
    private var item: Episode? = null
    private var webviewData: String? = null

    private lateinit var shownotesCleaner: ShownotesCleaner
    private lateinit var toolbar: MaterialToolbar
    private lateinit var root: ViewGroup
    private lateinit var webvDescription: ShownotesWebView
    private lateinit var txtvPodcast: TextView
    private lateinit var txtvTitle: TextView
    private lateinit var txtvDuration: TextView
    private lateinit var txtvPublished: TextView
    private lateinit var imgvCover: ImageView
    private lateinit var progbarDownload: CircularProgressBar
    private lateinit var progbarLoading: ProgressBar

    private lateinit var homeButtonAction: View
    private lateinit var butAction1: ImageView
    private lateinit var butAction2: ImageView
    private lateinit var noMediaLabel: View

    private var actionButton1: EpisodeActionButton? = null
    private var actionButton2: EpisodeActionButton? = null

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)

        _binding = EpisodeInfoFragmentBinding.inflate(inflater, container, false)
        root = binding.root
        Logd(TAG, "fragment onCreateView")

        toolbar = binding.toolbar
        toolbar.title = ""
        toolbar.inflateMenu(R.menu.feeditem_options)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        toolbar.setOnMenuItemClickListener(this)

        txtvPodcast = binding.txtvPodcast
        txtvPodcast.setOnClickListener { openPodcast() }
        txtvTitle = binding.txtvTitle
        txtvTitle.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
        txtvDuration = binding.txtvDuration
        txtvPublished = binding.txtvPublished
        txtvTitle.ellipsize = TextUtils.TruncateAt.END
        webvDescription = binding.webvDescription
        webvDescription.setTimecodeSelectedListener { time: Int? ->
            val cMedia = curMedia
            if (item?.media?.getIdentifier() == cMedia?.getIdentifier()) seekTo(time ?: 0)
            else (activity as MainActivity).showSnackbarAbovePlayer(R.string.play_this_to_seek_position, Snackbar.LENGTH_LONG)
        }
        registerForContextMenu(webvDescription)

        imgvCover = binding.imgvCover
        imgvCover.setOnClickListener { openPodcast() }
        progbarDownload = binding.circularProgressBar
        progbarLoading = binding.progbarLoading
        homeButtonAction = binding.homeButton
        butAction1 = binding.butAction1
        butAction2 = binding.butAction2
        noMediaLabel = binding.noMediaLabel

        homeButtonAction.setOnClickListener {
            if (!item?.link.isNullOrEmpty()) {
                homeFragment = EpisodeHomeFragment.newInstance(item!!)
                (activity as MainActivity).loadChildFragment(homeFragment!!)
            } else Toast.makeText(context, "Episode link is not valid ${item?.link}", Toast.LENGTH_LONG).show()
        }

        butAction1.setOnClickListener(View.OnClickListener {
            when {
                actionButton1 is StreamActionButton && !UserPreferences.isStreamOverDownload
                        && UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_STREAM) -> {
                    showOnDemandConfigBalloon(true)
                    return@OnClickListener
                }
                actionButton1 == null -> return@OnClickListener  // Not loaded yet
                else -> actionButton1?.onClick(requireContext())
            }
        })
        butAction2.setOnClickListener(View.OnClickListener {
            when {
                actionButton2 is DownloadActionButton && UserPreferences.isStreamOverDownload
                        && UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_DOWNLOAD) -> {
                    showOnDemandConfigBalloon(false)
                    return@OnClickListener
                }
                actionButton2 == null -> return@OnClickListener  // Not loaded yet
                else -> actionButton2?.onClick(requireContext())
            }
        })
        shownotesCleaner = ShownotesCleaner(requireContext())
        load()
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
    }

    @OptIn(UnstableApi::class) private fun showOnDemandConfigBalloon(offerStreaming: Boolean) {
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
        val positiveButton = ballonView.findViewById(R.id.balloon_button_positive) as Button
        val negativeButton = ballonView.findViewById(R.id.balloon_button_negative) as Button
        val message: TextView = ballonView.findViewById(R.id.balloon_message) as TextView
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
        balloon.showAlignBottom(butAction1, 0, (-12 * resources.displayMetrics.density).toInt())
    }

    @UnstableApi override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.share_notes -> {
                if (item == null) return false
                val notes = item!!.description
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
                if (item == null) return false
                return EpisodeMenuHandler.onMenuItemClicked(this, menuItem.itemId, item!!)
            }
        }
    }

    @UnstableApi override fun onResume() {
        super.onResume()
        if (itemLoaded) {
            progbarLoading.visibility = View.GONE
            updateAppearance()
        }
    }

    @OptIn(UnstableApi::class) override fun onDestroyView() {
        super.onDestroyView()
        Logd(TAG, "onDestroyView")
        _binding = null
        
        root.removeView(webvDescription)
        webvDescription.clearHistory()
        webvDescription.clearCache(true)
        webvDescription.clearView()
        webvDescription.destroy()
    }

    @UnstableApi private fun onFragmentLoaded() {
        if (webviewData != null && !itemLoaded)
            webvDescription.loadDataWithBaseURL("https://127.0.0.1", webviewData!!, "text/html", "utf-8", "about:blank")

//        if (item?.link != null) binding.webView.loadUrl(item!!.link!!)
        updateAppearance()
    }

    @UnstableApi private fun updateAppearance() {
        if (item == null) {
            Logd(TAG, "updateAppearance item is null")
            return
        }
        if (item!!.media != null) EpisodeMenuHandler.onPrepareMenu(toolbar.menu, item, R.id.open_podcast)
        // these are already available via button1 and button2
        else EpisodeMenuHandler.onPrepareMenu(toolbar.menu, item, R.id.open_podcast, R.id.mark_read_item, R.id.visit_website_item)

        if (item!!.feed != null) txtvPodcast.text = item!!.feed!!.title
        txtvTitle.text = item!!.title
        binding.itemLink.text = item!!.link

        if (item?.pubDate != null) {
            val pubDateStr = DateFormatter.formatAbbrev(context, Date(item!!.pubDate))
            txtvPublished.text = pubDateStr
            txtvPublished.setContentDescription(DateFormatter.formatForAccessibility(Date(item!!.pubDate)))
        }

        val media = item?.media
        when {
            media == null -> binding.txtvSize.text = ""
            media.size > 0 -> binding.txtvSize.text = Formatter.formatShortFileSize(activity, media.size)
            isEpisodeHeadDownloadAllowed && !media.checkedOnSizeButUnknown() -> {
                binding.txtvSize.text = "{faw_spinner}"
//                Iconify.addIcons(size)
                lifecycleScope.launch {
                    val sizeValue = getMediaSize(item)
                    if (sizeValue <= 0) binding.txtvSize.text = ""
                    else binding.txtvSize.text = Formatter.formatShortFileSize(activity, sizeValue)
                }
            }
            else -> binding.txtvSize.text = ""
        }

        val imgLocFB = ImageResourceUtils.getFallbackImageLocation(item!!)
        val imageLoader = imgvCover.context.imageLoader
        val imageRequest = ImageRequest.Builder(requireContext())
            .data(item!!.imageLocation)
            .placeholder(R.color.light_gray)
            .listener(object : ImageRequest.Listener {
                override fun onError(request: ImageRequest, result: ErrorResult) {
                    val fallbackImageRequest = ImageRequest.Builder(requireContext())
                        .data(imgLocFB)
                        .setHeader("User-Agent", "Mozilla/5.0")
                        .error(R.mipmap.ic_launcher)
                        .target(imgvCover)
                        .build()
                    imageLoader.enqueue(fallbackImageRequest)
                }
            })
            .target(imgvCover)
            .build()
        imageLoader.enqueue(imageRequest)

        updateButtons()
    }

    @UnstableApi private fun updateButtons() {
        progbarDownload.visibility = View.GONE
        val dls = DownloadServiceInterface.get()
        if (item != null && item!!.media != null && item!!.media!!.downloadUrl != null) {
            val url = item!!.media!!.downloadUrl!!
            if (dls != null && dls.isDownloadingEpisode(url)) {
                progbarDownload.visibility = View.VISIBLE
                progbarDownload.setPercentage(0.01f * max(1.0, dls.getProgress(url).toDouble()).toFloat(), item)
                progbarDownload.setIndeterminate(dls.isEpisodeQueued(url))
            }
        }

        val media: EpisodeMedia? = item?.media
        if (media == null) {
            if (item != null) {
//                actionButton1 = VisitWebsiteActionButton(item!!)
                butAction1.visibility = View.INVISIBLE
                actionButton2 = VisitWebsiteActionButton(item!!)
            }
            noMediaLabel.visibility = View.VISIBLE
        } else {
            noMediaLabel.visibility = View.GONE
            if (media.getDuration() > 0) {
                txtvDuration.text = Converter.getDurationStringLong(media.getDuration())
                txtvDuration.setContentDescription(Converter.getDurationStringLocalized(requireContext(), media.getDuration().toLong()))
            }
            if (item != null) {
                actionButton1 = when {
                    media.getMediaType() == MediaType.FLASH -> VisitWebsiteActionButton(item!!)
                    InTheatre.isCurrentlyPlaying(media) -> PauseActionButton(item!!)
                    item!!.feed != null && item!!.feed!!.isLocalFeed -> PlayLocalActionButton(item!!)
                    media.downloaded -> PlayActionButton(item!!)
                    else -> StreamActionButton(item!!)
                }
                actionButton2 = when {
                    media.getMediaType() == MediaType.FLASH -> VisitWebsiteActionButton(item!!)
                    dls != null && media.downloadUrl != null && dls.isDownloadingEpisode(media.downloadUrl!!) -> CancelDownloadActionButton(item!!)
                    !media.downloaded -> DownloadActionButton(item!!)
                    else -> DeleteActionButton(item!!)
                }
//                if (actionButton2 != null && media.getMediaType() == MediaType.FLASH) actionButton2!!.visibility = View.GONE
            }
        }

        if (actionButton1 != null) {
            butAction1.setImageResource(actionButton1!!.getDrawable())
            butAction1.visibility = actionButton1!!.visibility
        }
        if (actionButton2 != null) {
            butAction2.setImageResource(actionButton2!!.getDrawable())
            butAction2.visibility = actionButton2!!.visibility
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return webvDescription.onContextItemSelected(item)
    }

    @OptIn(UnstableApi::class) private fun openPodcast() {
        if (item?.feedId == null) return

        val fragment: Fragment = FeedEpisodesFragment.newInstance(item!!.feedId!!)
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

    private fun onEpisodeEvent(event: FlowEvent.EpisodeEvent) {
//        Logd(TAG, "onEventMainThread() called with ${event.TAG}")
        if (this.item == null) return
        for (item in event.episodes) {
            if (this.item!!.id == item.id) {
                load()
                return
            }
        }
    }

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
        if (item == null || item!!.media == null) return
        if (!event.urls.contains(item!!.media!!.downloadUrl)) return
        if (itemLoaded && activity != null) updateButtons()
    }

    @UnstableApi private fun load() {
        if (!itemLoaded) progbarLoading.visibility = View.VISIBLE

        Logd(TAG, "load() called")
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val feedItem = item
                    if (feedItem != null) {
                        val duration = feedItem.media?.getDuration()?: Int.MAX_VALUE
                        webviewData = shownotesCleaner.processShownotes(feedItem.description?:"", duration)
                    }
                    feedItem
                }
                withContext(Dispatchers.Main) {
                    progbarLoading.visibility = View.GONE
                    item = result
                    onFragmentLoaded()
                    itemLoaded = true
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
    }

    fun setItem(item_: Episode) {
        item = item_
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
                if (size <= 0) media.setCheckedOnSizeButUnknown()
                else media.size = size
                upsert(episode) {}

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
