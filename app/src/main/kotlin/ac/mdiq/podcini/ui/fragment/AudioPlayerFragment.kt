package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.utils.NetworkUtils.fetchHtmlSource
import ac.mdiq.podcini.playback.PlaybackServiceStarter
import ac.mdiq.podcini.playback.ServiceStatusHandler
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.playback.base.InTheatre.isCurrentlyPlaying
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.status
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
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.utils.ChapterUtils
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.storage.utils.ImageResourceUtils
import ac.mdiq.podcini.storage.utils.TimeSpeedConverter
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.Companion.videoMode
import ac.mdiq.podcini.ui.activity.starter.VideoPlayerActivityStarter
import ac.mdiq.podcini.ui.compose.ChaptersDialog
import ac.mdiq.podcini.ui.compose.ChooseRatingDialog
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.PlaybackSpeedFullDialog
import ac.mdiq.podcini.ui.compose.SkipDialog
import ac.mdiq.podcini.ui.compose.SkipDirection
import ac.mdiq.podcini.ui.dialog.*
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.view.ShownotesWebView
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter
import ac.mdiq.podcini.util.MiscFormatter.formatLargeInteger
import android.app.Activity
import android.content.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope

import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import net.dankito.readability4j.Readability4J
import java.text.DecimalFormat
import java.text.NumberFormat
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class AudioPlayerFragment : Fragment() {
    val prefs: SharedPreferences by lazy { requireContext().getSharedPreferences("AudioPlayerFragmentPrefs", Context.MODE_PRIVATE) }

    private var isCollapsed by mutableStateOf(true)

//    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: ServiceStatusHandler? = null

    private var prevMedia: Playable? = null
    private var currentMedia by mutableStateOf<Playable?>(null)
    private var prevItem: Episode? = null
    private var currentItem: Episode? = null

    private var playButInit = false
    private var isShowPlay: Boolean = true

    private var showTimeLeft = false
    private var titleText by mutableStateOf("")
    private var imgLoc by mutableStateOf<String?>(null)
    private var imgLocLarge by mutableStateOf<String?>(null)
    private var txtvPlaybackSpeed by mutableStateOf("")
    private var curPlaybackSpeed by mutableStateOf(1f)
    private var remainingTime by mutableIntStateOf(0)
    private var isVideoScreen = false
    private var playButRes by mutableIntStateOf(R.drawable.ic_play_48dp)
    private var currentPosition by mutableIntStateOf(0)
    private var duration by mutableIntStateOf(0)
    private var txtvLengtTexth by mutableStateOf("")
    private var sliderValue by mutableFloatStateOf(0f)
    private var sleepTimerActive by mutableStateOf(isSleepTimerActive())
    private var showSpeedDialog by mutableStateOf(false)

    private var shownotesCleaner: ShownotesCleaner? = null

    private var cleanedNotes by mutableStateOf<String?>(null)
    private var isLoading = false
    private var homeText: String? = null
    private var showHomeText = false
    private var readerhtml: String? = null
    private var txtvPodcastTitle by mutableStateOf("")
    private var episodeDate by mutableStateOf("")
//    private var chapterControlVisible by mutableStateOf(false)
    private var hasNextChapter by mutableStateOf(true)
    var rating by mutableStateOf(currentItem?.rating ?: Rating.UNRATED.code)

    private var displayedChapterIndex by mutableIntStateOf(-1)
    private val currentChapter: Chapter?
        get() {
            if (currentMedia == null || currentMedia!!.getChapters().isEmpty() || displayedChapterIndex == -1) return null
            return currentMedia!!.getChapters()[displayedChapterIndex]
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        Logd(TAG, "fragment onCreateView")
        controller = createHandler()
        controller!!.init()
        onCollaped()

        val composeView = ComposeView(requireContext()).apply {
            setContent {
                CustomTheme(requireContext()) {
                    if (showSpeedDialog) PlaybackSpeedFullDialog(settingCode = booleanArrayOf(true, true, true), indexDefault = 0, maxSpeed = 3f, onDismiss = {showSpeedDialog = false})
                    Box(modifier = Modifier.fillMaxWidth().then(if (isCollapsed) Modifier else Modifier.statusBarsPadding().navigationBarsPadding())) {
                        PlayerUI(Modifier.align(if (isCollapsed) Alignment.TopCenter else Alignment.BottomCenter).zIndex(1f))
                        if (!isCollapsed) {
                            Column(Modifier.padding(bottom = 120.dp)) {
                                Toolbar()
                                DetailUI(modifier = Modifier)
                            }
                        }
                    }
                }
            }
        }
        Logd(TAG, "curMedia: ${curMedia?.getIdentifier()}")
        (activity as MainActivity).setPlayerVisible(curMedia != null)
        if (curMedia != null) updateUi(curMedia!!)
//        if (curMedia is EpisodeMedia) setIsShowPlay(isCurrentlyPlaying(curMedia as EpisodeMedia))
        return composeView
    }

    override fun onDestroyView() {
        Logd(TAG, "Fragment destroyed")
        controller?.release()
        controller = null
//        MediaController.releaseFuture(controllerFuture)
        super.onDestroyView()
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun ControlUI() {
        val textColor = MaterialTheme.colorScheme.onSurface
        val context = LocalContext.current

        @Composable
        fun SpeedometerWithArc(speed: Float, maxSpeed: Float, trackColor: Color, modifier: Modifier) {
            val needleAngle = (speed / maxSpeed) * 270f - 225
            Canvas(modifier = modifier) {
                val radius = 1.3 * size.minDimension / 2
                val strokeWidth = 6.dp.toPx()
                val arcRect = Rect(left = strokeWidth / 2, top = strokeWidth / 2, right = size.width - strokeWidth / 2, bottom = size.height - strokeWidth / 2)
                drawArc(color = trackColor, startAngle = 135f, sweepAngle = 270f, useCenter = false, style = Stroke(width = strokeWidth), topLeft = arcRect.topLeft, size = arcRect.size)
                val needleAngleRad = Math.toRadians(needleAngle.toDouble())
                val needleEnd = Offset(x = size.center.x + (radius * 0.7f * cos(needleAngleRad)).toFloat(), y = size.center.y + (radius * 0.7f * sin(needleAngleRad)).toFloat())
                drawLine(color = Color.Red, start = size.center, end = needleEnd, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(color = Color.Cyan, center = size.center, radius = 3.dp.toPx())
            }
        }

        Row {
            fun ensureService() {
                if (curMedia == null) return
                if (playbackService == null) PlaybackServiceStarter(requireContext(), curMedia!!).start()
            }
            val imgLoc_ = remember(currentMedia) { imgLoc }
            AsyncImage(contentDescription = "imgvCover", model = ImageRequest.Builder(context).data(imgLoc_)
                .memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(),
                modifier = Modifier.width(65.dp).height(65.dp).padding(start = 5.dp)
                    .clickable(onClick = {
                        Logd(TAG, "playerUiFragment icon was clicked")
                        if (isCollapsed) {
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
                        } else (activity as MainActivity).bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
                    }))
            Spacer(Modifier.weight(0.1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SpeedometerWithArc(speed = curPlaybackSpeed*100, maxSpeed = 300f, trackColor = textColor,
                    modifier = Modifier.width(43.dp).height(43.dp).clickable(onClick = { showSpeedDialog = true }))
                Text(txtvPlaybackSpeed, color = textColor, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.weight(0.1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                var showSkipDialog by remember { mutableStateOf(false) }
                var rewindSecs by remember { mutableStateOf(NumberFormat.getInstance().format(UserPreferences.rewindSecs.toLong())) }
                if (showSkipDialog) SkipDialog(SkipDirection.SKIP_REWIND, onDismissRequest = { showSkipDialog = false }) { rewindSecs = it.toString() }
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_fast_rewind), tint = textColor, contentDescription = "rewind",
                    modifier = Modifier.width(43.dp).height(43.dp).combinedClickable(
                        onClick = { playbackService?.mPlayer?.seekDelta(-UserPreferences.rewindSecs * 1000) },
                        onLongClick = { showSkipDialog = true }))
                Text(rewindSecs, color = textColor, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.weight(0.1f))
            Icon(imageVector = ImageVector.vectorResource(playButRes), tint = textColor, contentDescription = "play",
                modifier = Modifier.width(64.dp).height(64.dp).combinedClickable(
                    onClick = {
                        if (controller == null) return@combinedClickable
                        if (curMedia != null) {
                            val media = curMedia!!
                            setIsShowPlay(!isShowPlay)
                            if (media.getMediaType() == MediaType.VIDEO && status != PlayerStatus.PLAYING &&
                                    (media is EpisodeMedia && media.episode?.feed?.preferences?.videoModePolicy != VideoMode.AUDIO_ONLY)) {
                                playPause()
                                requireContext().startActivity(getPlayerActivityIntent(requireContext(), curMedia!!.getMediaType()))
                            } else playPause()
                        } },
                    onLongClick = {
                        if (status == PlayerStatus.PLAYING) {
                            val fallbackSpeed = UserPreferences.fallbackSpeed
                            if (fallbackSpeed > 0.1f) toggleFallbackSpeed(fallbackSpeed)
                        } }))
            Spacer(Modifier.weight(0.1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                var showSkipDialog by remember { mutableStateOf(false) }
                var fastForwardSecs by remember { mutableStateOf(NumberFormat.getInstance().format(UserPreferences.fastForwardSecs.toLong())) }
                if (showSkipDialog) SkipDialog(SkipDirection.SKIP_FORWARD, onDismissRequest = {showSkipDialog = false }) { fastForwardSecs = it.toString()}
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_fast_forward), tint = textColor, contentDescription = "forward",
                    modifier = Modifier.width(43.dp).height(43.dp).combinedClickable(
                        onClick = { playbackService?.mPlayer?.seekDelta(UserPreferences.fastForwardSecs * 1000) },
                        onLongClick = { showSkipDialog = true }))
                Text(fastForwardSecs, color = textColor, style = MaterialTheme.typography.bodySmall)
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
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_skip_48dp), tint = textColor, contentDescription = "rewind",
                    modifier = Modifier.width(43.dp).height(43.dp).combinedClickable(
                        onClick = {
                            if (status == PlayerStatus.PLAYING) {
                                val speedForward = UserPreferences.speedforwardSpeed
                                if (speedForward > 0.1f) speedForward(speedForward)
                            } },
                        onLongClick = { activity?.sendBroadcast(MediaButtonReceiver.createIntent(requireContext(), KeyEvent.KEYCODE_MEDIA_NEXT)) }))
                if (UserPreferences.speedforwardSpeed > 0.1f) Text(NumberFormat.getInstance().format(UserPreferences.speedforwardSpeed), color = textColor, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.weight(0.1f))
        }
    }

    @Composable
    fun ProgressBar() {
        val textColor = MaterialTheme.colorScheme.onSurface
        Slider(value = sliderValue, valueRange = 0f..duration.toFloat(),
            modifier = Modifier.height(12.dp).padding(top = 2.dp, bottom = 2.dp),
            onValueChange = {
                Logd(TAG, "Slider onValueChange: $it")
                sliderValue = it
            }, onValueChangeFinished = {
                Logd(TAG, "Slider onValueChangeFinished: $sliderValue")
                currentPosition = sliderValue.toInt()
//                if (playbackService?.isServiceReady() == true) seekTo(currentPosition)
                seekTo(currentPosition)
            })
        Row {
            Text(DurationConverter.getDurationStringLong(currentPosition), color = textColor, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            val bitrate = (curMedia as? EpisodeMedia)?.bitrate ?: 0
            if (bitrate > 0) Text(formatLargeInteger(bitrate) + "bits", color = textColor, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            showTimeLeft = UserPreferences.shouldShowRemainingTime()
            Text(txtvLengtTexth, color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable {
                if (controller == null) return@clickable
                showTimeLeft = !showTimeLeft
                UserPreferences.setShowRemainTimeSetting(showTimeLeft)
                onPositionUpdate(FlowEvent.PlaybackPositionEvent(curMedia, curPositionFB, curDurationFB))
            })
        }
    }

    @Composable
    fun PlayerUI(modifier: Modifier) {
        val textColor = MaterialTheme.colorScheme.onSurface
        Column(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            Text(titleText, maxLines = 1, color = textColor, style = MaterialTheme.typography.bodyMedium)
            ProgressBar()
            ControlUI()
        }
    }

    @Composable
    fun VolumeAdaptionDialog(showDialog: Boolean, onDismissRequest: () -> Unit) {
        if (showDialog) {
            val (selectedOption, onOptionSelected) = remember { mutableStateOf((currentMedia as? EpisodeMedia)?.volumeAdaptionSetting ?: VolumeAdaptionSetting.OFF) }
            Dialog(onDismissRequest = { onDismissRequest() }) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column {
                            VolumeAdaptionSetting.entries.forEach { item ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = (item == selectedOption),
                                        onCheckedChange = { _ ->
                                            Logd(TAG, "row clicked: $item $selectedOption")
                                            if (item != selectedOption) {
                                                onOptionSelected(item)
                                                if (currentMedia is EpisodeMedia) {
                                                    (currentMedia as? EpisodeMedia)?.volumeAdaptionSetting = item
                                                    currentMedia = currentItem!!.media
                                                    curMedia = currentMedia
                                                    playbackService?.mPlayer?.pause(false, reinit = true)
                                                    playbackService?.mPlayer?.resume()
                                                }
                                                onDismissRequest()
                                            }
                                        }
                                    )
                                    Text(text = stringResource(item.resId), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun Toolbar() {
        val media: Playable = curMedia ?: return
        val feedItem = if (media is EpisodeMedia) media.episodeOrFetch() else null
        val textColor = MaterialTheme.colorScheme.onSurface
        val mediaType = curMedia?.getMediaType()
        val notAudioOnly = (curMedia as? EpisodeMedia)?.episode?.feed?.preferences?.videoModePolicy != VideoMode.AUDIO_ONLY
        var showVolumeDialog by remember { mutableStateOf(false) }
        if (showVolumeDialog) VolumeAdaptionDialog(showVolumeDialog, onDismissRequest = { showVolumeDialog = false })
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_down), tint = textColor, contentDescription = "Collapse", modifier = Modifier.clickable {
                (activity as MainActivity).bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
            })
            var homeIcon by remember { mutableIntStateOf(R.drawable.baseline_home_24)}
            Icon(imageVector = ImageVector.vectorResource(homeIcon), tint = textColor, contentDescription = "Home", modifier = Modifier.clickable {
                homeIcon = if (showHomeText) R.drawable.ic_home else R.drawable.outline_home_24
                buildHomeReaderText()
            })
            if (mediaType == MediaType.VIDEO) Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_fullscreen_24), tint = textColor, contentDescription = "Play video",
                modifier = Modifier.clickable {
                    if (!notAudioOnly && (curMedia as? EpisodeMedia)?.forceVideo != true) {
                        (curMedia as? EpisodeMedia)?.forceVideo = true
                        status = PlayerStatus.STOPPED
                        playbackService?.mPlayer?.pause(true, reinit = true)
                        playbackService?.recreateMediaPlayer()
                    }
                    VideoPlayerActivityStarter(requireContext()).start()
                })
            if (controller != null) {
                val sleepRes = if (sleepTimerActive) R.drawable.ic_sleep_off else R.drawable.ic_sleep
                Icon(imageVector = ImageVector.vectorResource(sleepRes), tint = textColor, contentDescription = "Sleep timer", modifier = Modifier.clickable {
                    SleepTimerDialog().show(childFragmentManager, "SleepTimerDialog")
                })
            }
            if (currentMedia is EpisodeMedia) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_feed), tint = textColor, contentDescription = "Open podcast",
                modifier = Modifier.clickable {
                    if (feedItem?.feedId != null) {
                        val intent: Intent = MainActivity.getIntentToOpenFeed(requireContext(), feedItem.feedId!!)
                        startActivity(intent)
                    }
                })
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_share), tint = textColor, contentDescription = "Share", modifier = Modifier.clickable {
                if (currentItem != null) {
                    val shareDialog: ShareDialog = ShareDialog.newInstance(currentItem!!)
                    shareDialog.show((requireActivity().supportFragmentManager), "ShareEpisodeDialog")
                }
            })
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_volume_adaption), tint = textColor, contentDescription = "Volume adaptation", modifier = Modifier.clickable {
                if (currentItem != null) showVolumeDialog = true
            })
            Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_offline_share_24), tint = textColor, contentDescription = "Share Note", modifier = Modifier.clickable {
                val notes = if (showHomeText) readerhtml else feedItem?.description
                if (!notes.isNullOrEmpty()) {
                    val shareText = HtmlCompat.fromHtml(notes, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                    val context = requireContext()
                    val intent = ShareCompat.IntentBuilder(context).setType("text/plain").setText(shareText).setChooserTitle(R.string.share_notes_label).createChooserIntent()
                    context.startActivity(intent)
                }
            })
            (activity as? CastEnabledActivity)?.CastIconButton()
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun DetailUI(modifier: Modifier) {
        var showChooseRatingDialog by remember { mutableStateOf(false) }
        if (showChooseRatingDialog) ChooseRatingDialog(listOf(currentItem!!)) { showChooseRatingDialog = false }
        var showChaptersDialog by remember { mutableStateOf(false) }
        if (showChaptersDialog) ChaptersDialog(media = currentMedia!!, onDismissRequest = {showChaptersDialog = false})

        val scrollState = rememberScrollState()
        Column(modifier = modifier.fillMaxWidth().verticalScroll(scrollState)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            fun copyText(text: String): Boolean {
                val clipboardManager: ClipboardManager? = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
                clipboardManager?.setPrimaryClip(ClipData.newPlainText("Podcini", text))
                if (Build.VERSION.SDK_INT <= 32)
                    (requireActivity() as MainActivity).showSnackbarAbovePlayer(resources.getString(R.string.copied_to_clipboard), Snackbar.LENGTH_SHORT)
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
            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp)) {
                Spacer(modifier = Modifier.weight(0.2f))
                val ratingIconRes = Rating.fromCode(rating).res
                Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                    modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(24.dp).height(24.dp).clickable(onClick = {
                    showChooseRatingDialog = true
                }))
                Spacer(modifier = Modifier.weight(0.4f))
                Text(episodeDate, textAlign = TextAlign.Center, color = textColor, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.weight(0.6f))
            }
            Text(titleText, textAlign = TextAlign.Center, color = textColor, style = CustomTextStyles.titleCustom, modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 5.dp)
                .combinedClickable(onClick = {}, onLongClick = { copyText(currentItem?.title?:"") }))
//            fun restoreFromPreference(): Boolean {
//                if ((activity as MainActivity).bottomSheet.state != BottomSheetBehavior.STATE_EXPANDED) return false
//                Logd(TAG, "Restoring from preferences")
//                val activity: Activity? = activity
//                if (activity != null) {
//                    val id = prefs!!.getString(PREF_PLAYABLE_ID, "")
//                    val scrollY = prefs!!.getInt(PREF_SCROLL_Y, -1)
//                    if (scrollY != -1) {
//                        if (id == curMedia?.getIdentifier()?.toString()) {
//                            Logd(TAG, "Restored scroll Position: $scrollY")
////                            binding.itemDescriptionFragment.scrollTo(binding.itemDescriptionFragment.scrollX, scrollY)
//                            return true
//                        }
//                        Logd(TAG, "reset scroll Position: 0")
////                        binding.itemDescriptionFragment.scrollTo(0, 0)
//                        return true
//                    }
//                }
//                return false
//            }
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                ShownotesWebView(context).apply {
                    setTimecodeSelectedListener { time: Int -> seekTo(time) }
                    setPageFinishedListener {
                        // Restoring the scroll position might not always work
//                        postDelayed({ restoreFromPreference() }, 50)
                        postDelayed({ }, 50)
                    }
                }
            }, update = { webView -> webView.loadDataWithBaseURL("https://127.0.0.1", cleanedNotes?:"No notes", "text/html", "utf-8", "about:blank") })
            if (displayedChapterIndex >= 0) {
                Row(modifier = Modifier.padding(start = 20.dp, end = 20.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_chapter_prev), tint = textColor, contentDescription = "prev_chapter",
                        modifier = Modifier.width(36.dp).height(36.dp).clickable(onClick = { seekToPrevChapter() }))
                    Text("Ch " + displayedChapterIndex.toString() + ": " + currentChapter?.title,
                        color = textColor, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 10.dp, end = 10.dp)
//                        .clickable(onClick = { ChaptersFragment().show(childFragmentManager, ChaptersFragment.TAG) }))
                        .clickable(onClick = { showChaptersDialog = true }))
                    if (hasNextChapter) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_chapter_next), tint = textColor, contentDescription = "next_chapter",
                        modifier = Modifier.width(36.dp).height(36.dp).clickable(onClick = { seekToNextChapter() }))
                }
            }
            AsyncImage(model = imgLocLarge, contentDescription = "imgvCover", placeholder = painterResource(R.mipmap.ic_launcher), error = painterResource(R.mipmap.ic_launcher),
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 10.dp).clickable(onClick = {}))
        }
    }

    fun setIsShowPlay(showPlay: Boolean) {
        Logd(TAG, "setIsShowPlay: $isShowPlay $showPlay")
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
        curPlaybackSpeed = event.newSpeed
        txtvPlaybackSpeed = speedStr
    }

    private fun onPositionUpdate(event: FlowEvent.PlaybackPositionEvent) {
//        Logd(TAG, "onPositionUpdate")
        if (!playButInit && playButRes == R.drawable.ic_play_48dp && curMedia is EpisodeMedia) {
            if (isCurrentlyPlaying(curMedia as? EpisodeMedia)) playButRes = R.drawable.ic_pause
            playButInit = true
        }

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
        txtvLengtTexth = if (showTimeLeft) (if (remainingTime > 0) "-" else "") + DurationConverter.getDurationStringLong(remainingTime)
        else DurationConverter.getDurationStringLong(duration)

        sliderValue = event.position.toFloat()
    }

    private fun onPlaybackServiceChanged(event: FlowEvent.PlaybackServiceEvent) {
        when (event.action) {
            FlowEvent.PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN -> (activity as MainActivity).setPlayerVisible(false)
            FlowEvent.PlaybackServiceEvent.Action.SERVICE_STARTED -> if (curMedia != null) (activity as MainActivity).setPlayerVisible(true)
//                PlaybackServiceEvent.Action.SERVICE_RESTARTED -> (activity as MainActivity).setPlayerVisible(true)
        }
    }

    private fun updateUi(media: Playable) {
        Logd(TAG, "updateUi called $media")
        titleText = media.getEpisodeTitle()
        txtvPlaybackSpeed = DecimalFormat("0.00").format(curSpeedFB.toDouble())
        curPlaybackSpeed = curSpeedFB
        onPositionUpdate(FlowEvent.PlaybackPositionEvent(media, media.getPosition(), media.getDuration()))
        if (prevMedia?.getIdentifier() != media.getIdentifier()) imgLoc = ImageResourceUtils.getEpisodeListImageLocation(media)
        if (isPlayingVideoLocally && (curMedia as? EpisodeMedia)?.episode?.feed?.preferences?.videoModePolicy != VideoMode.AUDIO_ONLY) {
            (activity as MainActivity).bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        prevMedia = media
    }

    private fun updateDetails() {
//        if (isLoading) return
        lifecycleScope.launch {
            Logd(TAG, "in updateDetails")
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
                    if (rating == Rating.UNRATED.code || prevItem?.identifyingValue != currentItem!!.identifyingValue) rating = currentItem!!.rating
                    currentMedia = currentItem!!.media
                    if (prevItem?.identifyingValue != currentItem!!.identifyingValue) cleanedNotes = null
                    Logd(TAG, "updateInfo ${cleanedNotes == null} ${prevItem?.identifyingValue} ${currentItem!!.identifyingValue}")
                    if (cleanedNotes == null) {
                        val url = currentItem!!.media?.downloadUrl
                        if (url?.contains("youtube.com") == true && currentItem!!.description?.startsWith("Short:") == true) {
                            Logd(TAG, "getting extended description: ${currentItem!!.title}")
                            try {
                                val info = currentItem!!.streamInfo
                                if (info?.description?.content != null) {
                                    currentItem = upsert(currentItem!!) { it.description = info.description?.content }
                                    cleanedNotes = shownotesCleaner?.processShownotes(info.description!!.content, currentMedia?.getDuration()?:0)
                                } else cleanedNotes = shownotesCleaner?.processShownotes(currentItem!!.description ?: "", currentMedia?.getDuration()?:0)
                            } catch (e: Exception) { Logd(TAG, "StreamInfo error: ${e.message}") }
                        } else cleanedNotes = shownotesCleaner?.processShownotes(currentItem!!.description ?: "", currentMedia?.getDuration()?:0)
                    }
                    prevItem = currentItem
                }
            }
            withContext(Dispatchers.Main) {
                Logd(TAG, "subscribe: ${currentMedia?.getEpisodeTitle()}")
                displayMediaInfo(currentMedia!!)
//                (activity as MainActivity).setPlayerVisible(curMedia != null)
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
                        currentItem = upsertBlk(currentItem!!) { it.setTranscriptIfLonger(readerhtml) }
                        homeText = currentItem!!.transcript
                    }
                }
                if (!homeText.isNullOrEmpty()) {
                    cleanedNotes = shownotesCleaner?.processShownotes(homeText!!, 0)
//                    withContext(Dispatchers.Main) {}
                } else withContext(Dispatchers.Main) { Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show() }
            } else {
                cleanedNotes = shownotesCleaner?.processShownotes(currentItem?.description ?: "", currentMedia?.getDuration() ?: 0)
                if (cleanedNotes.isNullOrEmpty())
                    withContext(Dispatchers.Main) { Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun displayMediaInfo(media: Playable) {
        Logd(TAG, "displayMediaInfo ${currentItem?.title} ${media.getEpisodeTitle()}")
        val pubDateStr = MiscFormatter.formatDateTimeFlex(media.getPubDate())
        txtvPodcastTitle = media.getFeedTitle().trim()
        episodeDate = pubDateStr.trim()
        titleText = currentItem?.title ?:""
        displayedChapterIndex = -1
        refreshChapterData(ChapterUtils.getCurrentChapterIndex(media, media.getPosition())) //calls displayCoverImage
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
        imgLocLarge = if (displayedChapterIndex == -1 || currentMedia!!.getChapters().isEmpty() || currentMedia!!.getChapters()[displayedChapterIndex].imageUrl.isNullOrEmpty())
            currentMedia!!.getImageLocation() else EmbeddedChapterImage.getModelFor(currentMedia!!, displayedChapterIndex)?.toString()
        Logd(TAG, "displayCoverImage: imgLoc: $imgLoc")
    }

     private fun seekToPrevChapter() {
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

     private fun seekToNextChapter() {
        if (currentMedia == null || currentMedia!!.getChapters().isEmpty() || displayedChapterIndex == -1 || displayedChapterIndex + 1 >= currentMedia!!.getChapters().size) return
        refreshChapterData(displayedChapterIndex + 1)
        seekTo(currentMedia!!.getChapters()[displayedChapterIndex].start.toInt())
    }

     private fun savePreference() {
        Logd(TAG, "Saving preferences")
        val editor = prefs.edit() ?: return
        if (curMedia != null) {
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
//            if (currentMedia != null) updateUi(currentMedia!!)
            setIsShowPlay(isShowPlay)
            updateDetails()
//        }
    }

    fun onCollaped() {
        Logd(TAG, "onCollaped()")
        isCollapsed = true
//        if (currentMedia != null) updateUi(currentMedia!!)
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
    fun loadMediaInfo() {
        Logd(TAG, "loadMediaInfo() curMedia: ${curMedia?.getIdentifier()}")
        val actMain = (activity as MainActivity)
        if (curMedia == null) {
            if (actMain.isPlayerVisible()) actMain.setPlayerVisible(false)
            return
        }
        if (!actMain.isPlayerVisible()) actMain.setPlayerVisible(true)
        if (!loadItemsRunning) {
            loadItemsRunning = true
//            if (!actMain.isPlayerVisible()) actMain.setPlayerVisible(true)
            val curMediaChanged = currentMedia == null || curMedia?.getIdentifier() != currentMedia?.getIdentifier()
            if (curMedia?.getIdentifier() != currentMedia?.getIdentifier()) updateUi(curMedia!!)
            if (!isCollapsed && curMediaChanged) {
                updateDetails()
                Logd(TAG, "loadMediaInfo loading details ${curMedia?.getIdentifier()}")
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        curMedia!!.apply {
                            ChapterUtils.loadChapters(this, requireContext(), false)
                        }
                    }
                    currentMedia = curMedia
                    val item = (currentMedia as? EpisodeMedia)?.episodeOrFetch()
                    if (item != null) setItem(item)
                    setChapterDividers()
                    sleepTimerActive = isSleepTimerActive()
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
            rating = currentItem!!.rating
            showHomeText = false
            homeText = null
        }
    }

    private fun createHandler(): ServiceStatusHandler {
        return object : ServiceStatusHandler(requireActivity()) {
            override fun updatePlayButton(showPlay: Boolean) {
                setIsShowPlay(showPlay)
            }
            override fun loadMediaInfo() {
                this@AudioPlayerFragment.loadMediaInfo()
//                if (!isCollapsed) updateDetails()
            }
            override fun onPlaybackEnd() {
                setIsShowPlay(true)
                (activity as MainActivity).setPlayerVisible(false)
            }
        }
    }

    override fun onResume() {
        Logd(TAG, "onResume() isCollapsed: $isCollapsed")
        super.onResume()
        loadMediaInfo()
        if (curMedia != null) onPositionUpdate(FlowEvent.PlaybackPositionEvent(curMedia!!, curMedia!!.getPosition(), curMedia!!.getDuration()))
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

//        loadMediaInfo()
    }

    override fun onStop() {
        Logd(TAG, "onStop()")
        super.onStop()
//        MediaController.releaseFuture(controllerFuture)
        cancelFlowEvents()
    }

     override fun onPause() {
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
            updateUi(currentMedia!!)
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
                    is FlowEvent.SleepTimerUpdatedEvent ->  if (event.isCancelled || event.wasJustEnabled()) sleepTimerActive = isSleepTimerActive()
                    is FlowEvent.PlaybackPositionEvent -> onPlaybackPositionEvent(event)
                    is FlowEvent.SpeedChangedEvent -> updatePlaybackSpeedButton(event)
                    else -> {}
                }
            }
        }
    }

    private fun onRatingEvent(event: FlowEvent.RatingEvent) {
        if (curEpisode?.id == event.episode.id) rating = event.rating
    }

//    fun scrollToTop() {
////        binding.itemDescriptionFragment.scrollTo(0, 0)
//        savePreference()
//    }

    companion object {
        val TAG = AudioPlayerFragment::class.simpleName ?: "Anonymous"
        var media3Controller: MediaController? = null

        private const val PREF_SCROLL_Y = "prefScrollY"
        private const val PREF_PLAYABLE_ID = "prefPlayableId"
    }
}
