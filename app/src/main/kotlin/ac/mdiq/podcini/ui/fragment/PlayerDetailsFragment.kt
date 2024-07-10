package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.PlayerDetailsFragmentBinding
import ac.mdiq.podcini.net.utils.NetworkUtils.fetchHtmlSource
import ac.mdiq.podcini.playback.PlaybackController.Companion.curPosition
import ac.mdiq.podcini.playback.PlaybackController.Companion.curSpeedMultiplier
import ac.mdiq.podcini.playback.PlaybackController.Companion.seekTo
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.storage.database.Episodes.persistEpisode
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.utils.ChapterUtils
import ac.mdiq.podcini.storage.utils.ImageResourceUtils
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.fragment.SubscriptionsFragment.Companion
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.view.ShownotesWebView
import ac.mdiq.podcini.util.DateFormatter
import ac.mdiq.podcini.util.Logd
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import org.apache.commons.lang3.StringUtils

/**
 * Displays the description of a Playable object in a Webview.
 */
@UnstableApi
class PlayerDetailsFragment : Fragment() {
    private lateinit var shownoteView: ShownotesWebView
    private var shownotesCleaner: ShownotesCleaner? = null

    private var _binding: PlayerDetailsFragmentBinding? = null
    private val binding get() = _binding!!

    private var prevItem: Episode? = null
    private var playable: Playable? = null
    private var currentItem: Episode? = null
    private var displayedChapterIndex = -1

    private var cleanedNotes: String? = null

    private var isLoading = false
    private var homeText: String? = null
    internal var showHomeText = false
    internal var readerhtml: String? = null

    private val currentChapter: Chapter?
        get() {
            if (playable == null || playable!!.getChapters().isEmpty() || displayedChapterIndex == -1) return null
            return playable!!.getChapters()[displayedChapterIndex]
        }

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logd(TAG, "fragment onCreateView")
        _binding = PlayerDetailsFragmentBinding.inflate(inflater)

        val colorFilter: ColorFilter? = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(binding.txtvPodcastTitle.currentTextColor, BlendModeCompat.SRC_IN)
        binding.butNextChapter.colorFilter = colorFilter
        binding.butPrevChapter.colorFilter = colorFilter
        binding.chapterButton.setOnClickListener { ChaptersFragment().show(childFragmentManager, ChaptersFragment.TAG) }
        binding.butPrevChapter.setOnClickListener { seekToPrevChapter() }
        binding.butNextChapter.setOnClickListener { seekToNextChapter() }

        Logd(TAG, "fragment onCreateView")
        shownoteView = binding.webview
        shownoteView.setTimecodeSelectedListener { time: Int? -> seekTo(time!!) }
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
        shownotesCleaner = ShownotesCleaner(requireContext())
        return binding.root
    }

    override fun onStart() {
        Logd(TAG, "onStart()")
        super.onStart()
//        procFlowEvents()
    }

    override fun onStop() {
        Logd(TAG, "onStop()")
        super.onStop()
//        cancelFlowEvents()
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        _binding = null
        prevItem = null
        currentItem = null
        Logd(TAG, "Fragment destroyed")
        shownoteView.removeAllViews()
        shownoteView.destroy()
        super.onDestroyView()
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return shownoteView.onContextItemSelected(item)
    }

    internal fun updateInfo() {
//        if (isLoading) return
        lifecycleScope.launch {
            Logd(TAG, "in updateInfo")
            isLoading = true
            withContext(Dispatchers.IO) {
                if (currentItem == null) {
                    playable = curMedia
                    if (playable != null && playable is EpisodeMedia) {
                        val episodeMedia = playable as EpisodeMedia
                        currentItem = episodeMedia.episode
                        showHomeText = false
                        homeText = null
                    }
                }
                if (currentItem != null) {
                    playable = currentItem!!.media
                    if (prevItem?.identifier != currentItem!!.identifier) cleanedNotes = null
                    if (cleanedNotes == null) {
                        Logd(TAG, "calling load description ${currentItem!!.description==null} ${currentItem!!.title}")
                        cleanedNotes = shownotesCleaner?.processShownotes(currentItem?.description ?: "", playable?.getDuration()?:0)
                    }
                    prevItem = currentItem
                }
            }
            withContext(Dispatchers.Main) {
                Logd(TAG, "subscribe: ${playable?.getEpisodeTitle()}")
                displayMediaInfo(playable!!)
                shownoteView.loadDataWithBaseURL("https://127.0.0.1", cleanedNotes?:"No notes", "text/html", "utf-8", "about:blank")
                Logd(TAG, "Webview loaded")
            }
        }.invokeOnCompletion { throwable ->
            isLoading = false
            if (throwable != null) Log.e(TAG, Log.getStackTraceString(throwable))
        }
    }

    fun buildHomeReaderText() {
        showHomeText = !showHomeText
        runOnIOScope {
            if (showHomeText) {
                homeText = currentItem!!.transcript
                if (homeText == null && currentItem?.link != null) {
                    val url = currentItem!!.link!!
                    val htmlSource = fetchHtmlSource(url)
                    val readability4J = Readability4J(currentItem!!.link!!, htmlSource)
                    val article = readability4J.parse()
                    readerhtml = article.contentWithDocumentsCharsetOrUtf8
                    if (!readerhtml.isNullOrEmpty()) {
                        currentItem!!.setTranscriptIfLonger(readerhtml)
                        homeText = currentItem!!.transcript
                        persistEpisode(currentItem)
                    }
                }
                if (!homeText.isNullOrEmpty()) {
//                    val shownotesCleaner = ShownotesCleaner(requireContext())
                    cleanedNotes = shownotesCleaner?.processShownotes(homeText!!, 0)
                    withContext(Dispatchers.Main) {
                        shownoteView.loadDataWithBaseURL("https://127.0.0.1",
                            cleanedNotes ?: "No notes",
                            "text/html",
                            "UTF-8",
                            null)
                    }
                } else withContext(Dispatchers.Main) { Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show() }
            } else {
//                val shownotesCleaner = ShownotesCleaner(requireContext())
                cleanedNotes = shownotesCleaner?.processShownotes(currentItem?.description ?: "", playable?.getDuration() ?: 0)
                if (!cleanedNotes.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        shownoteView.loadDataWithBaseURL("https://127.0.0.1",
                            cleanedNotes ?: "No notes",
                            "text/html",
                            "UTF-8",
                            null)
                    }
                } else withContext(Dispatchers.Main) { Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show() }
            }
        }
    }

    @UnstableApi private fun displayMediaInfo(media: Playable) {
        Logd(TAG, "displayMediaInfo ${currentItem?.title} ${media.getEpisodeTitle()}")
        val pubDateStr = DateFormatter.formatAbbrev(context, media.getPubDate())
        binding.txtvPodcastTitle.text = StringUtils.stripToEmpty(media.getFeedTitle())
        if (media is EpisodeMedia) {
            if (currentItem?.feedId != null) {
                val openFeed: Intent = MainActivity.getIntentToOpenFeed(requireContext(), currentItem!!.feedId!!)
                binding.txtvPodcastTitle.setOnClickListener { startActivity(openFeed) }
            }
        } else {
            binding.txtvPodcastTitle.setOnClickListener(null)
        }
        binding.txtvPodcastTitle.setOnLongClickListener { copyText(media.getFeedTitle()) }
        binding.episodeDate.text = StringUtils.stripToEmpty(pubDateStr)
        binding.txtvEpisodeTitle.text = currentItem?.title
        binding.txtvEpisodeTitle.setOnLongClickListener { copyText(currentItem?.title?:"") }
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
            playable?.getChapters() != null -> chapterControlVisible = playable!!.getChapters().isNotEmpty()
            playable is EpisodeMedia -> {
                val fm: EpisodeMedia? = (playable as EpisodeMedia?)
                // If an item has chapters but they are not loaded yet, still display the button.
                chapterControlVisible = fm?.episode != null && fm.episode!!.chapters.isNotEmpty()
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
        Logd(TAG, "in refreshChapterData $chapterIndex")
        if (playable != null && chapterIndex > -1) {
            if (playable!!.getPosition() > playable!!.getDuration() || chapterIndex >= playable!!.getChapters().size - 1) {
                displayedChapterIndex = playable!!.getChapters().size - 1
                binding.butNextChapter.visibility = View.INVISIBLE
            } else {
                displayedChapterIndex = chapterIndex
                binding.butNextChapter.visibility = View.VISIBLE
            }
        }
        displayCoverImage()
    }

    private fun displayCoverImage() {
        if (playable == null) return
        if (displayedChapterIndex == -1 || playable!!.getChapters().isEmpty() || playable!!.getChapters()[displayedChapterIndex].imageUrl.isNullOrEmpty()) {
            val imageLoader = binding.imgvCover.context.imageLoader
            val imageRequest = ImageRequest.Builder(requireContext())
                .data(playable!!.getImageLocation())
                .setHeader("User-Agent", "Mozilla/5.0")
                .placeholder(R.color.light_gray)
                .listener(object : ImageRequest.Listener {
                    override fun onError(request: ImageRequest, result: ErrorResult) {
                        val fallbackImageRequest = ImageRequest.Builder(requireContext())
                            .data(ImageResourceUtils.getFallbackImageLocation(playable!!))
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
            val imgLoc = EmbeddedChapterImage.getModelFor(playable!!, displayedChapterIndex)
            val imageLoader = binding.imgvCover.context.imageLoader
            val imageRequest = ImageRequest.Builder(requireContext())
                .data(imgLoc)
                .setHeader("User-Agent", "Mozilla/5.0")
                .placeholder(R.color.light_gray)
                .listener(object : ImageRequest.Listener {
                    override fun onError(request: ImageRequest, result: ErrorResult) {
                        val fallbackImageRequest = ImageRequest.Builder(requireContext())
                            .data(ImageResourceUtils.getFallbackImageLocation(playable!!))
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

    @UnstableApi private fun seekToPrevChapter() {
        val curr: Chapter? = currentChapter
        if (curr == null || displayedChapterIndex == -1) return

        when {
            displayedChapterIndex < 1 -> seekTo(0)
            (curPosition - 10000 * curSpeedMultiplier) < curr.start -> {
                refreshChapterData(displayedChapterIndex - 1)
                if (playable != null) seekTo(playable!!.getChapters()[displayedChapterIndex].start.toInt())
            }
            else -> seekTo(curr.start.toInt())
        }
    }

    @UnstableApi private fun seekToNextChapter() {
        if (playable == null || playable!!.getChapters().isEmpty() || displayedChapterIndex == -1 || displayedChapterIndex + 1 >= playable!!.getChapters().size) return

        refreshChapterData(displayedChapterIndex + 1)
        seekTo(playable!!.getChapters()[displayedChapterIndex].start.toInt())
    }


    @UnstableApi override fun onPause() {
        super.onPause()
        savePreference()
    }

    @UnstableApi private fun savePreference() {
        Logd(TAG, "Saving preferences")
        val editor = prefs!!.edit()
        if (curMedia != null) {
            Logd(TAG, "Saving scroll position: " + binding.itemDescriptionFragment.scrollY)
            editor.putInt(PREF_SCROLL_Y, binding.itemDescriptionFragment.scrollY)
            editor.putString(PREF_PLAYABLE_ID, curMedia!!.getIdentifier().toString())
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
            val id = prefs!!.getString(PREF_PLAYABLE_ID, "")
            val scrollY = prefs!!.getInt(PREF_SCROLL_Y, -1)
            if (scrollY != -1) {
                if (id == curMedia?.getIdentifier()?.toString()) {
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

//    private var eventSink: Job?     = null
//    private fun cancelFlowEvents() {
//        eventSink?.cancel()
//        eventSink = null
//    }
//    private fun procFlowEvents() {
//        if (eventSink != null) return
//        eventSink = lifecycleScope.launch {
//            EventFlow.events.collectLatest { event ->
//                Logd(TAG, "Received event: ${event.TAG}")
//                when (event) {
//                    is FlowEvent.PlaybackPositionEvent -> onPlaybackPositionEvent(event)
//                    else -> {}
//                }
//            }
//        }
//    }

    fun onPlaybackPositionEvent(event: FlowEvent.PlaybackPositionEvent) {
        if (playable?.getIdentifier() != event.media?.getIdentifier()) return
        val newChapterIndex: Int = ChapterUtils.getCurrentChapterIndex(playable, event.position)
        if (newChapterIndex >= 0 && newChapterIndex != displayedChapterIndex) {
            refreshChapterData(newChapterIndex)
        }
    }

    fun setItem(item_: Episode) {
        Logd(TAG, "setItem ${item_.title}")
        if (currentItem?.identifier != item_.identifier) {
            currentItem = item_
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

    @UnstableApi private fun copyText(text: String): Boolean {
        val clipboardManager: ClipboardManager? = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
        clipboardManager?.setPrimaryClip(ClipData.newPlainText("Podcini", text))
        if (Build.VERSION.SDK_INT <= 32) {
            (requireActivity() as MainActivity).showSnackbarAbovePlayer(resources.getString(R.string.copied_to_clipboard), Snackbar.LENGTH_SHORT)
        }
        return true
    }

    companion object {
        private val TAG: String = PlayerDetailsFragment::class.simpleName ?: "Anonymous"

        private const val PREF = "ItemDescriptionFragmentPrefs"
        private const val PREF_SCROLL_Y = "prefScrollY"
        private const val PREF_PLAYABLE_ID = "prefPlayableId"

        var prefs: SharedPreferences? = null
        fun getSharedPrefs(context: Context) {
            if (prefs == null) prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        }
    }
}
