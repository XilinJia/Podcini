package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.utils.NetworkUtils.fetchHtmlSource
import ac.mdiq.podcini.playback.PlaybackServiceStarter
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curMediaId
import ac.mdiq.podcini.playback.base.InTheatre.curState
import ac.mdiq.podcini.playback.base.InTheatre.isCurrentlyPlaying
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isYTMedia
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.status
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.ytMediaSpecs
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.playback.cast.CastEnabledActivity
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.curDurationFB
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.curPositionFB
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.curSpeedFB
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isPlayingVideoLocally
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isRunning
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isSleepTimerActive
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playPause
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.seekTo
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.toggleFallbackSpeed
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.isSkipSilence
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.preferences.AppPreferences.videoPlayMode
import ac.mdiq.podcini.preferences.SleepTimerPreferences.SleepTimerDialog
import ac.mdiq.podcini.receiver.MediaButtonReceiver
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.storage.utils.DurationConverter.convertOnSpeed
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.isBSExpanded
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.Companion.videoMode
import ac.mdiq.podcini.ui.compose.*
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.utils.starter.VideoPlayerActivityStarter
import ac.mdiq.podcini.ui.view.ShownotesWebView
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter
import ac.mdiq.podcini.util.MiscFormatter.formatLargeInteger
import android.content.*
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import net.dankito.readability4j.Readability4J
import java.text.DecimalFormat
import java.text.NumberFormat
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class AudioPlayerVM(val context: Context, val lcScope: CoroutineScope) {
    internal val actMain: MainActivity? = generateSequence(context) { if (it is ContextWrapper) it.baseContext else null }.filterIsInstance<MainActivity>().firstOrNull()

    internal var controllerFuture: ListenableFuture<MediaController>? = null
    internal var controller: ServiceStatusHandler? = null

    private var prevItem: Episode? = null
    internal var curItem by mutableStateOf<Episode?>(null)

    private var playButInit = false
    internal var isShowPlay: Boolean = true

    internal var showTimeLeft = false
    internal var titleText by mutableStateOf("")
    internal var imgLoc by mutableStateOf<String?>(null)
    internal var imgLocLarge by mutableStateOf<String?>(null)
    internal var txtvPlaybackSpeed by mutableStateOf("")
    internal var curPlaybackSpeed by mutableStateOf(1f)
    private var isVideoScreen = false
    internal var playButRes by mutableIntStateOf(R.drawable.ic_play_48dp)
    internal var curPosition by mutableIntStateOf(0)
    internal var duration by mutableIntStateOf(0)
    internal var txtvLengtTexth by mutableStateOf("")
    internal var sliderValue by mutableFloatStateOf(0f)
    internal var sleepTimerActive by mutableStateOf(isSleepTimerActive())
    internal var showSpeedDialog by mutableStateOf(false)

    internal var shownotesCleaner: ShownotesCleaner? = null

    internal var cleanedNotes by mutableStateOf<String?>(null)
    private var homeText: String? = null
    internal var showHomeText = false
    internal var readerhtml: String? = null

    internal var txtvPodcastTitle by mutableStateOf("")
    internal var episodeDate by mutableStateOf("")

    //    private var chapterControlVisible by mutableStateOf(false)
    internal var hasNextChapter by mutableStateOf(true)
    internal var rating by mutableStateOf(curItem?.rating ?: Rating.UNRATED.code)

    internal var resetPlayer by mutableStateOf(false)
    internal val showErrorDialog = mutableStateOf(false)
    internal var errorMessage by mutableStateOf("")

    internal var displayedChapterIndex by mutableIntStateOf(-1)
    internal val curChapter: Chapter?
        get() {
            if (curItem?.chapters.isNullOrEmpty() || displayedChapterIndex == -1) return null
            return curItem!!.chapters[displayedChapterIndex]
        }

    private var eventSink: Job?     = null
    internal fun cancelFlowEvents() {
        Logd(TAG, "cancelFlowEvents")
        eventSink?.cancel()
        eventSink = null
    }
    internal fun procFlowEvents() {
        Logd(TAG, "procFlowEvents")
        if (eventSink == null) eventSink = lcScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.PlaybackServiceEvent -> {
//                        if (event.action == FlowEvent.PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN)
//                            (context as? MainActivity)?.bottomSheet?.state = BottomSheetBehavior.STATE_EXPANDED
                        when (event.action) {
                            FlowEvent.PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN -> actMain?.setPlayerVisible(false)
                            FlowEvent.PlaybackServiceEvent.Action.SERVICE_STARTED -> if (curEpisode != null) actMain?.setPlayerVisible(true)
//                PlaybackServiceEvent.Action.SERVICE_RESTARTED -> (context as MainActivity).setPlayerVisible(true)
                        }
                    }
                    is FlowEvent.PlayEvent -> onPlayEvent(event)
                    is FlowEvent.RatingEvent -> if (curEpisode?.id == event.episode.id) rating = event.rating
//                    is FlowEvent.SleepTimerUpdatedEvent ->  if (event.isCancelled || event.wasJustEnabled()) loadMediaInfo(false)
                    is FlowEvent.PlayerErrorEvent -> {
                        showErrorDialog.value = true
                        errorMessage = event.message
                    }
                    is FlowEvent.SleepTimerUpdatedEvent ->  if (event.isCancelled || event.wasJustEnabled()) sleepTimerActive = isSleepTimerActive()
                    is FlowEvent.PlaybackPositionEvent -> onPlaybackPositionEvent(event)
                    is FlowEvent.SpeedChangedEvent -> updatePlaybackSpeedButton(event)
                    else -> {}
                }
            }
        }
    }
    private fun updatePlaybackSpeedButton(event: FlowEvent.SpeedChangedEvent) {
        val speedStr: String = DecimalFormat("0.00").format(event.newSpeed.toDouble())
        curPlaybackSpeed = event.newSpeed
        txtvPlaybackSpeed = speedStr
    }
    private fun onPlaybackPositionEvent(event: FlowEvent.PlaybackPositionEvent) {
//        Logd(TAG, "onPlaybackPositionEvent ${event.episode.title}")
        val media = event.episode ?: return
        if (curItem?.id == null || media.id != curItem?.id) {
            curItem = media
            updateUi(curItem!!)
            setItem(curEpisode!!)
        }
//        if (isShowPlay) setIsShowPlay(false)
        onPositionUpdate(event)
        if (isBSExpanded) {
            if (curItem?.id != event.episode.id) return
            val newChapterIndex: Int = curItem!!.getCurrentChapterIndex(event.position)
            if (newChapterIndex >= 0 && newChapterIndex != displayedChapterIndex) refreshChapterData(newChapterIndex)
        }
    }
    private fun onPlayEvent(event: FlowEvent.PlayEvent) {
        Logd(TAG, "onPlayEvent ${event.episode.title}")
        val currentitem = event.episode
        if (curItem?.id == null || currentitem.id != curItem?.id) {
            curItem = currentitem
            updateUi(curItem!!)
            setItem(currentitem)
        }
        actMain?.setPlayerVisible(true)
        setIsShowPlay(event.action == FlowEvent.PlayEvent.Action.END)
    }
    internal fun onPositionUpdate(event: FlowEvent.PlaybackPositionEvent) {
//        Logd(TAG, "onPositionUpdate")
        if (!playButInit && playButRes == R.drawable.ic_play_48dp && curEpisode != null) {
            if (isCurrentlyPlaying(curEpisode)) playButRes = R.drawable.ic_pause
            playButInit = true
        }
        if (curEpisode?.id != event.episode?.id || controller == null || curPositionFB == Episode.INVALID_TIME || curDurationFB == Episode.INVALID_TIME) return
//        val converter = TimeSpeedConverter(curSpeedFB)
        curPosition = convertOnSpeed(event.position, curSpeedFB)
        duration = convertOnSpeed(event.duration, curSpeedFB)
        val remainingTime: Int = convertOnSpeed(max((event.duration - event.position).toDouble(), 0.0).toInt(), curSpeedFB)
        if (curPosition == Episode.INVALID_TIME || duration == Episode.INVALID_TIME) {
            Log.w(TAG, "Could not react to position observer update because of invalid time")
            return
        }
        showTimeLeft = getPref(AppPrefs.showTimeLeft, false)
        txtvLengtTexth = if (showTimeLeft) (if (remainingTime > 0) "-" else "") + DurationConverter.getDurationStringLong(remainingTime)
        else DurationConverter.getDurationStringLong(duration)
        sliderValue = event.position.toFloat()
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

    internal fun updateUi(media: Episode) {
        Logd(TAG, "updateUi called $media")
        titleText = media.getEpisodeTitle()
        txtvPlaybackSpeed = DecimalFormat("0.00").format(curSpeedFB.toDouble())
        curPlaybackSpeed = curSpeedFB
        onPositionUpdate(FlowEvent.PlaybackPositionEvent(media, media.position, media.duration))
        if (isPlayingVideoLocally && curEpisode?.feed?.videoModePolicy != VideoMode.AUDIO_ONLY) isBSExpanded = false
        prevItem = media
    }

    internal fun updateDetails() {
        lcScope.launch {
            Logd(TAG, "in updateDetails")
            withContext(Dispatchers.IO) {
                curItem = curEpisode
                if (curItem != null) {
//                    currentItem = currentItem
//                    currentItem = currentItem!!.episodeOrFetch()
                    showHomeText = false
                    homeText = null
                }
                if (curItem != null) {
                    if (rating == Rating.UNRATED.code || prevItem?.identifyingValue != curItem!!.identifyingValue) rating = curItem!!.rating
//                    currentItem = currentItem
                    Logd(TAG, "updateDetails updateInfo ${cleanedNotes == null} ${prevItem?.identifyingValue} ${curItem!!.identifyingValue}")
                    val url = curItem!!.downloadUrl
                    if (url?.contains("youtube.com") == true && curItem!!.description?.startsWith("Short:") == true) {
                        Logd(TAG, "getting extended description: ${curItem!!.title}")
                        try {
                            val info = curItem!!.streamInfo
                            if (info?.description?.content != null) {
                                curItem = upsert(curItem!!) { it.description = info.description?.content }
                                cleanedNotes = shownotesCleaner?.processShownotes(info.description!!.content, curItem?.duration?:0)
                            } else cleanedNotes = shownotesCleaner?.processShownotes(curItem!!.description ?: "", curItem?.duration?:0)
                        } catch (e: Exception) { Logd(TAG, "StreamInfo error: ${e.message}") }
                    } else cleanedNotes = shownotesCleaner?.processShownotes(curItem!!.description ?: "", curItem?.duration?:0)
                    prevItem = curItem
                }
                Logd(TAG, "updateDetails cleanedNotes: ${cleanedNotes?.length}")
            }
            withContext(Dispatchers.Main) {
                Logd(TAG, "subscribe: ${curItem?.getEpisodeTitle()}")
                if (curItem != null) {
                    val media = curItem!!
                    Logd(TAG, "displayMediaInfo ${curItem?.title} ${media.getEpisodeTitle()}")
                    val pubDateStr = MiscFormatter.formatDateTimeFlex(media.getPubDate())
                    txtvPodcastTitle = (media.feed?.title?:"").trim()
                    episodeDate = pubDateStr.trim()
                    titleText = curItem?.title ?:""
                    displayedChapterIndex = -1
                    refreshChapterData(media.getCurrentChapterIndex(media.position))
                }
                Logd(TAG, "Webview loaded")
            }
        }.invokeOnCompletion { throwable -> if (throwable != null) Log.e(TAG, Log.getStackTraceString(throwable)) }
    }

    internal fun buildHomeReaderText() {
        showHomeText = !showHomeText
        runOnIOScope {
            if (showHomeText) {
                homeText = curItem!!.transcript
                if (homeText == null && curItem?.link != null) {
                    val url = curItem!!.link!!
                    val htmlSource = fetchHtmlSource(url)
                    val readability4J = Readability4J(curItem!!.link!!, htmlSource)
                    val article = readability4J.parse()
                    readerhtml = article.contentWithDocumentsCharsetOrUtf8
                    if (!readerhtml.isNullOrEmpty()) {
                        curItem = upsertBlk(curItem!!) { it.setTranscriptIfLonger(readerhtml) }
                        homeText = curItem!!.transcript
                    }
                }
                if (!homeText.isNullOrEmpty()) cleanedNotes = shownotesCleaner?.processShownotes(homeText!!, 0)
                else withContext(Dispatchers.Main) { Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show() }
            } else {
                cleanedNotes = shownotesCleaner?.processShownotes(curItem?.description ?: "", curItem?.duration ?: 0)
                if (cleanedNotes.isNullOrEmpty()) withContext(Dispatchers.Main) { Toast.makeText(context, R.string.web_content_not_available, Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun refreshChapterData(chapterIndex: Int) {
        Logd(TAG, "in refreshChapterData $chapterIndex")
        if (curItem != null && chapterIndex > -1) {
            if (curItem!!.position > curItem!!.duration || chapterIndex >= (curItem?.chapters?: listOf()).size - 1) {
                displayedChapterIndex = curItem!!.chapters.size - 1
                hasNextChapter = false
            } else {
                displayedChapterIndex = chapterIndex
                hasNextChapter = true
            }
        }
        if (curItem != null) {
            imgLocLarge = if (displayedChapterIndex == -1 || curItem?.chapters.isNullOrEmpty() || curItem!!.chapters[displayedChapterIndex].imageUrl.isNullOrEmpty())
                curItem!!.imageLocation else EmbeddedChapterImage.getModelFor(curItem!!, displayedChapterIndex)?.toString()
            Logd(TAG, "displayCoverImage: imgLoc: $imgLoc")
        }
    }

    internal fun seekToPrevChapter() {
        val curr: Chapter? = curChapter
        if (curr == null || displayedChapterIndex == -1) return
        when {
            displayedChapterIndex < 1 -> seekTo(0)
            (curPositionFB - 10000 * curSpeedFB) < curr.start -> {
                refreshChapterData(displayedChapterIndex - 1)
                if (!curItem?.chapters.isNullOrEmpty()) seekTo(curItem!!.chapters[displayedChapterIndex].start.toInt())
            }
            else -> seekTo(curr.start.toInt())
        }
    }

    internal fun seekToNextChapter() {
        if (curItem?.chapters.isNullOrEmpty() || displayedChapterIndex == -1 || displayedChapterIndex + 1 >= curItem!!.chapters.size) return
        refreshChapterData(displayedChapterIndex + 1)
        seekTo(curItem!!.chapters[displayedChapterIndex].start.toInt())
    }

    private var loadItemsRunning = false
    internal fun loadMediaInfo() {
        Logd(TAG, "loadMediaInfo() curMedia: ${curEpisode?.id}")
        if (actMain == null) return
//        val actMain = (context as? MainActivity) ?: return
        if (curEpisode == null) {
            if (actMain.isPlayerVisible()) actMain.setPlayerVisible(false)
            return
        }
        if (!actMain.isPlayerVisible()) actMain.setPlayerVisible(true)
        if (!loadItemsRunning) {
            loadItemsRunning = true
            val curMediaChanged = curItem == null || curEpisode?.id != curItem?.id
            if (curEpisode != null && curEpisode?.id != curItem?.id) {
                updateUi(curEpisode!!)
//                imgLoc = ImageResourceUtils.getEpisodeListImageLocation(curMedia!!)
                curItem = curEpisode
            }
            if (isBSExpanded && curMediaChanged) {
                Logd(TAG, "loadMediaInfo loading details ${curEpisode?.id}")
                lcScope.launch {
                    withContext(Dispatchers.IO) { curEpisode?.apply { this.loadChapters(context, false) } }
                    curItem = curEpisode
                    val item = curItem
//                    val item = currentItem?.episodeOrFetch()
                    if (item != null) setItem(item)
                    val chapters: List<Chapter> = curItem?.chapters ?: listOf()
                    if (chapters.isNotEmpty()) {
                        val dividerPos = FloatArray(chapters.size)
                        for (i in chapters.indices) dividerPos[i] = chapters[i].start / curDurationFB.toFloat()
                    }
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
        if (curItem?.identifyingValue != item_.identifyingValue) {
            curItem = item_
            rating = curItem!!.rating
            showHomeText = false
            homeText = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vm = remember { AudioPlayerVM(context, scope) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    Logd(TAG, "ON_CREATE")
                    if (vm.actMain != null) {
                        vm.controller = object : ServiceStatusHandler(vm.actMain) {
                            override fun updatePlayButton(showPlay: Boolean) {
                                vm.setIsShowPlay(showPlay)
                            }
                            override fun loadMediaInfo() {
                                Logd(TAG, "createHandler loadMediaInfo")
                                vm.loadMediaInfo()
                            }
                            override fun onPlaybackEnd() {
                                vm.setIsShowPlay(true)
                                vm.actMain.setPlayerVisible(false)
                            }
                        }
                        vm.controller!!.init()
                    }
                    isBSExpanded = false
                    if (vm.shownotesCleaner == null) vm.shownotesCleaner = ShownotesCleaner(context)
                    vm.actMain?.setPlayerVisible(curEpisode != null)
                    if (curEpisode != null) vm.updateUi(curEpisode!!)
                }
                Lifecycle.Event.ON_START -> {
                    Logd(TAG, "ON_START")
                    vm.procFlowEvents()

                    val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                    if (vm.controllerFuture == null) {
                        vm.controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
                        vm.controllerFuture?.addListener({ media3Controller = vm.controllerFuture!!.get() }, MoreExecutors.directExecutor())
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    Logd(TAG, "ON_RESUME")
                    vm.loadMediaInfo()
                    if (curEpisode != null) vm.onPositionUpdate(FlowEvent.PlaybackPositionEvent(curEpisode!!, curEpisode!!.position, curEpisode!!.duration))
                }
                Lifecycle.Event.ON_STOP -> {
                    Logd(TAG, "ON_STOP")
                    vm.cancelFlowEvents()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    Logd(TAG, "ON_DESTROY")
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            vm.controller?.release()
            vm.controller = null
            if (vm.controllerFuture != null) MediaController.releaseFuture(vm.controllerFuture!!)
            vm.controllerFuture =  null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isBSExpanded) {
        if (isBSExpanded) {
            if (vm.shownotesCleaner == null) vm.shownotesCleaner = ShownotesCleaner(context)
            vm.setIsShowPlay(vm.isShowPlay)
        } else vm.setIsShowPlay(vm.isShowPlay)
        Logd(TAG, "isExpanded: $isBSExpanded")
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
                if (curEpisode == null) return
                if (playbackService == null) PlaybackServiceStarter(context, curEpisode!!).start()
            }
            AsyncImage(contentDescription = "imgvCover", model = ImageRequest.Builder(context).data(vm.imgLoc)
                .memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(),
                modifier = Modifier.width(65.dp).height(65.dp).padding(start = 5.dp)
                    .clickable(onClick = {
                        Logd(TAG, "playerUi icon was clicked")
                        if (!isBSExpanded) {
                            val media = curEpisode
                            if (media != null) {
                                val mediaType = media.getMediaType()
                                if (mediaType == MediaType.AUDIO || videoPlayMode == VideoMode.AUDIO_ONLY.code || videoMode == VideoMode.AUDIO_ONLY
                                        || (media.feed?.videoModePolicy == VideoMode.AUDIO_ONLY)) {
                                    Logd(TAG, "popping as audio episode")
                                    ensureService()
                                    isBSExpanded = true
                                } else {
                                    Logd(TAG, "popping video context")
                                    val intent = getPlayerActivityIntent(context, mediaType)
                                    context.startActivity(intent)
                                }
                            }
                        } else isBSExpanded = false
                    }))
            Spacer(Modifier.weight(0.1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SpeedometerWithArc(speed = vm.curPlaybackSpeed*100, maxSpeed = 300f, trackColor = textColor,
                    modifier = Modifier.width(43.dp).height(43.dp).clickable(onClick = { vm.showSpeedDialog = true }))
                Text(vm.txtvPlaybackSpeed, color = textColor, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.weight(0.1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                var showSkipDialog by remember { mutableStateOf(false) }
                var rewindSecs by remember { mutableStateOf(NumberFormat.getInstance().format(AppPreferences.rewindSecs.toLong())) }
                if (showSkipDialog) SkipDialog(SkipDirection.SKIP_REWIND, onDismissRequest = { showSkipDialog = false }) { rewindSecs = it.toString() }
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_fast_rewind), tint = textColor, contentDescription = "rewind",
                    modifier = Modifier.width(43.dp).height(43.dp).combinedClickable(
                        onClick = { playbackService?.mPlayer?.seekDelta(-AppPreferences.rewindSecs * 1000) },
                        onLongClick = { showSkipDialog = true }))
                Text(rewindSecs, color = textColor, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.weight(0.1f))
            Icon(imageVector = ImageVector.vectorResource(vm.playButRes), tint = textColor, contentDescription = "play",
                modifier = Modifier.width(64.dp).height(64.dp).combinedClickable(
                    onClick = {
                        if (vm.controller == null) return@combinedClickable
                        if (curEpisode != null) {
                            val media = curEpisode!!
                            vm.setIsShowPlay(!vm.isShowPlay)
                            if (media.getMediaType() == MediaType.VIDEO && status != PlayerStatus.PLAYING &&
                                    (media.feed?.videoModePolicy != VideoMode.AUDIO_ONLY)) {
                                playPause()
                                context.startActivity(getPlayerActivityIntent(context, curEpisode!!.getMediaType()))
                            } else {
//                                if (resetPlayer) {
//                                    playbackService?.mPlayer?.reinit()
//                                    resetPlayer = false
//                                } else playPause()
                                playPause()
                            }
                        } },
                    onLongClick = {
                        if (status == PlayerStatus.PLAYING) {
                            val fallbackSpeed = AppPreferences.fallbackSpeed
                            if (fallbackSpeed > 0.1f) toggleFallbackSpeed(fallbackSpeed)
                        } }))
            Spacer(Modifier.weight(0.1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                var showSkipDialog by remember { mutableStateOf(false) }
                var fastForwardSecs by remember { mutableStateOf(NumberFormat.getInstance().format(AppPreferences.fastForwardSecs.toLong())) }
                if (showSkipDialog) SkipDialog(SkipDirection.SKIP_FORWARD, onDismissRequest = {showSkipDialog = false }) { fastForwardSecs = it.toString()}
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_fast_forward), tint = textColor, contentDescription = "forward",
                    modifier = Modifier.width(43.dp).height(43.dp).combinedClickable(
                        onClick = { playbackService?.mPlayer?.seekDelta(AppPreferences.fastForwardSecs * 1000) },
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
                                val speedForward = AppPreferences.speedforwardSpeed
                                if (speedForward > 0.1f) speedForward(speedForward)
                            } },
                        onLongClick = { context.sendBroadcast(MediaButtonReceiver.createIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT)) }))
                if (AppPreferences.speedforwardSpeed > 0.1f) Text(NumberFormat.getInstance().format(AppPreferences.speedforwardSpeed), color = textColor, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.weight(0.1f))
        }
    }

    @Composable
    fun ProgressBar() {
        val textColor = MaterialTheme.colorScheme.onSurface
        Slider(value = vm.sliderValue, valueRange = 0f..vm.duration.toFloat(),
            modifier = Modifier.height(12.dp).padding(top = 2.dp, bottom = 2.dp),
            onValueChange = {
                Logd(TAG, "Slider onValueChange: $it")
                vm.sliderValue = it
            }, onValueChangeFinished = {
                Logd(TAG, "Slider onValueChangeFinished: ${vm.sliderValue}")
                vm.curPosition = vm.sliderValue.toInt()
                seekTo(vm.curPosition)
            })
        Row {
            Text(DurationConverter.getDurationStringLong(vm.curPosition), color = textColor, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            val bitrate = curEpisode?.bitrate ?: 0
            if (bitrate > 0) Text(formatLargeInteger(bitrate) + "bits", color = textColor, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            vm.showTimeLeft = getPref(AppPrefs.showTimeLeft, false)
            Text(vm.txtvLengtTexth, color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable {
                if (vm.controller == null) return@clickable
                vm.showTimeLeft = !vm.showTimeLeft
                putPref(AppPrefs.showTimeLeft, vm.showTimeLeft)
                vm.onPositionUpdate(FlowEvent.PlaybackPositionEvent(curEpisode, curPositionFB, curDurationFB))
            })
        }
    }

    @Composable
    fun PlayerUI(modifier: Modifier) {
        val textColor = MaterialTheme.colorScheme.onSurface
        Box(modifier = modifier.fillMaxWidth().height(120.dp).border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)).background(MaterialTheme.colorScheme.surface)) {
            Column {
                Text(vm.titleText, maxLines = 1, color = textColor, style = MaterialTheme.typography.bodyMedium)
                ProgressBar()
                ControlUI()
            }
        }
    }

    @Composable
    fun VolumeAdaptionDialog(onDismissRequest: () -> Unit) {
        val (selectedOption, onOptionSelected) = remember { mutableStateOf(vm.curItem?.volumeAdaptionSetting ?: VolumeAdaptionSetting.OFF) }
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VolumeAdaptionSetting.entries.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = (item == selectedOption),
                                onCheckedChange = { _ ->
                                    Logd(TAG, "row clicked: $item $selectedOption")
                                    if (item != selectedOption) {
                                        onOptionSelected(item)
                                        if (vm.curItem != null) {
                                            vm.curItem?.volumeAdaptionSetting = item
                                            curEpisode = vm.curItem
                                            playbackService?.mPlayer?.pause(reinit = true)
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

    var showSleepTimeDialog by remember { mutableStateOf(false) }
    if (showSleepTimeDialog) SleepTimerDialog { showSleepTimeDialog = false }

    @Composable
    fun Toolbar() {
        val context = LocalContext.current
        val feedItem: Episode = curEpisode ?: return
//        val feedItem = media.episodeOrFetch()
        val textColor = MaterialTheme.colorScheme.onSurface
        val mediaType = curEpisode?.getMediaType()
        val notAudioOnly = curEpisode?.feed?.videoModePolicy != VideoMode.AUDIO_ONLY
        var showVolumeDialog by remember { mutableStateOf(false) }
        if (showVolumeDialog) VolumeAdaptionDialog { showVolumeDialog = false }
        var showShareDialog by remember { mutableStateOf(false) }
        if (showShareDialog && vm.curItem != null && vm.actMain != null) ShareDialog(vm.curItem!!, vm.actMain) {showShareDialog = false }
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_down), tint = textColor, contentDescription = "Collapse", modifier = Modifier.clickable {
                isBSExpanded = false
            })
            var homeIcon by remember { mutableIntStateOf(R.drawable.baseline_home_24)}
            Icon(imageVector = ImageVector.vectorResource(homeIcon), tint = textColor, contentDescription = "Home", modifier = Modifier.clickable {
                homeIcon = if (vm.showHomeText) R.drawable.ic_home else R.drawable.outline_home_24
                vm.buildHomeReaderText()
            })
            if (mediaType == MediaType.VIDEO) Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_fullscreen_24), tint = textColor, contentDescription = "Play video",
                modifier = Modifier.clickable {
                    if (!notAudioOnly && curEpisode?.forceVideo != true) {
                        curEpisode?.forceVideo = true
                        status = PlayerStatus.STOPPED
                        playbackService?.mPlayer?.pause(reinit = true)
                        playbackService?.recreateMediaPlayer()
                    }
                    VideoPlayerActivityStarter(context).start()
                })
            if (vm.controller != null) {
                val sleepRes = if (vm.sleepTimerActive) R.drawable.ic_sleep_off else R.drawable.ic_sleep
                Icon(imageVector = ImageVector.vectorResource(sleepRes), tint = textColor, contentDescription = "Sleep timer", modifier = Modifier.clickable { showSleepTimeDialog = true })
            }
            if (vm.curItem != null) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_feed), tint = textColor, contentDescription = "Open podcast",
                modifier = Modifier.clickable {
                    if (feedItem.feedId != null) {
                        val intent: Intent = MainActivity.getIntentToOpenFeed(context, feedItem.feedId!!)
                        context.startActivity(intent)
                    }
                })
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_share), tint = textColor, contentDescription = "Share", modifier = Modifier.clickable {
                if (vm.curItem != null) showShareDialog = true
            })
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_volume_adaption), tint = textColor, contentDescription = "Volume adaptation", modifier = Modifier.clickable {
                if (vm.curItem != null) showVolumeDialog = true
            })
            Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_offline_share_24), tint = textColor, contentDescription = "Share Note", modifier = Modifier.clickable {
                val notes = if (vm.showHomeText) vm.readerhtml else feedItem.description
                if (!notes.isNullOrEmpty()) {
                    val shareText = HtmlCompat.fromHtml(notes, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                    val context = context
                    val intent = ShareCompat.IntentBuilder(context).setType("text/plain").setText(shareText).setChooserTitle(R.string.share_notes_label).createChooserIntent()
                    context.startActivity(intent)
                }
            })
            (context as? CastEnabledActivity)?.CastIconButton()
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun DetailUI(modifier: Modifier) {
        val context = LocalContext.current
        var showChooseRatingDialog by remember { mutableStateOf(false) }
        if (showChooseRatingDialog) ChooseRatingDialog(listOf(vm.curItem!!)) { showChooseRatingDialog = false }
        var showChaptersDialog by remember { mutableStateOf(false) }
        if (showChaptersDialog) ChaptersDialog(media = vm.curItem!!, onDismissRequest = {showChaptersDialog = false})

        val scrollState = rememberScrollState()
        Column(modifier = modifier.fillMaxWidth().verticalScroll(scrollState)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            fun copyText(text: String): Boolean {
                val clipboardManager: ClipboardManager? = ContextCompat.getSystemService(context, ClipboardManager::class.java)
                clipboardManager?.setPrimaryClip(ClipData.newPlainText("Podcini", text))
//            if (Build.VERSION.SDK_INT <= 32) (context as MainActivity).showSnackbarAbovePlayer(context.getString(R.string.copied_to_clipboard), Snackbar.LENGTH_SHORT)
                return true
            }
            if (isYTMedia) {
                val locales = remember { mutableStateListOf<String>() }
                var locale by remember { mutableStateOf("") }
                val codecs = remember { mutableStateListOf<String>() }
                var codec by remember { mutableStateOf("") }
                val bitRates = remember { mutableStateListOf<String>() }
                var bitrate by remember { mutableIntStateOf(0) }
                LaunchedEffect(ytMediaSpecs.media.id) {
                    locales.clear()
                    bitRates.clear()
                    codecs.clear()
                    val lSet = mutableSetOf<String>()
                    val bSet = mutableSetOf<String>()
                    val cSet = mutableSetOf<String>("Any")
                    if (vm.curItem != null) {
                        for (s in ytMediaSpecs.aStreamsList) {
                            Logd(TAG, "s.codec ${s.codec} s.averageBitrate ${s.averageBitrate}")
                            lSet.add(s.audioLocale.toString())
                            bSet.add(s.averageBitrate.toString())
                            if (s.codec != null) cSet.add(s.codec!!) else cSet.add("null")
                        }
                    }
                    locales.addAll(lSet)
                    bitRates.addAll(bSet)
                    codecs.addAll(cSet)
                    if (locales.isNotEmpty()) locale = locales[0]
                    if (bitRates.isNotEmpty()) bitrate = bitRates[0].toInt()
                    if (codecs.isNotEmpty()) codec = codecs[0]
                }
                if (locales.size > 1) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(top = 5.dp)) {
                    Text("Locale:", color = textColor, modifier = Modifier.padding(end = 10.dp))
                    Spinner(items = locales, modifier = Modifier.widthIn(max = 60.dp), selectedItem = locale) { index ->
                        Logd(TAG, "Locale selected: ${locales[index]}")
                        bitRates.clear()
                        locale = locales[index]
                        val bSet = mutableSetOf<String>()
                        if (vm.curItem != null) for (s in ytMediaSpecs.aStreamsList) {
                            if (s.audioLocale.toString() == locale) bSet.add(s.averageBitrate.toString())
                        }
                        bitRates.addAll(bSet)
                        bitrate = bitRates[0].toInt()
                        ytMediaSpecs.setAudioStream(locale, codec, bitrate)
                        vm.resetPlayer = true
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp)) {
                    if (codecs.size > 1) {
                        Text("Codec:", color = textColor, modifier = Modifier.padding(end = 10.dp))
                        Spinner(items = codecs, modifier = Modifier.widthIn(max = 100.dp), selectedItem = codec) { index ->
                            Logd(TAG, "Codec selected: ${codecs[index]}")
                            bitRates.clear()
                            codec = codecs[index]
                            val bSet = mutableSetOf<String>()
                            if (vm.curItem != null) for (s in ytMediaSpecs.aStreamsList) {
                                Logd(TAG, "${s.codec} $codec ${s.averageBitrate}")
                                if (s.audioLocale.toString() == locale && (codec == "Any" || s.codec == codec)) bSet.add(s.averageBitrate.toString())
                            }
                            bitRates.addAll(bSet)
                            bitrate = bitRates[0].toInt()
                            ytMediaSpecs.setAudioStream(locale, codec, bitrate)
                            vm.resetPlayer = true
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("BPS:", color = textColor, modifier = Modifier.padding(end = 10.dp))
                    if (bitRates.size > 1) {
                        Spinner(items = bitRates, modifier = Modifier.widthIn(max = 50.dp), selectedItem = bitrate.toString()) { index ->
                            Logd(TAG, "BitRate selected: ${bitRates[index]}")
                            bitrate = bitRates[index].toInt()
                            ytMediaSpecs.setAudioStream(locale, codec, bitrate)
                            vm.resetPlayer = true
                        }
                    } else Text(bitrate.toString(), color = textColor)
                    Spacer(Modifier.weight(1f))
                    if (vm.resetPlayer) IconButton(onClick = {
                        playbackService?.mPlayer?.reinit()
                        vm.resetPlayer = false
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_build_24), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "Build") }
                }
            }
            Text(vm.txtvPodcastTitle, textAlign = TextAlign.Center, color = textColor, style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 5.dp).combinedClickable(onClick = {
                    if (vm.curItem != null) {
                        if (vm.curItem?.feedId != null) {
                            val openFeed: Intent = MainActivity.getIntentToOpenFeed(context, vm.curItem!!.feedId!!)
                            context.startActivity(openFeed)
                        }
                    }
                }, onLongClick = { copyText(vm.curItem?.feed?.title?:"") }))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp)) {
                Spacer(modifier = Modifier.weight(0.2f))
                val ratingIconRes = Rating.fromCode(vm.rating).res
                Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                    modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(24.dp).height(24.dp).clickable(onClick = {
                        showChooseRatingDialog = true
                    }))
                Spacer(modifier = Modifier.weight(0.4f))
                Text(vm.episodeDate, textAlign = TextAlign.Center, color = textColor, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.weight(0.6f))
            }
            Text(vm.titleText, textAlign = TextAlign.Center, color = textColor, style = CustomTextStyles.titleCustom, modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 5.dp)
                .combinedClickable(onClick = {}, onLongClick = { copyText(vm.curItem?.title?:"") }))

//            fun restoreFromPreference(): Boolean {
//                if ((context as MainActivity).bottomSheet.state != BottomSheetBehavior.STATE_EXPANDED) return false
//                Logd(TAG, "Restoring from preferences")
//                val context: Activity? = context
//                if (context != null) {
//                    val id = prefs!!.getString(PREF_PLAYABLE_ID, "")
//                    val scrollY = prefs!!.getInt(PREF_SCROLL_Y, -1)
//                    if (scrollY != -1) {
//                        if (id == curMedia?.id?.toString()) {
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
            }, update = { webView -> webView.loadDataWithBaseURL("https://127.0.0.1", if (vm.cleanedNotes.isNullOrBlank()) "No notes" else vm.cleanedNotes!!, "text/html", "utf-8", "about:blank") })
            if (vm.displayedChapterIndex >= 0) {
                Row(modifier = Modifier.padding(start = 20.dp, end = 20.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_chapter_prev), tint = textColor, contentDescription = "prev_chapter",
                        modifier = Modifier.width(36.dp).height(36.dp).clickable(onClick = { vm.seekToPrevChapter() }))
                    Text("Ch " + vm.displayedChapterIndex.toString() + ": " + vm.curChapter?.title,
                        color = textColor, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 10.dp, end = 10.dp).clickable(onClick = { showChaptersDialog = true }))
                    if (vm.hasNextChapter) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_chapter_next), tint = textColor, contentDescription = "next_chapter",
                        modifier = Modifier.width(36.dp).height(36.dp).clickable(onClick = { vm.seekToNextChapter() }))
                }
            }
            AsyncImage(model = vm.imgLocLarge, contentDescription = "imgvCover", placeholder = painterResource(R.mipmap.ic_launcher), error = painterResource(R.mipmap.ic_launcher),
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 10.dp).clickable(onClick = {}))
        }
    }

    if (vm.showSpeedDialog) PlaybackSpeedFullDialog(settingCode = booleanArrayOf(true, true, true), indexDefault = 0, maxSpeed = 3f, onDismiss = {vm.showSpeedDialog = false})
    LaunchedEffect(key1 = curMediaId) {
        vm.cleanedNotes = null
        if (curEpisode != null) {
            vm.updateUi(curEpisode!!)
            vm.imgLoc = curEpisode!!.getEpisodeListImageLocation()
            vm.curItem = curEpisode
        }
    }
    MediaPlayerErrorDialog(context, vm.errorMessage, vm.showErrorDialog)
    Box(modifier = Modifier.fillMaxWidth().then(if (!isBSExpanded) Modifier else Modifier.statusBarsPadding().navigationBarsPadding())) {
        PlayerUI(Modifier.align(if (!isBSExpanded) Alignment.TopCenter else Alignment.BottomCenter).zIndex(1f))
        if (isBSExpanded) {
            if (vm.cleanedNotes == null) vm.updateDetails()
            Column(Modifier.padding(bottom = 120.dp)) {
                Toolbar()
                DetailUI(modifier = Modifier)
            }
        }
    }
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

//    fun scrollToTop() {
////        binding.itemDescriptionFragment.scrollTo(0, 0)
//        savePreference()
//    }

/**
 * Communicates with the playback service. GUI classes should use this class to
 * control playback instead of communicating with the PlaybackService directly.
 */
abstract class ServiceStatusHandler(private val activity: MainActivity) {
    private var mediaInfoLoaded = false
    private var loadedFeedMediaId: Long = -1
    private var initialized = false

    private var prevStatus = PlayerStatus.STOPPED
    private val statusUpdate: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "statusUpdate onReceive called with action: ${intent.action}")
            if (playbackService != null && PlaybackService.mPlayerInfo != null) {
                val info = PlaybackService.mPlayerInfo!!
//                Logd(TAG, "statusUpdate onReceive $prevStatus ${MediaPlayerBase.status} ${info.playerStatus} ${curMedia?.id} ${info.playable?.id}.")
                if (prevStatus != info.playerStatus || curEpisode == null || curEpisode!!.id != info.playable?.id) {
                    Logd(TAG, "statusUpdate onReceive doing updates")
                    status = info.playerStatus
                    prevStatus = status
//                    curMedia = info.playable
                    handleStatus()
                }
            } else {
                Logd(TAG, "statusUpdate onReceive: Couldn't receive status update: playbackService was null")
                if (!isRunning) {
                    status = PlayerStatus.STOPPED
                    handleStatus()
                }
            }
        }
    }

    private val notificationReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "notificationReceiver onReceive called with action: ${intent.action}")
            val type = intent.getIntExtra(PlaybackService.EXTRA_NOTIFICATION_TYPE, -1)
            val code = intent.getIntExtra(PlaybackService.EXTRA_NOTIFICATION_CODE, -1)
            if (code == -1 || type == -1) {
                Logd(TAG, "Bad arguments. Won't handle intent")
                return
            }
            when (type) {
                PlaybackService.NOTIFICATION_TYPE_RELOAD -> {
                    if (playbackService == null && isRunning) return
                    mediaInfoLoaded = false
                    updateStatus()
                }
                PlaybackService.NOTIFICATION_TYPE_PLAYBACK_END -> onPlaybackEnd()
            }
        }
    }

    @Synchronized
    fun init() {
        Logd(TAG, "controller init")
        procFlowEvents()
        if (isRunning) initServiceRunning()
        else updatePlayButton(true)
    }

    private var eventSink: Job?     = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    private fun procFlowEvents() {
        if (eventSink != null) return
        eventSink = activity.lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.PlaybackServiceEvent -> {
                        if (event.action == FlowEvent.PlaybackServiceEvent.Action.SERVICE_STARTED) {
                            init()
                            updateStatus()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    @Synchronized
    private fun initServiceRunning() {
        if (initialized) return
        initialized = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(statusUpdate, IntentFilter(PlaybackService.ACTION_PLAYER_STATUS_CHANGED), Context.RECEIVER_NOT_EXPORTED)
            activity.registerReceiver(notificationReceiver, IntentFilter(PlaybackService.ACTION_PLAYER_NOTIFICATION), Context.RECEIVER_NOT_EXPORTED)
        } else {
            activity.registerReceiver(statusUpdate, IntentFilter(PlaybackService.ACTION_PLAYER_STATUS_CHANGED))
            activity.registerReceiver(notificationReceiver, IntentFilter(PlaybackService.ACTION_PLAYER_NOTIFICATION))
        }
        checkMediaInfoLoaded()
    }

    /**
     * Should be called if the PlaybackController is no longer needed, for
     * example in the activity's onStop() method.
     */
    fun release() {
        Logd(TAG, "Releasing PlaybackController")
        try {
            activity.unregisterReceiver(statusUpdate)
            activity.unregisterReceiver(notificationReceiver)
        } catch (e: IllegalArgumentException) {/* ignore */ }
        initialized = false
        cancelFlowEvents()
    }

    open fun onPlaybackEnd() {}

    /**
     * Is called whenever the PlaybackService changes its status. This method
     * should be used to update the GUI or start/cancel background threads.
     */
    private fun handleStatus() {
        Log.d(TAG, "handleStatus() called status: $status")
        checkMediaInfoLoaded()
        when (status) {
            PlayerStatus.PLAYING -> updatePlayButton(false)
            PlayerStatus.PREPARING -> updatePlayButton(!PlaybackService.isStartWhenPrepared)
            PlayerStatus.FALLBACK, PlayerStatus.PAUSED, PlayerStatus.PREPARED, PlayerStatus.STOPPED, PlayerStatus.INITIALIZED -> updatePlayButton(true)
            else -> {}
        }
    }

    private fun checkMediaInfoLoaded() {
        if (!mediaInfoLoaded || loadedFeedMediaId != curState.curMediaId) {
            loadedFeedMediaId = curState.curMediaId
            Logd(TAG, "checkMediaInfoLoaded: $loadedFeedMediaId")
            loadMediaInfo()
        }
        mediaInfoLoaded = true
    }

    protected open fun updatePlayButton(showPlay: Boolean) {}

    abstract fun loadMediaInfo()

    /**
     * Called when connection to playback service has been established or
     * information has to be refreshed
     */
    private fun updateStatus() {
        Logd(TAG, "Querying service info")
        if (playbackService != null && PlaybackService.mPlayerInfo != null) {
            status = PlaybackService.mPlayerInfo!!.playerStatus
//            curMedia = PlaybackService.mPlayerInfo!!.playable
            // make sure that new media is loaded if it's available
            mediaInfoLoaded = false
            handleStatus()
        } else Log.e(TAG, "queryService() was called without an existing connection to playbackservice")
    }

    companion object {
        private val TAG: String = ServiceStatusHandler::class.simpleName ?: "Anonymous"
    }
}


private const val TAG = "AudioPlayerScreen"
var media3Controller: MediaController? = null


