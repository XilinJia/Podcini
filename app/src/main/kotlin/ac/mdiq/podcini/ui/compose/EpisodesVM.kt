package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.net.sync.SynchronizationSettings.wifiSyncEnabledKey
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.status
import ac.mdiq.podcini.storage.database.Episodes
import ac.mdiq.podcini.storage.database.Episodes.deleteEpisodeMedia
import ac.mdiq.podcini.storage.database.Episodes.deleteMediaSync
import ac.mdiq.podcini.storage.database.Episodes.episodeFromStreamInfo
import ac.mdiq.podcini.storage.database.Episodes.prefDeleteRemovesFromQueue
import ac.mdiq.podcini.storage.database.Episodes.prefRemoveFromQueueMarkedPlayed
import ac.mdiq.podcini.storage.database.Episodes.setPlayState
import ac.mdiq.podcini.storage.database.Episodes.setPlayStateSync
import ac.mdiq.podcini.storage.database.Feeds.addToMiscSyndicate
import ac.mdiq.podcini.storage.database.Feeds.addToYoutubeSyndicate
import ac.mdiq.podcini.storage.database.Feeds.allowForAutoDelete
import ac.mdiq.podcini.storage.database.Queues
import ac.mdiq.podcini.storage.database.Queues.addToQueueSync
import ac.mdiq.podcini.storage.database.Queues.removeFromAllQueuesQuiet
import ac.mdiq.podcini.storage.database.Queues.removeFromAllQueuesSync
import ac.mdiq.podcini.storage.database.Queues.removeFromQueueSync
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.Feed.Companion.MAX_SYNTHETIC_ID
import ac.mdiq.podcini.storage.model.Feed.Companion.newId
import ac.mdiq.podcini.storage.model.PlayState.Companion.fromCode
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.storage.utils.EpisodeUtil.hasAlmostEnded
import ac.mdiq.podcini.storage.utils.ImageResourceUtils
import ac.mdiq.podcini.ui.actions.EpisodeActionButton
import ac.mdiq.podcini.ui.actions.NullActionButton
import ac.mdiq.podcini.ui.actions.SwipeAction
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.fragment.EpisodeInfoFragment
import ac.mdiq.podcini.ui.fragment.FeedInfoFragment
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter.formatAbbrev
import ac.mdiq.vista.extractor.Vista
import ac.mdiq.vista.extractor.services.youtube.YoutubeParsingHelper.isYoutubeServiceURL
import ac.mdiq.vista.extractor.services.youtube.YoutubeParsingHelper.isYoutubeURL
import ac.mdiq.vista.extractor.stream.StreamInfo
import android.net.Uri
import android.text.format.Formatter
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.notifications.UpdatedObject
import kotlinx.coroutines.*
import java.io.File
import java.net.URL
import java.util.*
import kotlin.math.roundToInt

@Composable
fun InforBar(text: MutableState<String>, leftAction: MutableState<SwipeAction>, rightAction: MutableState<SwipeAction>, actionConfig: () -> Unit) {
    val textColor = MaterialTheme.colorScheme.onSurface
    Logd("InforBar", "textState: ${text.value}")
    Row {
        Icon(imageVector = ImageVector.vectorResource(leftAction.value.getActionIcon()), tint = textColor, contentDescription = "left_action_icon",
            modifier = Modifier
                .width(24.dp)
                .height(24.dp)
                .clickable(onClick = actionConfig))
        Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_left_alt_24), tint = textColor, contentDescription = "left_arrow", modifier = Modifier
            .width(24.dp)
            .height(24.dp))
        Spacer(modifier = Modifier.weight(1f))
        Text(text.value, color = textColor, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.weight(1f))
        Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_right_alt_24), tint = textColor, contentDescription = "right_arrow", modifier = Modifier
            .width(24.dp)
            .height(24.dp))
        Icon(imageVector = ImageVector.vectorResource(rightAction.value.getActionIcon()), tint = textColor, contentDescription = "right_action_icon",
            modifier = Modifier
                .width(24.dp)
                .height(24.dp)
                .clickable(onClick = actionConfig))
    }
}

var queueChanged by mutableIntStateOf(0)

@Stable
class EpisodeVM(var episode: Episode) {
    var positionState by mutableStateOf(episode.media?.position?:0)
    var playedState by mutableIntStateOf(episode.playState)
    var isPlayingState by mutableStateOf(false)
    var ratingState by mutableIntStateOf(episode.rating)
    var inProgressState by mutableStateOf(episode.isInProgress)
    var downloadState by mutableIntStateOf(if (episode.media?.downloaded == true) DownloadStatus.State.COMPLETED.ordinal else DownloadStatus.State.UNKNOWN.ordinal)
//    var actionButton by mutableStateOf(forItem(episode))
    var actionButton by mutableStateOf<EpisodeActionButton>(NullActionButton(episode))
    var actionRes by mutableIntStateOf(actionButton.getDrawable())
    var showAltActionsDialog by mutableStateOf(false)
    var dlPercent by mutableIntStateOf(0)
//    var inQueueState by mutableStateOf(curQueue.contains(episode))
    var isSelected by mutableStateOf(false)
    var prog by mutableFloatStateOf(0f)

    private var episodeMonitor: Job? by mutableStateOf(null)
    private var mediaMonitor: Job? by mutableStateOf(null)

    fun stopMonitoring() {
        episodeMonitor?.cancel()
        mediaMonitor?.cancel()
        episodeMonitor = null
        mediaMonitor = null
        Logd("EpisodeVM", "cancel monitoring")
    }

    fun startMonitoring() {
        if (episodeMonitor == null) {
            episodeMonitor = CoroutineScope(Dispatchers.Default).launch {
                val item_ = realm.query(Episode::class).query("id == ${episode.id}").first()
                Logd("EpisodeVM", "start monitoring episode: ${episode.title}")
                val episodeFlow = item_.asFlow()
                episodeFlow.collect { changes: SingleQueryChange<Episode> ->
                    when (changes) {
                        is UpdatedObject -> {
                            Logd("EpisodeVM", "episodeMonitor UpdatedObject ${changes.obj.title} ${changes.changedFields.joinToString()}")
                            if (episode.id == changes.obj.id) {
                                withContext(Dispatchers.Main) {
                                    playedState = changes.obj.playState
                                    ratingState = changes.obj.rating
                                    episode = changes.obj     // direct assignment doesn't update member like media??
                                }
                                Logd("EpisodeVM", "episodeMonitor $playedState $playedState ")
                            } else Logd("EpisodeVM", "episodeMonitor index out bound")
                        }
                        else -> {}
                    }
                }
            }
        }
        if (mediaMonitor == null) {
            mediaMonitor = CoroutineScope(Dispatchers.Default).launch {
                val item_ = realm.query(Episode::class).query("id == ${episode.id}").first()
                Logd("EpisodeVM", "start monitoring media: ${episode.title}")
                val episodeFlow = item_.asFlow(listOf("media.*"))
                episodeFlow.collect { changes: SingleQueryChange<Episode> ->
                    when (changes) {
                        is UpdatedObject -> {
                            Logd("EpisodeVM", "mediaMonitor UpdatedObject ${changes.obj.title} ${changes.changedFields.joinToString()}")
                            if (episode.id == changes.obj.id) {
                                withContext(Dispatchers.Main) {
                                    positionState = changes.obj.media?.position ?: 0
                                    inProgressState = changes.obj.isInProgress
                                    downloadState = if (changes.obj.media?.downloaded == true) DownloadStatus.State.COMPLETED.ordinal else DownloadStatus.State.UNKNOWN.ordinal
                                    Logd("EpisodeVM", "mediaMonitor $positionState $inProgressState ${episode.title}")
                                    episode = changes.obj
//                                    Logd("EpisodeVM", "mediaMonitor downloaded: ${changes.obj.media?.downloaded} ${episode.media?.downloaded}")
                                }
                            } else Logd("EpisodeVM", "mediaMonitor index out bound")
                        }
                        else -> {}
                    }
                }
            }
        }
    }

//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (javaClass != other?.javaClass) return false
//        other as EpisodeVM
//
//        if (episode.id != other.episode.id) return false
//        return true
//    }
//
//    override fun hashCode(): Int {
//        var result = episode.id.hashCode()
//        return result
//    }
}

@Composable
fun ChooseRatingDialog(selected: List<Episode>, onDismissRequest: () -> Unit) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.Yellow)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (rating in Rating.entries.reversed()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                        .padding(4.dp)
                        .clickable {
                            for (item in selected) Episodes.setRating(item, rating.code)
                            onDismissRequest()
                        }) {
                        Icon(imageVector = ImageVector.vectorResource(id = rating.res), "")
                        Text(rating.name, Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PlayStateDialog(selected: List<Episode>, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.Yellow)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (state in PlayState.entries) {
                    if (state.userSet) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)
                            .clickable {
                                for (item in selected) {
                                    var media: EpisodeMedia? = item.media
                                    val hasAlmostEnded = if (media != null) hasAlmostEnded(media) else false
                                    var item_ = runBlocking { setPlayStateSync(state.code, item, hasAlmostEnded, false) }
                                    when (state) {
                                        PlayState.UNPLAYED -> {
                                            if (isProviderConnected && item_.feed?.isLocalFeed != true && item_.media != null) {
                                                val actionNew: EpisodeAction = EpisodeAction.Builder(item_, EpisodeAction.NEW).currentTimestamp().build()
                                                SynchronizationQueueSink.enqueueEpisodeActionIfSyncActive(context, actionNew)
                                            }
                                        }
                                        PlayState.PLAYED -> {
                                            if (hasAlmostEnded) item_ = upsertBlk(item_) { it.media?.playbackCompletionDate = Date() }
                                            val shouldAutoDelete = if (item_.feed == null) false else allowForAutoDelete(item_.feed!!)
                                            media = item_.media
                                            if (media != null && hasAlmostEnded && shouldAutoDelete) {
                                                item_ = deleteMediaSync(context, item_)
                                                if (prefDeleteRemovesFromQueue) removeFromAllQueuesSync(item_)
                                            } else if (prefRemoveFromQueueMarkedPlayed) removeFromAllQueuesSync(item_)
                                            if (item_.feed?.isLocalFeed != true && (isProviderConnected || wifiSyncEnabledKey)) {
                                                val media_: EpisodeMedia? = item_.media
                                                // not all items have media, Gpodder only cares about those that do
                                                if (isProviderConnected && media_ != null) {
                                                    val actionPlay: EpisodeAction = EpisodeAction.Builder(item_, EpisodeAction.PLAY)
                                                        .currentTimestamp()
                                                        .started(media_.getDuration() / 1000)
                                                        .position(media_.getDuration() / 1000)
                                                        .total(media_.getDuration() / 1000)
                                                        .build()
                                                    SynchronizationQueueSink.enqueueEpisodeActionIfSyncActive(context, actionPlay)
                                                }
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                                onDismissRequest()
                            }) {
                            Icon(imageVector = ImageVector.vectorResource(id = state.res), "")
                            Text(state.name, Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PutToQueueDialog(selected: List<Episode>, onDismissRequest: () -> Unit) {
    val queues = realm.query(PlayQueue::class).find()
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.Yellow)) {
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.verticalScroll(scrollState).padding(16.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                var removeChecked by remember { mutableStateOf(false) }
                var toQueue by remember { mutableStateOf(curQueue) }
                for (q in queues) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = toQueue == q, onClick = { toQueue = q })
                        Text(q.name)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = removeChecked, onCheckedChange = { removeChecked = it })
                    Text(text = stringResource(R.string.remove_from_other_queues), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 10.dp))
                }
                Row {
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        if (removeChecked) {
                            val toRemove = mutableSetOf<Long>()
                            val toRemoveCur = mutableListOf<Episode>()
                            selected.forEach { e ->
                                if (curQueue.contains(e)) toRemoveCur.add(e)
                            }
                            selected.forEach { e ->
                                for (q in queues) {
                                    if (q.contains(e)) {
                                        toRemove.add(e.id)
                                        break
                                    }
                                }
                            }
                            if (toRemove.isNotEmpty()) runBlocking { removeFromAllQueuesQuiet(toRemove.toList()) }
                            if (toRemoveCur.isNotEmpty()) EventFlow.postEvent(FlowEvent.QueueEvent.removed(toRemoveCur))
                        }
                        selected.forEach { e ->
                            runBlocking { addToQueueSync(e, toQueue) }
                        }
                        onDismissRequest()
                    }) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}

@Composable
fun ShelveDialog(selected: List<Episode>, onDismissRequest: () -> Unit) {
    val synthetics = realm.query(Feed::class).query("id >= 100 && id <= 1000").find()
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.Yellow)) {
            val scrollState = rememberScrollState()
            Column(modifier = Modifier
                .verticalScroll(scrollState)
                .padding(16.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                var removeChecked by remember { mutableStateOf(false) }
                var toFeed by remember { mutableStateOf<Feed?>(null) }
                if (synthetics.isNotEmpty()) {
                    for (f in synthetics) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = toFeed == f, onClick = { toFeed = f })
                            Text(f.title ?: "No title")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = removeChecked, onCheckedChange = { removeChecked = it })
                        Text(text = stringResource(R.string.remove_from_current_feed), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 10.dp))
                    }
                } else Text(text = stringResource(R.string.create_synthetic_first_note))
                if (toFeed != null) Row {
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        val eList: MutableList<Episode> = mutableListOf()
                        for (e in selected) {
                            var e_ = e
                            if (!removeChecked || (e.feedId != null && e.feedId!! >= MAX_SYNTHETIC_ID)) {
                                e_ = realm.copyFromRealm(e)
                                e_.id = newId()
                                e_.media?.id = e_.id
                            } else {
                                val feed = realm.query(Feed::class).query("id == $0", e_.feedId).first().find()
                                if (feed != null) {
                                    upsertBlk(feed) {
                                        it.episodes.remove(e_)
                                    }
                                }
                            }
                            upsertBlk(e_) {
                                it.feed = toFeed
                                it.feedId = toFeed!!.id
                                eList.add(it)
                            }
                        }
                        upsertBlk(toFeed!!) {
                            it.episodes.addAll(eList)
                        }
                        onDismissRequest()
                    }) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}

@Composable
fun EraseEpisodesDialog(selected: List<Episode>, feed: Feed?, onDismissRequest: () -> Unit) {
    val message = stringResource(R.string.erase_episodes_confirmation_msg)
    val textColor = MaterialTheme.colorScheme.onSurface
    var textState by remember { mutableStateOf(TextFieldValue("")) }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.Yellow)) {
            if (feed == null || feed.id > MAX_SYNTHETIC_ID) Text(stringResource(R.string.not_erase_message), modifier = Modifier.padding(10.dp))
            else Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(message + ": ${selected.size}")
                Text(stringResource(R.string.feed_delete_reason_msg))
                BasicTextField(value = textState, onValueChange = { textState = it }, textStyle = TextStyle(fontSize = 16.sp, color = textColor),
                    modifier = Modifier.fillMaxWidth().height(100.dp).padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                )
                Button(onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            for (e in selected) {
                                val sLog = SubscriptionLog(e.id, e.title?:"", e.media?.downloadUrl?:"", e.link?:"", SubscriptionLog.Type.Media.name)
                                upsert(sLog) {
                                    it.rating = e.rating
                                    it.comment = e.comment
                                    it.comment += "\nReason to remove:\n" + textState.text
                                    it.cancelDate = Date().time
                                }
                            }
                            realm.write {
                                for (e in selected) {
                                    val url = e.media?.fileUrl
                                    when {
                                        url != null && url.startsWith("content://") -> DocumentFile.fromSingleUri(context, Uri.parse(url))?.delete()
                                        url != null -> File(url).delete()
                                    }
                                    findLatest(feed)?.episodes?.remove(e)
                                    findLatest(e)?.let { delete(it) }
                                }
                            }
                            EventFlow.postStickyEvent(FlowEvent.FeedUpdatingEvent(false))
                        } catch (e: Throwable) { Log.e("EraseEpisodesDialog", Log.getStackTraceString(e)) }
                    }
                    onDismissRequest()
                }) {
                    Text("Confirm")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EpisodeLazyColumn(activity: MainActivity, vms: MutableList<EpisodeVM>, feed: Feed? = null,
                      isDraggable: Boolean = false, dragCB: ((Int, Int)->Unit)? = null,
                      refreshCB: (()->Unit)? = null, leftSwipeCB: ((Episode) -> Unit)? = null, rightSwipeCB: ((Episode) -> Unit)? = null,
                      actionButton_: ((Episode)-> EpisodeActionButton)? = null) {
    val TAG = "EpisodeLazyColumn"
    var selectMode by remember { mutableStateOf(false) }
    var selectedSize by remember { mutableStateOf(0) }
    val selected = remember { mutableStateListOf<Episode>() }
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    var longPressIndex by remember { mutableIntStateOf(-1) }
    val dls = remember { DownloadServiceInterface.get() }
    val context = LocalContext.current

    val showConfirmYoutubeDialog = remember { mutableStateOf(false) }
    val youtubeUrls = remember { mutableListOf<String>() }
    ConfirmAddYoutubeEpisode(youtubeUrls, showConfirmYoutubeDialog.value, onDismissRequest = {
        showConfirmYoutubeDialog.value = false
    })

    var showChooseRatingDialog by remember { mutableStateOf(false) }
    if (showChooseRatingDialog) ChooseRatingDialog(selected) { showChooseRatingDialog = false }

    var showPlayStateDialog by remember { mutableStateOf(false) }
    if (showPlayStateDialog) PlayStateDialog(selected) { showPlayStateDialog = false }

    var showPutToQueueDialog by remember { mutableStateOf(false) }
    if (showPutToQueueDialog) PutToQueueDialog(selected) { showPutToQueueDialog = false }

    var showShelveDialog by remember { mutableStateOf(false) }
    if (showShelveDialog) ShelveDialog(selected) { showShelveDialog = false }

    var showEraseDialog by remember { mutableStateOf(false) }
    if (showEraseDialog && feed != null) EraseEpisodesDialog(selected, feed, onDismissRequest = { showEraseDialog = false })

    @Composable
    fun EpisodeSpeedDial(modifier: Modifier = Modifier) {
        var isExpanded by remember { mutableStateOf(false) }
        val options = mutableListOf<@Composable () -> Unit>(
            { Row(modifier = Modifier
                .padding(horizontal = 16.dp)
                .clickable {
                    isExpanded = false
                    selectMode = false
                    Logd(TAG, "ic_delete: ${selected.size}")
                    runOnIOScope {
                        for (item_ in selected) {
                            var item = item_
                            if (!item.isDownloaded && item.feed?.isLocalFeed != true) continue
                            val media = item.media
                            if (media != null) {
                                val almostEnded = hasAlmostEnded(media)
                                if (almostEnded && item.playState < PlayState.PLAYED.code) item = setPlayStateSync(PlayState.PLAYED.code, item, almostEnded, false)
                                if (almostEnded) item = upsert(item) { it.media?.playbackCompletionDate = Date() }
                                deleteEpisodeMedia(activity, item)
                            }
                        }
                    }
//                    LocalDeleteModal.deleteEpisodesWarnLocal(activity, selected)
                }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete), "Delete media")
                Text(stringResource(id = R.string.delete_episode_label)) } },
            { Row(modifier = Modifier
                .padding(horizontal = 16.dp)
                .clickable {
                    isExpanded = false
                    selectMode = false
                    Logd(TAG, "ic_download: ${selected.size}")
                    for (episode in selected) {
                        if (episode.media != null && episode.feed != null && !episode.feed!!.isLocalFeed) DownloadServiceInterface.get()?.download(activity, episode)
                    }
                }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_download), "Download")
                Text(stringResource(id = R.string.download_label)) } },
            { Row(modifier = Modifier
                .padding(horizontal = 16.dp)
                .clickable {
                    showPlayStateDialog = true
                    isExpanded = false
                    selectMode = false
                    Logd(TAG, "ic_mark_played: ${selected.size}")
//                    setPlayState(PlayState.UNSPECIFIED.code, false, *selected.toTypedArray())
                }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_mark_played), "Toggle played state")
                Text(stringResource(id = R.string.set_play_state_label)) } },
            { Row(modifier = Modifier
                .padding(horizontal = 16.dp)
                .clickable {
                    isExpanded = false
                    selectMode = false
                    Logd(TAG, "ic_playlist_remove: ${selected.size}")
                    runOnIOScope {
                        for (item_ in selected) {
                            var item = item_
                            val media = item.media
                            if (media != null) {
                                val almostEnded = hasAlmostEnded(media)
                                if (almostEnded && item.playState < PlayState.PLAYED.code) item = setPlayStateSync(PlayState.PLAYED.code, item, almostEnded, false)
                                if (almostEnded) item = upsert(item) { it.media?.playbackCompletionDate = Date() }
                            }
                            if (item.playState < PlayState.SKIPPED.code) setPlayState(PlayState.SKIPPED.code, false, item)
                        }
                        removeFromQueueSync(curQueue, *selected.toTypedArray())
//                        removeFromQueue(*selected.toTypedArray())
                    }
                }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_remove), "Remove from active queue")
                Text(stringResource(id = R.string.remove_from_queue_label)) } },
            { Row(modifier = Modifier
                .padding(horizontal = 16.dp)
                .clickable {
                    isExpanded = false
                    selectMode = false
                    Logd(TAG, "ic_playlist_play: ${selected.size}")
                    Queues.addToQueue(*selected.toTypedArray())
                }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "Add to active queue")
                Text(stringResource(id = R.string.add_to_queue_label)) } },
            { Row(modifier = Modifier
                .padding(horizontal = 16.dp)
                .clickable {
                    isExpanded = false
                    selectMode = false
                    Logd(TAG, "shelve_label: ${selected.size}")
                    showShelveDialog = true
                }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_shelves_24), "Shelve")
                Text(stringResource(id = R.string.shelve_label)) } },
            { Row(modifier = Modifier
                .padding(horizontal = 16.dp)
                .clickable {
                    isExpanded = false
                    selectMode = false
                    Logd(TAG, "ic_playlist_play: ${selected.size}")
                    showPutToQueueDialog = true
                }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "Add to queue...")
                Text(stringResource(id = R.string.put_in_queue_label)) } },
            { Row(modifier = Modifier
                .padding(horizontal = 16.dp)
                .clickable {
                    selectMode = false
                    Logd(TAG, "ic_star: ${selected.size}")
                    showChooseRatingDialog = true
                    isExpanded = false
                }, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_star), "Set rating")
                Text(stringResource(id = R.string.set_rating_label)) } },
        )
        if (selected.isNotEmpty() && selected[0].isRemote.value)
            options.add {
                Row(modifier = Modifier.padding(horizontal = 16.dp)
                    .clickable {
                        isExpanded = false
                        selectMode = false
                        Logd(TAG, "reserve: ${selected.size}")
                        CoroutineScope(Dispatchers.IO).launch {
                            youtubeUrls.clear()
                            for (e in selected) {
                                Logd(TAG, "downloadUrl: ${e.media?.downloadUrl}")
                                val url = URL(e.media?.downloadUrl ?: "")
                                if ((isYoutubeURL(url) && url.path.startsWith("/watch")) || isYoutubeServiceURL(url)) {
                                    youtubeUrls.add(e.media!!.downloadUrl!!)
                                } else addToMiscSyndicate(e)
                            }
                            Logd(TAG, "youtubeUrls: ${youtubeUrls.size}")
                            withContext(Dispatchers.Main) {
                                showConfirmYoutubeDialog.value = youtubeUrls.isNotEmpty()
                            }
                        }
                    }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AddCircle, "Reserve episodes")
                    Text(stringResource(id = R.string.reserve_episodes_label))
                }
            }
        if (feed != null && feed.id <= MAX_SYNTHETIC_ID) {
            options.add {
                Row(modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clickable {
                        isExpanded = false
                        selectMode = false
                        showEraseDialog = true
                        Logd(TAG, "reserve: ${selected.size}")
                    }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.baseline_delete_forever_24), "Erase episodes")
                    Text(stringResource(id = R.string.erase_episodes_label))
                }
            }
        }

        val scrollState = rememberScrollState()
        Column(modifier = modifier.verticalScroll(scrollState), verticalArrangement = Arrangement.Bottom) {
            if (isExpanded) options.forEachIndexed { _, button ->
                FloatingActionButton(modifier = Modifier
                    .padding(start = 4.dp, bottom = 6.dp)
                    .height(40.dp),
                    containerColor = Color.LightGray,
                    onClick = {}) { button() }
            }
            FloatingActionButton(containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.secondary,
                onClick = { isExpanded = !isExpanded }) { Icon(Icons.Filled.Edit, "Edit") }
        }
    }

    @Composable
    fun MainRow(vm: EpisodeVM, index: Int, isBeingDragged: Boolean, yOffset: Float, onDragStart: () -> Unit, onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
        val textColor = MaterialTheme.colorScheme.onSurface
        fun toggleSelected() {
            vm.isSelected = !vm.isSelected
            if (vm.isSelected) selected.add(vms[index].episode)
            else selected.remove(vms[index].episode)
        }
        val density = LocalDensity.current
        Row(Modifier.background(if (vm.isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .offset(y = with(density) { yOffset.toDp() })) {
            if (isDraggable) {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(R.attr.dragview_background, typedValue, true)
                Icon(imageVector = ImageVector.vectorResource(typedValue.resourceId), tint = textColor, contentDescription = "drag handle",
                    modifier = Modifier.width(50.dp).align(Alignment.CenterVertically).padding(start = 10.dp, end = 15.dp)
                        .draggable(orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta -> onDrag(delta) },
                            onDragStarted = { onDragStart() },
                            onDragStopped = { onDragEnd() }
                        ))
            }
            ConstraintLayout(modifier = Modifier.width(56.dp).height(56.dp)) {
                val (imgvCover, checkMark) = createRefs()
                val imgLoc = remember(vm) { ImageResourceUtils.getEpisodeListImageLocation(vm.episode) }
//                Logd(TAG, "imgLoc: $imgLoc")
                AsyncImage(model = ImageRequest.Builder(context).data(imgLoc)
                    .memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(),
                    contentDescription = "imgvCover",
                    modifier = Modifier.width(56.dp).height(56.dp)
                        .constrainAs(imgvCover) {
                            top.linkTo(parent.top)
                            bottom.linkTo(parent.bottom)
                            start.linkTo(parent.start)
                        }
                        .clickable(onClick = {
                            Logd(TAG, "icon clicked!")
                            if (selectMode) toggleSelected()
                            else if (vm.episode.feed != null) activity.loadChildFragment(FeedInfoFragment.newInstance(vm.episode.feed!!))
                        }))
                if (vm.playedState >= PlayState.SKIPPED.code) {
                    Icon(imageVector = ImageVector.vectorResource(fromCode(vm.playedState).res), tint = textColor, contentDescription = "play state",
                        modifier = Modifier.background(Color.Green.copy(alpha = 0.6f)).width(20.dp).height(20.dp)
                            .constrainAs(checkMark) {
                                bottom.linkTo(parent.bottom)
                                end.linkTo(parent.end)
                            })
                }
            }
            Column(Modifier.weight(1f).padding(start = 6.dp, end = 6.dp)
                .combinedClickable(onClick = {
                    Logd(TAG, "clicked: ${vm.episode.title}")
                    if (selectMode) toggleSelected()
                    else activity.loadChildFragment(EpisodeInfoFragment.newInstance(vm.episode))
                }, onLongClick = {
                    selectMode = !selectMode
                    vm.isSelected = selectMode
                    selected.clear()
                    if (selectMode) {
                        selected.add(vms[index].episode)
                        longPressIndex = index
                    } else {
                        selectedSize = 0
                        longPressIndex = -1
                    }
                    Logd(TAG, "long clicked: ${vm.episode.title}")
                })) {
//                LaunchedEffect(key1 = queueChanged) {
//                    if (index >= vms.size) return@LaunchedEffect
//                    vms[index].inQueueState = curQueue.contains(vms[index].episode)
//                }
                Row(verticalAlignment = Alignment.CenterVertically) {
//                    Logd(TAG, "info row")
                    val ratingIconRes = Rating.fromCode(vm.ratingState).res
                    if (vm.ratingState != Rating.UNRATED.code)
                        Icon(imageVector = ImageVector.vectorResource(ratingIconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating",
                            modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(16.dp).height(16.dp))
                    val playStateRes = PlayState.fromCode(vm.playedState).res
                    Icon(imageVector = ImageVector.vectorResource(playStateRes), tint = textColor, contentDescription = "playState", modifier = Modifier.width(16.dp).height(16.dp))
//                    if (vm.inQueueState)
//                        Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_playlist_play), tint = textColor, contentDescription = "ivInPlaylist", modifier = Modifier.width(16.dp).height(16.dp))
                    if (vm.episode.media?.getMediaType() == MediaType.VIDEO)
                        Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_videocam), tint = textColor, contentDescription = "isVideo", modifier = Modifier.width(16.dp).height(16.dp))
                    val curContext = LocalContext.current
                    val dur = remember { vm.episode.media?.getDuration() ?: 0 }
                    val durText = remember { DurationConverter.getDurationStringLong(dur) }
                    val dateSizeText = " · " + formatAbbrev(curContext, vm.episode.getPubDate()) + " · " + durText + " · " +
                            if ((vm.episode.media?.size ?: 0) > 0) Formatter.formatShortFileSize(curContext, vm.episode.media?.size ?: 0) else ""
                    Text(dateSizeText, color = textColor, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(vm.episode.title ?: "", color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            var actionButton by remember(vm.episode.id) { mutableStateOf(vm.actionButton.forItem(vm.episode)) }
            fun isDownloading(): Boolean {
                return vms[index].downloadState > DownloadStatus.State.UNKNOWN.ordinal && vms[index].downloadState < DownloadStatus.State.COMPLETED.ordinal
            }
            if (actionButton_ == null) {
                LaunchedEffect(key1 = status, key2 = vm.downloadState) {
                    if (index >= vms.size) return@LaunchedEffect
                    if (isDownloading()) vm.dlPercent = dls?.getProgress(vms[index].episode.media?.downloadUrl ?: "") ?: 0
                    Logd(TAG, "LaunchedEffect $index isPlayingState: ${vms[index].isPlayingState} ${vm.episode.playState} ${vms[index].episode.title}")
                    Logd(TAG, "LaunchedEffect $index downloadState: ${vms[index].downloadState} ${vm.episode.media?.downloaded} ${vm.dlPercent}")
                    vm.actionButton = vm.actionButton.forItem(vm.episode)
                    if (vm.actionButton.getLabel() != actionButton.getLabel()) actionButton = vm.actionButton
                }
            } else {
                LaunchedEffect(Unit) {
                    Logd(TAG, "LaunchedEffect init actionButton")
                    vm.actionButton =  actionButton_(vm.episode)
                    actionButton = vm.actionButton
//                                  vm.actionRes = vm.actionButton!!.getDrawable()
                }
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(40.dp).height(40.dp).padding(end = 10.dp).align(Alignment.CenterVertically)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { vms[index].showAltActionsDialog = true },
                        onTap = {
//                            vms[index].actionButton.onClick(activity)
                            actionButton.onClick(activity)
                        })
                }, ) {
//                Logd(TAG, "button box")
                vm.actionRes = actionButton.getDrawable()
                Icon(imageVector = ImageVector.vectorResource(vm.actionRes), tint = textColor, contentDescription = null, modifier = Modifier.width(28.dp).height(32.dp))
                if (isDownloading() && vm.dlPercent >= 0) CircularProgressIndicator(progress = { 0.01f * vm.dlPercent },
                    strokeWidth = 4.dp, color = textColor, modifier = Modifier.width(30.dp).height(35.dp))
            }
            if (vm.showAltActionsDialog) actionButton.AltActionsDialog(activity, vm.showAltActionsDialog,
                onDismiss = { vm.showAltActionsDialog = false })
        }
    }

    @Composable
    fun ProgressRow(vm: EpisodeVM, index: Int) {
        val textColor = MaterialTheme.colorScheme.onSurface
        if (vm.inProgressState || InTheatre.isCurMedia(vm.episode.media)) {
            val pos = vm.positionState
            val dur = remember { vm.episode.media?.getDuration() ?: 0 }
            val durText = remember { DurationConverter.getDurationStringLong(dur) }
            vm.prog = if (dur > 0 && pos >= 0 && dur >= pos) 1.0f * pos / dur else 0f
//            Logd(TAG, "$index vm.prog: ${vm.prog}")
            Row {
                Text(DurationConverter.getDurationStringLong(vm.positionState), color = textColor, style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { vm.prog }, modifier = Modifier.weight(1f).height(4.dp).align(Alignment.CenterVertically))
                Text(durText, color = textColor, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    var refreshing by remember { mutableStateOf(false)}
    PullToRefreshBox(modifier = Modifier.fillMaxWidth(), isRefreshing = refreshing, indicator = {}, onRefresh = {
        refreshing = true
        refreshCB?.invoke()
        refreshing = false
    }) {
        fun <T> MutableList<T>.move(fromIndex: Int, toIndex: Int) {
            if (fromIndex != toIndex && fromIndex in indices && toIndex in indices) {
                val item = removeAt(fromIndex)
                add(toIndex, item)
            }
        }
        val rowHeightPx = with(LocalDensity.current) { 56.dp.toPx() }
        LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(vms, key = { _, vm -> vm.episode.id}) { index, vm ->
                vm.startMonitoring()
                DisposableEffect(Unit) {
                    onDispose {
                        Logd(TAG, "cancelling monitoring $index")
                        vm.stopMonitoring()
                    }
                }
                val velocityTracker = remember { VelocityTracker() }
                val offsetX = remember { Animatable(0f) }
                Box(modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragStart = { velocityTracker.resetTracking() },
                        onHorizontalDrag = { change, dragAmount ->
                            Logd(TAG, "onHorizontalDrag $dragAmount")
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            coroutineScope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                val velocity = velocityTracker.calculateVelocity().x
                                Logd(TAG, "velocity: $velocity")
                                if (velocity > 1000f || velocity < -1000f) {
//                                        Logd(TAG, "velocity: $velocity")
//                                        if (velocity > 0) rightSwipeCB?.invoke(vms[index].episode)
//                                        else leftSwipeCB?.invoke(vms[index].episode)
                                    if (velocity > 0) rightSwipeCB?.invoke(vm.episode)
                                    else leftSwipeCB?.invoke(vm.episode)
                                }
                                offsetX.animateTo(targetValue = 0f, animationSpec = tween(500))
                            }
                        }
                    )
                }.offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
                    LaunchedEffect(key1 = selectMode, key2 = selectedSize) {
                        vm.isSelected = selectMode && vm.episode in selected
                        Logd(TAG, "LaunchedEffect $index ${vm.isSelected} ${selected.size}")
                    }
                    Column {
                        var yOffset by remember { mutableStateOf(0f) }
                        var draggedIndex by remember { mutableStateOf<Int?>(null) }
                        MainRow(vm, index, isBeingDragged = draggedIndex == index,
                            yOffset = if (draggedIndex == index) yOffset else 0f,
                            onDragStart = { draggedIndex = index },
                            onDrag = { delta -> yOffset += delta },
                            onDragEnd = {
                                draggedIndex?.let { startIndex ->
                                    val newIndex = (startIndex + (yOffset / rowHeightPx).toInt()).coerceIn(0, vms.lastIndex)
                                    Logd(TAG, "onDragEnd draggedIndex: $draggedIndex newIndex: $newIndex")
                                    if (newIndex != startIndex) {
                                        dragCB?.invoke(startIndex, newIndex)
                                        val item = vms.removeAt(startIndex)
                                        vms.add(newIndex, item)
                                    }
                                }
                                draggedIndex = null
                                yOffset = 0f
                            })
                        ProgressRow(vm, index)
                    }
                }
            }
        }
        if (selectMode) {
            Row(modifier = Modifier.align(Alignment.TopEnd).width(150.dp).height(45.dp)
                .background(Color.LightGray), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_upward_24), tint = Color.Black, contentDescription = null,
                    modifier = Modifier.width(35.dp).height(35.dp).padding(end = 10.dp)
                    .clickable(onClick = {
                        selected.clear()
                        for (i in 0..longPressIndex) selected.add(vms[i].episode)
                        selectedSize = selected.size
                        Logd(TAG, "selectedIds: ${selected.size}")
                    }))
                Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_downward_24), tint = Color.Black, contentDescription = null,
                    modifier = Modifier.width(35.dp).height(35.dp).padding(end = 10.dp)
                    .clickable(onClick = {
                        selected.clear()
                        for (i in longPressIndex..<vms.size) selected.add(vms[i].episode)
                        selectedSize = selected.size
                        Logd(TAG, "selectedIds: ${selected.size}")
                    }))
                var selectAllRes by remember { mutableIntStateOf(R.drawable.ic_select_all) }
                Icon(imageVector = ImageVector.vectorResource(selectAllRes), tint = Color.Black, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp)
                    .clickable(onClick = {
                        if (selectedSize != vms.size) {
                            selected.clear()
                            for (vm in vms) selected.add(vm.episode)
                            selectAllRes = R.drawable.ic_select_none
                        } else {
                            selected.clear()
                            longPressIndex = -1
                            selectAllRes = R.drawable.ic_select_all
                        }
                        selectedSize = selected.size
                        Logd(TAG, "selectedIds: ${selected.size}")
                    }))
            }
            EpisodeSpeedDial(modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 16.dp, start = 16.dp))
        }
    }
}

@Composable
fun ConfirmAddYoutubeEpisode(sharedUrls: List<String>, showDialog: Boolean, onDismissRequest: () -> Unit) {
    val TAG = "confirmAddEpisode"
    var showToast by remember { mutableStateOf(false) }
    var toastMassege by remember { mutableStateOf("")}
    if (showToast) CustomToast(message = toastMassege, onDismiss = { showToast = false })

    if (showDialog) {
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.Yellow)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) {
                    var audioOnly by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth()) {
                        Checkbox(checked = audioOnly, onCheckedChange = { audioOnly = it })
                        Text(text = stringResource(R.string.pref_video_mode_audio_only), style = MaterialTheme.typography.bodyLarge.merge())
                    }
                    var showComfirmButton by remember { mutableStateOf(true) }
                    if (showComfirmButton) {
                        Button(onClick = {
                            showComfirmButton = false
                            CoroutineScope(Dispatchers.IO).launch {
                                for (url in sharedUrls) {
                                    val log = realm.query(ShareLog::class).query("url == $0", url).first().find()
                                    try {
                                        val info = StreamInfo.getInfo(Vista.getService(0), url)
                                        val episode = episodeFromStreamInfo(info)
                                        val status = addToYoutubeSyndicate(episode, !audioOnly)
                                        if (log != null) upsert(log) {
                                            it.title = episode.title
                                            it.status = status
                                        }
                                    } catch (e: Throwable) {
                                        toastMassege = "Receive share error: ${e.message}"
                                        Log.e(TAG, toastMassege)
                                        if (log != null) upsert(log) { it.details = e.message?: "error" }
                                        withContext(Dispatchers.Main) { showToast = true }
                                    }
                                }
                                withContext(Dispatchers.Main) { onDismissRequest() }
                            }
                        }) { Text("Confirm") }
                    } else CircularProgressIndicator(progress = { 0.6f }, strokeWidth = 4.dp, modifier = Modifier.padding(start = 20.dp, end = 20.dp).width(30.dp).height(30.dp))
                }
            }
        }
    }
}

@Composable
fun EpisodesFilterDialog(filter: EpisodeFilter? = null, filtersDisabled: MutableSet<EpisodeFilter.EpisodesFilterGroup> = mutableSetOf(),
                         onDismissRequest: () -> Unit, onFilterChanged: (Set<String>) -> Unit) {
    val filterValues = remember {  filter?.properties ?: mutableSetOf() }
    Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = { onDismissRequest() }) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.let { window ->
            window.setGravity(Gravity.BOTTOM)
            window.setDimAmount(0f)
        }
        Surface(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp).height(350.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.Yellow)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            val scrollState = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                var selectNone by remember { mutableStateOf(false) }
                for (item in EpisodeFilter.EpisodesFilterGroup.entries) {
                    if (item in filtersDisabled) continue
                    if (item.values.size == 2) {
                        Row(modifier = Modifier.padding(2.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            var selectedIndex by remember { mutableStateOf(-1) }
                            if (selectNone) selectedIndex = -1
                            LaunchedEffect(Unit) {
                                if (filter != null) {
                                    if (item.values[0].filterId in filter.properties) selectedIndex = 0
                                    else if (item.values[1].filterId in filter.properties) selectedIndex = 1
                                }
                            }
                            Text(stringResource(item.nameRes) + " :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = textColor, modifier = Modifier.padding(end = 10.dp))
                            Spacer(Modifier.weight(0.3f))
                            OutlinedButton(
                                modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (selectedIndex != 0) textColor else Color.Green),
                                onClick = {
                                    if (selectedIndex != 0) {
                                        selectNone = false
                                        selectedIndex = 0
                                        filterValues.add(item.values[0].filterId)
                                        filterValues.remove(item.values[1].filterId)
                                    } else {
                                        selectedIndex = -1
                                        filterValues.remove(item.values[0].filterId)
                                    }
                                    onFilterChanged(filterValues)
                                },
                            ) {
                                Text(text = stringResource(item.values[0].displayName), color = textColor)
                            }
                            Spacer(Modifier.weight(0.1f))
                            OutlinedButton(
                                modifier = Modifier.padding(0.dp), border = BorderStroke(2.dp, if (selectedIndex != 1) textColor else Color.Green),
                                onClick = {
                                    if (selectedIndex != 1) {
                                        selectNone = false
                                        selectedIndex = 1
                                        filterValues.add(item.values[1].filterId)
                                        filterValues.remove(item.values[0].filterId)
                                    } else {
                                        selectedIndex = -1
                                        filterValues.remove(item.values[1].filterId)
                                    }
                                    onFilterChanged(filterValues)
                                },
                            ) {
                                Text(text = stringResource(item.values[1].displayName), color = textColor)
                            }
                            Spacer(Modifier.weight(0.5f))
                        }
                    } else {
                        Column(modifier = Modifier.padding(start = 5.dp, bottom = 2.dp).fillMaxWidth()) {
                            val selectedList = remember { MutableList(item.values.size) { mutableStateOf(false)} }
                            var expandRow by remember { mutableStateOf(false) }
                            Row {
                                Text(stringResource(item.nameRes) + ".. :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = textColor, modifier = Modifier.clickable {
                                    expandRow = !expandRow
                                })
                                var lowerSelected by remember { mutableStateOf(false) }
                                var higherSelected by remember { mutableStateOf(false) }
                                Spacer(Modifier.weight(1f))
                                if (expandRow) Text("<<<", color = if (lowerSelected) Color.Green else textColor, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clickable {
                                    val hIndex = selectedList.indexOfLast { it.value }
                                    if (hIndex < 0) return@clickable
                                    if (!lowerSelected) {
                                        for (i in 0..hIndex) selectedList[i].value = true
                                    } else {
                                        for (i in 0..hIndex) selectedList[i].value = false
                                        selectedList[hIndex].value = true
                                    }
                                    lowerSelected = !lowerSelected
                                    for (i in item.values.indices) {
                                        if (selectedList[i].value) filterValues.add(item.values[i].filterId)
                                        else filterValues.remove(item.values[i].filterId)
                                    }
                                    onFilterChanged(filterValues)
                                })
                                Spacer(Modifier.weight(1f))
                                if (expandRow) Text("X", color = textColor, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clickable {
                                    lowerSelected = false
                                    higherSelected = false
                                    for (i in item.values.indices) {
                                        selectedList[i].value = false
                                        filterValues.remove(item.values[i].filterId)
                                    }
                                    onFilterChanged(filterValues)
                                })
                                Spacer(Modifier.weight(1f))
                                if (expandRow) Text(">>>", color = if (higherSelected) Color.Green else textColor, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clickable {
                                    val lIndex = selectedList.indexOfFirst { it.value }
                                    if (lIndex < 0) return@clickable
                                    if (!higherSelected) {
                                        for (i in lIndex..item.values.size - 1) selectedList[i].value = true
                                    } else {
                                        for (i in lIndex..item.values.size - 1) selectedList[i].value = false
                                        selectedList[lIndex].value = true
                                    }
                                    higherSelected = !higherSelected
                                    for (i in item.values.indices) {
                                        if (selectedList[i].value) filterValues.add(item.values[i].filterId)
                                        else filterValues.remove(item.values[i].filterId)
                                    }
                                    onFilterChanged(filterValues)
                                })
                                Spacer(Modifier.weight(1f))
                            }
                            if (expandRow) NonlazyGrid(columns = 3, itemCount = item.values.size) { index ->
                                if (selectNone) selectedList[index].value = false
                                LaunchedEffect(Unit) {
                                    if (filter != null) {
                                        if (item.values[index].filterId in filter.properties) selectedList[index].value = true
                                    }
                                }
                                OutlinedButton(
                                    modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(),
                                    border = BorderStroke(2.dp, if (selectedList[index].value) Color.Green else textColor),
                                    onClick = {
                                        selectNone = false
                                        selectedList[index].value = !selectedList[index].value
                                        if (selectedList[index].value) filterValues.add(item.values[index].filterId)
                                        else filterValues.remove(item.values[index].filterId)
                                        onFilterChanged(filterValues)
                                    },
                                ) {
                                    Text(text = stringResource(item.values[index].displayName), maxLines = 1, color = textColor)
                                }
                            }
                        }
                    }
                }
                Row {
                    Spacer(Modifier.weight(0.3f))
                    Button(onClick = {
                        selectNone = true
                        onFilterChanged(setOf(""))
                    }) {
                        Text(stringResource(R.string.reset))
                    }
                    Spacer(Modifier.weight(0.4f))
                    Button(onClick = {
                        onDismissRequest()
                    }) {
                        Text(stringResource(R.string.close_label))
                    }
                    Spacer(Modifier.weight(0.3f))
                }
            }
        }
    }
}