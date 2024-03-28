package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.R
import ac.mdiq.podcini.service.playback.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.service.playback.PlaybackService.Companion.isCasting
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.util.Converter.getDurationStringLong
import ac.mdiq.podcini.util.FeedItemUtil.getLinkWithFallback
import ac.mdiq.podcini.util.IntentUtils.openInBrowser
import ac.mdiq.podcini.util.ShareUtils.hasLinkToShare
import ac.mdiq.podcini.util.TimeSpeedConverter
import ac.mdiq.podcini.ui.utils.PictureInPictureUtil
import ac.mdiq.podcini.playback.PlaybackController
import ac.mdiq.podcini.databinding.VideoplayerActivityBinding
import ac.mdiq.podcini.ui.dialog.*
import ac.mdiq.podcini.playback.event.BufferUpdateEvent
import ac.mdiq.podcini.playback.event.PlaybackPositionEvent
import ac.mdiq.podcini.playback.event.PlaybackServiceEvent
import ac.mdiq.podcini.playback.event.SleepTimerUpdatedEvent
import ac.mdiq.podcini.ui.fragment.ChaptersFragment
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.cast.CastEnabledActivity
import ac.mdiq.podcini.preferences.UserPreferences.fastForwardSecs
import ac.mdiq.podcini.preferences.UserPreferences.rewindSecs
import ac.mdiq.podcini.preferences.UserPreferences.setShowRemainTimeSetting
import ac.mdiq.podcini.preferences.UserPreferences.shouldShowRemainingTime
import ac.mdiq.podcini.ui.appstartintent.MainActivityStarter
import ac.mdiq.podcini.util.event.MessageEvent
import ac.mdiq.podcini.util.event.PlayerErrorEvent
import android.content.DialogInterface
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.view.View.OnTouchListener
import android.view.animation.*
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Activity for playing video files.
 */
@UnstableApi
class VideoplayerActivity : CastEnabledActivity(), OnSeekBarChangeListener {

    private var _binding: VideoplayerActivityBinding? = null
    private val binding get() = _binding!!

    /**
     * True if video controls are currently visible.
     */
    private var videoControlsShowing = true
    private var videoSurfaceCreated = false
    private var destroyingDueToReload = false
    private var lastScreenTap: Long = 0
    private val videoControlsHider = Handler(Looper.getMainLooper())
    private var controller: PlaybackController? = null
    private var showTimeLeft = false
    private var isFavorite = false
    private var switchToAudioOnly = false
    private var disposable: Disposable? = null
    private var prog = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        // has to be called before setting layout content
        supportRequestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY)
        setTheme(R.style.Theme_Podcini_VideoPlayer)
        super.onCreate(savedInstanceState)

        window.setFormat(PixelFormat.TRANSPARENT)
        _binding = VideoplayerActivityBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        setupView()
        supportActionBar?.setBackgroundDrawable(ColorDrawable(-0x80000000))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        controller = newPlaybackController()
        controller!!.init()
        loadMediaInfo()
    }

    @UnstableApi
    override fun onResume() {
        super.onResume()
        switchToAudioOnly = false
        if (isCasting) {
            val intent = getPlayerActivityIntent(this)
            if (intent.component?.className != VideoplayerActivity::class.java.name) {
                destroyingDueToReload = true
                finish()
                startActivity(intent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        controller?.release()
        controller = null // prevent leak
        disposable?.dispose()
    }

    @UnstableApi
    override fun onStop() {
        EventBus.getDefault().unregister(this)
        super.onStop()
        if (!PictureInPictureUtil.isInPictureInPictureMode(this)) {
            videoControlsHider.removeCallbacks(hideVideoControls)
        }
        // Controller released; we will not receive buffering updates
        binding.progressBar.visibility = View.GONE
    }

    public override fun onUserLeaveHint() {
        if (!PictureInPictureUtil.isInPictureInPictureMode(this)) {
            compatEnterPictureInPicture()
        }
    }

    @UnstableApi
    override fun onStart() {
        super.onStart()
        onPositionObserverUpdate()
        EventBus.getDefault().register(this)
    }

    @UnstableApi
    override fun onPause() {
        if (!PictureInPictureUtil.isInPictureInPictureMode(this)) {
            if (controller?.status == PlayerStatus.PLAYING) {
                controller!!.pause()
            }
        }
        super.onPause()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Glide.get(this).trimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Glide.get(this).clearMemory()
    }

    @UnstableApi
    private fun newPlaybackController(): PlaybackController {
        return object : PlaybackController(this@VideoplayerActivity) {
            override fun updatePlayButtonShowsPlay(showPlay: Boolean) {
                binding.playButton.setIsShowPlay(showPlay)
                if (showPlay) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    setupVideoAspectRatio()
                    if (videoSurfaceCreated && controller != null) {
                        Log.d(TAG, "Videosurface already created, setting videosurface now")
                        controller!!.setVideoSurface(binding.videoView.holder)
                    }
                }
            }

            override fun loadMediaInfo() {
                this@VideoplayerActivity.loadMediaInfo()
            }

            override fun onPlaybackEnd() {
                finish()
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun bufferUpdate(event: BufferUpdateEvent) {
        when {
            event.hasStarted() -> {
                binding.progressBar.visibility = View.VISIBLE
            }
            event.hasEnded() -> {
                binding.progressBar.visibility = View.INVISIBLE
            }
            else -> {
                binding.sbPosition.secondaryProgress = (event.progress * binding.sbPosition.max).toInt()
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @Suppress("unused")
    fun sleepTimerUpdate(event: SleepTimerUpdatedEvent) {
        if (event.isCancelled || event.wasJustEnabled()) {
            supportInvalidateOptionsMenu()
        }
    }

    @UnstableApi
    private fun loadMediaInfo() {
        Log.d(TAG, "loadMediaInfo()")
        if (controller?.getMedia() == null) return

        if (controller!!.status == PlayerStatus.PLAYING && !controller!!.isPlayingVideoLocally) {
            Log.d(TAG, "Closing, no longer video")
            destroyingDueToReload = true
            finish()
            MainActivityStarter(this).withOpenPlayer().start()
            return
        }
        showTimeLeft = shouldShowRemainingTime()
        onPositionObserverUpdate()
        checkFavorite()
        val media = controller!!.getMedia()
        if (media != null) {
            supportActionBar!!.subtitle = media.getEpisodeTitle()
            supportActionBar!!.title = media.getFeedTitle()
        }
    }

    @UnstableApi
    private fun setupView() {
        showTimeLeft = shouldShowRemainingTime()
        Log.d("timeleft", if (showTimeLeft) "true" else "false")
        binding.durationLabel.setOnClickListener {
            showTimeLeft = !showTimeLeft
            val media = controller?.getMedia() ?: return@setOnClickListener

            val converter = TimeSpeedConverter(controller!!.currentPlaybackSpeedMultiplier)
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
            Log.d("timeleft on click", if (showTimeLeft) "true" else "false")
        }

        binding.sbPosition.setOnSeekBarChangeListener(this)
        binding.rewindButton.setOnClickListener { onRewind() }
        binding.rewindButton.setOnLongClickListener {
            SkipPreferenceDialog.showSkipPreference(this@VideoplayerActivity,
                SkipPreferenceDialog.SkipDirection.SKIP_REWIND, null)
            true
        }
        binding.playButton.setIsVideoScreen(true)
        binding.playButton.setOnClickListener { onPlayPause() }
        binding.fastForwardButton.setOnClickListener { onFastForward() }
        binding.fastForwardButton.setOnLongClickListener {
            SkipPreferenceDialog.showSkipPreference(this@VideoplayerActivity,
                SkipPreferenceDialog.SkipDirection.SKIP_FORWARD, null)
            false
        }
        // To suppress touches directly below the slider
        binding.bottomControlsContainer.setOnTouchListener { _: View?, _: MotionEvent? -> true }
        binding.bottomControlsContainer.fitsSystemWindows = true
        binding.videoView.holder.addCallback(surfaceHolderCallback)
        binding.videoView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        setupVideoControlsToggler()
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN)

        binding.videoPlayerContainer.setOnTouchListener(onVideoviewTouched)
        binding.videoPlayerContainer.viewTreeObserver.addOnGlobalLayoutListener {
            binding.videoView.setAvailableSize(
                binding.videoPlayerContainer.width.toFloat(), binding.videoPlayerContainer.height.toFloat())
        }
    }

    private val hideVideoControls = Runnable {
        if (videoControlsShowing) {
            Log.d(TAG, "Hiding video controls")
            supportActionBar?.hide()
            hideVideoControls(true)
            videoControlsShowing = false
        }
    }

    private val onVideoviewTouched = OnTouchListener { v: View, event: MotionEvent ->
        if (event.action != MotionEvent.ACTION_DOWN) {
            return@OnTouchListener false
        }
        if (PictureInPictureUtil.isInPictureInPictureMode(this)) {
            return@OnTouchListener true
        }
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
                supportActionBar?.hide()
                hideVideoControls(false)
                videoControlsShowing = false
            }
            return@OnTouchListener true
        }

        toggleVideoControlsVisibility()
        if (videoControlsShowing) {
            setupVideoControlsToggler()
        }

        lastScreenTap = System.currentTimeMillis()
        true
    }

    private fun showSkipAnimation(isForward: Boolean) {
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
            override fun onAnimationStart(animation: Animation) {
            }

            override fun onAnimationEnd(animation: Animation) {
                binding.skipAnimationImage.visibility = View.GONE
            }

            override fun onAnimationRepeat(animation: Animation) {
            }
        })
    }

    private fun setupVideoControlsToggler() {
        videoControlsHider.removeCallbacks(hideVideoControls)
        videoControlsHider.postDelayed(hideVideoControls, 2500)
    }

    @UnstableApi
    private fun setupVideoAspectRatio() {
        if (videoSurfaceCreated && controller != null) {
            val videoSize = controller!!.videoSize
            if (videoSize != null && videoSize.first > 0 && videoSize.second > 0) {
                Log.d(TAG, "Width,height of video: " + videoSize.first + ", " + videoSize.second)
                binding.videoView.setVideoSize(videoSize.first, videoSize.second)
            } else {
                Log.e(TAG, "Could not determine video size")
            }
        }
    }

    private fun toggleVideoControlsVisibility() {
        if (videoControlsShowing) {
            supportActionBar?.hide()
            hideVideoControls(true)
        } else {
            supportActionBar?.show()
            showVideoControls()
        }
        videoControlsShowing = !videoControlsShowing
    }

    @UnstableApi
    fun onRewind() {
        if (controller == null) return

        val curr = controller!!.position
        controller!!.seekTo(curr - rewindSecs * 1000)
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

        val curr = controller!!.position
        controller!!.seekTo(curr + fastForwardSecs * 1000)
        setupVideoControlsToggler()
    }

    private val surfaceHolderCallback: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            holder.setFixedSize(width, height)
        }

        @UnstableApi
        override fun surfaceCreated(holder: SurfaceHolder) {
            Log.d(TAG, "Videoview holder created")
            videoSurfaceCreated = true
            if (controller?.status == PlayerStatus.PLAYING) {
                controller!!.setVideoSurface(holder)
            }
            setupVideoAspectRatio()
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Log.d(TAG, "Videosurface was destroyed")
            videoSurfaceCreated = false
            if (controller != null && !destroyingDueToReload && !switchToAudioOnly) {
                controller!!.notifyVideoSurfaceAbandoned()
            }
        }
    }

    private fun showVideoControls() {
        binding.bottomControlsContainer.visibility = View.VISIBLE
        binding.controlsContainer.visibility = View.VISIBLE
        val animation = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        if (animation != null) {
            binding.bottomControlsContainer.startAnimation(animation)
            binding.controlsContainer.startAnimation(animation)
        }
        binding.videoView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun hideVideoControls(showAnimation: Boolean) {
        if (showAnimation) {
            val animation = AnimationUtils.loadAnimation(this, R.anim.fade_out)
            if (animation != null) {
                binding.bottomControlsContainer.startAnimation(animation)
                binding.controlsContainer.startAnimation(animation)
            }
        }
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        binding.bottomControlsContainer.fitsSystemWindows = true

        binding.bottomControlsContainer.visibility = View.GONE
        binding.controlsContainer.visibility = View.GONE
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent?) {
        onPositionObserverUpdate()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlaybackServiceChanged(event: PlaybackServiceEvent) {
        if (event.action == PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN) {
            finish()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMediaPlayerError(event: PlayerErrorEvent) {
        MediaPlayerErrorDialog.show(this, event)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: MessageEvent) {
        Log.d(TAG, "onEvent($event)")
        val errorDialog = MaterialAlertDialogBuilder(this)
        errorDialog.setMessage(event.message)
        errorDialog.setPositiveButton(event.actionText) { _: DialogInterface?, _: Int ->
            event.action?.accept(this)
        }

        errorDialog.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        requestCastButton(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.mediaplayer, menu)
        return true
    }

    @UnstableApi
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        if (controller == null) return false

        val media = controller!!.getMedia()
        val isFeedMedia = (media is FeedMedia)

        menu.findItem(R.id.open_feed_item).setVisible(isFeedMedia) // FeedMedia implies it belongs to a Feed

        val hasWebsiteLink = getWebsiteLinkWithFallback(media) != null
        menu.findItem(R.id.visit_website_item).setVisible(hasWebsiteLink)

        val isItemAndHasLink = isFeedMedia && hasLinkToShare((media as FeedMedia).item)
        val isItemHasDownloadLink = isFeedMedia && (media as FeedMedia?)?.download_url != null
        menu.findItem(R.id.share_item).setVisible(hasWebsiteLink || isItemAndHasLink || isItemHasDownloadLink)

        menu.findItem(R.id.add_to_favorites_item).setVisible(false)
        menu.findItem(R.id.remove_from_favorites_item).setVisible(false)
        if (isFeedMedia) {
            menu.findItem(R.id.add_to_favorites_item).setVisible(!isFavorite)
            menu.findItem(R.id.remove_from_favorites_item).setVisible(isFavorite)
        }

        menu.findItem(R.id.set_sleeptimer_item).setVisible(!controller!!.sleepTimerActive())
        menu.findItem(R.id.disable_sleeptimer_item).setVisible(controller!!.sleepTimerActive())

        menu.findItem(R.id.player_switch_to_audio_only).setVisible(true)

        menu.findItem(R.id.audio_controls).setVisible(controller!!.audioTracks.size >= 2)
        menu.findItem(R.id.playback_speed).setVisible(true)
        menu.findItem(R.id.player_show_chapters).setVisible(true)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // some options option requires FeedItem
        when {
            item.itemId == R.id.player_switch_to_audio_only -> {
                switchToAudioOnly = true
                finish()
                return true
            }
            item.itemId == android.R.id.home -> {
                val intent = Intent(this@VideoplayerActivity, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
                return true
            }
            item.itemId == R.id.player_show_chapters -> {
                ChaptersFragment().show(supportFragmentManager, ChaptersFragment.TAG)
                return true
            }
            controller == null -> {
                return false
            }
            else -> {
                val media = controller?.getMedia() ?: return false
                val feedItem = getFeedItem(media) // some options option requires FeedItem
                when {
                    item.itemId == R.id.add_to_favorites_item && feedItem != null -> {
                        DBWriter.addFavoriteItem(feedItem)
                        isFavorite = true
                        invalidateOptionsMenu()
                    }
                    item.itemId == R.id.remove_from_favorites_item && feedItem != null -> {
                        DBWriter.removeFavoriteItem(feedItem)
                        isFavorite = false
                        invalidateOptionsMenu()
                    }
                    item.itemId == R.id.disable_sleeptimer_item || item.itemId == R.id.set_sleeptimer_item -> {
                        SleepTimerDialog().show(supportFragmentManager, "SleepTimerDialog")
                    }
                    item.itemId == R.id.audio_controls -> {
                        val dialog = PlaybackControlsDialog.newInstance()
                        dialog.show(supportFragmentManager, "playback_controls")
                    }
                    item.itemId == R.id.open_feed_item && feedItem != null -> {
                        val intent = MainActivity.getIntentToOpenFeed(this, feedItem.feedId)
                        startActivity(intent)
                    }
                    item.itemId == R.id.visit_website_item -> {
                        val url = getWebsiteLinkWithFallback(media)
                        if (url != null) openInBrowser(this@VideoplayerActivity, url)
                    }
                    item.itemId == R.id.share_item && feedItem != null -> {
                        val shareDialog = ShareDialog.newInstance(feedItem)
                        shareDialog.show(supportFragmentManager, "ShareEpisodeDialog")
                    }
                    item.itemId == R.id.playback_speed -> {
                        VariableSpeedDialog().show(supportFragmentManager, null)
                    }
                    else -> {
                        return false
                    }
                }
                return true
            }
        }
    }

    fun onPositionObserverUpdate() {
        if (controller == null) return

        val converter = TimeSpeedConverter(controller!!.currentPlaybackSpeedMultiplier)
        val currentPosition = converter.convert(controller!!.position)
        val duration = converter.convert(controller!!.duration)
        val remainingTime = converter.convert(
            controller!!.duration - controller!!.position)
        //        Log.d(TAG, "currentPosition " + Converter.getDurationStringLong(currentPosition));
        if (currentPosition == Playable.INVALID_TIME || duration == Playable.INVALID_TIME) {
            Log.w(TAG, "Could not react to position observer update because of invalid time")
            return
        }
        binding.positionLabel.text = getDurationStringLong(currentPosition)
        if (showTimeLeft) {
            binding.durationLabel.text = "-" + getDurationStringLong(remainingTime)
        } else {
            binding.durationLabel.text = getDurationStringLong(duration)
        }
        updateProgressbarPosition(currentPosition, duration)
    }

    private fun updateProgressbarPosition(position: Int, duration: Int) {
        Log.d(TAG, "updateProgressbarPosition($position, $duration)")
        val progress = (position.toFloat()) / duration
        binding.sbPosition.progress = (progress * binding.sbPosition.max).toInt()
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (controller == null) return

        if (fromUser) {
            prog = progress / (seekBar.max.toFloat())
            val converter = TimeSpeedConverter(controller!!.currentPlaybackSpeedMultiplier)
            val position = converter.convert((prog * controller!!.duration).toInt())
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
        if (controller != null) {
            controller!!.seekTo((prog * controller!!.duration).toInt())
        }
        binding.seekCardView.scaleX = 1f
        binding.seekCardView.scaleY = 1f
        binding.seekCardView.animate()
            .setInterpolator(FastOutSlowInInterpolator())
            .alpha(0f).scaleX(.8f).scaleY(.8f)
            .setDuration(200)
            .start()
        setupVideoControlsToggler()
    }

    private fun checkFavorite() {
        val feedItem = getFeedItem(controller?.getMedia()) ?: return
        disposable?.dispose()

        disposable = Observable.fromCallable { DBReader.getFeedItem(feedItem.id) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { item: FeedItem? ->
                    if (item != null) {
                        val isFav = item.isTagged(FeedItem.TAG_FAVORITE)
                        if (isFavorite != isFav) {
                            isFavorite = isFav
                            invalidateOptionsMenu()
                        }
                    }
                }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    private fun compatEnterPictureInPicture() {
        if (PictureInPictureUtil.supportsPictureInPicture(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            supportActionBar?.hide()
            hideVideoControls(false)
            enterPictureInPictureMode()
        }
    }

    //Hardware keyboard support
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val currentFocus = currentFocus
        if (currentFocus is EditText) {
            return super.onKeyUp(keyCode, event)
        }

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        when (keyCode) {
            KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_SPACE -> {
                onPlayPause()
                toggleVideoControlsVisibility()
                return true
            }
            KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_COMMA -> {
                onRewind()
                showSkipAnimation(false)
                return true
            }
            KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_PERIOD -> {
                onFastForward()
                showSkipAnimation(true)
                return true
            }
            KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_ESCAPE -> {
                //Exit fullscreen mode
                onBackPressed()
                return true
            }
            KeyEvent.KEYCODE_I -> {
                compatEnterPictureInPicture()
                return true
            }
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_W -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                return true
            }
            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_S -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                return true
            }
            KeyEvent.KEYCODE_M -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                return true
            }
        }
        //Go to x% of video:
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            controller?.seekTo((0.1f * (keyCode - KeyEvent.KEYCODE_0) * controller!!.duration).toInt())
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    companion object {
        private const val TAG = "VideoplayerActivity"

        private fun getWebsiteLinkWithFallback(media: Playable?): String? {
            when {
                media == null -> {
                    return null
                }
                !media.getWebsiteLink().isNullOrBlank() -> {
                    return media.getWebsiteLink()
                }
                media is FeedMedia -> {
                    return getLinkWithFallback(media.item)
                }
                else -> return null
            }
        }

        private fun getFeedItem(playable: Playable?): FeedItem? {
            return if (playable is FeedMedia) {
                playable.item
            } else {
                null
            }
        }
    }
}
