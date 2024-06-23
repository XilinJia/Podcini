package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.VideoEpisodeFragmentBinding
import ac.mdiq.podcini.playback.PlaybackController
import ac.mdiq.podcini.playback.PlaybackController.Companion.curSpeedMultiplier
import ac.mdiq.podcini.playback.PlaybackController.Companion.duration
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.PlaybackController.Companion.isPlayingVideoLocally
import ac.mdiq.podcini.playback.PlaybackController.Companion.playbackService
import ac.mdiq.podcini.playback.PlaybackController.Companion.position
import ac.mdiq.podcini.playback.PlaybackController.Companion.seekTo
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.preferences.UserPreferences.fastForwardSecs
import ac.mdiq.podcini.preferences.UserPreferences.rewindSecs
import ac.mdiq.podcini.preferences.UserPreferences.setShowRemainTimeSetting
import ac.mdiq.podcini.preferences.UserPreferences.shouldShowRemainingTime
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.ui.activity.VideoplayerActivity
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.Companion.videoMode
import ac.mdiq.podcini.ui.activity.starter.MainActivityStarter
import ac.mdiq.podcini.ui.dialog.SkipPreferenceDialog
import ac.mdiq.podcini.ui.utils.PictureInPictureUtil
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.view.ShownotesWebView
import ac.mdiq.podcini.util.Converter.getDurationStringLong
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.TimeSpeedConverter
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Pair
import android.view.*
import android.view.animation.*
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.invalidateOptionsMenu
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class VideoEpisodeFragment : Fragment(), OnSeekBarChangeListener {
    private var _binding: VideoEpisodeFragmentBinding? = null
    private val binding get() = _binding!!
    private lateinit var root: ViewGroup

    /**
     * True if video controls are currently visible.
     */
    private var videoControlsShowing = true
    private var videoSurfaceCreated = false
    private var lastScreenTap: Long = 0
    private val videoControlsHider = Handler(Looper.getMainLooper())
    private var showTimeLeft = false
    private var prog = 0f

    private var itemsLoaded = false
    private var item: Episode? = null
    private var webviewData: String? = null
    private lateinit var webvDescription: ShownotesWebView

    var destroyingDueToReload = false
    var controller: PlaybackController? = null
    var isFavorite = false

    @OptIn(UnstableApi::class) override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = VideoEpisodeFragmentBinding.inflate(LayoutInflater.from(requireContext()))
        root = binding.root

        controller = newPlaybackController()
        controller!!.init()
//        loadMediaInfo()

        setupView()

        return root
    }

    @OptIn(UnstableApi::class) private fun newPlaybackController(): PlaybackController {
        return object : PlaybackController(requireActivity()) {
            override fun updatePlayButtonShowsPlay(showPlay: Boolean) {
                Logd(TAG, "updatePlayButtonShowsPlay called")
                binding.playButton.setIsShowPlay(showPlay)
                if (showPlay) (activity as AppCompatActivity).window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else {
                    (activity as AppCompatActivity).window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    setupVideoAspectRatio()
                    if (videoSurfaceCreated && controller != null) {
                        Logd(TAG, "Videosurface already created, setting videosurface now")
                        setVideoSurface(binding.videoView.holder)
                    }
                }
            }

            override fun loadMediaInfo() {
                this@VideoEpisodeFragment.loadMediaInfo()
            }

            override fun onPlaybackEnd() {
                activity?.finish()
            }
        }
    }

    @UnstableApi
    override fun onStart() {
        super.onStart()
        onPositionObserverUpdate()
        procFlowEvents()
    }

    @UnstableApi
    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
        if (!PictureInPictureUtil.isInPictureInPictureMode(requireActivity())) videoControlsHider.removeCallbacks(hideVideoControls)

        // Controller released; we will not receive buffering updates
        binding.progressBar.visibility = View.GONE
    }

    @UnstableApi
    override fun onPause() {
        if (!PictureInPictureUtil.isInPictureInPictureMode(requireActivity())) {
            if (MediaPlayerBase.status == PlayerStatus.PLAYING) controller!!.pause()
        }
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        root.removeView(webvDescription)
        webvDescription.destroy()
        _binding = null
        controller?.release()
        controller = null // prevent leak
//        scope.cancel()
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
                    is FlowEvent.BufferUpdateEvent -> bufferUpdate(event)
                    is FlowEvent.PlaybackPositionEvent -> onPositionObserverUpdate()
                    else -> {}
                }
            }
        }
    }

    private fun bufferUpdate(event: FlowEvent.BufferUpdateEvent) {
        when {
            event.hasStarted() -> binding.progressBar.visibility = View.VISIBLE
            event.hasEnded() -> binding.progressBar.visibility = View.INVISIBLE
            else -> binding.sbPosition.secondaryProgress = (event.progress * binding.sbPosition.max).toInt()
        }
    }

    private fun setupVideoAspectRatio() {
        if (videoSurfaceCreated && controller != null) {
            if (videoSize != null && videoSize!!.first > 0 && videoSize!!.second > 0) {
                Logd(TAG, "Width,height of video: ${videoSize!!.first}, ${videoSize!!.second}")
                val videoWidth = resources.displayMetrics.widthPixels
                val videoHeight = (videoWidth.toFloat() / videoSize!!.first * videoSize!!.second).toInt()
                Logd(TAG, "Width,height of video: $videoWidth, $videoHeight")
                binding.videoView.setVideoSize(videoWidth, videoHeight)
//                binding.videoView.setVideoSize(videoSize.first, videoSize.second)
//                binding.videoView.setVideoSize(-1, -1)
            } else {
                Log.e(TAG, "Could not determine video size")
                val videoWidth = resources.displayMetrics.widthPixels
                val videoHeight = (videoWidth.toFloat() / 16 * 9).toInt()
                Logd(TAG, "Width,height of video: $videoWidth, $videoHeight")
                binding.videoView.setVideoSize(videoWidth, videoHeight)
            }
        }
    }

    @OptIn(UnstableApi::class) private fun loadMediaInfo() {
        Logd(TAG, "loadMediaInfo called")
        if (curMedia == null) return

        if (MediaPlayerBase.status == PlayerStatus.PLAYING && !isPlayingVideoLocally) {
            Logd(TAG, "Closing, no longer video")
            destroyingDueToReload = true
            activity?.finish()
            MainActivityStarter(requireContext()).withOpenPlayer().start()
            return
        }
        showTimeLeft = shouldShowRemainingTime()
        onPositionObserverUpdate()
        load()
        val media = curMedia
        if (media != null) {
            (activity as AppCompatActivity).supportActionBar!!.subtitle = media.getEpisodeTitle()
            (activity as AppCompatActivity).supportActionBar!!.title = media.getFeedTitle()
        }
    }

    @UnstableApi private fun load() {
        Logd(TAG, "load() called")
        lifecycleScope.launch {
            try {
                item = withContext(Dispatchers.IO) {
                    loadInBackground()
                }
                withContext(Dispatchers.Main) {
                    Logd(TAG, "load() item ${item?.id}")
                    if (item != null) {
                        val isFav = item!!.isFavorite
                        if (isFavorite != isFav) {
                            isFavorite = isFav
                            invalidateOptionsMenu(requireActivity())
                        }
                    }
                    onFragmentLoaded()
                    itemsLoaded = true
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }

    }

    private fun loadInBackground(): Episode? {
        val feedItem = (curMedia as? EpisodeMedia)?.episode
        if (feedItem != null) {
            val duration = feedItem.media?.getDuration()?: Int.MAX_VALUE
            webviewData = ShownotesCleaner(requireContext()).processShownotes(feedItem.description?:"", duration)
        }
        return feedItem
    }

    @UnstableApi private fun onFragmentLoaded() {
        if (webviewData != null && !itemsLoaded)
            webvDescription.loadDataWithBaseURL("https://127.0.0.1", webviewData!!, "text/html", "utf-8", "about:blank")
    }

    @UnstableApi
    private fun setupView() {
        showTimeLeft = shouldShowRemainingTime()
        Logd(TAG, "setupView showTimeLeft: $showTimeLeft")

        binding.durationLabel.setOnClickListener {
            showTimeLeft = !showTimeLeft
            val media = curMedia ?: return@setOnClickListener

            val converter = TimeSpeedConverter(curSpeedMultiplier)
            val length: String
            if (showTimeLeft) {
                val remainingTime = converter.convert(media.getDuration() - media.getPosition())
                length = "-" + getDurationStringLong(remainingTime)
            } else {
                val duration = converter.convert(media.getDuration())
                length = getDurationStringLong(duration)
            }
            binding.durationLabel.text = length

            setShowRemainTimeSetting(showTimeLeft)
            Logd("timeleft on click", if (showTimeLeft) "true" else "false")
        }

        binding.sbPosition.setOnSeekBarChangeListener(this)
        binding.rewindButton.setOnClickListener { onRewind() }
        binding.rewindButton.setOnLongClickListener {
            SkipPreferenceDialog.showSkipPreference(requireContext(), SkipPreferenceDialog.SkipDirection.SKIP_REWIND, null)
            true
        }
        binding.playButton.setIsVideoScreen(true)
        binding.playButton.setOnClickListener { onPlayPause() }
        binding.fastForwardButton.setOnClickListener { onFastForward() }
        binding.fastForwardButton.setOnLongClickListener {
            SkipPreferenceDialog.showSkipPreference(requireContext(), SkipPreferenceDialog.SkipDirection.SKIP_FORWARD, null)
            false
        }
        // To suppress touches directly below the slider
        binding.bottomControlsContainer.setOnTouchListener { _: View?, _: MotionEvent? -> true }
        binding.videoView.holder.addCallback(surfaceHolderCallback)
        binding.bottomControlsContainer.fitsSystemWindows = true
//        binding.videoView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        setupVideoControlsToggler()
//        (activity as AppCompatActivity).window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        binding.videoPlayerContainer.setOnTouchListener(onVideoviewTouched)
        binding.videoPlayerContainer.viewTreeObserver.addOnGlobalLayoutListener {
            binding.videoView.setAvailableSize(binding.videoPlayerContainer.width.toFloat(), binding.videoPlayerContainer.height.toFloat())
        }

        webvDescription = binding.webvDescription
//        webvDescription.setTimecodeSelectedListener { time: Int? ->
//            val cMedia = getMedia
//            if (item?.media?.getIdentifier() == cMedia?.getIdentifier()) {
//                seekTo(time ?: 0)
//            } else {
//                (activity as MainActivity).showSnackbarAbovePlayer(R.string.play_this_to_seek_position,
//                    Snackbar.LENGTH_LONG)
//            }
//        }
//        registerForContextMenu(webvDescription)
//        webvDescription.visibility = View.GONE

        binding.toggleViews.setOnClickListener {
            (activity as? VideoplayerActivity)?.toggleViews()
        }
        binding.audioOnly.setOnClickListener {
            (activity as? VideoplayerActivity)?.switchToAudioOnly = true
            (activity as? VideoplayerActivity)?.finish()
        }

    }

    private val onVideoviewTouched = View.OnTouchListener { v: View, event: MotionEvent ->
        if (event.action != MotionEvent.ACTION_DOWN) return@OnTouchListener false

        if (PictureInPictureUtil.isInPictureInPictureMode(requireActivity())) return@OnTouchListener true

        videoControlsHider.removeCallbacks(hideVideoControls)

        if (System.currentTimeMillis() - lastScreenTap < 300) {
            if (event.x > v.measuredWidth / 2.0f) {
                onFastForward()
                showSkipAnimation(true)
            } else {
                onRewind()
                showSkipAnimation(false)
            }
            if (videoControlsShowing) {
                hideVideoControls(false)
                if (videoMode == VideoplayerActivity.VideoMode.FULL_SCREEN_VIEW) (activity as AppCompatActivity).supportActionBar?.hide()
                videoControlsShowing = false
            }
            return@OnTouchListener true
        }

        toggleVideoControlsVisibility()
        if (videoControlsShowing) setupVideoControlsToggler()

        lastScreenTap = System.currentTimeMillis()
        true
    }

    fun toggleVideoControlsVisibility() {
        if (videoControlsShowing) {
            hideVideoControls(true)
            if (videoMode == VideoplayerActivity.VideoMode.FULL_SCREEN_VIEW) (activity as AppCompatActivity).supportActionBar?.hide()
        } else {
            showVideoControls()
            (activity as AppCompatActivity).supportActionBar?.show()
        }
        videoControlsShowing = !videoControlsShowing
    }

    fun showSkipAnimation(isForward: Boolean) {
        val skipAnimation = AnimationSet(true)
        skipAnimation.addAnimation(ScaleAnimation(1f, 2f, 1f, 2f,
            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f))
        skipAnimation.addAnimation(AlphaAnimation(1f, 0f))
        skipAnimation.fillAfter = false
        skipAnimation.duration = 800

        val params = binding.skipAnimationImage.layoutParams as FrameLayout.LayoutParams
        if (isForward) {
            binding.skipAnimationImage.setImageResource(R.drawable.ic_fast_forward_video_white)
            params.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        } else {
            binding.skipAnimationImage.setImageResource(R.drawable.ic_fast_rewind_video_white)
            params.gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
        }

        binding.skipAnimationImage.visibility = View.VISIBLE
        binding.skipAnimationImage.layoutParams = params
        binding.skipAnimationImage.startAnimation(skipAnimation)
        skipAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}

            override fun onAnimationEnd(animation: Animation) {
                binding.skipAnimationImage.visibility = View.GONE
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
    }

    private val surfaceHolderCallback: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            holder.setFixedSize(width, height)
        }

        @UnstableApi
        override fun surfaceCreated(holder: SurfaceHolder) {
            Logd(TAG, "Videoview holder created")
            videoSurfaceCreated = true
            if (MediaPlayerBase.status == PlayerStatus.PLAYING) setVideoSurface(holder)
            setupVideoAspectRatio()
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Logd(TAG, "Videosurface was destroyed")
            videoSurfaceCreated = false
            if (controller != null && !destroyingDueToReload && !(activity as VideoplayerActivity).switchToAudioOnly)
                notifyVideoSurfaceAbandoned()
        }
    }

    fun notifyVideoSurfaceAbandoned() {
//            playbackService?.notifyVideoSurfaceAbandoned()
        playbackService?.mediaPlayer?.pause(abandonFocus = true, reinit = false)
        playbackService?.mediaPlayer?.resetVideoSurface()
    }

    fun setVideoSurface(holder: SurfaceHolder?) {
        playbackService?.mediaPlayer?.setVideoSurface(holder)
    }

    @UnstableApi
    fun onRewind() {
        if (controller == null) return

        val curr = position
        seekTo(curr - rewindSecs * 1000)
        setupVideoControlsToggler()
    }

    @UnstableApi
    fun onPlayPause() {
        if (controller == null) return

        controller!!.playPause()
        setupVideoControlsToggler()
    }

    @UnstableApi
    fun onFastForward() {
        if (controller == null) return

        val curr = position
        seekTo(curr + fastForwardSecs * 1000)
        setupVideoControlsToggler()
    }

    private fun setupVideoControlsToggler() {
        videoControlsHider.removeCallbacks(hideVideoControls)
        videoControlsHider.postDelayed(hideVideoControls, 2500)
    }

    private val hideVideoControls = Runnable {
        if (videoControlsShowing) {
            Logd(TAG, "Hiding video controls")
            hideVideoControls(true)
            if (videoMode == VideoplayerActivity.VideoMode.FULL_SCREEN_VIEW) (activity as? AppCompatActivity)?.supportActionBar?.hide()
            videoControlsShowing = false
        }
    }

    private fun showVideoControls() {
        binding.bottomControlsContainer.visibility = View.VISIBLE
        binding.controlsContainer.visibility = View.VISIBLE
        val animation = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in)
        if (animation != null) {
            binding.bottomControlsContainer.startAnimation(animation)
            binding.controlsContainer.startAnimation(animation)
        }
        (activity as AppCompatActivity).window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
//        binding.videoView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        binding.bottomControlsContainer.fitsSystemWindows = true
    }

    fun hideVideoControls(showAnimation: Boolean) {
        if (showAnimation) {
            val animation = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_out)
            if (animation != null) {
                binding.bottomControlsContainer.startAnimation(animation)
                binding.controlsContainer.startAnimation(animation)
            }
        }
        (activity as AppCompatActivity).window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
//        (activity as AppCompatActivity).window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
//                or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        binding.bottomControlsContainer.fitsSystemWindows = true

        binding.bottomControlsContainer.visibility = View.GONE
        binding.controlsContainer.visibility = View.GONE
    }

    private fun onPositionObserverUpdate() {
        if (controller == null) return

        val converter = TimeSpeedConverter(curSpeedMultiplier)
        val currentPosition = converter.convert(position)
        val duration_ = converter.convert(duration)
        val remainingTime = converter.convert(duration - position)
        //        Log.d(TAG, "currentPosition " + Converter.getDurationStringLong(currentPosition));
        if (currentPosition == Playable.INVALID_TIME || duration_ == Playable.INVALID_TIME) {
            Log.w(TAG, "Could not react to position observer update because of invalid time")
            return
        }
        binding.positionLabel.text = getDurationStringLong(currentPosition)
        if (showTimeLeft) binding.durationLabel.text = "-" + getDurationStringLong(remainingTime)
        else binding.durationLabel.text = getDurationStringLong(duration_)

        updateProgressbarPosition(currentPosition, duration_)
    }

    private fun updateProgressbarPosition(position: Int, duration: Int) {
        Logd(TAG, "updateProgressbarPosition ($position, $duration)")
        val progress = (position.toFloat()) / duration
        binding.sbPosition.progress = (progress * binding.sbPosition.max).toInt()
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (controller == null) return

        if (fromUser) {
            prog = progress / (seekBar.max.toFloat())
            val converter = TimeSpeedConverter(curSpeedMultiplier)
            val position = converter.convert((prog * duration).toInt())
            binding.seekPositionLabel.text = getDurationStringLong(position)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        binding.seekCardView.scaleX = .8f
        binding.seekCardView.scaleY = .8f
        binding.seekCardView.animate()
            .setInterpolator(FastOutSlowInInterpolator())
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(200)
            .start()
        videoControlsHider.removeCallbacks(hideVideoControls)
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        seekTo((prog * duration).toInt())

        binding.seekCardView.scaleX = 1f
        binding.seekCardView.scaleY = 1f
        binding.seekCardView.animate()
            .setInterpolator(FastOutSlowInInterpolator())
            .alpha(0f).scaleX(.8f).scaleY(.8f)
            .setDuration(200)
            .start()
        setupVideoControlsToggler()
    }

    companion object {
        val TAG: String = VideoEpisodeFragment::class.simpleName ?: "Anonymous"

        val videoSize: Pair<Int, Int>?
            get() = playbackService?.mediaPlayer?.getVideoSize()
    }
}