package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.VideoplayerActivityBinding
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.LocalMediaPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.playback.cast.CastEnabledActivity
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.curDurationFB
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isCasting
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isPlayingVideoLocally
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isSleepTimerActive
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playPause
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.seekTo
import ac.mdiq.podcini.preferences.UserPreferences.fastForwardSecs
import ac.mdiq.podcini.preferences.UserPreferences.rewindSecs
import ac.mdiq.podcini.preferences.UserPreferences.videoPlayMode
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.ui.activity.starter.MainActivityStarter
import ac.mdiq.podcini.ui.compose.ChaptersDialog
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.PlaybackSpeedFullDialog
import ac.mdiq.podcini.ui.dialog.MediaPlayerErrorDialog
import ac.mdiq.podcini.ui.dialog.ShareDialog
import ac.mdiq.podcini.ui.dialog.SleepTimerDialog
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.view.ShownotesWebView
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.IntentUtils.openInBrowser
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.ShareUtils.hasLinkToShare
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.MenuItem.SHOW_AS_ACTION_NEVER
import android.widget.EditText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowCompat.getInsetsController
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoplayerActivity : CastEnabledActivity() {
    private var _binding: VideoplayerActivityBinding? = null
    private val binding get() = _binding!!
    var switchToAudioOnly = false
    private var cleanedNotes by mutableStateOf<String?>(null)
    private var feedTitle by mutableStateOf("")
    private var episodeTitle by mutableStateOf("")
    private var showAcrionBar by mutableStateOf(false)
    var landscape by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Podcini_VideoPlayer)
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
        landscape = videoMode == VideoMode.FULL_SCREEN_VIEW

        supportRequestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY)
        super.onCreate(savedInstanceState)

        _binding = VideoplayerActivityBinding.inflate(LayoutInflater.from(this))
        setForVideoMode()

        binding.mainView.setContent {
            CustomTheme(this) {
                if (landscape) Box(modifier = Modifier.fillMaxSize()) { VideoPlayer() }
                else {
                    val textColor = MaterialTheme.colorScheme.onSurface
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f)) { VideoPlayer() }
                        Text(curMedia?.getFeedTitle()?:"", color = textColor, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(horizontal = 10.dp))
                        Text(curMedia?.getEpisodeTitle()?:"", color = textColor, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(horizontal = 10.dp))
                        MediaDetails()
                    }
                }
            }
        }
        setContentView(binding.root)
        supportActionBar?.setBackgroundDrawable(ColorDrawable(-0x80000000))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setForVideoMode()
    }

    private fun setForVideoMode() {
        Logd(TAG, "setForVideoMode videoMode: $videoMode")
        setTheme(R.style.Theme_Podcini_VideoPlayer)
        supportActionBar?.hide()
        when (videoMode) {
            VideoMode.FULL_SCREEN_VIEW -> hideSystemUI()
            VideoMode.WINDOW_VIEW -> showSystemUI()
            else -> {}
        }
        val flags = window.attributes.flags
        Logd(TAG, "Current Flags: $flags")
    }

    private fun showSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.apply { systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE }
        } else {
            window.insetsController?.apply {
                show(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
            }
        }
    }

    fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.apply { systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN }
        } else {
            window.insetsController?.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    @Composable
    fun VideoPlayer() {
        AndroidView(modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                PlayerView(context).apply {
                    this.player = LocalMediaPlayer.exoPlayer
                    useController = true
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            if (visibility == View.VISIBLE) {
                                showAcrionBar = true
                                supportActionBar?.show()
                            } else {
                                showAcrionBar = false
                                supportActionBar?.hide()
                            }
                        }
                    )
                }
            }
        )
    }

    @Composable
    fun MediaDetails() {
        val textColor = MaterialTheme.colorScheme.onSurface
        if (cleanedNotes == null) loadMediaInfo()
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
            ShownotesWebView(context).apply {
                setTimecodeSelectedListener { time: Int -> seekTo(time) }
                setPageFinishedListener {
                    postDelayed({ }, 50)
                }
            }
        }, update = { webView -> webView.loadDataWithBaseURL("https://127.0.0.1", cleanedNotes?:"No notes", "text/html", "utf-8", "about:blank") })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        videoMode = if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) VideoMode.FULL_SCREEN_VIEW else VideoMode.WINDOW_VIEW
        setForVideoMode()
        landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        Logd(TAG, "onConfigurationChanged landscape: $landscape")
    }

    override fun onResume() {
        super.onResume()
        setForVideoMode()
        switchToAudioOnly = false
        if (isCasting) {
            val intent = getPlayerActivityIntent(this)
            if (intent.component?.className != VideoplayerActivity::class.java.name) {
                finish()
                startActivity(intent)
            }
        }
    }

    override fun onDestroy() {
        val insetsController = getInsetsController(window, window.decorView)
        insetsController.show(WindowInsetsCompat.Type.statusBars())
        insetsController.show(WindowInsetsCompat.Type.navigationBars())
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        _binding = null
        super.onDestroy()
    }

    public override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isInPictureInPictureMode) compatEnterPictureInPicture()
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
    }

    private var loadItemsRunning = false
    private fun loadMediaInfo() {
        Logd(TAG, "loadMediaInfo called")
        if (curMedia == null) return
        if (MediaPlayerBase.status == PlayerStatus.PLAYING && !isPlayingVideoLocally) {
            Logd(TAG, "Closing, no longer video")
            finish()
            MainActivityStarter(this).withOpenPlayer().start()
            return
        }
        if (!loadItemsRunning) {
            loadItemsRunning = true
            lifecycleScope.launch {
                try {
                    val episode = withContext(Dispatchers.IO) {
                        var episode_ = (curMedia as? EpisodeMedia)?.episodeOrFetch()
                        if (episode_ != null) {
                            val duration = episode_.media?.getDuration() ?: Int.MAX_VALUE
                            val url = episode_.media?.downloadUrl
                            val shownotesCleaner = ShownotesCleaner(this@VideoplayerActivity)
                            if (url?.contains("youtube.com") == true && episode_.description?.startsWith("Short:") == true) {
                                Logd(TAG, "getting extended description: ${episode_.title}")
                                try {
                                    val info = episode_.streamInfo
                                    if (info?.description?.content != null) {
                                        episode_ = upsert(episode_) { it.description = info.description?.content }
                                        cleanedNotes = shownotesCleaner.processShownotes(info.description!!.content, duration)
                                    } else cleanedNotes = shownotesCleaner.processShownotes(episode_.description ?: "", duration)
                                } catch (e: Exception) { Logd(TAG, "StreamInfo error: ${e.message}") }
                            } else cleanedNotes = shownotesCleaner.processShownotes(episode_.description ?: "", duration)
                        }
                        episode_
                    }
                } catch (e: Throwable) { Log.e(TAG, Log.getStackTraceString(e))
                } finally { loadItemsRunning = false }
            }
        }
        val media = curMedia
        if (media != null) {
            feedTitle = media.getFeedTitle()
            episodeTitle = media.getEpisodeTitle()
            supportActionBar?.subtitle = episodeTitle
            supportActionBar?.title = feedTitle
        }
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
        // TODO: consider enable this
//        requestCastButton(menu)
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

        menu.findItem(R.id.set_sleeptimer_item).isVisible = !isSleepTimerActive()
        menu.findItem(R.id.disable_sleeptimer_item).isVisible = isSleepTimerActive()
        menu.findItem(R.id.player_switch_to_audio_only).isVisible = true

        menu.findItem(R.id.audio_controls).isVisible = audioTracks.size >= 2
        menu.findItem(R.id.playback_speed).isVisible = true
        menu.findItem(R.id.player_show_chapters).isVisible = true

        if (videoMode == VideoMode.WINDOW_VIEW) {
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
                    item.itemId == R.id.disable_sleeptimer_item || item.itemId == R.id.set_sleeptimer_item ->
                        SleepTimerDialog().show(supportFragmentManager, "SleepTimerDialog")
                    item.itemId == R.id.audio_controls -> {
//                        val dialog = PlaybackControlsDialog.newInstance()
//                        dialog.show(supportFragmentManager, "playback_controls")
                        val composeView = ComposeView(this).apply {
                            setContent {
                                var showAudioControlDialog by remember { mutableStateOf(true) }
                                if (showAudioControlDialog) PlaybackControlsDialog(onDismiss = {
                                        showAudioControlDialog = false
                                        (parent as? ViewGroup)?.removeView(this)
                                    })
                            }
                        }
                        (window.decorView as? ViewGroup)?.addView(composeView)
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
                    item.itemId == R.id.playback_speed -> {
                        val composeView = ComposeView(this).apply {
                            setContent {
                                var showSpeedDialog by remember { mutableStateOf(true) }
                                if (showSpeedDialog) PlaybackSpeedFullDialog(settingCode = booleanArrayOf(true, true, true), indexDefault = 0, maxSpeed = 3f,
                                    onDismiss = {
                                        showSpeedDialog = false
                                        (parent as? ViewGroup)?.removeView(this)
                                    })
                            }
                        }
                        (window.decorView as? ViewGroup)?.addView(composeView)
//                        VariableSpeedDialog.newInstance(booleanArrayOf(true, true, true))?.show(supportFragmentManager, null)
                    }
                    else -> return false
                }
                return true
            }
        }
    }

    private fun compatEnterPictureInPicture() {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            if (videoMode == VideoMode.FULL_SCREEN_VIEW) supportActionBar?.hide()
//            videoEpisodeFragment.hideVideoControls(false)
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
                playPause()
                return true
            }
            KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_COMMA -> {
                playbackService?.mPlayer?.seekDelta(-rewindSecs * 1000)
                return true
            }
            KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_PERIOD -> {
                playbackService?.mPlayer?.seekDelta(fastForwardSecs * 1000)
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

    override fun isInPictureInPictureMode(): Boolean {
        return if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) super.isInPictureInPictureMode
        else false
    }

    @Composable
    fun PlaybackControlsDialog(onDismiss: ()-> Unit) {
        val textColor = MaterialTheme.colorScheme.onSurface
        AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.audio_controls)) },
            text = {
                LazyColumn {
                    items(audioTracks.size) {index ->
                        Text(audioTracks[index], color = textColor, modifier = Modifier.clickable(onClick = {
                            playbackService?.mPlayer?.setAudioTrack((selectedAudioTrack + 1) % audioTracks.size)
//                            Handler(Looper.getMainLooper()).postDelayed({ setupAudioTracks() }, 500)
                        }))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.close_label)) } }
        )
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
