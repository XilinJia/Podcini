package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.AudioplayerFragmentBinding
import ac.mdiq.podcini.databinding.PlayerDetailsFragmentBinding
import ac.mdiq.podcini.databinding.PlayerUiFragmentBinding
import ac.mdiq.podcini.net.utils.NetworkUtils.fetchHtmlSource
import ac.mdiq.podcini.playback.PlaybackServiceStarter
import ac.mdiq.podcini.playback.ServiceStatusHandler
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.getCurrentPlaybackSpeed
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.playback.cast.CastEnabledActivity
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.curDurationFB
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.curPositionFB
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.curSpeedFB
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isPlayingVideoLocally
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isSleepTimerActive
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playPause
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.seekTo
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.toggleFallbackSpeed
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.isSkipSilence
import ac.mdiq.podcini.preferences.UserPreferences.videoPlayMode
import ac.mdiq.podcini.receiver.MediaButtonReceiver
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.utils.ChapterUtils
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.storage.utils.ImageResourceUtils
import ac.mdiq.podcini.storage.utils.TimeSpeedConverter
import ac.mdiq.podcini.ui.actions.handler.EpisodeMenuHandler
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.Companion.videoMode
import ac.mdiq.podcini.ui.activity.starter.VideoPlayerActivityStarter
import ac.mdiq.podcini.ui.dialog.MediaPlayerErrorDialog
import ac.mdiq.podcini.ui.dialog.SkipPreferenceDialog
import ac.mdiq.podcini.ui.dialog.SleepTimerDialog
import ac.mdiq.podcini.ui.dialog.VariableSpeedDialog
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.view.ChapterSeekBar
import ac.mdiq.podcini.ui.view.PlayButton
import ac.mdiq.podcini.ui.view.ShownotesWebView
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter
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
import android.view.*
import android.view.View.OnLayoutChangeListener
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import coil.imageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.snackbar.Snackbar
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import org.apache.commons.lang3.StringUtils
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
        (activity as MainActivity).setPlayerVisible(false)
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
//        MediaController.releaseFuture(controllerFuture)
        super.onDestroyView()
    }

    fun onExpanded() {
        Logd(TAG, "onExpanded()")
        initDetailedView()
//        the function can also be called from MainActivity when a select menu pops up and closes
        if (isCollapsed) {
            isCollapsed = false
            playerUI = playerUI2
            if (currentMedia != null) playerUI?.updateUi(currentMedia!!)
            playerUI?.butPlay?.setIsShowPlay(isShowPlay)
            playerDetailsFragment?.updateInfo()
        }
    }

    fun onCollaped() {
        Logd(TAG, "onCollaped()")
        isCollapsed = true
        playerUI = playerUI1
        if (currentMedia != null) playerUI?.updateUi(currentMedia!!)
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
                    if (currentMedia != null) playerUI?.updateUi(currentMedia!!)
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

//        val sessionToken = SessionToken(requireContext(), ComponentName(requireContext(), PlaybackService::class.java))
//        controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()
//        controllerFuture.addListener({
////            mediaController = controllerFuture.get()
////            Logd(TAG, "controllerFuture.addListener: $mediaController")
//        }, MoreExecutors.directExecutor())

        loadMediaInfo(false)
    }

    override fun onStop() {
        Logd(TAG, "onStop()")
        super.onStop()
//        MediaController.releaseFuture(controllerFuture)
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
            playerUI?.updateUi(currentMedia!!)
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
        val notAudioOnly = (curMedia as? EpisodeMedia)?.episode?.feed?.preferences?.videoModePolicy != VideoMode.AUDIO_ONLY
        toolbar.menu?.findItem(R.id.show_video)?.setVisible(mediaType == MediaType.VIDEO && notAudioOnly)

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
                VideoPlayerActivityStarter(requireContext()).start()
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
                    if (mediaType == MediaType.AUDIO || videoPlayMode == VideoMode.AUDIO_ONLY.code || videoMode == VideoMode.AUDIO_ONLY
                            || (media is EpisodeMedia && media.episode?.feed?.preferences?.videoModePolicy == VideoMode.AUDIO_ONLY)) {
                        Logd(TAG, "popping as audio episode")
                        ensureService()
                        (activity as MainActivity).bottomSheet.setState(BottomSheetBehavior.STATE_EXPANDED)
                    } else {
                        Logd(TAG, "popping video activity")
//                        playPause()
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
                    val media = curMedia!!
                    if (media.getMediaType() == MediaType.VIDEO && MediaPlayerBase.status != PlayerStatus.PLAYING &&
                            (media is EpisodeMedia && media.episode?.feed?.preferences?.videoModePolicy != VideoMode.AUDIO_ONLY)) {
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
                FlowEvent.PlaybackServiceEvent.Action.SERVICE_STARTED -> {
                    if (curMedia != null) (activity as MainActivity).setPlayerVisible(true)
                }
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
        fun updateUi(media: Playable) {
            Logd(TAG, "updateUi called $media")
//            if (media == null) return
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
            if (isPlayingVideoLocally && (curMedia as? EpisodeMedia)?.episode?.feed?.preferences?.videoModePolicy != VideoMode.AUDIO_ONLY) {
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
            _binding = PlayerDetailsFragmentBinding.inflate(inflater, container, false)

            val colorFilter: ColorFilter? = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(binding.txtvPodcastTitle.currentTextColor, BlendModeCompat.SRC_IN)
            binding.butNextChapter.colorFilter = colorFilter
            binding.butPrevChapter.colorFilter = colorFilter
            binding.chapterButton.setOnClickListener { ChaptersFragment().show(childFragmentManager, ChaptersFragment.TAG) }
            binding.butPrevChapter.setOnClickListener { seekToPrevChapter() }
            binding.butNextChapter.setOnClickListener { seekToNextChapter() }

            Logd(TAG, "fragment onCreateView")
            shownoteView = binding.webview
            shownoteView.setTimecodeSelectedListener { time: Int -> seekTo(time) }
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

//    override fun onStart() {
//        Logd(TAG, "onStart()")
//        super.onStart()
//    }

//    override fun onStop() {
//        Logd(TAG, "onStop()")
//        super.onStop()
//    }

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
                            currentItem = episodeMedia.episodeOrFetch()
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
                            currentItem = upsertBlk(currentItem!!) {
                                it.setTranscriptIfLonger(readerhtml)
                            }
                            homeText = currentItem!!.transcript
//                        persistEpisode(currentItem)
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
            val pubDateStr = MiscFormatter.formatAbbrev(context, media.getPubDate())
            binding.txtvPodcastTitle.text = StringUtils.stripToEmpty(media.getFeedTitle())
            if (media is EpisodeMedia) {
                if (currentItem?.feedId != null) {
                    val openFeed: Intent = MainActivity.getIntentToOpenFeed(requireContext(), currentItem!!.feedId!!)
                    binding.txtvPodcastTitle.setOnClickListener { startActivity(openFeed) }
                }
            } else binding.txtvPodcastTitle.setOnClickListener(null)

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
                    val item_ = (playable as EpisodeMedia).episodeOrFetch()
                    // If an item has chapters but they are not loaded yet, still display the button.
                    chapterControlVisible = !item_?.chapters.isNullOrEmpty()
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
                (curPositionFB - 10000 * curSpeedFB) < curr.start -> {
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
            val editor = prefs?.edit() ?: return
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

        fun onPlaybackPositionEvent(event: FlowEvent.PlaybackPositionEvent) {
            if (playable?.getIdentifier() != event.media?.getIdentifier()) return
            val newChapterIndex: Int = ChapterUtils.getCurrentChapterIndex(playable, event.position)
            if (newChapterIndex >= 0 && newChapterIndex != displayedChapterIndex) refreshChapterData(newChapterIndex)
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

    companion object {
        val TAG = AudioPlayerFragment::class.simpleName ?: "Anonymous"
    }
}
