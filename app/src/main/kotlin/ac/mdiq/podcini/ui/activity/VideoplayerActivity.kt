package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.AudioControlsBinding
import ac.mdiq.podcini.databinding.VideoplayerActivityBinding
import ac.mdiq.podcini.playback.ServiceStatusHandler
import ac.mdiq.podcini.playback.ServiceStatusHandler.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.cast.CastEnabledActivity
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.curDurationFB
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isCasting
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isSleepTimerActive
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.seekTo
import ac.mdiq.podcini.preferences.UserPreferences.videoPlayMode
import ac.mdiq.podcini.storage.database.Episodes.setFavorite
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.ui.dialog.MediaPlayerErrorDialog
import ac.mdiq.podcini.ui.dialog.ShareDialog
import ac.mdiq.podcini.ui.dialog.SleepTimerDialog
import ac.mdiq.podcini.ui.dialog.VariableSpeedDialog
import ac.mdiq.podcini.ui.fragment.ChaptersFragment
import ac.mdiq.podcini.ui.fragment.VideoEpisodeFragment
import ac.mdiq.podcini.ui.utils.PictureInPictureUtil
import ac.mdiq.podcini.util.IntentUtils.openInBrowser
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.ShareUtils.hasLinkToShare
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.MenuItem.SHOW_AS_ACTION_NEVER
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Activity for playing video files.
 */
@UnstableApi
class VideoplayerActivity : CastEnabledActivity() {

    enum class VideoMode(val mode: Int) {
        None(0),
        WINDOW_VIEW(1),
        FULL_SCREEN_VIEW(2),
        AUDIO_ONLY(3)
    }

    private var _binding: VideoplayerActivityBinding? = null
    private val binding get() = _binding!!

    private lateinit var videoEpisodeFragment: VideoEpisodeFragment

    var switchToAudioOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        videoMode = (intent.getSerializableExtra(VIDEO_MODE) as? VideoMode) ?: VideoMode.None
        if (videoMode == VideoMode.None) {
            videoMode = VideoMode.entries.toTypedArray().getOrElse(videoPlayMode) { VideoMode.WINDOW_VIEW }
            if (videoMode == VideoMode.AUDIO_ONLY) {
                switchToAudioOnly = true
                finish()
            }
            if (videoMode != VideoMode.FULL_SCREEN_VIEW && videoMode != VideoMode.WINDOW_VIEW) {
                Logd(TAG, "videoMode not selected, use window mode")
                videoMode = VideoMode.WINDOW_VIEW
            }
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
        transaction.replace(R.id.main_view, videoEpisodeFragment, VideoEpisodeFragment.TAG)
        transaction.commit()
    }

    private fun setForVideoMode() {
        when (videoMode) {
            VideoMode.FULL_SCREEN_VIEW -> {
                window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
                setTheme(R.style.Theme_Podcini_VideoPlayer)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
                window.setFormat(PixelFormat.TRANSPARENT)
            }
            VideoMode.WINDOW_VIEW -> {
                setTheme(R.style.Theme_Podcini_VideoEpisode)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                window.setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
                window.setFormat(PixelFormat.TRANSPARENT)
            }
            else -> {}
        }
    }

    @UnstableApi
    override fun onResume() {
        super.onResume()
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
        if (!PictureInPictureUtil.isInPictureInPictureMode(this)) compatEnterPictureInPicture()
    }

    @UnstableApi
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

    fun onEventMainThread(event: FlowEvent.MessageEvent) {
//        Logd(TAG, "onEvent($event)")
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

        val media = curMedia
        val isEpisodeMedia = (media is EpisodeMedia)

        menu.findItem(R.id.show_home_reader_view).setVisible(false)
        menu.findItem(R.id.open_feed_item).setVisible(isEpisodeMedia) // EpisodeMedia implies it belongs to a Feed

        val hasWebsiteLink = getWebsiteLinkWithFallback(media) != null
        menu.findItem(R.id.visit_website_item).setVisible(hasWebsiteLink)

        val isItemAndHasLink = isEpisodeMedia && hasLinkToShare((media as EpisodeMedia).episodeOrFetch())
        val isItemHasDownloadLink = isEpisodeMedia && (media as EpisodeMedia?)?.downloadUrl != null
        menu.findItem(R.id.share_item).setVisible(hasWebsiteLink || isItemAndHasLink || isItemHasDownloadLink)

        menu.findItem(R.id.add_to_favorites_item).setVisible(false)
        menu.findItem(R.id.remove_from_favorites_item).setVisible(false)
        if (isEpisodeMedia) {
            menu.findItem(R.id.add_to_favorites_item).setVisible(!videoEpisodeFragment.isFavorite)
            menu.findItem(R.id.remove_from_favorites_item).setVisible(videoEpisodeFragment.isFavorite)
        }

        menu.findItem(R.id.set_sleeptimer_item).setVisible(!isSleepTimerActive())
        menu.findItem(R.id.disable_sleeptimer_item).setVisible(isSleepTimerActive())
        menu.findItem(R.id.player_switch_to_audio_only).setVisible(true)

        menu.findItem(R.id.audio_controls).setVisible(audioTracks.size >= 2)
        menu.findItem(R.id.playback_speed).setVisible(true)
        menu.findItem(R.id.player_show_chapters).setVisible(true)

        if (videoMode == VideoMode.WINDOW_VIEW) {
            menu.findItem(R.id.add_to_favorites_item).setShowAsAction(SHOW_AS_ACTION_NEVER)
            menu.findItem(R.id.remove_from_favorites_item).setShowAsAction(SHOW_AS_ACTION_NEVER)
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
            else -> {
                val media = curMedia ?: return false
                val feedItem = (media as? EpisodeMedia)?.episodeOrFetch()
                when {
                    item.itemId == R.id.add_to_favorites_item && feedItem != null -> {
                        setFavorite(feedItem, true)
                        videoEpisodeFragment.isFavorite = true
                        invalidateOptionsMenu()
                    }
                    item.itemId == R.id.remove_from_favorites_item && feedItem != null -> {
                        setFavorite(feedItem, false)
                        videoEpisodeFragment.isFavorite = false
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
        if (PictureInPictureUtil.supportsPictureInPicture(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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

    class PlaybackControlsDialog : DialogFragment() {
        private lateinit var dialog: AlertDialog
        private var _binding: AudioControlsBinding? = null
        private val binding get() = _binding!!
        private var controller: ServiceStatusHandler? = null

        @UnstableApi override fun onStart() {
            super.onStart()
            controller = object : ServiceStatusHandler(requireActivity()) {
                override fun loadMediaInfo() {
                    setupAudioTracks()
                }
            }
            controller?.init()
        }

        @UnstableApi override fun onStop() {
            super.onStop()
            controller?.release()
            controller = null
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

        @UnstableApi private fun setupAudioTracks() {
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

    companion object {
        private val TAG: String = VideoplayerActivity::class.simpleName ?: "Anonymous"
        const val VIDEO_MODE = "Video_Mode"

        var videoMode = VideoMode.None

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
