package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.PlayerDetailsFragmentBinding
import ac.mdiq.podcini.feed.util.ImageResourceUtils
import ac.mdiq.podcini.playback.PlaybackController
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBWriter.persistFeedItem
import ac.mdiq.podcini.storage.model.feed.Chapter
import ac.mdiq.podcini.storage.model.feed.EmbeddedChapterImage
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeActions
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeActions.Companion.SWIPE_ACTIONS_PREF_NAME
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.view.ShownotesWebView
import ac.mdiq.podcini.util.ChapterUtils
import ac.mdiq.podcini.util.DateFormatter
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.NetworkUtils.fetchHtmlSource
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.*
import android.graphics.ColorFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import org.apache.commons.lang3.StringUtils

/**
 * Displays the description of a Playable object in a Webview.
 */
@UnstableApi
class  PlayerDetailsFragment : Fragment() {
    private lateinit var shownoteView: ShownotesWebView

    private var _binding: PlayerDetailsFragmentBinding? = null
    private val binding get() = _binding!!

    private var prevItem: FeedItem? = null
    private var media: Playable? = null
    private var item: FeedItem? = null
    private var displayedChapterIndex = -1

//    val scope = CoroutineScope(Dispatchers.Main)
    private var cleanedNotes: String? = null
//    private var webViewLoader: Disposable? = null
    private var controller: PlaybackController? = null

    internal var showHomeText = false
    internal var homeText: String? = null
    internal var readerhtml: String? = null

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logd(TAG, "fragment onCreateView")
        _binding = PlayerDetailsFragmentBinding.inflate(inflater)

        binding.imgvCover.setOnClickListener { onPlayPause() }

        val colorFilter: ColorFilter? = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(binding.txtvPodcastTitle.currentTextColor, BlendModeCompat.SRC_IN)
        binding.butNextChapter.colorFilter = colorFilter
        binding.butPrevChapter.colorFilter = colorFilter
        binding.chapterButton.setOnClickListener { ChaptersFragment().show(childFragmentManager, ChaptersFragment.TAG) }
        binding.butPrevChapter.setOnClickListener { seekToPrevChapter() }
        binding.butNextChapter.setOnClickListener { seekToNextChapter() }

        Logd(TAG, "fragment onCreateView")
        shownoteView = binding.webview
        shownoteView.setTimecodeSelectedListener { time: Int? -> controller?.seekTo(time!!) }
        shownoteView.setPageFinishedListener {
            // Restoring the scroll position might not always work
            shownoteView.postDelayed({ this@PlayerDetailsFragment.restoreFromPreference() }, 50)
        }

        binding.root.addOnLayoutChangeListener(object : OnLayoutChangeListener {
            override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                if (binding.root.measuredHeight != shownoteView.minimumHeight) shownoteView.setMinimumHeight(binding.root.measuredHeight)
                binding.root.removeOnLayoutChangeListener(this)
            }
        })
        registerForContextMenu(shownoteView)
        controller = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {
                load()
            }
        }
        controller?.init()
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        controller?.release()
        controller = null
        Logd(TAG, "Fragment destroyed")
        shownoteView.removeAllViews()
        shownoteView.destroy()
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return shownoteView.onContextItemSelected(item)
    }

    private fun load() {
        val context = context ?: return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (item == null) {
                    media = controller?.getMedia()
                    if (media != null && media is FeedMedia) {
                        val feedMedia = media as FeedMedia
                        item = feedMedia.item
                        item?.setDescription(null)
                        showHomeText = false
                        homeText = null
                    }
                }
                if (item != null) {
                    media = item!!.media
                    if (item!!.description == null) DBReader.loadTextDetailsOfFeedItem(item!!)
                    if (prevItem?.itemIdentifier != item!!.itemIdentifier) cleanedNotes = null
                    if (cleanedNotes == null) {
                        Logd(TAG, "calling load description ${item!!.description==null} ${item!!.title}")
                        val shownotesCleaner = ShownotesCleaner(context, item?.description ?: "", media?.getDuration()?:0)
                        cleanedNotes = shownotesCleaner.processShownotes()
                    }
                    prevItem = item
                }
            }
            withContext(Dispatchers.Main) {
                Logd(TAG, "subscribe: ${media?.getEpisodeTitle()}")
                displayMediaInfo(media!!)
                shownoteView.loadDataWithBaseURL("https://127.0.0.1", cleanedNotes?:"No notes", "text/html", "utf-8", "about:blank")
                Logd(TAG, "Webview loaded")
            }
        }.invokeOnCompletion { throwable ->
            if (throwable!= null) {
                Log.e(TAG, Log.getStackTraceString(throwable))
            }
        }
    }

    fun buildHomeReaderText() {
        showHomeText = !showHomeText
        if (showHomeText) {
            homeText = item!!.transcript
            runBlocking {
                if (homeText == null && item?.link != null) {
                    val url = item!!.link!!
                    val htmlSource = fetchHtmlSource(url)
                    val readability4J = Readability4J(item!!.link!!, htmlSource)
                    val article = readability4J.parse()
                    readerhtml = article.contentWithDocumentsCharsetOrUtf8
                    if (!readerhtml.isNullOrEmpty()) {
                        item!!.setTranscriptIfLonger(readerhtml)
                        homeText = item!!.transcript
                        persistFeedItem(item)
                    }
                }
            }
            if (!homeText.isNullOrEmpty()) {
                val shownotesCleaner = ShownotesCleaner(requireContext(), homeText!!, 0)
                cleanedNotes = shownotesCleaner.processShownotes()
                shownoteView.loadDataWithBaseURL("https://127.0.0.1", cleanedNotes?:"No notes", "text/html", "UTF-8", null)
            } else Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show()
        } else {
            val shownotesCleaner = ShownotesCleaner(requireContext(), item?.description ?: "", media?.getDuration()?:0)
            cleanedNotes = shownotesCleaner.processShownotes()
            if (!cleanedNotes.isNullOrEmpty()) shownoteView.loadDataWithBaseURL("https://127.0.0.1", cleanedNotes?:"No notes", "text/html", "UTF-8", null)
            else Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show()
        }
    }

    @UnstableApi private fun displayMediaInfo(media: Playable) {
        Logd(TAG, "displayMediaInfo ${item?.title} ${media.getEpisodeTitle()}")
        val pubDateStr = DateFormatter.formatAbbrev(context, media.getPubDate())
        binding.txtvPodcastTitle.text = StringUtils.stripToEmpty(media.getFeedTitle())
        if (media is FeedMedia) {
            if (item != null) {
                val openFeed: Intent = MainActivity.getIntentToOpenFeed(requireContext(), item!!.feedId)
                binding.txtvPodcastTitle.setOnClickListener { startActivity(openFeed) }
            }
        } else {
            binding.txtvPodcastTitle.setOnClickListener(null)
        }
        binding.txtvPodcastTitle.setOnLongClickListener { copyText(media.getFeedTitle()) }
        binding.episodeDate.text = StringUtils.stripToEmpty(pubDateStr)
        binding.txtvEpisodeTitle.text = item?.title
        binding.txtvEpisodeTitle.setOnLongClickListener { copyText(item?.title?:"") }
        binding.txtvEpisodeTitle.setOnClickListener {
            val lines = binding.txtvEpisodeTitle.lineCount
            val animUnit = 1500
            if (lines > binding.txtvEpisodeTitle.maxLines) {
                val titleHeight = (binding.txtvEpisodeTitle.height - binding.txtvEpisodeTitle.paddingTop - binding.txtvEpisodeTitle.paddingBottom)
                val verticalMarquee: ObjectAnimator = ObjectAnimator.ofInt(binding.txtvEpisodeTitle, "scrollY", 0,
                    (lines - binding.txtvEpisodeTitle.maxLines) * (titleHeight / binding.txtvEpisodeTitle.maxLines)).setDuration((lines * animUnit).toLong())
                val fadeOut: ObjectAnimator = ObjectAnimator.ofFloat(binding.txtvEpisodeTitle, "alpha", 0f)
                fadeOut.setStartDelay(animUnit.toLong())
                fadeOut.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        binding.txtvEpisodeTitle.scrollTo(0, 0)
                    }
                })
                val fadeBackIn: ObjectAnimator = ObjectAnimator.ofFloat(binding.txtvEpisodeTitle, "alpha", 1f)
                val set = AnimatorSet()
                set.playSequentially(verticalMarquee, fadeOut, fadeBackIn)
                set.start()
            }
        }

        displayedChapterIndex = -1
        refreshChapterData(ChapterUtils.getCurrentChapterIndex(media, media.getPosition())) //calls displayCoverImage
        updateChapterControlVisibility()
    }

    private fun updateChapterControlVisibility() {
        var chapterControlVisible = false
        when {
            media?.getChapters() != null -> chapterControlVisible = media!!.getChapters().isNotEmpty()
            media is FeedMedia -> {
                val fm: FeedMedia? = (media as FeedMedia?)
                // If an item has chapters but they are not loaded yet, still display the button.
                chapterControlVisible = fm?.item != null && fm.item!!.hasChapters()
            }
        }
        val newVisibility = if (chapterControlVisible) View.VISIBLE else View.GONE
        if (binding.chapterButton.visibility != newVisibility) {
            binding.chapterButton.visibility = newVisibility
            ObjectAnimator.ofFloat(binding.chapterButton, "alpha",
                (if (chapterControlVisible) 0 else 1).toFloat(), (if (chapterControlVisible) 1 else 0).toFloat()).start()
        }
    }

    private fun refreshChapterData(chapterIndex: Int) {
        if (media != null && chapterIndex > -1) {
            if (media!!.getPosition() > media!!.getDuration() || chapterIndex >= media!!.getChapters().size - 1) {
                displayedChapterIndex = media!!.getChapters().size - 1
                binding.butNextChapter.visibility = View.INVISIBLE
            } else {
                displayedChapterIndex = chapterIndex
                binding.butNextChapter.visibility = View.VISIBLE
            }
        }
        displayCoverImage()
    }

    private fun displayCoverImage() {
        if (media == null) return
        if (displayedChapterIndex == -1 || media!!.getChapters().isEmpty() || media!!.getChapters()[displayedChapterIndex].imageUrl.isNullOrEmpty()) {
            val imageLoader = binding.imgvCover.context.imageLoader
            val imageRequest = ImageRequest.Builder(requireContext())
                .data(media!!.getImageLocation())
                .setHeader("User-Agent", "Mozilla/5.0")
                .placeholder(R.color.light_gray)
                .listener(object : ImageRequest.Listener {
                    override fun onError(request: ImageRequest, throwable: ErrorResult) {
                        val fallbackImageRequest = ImageRequest.Builder(requireContext())
                            .data(ImageResourceUtils.getFallbackImageLocation(media!!))
                            .setHeader("User-Agent", "Mozilla/5.0")
                            .error(R.mipmap.ic_launcher)
                            .target(binding.imgvCover)
                            .build()
                        imageLoader.enqueue(fallbackImageRequest)
                    }
                })
                .target(binding.imgvCover)
                .build()
            imageLoader.enqueue(imageRequest)

        } else {
            val imgLoc = EmbeddedChapterImage.getModelFor(media!!, displayedChapterIndex)
            val imageLoader = binding.imgvCover.context.imageLoader
            val imageRequest = ImageRequest.Builder(requireContext())
                .data(imgLoc)
                .setHeader("User-Agent", "Mozilla/5.0")
                .placeholder(R.color.light_gray)
                .listener(object : ImageRequest.Listener {
                    override fun onError(request: ImageRequest, throwable: ErrorResult) {
                        val fallbackImageRequest = ImageRequest.Builder(requireContext())
                            .data(ImageResourceUtils.getFallbackImageLocation(media!!))
                            .setHeader("User-Agent", "Mozilla/5.0")
                            .error(R.mipmap.ic_launcher)
                            .target(binding.imgvCover)
                            .build()
                        imageLoader.enqueue(fallbackImageRequest)
                    }
                })
                .target(binding.imgvCover)
                .build()
            imageLoader.enqueue(imageRequest)
        }
    }

    @UnstableApi fun onPlayPause() {
        controller?.playPause()
    }

    private val currentChapter: Chapter?
        get() {
            if (media == null || media!!.getChapters().isEmpty() || displayedChapterIndex == -1) return null
            return media!!.getChapters()[displayedChapterIndex]
        }

    @UnstableApi private fun seekToPrevChapter() {
        val curr: Chapter? = currentChapter
        if (controller == null || curr == null || displayedChapterIndex == -1) return

        when {
            displayedChapterIndex < 1 -> controller!!.seekTo(0)
            (controller!!.position - 10000 * controller!!.currentPlaybackSpeedMultiplier) < curr.start -> {
                refreshChapterData(displayedChapterIndex - 1)
                if (media != null) controller!!.seekTo(media!!.getChapters()[displayedChapterIndex].start.toInt())
            }
            else -> controller!!.seekTo(curr.start.toInt())
        }
    }

    @UnstableApi private fun seekToNextChapter() {
        if (controller == null || media == null || media!!.getChapters().isEmpty() || displayedChapterIndex == -1
                || displayedChapterIndex + 1 >= media!!.getChapters().size) return

        refreshChapterData(displayedChapterIndex + 1)
        controller!!.seekTo(media!!.getChapters()[displayedChapterIndex].start.toInt())
    }


    @UnstableApi override fun onPause() {
        super.onPause()
        savePreference()
    }

    @UnstableApi private fun savePreference() {
        Logd(TAG, "Saving preferences")
//        val prefs = requireActivity().getSharedPreferences(PREF, Activity.MODE_PRIVATE)
        val editor = prefs!!.edit()
        if (controller?.getMedia() != null) {
            Logd(TAG, "Saving scroll position: " + binding.itemDescriptionFragment.scrollY)
            editor.putInt(PREF_SCROLL_Y, binding.itemDescriptionFragment.scrollY)
            editor.putString(PREF_PLAYABLE_ID, controller!!.getMedia()!!.getIdentifier().toString())
        } else {
            Logd(TAG, "savePreferences was called while media or webview was null")
            editor.putInt(PREF_SCROLL_Y, -1)
            editor.putString(PREF_PLAYABLE_ID, "")
        }
        editor.apply()
    }

    @UnstableApi private fun restoreFromPreference(): Boolean {
        if ((activity as MainActivity).bottomSheet.state != BottomSheetBehavior.STATE_EXPANDED) return false

        Logd(TAG, "Restoring from preferences")
        val activity: Activity? = activity
        if (activity != null) {
//            val prefs = activity.getSharedPreferences(PREF, Activity.MODE_PRIVATE)
            val id = prefs!!.getString(PREF_PLAYABLE_ID, "")
            val scrollY = prefs!!.getInt(PREF_SCROLL_Y, -1)
            if (scrollY != -1) {
                if (id == controller?.getMedia()?.getIdentifier()?.toString()) {
                    Logd(TAG, "Restored scroll Position: $scrollY")
                    binding.itemDescriptionFragment.scrollTo(binding.itemDescriptionFragment.scrollX, scrollY)
                    return true
                }
                Logd(TAG, "reset scroll Position: 0")
                binding.itemDescriptionFragment.scrollTo(0, 0)

                return true
            }
        }
        return false
    }

    fun scrollToTop() {
        binding.itemDescriptionFragment.scrollTo(0, 0)
        savePreference()
    }

    private fun procFlowEvents() {
        lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: $event")
                when (event) {
                    is FlowEvent.PlaybackPositionEvent -> onEventMainThread(event)
                    else -> {}
                }
            }
        }
    }

    fun onEventMainThread(event: FlowEvent.PlaybackPositionEvent) {
        val newChapterIndex: Int = ChapterUtils.getCurrentChapterIndex(media, event.position)
        if (newChapterIndex > -1 && newChapterIndex != displayedChapterIndex) {
            refreshChapterData(newChapterIndex)
        }
    }

    fun setItem(item_: FeedItem) {
        Logd(TAG, "setItem ${item_.title}")
        if (item?.itemIdentifier != item_.itemIdentifier) {
            item = item_
            showHomeText = false
            homeText = null
        }
    }

//    override fun onConfigurationChanged(newConfig: Configuration) {
//        super.onConfigurationChanged(newConfig)
//        configureForOrientation(newConfig)
//    }
//
//    private fun configureForOrientation(newConfig: Configuration) {
//        val isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
//
////        binding.coverFragment.orientation = if (isPortrait) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
//
//        if (isPortrait) {
//            binding.coverHolder.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
////            binding.coverFragmentTextContainer.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
//        } else {
//            binding.coverHolder.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
////            binding.coverFragmentTextContainer.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
//        }
//
//        (binding.episodeDetails.parent as ViewGroup).removeView(binding.episodeDetails)
//        if (isPortrait) {
//            binding.coverFragment.addView(binding.episodeDetails)
//        } else {
//            binding.coverFragmentTextContainer.addView(binding.episodeDetails)
//        }
//    }

    override fun onStop() {
        super.onStop()
//        webViewLoader?.dispose()
    }

    @UnstableApi private fun copyText(text: String): Boolean {
        val clipboardManager: ClipboardManager? = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
        clipboardManager?.setPrimaryClip(ClipData.newPlainText("Podcini", text))
        if (Build.VERSION.SDK_INT <= 32) {
            (requireActivity() as MainActivity).showSnackbarAbovePlayer(resources.getString(R.string.copied_to_clipboard), Snackbar.LENGTH_SHORT)
        }
        return true
    }

    companion object {
        private const val TAG = "PlayerDetailsFragment"

        private const val PREF = "ItemDescriptionFragmentPrefs"
        private const val PREF_SCROLL_Y = "prefScrollY"
        private const val PREF_PLAYABLE_ID = "prefPlayableId"

        var prefs: SharedPreferences? = null
        fun getSharedPrefs(context: Context) {
            if (prefs == null) prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        }
    }
}
