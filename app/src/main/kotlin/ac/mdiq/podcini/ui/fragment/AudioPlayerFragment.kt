package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.AudioplayerFragmentBinding
import ac.mdiq.podcini.net.utils.NetworkUtils.fetchHtmlSource
import ac.mdiq.podcini.playback.PlaybackServiceStarter
import ac.mdiq.podcini.playback.ServiceStatusHandler
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.MediaPlayerBase
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
import ac.mdiq.podcini.ui.actions.EpisodeMenuHandler
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.Companion.videoMode
import ac.mdiq.podcini.ui.activity.starter.VideoPlayerActivityStarter
import ac.mdiq.podcini.ui.compose.ChooseRatingDialog
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.dialog.MediaPlayerErrorDialog
import ac.mdiq.podcini.ui.dialog.SkipPreferenceDialog
import ac.mdiq.podcini.ui.dialog.SleepTimerDialog
import ac.mdiq.podcini.ui.dialog.VariableSpeedDialog
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.view.ShownotesWebView
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter
import android.app.Activity
import android.content.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
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
class AudioPlayerFragment : Fragment(), Toolbar.OnMenuItemClickListener {
    var _binding: AudioplayerFragmentBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var toolbar: MaterialToolbar
    private var showPlayer1 by mutableStateOf(true)
    var isCollapsed by mutableStateOf(true)

//    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: ServiceStatusHandler? = null

    private var prevMedia: Playable? = null
    private var currentMedia by mutableStateOf<Playable?>(null)
    private var prevItem: Episode? = null
    private var currentItem: Episode? = null

    private var isShowPlay: Boolean = true

    private var showTimeLeft = false
    private var titleText by mutableStateOf("")
    private var imgLoc by mutableStateOf<String?>(null)
    private var txtvPlaybackSpeed by mutableStateOf("")
    private var remainingTime by mutableIntStateOf(0)
    private var isVideoScreen = false
    private var playButRes by mutableIntStateOf(R.drawable.ic_play_48dp)
    private var currentPosition by mutableIntStateOf(0)
    private var duration by mutableIntStateOf(0)
    private var txtvLengtTexth by mutableStateOf("")
    private var sliderValue by mutableFloatStateOf(0f)

    private var shownotesCleaner: ShownotesCleaner? = null

    private var displayedChapterIndex = -1

    private var cleanedNotes by mutableStateOf<String?>(null)
    private var isLoading = false
    private var homeText: String? = null
    private var showHomeText = false
    private var readerhtml: String? = null
    private var txtvPodcastTitle by mutableStateOf("")
    private var episodeDate by mutableStateOf("")
    private var chapterControlVisible by mutableStateOf(false)
    private var hasNextChapter by mutableStateOf(true)
    var rating by mutableStateOf(currentItem?.rating ?: 0)
    private val currentChapter: Chapter?
        get() {
            if (currentMedia == null || currentMedia!!.getChapters().isEmpty() || displayedChapterIndex == -1) return null
            return currentMedia!!.getChapters()[displayedChapterIndex]
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = AudioplayerFragmentBinding.inflate(inflater)
        binding.root.setOnTouchListener { _: View?, _: MotionEvent? -> true } // Avoid clicks going through player to fragments below

        Logd(TAG, "fragment onCreateView")
        toolbar = binding.toolbar
        toolbar.title = ""
        toolbar.setNavigationOnClickListener {
            val bottomSheet = (activity as MainActivity).bottomSheet
            bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        toolbar.setOnMenuItemClickListener(this)
        controller = createHandler()
        controller!!.init()
        onCollaped()

        binding.player1.setContent {
            CustomTheme(requireContext()) {
                if (showPlayer1) PlayerUI()
                else Spacer(modifier = Modifier.size(0.dp))
            }
        }
        binding.composeDetailView.setContent {
            CustomTheme(requireContext()) {
                DetailUI()
//                if (!isCollapsed) DetailUI()
//                else Spacer(modifier = Modifier.size(0.dp))
            }
        }
        binding.player2.setContent {
            CustomTheme(requireContext()) {
                if (!showPlayer1) PlayerUI()
                else Spacer(modifier = Modifier.size(0.dp))
            }
        }
//        cardViewSeek = binding.cardViewSeek
        (activity as MainActivity).setPlayerVisible(false)
        return binding.root
    }

    override fun onDestroyView() {
        Logd(TAG, "Fragment destroyed")
        _binding = null
        controller?.release()
        controller = null
//        MediaController.releaseFuture(controllerFuture)
        super.onDestroyView()
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun PlayerUI() {
        Column(modifier = Modifier.fillMaxWidth()) {
            val textColor = MaterialTheme.colorScheme.onSurface
            Text(titleText, maxLines = 1, color = textColor, style = MaterialTheme.typography.bodyMedium)
            Slider(value = sliderValue, valueRange = 0f..duration.toFloat(),
//                colors = SliderDefaults.colors(
//                thumbColor = MaterialTheme.colorScheme.secondary,
//                activeTrackColor = MaterialTheme.colorScheme.secondary,
//                inactiveTrackColor = Color.Gray,
//            ),
                modifier = Modifier.height(12.dp).padding(top = 2.dp, bottom = 2.dp),
                onValueChange = {
                    Logd(TAG, "Slider onValueChange: $it")
                    sliderValue = it
                }, onValueChangeFinished = {
                    Logd(TAG, "Slider onValueChangeFinished: $sliderValue")
                    currentPosition = sliderValue.toInt()
                    if (playbackService?.isServiceReady() == true) seekTo(currentPosition)
                })
            Row {
                Text(DurationConverter.getDurationStringLong(currentPosition), color = textColor, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                showTimeLeft = UserPreferences.shouldShowRemainingTime()
                Text(txtvLengtTexth, color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable {
                    if (controller == null) return@clickable
                    showTimeLeft = !showTimeLeft
                    UserPreferences.setShowRemainTimeSetting(showTimeLeft)
                    onPositionUpdate(FlowEvent.PlaybackPositionEvent(curMedia, curPositionFB, curDurationFB))
                })
            }
            Row {
                fun ensureService() {
                    if (curMedia == null) return
                    if (playbackService == null) PlaybackServiceStarter(requireContext(), curMedia!!).start()
                }
                AsyncImage(model = imgLoc, contentDescription = "imgvCover", placeholder = painterResource(R.mipmap.ic_launcher),
                    modifier = Modifier.width(70.dp).height(70.dp).padding(start = 5.dp)
                        .clickable(onClick = {
                            Logd(TAG, "icon clicked!")
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
                                    val intent = getPlayerActivityIntent(requireContext(), mediaType)
                                    startActivity(intent)
                                }
                            }
                        }))
                Spacer(Modifier.weight(0.1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painter = painterResource(R.drawable.ic_playback_speed), tint = textColor,
                        contentDescription = "speed",
                        modifier = Modifier.width(48.dp).height(48.dp).clickable(onClick = {
                            VariableSpeedDialog.newInstance(booleanArrayOf(true, true, true), null)?.show(childFragmentManager, null)
                        }))
                    Text(txtvPlaybackSpeed, color = textColor, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.weight(0.1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painter = painterResource(R.drawable.ic_fast_rewind), tint = textColor,
                        contentDescription = "rewind",
                        modifier = Modifier.width(48.dp).height(48.dp).combinedClickable(onClick = {
                            if (controller != null && playbackService?.isServiceReady() == true) {
                                playbackService?.mPlayer?.seekDelta(-UserPreferences.rewindSecs * 1000)
                            }
                        }, onLongClick = {
                            SkipPreferenceDialog.showSkipPreference(requireContext(), SkipPreferenceDialog.SkipDirection.SKIP_REWIND)
                        }))
                    Text(NumberFormat.getInstance().format(UserPreferences.rewindSecs.toLong()), color = textColor, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.weight(0.1f))
                Icon(painter = painterResource(playButRes), tint = textColor, contentDescription = "play",
                    modifier = Modifier.width(64.dp).height(64.dp).combinedClickable(onClick = {
                        if (controller == null) return@combinedClickable
                        if (curMedia != null) {
                            val media = curMedia!!
                            setIsShowPlay(!isShowPlay)
                            if (media.getMediaType() == MediaType.VIDEO && MediaPlayerBase.status != PlayerStatus.PLAYING &&
                                    (media is EpisodeMedia && media.episode?.feed?.preferences?.videoModePolicy != VideoMode.AUDIO_ONLY)) {
                                playPause()
                                requireContext().startActivity(getPlayerActivityIntent(requireContext(), curMedia!!.getMediaType()))
                            } else playPause()
                        }
                    }, onLongClick = {
                        if (controller != null && MediaPlayerBase.status == PlayerStatus.PLAYING) {
                            val fallbackSpeed = UserPreferences.fallbackSpeed
                            if (fallbackSpeed > 0.1f) toggleFallbackSpeed(fallbackSpeed)
                        }
                    }))
                Spacer(Modifier.weight(0.1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painter = painterResource(R.drawable.ic_fast_forward), tint = textColor,
                        contentDescription = "forward",
                        modifier = Modifier.width(48.dp).height(48.dp).combinedClickable(onClick = {
                            if (controller != null && playbackService?.isServiceReady() == true) {
                                playbackService?.mPlayer?.seekDelta(UserPreferences.fastForwardSecs * 1000)
                            }
                        }, onLongClick = {
                            SkipPreferenceDialog.showSkipPreference(requireContext(), SkipPreferenceDialog.SkipDirection.SKIP_FORWARD)
                        }))
                    Text(NumberFormat.getInstance().format(UserPreferences.fastForwardSecs.toLong()), color = textColor, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.weight(0.1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    fun speedForward(speed: Float) {
                        if (playbackService?.mPlayer == null || playbackService?.isFallbackSpeed == true) return
                        if (playbackService?.isSpeedForward == false) {
                            playbackService?.normalSpeed = playbackService?.mPlayer!!.getPlaybackSpeed()
                            playbackService?.mPlayer!!.setPlaybackParams(speed, isSkipSilence)
                        } else playbackService?.mPlayer?.setPlaybackParams(playbackService!!.normalSpeed, isSkipSilence)
                        playbackService!!.isSpeedForward = !playbackService!!.isSpeedForward
                    }
                    Icon(painter = painterResource(R.drawable.ic_skip_48dp), tint = textColor,
                        contentDescription = "rewind",
                        modifier = Modifier.width(48.dp).height(48.dp).combinedClickable(onClick = {
                            if (controller != null && MediaPlayerBase.status == PlayerStatus.PLAYING) {
                                val speedForward = UserPreferences.speedforwardSpeed
                                if (speedForward > 0.1f) speedForward(speedForward)
                            }
                        }, onLongClick = {
                            activity?.sendBroadcast(MediaButtonReceiver.createIntent(requireContext(), KeyEvent.KEYCODE_MEDIA_NEXT))
                        }))
                    if (UserPreferences.speedforwardSpeed > 0.1f) Text(NumberFormat.getInstance().format(UserPreferences.speedforwardSpeed), color = textColor, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.weight(0.1f))
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun DetailUI() {
        var showChooseRatingDialog by remember { mutableStateOf(false) }
        if (showChooseRatingDialog) ChooseRatingDialog(listOf(currentItem!!)) {
            showChooseRatingDialog = false
        }

        val scrollState = rememberScrollState()
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            fun copyText(text: String): Boolean {
                val clipboardManager: ClipboardManager? = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
                clipboardManager?.setPrimaryClip(ClipData.newPlainText("Podcini", text))
                if (Build.VERSION.SDK_INT <= 32) {
                    (requireActivity() as MainActivity).showSnackbarAbovePlayer(resources.getString(R.string.copied_to_clipboard), Snackbar.LENGTH_SHORT)
                }
                return true
            }
            Text(txtvPodcastTitle, textAlign = TextAlign.Center, color = textColor, style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 5.dp).combinedClickable(onClick = {
                    if (currentMedia is EpisodeMedia) {
                        if (currentItem?.feedId != null) {
                            val openFeed: Intent = MainActivity.getIntentToOpenFeed(requireContext(), currentItem!!.feedId!!)
                            startActivity(openFeed)
                        }
                    }
                }, onLongClick = { copyText(currentMedia?.getFeedTitle()?:"") }))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp), ) {
                Spacer(modifier = Modifier.weight(0.2f))
                var ratingIconRes = Episode.Rating.fromCode(rating).res
                Icon(painter = painterResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.width(15.dp).height(15.dp).clickable(onClick = {
                    showChooseRatingDialog = true
                }))
                Spacer(modifier = Modifier.weight(0.4f))
                Text(episodeDate, textAlign = TextAlign.Center, color = textColor, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.weight(0.6f))
            }
            Text(titleText, textAlign = TextAlign.Center, color = textColor, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 5.dp)
                .combinedClickable(onClick = {}, onLongClick = { copyText(currentItem?.title?:"") }))
            fun restoreFromPreference(): Boolean {
                if ((activity as MainActivity).bottomSheet.state != BottomSheetBehavior.STATE_EXPANDED) return false
                Logd(TAG, "Restoring from preferences")
                val activity: Activity? = activity
                if (activity != null) {
                    val id = prefs!!.getString(PREF_PLAYABLE_ID, "")
                    val scrollY = prefs!!.getInt(PREF_SCROLL_Y, -1)
                    if (scrollY != -1) {
                        if (id == curMedia?.getIdentifier()?.toString()) {
                            Logd(TAG, "Restored scroll Position: $scrollY")
//                            binding.itemDescriptionFragment.scrollTo(binding.itemDescriptionFragment.scrollX, scrollY)
                            return true
                        }
                        Logd(TAG, "reset scroll Position: 0")
//                        binding.itemDescriptionFragment.scrollTo(0, 0)
                        return true
                    }
                }
                return false
            }
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                ShownotesWebView(context).apply {
                    setTimecodeSelectedListener { time: Int -> seekTo(time) }
//                    setPageFinishedListener {
//                        // Restoring the scroll position might not always work
//                        postDelayed({ restoreFromPreference() }, 50)
//                    }
                }
            }, update = { webView ->
                Logd(TAG, "AndroidView update: $cleanedNotes")
                webView.loadDataWithBaseURL("https://127.0.0.1", cleanedNotes?:"No notes", "text/html", "utf-8", "about:blank")
            })
            if (chapterControlVisible) {
                Row {
                    Icon(painter = painterResource(R.drawable.ic_chapter_prev), tint = textColor, contentDescription = "prev_chapter",
                        modifier = Modifier.width(36.dp).height(36.dp).padding(end = 10.dp).clickable(onClick = { seekToPrevChapter() }))
                    Text(stringResource(id = R.string.chapters_label), modifier = Modifier.weight(1f)
                        .clickable(onClick = { ChaptersFragment().show(childFragmentManager, ChaptersFragment.TAG) }))
                    if (hasNextChapter) Icon(painter = painterResource(R.drawable.ic_chapter_prev), tint = textColor, contentDescription = "prev_chapter",
                        modifier = Modifier.width(36.dp).height(36.dp).padding(end = 10.dp).clickable(onClick = { seekToNextChapter() }))
                }
            }
            AsyncImage(model = imgLoc, contentDescription = "imgvCover", placeholder = painterResource(R.mipmap.ic_launcher),
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 10.dp).clickable(onClick = {
                }))
        }
    }

    fun setIsShowPlay(showPlay: Boolean) {
        Logd(TAG, "setIsShowPlay: ${isShowPlay} $showPlay")
        if (isShowPlay != showPlay) {
            isShowPlay = showPlay
            playButRes = when {
                isVideoScreen -> if (showPlay) R.drawable.ic_play_video_white else R.drawable.ic_pause_video_white
                showPlay -> R.drawable.ic_play_48dp
                else -> R.drawable.ic_pause
            }
        }
    }
    private fun updatePlaybackSpeedButton(event: FlowEvent.SpeedChangedEvent) {
        val speedStr: String = DecimalFormat("0.00").format(event.newSpeed.toDouble())
        txtvPlaybackSpeed = speedStr
//            binding.butPlaybackSpeed.setSpeed(event.newSpeed) TODO
    }
    @UnstableApi
    fun onPositionUpdate(event: FlowEvent.PlaybackPositionEvent) {
        if (curMedia?.getIdentifier() != event.media?.getIdentifier() || controller == null || curPositionFB == Playable.INVALID_TIME || curDurationFB == Playable.INVALID_TIME) return
        val converter = TimeSpeedConverter(curSpeedFB)
        currentPosition = converter.convert(event.position)
        duration = converter.convert(event.duration)
        val remainingTime: Int = converter.convert(max((event.duration - event.position).toDouble(), 0.0).toInt())
        if (currentPosition == Playable.INVALID_TIME || duration == Playable.INVALID_TIME) {
            Log.w(TAG, "Could not react to position observer update because of invalid time")
            return
        }
        showTimeLeft = UserPreferences.shouldShowRemainingTime()
        txtvLengtTexth = if (showTimeLeft) {
            (if (remainingTime > 0) "-" else "") + DurationConverter.getDurationStringLong(remainingTime)
        } else DurationConverter.getDurationStringLong(duration)

//        val progress: Float = (event.position.toFloat()) / event.duration
        sliderValue = event.position.toFloat()
    }
    private fun onPlaybackServiceChanged(event: FlowEvent.PlaybackServiceEvent) {
        when (event.action) {
            FlowEvent.PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN -> (activity as MainActivity).setPlayerVisible(false)
            FlowEvent.PlaybackServiceEvent.Action.SERVICE_STARTED -> if (curMedia != null) (activity as MainActivity).setPlayerVisible(true)
//                PlaybackServiceEvent.Action.SERVICE_RESTARTED -> (activity as MainActivity).setPlayerVisible(true)
        }
    }
    @UnstableApi
    fun updateUi(media: Playable) {
        Logd(TAG, "updateUi called $media")
        titleText = media.getEpisodeTitle()
//            (activity as MainActivity).setPlayerVisible(true)
        onPositionUpdate(FlowEvent.PlaybackPositionEvent(media, media.getPosition(), media.getDuration()))
        if (prevMedia?.getIdentifier() != media.getIdentifier()) {
            imgLoc = ImageResourceUtils.getEpisodeListImageLocation(media)
//                val imgLocFB = ImageResourceUtils.getFallbackImageLocation(media)
//                val imageLoader = imgvCover.context.imageLoader
//                val imageRequest = ImageRequest.Builder(requireContext())
//                    .data(imgLoc)
//                    .setHeader("User-Agent", "Mozilla/5.0")
//                    .placeholder(R.color.light_gray)
//                    .listener(object : ImageRequest.Listener {
//                        override fun onError(request: ImageRequest, result: ErrorResult) {
//                            val fallbackImageRequest = ImageRequest.Builder(requireContext())
//                                .data(imgLocFB)
//                                .setHeader("User-Agent", "Mozilla/5.0")
//                                .error(R.mipmap.ic_launcher)
//                                .target(imgvCover)
//                                .build()
//                            imageLoader.enqueue(fallbackImageRequest)
//                        }
//                    })
//                    .target(imgvCover)
//                    .build()
//                imageLoader.enqueue(imageRequest)
        }
        if (isPlayingVideoLocally && (curMedia as? EpisodeMedia)?.episode?.feed?.preferences?.videoModePolicy != VideoMode.AUDIO_ONLY) {
            (activity as MainActivity).bottomSheet.setLocked(true)
            (activity as MainActivity).bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
        } else {
            (activity as MainActivity).bottomSheet.setLocked(false)
        }
        prevMedia = media
    }

    internal fun updateDetails() {
//        if (isLoading) return
        lifecycleScope.launch {
            Logd(TAG, "in updateInfo")
            isLoading = true
            withContext(Dispatchers.IO) {
                currentMedia = curMedia
                if (currentMedia != null && currentMedia is EpisodeMedia) {
                    val episodeMedia = currentMedia as EpisodeMedia
                    currentItem = episodeMedia.episodeOrFetch()
                    showHomeText = false
                    homeText = null
                }
                if (currentItem != null) {
                    currentMedia = currentItem!!.media
                    if (prevItem?.identifyingValue != currentItem!!.identifyingValue) cleanedNotes = null
                    Logd(TAG, "updateInfo ${cleanedNotes == null} ${prevItem?.identifyingValue} ${currentItem!!.identifyingValue}")
                    if (cleanedNotes == null) {
                        Logd(TAG, "calling load description ${currentItem!!.description==null} ${currentItem!!.title}")
                        cleanedNotes = shownotesCleaner?.processShownotes(currentItem?.description ?: "", currentMedia?.getDuration()?:0)
                    }
                    prevItem = currentItem
                }
            }
            withContext(Dispatchers.Main) {
                Logd(TAG, "subscribe: ${currentMedia?.getEpisodeTitle()}")
                displayMediaInfo(currentMedia!!)
//                    shownoteView.loadDataWithBaseURL("https://127.0.0.1", cleanedNotes?:"No notes", "text/html", "utf-8", "about:blank")
                Logd(TAG, "Webview loaded")
            }
        }.invokeOnCompletion { throwable ->
            isLoading = false
            if (throwable != null) Log.e(TAG, Log.getStackTraceString(throwable))
        }
    }

    private fun buildHomeReaderText() {
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
//                            shownoteView.loadDataWithBaseURL("https://127.0.0.1",
//                                cleanedNotes ?: "No notes",
//                                "text/html",
//                                "UTF-8",
//                                null)
                    }
                } else withContext(Dispatchers.Main) { Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show() }
            } else {
//                val shownotesCleaner = ShownotesCleaner(requireContext())
                cleanedNotes = shownotesCleaner?.processShownotes(currentItem?.description ?: "", currentMedia?.getDuration() ?: 0)
                if (!cleanedNotes.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
//                            shownoteView.loadDataWithBaseURL("https://127.0.0.1",
//                                cleanedNotes ?: "No notes",
//                                "text/html",
//                                "UTF-8",
//                                null)
                    }
                } else withContext(Dispatchers.Main) { Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun displayMediaInfo(media: Playable) {
        Logd(TAG, "displayMediaInfo ${currentItem?.title} ${media.getEpisodeTitle()}")
        val pubDateStr = MiscFormatter.formatAbbrev(context, media.getPubDate())
        txtvPodcastTitle = StringUtils.stripToEmpty(media.getFeedTitle())
        episodeDate = StringUtils.stripToEmpty(pubDateStr)
        titleText = currentItem?.title ?:""
        displayedChapterIndex = -1
        refreshChapterData(ChapterUtils.getCurrentChapterIndex(media, media.getPosition())) //calls displayCoverImage
        updateChapterControlVisibility()
    }

    private fun updateChapterControlVisibility() {
        when {
            currentMedia?.getChapters() != null -> chapterControlVisible = currentMedia!!.getChapters().isNotEmpty()
            currentMedia is EpisodeMedia -> {
                val item_ = (currentMedia as EpisodeMedia).episodeOrFetch()
                // If an item has chapters but they are not loaded yet, still display the button.
                chapterControlVisible = !item_?.chapters.isNullOrEmpty()
            }
        }
//            val newVisibility = if (chapterControlVisible) View.VISIBLE else View.GONE
//            if (binding.chapterButton.visibility != newVisibility) {
//                binding.chapterButton.visibility = newVisibility
//                ObjectAnimator.ofFloat(binding.chapterButton, "alpha",
//                    (if (chapterControlVisible) 0 else 1).toFloat(), (if (chapterControlVisible) 1 else 0).toFloat()).start()
//            }
    }

    private fun refreshChapterData(chapterIndex: Int) {
        Logd(TAG, "in refreshChapterData $chapterIndex")
        if (currentMedia != null && chapterIndex > -1) {
            if (currentMedia!!.getPosition() > currentMedia!!.getDuration() || chapterIndex >= currentMedia!!.getChapters().size - 1) {
                displayedChapterIndex = currentMedia!!.getChapters().size - 1
                hasNextChapter = false
            } else {
                displayedChapterIndex = chapterIndex
                hasNextChapter = true
            }
        }
        displayCoverImage()
    }

    private fun displayCoverImage() {
        if (currentMedia == null) return
        if (displayedChapterIndex == -1 || currentMedia!!.getChapters().isEmpty() || currentMedia!!.getChapters()[displayedChapterIndex].imageUrl.isNullOrEmpty()) {
            imgLoc = currentMedia!!.getImageLocation()
//                val imageLoader = binding.imgvCover.context.imageLoader
//                val imageRequest = ImageRequest.Builder(requireContext())
//                    .data(playable!!.getImageLocation())
//                    .setHeader("User-Agent", "Mozilla/5.0")
//                    .placeholder(R.color.light_gray)
//                    .listener(object : ImageRequest.Listener {
//                        override fun onError(request: ImageRequest, result: ErrorResult) {
//                            val fallbackImageRequest = ImageRequest.Builder(requireContext())
//                                .data(ImageResourceUtils.getFallbackImageLocation(playable!!))
//                                .setHeader("User-Agent", "Mozilla/5.0")
//                                .error(R.mipmap.ic_launcher)
//                                .target(binding.imgvCover)
//                                .build()
//                            imageLoader.enqueue(fallbackImageRequest)
//                        }
//                    })
//                    .target(binding.imgvCover)
//                    .build()
//                imageLoader.enqueue(imageRequest)
        } else {
            imgLoc = EmbeddedChapterImage.getModelFor(currentMedia!!, displayedChapterIndex)?.toString()
//                val imageLoader = binding.imgvCover.context.imageLoader
//                val imageRequest = ImageRequest.Builder(requireContext())
//                    .data(imgLoc)
//                    .setHeader("User-Agent", "Mozilla/5.0")
//                    .placeholder(R.color.light_gray)
//                    .listener(object : ImageRequest.Listener {
//                        override fun onError(request: ImageRequest, result: ErrorResult) {
//                            val fallbackImageRequest = ImageRequest.Builder(requireContext())
//                                .data(ImageResourceUtils.getFallbackImageLocation(playable!!))
//                                .setHeader("User-Agent", "Mozilla/5.0")
//                                .error(R.mipmap.ic_launcher)
//                                .target(binding.imgvCover)
//                                .build()
//                            imageLoader.enqueue(fallbackImageRequest)
//                        }
//                    })
//                    .target(binding.imgvCover)
//                    .build()
//                imageLoader.enqueue(imageRequest)
        }
        Logd(TAG, "displayCoverImage: imgLoc: $imgLoc")
    }

    @UnstableApi private fun seekToPrevChapter() {
        val curr: Chapter? = currentChapter
        if (curr == null || displayedChapterIndex == -1) return

        when {
            displayedChapterIndex < 1 -> seekTo(0)
            (curPositionFB - 10000 * curSpeedFB) < curr.start -> {
                refreshChapterData(displayedChapterIndex - 1)
                if (currentMedia != null) seekTo(currentMedia!!.getChapters()[displayedChapterIndex].start.toInt())
            }
            else -> seekTo(curr.start.toInt())
        }
    }

    @UnstableApi private fun seekToNextChapter() {
        if (currentMedia == null || currentMedia!!.getChapters().isEmpty() || displayedChapterIndex == -1 || displayedChapterIndex + 1 >= currentMedia!!.getChapters().size) return
        refreshChapterData(displayedChapterIndex + 1)
        seekTo(currentMedia!!.getChapters()[displayedChapterIndex].start.toInt())
    }

    @UnstableApi private fun savePreference() {
        Logd(TAG, "Saving preferences")
        val editor = prefs?.edit() ?: return
        if (curMedia != null) {
//            Logd(TAG, "Saving scroll position: " + binding.itemDescriptionFragment.scrollY)
//            editor.putInt(PREF_SCROLL_Y, binding.itemDescriptionFragment.scrollY)
            editor.putString(PREF_PLAYABLE_ID, curMedia!!.getIdentifier().toString())
        } else {
            Logd(TAG, "savePreferences was called while media or webview was null")
            editor.putInt(PREF_SCROLL_Y, -1)
            editor.putString(PREF_PLAYABLE_ID, "")
        }
        editor.apply()
    }

    fun onExpanded() {
        Logd(TAG, "onExpanded()")
//        the function can also be called from MainActivity when a select menu pops up and closes
//        if (isCollapsed) {
            isCollapsed = false
            if (shownotesCleaner == null) shownotesCleaner = ShownotesCleaner(requireContext())
            showPlayer1 = false
            if (currentMedia != null) updateUi(currentMedia!!)
            setIsShowPlay(isShowPlay)
            updateDetails()
//        }
    }

    fun onCollaped() {
        Logd(TAG, "onCollaped()")
        isCollapsed = true
        showPlayer1 = true
        if (currentMedia != null) updateUi(currentMedia!!)
        setIsShowPlay(isShowPlay)
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
            if (!isCollapsed && (currentMedia == null || curMedia?.getIdentifier() != currentMedia?.getIdentifier())) updateDetails()

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
                    if (item != null) setItem(item)
                    setChapterDividers()
                    setupOptionsMenu()
                    if (currentMedia != null) updateUi(currentMedia!!)
//                TODO: disable for now
//                if (!includingChapters) loadMediaInfo(true)
                }.invokeOnCompletion { throwable ->
                    if (throwable != null) Log.e(TAG, Log.getStackTraceString(throwable))
                    loadItemsRunning = false
                }
            }
        }
    }

    private fun setItem(item_: Episode) {
        Logd(TAG, "setItem ${item_.title}")
        if (currentItem?.identifyingValue != item_.identifyingValue) {
            currentItem = item_
            showHomeText = false
            homeText = null
        }
    }

    private fun createHandler(): ServiceStatusHandler {
        return object : ServiceStatusHandler(requireActivity()) {
            override fun updatePlayButton(showPlay: Boolean) {
//                isShowPlay = showPlay
                setIsShowPlay(showPlay)
            }
            override fun loadMediaInfo() {
                this@AudioPlayerFragment.loadMediaInfo(false)
                if (!isCollapsed) updateDetails()
            }
            override fun onPlaybackEnd() {
//                isShowPlay = true
                setIsShowPlay(true)
                (activity as MainActivity).setPlayerVisible(false)
            }
        }
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
    }

    @UnstableApi override fun onPause() {
        super.onPause()
        savePreference()
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
            setItem(currentitem)
        }
        (activity as MainActivity).setPlayerVisible(true)
        setIsShowPlay(event.action == FlowEvent.PlayEvent.Action.END)
    }

    private fun onPlaybackPositionEvent(event: FlowEvent.PlaybackPositionEvent) {
//        Logd(TAG, "onPlaybackPositionEvent ${event.episode.title}")
        val media = event.media ?: return
        if (currentMedia?.getIdentifier() == null || media.getIdentifier() != currentMedia?.getIdentifier()) {
            currentMedia = media
            updateUi(currentMedia!!)
            setItem(curEpisode!!)
        }
//        if (isShowPlay) setIsShowPlay(false)
        onPositionUpdate(event)
        if (!isCollapsed) {
            if (currentMedia?.getIdentifier() != event.media.getIdentifier()) return
            val newChapterIndex: Int = ChapterUtils.getCurrentChapterIndex(currentMedia, event.position)
            if (newChapterIndex >= 0 && newChapterIndex != displayedChapterIndex) refreshChapterData(newChapterIndex)
        }
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
                        onPlaybackServiceChanged(event)
                    }
                    is FlowEvent.PlayEvent -> onPlayEvent(event)
                    is FlowEvent.RatingEvent -> onRatingEvent(event)
                    is FlowEvent.PlayerErrorEvent -> MediaPlayerErrorDialog.show(activity as Activity, event)
//                    is FlowEvent.SleepTimerUpdatedEvent ->  if (event.isCancelled || event.wasJustEnabled()) loadMediaInfo(false)
                    is FlowEvent.SleepTimerUpdatedEvent ->  if (event.isCancelled || event.wasJustEnabled()) setupOptionsMenu()
                    is FlowEvent.PlaybackPositionEvent -> onPlaybackPositionEvent(event)
                    is FlowEvent.SpeedChangedEvent -> updatePlaybackSpeedButton(event)
                    else -> {}
                }
            }
        }
    }

    private fun onRatingEvent(event: FlowEvent.RatingEvent) {
        if (curEpisode?.id == event.episode.id) {
            rating = event.rating
            EpisodeMenuHandler.onPrepareMenu(toolbar.menu, event.episode)
        }
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
                if (showHomeText) menuItem.setIcon(R.drawable.ic_home)
                else menuItem.setIcon(R.drawable.outline_home_24)
                buildHomeReaderText()
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
                val notes = if (showHomeText) readerhtml else feedItem?.description
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
//        binding.itemDescriptionFragment.scrollTo(0, 0)
        savePreference()
    }

    fun fadePlayerToToolbar(slideOffset: Float) {
        val playerFadeProgress = (max(0.0, min(0.2, (slideOffset - 0.2f).toDouble())) / 0.2f).toFloat()
        val player = binding.player1
        player.alpha = 1 - playerFadeProgress
        player.visibility = if (playerFadeProgress > 0.99f) View.GONE else View.VISIBLE
        val toolbarFadeProgress = (max(0.0, min(0.2, (slideOffset - 0.6f).toDouble())) / 0.2f).toFloat()
        toolbar.setAlpha(toolbarFadeProgress)
        toolbar.visibility = if (toolbarFadeProgress < 0.01f) View.GONE else View.VISIBLE
    }

    companion object {
        val TAG = AudioPlayerFragment::class.simpleName ?: "Anonymous"
        var media3Controller: MediaController? = null

        private const val PREF = "ItemDescriptionFragmentPrefs"
        private const val PREF_SCROLL_Y = "prefScrollY"
        private const val PREF_PLAYABLE_ID = "prefPlayableId"

        var prefs: SharedPreferences? = null
        fun getSharedPrefs(context: Context) {
            if (prefs == null) prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        }
    }
}
