package ac.mdiq.podcini.ui.adapter

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.EpisodeHomeFragmentBinding
import ac.mdiq.podcini.databinding.EpisodeInfoFragmentBinding
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.net.utils.NetworkUtils.fetchHtmlSource
import ac.mdiq.podcini.net.utils.NetworkUtils.isEpisodeHeadDownloadAllowed
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
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
import ac.mdiq.podcini.ui.actions.actionbutton.*
import ac.mdiq.podcini.ui.actions.handler.EpisodeMenuHandler
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.fragment.FeedEpisodesFragment
import ac.mdiq.podcini.ui.fragment.FeedInfoFragment
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.utils.ThemeUtils
import ac.mdiq.podcini.ui.view.EpisodeViewHolder
import ac.mdiq.podcini.ui.view.ShownotesWebView
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter.formatAbbrev
import ac.mdiq.podcini.util.MiscFormatter.formatForAccessibility
import android.R.color
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Layout
import android.text.TextUtils
import android.text.format.Formatter.formatShortFileSize
import android.util.Log
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ShareCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
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
import net.dankito.readability4j.extended.Readability4JExtended
import okhttp3.Request.Builder
import java.io.File
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.max

/**
 * List adapter for the list of new episodes.
 */
open class EpisodesAdapter(mainActivity: MainActivity, var refreshFragPosCallback: ((Int, Episode) -> Unit)? = null)
    : SelectableAdapter<EpisodeViewHolder?>(mainActivity) {

    private val TAG: String = this::class.simpleName ?: "Anonymous"

    val mainActivityRef: WeakReference<MainActivity> = WeakReference<MainActivity>(mainActivity)
    protected val activity: Activity?
        get() = mainActivityRef.get()

    private var episodes: MutableList<Episode> = ArrayList()
    private var feed: Feed? = null
    var longPressedItem: Episode? = null
    private var longPressedPosition: Int = 0 // used to init actionMode
    private var dummyViews = 0

    val selectedItems: List<Episode>
        get() {
            val items: MutableList<Episode> = ArrayList()
            for (i in 0 until itemCount) {
                if (i < episodes.size && isSelected(i)) {
                    val item = getItem(i)
                    if (item != null) items.add(item)
                }
            }
            return items
        }

    init {
        setHasStableIds(true)
    }

    @UnstableApi
    fun refreshPosCallback(pos: Int, episode: Episode) {
        Logd(TAG, "refreshPosCallback: $pos ${episode.title}")
        if (pos >= 0 && pos < episodes.size && episodes[pos].id == episode.id) {
            episodes[pos] = episode
//        notifyItemChanged(pos, "foo")
            refreshFragPosCallback?.invoke(pos, episode)
        }
    }

    fun clearData() {
        episodes = mutableListOf()
        feed = null
        notifyDataSetChanged()
    }

    fun updateItems(items: MutableList<Episode>, feed_: Feed? = null) {
        episodes = items
        feed = feed_
        notifyDataSetChanged()
        updateTitle()
    }

    override fun getItemViewType(position: Int): Int {
        return R.id.view_type_episode_item
    }

    @UnstableApi override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
//  TODO: the Invalid resource ID 0x00000000 on Android 14 occurs after this and before onBindViewHolder,
//        somehow, only on the first time EpisodeItemListAdapter is called
        return EpisodeViewHolder(mainActivityRef.get()!!, parent)
    }

    @UnstableApi
    override fun onBindViewHolder(holder: EpisodeViewHolder, pos: Int) {
        Logd(TAG, "onBindViewHolder $pos ${episodes[pos].title}")
        if (pos >= episodes.size || pos < 0) {
            Logd(TAG, "onBindViewHolder got invalid pos: $pos of ${episodes.size}")
            return
        }

        holder.refreshAdapterPosCallback = ::refreshPosCallback
        holder.setPosIndex(pos)

        // Reset state of recycled views
        holder.coverHolder.visibility = View.VISIBLE
        holder.dragHandle.visibility = View.GONE

        beforeBindViewHolder(holder, pos)

        val item: Episode = episodes[pos]
        item.feed = feed ?: episodes[pos].feed
        holder.bind(item)

        holder.infoCard.setOnLongClickListener {
            longPressedItem = holder.episode
            longPressedPosition = holder.bindingAdapterPosition
            startSelectMode(longPressedPosition)
            true
        }
        holder.infoCard.setOnClickListener {
            val activity: MainActivity? = mainActivityRef.get()
            if (!inActionMode() && holder.episode != null) activity?.loadChildFragment(EpisodeInfoFragment.newInstance(holder.episode!!))
            else toggleSelection(holder.bindingAdapterPosition)
        }
        holder.coverHolder.setOnClickListener {
            val activity: MainActivity? = mainActivityRef.get()
            if (!inActionMode() && holder.episode?.feed != null) activity?.loadChildFragment(FeedInfoFragment.newInstance(holder.episode!!.feed!!))
            else toggleSelection(holder.bindingAdapterPosition)
        }
        holder.itemView.setOnTouchListener(View.OnTouchListener { _: View?, e: MotionEvent ->
            if (e.isFromSource(InputDevice.SOURCE_MOUSE) && e.buttonState == MotionEvent.BUTTON_SECONDARY) {
                longPressedItem = holder.episode
                longPressedPosition = holder.bindingAdapterPosition
                return@OnTouchListener false
            }
            false
        })
        if (inActionMode()) {
            holder.secondaryActionButton.setOnClickListener(null)
            if (isSelected(pos))
                holder.itemView.setBackgroundColor(-0x78000000 + (0xffffff and ThemeUtils.getColorFromAttr(mainActivityRef.get()!!, androidx.appcompat.R.attr.colorAccent)))
            else holder.itemView.setBackgroundResource(color.transparent)
        }

        afterBindViewHolder(holder, pos)
        holder.hideSeparatorIfNecessary()
    }

    @UnstableApi
    override fun onBindViewHolder(holder: EpisodeViewHolder, pos: Int, payloads: MutableList<Any>) {
//       Logd(TAG, "onBindViewHolder payloads $pos ${holder.episode?.title}")
        if (payloads.isEmpty()) onBindViewHolder(holder, pos)
        else {
            holder.refreshAdapterPosCallback = ::refreshPosCallback
            val payload = payloads[0]
            when {
                payload is String && payload == "foo" -> onBindViewHolder(holder, pos)
                payload is Bundle && !payload.getString("PositionUpdate").isNullOrEmpty() -> holder.updatePlaybackPositionNew(episodes[pos])
            }
        }
    }

    protected open fun beforeBindViewHolder(holder: EpisodeViewHolder, pos: Int) {}

    protected open fun afterBindViewHolder(holder: EpisodeViewHolder, pos: Int) {}

    @UnstableApi override fun onViewRecycled(holder: EpisodeViewHolder) {
        super.onViewRecycled(holder)
        holder.refreshAdapterPosCallback = null
        holder.unbind()
    }

    /**
     * [.notifyItemChanged] is final, so we can not override.
     * Calling [.notifyItemChanged] may bind the item to a new ViewHolder and execute a transition.
     * This causes flickering and breaks the download animation that stores the old progress in the View.
     * Instead, we tell the adapter to use partial binding by calling [.notifyItemChanged].
     * We actually ignore the payload and always do a full bind but calling the partial bind method ensures
     * that ViewHolders are always re-used.
     * @param position Position of the item that has changed
     */
    fun notifyItemChangedCompat(position: Int) {
        notifyItemChanged(position, "foo")
    }

    override fun getItemId(position: Int): Long {
        return getItem(position)?.id ?: 0L
    }

    override fun getItemCount(): Int {
        return dummyViews + episodes.size
    }

    protected fun getItem(index: Int): Episode? {
        val item = if (index in episodes.indices) episodes[index] else null
        return item
    }

    /**
     * Displays information about an Episode (FeedItem) and actions.
     */
    @UnstableApi class EpisodeInfoFragment : Fragment(), Toolbar.OnMenuItemClickListener {
        private var _binding: EpisodeInfoFragmentBinding? = null
        private val binding get() = _binding!!

        private var homeFragment: EpisodeHomeFragment? = null

        private var itemLoaded = false
        private var episode: Episode? = null    // managed
        private var webviewData: String? = null

        private lateinit var shownotesCleaner: ShownotesCleaner
        private lateinit var toolbar: MaterialToolbar
        private lateinit var webvDescription: ShownotesWebView
        private lateinit var imgvCover: ImageView

        private lateinit var butAction1: ImageView
        private lateinit var butAction2: ImageView

        private var actionButton1: EpisodeActionButton? = null
        private var actionButton2: EpisodeActionButton? = null

        @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            super.onCreateView(inflater, container, savedInstanceState)

            _binding = EpisodeInfoFragmentBinding.inflate(inflater, container, false)
            Logd(TAG, "fragment onCreateView")

            toolbar = binding.toolbar
            toolbar.title = ""
            toolbar.inflateMenu(R.menu.feeditem_options)
            toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
            toolbar.setOnMenuItemClickListener(this)

            binding.txtvPodcast.setOnClickListener { openPodcast() }
            binding.txtvTitle.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
            binding.txtvTitle.ellipsize = TextUtils.TruncateAt.END
            webvDescription = binding.webvDescription
            webvDescription.setTimecodeSelectedListener { time: Int? ->
                val cMedia = curMedia
                if (episode?.media?.getIdentifier() == cMedia?.getIdentifier()) seekTo(time ?: 0)
                else (activity as MainActivity).showSnackbarAbovePlayer(R.string.play_this_to_seek_position, Snackbar.LENGTH_LONG)
            }
            registerForContextMenu(webvDescription)

            imgvCover = binding.imgvCover
            imgvCover.setOnClickListener { openPodcast() }
            butAction1 = binding.butAction1
            butAction2 = binding.butAction2

            binding.homeButton.setOnClickListener {
                if (!episode?.link.isNullOrEmpty()) {
                    homeFragment = EpisodeHomeFragment.newInstance(episode!!)
                    (activity as MainActivity).loadChildFragment(homeFragment!!)
                } else Toast.makeText(context, "Episode link is not valid ${episode?.link}", Toast.LENGTH_LONG).show()
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
            balloon.showAlignBottom(butAction1, 0, (-12 * resources.displayMetrics.density).toInt())
        }

        @UnstableApi override fun onMenuItemClick(menuItem: MenuItem): Boolean {
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

        @UnstableApi override fun onResume() {
            super.onResume()
            if (itemLoaded) {
                binding.progbarLoading.visibility = View.GONE
                updateAppearance()
            }
        }

        @OptIn(UnstableApi::class) override fun onDestroyView() {
            Logd(TAG, "onDestroyView")
            binding.root.removeView(webvDescription)
            episode = null
            webvDescription.clearHistory()
            webvDescription.clearCache(true)
            webvDescription.clearView()
            webvDescription.destroy()
            _binding = null
            super.onDestroyView()
        }

        @UnstableApi private fun onFragmentLoaded() {
            if (webviewData != null && !itemLoaded)
                webvDescription.loadDataWithBaseURL("https://127.0.0.1", webviewData!!, "text/html", "utf-8", "about:blank")
//        if (item?.link != null) binding.webView.loadUrl(item!!.link!!)
            updateAppearance()
        }

        private fun prepareMenu() {
            if (episode!!.media != null) EpisodeMenuHandler.onPrepareMenu(toolbar.menu, episode, R.id.open_podcast)
            // these are already available via button1 and button2
            else EpisodeMenuHandler.onPrepareMenu(toolbar.menu, episode, R.id.open_podcast, R.id.mark_read_item, R.id.visit_website_item)
        }

        @UnstableApi private fun updateAppearance() {
            if (episode == null) {
                Logd(TAG, "updateAppearance item is null")
                return
            }
            prepareMenu()

            if (episode!!.feed != null) binding.txtvPodcast.text = episode!!.feed!!.title
            binding.txtvTitle.text = episode!!.title
            binding.itemLink.text = episode!!.link

            if (episode?.pubDate != null) {
                val pubDateStr = formatAbbrev(context, Date(episode!!.pubDate))
                binding.txtvPublished.text = pubDateStr
                binding.txtvPublished.setContentDescription(formatForAccessibility(Date(episode!!.pubDate)))
            }

            val media = episode?.media
            when {
                media == null -> binding.txtvSize.text = ""
                media.size > 0 -> binding.txtvSize.text = formatShortFileSize(activity, media.size)
                isEpisodeHeadDownloadAllowed && !media.checkedOnSizeButUnknown() -> {
                    binding.txtvSize.text = "{faw_spinner}"
//                Iconify.addIcons(size)
                    lifecycleScope.launch {
                        val sizeValue = getMediaSize(episode)
                        if (sizeValue <= 0) binding.txtvSize.text = ""
                        else binding.txtvSize.text = formatShortFileSize(activity, sizeValue)
                    }
                }
                else -> binding.txtvSize.text = ""
            }

            val imgLocFB = ImageResourceUtils.getFallbackImageLocation(episode!!)
            val imageLoader = imgvCover.context.imageLoader
            val imageRequest = ImageRequest.Builder(requireContext())
                .data(episode!!.imageLocation)
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
            binding.circularProgressBar.visibility = View.GONE
            val dls = DownloadServiceInterface.get()
            if (episode != null && episode!!.media != null && episode!!.media!!.downloadUrl != null) {
                val url = episode!!.media!!.downloadUrl!!
                if (dls != null && dls.isDownloadingEpisode(url)) {
                    binding.circularProgressBar.visibility = View.VISIBLE
                    binding.circularProgressBar.setPercentage(0.01f * max(1.0, dls.getProgress(url).toDouble()).toFloat(), episode)
                    binding.circularProgressBar.setIndeterminate(dls.isEpisodeQueued(url))
                }
            }

            val media: EpisodeMedia? = episode?.media
            if (media == null) {
                if (episode != null) {
//                actionButton1 = VisitWebsiteActionButton(item!!)
                    butAction1.visibility = View.INVISIBLE
                    actionButton2 = VisitWebsiteActionButton(episode!!)
                }
                binding.noMediaLabel.visibility = View.VISIBLE
            } else {
                binding.noMediaLabel.visibility = View.GONE
                if (media.getDuration() > 0) {
                    binding.txtvDuration.text = DurationConverter.getDurationStringLong(media.getDuration())
                    binding.txtvDuration.setContentDescription(DurationConverter.getDurationStringLocalized(requireContext(), media.getDuration().toLong()))
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
                        is FlowEvent.FavoritesEvent -> onFavoriteEvent(event)
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

        private fun onFavoriteEvent(event: FlowEvent.FavoritesEvent) {
            if (episode?.id == event.episode.id) {
                episode = unmanaged(episode!!)
                episode!!.isFavorite = event.episode.isFavorite
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
//                episode = unmanaged(item_)
//                episode = item_
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
        @UnstableApi private fun load() {
            if (!itemLoaded) binding.progbarLoading.visibility = View.VISIBLE
            Logd(TAG, "load() called")
            if (!loadItemsRunning) {
                loadItemsRunning = true
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            if (episode != null) {
                                val duration = episode!!.media?.getDuration() ?: Int.MAX_VALUE
                                webviewData = shownotesCleaner.processShownotes(episode!!.description ?: "", duration)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            binding.progbarLoading.visibility = View.GONE
                            onFragmentLoaded()
                            itemLoaded = true
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, Log.getStackTraceString(e))
                    } finally {
                        loadItemsRunning = false
                    }
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

            @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
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

            @UnstableApi override fun onResume() {
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

            @UnstableApi private fun updateAppearance() {
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
                    Logd(TAG, "item.itemIdentifier ${item.identifier}")
                    if (item.identifier != episode?.identifier) episode = item
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

}
