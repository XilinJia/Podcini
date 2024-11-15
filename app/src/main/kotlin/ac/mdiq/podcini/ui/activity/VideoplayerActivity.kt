package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.AudioControlsBinding
import ac.mdiq.podcini.databinding.VideoEpisodeFragmentBinding
import ac.mdiq.podcini.databinding.VideoplayerActivityBinding
import ac.mdiq.podcini.playback.ServiceStatusHandler
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.playback.cast.CastEnabledActivity
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.curDurationFB
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.curPositionFB
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.curSpeedFB
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isCasting
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isPlayingVideoLocally
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isSleepTimerActive
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playPause
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.seekTo
import ac.mdiq.podcini.preferences.UserPreferences.fastForwardSecs
import ac.mdiq.podcini.preferences.UserPreferences.rewindSecs
import ac.mdiq.podcini.preferences.UserPreferences.setShowRemainTimeSetting
import ac.mdiq.podcini.preferences.UserPreferences.shouldShowRemainingTime
import ac.mdiq.podcini.preferences.UserPreferences.videoPlayMode
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.storage.utils.DurationConverter.getDurationStringLong
import ac.mdiq.podcini.storage.utils.TimeSpeedConverter
import ac.mdiq.podcini.ui.activity.starter.MainActivityStarter
import ac.mdiq.podcini.ui.compose.ChaptersDialog
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.dialog.*
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.view.ShownotesWebView
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.IntentUtils.openInBrowser
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.ShareUtils.hasLinkToShare
import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Pair
import android.view.*
import android.view.MenuItem.SHOW_AS_ACTION_NEVER
import android.view.animation.*
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityCompat.invalidateOptionsMenu
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.WindowMetricsCalculator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

class VideoplayerActivity : CastEnabledActivity() {
    private var _binding: VideoplayerActivityBinding? = null
    private val binding get() = _binding!!
    private lateinit var videoEpisodeFragment: VideoEpisodeFragment
    var switchToAudioOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        var vmCode = 0
        if (curMedia is EpisodeMedia) {
            val media_ = curMedia as EpisodeMedia
            var vPol = media_.episode?.feed?.preferences?.videoModePolicy
            if (vPol != null) {
                if (vPol == VideoMode.AUDIO_ONLY && media_.forceVideo) vPol = VideoMode.WINDOW_VIEW
                if (vPol != VideoMode.NONE) vmCode = vPol.code
            }
        }
        Logd(TAG, "onCreate vmCode: $vmCode")
        if (vmCode == 0) vmCode = videoPlayMode
        Logd(TAG, "onCreate vmCode: $vmCode")
        videoMode = VideoMode.entries.toTypedArray().getOrElse(vmCode) { VideoMode.WINDOW_VIEW }
        if (videoMode == VideoMode.AUDIO_ONLY) {
            switchToAudioOnly = true
            finish()
        }
        if (videoMode != VideoMode.FULL_SCREEN_VIEW && videoMode != VideoMode.WINDOW_VIEW) {
            Logd(TAG, "videoMode not selected, use window mode")
            videoMode = VideoMode.WINDOW_VIEW
        }

        supportRequestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY)
        setForVideoMode()
        super.onCreate(savedInstanceState)

        _binding = VideoplayerActivityBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        supportActionBar?.setBackgroundDrawable(ColorDrawable(-0x80000000))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val fm = supportFragmentManager
        val transaction = fm.beginTransaction()
        videoEpisodeFragment = VideoEpisodeFragment()
        transaction.replace(R.id.main_view, videoEpisodeFragment, "VideoEpisodeFragment")
        transaction.commit()
    }

    private fun setForVideoMode() {
        Logd(TAG, "setForVideoMode videoMode: $videoMode")
        when (videoMode) {
            VideoMode.FULL_SCREEN_VIEW -> {
                window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
                window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
                setTheme(R.style.Theme_Podcini_VideoPlayer)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
                window.setFormat(PixelFormat.TRANSPARENT)
            }
            VideoMode.WINDOW_VIEW -> {
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
//                setTheme(R.style.Theme_Podcini_VideoEpisode)
                setTheme(R.style.Theme_Podcini_VideoPlayer)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                window.setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
                window.setFormat(PixelFormat.TRANSPARENT)
            }
            else -> {}
        }
        if (::videoEpisodeFragment.isInitialized) videoEpisodeFragment.setForVideoMode()
    }

    
    override fun onResume() {
        super.onResume()
        setForVideoMode()
        switchToAudioOnly = false
        if (isCasting) {
            val intent = getPlayerActivityIntent(this)
            if (intent.component?.className != VideoplayerActivity::class.java.name) {
                videoEpisodeFragment.destroyingDueToReload = true
                finish()
                startActivity(intent)
            }
        }
    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
        _binding = null
        super.onDestroy()
    }

    public override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isInPictureInPictureMode()) compatEnterPictureInPicture()
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
    }

    fun toggleViews() {
        videoMode = if (videoMode == VideoMode.FULL_SCREEN_VIEW) VideoMode.WINDOW_VIEW else VideoMode.FULL_SCREEN_VIEW
        setForVideoMode()
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
                    is FlowEvent.SleepTimerUpdatedEvent -> if (event.isCancelled || event.wasJustEnabled()) supportInvalidateOptionsMenu()
                    is FlowEvent.PlaybackServiceEvent -> if (event.action == FlowEvent.PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN) finish()
                    is FlowEvent.PlayerErrorEvent -> MediaPlayerErrorDialog.show(this@VideoplayerActivity, event)
                    is FlowEvent.MessageEvent -> onEventMainThread(event)
                    else -> {}
                }
            }
        }
    }

    private fun onEventMainThread(event: FlowEvent.MessageEvent) {
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

    
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)

        val media = curMedia
        val isEpisodeMedia = (media is EpisodeMedia)

        menu.findItem(R.id.show_home_reader_view).isVisible = false
        menu.findItem(R.id.open_feed_item).isVisible = isEpisodeMedia // EpisodeMedia implies it belongs to a Feed

        val hasWebsiteLink = getWebsiteLinkWithFallback(media) != null
        menu.findItem(R.id.visit_website_item).isVisible = hasWebsiteLink

        val isItemAndHasLink = isEpisodeMedia && hasLinkToShare(media.episodeOrFetch())
        val isItemHasDownloadLink = isEpisodeMedia && (media as EpisodeMedia?)?.downloadUrl != null
        menu.findItem(R.id.share_item).isVisible = hasWebsiteLink || isItemAndHasLink || isItemHasDownloadLink

//        menu.findItem(R.id.add_to_favorites_item).setVisible(false)
//        menu.findItem(R.id.remove_from_favorites_item).setVisible(false)
//        if (isEpisodeMedia) {
//            menu.findItem(R.id.add_to_favorites_item).setVisible(!videoEpisodeFragment.isFavorite)
//            menu.findItem(R.id.remove_from_favorites_item).setVisible(videoEpisodeFragment.isFavorite)
//        }

        menu.findItem(R.id.set_sleeptimer_item).isVisible = !isSleepTimerActive()
        menu.findItem(R.id.disable_sleeptimer_item).isVisible = isSleepTimerActive()
        menu.findItem(R.id.player_switch_to_audio_only).isVisible = true

        menu.findItem(R.id.audio_controls).isVisible = audioTracks.size >= 2
        menu.findItem(R.id.playback_speed).isVisible = true
        menu.findItem(R.id.player_show_chapters).isVisible = true

        if (videoMode == VideoMode.WINDOW_VIEW) {
//            menu.findItem(R.id.add_to_favorites_item).setShowAsAction(SHOW_AS_ACTION_NEVER)
//            menu.findItem(R.id.remove_from_favorites_item).setShowAsAction(SHOW_AS_ACTION_NEVER)
            menu.findItem(R.id.set_sleeptimer_item).setShowAsAction(SHOW_AS_ACTION_NEVER)
            menu.findItem(R.id.disable_sleeptimer_item).setShowAsAction(SHOW_AS_ACTION_NEVER)
            menu.findItem(R.id.player_switch_to_audio_only).setShowAsAction(SHOW_AS_ACTION_NEVER)
            menu.findItem(R.id.open_feed_item).setShowAsAction(SHOW_AS_ACTION_NEVER)
            menu.findItem(R.id.share_item).setShowAsAction(SHOW_AS_ACTION_NEVER)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // some options option requires FeedItem
        when (item.itemId) {
            R.id.player_switch_to_audio_only -> {
                switchToAudioOnly = true
                (curMedia as? EpisodeMedia)?.forceVideo = false
                finish()
                return true
            }
            android.R.id.home -> {
                val intent = Intent(this@VideoplayerActivity, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
                return true
            }
            R.id.player_show_chapters -> {
                val composeView = ComposeView(this).apply {
                    setContent {
                        val showDialog = remember { mutableStateOf(true) }
                        CustomTheme(this@VideoplayerActivity) {
                            ChaptersDialog(curMedia!!, onDismissRequest = {
                                showDialog.value = false
                                (binding.root as? ViewGroup)?.removeView(this@apply)
                            })
                        }
                    }
                }
                (binding.root as? ViewGroup)?.addView(composeView)
//                ChaptersFragment().show(supportFragmentManager, ChaptersFragment.TAG)
                return true
            }
            else -> {
                val media = curMedia ?: return false
                val feedItem = (media as? EpisodeMedia)?.episodeOrFetch()
                when {
//                    item.itemId == R.id.add_to_favorites_item && feedItem != null -> {
//                        setFavorite(feedItem, true)
//                        videoEpisodeFragment.isFavorite = true
//                        invalidateOptionsMenu()
//                    }
//                    item.itemId == R.id.remove_from_favorites_item && feedItem != null -> {
//                        setFavorite(feedItem, false)
//                        videoEpisodeFragment.isFavorite = false
//                        invalidateOptionsMenu()
//                    }
                    item.itemId == R.id.disable_sleeptimer_item || item.itemId == R.id.set_sleeptimer_item ->
                        SleepTimerDialog().show(supportFragmentManager, "SleepTimerDialog")
                    item.itemId == R.id.audio_controls -> {
                        val dialog = PlaybackControlsDialog.newInstance()
                        dialog.show(supportFragmentManager, "playback_controls")
                    }
                    item.itemId == R.id.open_feed_item && feedItem != null -> {
                        val intent = MainActivity.getIntentToOpenFeed(this, feedItem.feedId!!)
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
                    item.itemId == R.id.playback_speed ->
                        VariableSpeedDialog.newInstance(booleanArrayOf(true, true, true))?.show(supportFragmentManager, null)
                    else -> return false
                }
                return true
            }
        }
    }

    private fun compatEnterPictureInPicture() {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            if (videoMode == VideoMode.FULL_SCREEN_VIEW) supportActionBar?.hide()
            videoEpisodeFragment.hideVideoControls(false)
            enterPictureInPictureMode()
        }
    }

    //Hardware keyboard support
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val currentFocus = currentFocus
        if (currentFocus is EditText) return super.onKeyUp(keyCode, event)
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        when (keyCode) {
            KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_SPACE -> {
                videoEpisodeFragment.onPlayPause()
                videoEpisodeFragment.toggleVideoControlsVisibility()
                return true
            }
            KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_COMMA -> {
                videoEpisodeFragment.onRewind()
                videoEpisodeFragment.showSkipAnimation(false)
                return true
            }
            KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_PERIOD -> {
                videoEpisodeFragment.onFastForward()
                videoEpisodeFragment.showSkipAnimation(true)
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
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                return true
            }
            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_S -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                return true
            }
            KeyEvent.KEYCODE_M -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                return true
            }
        }
        //Go to x% of video:
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            seekTo((0.1f * (keyCode - KeyEvent.KEYCODE_0) * curDurationFB).toInt())
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

//    fun supportsPictureInPicture(): Boolean {
////        val packageManager = activity.packageManager
//        return packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
//    }

    override fun isInPictureInPictureMode(): Boolean {
        return if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) super.isInPictureInPictureMode
        else false
    }

    class PlaybackControlsDialog : DialogFragment() {
        private lateinit var dialog: AlertDialog
        private var _binding: AudioControlsBinding? = null
        private val binding get() = _binding!!
        private var statusHandler: ServiceStatusHandler? = null

         override fun onStart() {
            super.onStart()
            statusHandler = object : ServiceStatusHandler(requireActivity()) {
                override fun loadMediaInfo() {
                    setupAudioTracks()
                }
            }
            statusHandler?.init()
        }
         override fun onStop() {
            super.onStop()
            statusHandler?.release()
            statusHandler = null
        }
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            _binding = AudioControlsBinding.inflate(layoutInflater)
            dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.audio_controls)
                .setView(R.layout.audio_controls)
                .setPositiveButton(R.string.close_label, null).create()
            return dialog
        }
        override fun onDestroyView() {
            Logd(TAG, "onDestroyView")
            _binding = null
            super.onDestroyView()
        }
         private fun setupAudioTracks() {
            val butAudioTracks = binding.audioTracks
            if (audioTracks.size < 2 || selectedAudioTrack < 0) {
                butAudioTracks.visibility = View.GONE
                return
            }
            butAudioTracks.visibility = View.VISIBLE
            butAudioTracks.text = audioTracks[selectedAudioTrack]
            butAudioTracks.setOnClickListener {
//                setAudioTrack((selectedAudioTrack + 1) % audioTracks.size)
                playbackService?.mPlayer?.setAudioTrack((selectedAudioTrack + 1) % audioTracks.size)
                Handler(Looper.getMainLooper()).postDelayed({ this.setupAudioTracks() }, 500)
            }
        }

        companion object {
            fun newInstance(): PlaybackControlsDialog {
                val dialog = PlaybackControlsDialog()
                return dialog
            }
        }
    }

    class VideoEpisodeFragment : Fragment(), OnSeekBarChangeListener {
        private var _binding: VideoEpisodeFragmentBinding? = null
        private val binding get() = _binding!!
        private lateinit var root: ViewGroup
        private var videoControlsVisible = true
        private var videoSurfaceCreated = false
        private var lastScreenTap: Long = 0
        private val videoControlsHider = Handler(Looper.getMainLooper())
        private var showTimeLeft = false
        private var prog = 0f

        private var itemsLoaded = false
        private var episode: Episode? = null
        private var webviewData: String = ""
        private var webvDescription: ShownotesWebView? = null

        var destroyingDueToReload = false
        private var statusHandler: ServiceStatusHandler? = null
        var isFavorite = false

        private val onVideoviewTouched = View.OnTouchListener { v: View, event: MotionEvent ->
            Logd(TAG, "onVideoviewTouched ${event.action}")
            if (event.action != MotionEvent.ACTION_DOWN) return@OnTouchListener false
            if (requireActivity().isInPictureInPictureMode()) return@OnTouchListener true
            videoControlsHider.removeCallbacks(hideVideoControls)
            Logd(TAG, "onVideoviewTouched $videoControlsVisible ${System.currentTimeMillis() - lastScreenTap}")
            if (System.currentTimeMillis() - lastScreenTap < 300) {
                if (event.x > v.measuredWidth / 2.0f) {
                    onFastForward()
                    showSkipAnimation(true)
                } else {
                    onRewind()
                    showSkipAnimation(false)
                }
                if (videoControlsVisible) {
                    hideVideoControls(false)
                    videoControlsVisible = false
                }
                return@OnTouchListener true
            }
            toggleVideoControlsVisibility()
            if (videoControlsVisible) setupVideoControlsToggler()
            lastScreenTap = System.currentTimeMillis()
            true
        }

        private val surfaceHolderCallback: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                holder.setFixedSize(width, height)
            }
            
            override fun surfaceCreated(holder: SurfaceHolder) {
                Logd(TAG, "Videoview holder created")
                videoSurfaceCreated = true
                if (MediaPlayerBase.status == PlayerStatus.PLAYING) playbackService?.mPlayer?.setVideoSurface(holder)
                setupVideoAspectRatio()
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Logd(TAG, "Videosurface was destroyed")
                videoSurfaceCreated = false
                (activity as? VideoplayerActivity)?.finish()
            }
        }

        private val hideVideoControls = Runnable {
            if (videoControlsVisible) {
                Logd(TAG, "Hiding video controls")
                hideVideoControls(true)
                videoControlsVisible = false
            }
        }
        
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            super.onCreateView(inflater, container, savedInstanceState)
            Logd(TAG, "fragment onCreateView")
            _binding = VideoEpisodeFragmentBinding.inflate(inflater)
            root = binding.root
            statusHandler = newStatusHandler()
            statusHandler!!.init()
            setupView()
            return root
        }

        private fun newStatusHandler(): ServiceStatusHandler {
            return object : ServiceStatusHandler(requireActivity()) {
                override fun updatePlayButton(showPlay: Boolean) {
                    Logd(TAG, "updatePlayButtonShowsPlay called")
                    binding.playButton.setIsShowPlay(showPlay)
                    if (showPlay) (activity as AppCompatActivity).window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else {
                        (activity as AppCompatActivity).window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        setupVideoAspectRatio()
                        if (videoSurfaceCreated) {
                            Logd(TAG, "Videosurface already created, setting videosurface now")
                            playbackService?.mPlayer?.setVideoSurface(binding.videoView.holder)
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
        
        override fun onStart() {
            super.onStart()
            onPositionObserverUpdate()
            procFlowEvents()
        }

        override fun onStop() {
            super.onStop()
            cancelFlowEvents()
            if (!requireActivity().isInPictureInPictureMode()) videoControlsHider.removeCallbacks(hideVideoControls)
            // Controller released; we will not receive buffering updates
            binding.progressBar.visibility = View.GONE
        }

        override fun onDestroyView() {
            Logd(TAG, "onDestroyView")
            if (webvDescription != null) {
                root.removeView(webvDescription!!)
                webvDescription!!.destroy()
            }
            _binding = null
            statusHandler?.release()
            statusHandler = null // prevent leak
            super.onDestroyView()
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

        fun setForVideoMode() {
            when (videoMode) {
                VideoMode.FULL_SCREEN_VIEW -> {
                    Logd(TAG, "setForVideoMode setting for FULL_SCREEN_VIEW")
                    webvDescription?.visibility = View.GONE
                    val layoutParams = binding.videoPlayerContainer.layoutParams
                    layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    binding.videoPlayerContainer.layoutParams = layoutParams
                    binding.topBar.visibility = View.GONE
                }
                VideoMode.WINDOW_VIEW -> {
                    Logd(TAG, "setForVideoMode setting for WINDOW_VIEW")
                    webvDescription?.visibility = View.VISIBLE
                    val layoutParams = binding.videoPlayerContainer.layoutParams
                    layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    binding.videoPlayerContainer.layoutParams = layoutParams
                    binding.topBar.visibility = View.VISIBLE
                }
                else -> {}
            }
            setupVideoAspectRatio()
        }

        private fun bufferUpdate(event: FlowEvent.BufferUpdateEvent) {
            when {
                event.hasStarted() -> binding.progressBar.visibility = View.VISIBLE
                event.hasEnded() -> binding.progressBar.visibility = View.INVISIBLE
                else -> binding.sbPosition.secondaryProgress = (event.progress * binding.sbPosition.max).toInt()
            }
        }

        private fun setupVideoAspectRatio() {
            if (videoSurfaceCreated) {
                val windowMetrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity as Activity)
                val videoWidth = when (videoMode) {
                    VideoMode.FULL_SCREEN_VIEW -> max(windowMetrics.bounds.width(), windowMetrics.bounds.height())
                    VideoMode.WINDOW_VIEW -> min(windowMetrics.bounds.width(), windowMetrics.bounds.height())
                    else -> min(windowMetrics.bounds.width(), windowMetrics.bounds.height())
                }
                val videoHeight: Int
                if (videoSize != null && videoSize!!.first > 0 && videoSize!!.second > 0) {
                    Logd(TAG, "setupVideoAspectRatio video width: ${videoSize!!.first} height: ${videoSize!!.second}")
                    videoHeight = (videoWidth.toFloat() / videoSize!!.first * videoSize!!.second).toInt()
                    Logd(TAG, "setupVideoAspectRatio adjusted video width: $videoWidth height: $videoHeight")
                } else {
                    videoHeight = (videoWidth.toFloat() / 16 * 9).toInt()
                    Logd(TAG, "setupVideoAspectRatio Could not determine video size, use: $videoWidth $videoHeight")
                }
                val lp = binding.videoView.layoutParams
                lp.width = videoWidth
                lp.height = videoHeight
                binding.videoView.layoutParams = lp
            }
        }

        private var loadItemsRunning = false
        
        private fun loadMediaInfo() {
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
            if (!loadItemsRunning) {
                loadItemsRunning = true
                lifecycleScope.launch {
                    try {
                        episode = withContext(Dispatchers.IO) {
                            var episode_ = (curMedia as? EpisodeMedia)?.episodeOrFetch()
                            if (episode_ != null) {
                                val duration = episode_.media?.getDuration() ?: Int.MAX_VALUE
                                val url = episode_.media?.downloadUrl
                                val shownotesCleaner = ShownotesCleaner(requireContext())
                                if (url?.contains("youtube.com") == true && episode_.description?.startsWith("Short:") == true) {
                                    Logd(TAG, "getting extended description: ${episode_.title}")
                                    try {
                                        val info = episode_.streamInfo
                                        if (info?.description?.content != null) {
                                            episode_ = upsert(episode_) { it.description = info.description?.content }
                                            webviewData = shownotesCleaner.processShownotes(info.description!!.content, duration)
                                        } else webviewData = shownotesCleaner.processShownotes(episode_.description ?: "", duration)
                                    } catch (e: Exception) { Logd(TAG, "StreamInfo error: ${e.message}") }
                                } else webviewData = shownotesCleaner.processShownotes(episode_.description ?: "", duration)
                            }
                            episode_
                        }
                        withContext(Dispatchers.Main) {
                            Logd(TAG, "load() item ${episode?.id}")
                            if (episode != null) {
                                val isFav = episode!!.isSUPER
                                if (isFavorite != isFav) {
                                    isFavorite = isFav
                                    invalidateOptionsMenu(activity)
                                }
                            }
                            if (!itemsLoaded) webvDescription?.loadDataWithBaseURL("https://127.0.0.1", webviewData,
                                "text/html", "utf-8", "about:blank")
                            itemsLoaded = true
                        }
                    } catch (e: Throwable) { Log.e(TAG, Log.getStackTraceString(e))
                    } finally { loadItemsRunning = false }
                }
            }
            val media = curMedia
            if (media != null) {
                (activity as AppCompatActivity).supportActionBar?.subtitle = media.getEpisodeTitle()
                (activity as AppCompatActivity).supportActionBar?.title = media.getFeedTitle()
            }
        }

        
        private fun setupView() {
            showTimeLeft = shouldShowRemainingTime()
            Logd(TAG, "setupView showTimeLeft: $showTimeLeft")
            binding.durationLabel.setOnClickListener {
                showTimeLeft = !showTimeLeft
                val media = curMedia ?: return@setOnClickListener
                val converter = TimeSpeedConverter(curSpeedFB)
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
            setupVideoControlsToggler()
            binding.videoPlayerContainer.setOnTouchListener(onVideoviewTouched)
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
            binding.toggleViews.setOnClickListener { (activity as VideoplayerActivity).toggleViews() }
            binding.audioOnly.setOnClickListener {
                (activity as? VideoplayerActivity)?.switchToAudioOnly = true
                (curMedia as? EpisodeMedia)?.forceVideo = false
                (activity as? VideoplayerActivity)?.finish()
            }
            if (!itemsLoaded) webvDescription?.loadDataWithBaseURL("https://127.0.0.1", webviewData,
                "text/html", "utf-8", "about:blank")
        }

        fun toggleVideoControlsVisibility() {
            if (videoControlsVisible) hideVideoControls(true)
            else showVideoControls()
            videoControlsVisible = !videoControlsVisible
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

        
        fun onRewind() {
            playbackService?.mPlayer?.seekDelta(-rewindSecs * 1000)
            setupVideoControlsToggler()
        }

        
        fun onPlayPause() {
            playPause()
            setupVideoControlsToggler()
        }

        
        fun onFastForward() {
            playbackService?.mPlayer?.seekDelta(fastForwardSecs * 1000)
            setupVideoControlsToggler()
        }

        private fun setupVideoControlsToggler() {
            videoControlsHider.removeCallbacks(hideVideoControls)
            videoControlsHider.postDelayed(hideVideoControls, 2500)
        }

        private fun showVideoControls() {
            Logd(TAG, "showVideoControls")
            binding.bottomControlsContainer.visibility = View.VISIBLE
            binding.controlsContainer.visibility = View.VISIBLE
            val animation = AnimationUtils.loadAnimation(activity, R.anim.fade_in)
            if (animation != null) {
                binding.bottomControlsContainer.startAnimation(animation)
                binding.controlsContainer.startAnimation(animation)
            }
            (activity as AppCompatActivity).window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            (activity as AppCompatActivity).supportActionBar?.show()
        }

        fun hideVideoControls(showAnimation: Boolean) {
            Logd(TAG, "hideVideoControls $showAnimation")
            if (!isAdded) return
            if (showAnimation) {
                val animation = AnimationUtils.loadAnimation(activity, R.anim.fade_out)
                if (animation != null) {
                    binding.bottomControlsContainer.startAnimation(animation)
                    binding.controlsContainer.startAnimation(animation)
                }
            }
            (activity as AppCompatActivity).window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            binding.bottomControlsContainer.visibility = View.GONE
            binding.controlsContainer.visibility = View.GONE
            if (videoMode == VideoMode.FULL_SCREEN_VIEW) (activity as AppCompatActivity).supportActionBar?.hide()
        }

        private fun onPositionObserverUpdate() {
            val converter = TimeSpeedConverter(curSpeedFB)
            val currentPosition = converter.convert(curPositionFB)
            val duration_ = converter.convert(curDurationFB)
            val remainingTime = converter.convert(curDurationFB - curPositionFB)
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
            if (fromUser) {
                prog = progress / (seekBar.max.toFloat())
                val converter = TimeSpeedConverter(curSpeedFB)
                val position = converter.convert((prog * curDurationFB).toInt())
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
            seekTo((prog * curDurationFB).toInt())
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
            val videoSize: Pair<Int, Int>?
                get() = playbackService?.mPlayer?.getVideoSize()
        }
    }

    companion object {
        private val TAG: String = VideoplayerActivity::class.simpleName ?: "Anonymous"
        var videoMode = VideoMode.NONE

        private val audioTracks: List<String>
            get() {
                val tracks = playbackService?.mPlayer?.getAudioTracks()
                if (tracks.isNullOrEmpty()) return emptyList()
                return tracks.filterNotNull().map { it }
            }

        private val selectedAudioTrack: Int
            get() = playbackService?.mPlayer?.getSelectedAudioTrack() ?: -1

        private fun getWebsiteLinkWithFallback(media: Playable?): String? {
            return when {
                media == null -> null
                !media.getWebsiteLink().isNullOrBlank() -> media.getWebsiteLink()
                media is EpisodeMedia -> media.episodeOrFetch()?.getLinkWithFallback()
                else -> null
            }
        }
    }
}
