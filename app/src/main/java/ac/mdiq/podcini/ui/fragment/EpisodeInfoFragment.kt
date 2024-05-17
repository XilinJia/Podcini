package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.EpisodeInfoFragmentBinding
import ac.mdiq.podcini.feed.util.ImageResourceUtils
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.playback.PlaybackController
import ac.mdiq.podcini.preferences.UsageStatistics
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.storage.model.playback.MediaType
import ac.mdiq.podcini.ui.actions.actionbutton.*
import ac.mdiq.podcini.ui.actions.menuhandler.FeedItemMenuHandler
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.utils.ThemeUtils
import ac.mdiq.podcini.ui.view.CircularProgressBar
import ac.mdiq.podcini.ui.view.ShownotesWebView
import ac.mdiq.podcini.util.Converter
import ac.mdiq.podcini.util.DateFormatter
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.PlaybackStatus
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.text.TextUtils
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.max

/**
 * Displays information about an Episode (FeedItem) and actions.
 */
@UnstableApi class EpisodeInfoFragment : Fragment(), Toolbar.OnMenuItemClickListener {
    private var _binding: EpisodeInfoFragmentBinding? = null
    private val binding get() = _binding!!

    private var homeFragment: EpisodeHomeFragment? = null

    private var itemsLoaded = false
    private var item: FeedItem? = null
    private var webviewData: String? = null

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

    private var actionButton1: ItemActionButton? = null
    private var actionButton2: ItemActionButton? = null

//    private var disposable: Disposable? = null
    private var controller: PlaybackController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        item = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requireArguments().getSerializable(ARG_FEEDITEM, FeedItem::class.java)
//        else requireArguments().getSerializable(ARG_FEEDITEM) as? FeedItem
    }

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
            val cMedia = controller?.getMedia()
            if (item?.media?.getIdentifier() == cMedia?.getIdentifier()) controller!!.seekTo(time ?: 0)
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
            } else {
                Toast.makeText(context, "Episode link is not valid ${item?.link}", Toast.LENGTH_LONG).show()
            }
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

        controller = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {
                // Do nothing
            }
        }
        controller?.init()
        load()

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
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
            .setBackgroundColor(ThemeUtils.getColorFromAttr(requireContext(), R.attr.colorSecondary))
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
            EventFlow.postEvent(FlowEvent.UnreadItemsUpdateEvent())
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
                return FeedItemMenuHandler.onMenuItemClicked(this, menuItem.itemId, item!!)
            }
        }
    }

    @UnstableApi override fun onResume() {
        super.onResume()
        if (itemsLoaded) {
            progbarLoading.visibility = View.GONE
            updateAppearance()
        }
    }

    @OptIn(UnstableApi::class) override fun onDestroyView() {
        super.onDestroyView()
        Logd(TAG, "onDestroyView")
        _binding = null
        
        controller?.release()
//        disposable?.dispose()
        root.removeView(webvDescription)
        webvDescription.clearHistory()
        webvDescription.clearCache(true)
        webvDescription.clearView()
        webvDescription.destroy()
    }

    @UnstableApi private fun onFragmentLoaded() {
        if (webviewData != null && !itemsLoaded) {
            webvDescription.loadDataWithBaseURL("https://127.0.0.1", webviewData!!, "text/html", "utf-8", "about:blank")
        }
//        if (item?.link != null) binding.webView.loadUrl(item!!.link!!)
        updateAppearance()
    }

    @UnstableApi private fun updateAppearance() {
        if (item == null) {
            Logd(TAG, "updateAppearance item is null")
            return
        }
        if (item!!.hasMedia()) {
            FeedItemMenuHandler.onPrepareMenu(toolbar.menu, item, R.id.open_podcast)
        } else {
            // these are already available via button1 and button2
            FeedItemMenuHandler.onPrepareMenu(toolbar.menu, item, R.id.open_podcast, R.id.mark_read_item, R.id.visit_website_item)
        }
        if (item!!.feed != null) txtvPodcast.text = item!!.feed!!.title
        txtvTitle.text = item!!.title

        if (item?.pubDate != null) {
            val pubDateStr = DateFormatter.formatAbbrev(context, item!!.pubDate)
            txtvPublished.text = pubDateStr
            txtvPublished.setContentDescription(DateFormatter.formatForAccessibility(item!!.pubDate))
        }

        val imgLocFB = ImageResourceUtils.getFallbackImageLocation(item!!)
        val imageLoader = imgvCover.context.imageLoader
        val imageRequest = ImageRequest.Builder(requireContext())
            .data(item!!.imageLocation)
            .placeholder(R.color.light_gray)
            .listener(object : ImageRequest.Listener {
                override fun onError(request: ImageRequest, throwable: ErrorResult) {
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
        if (item != null && item!!.hasMedia() && item!!.media!!.download_url != null) {
            val url = item!!.media!!.download_url!!
            if (dls != null && dls.isDownloadingEpisode(url)) {
                progbarDownload.visibility = View.VISIBLE
                progbarDownload.setPercentage(0.01f * max(1.0, dls.getProgress(url).toDouble()).toFloat(), item)
                progbarDownload.setIndeterminate(dls.isEpisodeQueued(url))
            }
        }

        val media: FeedMedia? = item?.media
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
                    PlaybackStatus.isCurrentlyPlaying(media) -> PauseActionButton(item!!)
                    item!!.feed != null && item!!.feed!!.isLocalFeed -> PlayLocalActionButton(item!!)
                    media.isDownloaded() -> PlayActionButton(item!!)
                    else -> StreamActionButton(item!!)
                }
                actionButton2 = when {
                    media.getMediaType() == MediaType.FLASH -> VisitWebsiteActionButton(item!!)
                    dls != null && media.download_url != null && dls.isDownloadingEpisode(media.download_url!!) -> CancelDownloadActionButton(item!!)
                    !media.isDownloaded() -> DownloadActionButton(item!!)
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
        if (item == null) return

        val fragment: Fragment = FeedItemlistFragment.newInstance(item!!.feedId)
        (activity as MainActivity).loadChildFragment(fragment)
    }

    private fun procFlowEvents() {
        lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                when (event) {
                    is FlowEvent.FeedItemEvent -> onEventMainThread(event)
                    is FlowEvent.PlayerStatusEvent -> updateButtons()
                    is FlowEvent.UnreadItemsUpdateEvent -> load()
                    else -> {}
                }
            }
        }
        lifecycleScope.launch {
            EventFlow.stickyEvents.collectLatest { event ->
                when (event) {
                    is FlowEvent.EpisodeDownloadEvent -> onEventMainThread(event)
                    else -> {}
                }
            }
        }
    }

    fun onEventMainThread(event: FlowEvent.FeedItemEvent) {
        Logd(TAG, "onEventMainThread() called with: event = [$event]")
        if (this.item == null) return
        for (item in event.items) {
            if (this.item!!.id == item.id) {
                load()
                return
            }
        }
    }

    fun onEventMainThread(event: FlowEvent.EpisodeDownloadEvent) {
        if (item == null || item!!.media == null) return
        if (!event.urls.contains(item!!.media!!.download_url)) return
        if (itemsLoaded && activity != null) updateButtons()
    }

//    @UnstableApi private fun load0() {
//        disposable?.dispose()
//        if (!itemsLoaded) progbarLoading.visibility = View.VISIBLE
//
//        Logd(TAG, "load() called")
//        disposable = Observable.fromCallable<FeedItem?> { this.loadInBackground() }
//            .subscribeOn(Schedulers.io())
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribe({ result: FeedItem? ->
//                progbarLoading.visibility = View.GONE
//                item = result
//                onFragmentLoaded()
//                itemsLoaded = true
//            },
//                { error: Throwable? ->
//                    Log.e(TAG, Log.getStackTraceString(error))
//                })
//    }

    @UnstableApi private fun load() {
        if (!itemsLoaded) progbarLoading.visibility = View.VISIBLE

        Logd(TAG, "load() called")
//        val scope = CoroutineScope(Dispatchers.Main)
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val feedItem = item
                    if (feedItem != null) {
                        val duration = feedItem.media?.getDuration()?: Int.MAX_VALUE
                        if (feedItem.description == null || feedItem.transcript == null) DBReader.loadTextDetailsOfFeedItem(feedItem)
                        webviewData = ShownotesCleaner(requireContext(), feedItem.description?:"", duration).processShownotes()
                    }
                    feedItem
                }
                withContext(Dispatchers.Main) {
                    progbarLoading.visibility = View.GONE
                    item = result
                    onFragmentLoaded()
                    itemsLoaded = true
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
    }

    fun setItem(item_: FeedItem) {
        item = item_
    }

    companion object {
        private const val TAG = "EpisodeInfoFragment"

        @JvmStatic
        fun newInstance(item: FeedItem): EpisodeInfoFragment {
            val fragment = EpisodeInfoFragment()
            fragment.setItem(item)
            return fragment
        }
    }
}
