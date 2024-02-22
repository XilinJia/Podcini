package ac.mdiq.podcini.fragment

import ac.mdiq.podcini.activity.MainActivity
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.snackbar.Snackbar
import com.skydoves.balloon.ArrowOrientation
import com.skydoves.balloon.ArrowOrientationRules
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import ac.mdiq.podcini.R
import ac.mdiq.podcini.adapter.actionbutton.*
import ac.mdiq.podcini.core.feed.util.ImageResourceUtils
import ac.mdiq.podcini.core.preferences.UsageStatistics
import ac.mdiq.podcini.core.storage.DBReader
import ac.mdiq.podcini.core.util.Converter
import ac.mdiq.podcini.core.util.DateFormatter
import ac.mdiq.podcini.core.util.PlaybackStatus
import ac.mdiq.podcini.core.util.gui.ShownotesCleaner
import ac.mdiq.podcini.core.util.playback.PlaybackController
import ac.mdiq.podcini.event.EpisodeDownloadEvent
import ac.mdiq.podcini.event.FeedItemEvent
import ac.mdiq.podcini.event.PlayerStatusEvent
import ac.mdiq.podcini.event.UnreadItemsUpdateEvent
import ac.mdiq.podcini.model.feed.FeedItem
import ac.mdiq.podcini.model.feed.FeedMedia
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.storage.preferences.UserPreferences
import ac.mdiq.podcini.ui.common.CircularProgressBar
import ac.mdiq.podcini.ui.common.ThemeUtils
import ac.mdiq.podcini.view.ShownotesWebView
import androidx.annotation.OptIn
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*
import kotlin.math.max

/**
 * Displays information about a FeedItem and actions.
 */
class ItemFragment : Fragment() {
    private var itemsLoaded = false
    private var itemId: Long = 0
    private var item: FeedItem? = null
    private var webviewData: String? = null

    private lateinit var root: ViewGroup
    private lateinit var webvDescription: ShownotesWebView
    private lateinit var txtvPodcast: TextView
    private lateinit var txtvTitle: TextView
    private lateinit var txtvDuration: TextView
    private lateinit var txtvPublished: TextView
    private lateinit var imgvCover: ImageView
    private lateinit var progbarDownload: CircularProgressBar
    private lateinit var progbarLoading: ProgressBar
    private lateinit var butAction1Text: TextView
    private lateinit var butAction2Text: TextView
    private lateinit var butAction1Icon: ImageView
    private lateinit var butAction2Icon: ImageView
    private lateinit var butAction1: View
    private lateinit var butAction2: View
    private lateinit var noMediaLabel: View

    private var actionButton1: ItemActionButton? = null
    private var actionButton2: ItemActionButton? = null

    private var disposable: Disposable? = null
    private var controller: PlaybackController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        itemId = requireArguments().getLong(ARG_FEEDITEM)
    }

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        val layout: View = inflater.inflate(R.layout.feeditem_fragment, container, false)

        root = layout.findViewById(R.id.content_root)

        Log.d(TAG, "fregment onCreateView")
        txtvPodcast = layout.findViewById(R.id.txtvPodcast)
        txtvPodcast.setOnClickListener { v: View? -> openPodcast() }
        txtvTitle = layout.findViewById(R.id.txtvTitle)
        if (Build.VERSION.SDK_INT >= 23) {
            txtvTitle.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
        }
        txtvDuration = layout.findViewById(R.id.txtvDuration)
        txtvPublished = layout.findViewById(R.id.txtvPublished)
        txtvTitle.ellipsize = TextUtils.TruncateAt.END
        webvDescription = layout.findViewById(R.id.webvDescription)
        webvDescription.setTimecodeSelectedListener { time: Int? ->
            if (controller != null && item != null && item!!.media != null && controller!!.getMedia() != null &&
                    item!!.media!!.getIdentifier() == controller!!.getMedia()!!.getIdentifier()) {
                controller!!.seekTo(time ?: 0)
            } else {
                (activity as MainActivity).showSnackbarAbovePlayer(R.string.play_this_to_seek_position,
                    Snackbar.LENGTH_LONG)
            }
        }
        registerForContextMenu(webvDescription)

        imgvCover = layout.findViewById(R.id.imgvCover)
        imgvCover.setOnClickListener { v: View? -> openPodcast() }
        progbarDownload = layout.findViewById(R.id.circularProgressBar)
        progbarLoading = layout.findViewById(R.id.progbarLoading)
        butAction1 = layout.findViewById(R.id.butAction1)
        butAction2 = layout.findViewById(R.id.butAction2)
        butAction1Icon = layout.findViewById(R.id.butAction1Icon)
        butAction2Icon = layout.findViewById(R.id.butAction2Icon)
        butAction1Text = layout.findViewById(R.id.butAction1Text)
        butAction2Text = layout.findViewById(R.id.butAction2Text)
        noMediaLabel = layout.findViewById(R.id.noMediaLabel)

        butAction1.setOnClickListener(View.OnClickListener { v: View? ->
            if (actionButton1 is StreamActionButton && !UserPreferences.isStreamOverDownload
                    && UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_STREAM)) {
                showOnDemandConfigBalloon(true)
                return@OnClickListener
            } else if (actionButton1 == null) {
                return@OnClickListener  // Not loaded yet
            }
            actionButton1?.onClick(requireContext())
        })
        butAction2.setOnClickListener(View.OnClickListener { v: View? ->
            if (actionButton2 is DownloadActionButton && UserPreferences.isStreamOverDownload
                    && UsageStatistics.hasSignificantBiasTo(UsageStatistics.ACTION_DOWNLOAD)) {
                showOnDemandConfigBalloon(false)
                return@OnClickListener
            } else if (actionButton2 == null) {
                return@OnClickListener  // Not loaded yet
            }
            actionButton2?.onClick(requireContext())
        })

        EventBus.getDefault().register(this)
        controller = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {
                // Do nothing
            }
        }
        controller?.init()
        load()

        return layout
    }

    @OptIn(UnstableApi::class) private fun showOnDemandConfigBalloon(offerStreaming: Boolean) {
        val isLocaleRtl = (TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL)
        val balloon: Balloon = Balloon.Builder(requireContext())
            .setArrowOrientation(ArrowOrientation.TOP)
            .setArrowOrientationRules(ArrowOrientationRules.ALIGN_FIXED)
            .setArrowPosition(0.25f + (if ((isLocaleRtl xor offerStreaming)) 0f else 0.5f))
            .setWidthRatio(1.0f)
            .setMarginLeft(8)
            .setMarginRight(8)
            .setBackgroundColor(ThemeUtils.getColorFromAttr(requireContext(), R.attr.colorSecondary))
            .setBalloonAnimation(BalloonAnimation.OVERSHOOT)
            .setLayout(R.layout.popup_bubble_view)
            .setDismissWhenTouchOutside(true)
            .setLifecycleOwner(this)
            .build()
        val positiveButton = balloon.getContentView().findViewById<Button>(R.id.balloon_button_positive)
        val negativeButton = balloon.getContentView().findViewById<Button>(R.id.balloon_button_negative)
        val message: TextView = balloon.getContentView().findViewById(R.id.balloon_message)
        message.setText(if (offerStreaming
        ) R.string.on_demand_config_stream_text else R.string.on_demand_config_download_text)
        positiveButton.setOnClickListener { v1: View? ->
            UserPreferences.isStreamOverDownload = offerStreaming
            // Update all visible lists to reflect new streaming action button
            EventBus.getDefault().post(UnreadItemsUpdateEvent())
            (activity as MainActivity).showSnackbarAbovePlayer(R.string.on_demand_config_setting_changed, Snackbar.LENGTH_SHORT)
            balloon.dismiss()
        }
        negativeButton.setOnClickListener { v1: View? ->
            UsageStatistics.doNotAskAgain(UsageStatistics.ACTION_STREAM) // Type does not matter. Both are silenced.
            balloon.dismiss()
        }
        balloon.showAlignBottom(butAction1, 0, (-12 * resources.displayMetrics.density).toInt())
    }

    @UnstableApi override fun onStart() {
        super.onStart()
//        EventBus.getDefault().register(this)
//        controller = object : PlaybackController(activity) {
//            override fun loadMediaInfo() {
//                // Do nothing
//            }
//        }
//        controller?.init()
//        load()
    }

    @UnstableApi override fun onResume() {
        super.onResume()
        if (itemsLoaded) {
            progbarLoading.visibility = View.GONE
            updateAppearance()
        }
    }

    @UnstableApi override fun onStop() {
        super.onStop()
//        EventBus.getDefault().unregister(this)
//        controller?.release()
    }

    @OptIn(UnstableApi::class) override fun onDestroyView() {
        super.onDestroyView()
        EventBus.getDefault().unregister(this)
        controller?.release()
        disposable?.dispose()
        root.removeView(webvDescription)
        webvDescription.destroy()
    }

    @UnstableApi private fun onFragmentLoaded() {
        if (webviewData != null && !itemsLoaded) {
            webvDescription.loadDataWithBaseURL("https://127.0.0.1", webviewData!!, "text/html", "utf-8", "about:blank")
        }
        updateAppearance()
    }

    @UnstableApi private fun updateAppearance() {
        if (item == null) {
            Log.d(TAG, "updateAppearance item is null")
            return
        }
        if (item!!.feed != null) txtvPodcast.text = item!!.feed!!.title
        txtvTitle.text = item!!.title

        if (item?.pubDate != null) {
            val pubDateStr = DateFormatter.formatAbbrev(activity, item!!.pubDate)
            txtvPublished.text = pubDateStr
            txtvPublished.setContentDescription(DateFormatter.formatForAccessibility(item!!.pubDate))
        }

        val options: RequestOptions = RequestOptions()
            .error(R.color.light_gray)
            .transform(FitCenter(),
                RoundedCorners((8 * resources.displayMetrics.density).toInt()))
            .dontAnimate()

        Glide.with(this)
            .load(item!!.imageLocation)
            .error(Glide.with(this)
                .load(ImageResourceUtils.getFallbackImageLocation(item!!))
                .apply(options))
            .apply(options)
            .into(imgvCover)
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
                actionButton1 = MarkAsPlayedActionButton(item!!)
                actionButton2 = VisitWebsiteActionButton(item!!)
            }
            noMediaLabel.visibility = View.VISIBLE
        } else {
            noMediaLabel.visibility = View.GONE
            if (media.getDuration() > 0) {
                txtvDuration.text = Converter.getDurationStringLong(media.getDuration())
                txtvDuration.setContentDescription(
                    Converter.getDurationStringLocalized(requireContext(), media.getDuration().toLong()))
            }
            if (item != null) {
                actionButton1 = if (PlaybackStatus.isCurrentlyPlaying(media)) {
                    PauseActionButton(item!!)
                } else if (item!!.feed != null && item!!.feed!!.isLocalFeed) {
                    PlayLocalActionButton(item)
                } else if (media.isDownloaded()) {
                    PlayActionButton(item!!)
                } else {
                    StreamActionButton(item!!)
                }
                actionButton2 = if (dls != null && media.download_url != null && dls.isDownloadingEpisode(media.download_url!!)) {
                    CancelDownloadActionButton(item!!)
                } else if (!media.isDownloaded()) {
                    DownloadActionButton(item!!)
                } else {
                    DeleteActionButton(item!!)
                }
            }
        }

        if (actionButton1 != null) {
            butAction1Text.setText(actionButton1!!.getLabel())
            butAction1Icon.setImageResource(actionButton1!!.getDrawable())
        }
        butAction1Text.transformationMethod = null
        if (actionButton1 != null) butAction1.visibility = actionButton1!!.visibility

        if (actionButton2 != null) {
            butAction2Text.setText(actionButton2!!.getLabel())
            butAction2Icon.setImageResource(actionButton2!!.getDrawable())
        }
        butAction2Text.transformationMethod = null
        if (actionButton2 != null) butAction2.visibility = actionButton2!!.visibility
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return webvDescription.onContextItemSelected(item)
    }

    @OptIn(UnstableApi::class) private fun openPodcast() {
        if (item == null) {
            return
        }
        val fragment: Fragment = FeedItemlistFragment.newInstance(item!!.feedId)
        (activity as MainActivity).loadChildFragment(fragment)
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        Log.d(TAG, "onEventMainThread() called with: event = [$event]")
        if (this.item == null) return
        for (item in event.items) {
            if (this.item!!.id == item.id) {
                load()
                return
            }
        }
    }

    @UnstableApi @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: EpisodeDownloadEvent) {
        if (item == null || item!!.media == null) {
            return
        }
        if (!event.urls.contains(item!!.media!!.download_url)) {
            return
        }
        if (itemsLoaded && activity != null) {
            updateButtons()
        }
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusChanged(event: PlayerStatusEvent?) {
        updateButtons()
    }

    @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUnreadItemsChanged(event: UnreadItemsUpdateEvent?) {
        load()
    }

    @UnstableApi private fun load() {
        disposable?.dispose()

        if (!itemsLoaded) {
            progbarLoading.visibility = View.VISIBLE
        }
        disposable = Observable.fromCallable<FeedItem?> { this.loadInBackground() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result: FeedItem? ->
                progbarLoading.visibility = View.GONE
                item = result
                onFragmentLoaded()
                itemsLoaded = true
            },
                { error: Throwable? ->
                    Log.e(TAG,
                        Log.getStackTraceString(error))
                })
    }

    private fun loadInBackground(): FeedItem? {
        val feedItem: FeedItem? = DBReader.getFeedItem(itemId)
        val context = context
        if (feedItem != null && context != null) {
            val duration = if (feedItem.media != null) feedItem.media!!.getDuration() else Int.MAX_VALUE
            DBReader.loadDescriptionOfFeedItem(feedItem)
            val t = ShownotesCleaner(context, feedItem.description?:"", duration)
            webviewData = t.processShownotes()
        }
        return feedItem
    }

    companion object {
        private const val TAG = "ItemFragment"
        private const val ARG_FEEDITEM = "feeditem"

        /**
         * Creates a new instance of an ItemFragment
         *
         * @param feeditem The ID of the FeedItem to show
         * @return The ItemFragment instance
         */
        @JvmStatic
        fun newInstance(feeditem: Long): ItemFragment {
            val fragment = ItemFragment()
            val args = Bundle()
            args.putLong(ARG_FEEDITEM, feeditem)
            fragment.arguments = args
            return fragment
        }
    }
}
