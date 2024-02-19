package ac.mdiq.podvinci.fragment

import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.activity.MainActivity
import ac.mdiq.podvinci.core.feed.util.ImageResourceUtils
import ac.mdiq.podvinci.core.util.ChapterUtils
import ac.mdiq.podvinci.core.util.DateFormatter
import ac.mdiq.podvinci.core.util.playback.PlaybackController
import ac.mdiq.podvinci.databinding.CoverFragmentBinding
import ac.mdiq.podvinci.event.playback.PlaybackPositionEvent
import ac.mdiq.podvinci.model.feed.Chapter
import ac.mdiq.podvinci.model.feed.EmbeddedChapterImage
import ac.mdiq.podvinci.model.feed.FeedMedia
import ac.mdiq.podvinci.model.playback.Playable
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.OptIn
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
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Displays the cover and the title of a FeedItem.
 */
class CoverFragment : Fragment() {
    private lateinit var viewBinding: CoverFragmentBinding
    private var controller: PlaybackController? = null
    private var disposable: Disposable? = null
    private var displayedChapterIndex = -1
    private var media: Playable? = null

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewBinding = CoverFragmentBinding.inflate(inflater)
        viewBinding.imgvCover.setOnClickListener { v: View? -> onPlayPause() }
        viewBinding.openDescription.setOnClickListener { view: View? ->
            (requireParentFragment() as AudioPlayerFragment)
                .scrollToPage(AudioPlayerFragment.POS_DESCRIPTION, true)
        }
        val colorFilter: ColorFilter? = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
            viewBinding.txtvPodcastTitle.currentTextColor, BlendModeCompat.SRC_IN)
        viewBinding.butNextChapter.colorFilter = colorFilter
        viewBinding.butPrevChapter.colorFilter = colorFilter
        viewBinding.descriptionIcon.colorFilter = colorFilter
        viewBinding.chapterButton.setOnClickListener { v: View? ->
            ChaptersFragment().show(
                childFragmentManager, ChaptersFragment.TAG)
        }
        viewBinding.butPrevChapter.setOnClickListener { v: View? -> seekToPrevChapter() }
        viewBinding.butNextChapter.setOnClickListener { v: View? -> seekToNextChapter() }

        controller = object : PlaybackController(activity) {
            override fun loadMediaInfo() {
                this@CoverFragment.loadMediaInfo(false)
            }
        }
        controller?.init()
        loadMediaInfo(false)
        EventBus.getDefault().register(this)

        return viewBinding.root
    }

    @OptIn(UnstableApi::class) override fun onDestroyView() {
        super.onDestroyView()
        controller?.release()
        controller = null
        EventBus.getDefault().unregister(this)
        Log.d(TAG, "Fragment destroyed")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        configureForOrientation(resources.configuration)
    }

    @UnstableApi private fun loadMediaInfo(includingChapters: Boolean) {
        disposable?.dispose()

        disposable = Maybe.create<Playable> { emitter: MaybeEmitter<Playable?> ->
            val media: Playable? = controller?.getMedia()
            if (media != null) {
                if (includingChapters) {
                    ChapterUtils.loadChapters(media, requireContext(), false)
                }
                emitter.onSuccess(media)
            } else {
                emitter.onComplete()
            }
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ media: Playable ->
                this.media = media
                displayMediaInfo(media)
                if (!includingChapters) {
                    loadMediaInfo(true)
                }
            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    @UnstableApi private fun displayMediaInfo(media: Playable) {
        val pubDateStr = DateFormatter.formatAbbrev(activity, media.getPubDate())
        viewBinding.txtvPodcastTitle.text = (StringUtils.stripToEmpty(media.getFeedTitle())
                + "\u00A0"
                + "・"
                + "\u00A0"
                + StringUtils.replace(StringUtils.stripToEmpty(pubDateStr), " ", "\u00A0"))
        if (media is FeedMedia) {
            if (media.getItem() != null) {
                val openFeed: Intent =
                    MainActivity.getIntentToOpenFeed(requireContext(), media.getItem()!!.feedId)
                viewBinding.txtvPodcastTitle.setOnClickListener { v: View? -> startActivity(openFeed) }
            }
        } else {
            viewBinding.txtvPodcastTitle.setOnClickListener(null)
        }
        viewBinding.txtvPodcastTitle.setOnLongClickListener { v: View? -> copyText(media.getFeedTitle()) }
        viewBinding.txtvEpisodeTitle.text = media.getEpisodeTitle()
        viewBinding.txtvEpisodeTitle.setOnLongClickListener { v: View? -> copyText(media.getEpisodeTitle()) }
        viewBinding.txtvEpisodeTitle.setOnClickListener { v: View? ->
            val lines = viewBinding.txtvEpisodeTitle.lineCount
            val animUnit = 1500
            if (lines > viewBinding.txtvEpisodeTitle.maxLines) {
                val titleHeight = (viewBinding.txtvEpisodeTitle.height
                        - viewBinding.txtvEpisodeTitle.paddingTop
                        - viewBinding.txtvEpisodeTitle.paddingBottom)
                val verticalMarquee: ObjectAnimator = ObjectAnimator.ofInt(
                    viewBinding.txtvEpisodeTitle, "scrollY", 0,
                    (lines - viewBinding.txtvEpisodeTitle.maxLines)
                            * (titleHeight / viewBinding.txtvEpisodeTitle.maxLines))
                    .setDuration((lines * animUnit).toLong())
                val fadeOut: ObjectAnimator = ObjectAnimator.ofFloat(
                    viewBinding.txtvEpisodeTitle, "alpha", 0f)
                fadeOut.setStartDelay(animUnit.toLong())
                fadeOut.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        viewBinding.txtvEpisodeTitle.scrollTo(0, 0)
                    }
                })
                val fadeBackIn: ObjectAnimator = ObjectAnimator.ofFloat(
                    viewBinding.txtvEpisodeTitle, "alpha", 1f)
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
        if (media?.getChapters() != null) {
            chapterControlVisible = media!!.getChapters().isNotEmpty()
        } else if (media is FeedMedia) {
            val fm: FeedMedia? = (media as FeedMedia?)
            // If an item has chapters but they are not loaded yet, still display the button.
            chapterControlVisible = fm?.getItem() != null && fm.getItem()!!.hasChapters()
        }
        val newVisibility = if (chapterControlVisible) View.VISIBLE else View.GONE
        if (viewBinding.chapterButton.visibility != newVisibility) {
            viewBinding.chapterButton.visibility = newVisibility
            ObjectAnimator.ofFloat(viewBinding.chapterButton,
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
                viewBinding.butNextChapter.visibility = View.INVISIBLE
            } else {
                displayedChapterIndex = chapterIndex
                viewBinding.butNextChapter.visibility = View.VISIBLE
            }
        }

        displayCoverImage()
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

        if (controller == null || curr == null || displayedChapterIndex == -1) {
            return
        }

        if (displayedChapterIndex < 1) {
            controller!!.seekTo(0)
        } else if ((controller!!.position - 10000 * controller!!.currentPlaybackSpeedMultiplier) < curr.start) {
            refreshChapterData(displayedChapterIndex - 1)
            if (media != null) controller!!.seekTo(media!!.getChapters()[displayedChapterIndex].start.toInt())
        } else {
            controller!!.seekTo(curr.start.toInt())
        }
    }

    @UnstableApi private fun seekToNextChapter() {
        if (controller == null || media == null || media!!.getChapters().isEmpty() || displayedChapterIndex == -1 || displayedChapterIndex + 1 >= media!!.getChapters().size) {
            return
        }

        refreshChapterData(displayedChapterIndex + 1)
        controller!!.seekTo(media!!.getChapters()[displayedChapterIndex].start.toInt())
    }

    @UnstableApi override fun onStart() {
        super.onStart()
//        controller = object : PlaybackController(activity) {
//            override fun loadMediaInfo() {
//                this@CoverFragment.loadMediaInfo(false)
//            }
//        }
//        controller?.init()
//        loadMediaInfo(false)
//        EventBus.getDefault().register(this)
    }

    @UnstableApi override fun onStop() {
        super.onStop()
        disposable?.dispose()
//        controller?.release()
//        controller = null
//        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        val newChapterIndex: Int = ChapterUtils.getCurrentChapterIndex(media, event.position)
        if (newChapterIndex > -1 && newChapterIndex != displayedChapterIndex) {
            refreshChapterData(newChapterIndex)
        }
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
            cover.into(viewBinding.imgvCover)
        } else {
            Glide.with(this)
                .load(EmbeddedChapterImage.getModelFor(media!!, displayedChapterIndex))
                .apply(options)
                .thumbnail(cover)
                .error(cover)
                .into(viewBinding.imgvCover)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configureForOrientation(newConfig)
    }

    private fun configureForOrientation(newConfig: Configuration) {
        val isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT

        viewBinding.coverFragment.orientation = if (isPortrait) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL

        if (isPortrait) {
            viewBinding.coverHolder.layoutParams =
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            viewBinding.coverFragmentTextContainer.layoutParams =
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
        } else {
            viewBinding.coverHolder.layoutParams =
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            viewBinding.coverFragmentTextContainer.layoutParams =
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }

        (viewBinding.episodeDetails.parent as ViewGroup).removeView(viewBinding.episodeDetails)
        if (isPortrait) {
            viewBinding.coverFragment.addView(viewBinding.episodeDetails)
        } else {
            viewBinding.coverFragmentTextContainer.addView(viewBinding.episodeDetails)
        }
    }

    @UnstableApi fun onPlayPause() {
        if (controller == null) {
            return
        }
        controller!!.playPause()
    }

    @UnstableApi private fun copyText(text: String): Boolean {
        val clipboardManager: ClipboardManager? = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
        clipboardManager?.setPrimaryClip(ClipData.newPlainText("PodVinci", text))
        if (Build.VERSION.SDK_INT <= 32) {
            (requireActivity() as MainActivity).showSnackbarAbovePlayer(
                resources.getString(R.string.copied_to_clipboard), Snackbar.LENGTH_SHORT)
        }
        return true
    }

    companion object {
        private const val TAG = "CoverFragment"
    }
}
