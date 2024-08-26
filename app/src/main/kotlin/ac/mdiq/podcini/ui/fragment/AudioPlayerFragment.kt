package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.AudioplayerFragmentBinding
import ac.mdiq.podcini.databinding.PlayerUiFragmentBinding
import ac.mdiq.podcini.playback.PlaybackServiceStarter
import ac.mdiq.podcini.playback.ServiceStatusHandler
import ac.mdiq.podcini.playback.ServiceStatusHandler.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.getCurrentPlaybackSpeed
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.cast.CastEnabledActivity
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.curDurationFB
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.curPositionFB
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.curSpeedFB
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.toggleFallbackSpeed
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isPlayingVideoLocally
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isSleepTimerActive
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playPause
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.seekTo
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.isSkipSilence
import ac.mdiq.podcini.preferences.UserPreferences.videoPlayMode
import ac.mdiq.podcini.receiver.MediaButtonReceiver
import ac.mdiq.podcini.storage.model.Chapter
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.storage.utils.ChapterUtils
import ac.mdiq.podcini.storage.utils.ImageResourceUtils
import ac.mdiq.podcini.storage.model.MediaType
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
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.storage.utils.TimeSpeedConverter
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.*
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
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.imageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.elevation.SurfaceColors
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
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

    private var playerUI1: PlayerUIFragment? = null
    private var playerUI2: PlayerUIFragment? = null
    private var playerUI: PlayerUIFragment? = null
    private var playerUIView1: View? = null
    private var playerUIView2: View? = null

    private lateinit  var cardViewSeek: CardView

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: ServiceStatusHandler? = null
    private var seekedToChapterStart = false
//    private var currentChapterIndex = -1

    private var currentMedia: Playable? = null

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

        controller = createHandler()
        controller!!.init()

        playerUI1 = PlayerUIFragment.newInstance(controller!!)
        childFragmentManager.beginTransaction()
            .replace(R.id.playerFragment1, playerUI1!!, "InternalPlayerFragment1")
            .commit()
        playerUIView1 = binding.root.findViewById(R.id.playerFragment1)
        playerUIView1?.setBackgroundColor(SurfaceColors.getColorForElevation(requireContext(), 8 * resources.displayMetrics.density))

        playerUI2 = PlayerUIFragment.newInstance(controller!!)
        childFragmentManager.beginTransaction()
            .replace(R.id.playerFragment2, playerUI2!!, "InternalPlayerFragment2")
            .commit()
        playerUIView2 = binding.root.findViewById(R.id.playerFragment2)
        playerUIView2?.setBackgroundColor(SurfaceColors.getColorForElevation(requireContext(), 8 * resources.displayMetrics.density))
        onCollaped()
        cardViewSeek = binding.cardViewSeek

        initDetailedView()

        return binding.root
    }

    private fun initDetailedView() {
        if (playerDetailsFragment == null) {
            val fm = requireActivity().supportFragmentManager
            val transaction = fm.beginTransaction()
            playerDetailsFragment = PlayerDetailsFragment()
            transaction.replace(R.id.itemDescription, playerDetailsFragment!!).commit()
        }
    }

    override fun onDestroyView() {
        Logd(TAG, "Fragment destroyed")
        _binding = null
        controller?.release()
        controller = null
        super.onDestroyView()
    }

    fun onExpanded() {
        Logd(TAG, "onExpanded()")
        initDetailedView()
//        the function can also be called from MainActivity when a select menu pops up and closes
        if (isCollapsed) {
            isCollapsed = false
            playerUI = playerUI2
            playerUI?.updateUi(currentMedia)
            playerUI?.butPlay?.setIsShowPlay(isShowPlay)
            playerDetailsFragment?.updateInfo()
        }
    }

    fun onCollaped() {
        Logd(TAG, "onCollaped()")
        isCollapsed = true
        playerUI = playerUI1
        playerUI?.updateUi(currentMedia)
        playerUI?.butPlay?.setIsShowPlay(isShowPlay)
    }

    private fun setChapterDividers() {
        if (currentMedia == null) return

        if (currentMedia!!.getChapters().isNotEmpty()) {
            val chapters: List<Chapter> = currentMedia!!.getChapters()
            val dividerPos = FloatArray(chapters.size)

            for (i in chapters.indices) {
                dividerPos[i] = chapters[i].start / curDurationFB.toFloat()
            }
        }
    }

//    @Subscribe(threadMode = ThreadMode.MAIN)
//    fun onUnreadItemsUpdate(event: UnreadItemsUpdateEvent?) {
//        if (controller == null) return
//        updatePosition(PlaybackPositionEvent(position, duration))
//    }

    private var loadItemsRunning = false
    fun loadMediaInfo(includingChapters: Boolean) {
        val actMain = (activity as MainActivity)
        if (curMedia == null) {
            if (actMain.isPlayerVisible()) actMain.setPlayerVisible(false)
            return
        }
        if (!loadItemsRunning) {
            loadItemsRunning = true
            if (!actMain.isPlayerVisible()) actMain.setPlayerVisible(true)
            if (!isCollapsed && (currentMedia == null || curMedia?.getIdentifier() != currentMedia?.getIdentifier())) playerDetailsFragment?.updateInfo()

            if (currentMedia == null || curMedia?.getIdentifier() != currentMedia?.getIdentifier() || (includingChapters && !curMedia!!.chaptersLoaded())) {
                Logd(TAG, "loadMediaInfo loading details ${curMedia?.getIdentifier()} chapter: $includingChapters")
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        curMedia!!.apply {
                            if (includingChapters) ChapterUtils.loadChapters(this, requireContext(), false)
                        }
                    }
                    currentMedia = curMedia
                    val item = (currentMedia as? EpisodeMedia)?.episodeOrFetch()
                    if (item != null) playerDetailsFragment?.setItem(item)
                    updateUi()
                    playerUI?.updateUi(currentMedia)
//                TODO: disable for now
//                if (!includingChapters) loadMediaInfo(true)
                }.invokeOnCompletion { throwable ->
                    if (throwable != null) {
                        Log.e(TAG, Log.getStackTraceString(throwable))
                    }
                    loadItemsRunning = false
                }
            }
        }
    }

    private fun createHandler(): ServiceStatusHandler {
        return object : ServiceStatusHandler(requireActivity()) {
            override fun updatePlayButton(showPlay: Boolean) {
                isShowPlay = showPlay
                playerUI?.butPlay?.setIsShowPlay(showPlay)
//                playerFragment2?.butPlay?.setIsShowPlay(showPlay)
            }
            override fun loadMediaInfo() {
                this@AudioPlayerFragment.loadMediaInfo(false)
                if (!isCollapsed) playerDetailsFragment?.updateInfo()
            }
            override fun onPlaybackEnd() {
                isShowPlay = true
                playerUI?.butPlay?.setIsShowPlay(true)
//                playerFragment2?.butPlay?.setIsShowPlay(true)
                (activity as MainActivity).setPlayerVisible(false)
            }
        }
    }

    private fun updateUi() {
        Logd(TAG, "updateUi called")
        setChapterDividers()
        setupOptionsMenu()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onResume() {
        Logd(TAG, "onResume() isCollapsed: $isCollapsed")
        super.onResume()
        loadMediaInfo(false)
    }

    override fun onStart() {
        Logd(TAG, "onStart() isCollapsed: $isCollapsed")
        super.onStart()
        procFlowEvents()

        val sessionToken = SessionToken(requireContext(), ComponentName(requireContext(), PlaybackService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()
        controllerFuture.addListener({
//            mediaController = controllerFuture.get()
//            Logd(TAG, "controllerFuture.addListener: $mediaController")
        }, MoreExecutors.directExecutor())

        loadMediaInfo(false)
    }

    override fun onStop() {
        Logd(TAG, "onStop()")
        super.onStop()
        MediaController.releaseFuture(controllerFuture)
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

    private fun onPlayEvent(event: FlowEvent.PlayEvent) {
        Logd(TAG, "onPlayEvent ${event.episode.title}")
        val currentitem = event.episode
        if (currentMedia?.getIdentifier() == null || currentitem.media?.getIdentifier() != currentMedia?.getIdentifier()) {
            currentMedia = currentitem.media
            playerDetailsFragment?.setItem(currentitem)
        }
        (activity as MainActivity).setPlayerVisible(true)
    }

    private fun onPlaybackPositionEvent(event: FlowEvent.PlaybackPositionEvent) {
//        Logd(TAG, "onPlayEvent ${event.episode.title}")
        val media = event.media ?: return
        if (currentMedia?.getIdentifier() == null || media.getIdentifier() != currentMedia?.getIdentifier()) {
            currentMedia = media
            playerUI?.updateUi(currentMedia)
            playerDetailsFragment?.setItem(curEpisode!!)
        }
        playerUI?.onPositionUpdate(event)
        if (!isCollapsed) playerDetailsFragment?.onPlaybackPositionEvent(event)
    }

    private var eventSink: Job?     = null
    private fun cancelFlowEvents() {
        Logd(TAG, "cancelFlowEvents")
        eventSink?.cancel()
        eventSink = null
    }
    private fun procFlowEvents() {
        if (eventSink != null) return
        Logd(TAG, "procFlowEvents")
        eventSink = lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.PlaybackServiceEvent -> {
                        if (event.action == FlowEvent.PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN)
                            (activity as MainActivity).bottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
                        playerUI?.onPlaybackServiceChanged(event)
                    }
                    is FlowEvent.PlayEvent -> onPlayEvent(event)
                    is FlowEvent.FavoritesEvent -> onFavoriteEvent(event)
                    is FlowEvent.PlayerErrorEvent -> MediaPlayerErrorDialog.show(activity as Activity, event)
                    is FlowEvent.SleepTimerUpdatedEvent ->  if (event.isCancelled || event.wasJustEnabled()) loadMediaInfo(false)
                    is FlowEvent.PlaybackPositionEvent -> onPlaybackPositionEvent(event)
                    is FlowEvent.SpeedChangedEvent -> playerUI?.updatePlaybackSpeedButton(event)
                    else -> {}
                }
            }
        }
    }

    private fun onFavoriteEvent(event: FlowEvent.FavoritesEvent) {
        if (curEpisode?.id == event.episode.id) EpisodeMenuHandler.onPrepareMenu(toolbar.menu, event.episode)
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (controller == null) return
        when {
            fromUser -> {
                val prog: Float = progress / (seekBar.max.toFloat())
                val converter = TimeSpeedConverter(curSpeedFB)
                val position: Int = converter.convert((prog * curDurationFB).toInt())
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
                    binding.txtvSeek.text = curMedia?.getChapters()?.get(newChapterIndex)?.title ?: ("\n${DurationConverter.getDurationStringLong(position)}")
                } else binding.txtvSeek.text = DurationConverter.getDurationStringLong(position)
            }
            curDurationFB != playbackService?.curDuration -> updateUi()
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
                seekTo((prog * curDurationFB).toInt())
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

    private fun setupOptionsMenu() {
        if (toolbar.menu.size() == 0) toolbar.inflateMenu(R.menu.mediaplayer)

        val isEpisodeMedia = currentMedia is EpisodeMedia
        toolbar.menu?.findItem(R.id.open_feed_item)?.setVisible(isEpisodeMedia)
        val item = if (isEpisodeMedia) (currentMedia as EpisodeMedia).episodeOrFetch() else null
        EpisodeMenuHandler.onPrepareMenu(toolbar.menu, item)

        val mediaType = curMedia?.getMediaType()
        toolbar.menu?.findItem(R.id.show_video)?.setVisible(mediaType == MediaType.VIDEO)

        if (controller != null) {
            toolbar.menu.findItem(R.id.set_sleeptimer_item).setVisible(!isSleepTimerActive())
            toolbar.menu.findItem(R.id.disable_sleeptimer_item).setVisible(isSleepTimerActive())
        }
        (activity as? CastEnabledActivity)?.requestCastButton(toolbar.menu)
    }

    override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        val media: Playable = curMedia ?: return false
        val feedItem = if (media is EpisodeMedia) media.episodeOrFetch() else null
        if (feedItem != null && EpisodeMenuHandler.onMenuItemClicked(this, menuItem.itemId, feedItem)) return true

        val itemId = menuItem.itemId
        when (itemId) {
            R.id.show_home_reader_view -> {
                if (playerDetailsFragment?.showHomeText == true) menuItem.setIcon(R.drawable.ic_home)
                else menuItem.setIcon(R.drawable.outline_home_24)
                playerDetailsFragment?.buildHomeReaderText()
            }
            R.id.show_video -> {
                playPause()
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
        val player = playerUIView1
        player?.alpha = 1 - playerFadeProgress
        player?.visibility = if (playerFadeProgress > 0.99f) View.INVISIBLE else View.VISIBLE
        val toolbarFadeProgress = (max(0.0, min(0.2, (slideOffset - 0.6f).toDouble())) / 0.2f).toFloat()
        toolbar.setAlpha(toolbarFadeProgress)
        toolbar.visibility = if (toolbarFadeProgress < 0.01f) View.INVISIBLE else View.VISIBLE
    }

    class PlayerUIFragment : Fragment(), SeekBar.OnSeekBarChangeListener {
        val TAG = this::class.simpleName ?: "Anonymous"

        private var _binding: PlayerUiFragmentBinding? = null
        private val binding get() = _binding!!
        private lateinit var imgvCover: ImageView
        var butPlay: PlayButton? = null
        private var isControlButtonsSet = false
        private lateinit var txtvLength: TextView
        private lateinit var sbPosition: ChapterSeekBar
        private var prevMedia: Playable? = null
        private var showTimeLeft = false

        @UnstableApi
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            _binding = PlayerUiFragmentBinding.inflate(inflater)
            Logd(TAG, "fragment onCreateView")

            imgvCover = binding.imgvCover
            butPlay = binding.butPlay
            sbPosition = binding.sbPosition
            txtvLength = binding.txtvLength

            setupLengthTextView()
            setupControlButtons()
            binding.butPlaybackSpeed.setOnClickListener {
                VariableSpeedDialog.newInstance(booleanArrayOf(true, true, true), null)?.show(childFragmentManager, null)
            }
            sbPosition.setOnSeekBarChangeListener(this)
            binding.playerUiFragment.setOnClickListener {
                Logd(TAG, "playerUiFragment was clicked")
                val media = curMedia
                if (media != null) {
                    val mediaType = media.getMediaType()
                    if (mediaType == MediaType.AUDIO ||
                            (mediaType == MediaType.VIDEO && (videoPlayMode == VideoMode.AUDIO_ONLY.mode || videoMode == VideoMode.AUDIO_ONLY))) {
                        ensureService()
                        (activity as MainActivity).bottomSheet.setState(BottomSheetBehavior.STATE_EXPANDED)
                    } else {
                        playPause()
//                        controller!!.ensureService()
                        val intent = getPlayerActivityIntent(requireContext(), mediaType)
                        startActivity(intent)
                    }
                }
            }
            return binding.root
        }
        @OptIn(UnstableApi::class) override fun onDestroyView() {
            Logd(TAG, "onDestroyView")
            _binding = null
            super.onDestroyView()
        }
        @UnstableApi
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            butPlay?.setOnClickListener {
                if (controller == null) return@setOnClickListener
                if (curMedia != null) {
                    if (curMedia?.getMediaType() == MediaType.VIDEO && MediaPlayerBase.status != PlayerStatus.PLAYING) {
                        playPause()
                        requireContext().startActivity(getPlayerActivityIntent(requireContext(), curMedia!!.getMediaType()))
                    } else playPause()
                    if (!isControlButtonsSet) {
                        sbPosition.visibility = View.VISIBLE
                        isControlButtonsSet = true
                    }
                }
            }
        }
        @OptIn(UnstableApi::class) private fun setupControlButtons() {
            binding.butRev.setOnClickListener {
                if (controller != null && playbackService?.isServiceReady() == true) {
                    playbackService?.mPlayer?.seekDelta(-UserPreferences.rewindSecs * 1000)
                    sbPosition.visibility = View.VISIBLE
                }
            }
            binding.butRev.setOnLongClickListener {
                SkipPreferenceDialog.showSkipPreference(requireContext(), SkipPreferenceDialog.SkipDirection.SKIP_REWIND, binding.txtvRev)
                true
            }
            butPlay?.setOnLongClickListener {
                if (controller != null && MediaPlayerBase.status == PlayerStatus.PLAYING) {
                    val fallbackSpeed = UserPreferences.fallbackSpeed
                    if (fallbackSpeed > 0.1f) toggleFallbackSpeed(fallbackSpeed)
                }
                true
            }
            binding.butFF.setOnClickListener {
                if (controller != null && playbackService?.isServiceReady() == true) {
                    playbackService?.mPlayer?.seekDelta(UserPreferences.fastForwardSecs * 1000)
                    sbPosition.visibility = View.VISIBLE
                }
            }
            binding.butFF.setOnLongClickListener {
                SkipPreferenceDialog.showSkipPreference(requireContext(), SkipPreferenceDialog.SkipDirection.SKIP_FORWARD, binding.txtvFF)
                true
            }
            binding.butSkip.setOnClickListener {
                if (controller != null && MediaPlayerBase.status == PlayerStatus.PLAYING) {
                    val speedForward = UserPreferences.speedforwardSpeed
                    if (speedForward > 0.1f) speedForward(speedForward)
                }
            }
            binding.butSkip.setOnLongClickListener {
                activity?.sendBroadcast(MediaButtonReceiver.createIntent(requireContext(), KeyEvent.KEYCODE_MEDIA_NEXT))
                true
            }
        }
        private fun ensureService() {
            if (curMedia == null) return
            if (playbackService == null) PlaybackServiceStarter(requireContext(), curMedia!!).start()
        }
        private fun speedForward(speed: Float) {
//            playbackService?.speedForward(speed)
            if (playbackService?.mPlayer == null || playbackService?.isFallbackSpeed == true) return
            if (playbackService?.isSpeedForward == false) {
                playbackService?.normalSpeed = playbackService?.mPlayer!!.getPlaybackSpeed()
                playbackService?.mPlayer!!.setPlaybackParams(speed, isSkipSilence)
            } else playbackService?.mPlayer?.setPlaybackParams(playbackService!!.normalSpeed, isSkipSilence)
            playbackService!!.isSpeedForward = !playbackService!!.isSpeedForward
        }
        @OptIn(UnstableApi::class) private fun setupLengthTextView() {
            showTimeLeft = UserPreferences.shouldShowRemainingTime()
            txtvLength.setOnClickListener(View.OnClickListener {
                if (controller == null) return@OnClickListener
                showTimeLeft = !showTimeLeft
                UserPreferences.setShowRemainTimeSetting(showTimeLeft)
                onPositionUpdate(FlowEvent.PlaybackPositionEvent(curMedia, curPositionFB, curDurationFB))
            })
        }
        fun updatePlaybackSpeedButton(event: FlowEvent.SpeedChangedEvent) {
            val speedStr: String = DecimalFormat("0.00").format(event.newSpeed.toDouble())
            binding.txtvPlaybackSpeed.text = speedStr
            binding.butPlaybackSpeed.setSpeed(event.newSpeed)
        }
        @UnstableApi
        fun onPositionUpdate(event: FlowEvent.PlaybackPositionEvent) {
            if (curMedia?.getIdentifier() != event.media?.getIdentifier() || controller == null || curPositionFB == Playable.INVALID_TIME || curDurationFB == Playable.INVALID_TIME) return
            val converter = TimeSpeedConverter(curSpeedFB)
            val currentPosition: Int = converter.convert(event.position)
            val duration: Int = converter.convert(event.duration)
            val remainingTime: Int = converter.convert(max((event.duration - event.position).toDouble(), 0.0).toInt())
            if (currentPosition == Playable.INVALID_TIME || duration == Playable.INVALID_TIME) {
                Log.w(TAG, "Could not react to position observer update because of invalid time")
                return
            }
            binding.txtvPosition.text = DurationConverter.getDurationStringLong(currentPosition)
            binding.txtvPosition.setContentDescription(getString(R.string.position,
                DurationConverter.getDurationStringLocalized(requireContext(), currentPosition.toLong())))
            val showTimeLeft = UserPreferences.shouldShowRemainingTime()
            if (showTimeLeft) {
                txtvLength.setContentDescription(getString(R.string.remaining_time,
                    DurationConverter.getDurationStringLocalized(requireContext(), remainingTime.toLong())))
                txtvLength.text = (if (remainingTime > 0) "-" else "") + DurationConverter.getDurationStringLong(remainingTime)
            } else {
                txtvLength.setContentDescription(getString(R.string.chapter_duration,
                    DurationConverter.getDurationStringLocalized(requireContext(), duration.toLong())))
                txtvLength.text = DurationConverter.getDurationStringLong(duration)
            }
            if (sbPosition.visibility == View.INVISIBLE && playbackService?.isServiceReady() == true) sbPosition.visibility = View.VISIBLE

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
            binding.txtvRev.text = NumberFormat.getInstance().format(UserPreferences.rewindSecs.toLong())
            binding.txtvFF.text = NumberFormat.getInstance().format(UserPreferences.fastForwardSecs.toLong())
            if (UserPreferences.speedforwardSpeed > 0.1f) binding.txtvSkip.text = NumberFormat.getInstance().format(UserPreferences.speedforwardSpeed)
            else binding.txtvSkip.visibility = View.GONE
            val media = curMedia ?: return
            updatePlaybackSpeedButton(FlowEvent.SpeedChangedEvent(getCurrentPlaybackSpeed(media)))
        }
        @UnstableApi
        override fun onPause() {
            Logd(TAG, "onPause() called")
            super.onPause()
//            controller?.pause()
        }
        @OptIn(UnstableApi::class) override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
        override fun onStartTrackingTouch(seekBar: SeekBar) {}
        @OptIn(UnstableApi::class) override fun onStopTrackingTouch(seekBar: SeekBar) {
            if (playbackService?.isServiceReady() == true) {
                val prog: Float = seekBar.progress / (seekBar.max.toFloat())
                seekTo((prog * curDurationFB).toInt())
            }
        }
        @UnstableApi
        fun updateUi(media: Playable?) {
            Logd(TAG, "updateUi called $media")
            if (media == null) return
            binding.titleView.text = media.getEpisodeTitle()
//            (activity as MainActivity).setPlayerVisible(true)
            onPositionUpdate(FlowEvent.PlaybackPositionEvent(media, media.getPosition(), media.getDuration()))
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
            var controller: ServiceStatusHandler? = null
            fun newInstance(controller_: ServiceStatusHandler) : PlayerUIFragment {
                controller = controller_
                return PlayerUIFragment()
            }
        }
    }

    companion object {
        val TAG = AudioPlayerFragment::class.simpleName ?: "Anonymous"
    }
}
