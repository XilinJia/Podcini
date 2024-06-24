package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.AudioplayerFragmentBinding
import ac.mdiq.podcini.databinding.InternalPlayerFragmentBinding
import ac.mdiq.podcini.storage.utils.ChapterUtils
import ac.mdiq.podcini.storage.utils.ImageResourceUtils
import ac.mdiq.podcini.playback.PlaybackController
import ac.mdiq.podcini.playback.PlaybackController.Companion.curSpeedMultiplier
import ac.mdiq.podcini.playback.PlaybackController.Companion.duration
import ac.mdiq.podcini.playback.PlaybackController.Companion.fallbackSpeed
import ac.mdiq.podcini.playback.PlaybackController.Companion.isPlayingVideoLocally
import ac.mdiq.podcini.playback.PlaybackController.Companion.playbackService
import ac.mdiq.podcini.playback.PlaybackController.Companion.position
import ac.mdiq.podcini.playback.PlaybackController.Companion.seekTo
import ac.mdiq.podcini.playback.PlaybackController.Companion.sleepTimerActive
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.InTheatre.loadPlayableFromPreferences
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.getCurrentPlaybackSpeed
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.cast.CastEnabledActivity
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.isSkipSilence
import ac.mdiq.podcini.preferences.UserPreferences.videoPlayMode
import ac.mdiq.podcini.receiver.MediaButtonReceiver
import ac.mdiq.podcini.storage.model.Chapter
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.storage.utils.MediaType
import ac.mdiq.podcini.ui.actions.menuhandler.EpisodeMenuHandler
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.Companion.videoMode
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.VideoMode
import ac.mdiq.podcini.ui.activity.starter.VideoPlayerActivityStarter
import ac.mdiq.podcini.ui.dialog.MediaPlayerErrorDialog
import ac.mdiq.podcini.ui.dialog.SkipPreferenceDialog
import ac.mdiq.podcini.ui.dialog.SleepTimerDialog
import ac.mdiq.podcini.ui.dialog.VariableSpeedDialog
import ac.mdiq.podcini.ui.view.ChapterSeekBar
import ac.mdiq.podcini.ui.view.PlayButton
import ac.mdiq.podcini.ui.view.PlaybackSpeedIndicatorView
import ac.mdiq.podcini.util.Converter
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.TimeSpeedConverter
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.app.Activity
import android.content.Intent
import android.os.Bundle
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
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.elevation.SurfaceColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private var playerDetailsFragment: PlayerDetailsFragment? = null

    private lateinit var toolbar: MaterialToolbar
    private var playerFragment1: InternalPlayerFragment? = null
    private var playerFragment2: InternalPlayerFragment? = null
    private var playerFragment: InternalPlayerFragment? = null

    private var playerView1: View? = null
    private var playerView2: View? = null

    private lateinit  var cardViewSeek: CardView
    private lateinit  var txtvSeek: TextView

    private var controller: PlaybackController? = null
    private var seekedToChapterStart = false
//    private var currentChapterIndex = -1
    private var duration = 0

    private var currentMedia: Playable? = null
    private var currentitem: Episode? = null

    private var isShowPlay: Boolean = false
    var isCollapsed = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = AudioplayerFragmentBinding.inflate(inflater)
        binding.root.setOnTouchListener { _: View?, _: MotionEvent? -> true } // Avoid clicks going through player to fragments below

        Logd(TAG, "fragment onCreateView")
        toolbar = binding.toolbar
        toolbar.title = ""
        toolbar.setNavigationOnClickListener {
//            val mtype = getMedia?.getMediaType()
//            if (mtype == MediaType.AUDIO || (mtype == MediaType.VIDEO && videoPlayMode == VideoMode.AUDIO_ONLY)) {
                val bottomSheet = (activity as MainActivity).bottomSheet
//                if (bottomSheet.state == BottomSheetBehavior.STATE_EXPANDED)
                    bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
//                else bottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
//            }
        }
        toolbar.setOnMenuItemClickListener(this)

        controller = createController()
        controller!!.init()

        playerFragment1 = InternalPlayerFragment.newInstance(controller!!)
        childFragmentManager.beginTransaction()
            .replace(R.id.playerFragment1, playerFragment1!!, "InternalPlayerFragment1")
            .commit()
        playerView1 = binding.root.findViewById(R.id.playerFragment1)
        playerView1?.setBackgroundColor(SurfaceColors.getColorForElevation(requireContext(), 8 * resources.displayMetrics.density))

        playerFragment2 = InternalPlayerFragment.newInstance(controller!!)
        childFragmentManager.beginTransaction()
            .replace(R.id.playerFragment2, playerFragment2!!, "InternalPlayerFragment2")
            .commit()
        playerView2 = binding.root.findViewById(R.id.playerFragment2)
        playerView2?.setBackgroundColor(SurfaceColors.getColorForElevation(requireContext(), 8 * resources.displayMetrics.density))

        onCollaped()

        cardViewSeek = binding.cardViewSeek
        txtvSeek = binding.txtvSeek

        return binding.root
    }

//    fun initDetailedView() {
//        if (playerDetailsFragment == null) {
//            val fm = requireActivity().supportFragmentManager
//            val transaction = fm.beginTransaction()
//            playerDetailsFragment = PlayerDetailsFragment()
//            transaction.replace(R.id.itemDescription, playerDetailsFragment!!).commit()
//        }
//    }

    override fun onDestroyView() {
        Logd(TAG, "Fragment destroyed")
        super.onDestroyView()
        _binding = null
        controller?.release()
        controller = null
    }

    fun onExpanded() {
        Logd(TAG, "onExpanded()")
        if (playerDetailsFragment == null) {
            val fm = requireActivity().supportFragmentManager
            val transaction = fm.beginTransaction()
            playerDetailsFragment = PlayerDetailsFragment()
            transaction.replace(R.id.itemDescription, playerDetailsFragment!!).commit()
        }
        isCollapsed = false
        playerFragment = playerFragment2
        playerFragment?.updateUi(currentMedia)
        playerFragment?.butPlay?.setIsShowPlay(isShowPlay)
        playerDetailsFragment?.load()
    }

    fun onCollaped() {
        Logd(TAG, "onCollaped()")
        isCollapsed = true
        playerFragment = playerFragment1
        playerFragment?.updateUi(currentMedia)
        playerFragment?.butPlay?.setIsShowPlay(isShowPlay)
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
//        updatePosition(PlaybackPositionEvent(position, duration))
//    }

    fun loadMediaInfo(includingChapters: Boolean) {
        if (curMedia == null) {
            (activity as MainActivity).setPlayerVisible(false)
            return
        }
        (activity as MainActivity).setPlayerVisible(true)
        if (currentMedia == null || curMedia?.getIdentifier() != currentMedia?.getIdentifier() || (includingChapters && !curMedia!!.chaptersLoaded())) {
            Logd(TAG, "loadMediaInfo loading details ${curMedia?.getIdentifier()} chapter: $includingChapters")
            lifecycleScope.launch {
                val media: Playable = withContext(Dispatchers.IO) {
                    curMedia!!.apply {
                        if (includingChapters) ChapterUtils.loadChapters(this, requireContext(), false)
                    }
                }
                currentMedia = media
                updateUi()
                playerFragment?.updateUi(currentMedia)
                if (!includingChapters) loadMediaInfo(true)
            }.invokeOnCompletion { throwable ->
                if (throwable!= null) {
                    Log.e(TAG, Log.getStackTraceString(throwable))
                }
            }
        }
    }

    private fun createController(): PlaybackController {
        return object : PlaybackController(requireActivity()) {
            override fun updatePlayButtonShowsPlay(showPlay: Boolean) {
                isShowPlay = showPlay
                playerFragment?.butPlay?.setIsShowPlay(showPlay)
//                playerFragment2?.butPlay?.setIsShowPlay(showPlay)
            }
            override fun loadMediaInfo() {
                this@AudioPlayerFragment.loadMediaInfo(false)
                if (!isCollapsed) playerDetailsFragment?.load()
            }
            override fun onPlaybackEnd() {
                isShowPlay = true
                playerFragment?.butPlay?.setIsShowPlay(true)
//                playerFragment2?.butPlay?.setIsShowPlay(true)
                (activity as MainActivity).setPlayerVisible(false)
            }
        }
    }

    private fun updateUi() {
        Logd(TAG, "updateUi called")
        setChapterDividers(currentMedia)
        setupOptionsMenu(currentMedia)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
        loadMediaInfo(false)
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
//        progressIndicator.visibility = View.GONE // Controller released; we will not receive buffering updates
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

    private fun onEvenStartPlay(event: FlowEvent.PlayEvent) {
        Logd(TAG, "onEvenStartPlay ${event.episode.title}")
        currentitem = event.episode
        if (currentMedia?.getIdentifier() == null || currentitem!!.media?.getIdentifier() != currentMedia?.getIdentifier())
            playerDetailsFragment?.setItem(currentitem!!)
        (activity as MainActivity).setPlayerVisible(true)
    }

    private var eventSink: Job?     = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    private fun procFlowEvents() {
        if (eventSink != null) return
        eventSink = lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.PlaybackServiceEvent -> {
                        if (event.action == FlowEvent.PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN)
                            (activity as MainActivity).bottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
                        playerFragment?.onPlaybackServiceChanged(event)
                    }
                    is FlowEvent.PlayEvent -> onEvenStartPlay(event)
                    is FlowEvent.PlayerErrorEvent -> MediaPlayerErrorDialog.show(activity as Activity, event)
                    is FlowEvent.FavoritesEvent -> loadMediaInfo(false)
                    is FlowEvent.SleepTimerUpdatedEvent ->  if (event.isCancelled || event.wasJustEnabled()) loadMediaInfo(false)

                    is FlowEvent.PlaybackPositionEvent -> playerFragment?.onPositionUpdate(event)
                    is FlowEvent.SpeedChangedEvent -> playerFragment?.updatePlaybackSpeedButton(event)

                    else -> {}
                }
            }
        }
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (controller == null) return

        when {
            fromUser -> {
                val prog: Float = progress / (seekBar.max.toFloat())
                val converter = TimeSpeedConverter(curSpeedMultiplier)
                val position: Int = converter.convert((prog * duration).toInt())
                val newChapterIndex: Int = ChapterUtils.getCurrentChapterIndex(curMedia, position)
                if (newChapterIndex > -1) {
//                    if (!sbPosition.isPressed && currentChapterIndex != newChapterIndex) {
//                        currentChapterIndex = newChapterIndex
//                        val media = getMedia
//                        position = media?.getChapters()?.get(currentChapterIndex)?.start?.toInt() ?: 0
//                        seekedToChapterStart = true
//                        seekTo(position)
//                        updateUi(controller!!.getMedia)
//                        sbPosition.highlightCurrentChapter()
//                    }
                    txtvSeek.text = curMedia?.getChapters()?.get(newChapterIndex)?.title ?: ("\n${Converter.getDurationStringLong(position)}")
                } else txtvSeek.text = Converter.getDurationStringLong(position)
            }
            duration != playbackService?.duration -> updateUi()
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
                seekTo((prog * duration).toInt())
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

    private fun setupOptionsMenu(media: Playable?) {
        if (toolbar.menu.size() == 0) toolbar.inflateMenu(R.menu.mediaplayer)

        val isEpisodeMedia = media is EpisodeMedia
        toolbar.menu?.findItem(R.id.open_feed_item)?.setVisible(isEpisodeMedia)
        var item = currentitem
        if (item == null && isEpisodeMedia) item = (media as EpisodeMedia).episode
        EpisodeMenuHandler.onPrepareMenu(toolbar.menu, item)

        val mediaType = curMedia?.getMediaType()
        toolbar.menu?.findItem(R.id.show_video)?.setVisible(mediaType == MediaType.VIDEO)

        if (controller != null) {
            toolbar.menu.findItem(R.id.set_sleeptimer_item).setVisible(!sleepTimerActive())
            toolbar.menu.findItem(R.id.disable_sleeptimer_item).setVisible(sleepTimerActive())
        }
        (activity as? CastEnabledActivity)?.requestCastButton(toolbar.menu)
    }

    override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        val media: Playable = curMedia ?: return false

        var feedItem = currentitem
        if (feedItem == null && media is EpisodeMedia) feedItem = media.episode
//        feedItem: FeedItem? = if (media is EpisodeMedia) media.item else null
        if (feedItem != null && EpisodeMenuHandler.onMenuItemClicked(this, menuItem.itemId, feedItem)) return true

        val itemId = menuItem.itemId
        when (itemId) {
            R.id.show_home_reader_view -> playerDetailsFragment?.buildHomeReaderText()
            R.id.show_video -> {
                controller!!.playPause()
                VideoPlayerActivityStarter(requireContext(), VideoMode.FULL_SCREEN_VIEW).start()
            }
            R.id.disable_sleeptimer_item, R.id.set_sleeptimer_item -> SleepTimerDialog().show(childFragmentManager, "SleepTimerDialog")
            R.id.open_feed_item -> {
                if (feedItem?.feedId != null) {
                    val intent: Intent = MainActivity.getIntentToOpenFeed(requireContext(), feedItem.feedId!!)
                    startActivity(intent)
                }
            }
            R.id.share_notes -> {
                val notes = if (playerDetailsFragment?.showHomeText == true) playerDetailsFragment!!.readerhtml else feedItem?.description
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
            }
            else -> return false
        }
        return true
    }

    fun scrollToTop() {
        playerDetailsFragment?.scrollToTop()
    }

    fun fadePlayerToToolbar(slideOffset: Float) {
        val playerFadeProgress = (max(0.0, min(0.2, (slideOffset - 0.2f).toDouble())) / 0.2f).toFloat()
        val player = playerView1
        player?.alpha = 1 - playerFadeProgress
        player?.visibility = if (playerFadeProgress > 0.99f) View.INVISIBLE else View.VISIBLE
        val toolbarFadeProgress = (max(0.0, min(0.2, (slideOffset - 0.6f).toDouble())) / 0.2f).toFloat()
        toolbar.setAlpha(toolbarFadeProgress)
        toolbar.visibility = if (toolbarFadeProgress < 0.01f) View.INVISIBLE else View.VISIBLE
    }

    class InternalPlayerFragment : Fragment(), SeekBar.OnSeekBarChangeListener {
        val TAG = this::class.simpleName ?: "Anonymous"

        private var _binding: InternalPlayerFragmentBinding? = null
        private val binding get() = _binding!!

        private lateinit var imgvCover: ImageView
        var butPlay: PlayButton? = null

        private var isControlButtonsSet = false

        private lateinit var butPlaybackSpeed: PlaybackSpeedIndicatorView
        private lateinit var txtvPlaybackSpeed: TextView

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

        private var prevMedia: Playable? = null

        private var showTimeLeft = false

        @UnstableApi
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            _binding = InternalPlayerFragmentBinding.inflate(inflater)
            Logd(TAG, "fragment onCreateView")

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
            setupControlButtons()
            butPlaybackSpeed.setOnClickListener {
                VariableSpeedDialog.newInstance(booleanArrayOf(true, true, true), null)?.show(childFragmentManager, null)
            }
            sbPosition.setOnSeekBarChangeListener(this)

            binding.internalPlayerFragment.setOnClickListener {
                Logd(TAG, "internalPlayerFragment was clicked")
                val media = curMedia
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
            return binding.root
        }

        @OptIn(UnstableApi::class) override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
        }

        @UnstableApi
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            butPlay?.setOnClickListener {
                if (controller == null) return@setOnClickListener

                val media = curMedia
                if (media != null) {
                    if (media.getMediaType() == MediaType.VIDEO && MediaPlayerBase.status != PlayerStatus.PLAYING) {
                        controller!!.playPause()
                        requireContext().startActivity(PlaybackService.getPlayerActivityIntent(requireContext(), media.getMediaType()))
                    } else controller!!.playPause()

                    if (!isControlButtonsSet) {
                        sbPosition.visibility = View.VISIBLE
                        isControlButtonsSet = true
                    }
                }
            }
        }

        @OptIn(UnstableApi::class) private fun setupControlButtons() {
            butRev.setOnClickListener {
                if (controller != null && isPlaybackServiceReady()) {
                    val curr: Int = position
                    seekTo(curr - UserPreferences.rewindSecs * 1000)
                    sbPosition.visibility = View.VISIBLE
                }
            }
            butRev.setOnLongClickListener {
                SkipPreferenceDialog.showSkipPreference(requireContext(), SkipPreferenceDialog.SkipDirection.SKIP_REWIND, txtvRev)
                true
            }
            butPlay?.setOnLongClickListener {
                if (controller != null && MediaPlayerBase.status == PlayerStatus.PLAYING) {
                    val fallbackSpeed = UserPreferences.fallbackSpeed
                    if (fallbackSpeed > 0.1f) fallbackSpeed(fallbackSpeed)
                }
                true
            }
            butFF.setOnClickListener {
                if (controller != null && isPlaybackServiceReady()) {
                    val curr: Int = position
                    seekTo(curr + UserPreferences.fastForwardSecs * 1000)
                    sbPosition.visibility = View.VISIBLE
                }
            }
            butFF.setOnLongClickListener {
                SkipPreferenceDialog.showSkipPreference(requireContext(), SkipPreferenceDialog.SkipDirection.SKIP_FORWARD, txtvFF)
                true
            }
            butSkip.setOnClickListener {
                if (controller != null && MediaPlayerBase.status == PlayerStatus.PLAYING) {
                    val speedForward = UserPreferences.speedforwardSpeed
                    if (speedForward > 0.1f) speedForward(speedForward)
                }
            }
            butSkip.setOnLongClickListener {
                activity?.sendBroadcast(MediaButtonReceiver.createIntent(requireContext(), KeyEvent.KEYCODE_MEDIA_NEXT))
                true
            }
        }

        private fun speedForward(speed: Float) {
//            playbackService?.speedForward(speed)
            if (playbackService?.mediaPlayer == null || playbackService?.isFallbackSpeed == true) return

            if (playbackService?.isSpeedForward == false) {
                playbackService?.normalSpeed = playbackService?.mediaPlayer!!.getPlaybackSpeed()
                playbackService?.mediaPlayer!!.setPlaybackParams(speed, isSkipSilence)
            } else playbackService?.mediaPlayer?.setPlaybackParams(playbackService!!.normalSpeed, isSkipSilence)

            playbackService!!.isSpeedForward = !playbackService!!.isSpeedForward
        }

        @OptIn(UnstableApi::class) private fun setupLengthTextView() {
            showTimeLeft = UserPreferences.shouldShowRemainingTime()
            txtvLength.setOnClickListener(View.OnClickListener {
                if (controller == null) return@OnClickListener
                showTimeLeft = !showTimeLeft
                UserPreferences.setShowRemainTimeSetting(showTimeLeft)
                onPositionUpdate(FlowEvent.PlaybackPositionEvent(position, duration))
            })
        }

        fun updatePlaybackSpeedButton(event: FlowEvent.SpeedChangedEvent) {
            val speedStr: String = DecimalFormat("0.00").format(event.newSpeed.toDouble())
            txtvPlaybackSpeed.text = speedStr
            butPlaybackSpeed.setSpeed(event.newSpeed)
        }

        @UnstableApi
        fun onPositionUpdate(event: FlowEvent.PlaybackPositionEvent) {
            if (controller == null || position == Playable.INVALID_TIME || duration == Playable.INVALID_TIME) return

            val converter = TimeSpeedConverter(curSpeedMultiplier)
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
            if (sbPosition.visibility == View.INVISIBLE && isPlaybackServiceReady()) sbPosition.visibility = View.VISIBLE

            if (!sbPosition.isPressed) {
                val progress: Float = (event.position.toFloat()) / event.duration
//                Log.d(TAG, "updating sbPosition: $progress")
                sbPosition.progress = (progress * sbPosition.max).toInt()
            }
        }

        fun onPlaybackServiceChanged(event: FlowEvent.PlaybackServiceEvent) {
            when (event.action) {
                FlowEvent.PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN -> (activity as MainActivity).setPlayerVisible(false)
                FlowEvent.PlaybackServiceEvent.Action.SERVICE_STARTED -> (activity as MainActivity).setPlayerVisible(true)
//                PlaybackServiceEvent.Action.SERVICE_RESTARTED -> (activity as MainActivity).setPlayerVisible(true)
            }
        }

        @OptIn(UnstableApi::class) override fun onStart() {
            Logd(TAG, "onStart() called")
            super.onStart()

            txtvRev.text = NumberFormat.getInstance().format(UserPreferences.rewindSecs.toLong())
            txtvFF.text = NumberFormat.getInstance().format(UserPreferences.fastForwardSecs.toLong())
            if (UserPreferences.speedforwardSpeed > 0.1f) txtvSkip.text = NumberFormat.getInstance().format(UserPreferences.speedforwardSpeed)
            else txtvSkip.visibility = View.GONE
            val media = curMedia ?: return
            updatePlaybackSpeedButton(FlowEvent.SpeedChangedEvent(getCurrentPlaybackSpeed(media)))
        }

        @UnstableApi
        override fun onPause() {
            Logd(TAG, "onPause() called")
            super.onPause()
            controller?.pause()
        }

        @OptIn(UnstableApi::class) override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}

        override fun onStartTrackingTouch(seekBar: SeekBar) {}

        @OptIn(UnstableApi::class) override fun onStopTrackingTouch(seekBar: SeekBar) {
            if (isPlaybackServiceReady()) {
                val prog: Float = seekBar.progress / (seekBar.max.toFloat())
                seekTo((prog * duration).toInt())
            }
        }

        @UnstableApi
        fun updateUi(media: Playable?) {
            Logd(TAG, "updateUi called $media")
            if (media == null) return

            episodeTitle.text = media.getEpisodeTitle()
//            (activity as MainActivity).setPlayerVisible(true)
            onPositionUpdate(FlowEvent.PlaybackPositionEvent(media.getPosition(), media.getDuration()))

            if (prevMedia?.getIdentifier() != media.getIdentifier()) {
                val imgLoc = ImageResourceUtils.getEpisodeListImageLocation(media)
                val imgLocFB = ImageResourceUtils.getFallbackImageLocation(media)
                val imageLoader = imgvCover.context.imageLoader
                val imageRequest = ImageRequest.Builder(requireContext())
                    .data(imgLoc)
                    .setHeader("User-Agent", "Mozilla/5.0")
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
            }
            if (isPlayingVideoLocally) {
                (activity as MainActivity).bottomSheet.setLocked(true)
                (activity as MainActivity).bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
            } else {
                butPlay?.visibility = View.VISIBLE
                (activity as MainActivity).bottomSheet.setLocked(false)
            }
            prevMedia = media
        }

        companion object {
            var controller: PlaybackController? = null
            fun newInstance(controller_: PlaybackController) : InternalPlayerFragment {
                controller = controller_
                return InternalPlayerFragment()
            }
        }
    }

    companion object {
        val TAG = AudioPlayerFragment::class.simpleName ?: "Anonymous"

        fun isPlaybackServiceReady() : Boolean {
            return playbackService?.isServiceReady() == true
        }
    }
}
