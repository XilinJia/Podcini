package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.ui.activity.MainActivity
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.elevation.SurfaceColors
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.AudioplayerFragmentBinding
import ac.mdiq.podcini.feed.util.PlaybackSpeedUtils
import ac.mdiq.podcini.receiver.MediaButtonReceiver
import ac.mdiq.podcini.util.ChapterUtils
import ac.mdiq.podcini.util.TimeSpeedConverter
import ac.mdiq.podcini.playback.PlaybackController
import ac.mdiq.podcini.ui.dialog.MediaPlayerErrorDialog
import ac.mdiq.podcini.ui.dialog.SkipPreferenceDialog
import ac.mdiq.podcini.ui.dialog.SleepTimerDialog
import ac.mdiq.podcini.ui.dialog.VariableSpeedDialog
import ac.mdiq.podcini.util.event.FavoritesEvent
import ac.mdiq.podcini.ui.menuhandler.FeedItemMenuHandler
import ac.mdiq.podcini.storage.model.feed.Chapter
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.playback.cast.CastEnabledActivity
import ac.mdiq.podcini.playback.event.*
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.ui.common.PlaybackSpeedIndicatorView
import ac.mdiq.podcini.ui.view.ChapterSeekBar
import ac.mdiq.podcini.ui.view.PlayButton
import ac.mdiq.podcini.util.Converter
import ac.mdiq.podcini.util.event.PlayerErrorEvent
import ac.mdiq.podcini.util.event.UnreadItemsUpdateEvent
import io.reactivex.Maybe
import io.reactivex.MaybeEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.text.DecimalFormat
import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.min

/**
 * Shows the audio player.
 */
@UnstableApi
class AudioPlayerFragment : Fragment(), SeekBar.OnSeekBarChangeListener, Toolbar.OnMenuItemClickListener {
    var _binding: AudioplayerFragmentBinding? = null
    private val binding get() = _binding!!

    lateinit var butPlaybackSpeed: PlaybackSpeedIndicatorView
    lateinit var txtvPlaybackSpeed: TextView

    private lateinit var episodeTitle: TextView
    private lateinit var pager: ViewPager2
    private lateinit var txtvPosition: TextView
    private lateinit var txtvLength: TextView
    private lateinit var sbPosition: ChapterSeekBar
    private lateinit var butRev: ImageButton
    private lateinit var txtvRev: TextView
    private lateinit  var butPlay: PlayButton
    private lateinit  var butFF: ImageButton
    private lateinit  var txtvFF: TextView
    private lateinit  var butSkip: ImageButton
    private lateinit var toolbar: MaterialToolbar
    private lateinit var playerFragment: View
    
    private lateinit var progressIndicator: ProgressBar
    private lateinit  var cardViewSeek: CardView
    private lateinit  var txtvSeek: TextView

    private var controller: PlaybackController? = null
    private var disposable: Disposable? = null
    private var showTimeLeft = false
    private var seekedToChapterStart = false
    private var currentChapterIndex = -1
    private var duration = 0

    @SuppressLint("WrongConstant")
    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = AudioplayerFragmentBinding.inflate(inflater)

        binding.root.setOnTouchListener { _: View?, _: MotionEvent? -> true } // Avoid clicks going through player to fragments below

        Log.d(TAG, "fragment onCreateView")
        toolbar = binding.toolbar
        toolbar.title = ""
        toolbar.setNavigationOnClickListener {
            (activity as MainActivity).bottomSheet.setState(BottomSheetBehavior.STATE_EXPANDED)
        }
        toolbar.setOnMenuItemClickListener(this)

        val externalPlayerFragment = ExternalPlayerFragment()
        childFragmentManager.beginTransaction()
            .replace(R.id.playerFragment, externalPlayerFragment, ExternalPlayerFragment.TAG)
            .commit()
//        playerFragment = binding.playerFragment
        playerFragment = binding.root.findViewById(R.id.playerFragment)
        playerFragment.setBackgroundColor(
            SurfaceColors.getColorForElevation(requireContext(), 8 * resources.displayMetrics.density))

        episodeTitle = binding.titleView
        butPlaybackSpeed = binding.butPlaybackSpeed
        txtvPlaybackSpeed = binding.txtvPlaybackSpeed
        sbPosition = binding.sbPosition
        txtvPosition = binding.txtvPosition
        txtvLength = binding.txtvLength
        butRev = binding.butRev
        txtvRev = binding.txtvRev
        butPlay = binding.butPlay
        butFF = binding.butFF
        txtvFF = binding.txtvFF
        butSkip = binding.butSkip
        progressIndicator = binding.progLoading
        cardViewSeek = binding.cardViewSeek
        txtvSeek = binding.txtvSeek

        setupLengthTextView()
        setupControlButtons()
        butPlaybackSpeed.setOnClickListener {
            VariableSpeedDialog().show(childFragmentManager, null)
        }
        sbPosition.setOnSeekBarChangeListener(this)

        pager = binding.pager
        pager.adapter = AudioPlayerPagerAdapter(this@AudioPlayerFragment)
        // Required for getChildAt(int) in ViewPagerBottomSheetBehavior to return the correct page
        pager.offscreenPageLimit = NUM_CONTENT_FRAGMENTS
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pager.post {
                    if (activity != null) {
                        // By the time this is posted, the activity might be closed again.
                        (activity as MainActivity).bottomSheet.updateScrollingChild()
                    }
                }
            }
        })

        controller = newPlaybackController()
        controller?.init()
        loadMediaInfo(false)
        EventBus.getDefault().register(this)

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        controller?.release()
        controller = null
        EventBus.getDefault().unregister(this)
        Log.d(TAG, "Fragment destroyed")
    }

    private fun setChapterDividers(media: Playable?) {
        if (media == null) return

        var dividerPos: FloatArray? = null

        if (media.getChapters().isNotEmpty()) {
            val chapters: List<Chapter> = media.getChapters()
            dividerPos = FloatArray(chapters.size)

            for (i in chapters.indices) {
                dividerPos[i] = chapters[i].start / duration.toFloat()
            }
        }

        sbPosition.setDividerPos(dividerPos)
    }

    private fun setupControlButtons() {
        butRev.setOnClickListener {
            if (controller != null) {
                val curr: Int = controller!!.position
                controller!!.seekTo(curr - UserPreferences.rewindSecs * 1000)
            }
        }
        butRev.setOnLongClickListener {
            SkipPreferenceDialog.showSkipPreference(requireContext(),
                SkipPreferenceDialog.SkipDirection.SKIP_REWIND, txtvRev)
            true
        }
        butPlay.setOnClickListener {
            controller?.init()
            controller?.playPause()
        }
        butFF.setOnClickListener {
            if (controller != null) {
                val curr: Int = controller!!.position
                controller!!.seekTo(curr + UserPreferences.fastForwardSecs * 1000)
            }
        }
        butFF.setOnLongClickListener {
            SkipPreferenceDialog.showSkipPreference(requireContext(),
                SkipPreferenceDialog.SkipDirection.SKIP_FORWARD, txtvFF)
            false
        }
        butSkip.setOnClickListener {
            activity?.sendBroadcast(
                MediaButtonReceiver.createIntent(requireContext(), KeyEvent.KEYCODE_MEDIA_NEXT))
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onUnreadItemsUpdate(event: UnreadItemsUpdateEvent?) {
        if (controller == null) return
        updatePosition(PlaybackPositionEvent(controller!!.position, controller!!.duration))
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaybackServiceChanged(event: PlaybackServiceEvent) {
        if (event.action == PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN) {
            (activity as MainActivity).bottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun setupLengthTextView() {
        showTimeLeft = UserPreferences.shouldShowRemainingTime()
        txtvLength.setOnClickListener(View.OnClickListener {
            if (controller == null) return@OnClickListener

            showTimeLeft = !showTimeLeft
            UserPreferences.setShowRemainTimeSetting(showTimeLeft)
            updatePosition(PlaybackPositionEvent(controller!!.position, controller!!.duration))
        })
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun updatePlaybackSpeedButton(event: SpeedChangedEvent) {
        val speedStr: String = DecimalFormat("0.00").format(event.newSpeed.toDouble())
        txtvPlaybackSpeed.text = speedStr
        butPlaybackSpeed.setSpeed(event.newSpeed)
    }

    private fun loadMediaInfo(includingChapters: Boolean) {
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
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ media: Playable ->
                updateUi(media)
                if (!includingChapters) {
                    loadMediaInfo(true)
                }
            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) },
                { updateUi(null) })
    }

    private fun newPlaybackController(): PlaybackController {
        return object : PlaybackController(requireActivity()) {
            override fun updatePlayButtonShowsPlay(showPlay: Boolean) {
                butPlay.setIsShowPlay(showPlay)
            }

            override fun loadMediaInfo() {
                this@AudioPlayerFragment.loadMediaInfo(false)
            }

            override fun onPlaybackEnd() {
                (activity as MainActivity).bottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun updateUi(media: Playable?) {
        if (controller != null) duration = controller!!.duration
        if (media == null) return

        episodeTitle.text = media.getEpisodeTitle()
        updatePosition(PlaybackPositionEvent(media.getPosition(), media.getDuration()))
        updatePlaybackSpeedButton(SpeedChangedEvent(PlaybackSpeedUtils.getCurrentPlaybackSpeed(media)))
        setChapterDividers(media)
        setupOptionsMenu(media)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun sleepTimerUpdate(event: SleepTimerUpdatedEvent) {
        if (event.isCancelled || event.wasJustEnabled()) {
            this@AudioPlayerFragment.loadMediaInfo(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onStart() {
        super.onStart()
        txtvRev.text = NumberFormat.getInstance().format(UserPreferences.rewindSecs.toLong())
        txtvFF.text = NumberFormat.getInstance().format(UserPreferences.fastForwardSecs.toLong())
    }

    override fun onStop() {
        super.onStop()
        progressIndicator.visibility = View.GONE // Controller released; we will not receive buffering updates
        disposable?.dispose()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun bufferUpdate(event: BufferUpdateEvent) {
        when {
            event.hasStarted() -> {
                progressIndicator.visibility = View.VISIBLE
            }
            event.hasEnded() -> {
                progressIndicator.visibility = View.GONE
            }
            controller != null && controller!!.isStreaming -> {
                sbPosition.setSecondaryProgress((event.progress * sbPosition.max).toInt())
            }
            else -> {
                sbPosition.setSecondaryProgress(0)
            }
        }
    }

    @UnstableApi
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun updatePosition(event: PlaybackPositionEvent) {
        if (controller == null) return

        val converter = TimeSpeedConverter(controller!!.currentPlaybackSpeedMultiplier)
        val currentPosition: Int = converter.convert(event.position)
        val duration: Int = converter.convert(event.duration)
        val remainingTime: Int = converter.convert(max((event.duration - event.position).toDouble(), 0.0).toInt())
        currentChapterIndex = ChapterUtils.getCurrentChapterIndex(controller!!.getMedia(), currentPosition)
        //        Log.d(TAG, "currentPosition " + Converter.getDurationStringLong(currentPosition));
        if (currentPosition == Playable.INVALID_TIME || duration == Playable.INVALID_TIME) {
            Log.w(TAG, "Could not react to position observer update because of invalid time $currentPosition $duration")
            return
        }
        txtvPosition.text = Converter.getDurationStringLong(currentPosition)
        txtvPosition.setContentDescription(getString(R.string.position,
            Converter.getDurationStringLocalized(requireContext(), currentPosition.toLong())))
        showTimeLeft = UserPreferences.shouldShowRemainingTime()
        if (showTimeLeft) {
            txtvLength.setContentDescription(getString(R.string.remaining_time,
                Converter.getDurationStringLocalized(requireContext(), remainingTime.toLong())))
            txtvLength.text = (if (remainingTime > 0) "-" else "") + Converter.getDurationStringLong(remainingTime)
        } else {
            txtvLength.setContentDescription(getString(R.string.chapter_duration,
                Converter.getDurationStringLocalized(requireContext(), duration.toLong())))
            txtvLength.text = Converter.getDurationStringLong(duration)
        }

        if (!sbPosition.isPressed && event.duration > 0) {
            val progress: Float = (event.position.toFloat()) / event.duration
            sbPosition.progress = (progress * sbPosition.max).toInt()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun favoritesChanged(event: FavoritesEvent?) {
        this@AudioPlayerFragment.loadMediaInfo(false)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun mediaPlayerError(event: PlayerErrorEvent) {
        MediaPlayerErrorDialog.show(activity as Activity, event)
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (controller == null) return

        if (fromUser) {
            val prog: Float = progress / (seekBar.max.toFloat())
            val converter = TimeSpeedConverter(controller!!.currentPlaybackSpeedMultiplier)
            var position: Int = converter.convert((prog * controller!!.duration).toInt())
            val newChapterIndex: Int = ChapterUtils.getCurrentChapterIndex(controller!!.getMedia(), position)
            if (newChapterIndex > -1) {
                if (!sbPosition.isPressed && currentChapterIndex != newChapterIndex) {
                    currentChapterIndex = newChapterIndex
                    val media = controller!!.getMedia()
                    position = media?.getChapters()?.get(currentChapterIndex)?.start?.toInt() ?: 0
                    seekedToChapterStart = true
                    controller!!.seekTo(position)
                    updateUi(controller!!.getMedia())
                    sbPosition.highlightCurrentChapter()
                }
                txtvSeek.text = controller!!.getMedia()?.getChapters()?.get(newChapterIndex)?.title ?: (""
                        + "\n" + Converter.getDurationStringLong(position))
            } else {
                txtvSeek.text = Converter.getDurationStringLong(position)
            }
        } else if (duration != controller!!.duration) {
            updateUi(controller!!.getMedia())
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        // interrupt position Observer, restart later
        cardViewSeek.scaleX = .8f
        cardViewSeek.scaleY = .8f
        cardViewSeek.animate()
            ?.setInterpolator(FastOutSlowInInterpolator())
            ?.alpha(1f)?.scaleX(1f)?.scaleY(1f)
            ?.setDuration(200)
            ?.start()
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        if (controller != null) {
            if (seekedToChapterStart) {
                seekedToChapterStart = false
            } else {
                val prog: Float = seekBar.progress / (seekBar.max.toFloat())
                controller!!.seekTo((prog * controller!!.duration).toInt())
            }
        }
        cardViewSeek.scaleX = 1f
        cardViewSeek.scaleY = 1f
        cardViewSeek.animate()
            ?.setInterpolator(FastOutSlowInInterpolator())
            ?.alpha(0f)?.scaleX(.8f)?.scaleY(.8f)
            ?.setDuration(200)
            ?.start()
    }

    fun setupOptionsMenu(media: Playable?) {
        if (toolbar.menu.size() == 0) {
            toolbar.inflateMenu(R.menu.mediaplayer)
        }

        val isFeedMedia = media is FeedMedia
        toolbar.menu?.findItem(R.id.open_feed_item)?.setVisible(isFeedMedia)
        if (media != null && isFeedMedia) {
            FeedItemMenuHandler.onPrepareMenu(toolbar.menu, (media as FeedMedia).item)
        }

        if (controller != null) {
            toolbar.menu.findItem(R.id.set_sleeptimer_item).setVisible(!controller!!.sleepTimerActive())
            toolbar.menu.findItem(R.id.disable_sleeptimer_item).setVisible(controller!!.sleepTimerActive())
        }
        (activity as? CastEnabledActivity)?.requestCastButton(toolbar.menu)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val media: Playable = controller?.getMedia() ?: return false

        val feedItem: FeedItem? = if ((media is FeedMedia)) media.item else null
        if (feedItem != null && FeedItemMenuHandler.onMenuItemClicked(this, item.itemId, feedItem)) {
            return true
        }

        val itemId = item.itemId
        if (itemId == R.id.disable_sleeptimer_item || itemId == R.id.set_sleeptimer_item) {
            SleepTimerDialog().show(childFragmentManager, "SleepTimerDialog")
            return true
        } else if (itemId == R.id.open_feed_item) {
            if (feedItem != null) {
                val intent: Intent = MainActivity.getIntentToOpenFeed(requireContext(), feedItem.feedId)
                startActivity(intent)
            }
            return true
        }
        return false
    }

    fun fadePlayerToToolbar(slideOffset: Float) {
        val playerFadeProgress = (max(0.0, min(0.2, (slideOffset - 0.2f).toDouble())) / 0.2f).toFloat()
        val player = playerFragment
        player.alpha = 1 - playerFadeProgress
        player.visibility = if (playerFadeProgress > 0.99f) View.INVISIBLE else View.VISIBLE
        val toolbarFadeProgress = (max(0.0, min(0.2, (slideOffset - 0.6f).toDouble())) / 0.2f).toFloat()
        toolbar.setAlpha(toolbarFadeProgress)
        toolbar.visibility = if (toolbarFadeProgress < 0.01f) View.INVISIBLE else View.VISIBLE
    }

    private class AudioPlayerPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun createFragment(position: Int): Fragment {
            Log.d(TAG, "getItem($position)")

            return when (position) {
                FIRST_PAGE -> ItemDescriptionFragment()
                SECOND_PAGE -> CoverFragment()
                else -> ItemDescriptionFragment()
            }
        }

        override fun getItemCount(): Int {
            return NUM_CONTENT_FRAGMENTS
        }
        companion object {
            private const val TAG = "AudioPlayerPagerAdapter"
        }
    }

    @JvmOverloads
    fun scrollToPage(page: Int, smoothScroll: Boolean = false) {
        pager.setCurrentItem(page, smoothScroll)

        val visibleChild = childFragmentManager.findFragmentByTag("f$FIRST_PAGE")
        if (visibleChild is ItemDescriptionFragment) {
            visibleChild.scrollToTop()
        }
    }

    companion object {
        const val TAG: String = "AudioPlayerFragment"
        const val FIRST_PAGE: Int = 0
        const val SECOND_PAGE: Int = 1
        private const val NUM_CONTENT_FRAGMENTS = 2
    }
}
