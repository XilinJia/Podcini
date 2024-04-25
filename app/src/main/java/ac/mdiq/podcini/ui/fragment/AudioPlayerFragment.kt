package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.AudioplayerFragmentBinding
import ac.mdiq.podcini.databinding.InternalPlayerFragmentBinding
import ac.mdiq.podcini.feed.util.ImageResourceUtils
import ac.mdiq.podcini.feed.util.PlaybackSpeedUtils
import ac.mdiq.podcini.playback.PlaybackController
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.cast.CastEnabledActivity
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.videoPlayMode
import ac.mdiq.podcini.receiver.MediaButtonReceiver
import ac.mdiq.podcini.storage.model.feed.Chapter
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.storage.model.playback.MediaType
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.ui.actions.menuhandler.FeedItemMenuHandler
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.Companion.videoMode
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.VideoMode
import ac.mdiq.podcini.ui.activity.appstartintent.VideoPlayerActivityStarter
import ac.mdiq.podcini.ui.dialog.MediaPlayerErrorDialog
import ac.mdiq.podcini.ui.dialog.SkipPreferenceDialog
import ac.mdiq.podcini.ui.dialog.SleepTimerDialog
import ac.mdiq.podcini.ui.dialog.VariableSpeedDialog
import ac.mdiq.podcini.ui.fragment.EpisodeHomeFragment.Companion.fetchHtmlSource
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.view.ChapterSeekBar
import ac.mdiq.podcini.ui.view.PlayButton
import ac.mdiq.podcini.ui.view.PlaybackSpeedIndicatorView
import ac.mdiq.podcini.util.ChapterUtils
import ac.mdiq.podcini.util.Converter
import ac.mdiq.podcini.util.TimeSpeedConverter
import ac.mdiq.podcini.util.event.FavoritesEvent
import ac.mdiq.podcini.util.event.PlayerErrorEvent
import ac.mdiq.podcini.util.event.playback.*
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.app.ShareCompat
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.elevation.SurfaceColors
import io.reactivex.Maybe
import io.reactivex.MaybeEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.runBlocking
import net.dankito.readability4j.Readability4J
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

    private lateinit var itemDescFrag: PlayerDetailsFragment

    private lateinit var toolbar: MaterialToolbar
    private var playerFragment1: InternalPlayerFragment? = null
    private var playerFragment2: InternalPlayerFragment? = null
    private lateinit var playerView1: View
    private lateinit var playerView2: View

    private lateinit  var cardViewSeek: CardView
    private lateinit  var txtvSeek: TextView

    private var controller: PlaybackController? = null
    private var disposable: Disposable? = null
    private var seekedToChapterStart = false
    private var currentChapterIndex = -1
    private var duration = 0

    private var currentMedia: Playable? = null
    private var currentitem: FeedItem? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = AudioplayerFragmentBinding.inflate(inflater)

        binding.root.setOnTouchListener { _: View?, _: MotionEvent? -> true } // Avoid clicks going through player to fragments below

        Log.d(TAG, "fragment onCreateView")
        toolbar = binding.toolbar
        toolbar.title = ""
        toolbar.setNavigationOnClickListener {
//            val mtype = controller?.getMedia()?.getMediaType()
//            if (mtype == MediaType.AUDIO || (mtype == MediaType.VIDEO && videoPlayMode == VideoMode.AUDIO_ONLY)) {
                val bottomSheet = (activity as MainActivity).bottomSheet
//                if (bottomSheet.state == BottomSheetBehavior.STATE_EXPANDED)
                    bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
//                else bottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
//            }
        }
        toolbar.setOnMenuItemClickListener(this)

        controller = newPlaybackController()
        controller!!.init()

        playerFragment1 = InternalPlayerFragment.newInstance(controller!!)
        childFragmentManager.beginTransaction()
            .replace(R.id.playerFragment1, playerFragment1!!, InternalPlayerFragment.TAG)
            .commit()
        playerView1 = binding.root.findViewById(R.id.playerFragment1)
        playerView1.setBackgroundColor(SurfaceColors.getColorForElevation(requireContext(), 8 * resources.displayMetrics.density))

        playerFragment2 = InternalPlayerFragment.newInstance(controller!!)
        childFragmentManager.beginTransaction()
            .replace(R.id.playerFragment2, playerFragment2!!, InternalPlayerFragment.TAG)
            .commit()
        playerView2 = binding.root.findViewById(R.id.playerFragment2)
        playerView2.setBackgroundColor(SurfaceColors.getColorForElevation(requireContext(), 8 * resources.displayMetrics.density))

        cardViewSeek = binding.cardViewSeek
        txtvSeek = binding.txtvSeek

        val fm = requireActivity().supportFragmentManager
        val transaction = fm.beginTransaction()
        itemDescFrag = PlayerDetailsFragment()
        transaction.replace(R.id.itemDescription, itemDescFrag).commit()

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

        if (media.getChapters().isNotEmpty()) {
            val chapters: List<Chapter> = media.getChapters()
            val dividerPos = FloatArray(chapters.size)

            for (i in chapters.indices) {
                dividerPos[i] = chapters[i].start / duration.toFloat()
            }
        }
    }

//    @Subscribe(threadMode = ThreadMode.MAIN)
//    fun onUnreadItemsUpdate(event: UnreadItemsUpdateEvent?) {
//        if (controller == null) return
//        updatePosition(PlaybackPositionEvent(controller!!.position, controller!!.duration))
//    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaybackServiceChanged(event: PlaybackServiceEvent) {
        if (event.action == PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN)
            (activity as MainActivity).bottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun loadMediaInfo(includingChapters: Boolean) {
        Log.d(TAG, "loadMediaInfo called")

        val theMedia = controller?.getMedia() ?: return
        Log.d(TAG, "loadMediaInfo $theMedia")

        if (currentMedia == null || theMedia?.getIdentifier() != currentMedia?.getIdentifier()) {
            Log.d(TAG, "loadMediaInfo loading details")
            disposable?.dispose()
            disposable = Maybe.create<Playable> { emitter: MaybeEmitter<Playable?> ->
                val media: Playable? = theMedia
                if (media != null) {
                    if (includingChapters) ChapterUtils.loadChapters(media, requireContext(), false)
                    emitter.onSuccess(media)
                } else emitter.onComplete()
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ media: Playable ->
                    currentMedia = media
                    updateUi(media)
                    playerFragment1?.updateUi(media)
                    playerFragment2?.updateUi(media)
                    if (!includingChapters) loadMediaInfo(true)
                }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) },
                    { updateUi(null) })
        }
    }

    private fun newPlaybackController(): PlaybackController {
        return object : PlaybackController(requireActivity()) {
            override fun updatePlayButtonShowsPlay(showPlay: Boolean) {
                playerFragment1?.butPlay?.setIsShowPlay(showPlay)
                playerFragment2?.butPlay?.setIsShowPlay(showPlay)
            }

            override fun loadMediaInfo() {
                this@AudioPlayerFragment.loadMediaInfo(false)
            }

            override fun onPlaybackEnd() {
                playerFragment1?.butPlay?.setIsShowPlay(true)
                playerFragment2?.butPlay?.setIsShowPlay(true)
                (activity as MainActivity).setPlayerVisible(false)
            }
        }
    }

    private fun updateUi(media: Playable?) {
        Log.d(TAG, "updateUi called")
        setChapterDividers(media)
        setupOptionsMenu(media)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun sleepTimerUpdate(event: SleepTimerUpdatedEvent) {
        if (event.isCancelled || event.wasJustEnabled()) this@AudioPlayerFragment.loadMediaInfo(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onStart() {
        super.onStart()
        loadMediaInfo(false)
    }

    override fun onStop() {
        super.onStop()
//        progressIndicator.visibility = View.GONE // Controller released; we will not receive buffering updates
        disposable?.dispose()
    }

//    @Subscribe(threadMode = ThreadMode.MAIN)
//    @Suppress("unused")
//    fun bufferUpdate(event: BufferUpdateEvent) {
//        when {
//            event.hasStarted() -> {
//                progressIndicator.visibility = View.VISIBLE
//            }
//            event.hasEnded() -> {
//                progressIndicator.visibility = View.GONE
//            }
////            controller != null && controller!!.isStreaming -> {
////                sbPosition.setSecondaryProgress((event.progress * sbPosition.max).toInt())
////            }
//            else -> {
////                sbPosition.setSecondaryProgress(0)
//            }
//        }
//    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun favoritesChanged(event: FavoritesEvent?) {
        this@AudioPlayerFragment.loadMediaInfo(false)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun mediaPlayerError(event: PlayerErrorEvent) {
        MediaPlayerErrorDialog.show(activity as Activity, event)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvenStartPlay(event: StartPlayEvent) {
        Log.d(TAG, "onEvenStartPlay ${event.item.title}")
        currentitem = event.item
        if (currentMedia?.getIdentifier() == null || currentitem!!.media!!.getIdentifier() != currentMedia?.getIdentifier())
            itemDescFrag.setItem(currentitem!!)
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (controller == null) return

        when {
            fromUser -> {
                val prog: Float = progress / (seekBar.max.toFloat())
                val converter = TimeSpeedConverter(controller!!.currentPlaybackSpeedMultiplier)
                var position: Int = converter.convert((prog * controller!!.duration).toInt())
                val newChapterIndex: Int = ChapterUtils.getCurrentChapterIndex(controller!!.getMedia(), position)
                if (newChapterIndex > -1) {
//                    if (!sbPosition.isPressed && currentChapterIndex != newChapterIndex) {
//                        currentChapterIndex = newChapterIndex
//                        val media = controller!!.getMedia()
//                        position = media?.getChapters()?.get(currentChapterIndex)?.start?.toInt() ?: 0
//                        seekedToChapterStart = true
//                        controller!!.seekTo(position)
//                        updateUi(controller!!.getMedia())
//                        sbPosition.highlightCurrentChapter()
//                    }
                    txtvSeek.text = controller!!.getMedia()?.getChapters()?.get(newChapterIndex)?.title ?: ("\n${Converter.getDurationStringLong(position)}")
                } else txtvSeek.text = Converter.getDurationStringLong(position)
            }
            duration != controller!!.duration -> updateUi(controller!!.getMedia())
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
        if (toolbar.menu.size() == 0) toolbar.inflateMenu(R.menu.mediaplayer)

        val isFeedMedia = media is FeedMedia
        toolbar.menu?.findItem(R.id.open_feed_item)?.setVisible(isFeedMedia)
        var item = currentitem
        if (item == null && isFeedMedia) item = (media as FeedMedia).item
        FeedItemMenuHandler.onPrepareMenu(toolbar.menu, item)

        val mediaType = controller?.getMedia()?.getMediaType()
        toolbar.menu?.findItem(R.id.show_video)?.setVisible(mediaType == MediaType.VIDEO)

        if (controller != null) {
            toolbar.menu.findItem(R.id.set_sleeptimer_item).setVisible(!controller!!.sleepTimerActive())
            toolbar.menu.findItem(R.id.disable_sleeptimer_item).setVisible(controller!!.sleepTimerActive())
        }
        (activity as? CastEnabledActivity)?.requestCastButton(toolbar.menu)
    }

    override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        val media: Playable = controller?.getMedia() ?: return false

        var feedItem = currentitem
        if (feedItem == null && media is FeedMedia) feedItem = media.item
//        feedItem: FeedItem? = if (media is FeedMedia) media.item else null
        if (feedItem != null && FeedItemMenuHandler.onMenuItemClicked(this, menuItem.itemId, feedItem)) return true

        val itemId = menuItem.itemId
        when (itemId) {
            R.id.show_home_reader_view -> {
                itemDescFrag.buildHomeReaderText()
                return true
            }
            R.id.show_video -> {
                controller!!.playPause()
                VideoPlayerActivityStarter(requireContext(), VideoMode.FULL_SCREEN_VIEW).start()
                return true
            }
            R.id.disable_sleeptimer_item, R.id.set_sleeptimer_item -> {
                SleepTimerDialog().show(childFragmentManager, "SleepTimerDialog")
                return true
            }
            R.id.open_feed_item -> {
                if (feedItem != null) {
                    val intent: Intent = MainActivity.getIntentToOpenFeed(requireContext(), feedItem.feedId)
                    startActivity(intent)
                }
                return true
            }
            R.id.share_notes -> {
                if (feedItem == null) return false
                val notes = feedItem.description
                if (!notes.isNullOrEmpty()) {
                    val shareText = if (Build.VERSION.SDK_INT >= 24) Html.fromHtml(notes, Html.FROM_HTML_MODE_LEGACY).toString()
                    else Html.fromHtml(notes).toString()
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
            else -> return false
        }
    }

    @JvmOverloads
    fun scrollToTop() {
        itemDescFrag.scrollToTop()
    }

    fun fadePlayerToToolbar(slideOffset: Float) {
        val playerFadeProgress = (max(0.0, min(0.2, (slideOffset - 0.2f).toDouble())) / 0.2f).toFloat()
        val player = playerView1
        player.alpha = 1 - playerFadeProgress
        player.visibility = if (playerFadeProgress > 0.99f) View.INVISIBLE else View.VISIBLE
        val toolbarFadeProgress = (max(0.0, min(0.2, (slideOffset - 0.6f).toDouble())) / 0.2f).toFloat()
        toolbar.setAlpha(toolbarFadeProgress)
        toolbar.visibility = if (toolbarFadeProgress < 0.01f) View.INVISIBLE else View.VISIBLE
    }

    class InternalPlayerFragment : Fragment(), SeekBar.OnSeekBarChangeListener {
        private var _binding: InternalPlayerFragmentBinding? = null
        private val binding get() = _binding!!

        private lateinit var imgvCover: ImageView
        lateinit var butPlay: PlayButton

        private var isControlButtonsSet = false

        lateinit var butPlaybackSpeed: PlaybackSpeedIndicatorView
        lateinit var txtvPlaybackSpeed: TextView

        private lateinit var episodeTitle: TextView
        private lateinit var butRev: ImageButton
        private lateinit var txtvRev: TextView
        private lateinit  var butFF: ImageButton
        private lateinit  var txtvFF: TextView
        private lateinit  var butSkip: ImageButton
        private lateinit  var txtvSkip: TextView

        private lateinit var txtvPosition: TextView
        private lateinit var txtvLength: TextView
        private lateinit var sbPosition: ChapterSeekBar

        private var showTimeLeft = false

        private var disposable: Disposable? = null

        @UnstableApi
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            _binding = InternalPlayerFragmentBinding.inflate(inflater)
            Log.d(TAG, "fragment onCreateView")

            episodeTitle = binding.titleView
            butPlaybackSpeed = binding.butPlaybackSpeed
            txtvPlaybackSpeed = binding.txtvPlaybackSpeed
            imgvCover = binding.imgvCover
            butPlay = binding.butPlay
            butRev = binding.butRev
            txtvRev = binding.txtvRev
            butFF = binding.butFF
            txtvFF = binding.txtvFF
            butSkip = binding.butSkip
            txtvSkip = binding.txtvSkip
            sbPosition = binding.sbPosition
            txtvPosition = binding.txtvPosition
            txtvLength = binding.txtvLength

            setupLengthTextView()
//            setupControlButtons()
            butPlaybackSpeed.setOnClickListener {
                VariableSpeedDialog.newInstance(booleanArrayOf(true, true, true), null)?.show(childFragmentManager, null)
            }
//            sbPosition.setOnSeekBarChangeListener(null)

            binding.internalPlayerFragment.setOnClickListener {
                Log.d(TAG, "internalPlayerFragment was clicked")
                val media = controller?.getMedia()
                if (media != null) {
                    val mediaType = media.getMediaType()
                    if (mediaType == MediaType.AUDIO ||
                            (mediaType == MediaType.VIDEO && (videoPlayMode == VideoMode.AUDIO_ONLY.mode || videoMode == VideoMode.AUDIO_ONLY))) {
                        controller!!.ensureService()
                        (activity as MainActivity).bottomSheet.setState(BottomSheetBehavior.STATE_EXPANDED)
                    } else {
                        controller?.playPause()
//                        controller!!.ensureService()
                        val intent = PlaybackService.getPlayerActivityIntent(requireContext(), mediaType)
                        startActivity(intent)
                    }
                }
            }

            EventBus.getDefault().register(this)
            return binding.root
        }

        @OptIn(UnstableApi::class) override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
            EventBus.getDefault().unregister(this)
        }

        @UnstableApi
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            butPlay.setOnClickListener {
                if (controller == null) return@setOnClickListener

                val media = controller!!.getMedia()
                if (media != null) {
                    if (media.getMediaType() == MediaType.VIDEO && controller!!.status != PlayerStatus.PLAYING) {
                        controller!!.playPause()
                        requireContext().startActivity(PlaybackService.getPlayerActivityIntent(requireContext(), media.getMediaType()))
                    } else controller!!.playPause()
                    if (!isControlButtonsSet) {
                        setupControlButtons()
                        sbPosition.visibility = View.VISIBLE
                        sbPosition.setOnSeekBarChangeListener(this)
                        isControlButtonsSet = true
                    }
                }
            }
        }

        @OptIn(UnstableApi::class) private fun setupControlButtons() {
            butRev.setOnClickListener {
                if (controller != null && controller!!.isPlaybackServiceReady()) {
                    val curr: Int = controller!!.position
                    controller!!.seekTo(curr - UserPreferences.rewindSecs * 1000)
                }
            }
            butRev.setOnLongClickListener {
                SkipPreferenceDialog.showSkipPreference(requireContext(), SkipPreferenceDialog.SkipDirection.SKIP_REWIND, txtvRev)
                true
            }
//            butPlay.setOnClickListener {
//                controller?.init()
//                controller?.playPause()
//            }
            butPlay.setOnLongClickListener {
                if (controller != null && controller!!.status == PlayerStatus.PLAYING) {
                    val fallbackSpeed = UserPreferences.fallbackSpeed
                    if (fallbackSpeed > 0.1f) controller!!.fallbackSpeed(fallbackSpeed)
                }
                true
            }
            butFF.setOnClickListener {
                if (controller != null && controller!!.isPlaybackServiceReady()) {
                    val curr: Int = controller!!.position
                    controller!!.seekTo(curr + UserPreferences.fastForwardSecs * 1000)
                }
            }
            butFF.setOnLongClickListener {
                SkipPreferenceDialog.showSkipPreference(requireContext(), SkipPreferenceDialog.SkipDirection.SKIP_FORWARD, txtvFF)
                true
            }
            butSkip.setOnClickListener {
                if (controller != null && controller!!.status == PlayerStatus.PLAYING) {
                    val speedForward = UserPreferences.speedforwardSpeed
                    if (speedForward > 0.1f) controller!!.speedForward(speedForward)
                }
            }
            butSkip.setOnLongClickListener {
                activity?.sendBroadcast(MediaButtonReceiver.createIntent(requireContext(), KeyEvent.KEYCODE_MEDIA_NEXT))
                true
            }
        }

        @OptIn(UnstableApi::class) private fun setupLengthTextView() {
            showTimeLeft = UserPreferences.shouldShowRemainingTime()
            txtvLength.setOnClickListener(View.OnClickListener {
                if (controller == null) return@OnClickListener
                showTimeLeft = !showTimeLeft
                UserPreferences.setShowRemainTimeSetting(showTimeLeft)
                onPositionObserverUpdate(PlaybackPositionEvent(controller!!.position, controller!!.duration))
            })
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        fun updatePlaybackSpeedButton(event: SpeedChangedEvent) {
            val speedStr: String = DecimalFormat("0.00").format(event.newSpeed.toDouble())
            txtvPlaybackSpeed.text = speedStr
            butPlaybackSpeed.setSpeed(event.newSpeed)
        }

        @UnstableApi
        @Subscribe(threadMode = ThreadMode.MAIN)
        fun onPositionObserverUpdate(event: PlaybackPositionEvent) {
            if (controller == null || controller!!.position == Playable.INVALID_TIME || controller!!.duration == Playable.INVALID_TIME) return

            val converter = TimeSpeedConverter(controller!!.currentPlaybackSpeedMultiplier)
            val currentPosition: Int = converter.convert(event.position)
            val duration: Int = converter.convert(event.duration)
            val remainingTime: Int = converter.convert(max((event.duration - event.position).toDouble(), 0.0).toInt())
            if (currentPosition == Playable.INVALID_TIME || duration == Playable.INVALID_TIME) {
                Log.w(TAG, "Could not react to position observer update because of invalid time")
                return
            }

            txtvPosition.text = Converter.getDurationStringLong(currentPosition)
            txtvPosition.setContentDescription(getString(R.string.position,
                Converter.getDurationStringLocalized(requireContext(), currentPosition.toLong())))
            val showTimeLeft = UserPreferences.shouldShowRemainingTime()
            if (showTimeLeft) {
                txtvLength.setContentDescription(getString(R.string.remaining_time,
                    Converter.getDurationStringLocalized(requireContext(), remainingTime.toLong())))
                txtvLength.text = (if (remainingTime > 0) "-" else "") + Converter.getDurationStringLong(remainingTime)
            } else {
                txtvLength.setContentDescription(getString(R.string.chapter_duration,
                    Converter.getDurationStringLocalized(requireContext(), duration.toLong())))
                txtvLength.text = Converter.getDurationStringLong(duration)
            }

            if (!sbPosition.isPressed) {
                val progress: Float = (event.position.toFloat()) / event.duration
                sbPosition.progress = (progress * sbPosition.max).toInt()
            }
        }

        @UnstableApi @Subscribe(threadMode = ThreadMode.MAIN)
        fun onPlaybackServiceChanged(event: PlaybackServiceEvent) {
            when (event.action) {
                PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN -> (activity as MainActivity).setPlayerVisible(false)
                PlaybackServiceEvent.Action.SERVICE_STARTED -> (activity as MainActivity).setPlayerVisible(true)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            Log.d(TAG, "Fragment is about to be destroyed")
            disposable?.dispose()
        }

        @OptIn(UnstableApi::class) override fun onStart() {
            super.onStart()
            txtvRev.text = NumberFormat.getInstance().format(UserPreferences.rewindSecs.toLong())
            txtvFF.text = NumberFormat.getInstance().format(UserPreferences.fastForwardSecs.toLong())
            if (UserPreferences.speedforwardSpeed > 0.1f) txtvSkip.text = NumberFormat.getInstance().format(UserPreferences.speedforwardSpeed)
            else txtvSkip.visibility = View.GONE
            val media = controller?.getMedia() ?: return
            updatePlaybackSpeedButton(SpeedChangedEvent(PlaybackSpeedUtils.getCurrentPlaybackSpeed(media)))
        }

        @UnstableApi
        override fun onPause() {
            super.onPause()
            controller?.pause()
        }

        @OptIn(UnstableApi::class) override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}

        override fun onStartTrackingTouch(seekBar: SeekBar) {}

        @OptIn(UnstableApi::class) override fun onStopTrackingTouch(seekBar: SeekBar) {
            if (controller != null && controller!!.isPlaybackServiceReady()) {
                val prog: Float = seekBar.progress / (seekBar.max.toFloat())
                controller!!.seekTo((prog * controller!!.duration).toInt())
            }
        }

        @UnstableApi
        fun updateUi(media: Playable?) {
            Log.d(TAG, "updateUi called $media")
            if (media == null) return

            episodeTitle.text = media.getEpisodeTitle()
            (activity as MainActivity).setPlayerVisible(true)
            onPositionObserverUpdate(PlaybackPositionEvent(media.getPosition(), media.getDuration()))

            val options = RequestOptions()
                .placeholder(R.color.light_gray)
                .error(R.color.light_gray)
                .fitCenter()
                .dontAnimate()

            val imgLoc = ImageResourceUtils.getEpisodeListImageLocation(media) + "sdfsdf"
            val imgLocFB = ImageResourceUtils.getFallbackImageLocation(media)

            Glide.with(this)
                .load(imgLoc)
                .error(Glide.with(this)
                    .load(imgLocFB)
                    .error(R.mipmap.ic_launcher)
                    .apply(options))
                .apply(options)
                .into(imgvCover)

            if (controller?.isPlayingVideoLocally == true) {
                (activity as MainActivity).bottomSheet.setLocked(true)
                (activity as MainActivity).bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
            } else {
                butPlay.visibility = View.VISIBLE
                (activity as MainActivity).bottomSheet.setLocked(false)
            }
        }

        companion object {
            const val TAG: String = "InternalPlayerFragment"

            var controller: PlaybackController? = null

            fun newInstance(controller_: PlaybackController) : InternalPlayerFragment {
                controller = controller_
                return InternalPlayerFragment()
            }
        }
    }

    companion object {
        const val TAG: String = "AudioPlayerFragment"
    }
}
