package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.PlayerDetailsFragmentBinding
import ac.mdiq.podcini.feed.util.ImageResourceUtils
import ac.mdiq.podcini.playback.PlaybackController
import ac.mdiq.podcini.util.event.playback.PlaybackPositionEvent
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.model.feed.Chapter
import ac.mdiq.podcini.storage.model.feed.EmbeddedChapterImage
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.view.ShownotesWebView
import ac.mdiq.podcini.util.ChapterUtils
import ac.mdiq.podcini.util.DateFormatter
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.snackbar.Snackbar
import io.reactivex.Maybe
import io.reactivex.MaybeEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.apache.commons.lang3.StringUtils
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Displays the description of a Playable object in a Webview.
 */
@UnstableApi
class PlayerDetailsFragment : Fragment() {
    private lateinit var webvDescription: ShownotesWebView

    private var _binding: PlayerDetailsFragmentBinding? = null
    private val binding get() = _binding!!

    private var media: Playable? = null
    private var item: FeedItem? = null
    private var loadedMediaId: Any? = null
    private var displayedChapterIndex = -1

    private var cleanedNotes: String? = null
    private var disposable: Disposable? = null
    private var webViewLoader: Disposable? = null
    private var controller: PlaybackController? = null

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Log.d(TAG, "fragment onCreateView")
        _binding = PlayerDetailsFragmentBinding.inflate(inflater)

        binding.imgvCover.setOnClickListener { onPlayPause() }

        val colorFilter: ColorFilter? = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
            binding.txtvPodcastTitle.currentTextColor, BlendModeCompat.SRC_IN)
        binding.butNextChapter.colorFilter = colorFilter
        binding.butPrevChapter.colorFilter = colorFilter
        binding.chapterButton.setOnClickListener {
            ChaptersFragment().show(childFragmentManager, ChaptersFragment.TAG)
        }
        binding.butPrevChapter.setOnClickListener { seekToPrevChapter() }
        binding.butNextChapter.setOnClickListener { seekToNextChapter() }

        Log.d(TAG, "fragment onCreateView")
        webvDescription = binding.webview
        webvDescription.setTimecodeSelectedListener { time: Int? ->
            controller?.seekTo(time!!)
        }
        webvDescription.setPageFinishedListener {
            // Restoring the scroll position might not always work
            webvDescription.postDelayed({ this@PlayerDetailsFragment.restoreFromPreference() }, 50)
        }

        binding.root.addOnLayoutChangeListener(object : OnLayoutChangeListener {
            override fun onLayoutChange(v: View, left: Int, top: Int, right: Int,
                                        bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
            ) {
                if (binding.root.measuredHeight != webvDescription.minimumHeight) {
                    webvDescription.setMinimumHeight(binding.root.measuredHeight)
                }
                binding.root.removeOnLayoutChangeListener(this)
            }
        })
        registerForContextMenu(webvDescription)
        controller = object : PlaybackController(requireActivity()) {
            override fun loadMediaInfo() {
                load()
            }
        }
        controller?.init()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        controller?.release()
        controller = null
        Log.d(TAG, "Fragment destroyed")
        webvDescription.removeAllViews()
        webvDescription.destroy()
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return webvDescription.onContextItemSelected(item)
    }

    @UnstableApi private fun load() {
        Log.d(TAG, "load() called")
        webViewLoader?.dispose()

        val context = context ?: return
        webViewLoader = Maybe.create { emitter: MaybeEmitter<String?> ->
            media = controller?.getMedia()
            if (media == null) {
                emitter.onComplete()
                return@create
            }
            if (media is FeedMedia) {
                val feedMedia = media as FeedMedia
                item = feedMedia.item
            }
//            Log.d(TAG, "webViewLoader ${item?.id} ${cleanedNotes==null} ${item!!.description==null} ${loadedMediaId == null} ${item?.media?.getIdentifier()} ${media?.getIdentifier()}")
            if (item != null) {
                if (cleanedNotes == null || item!!.description == null || loadedMediaId != media?.getIdentifier()) {
                    Log.d(TAG, "calling load description ${cleanedNotes==null} ${item!!.description==null} ${item!!.media?.getIdentifier()} ${media?.getIdentifier()}")
//                    printStackTrce()
                    DBReader.loadDescriptionOfFeedItem(item!!)
                    loadedMediaId = media?.getIdentifier()
                    val shownotesCleaner = ShownotesCleaner(context, item?.description ?: "", media?.getDuration()?:0)
                    cleanedNotes = shownotesCleaner.processShownotes()
                }
            }
            emitter.onSuccess(cleanedNotes?:"")
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ data: String? ->
                webvDescription.loadDataWithBaseURL("https://127.0.0.1", data!!, "text/html",
                    "utf-8", "about:blank")
                Log.d(TAG, "Webview loaded")
            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
        loadMediaInfo()
    }

    @UnstableApi private fun loadMediaInfo() {
        disposable?.dispose()

        disposable = Maybe.create<Playable> { emitter: MaybeEmitter<Playable?> ->
            media = controller?.getMedia()
            if (media != null) {
                emitter.onSuccess(media!!)
            } else {
                emitter.onComplete()
            }
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ media: Playable ->
                this.media = media
                displayMediaInfo(media)
            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    @UnstableApi private fun displayMediaInfo(media: Playable) {
        val pubDateStr = DateFormatter.formatAbbrev(context, media.getPubDate())
        binding.txtvPodcastTitle.text = StringUtils.stripToEmpty(media.getFeedTitle())
        if (item == null || item!!.media?.getIdentifier() != media.getIdentifier()) {
            if (media is FeedMedia) {
                item = media.item
                if (item != null) {
                    val openFeed: Intent = MainActivity.getIntentToOpenFeed(requireContext(), item!!.feedId)
                    binding.txtvPodcastTitle.setOnClickListener { startActivity(openFeed) }
                }
            } else {
                binding.txtvPodcastTitle.setOnClickListener(null)
            }
        }
        binding.txtvPodcastTitle.setOnLongClickListener { copyText(media.getFeedTitle()) }
        binding.episodeDate.text = StringUtils.stripToEmpty(pubDateStr)
        binding.txtvEpisodeTitle.text = media.getEpisodeTitle()
        binding.txtvEpisodeTitle.setOnLongClickListener { copyText(media.getEpisodeTitle()) }
        binding.txtvEpisodeTitle.setOnClickListener {
            val lines = binding.txtvEpisodeTitle.lineCount
            val animUnit = 1500
            if (lines > binding.txtvEpisodeTitle.maxLines) {
                val titleHeight = (binding.txtvEpisodeTitle.height
                        - binding.txtvEpisodeTitle.paddingTop
                        - binding.txtvEpisodeTitle.paddingBottom)
                val verticalMarquee: ObjectAnimator = ObjectAnimator.ofInt(
                    binding.txtvEpisodeTitle, "scrollY", 0,
                    (lines - binding.txtvEpisodeTitle.maxLines) * (titleHeight / binding.txtvEpisodeTitle.maxLines))
                    .setDuration((lines * animUnit).toLong())
                val fadeOut: ObjectAnimator = ObjectAnimator.ofFloat(binding.txtvEpisodeTitle, "alpha", 0f)
                fadeOut.setStartDelay(animUnit.toLong())
                fadeOut.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        binding.txtvEpisodeTitle.scrollTo(0, 0)
                    }
                })
                val fadeBackIn: ObjectAnimator = ObjectAnimator.ofFloat(
                    binding.txtvEpisodeTitle, "alpha", 1f)
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
            media?.getChapters() != null -> {
                chapterControlVisible = media!!.getChapters().isNotEmpty()
            }
            media is FeedMedia -> {
                val fm: FeedMedia? = (media as FeedMedia?)
                // If an item has chapters but they are not loaded yet, still display the button.
                chapterControlVisible = fm?.item != null && fm.item!!.hasChapters()
            }
        }
        val newVisibility = if (chapterControlVisible) View.VISIBLE else View.GONE
        if (binding.chapterButton.visibility != newVisibility) {
            binding.chapterButton.visibility = newVisibility
            ObjectAnimator.ofFloat(binding.chapterButton,
                "alpha",
                (if (chapterControlVisible) 0 else 1).toFloat(),
                (if (chapterControlVisible) 1 else 0).toFloat())
                .start()
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
        val options: RequestOptions = RequestOptions()
            .dontAnimate()
            .transform(FitCenter(),
                RoundedCorners((16 * resources.displayMetrics.density).toInt()))

        val cover: RequestBuilder<Drawable> = Glide.with(this)
            .load(media!!.getImageLocation())
            .error(Glide.with(this)
                .load(ImageResourceUtils.getFallbackImageLocation(media!!))
                .apply(options))
            .apply(options)

        if (displayedChapterIndex == -1 || media!!.getChapters().isEmpty() || media!!.getChapters()[displayedChapterIndex].imageUrl.isNullOrEmpty()) {
            cover.into(binding.imgvCover)
        } else {
            val imgLoc = EmbeddedChapterImage.getModelFor(media!!, displayedChapterIndex)
            if (imgLoc != null) Glide.with(this)
                .load(imgLoc)
                .apply(options)
                .thumbnail(cover)
                .error(cover)
                .into(binding.imgvCover)
        }
    }

    @UnstableApi fun onPlayPause() {
        controller?.playPause()
    }

    private val currentChapter: Chapter?
        get() {
            if (media == null || media!!.getChapters().isEmpty() || displayedChapterIndex == -1) {
                return null
            }
            return media!!.getChapters()[displayedChapterIndex]
        }

    @UnstableApi private fun seekToPrevChapter() {
        val curr: Chapter? = currentChapter

        if (controller == null || curr == null || displayedChapterIndex == -1) return

        when {
            displayedChapterIndex < 1 -> {
                controller!!.seekTo(0)
            }
            (controller!!.position - 10000 * controller!!.currentPlaybackSpeedMultiplier) < curr.start -> {
                refreshChapterData(displayedChapterIndex - 1)
                if (media != null) controller!!.seekTo(media!!.getChapters()[displayedChapterIndex].start.toInt())
            }
            else -> {
                controller!!.seekTo(curr.start.toInt())
            }
        }
    }

    @UnstableApi private fun seekToNextChapter() {
        if (controller == null || media == null || media!!.getChapters().isEmpty() || displayedChapterIndex == -1 || displayedChapterIndex + 1 >= media!!.getChapters().size) {
            return
        }

        refreshChapterData(displayedChapterIndex + 1)
        controller!!.seekTo(media!!.getChapters()[displayedChapterIndex].start.toInt())
    }


    @UnstableApi override fun onPause() {
        super.onPause()
        savePreference()
    }

    @UnstableApi private fun savePreference() {
        Log.d(TAG, "Saving preferences")
        val prefs = requireActivity().getSharedPreferences(PREF, Activity.MODE_PRIVATE)
        val editor = prefs.edit()
        if (controller?.getMedia() != null) {
            Log.d(TAG, "Saving scroll position: " + webvDescription.scrollY)
            editor.putInt(PREF_SCROLL_Y, webvDescription.scrollY)
            editor.putString(PREF_PLAYABLE_ID, controller!!.getMedia()!!.getIdentifier().toString())
        } else {
            Log.d(TAG, "savePreferences was called while media or webview was null")
            editor.putInt(PREF_SCROLL_Y, -1)
            editor.putString(PREF_PLAYABLE_ID, "")
        }
        editor.apply()
    }

    @UnstableApi private fun restoreFromPreference(): Boolean {
        Log.d(TAG, "Restoring from preferences")
        val activity: Activity? = activity
        if (activity != null) {
            val prefs = activity.getSharedPreferences(PREF, Activity.MODE_PRIVATE)
            val id = prefs.getString(PREF_PLAYABLE_ID, "")
            val scrollY = prefs.getInt(PREF_SCROLL_Y, -1)
            if (scrollY != -1) {
                if (id == controller?.getMedia()?.getIdentifier()?.toString()) {
                    Log.d(TAG, "Restored scroll Position: $scrollY")
                    webvDescription.scrollTo(webvDescription.scrollX, scrollY)
                    return true
                }
                Log.d(TAG, "reset scroll Position: 0")
                webvDescription.scrollTo(webvDescription.scrollX, 0)
                return true
            }
        }
        return false
    }

    fun scrollToTop() {
        webvDescription.scrollTo(0, 0)
        savePreference()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        val newChapterIndex: Int = ChapterUtils.getCurrentChapterIndex(media, event.position)
        if (newChapterIndex > -1 && newChapterIndex != displayedChapterIndex) {
            refreshChapterData(newChapterIndex)
        }
    }

    fun setItem(item_: FeedItem) {
        Log.d(TAG, "setItem ${item_.title}")
        item = item_
    }

//    override fun onConfigurationChanged(newConfig: Configuration) {
//        super.onConfigurationChanged(newConfig)
//        configureForOrientation(newConfig)
//    }
//
//    private fun configureForOrientation(newConfig: Configuration) {
//        val isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
//
//        binding.coverFragment.orientation = if (isPortrait) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
//
//        if (isPortrait) {
//            binding.coverHolder.layoutParams =
//                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
//            binding.coverFragmentTextContainer.layoutParams =
//                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
//        } else {
//            binding.coverHolder.layoutParams =
//                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
//            binding.coverFragmentTextContainer.layoutParams =
//                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
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
        webViewLoader?.dispose()
    }

    @UnstableApi private fun copyText(text: String): Boolean {
        val clipboardManager: ClipboardManager? = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
        clipboardManager?.setPrimaryClip(ClipData.newPlainText("Podcini", text))
        if (Build.VERSION.SDK_INT <= 32) {
            (requireActivity() as MainActivity).showSnackbarAbovePlayer(
                resources.getString(R.string.copied_to_clipboard), Snackbar.LENGTH_SHORT)
        }
        return true
    }

    companion object {
        private const val TAG = "ItemDescriptionFragment"

        private const val PREF = "ItemDescriptionFragmentPrefs"
        private const val PREF_SCROLL_Y = "prefScrollY"
        private const val PREF_PLAYABLE_ID = "prefPlayableId"
    }
}
