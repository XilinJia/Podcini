package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.storage.database.Episodes
import ac.mdiq.podcini.storage.database.Episodes.setPlayState
import ac.mdiq.podcini.storage.database.Queues
import ac.mdiq.podcini.storage.database.Queues.removeFromQueue
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.MediaType
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.storage.utils.ImageResourceUtils
import ac.mdiq.podcini.ui.actions.actionbutton.EpisodeActionButton
import ac.mdiq.podcini.ui.actions.handler.EpisodeMultiSelectHandler.PutToQueueDialog
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeAction
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.fragment.EpisodeInfoFragment
import ac.mdiq.podcini.ui.fragment.FeedInfoFragment
import ac.mdiq.podcini.ui.utils.LocalDeleteModal
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter.formatAbbrev
import android.text.format.Formatter
import android.util.TypedValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import coil.compose.AsyncImage
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.notifications.UpdatedObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun InforBar(text: MutableState<String>, leftAction: MutableState<SwipeAction?>, rightAction: MutableState<SwipeAction?>, actionConfig: () -> Unit) {
    val textColor = MaterialTheme.colors.onSurface
    Logd("InforBar", "textState: ${text.value}")
    Row {
        Icon(painter = painterResource(leftAction.value?.getActionIcon() ?:R.drawable.ic_questionmark), tint = textColor, contentDescription = "left_action_icon",
            modifier = Modifier.width(24.dp).height(24.dp).clickable(onClick = actionConfig))
        Icon(painter = painterResource(R.drawable.baseline_arrow_left_alt_24), tint = textColor, contentDescription = "left_arrow", modifier = Modifier.width(24.dp).height(24.dp))
        Spacer(modifier = Modifier.weight(1f))
        Text(text.value, color = textColor, style = MaterialTheme.typography.body2)
        Spacer(modifier = Modifier.weight(1f))
        Icon(painter = painterResource(R.drawable.baseline_arrow_right_alt_24), tint = textColor, contentDescription = "right_arrow", modifier = Modifier.width(24.dp).height(24.dp))
        Icon(painter = painterResource(rightAction.value?.getActionIcon() ?:R.drawable.ic_questionmark), tint = textColor, contentDescription = "right_action_icon",
            modifier = Modifier.width(24.dp).height(24.dp).clickable(onClick = actionConfig))
    }
}

@Composable
fun EpisodeSpeedDial(activity: MainActivity, selected: SnapshotStateList<Episode>, modifier: Modifier = Modifier) {
    val TAG = "EpisodeSpeedDial ${selected.size}"
    var isExpanded by remember { mutableStateOf(false) }
    val options = listOf<@Composable () -> Unit>(
        {
            Row(modifier = Modifier.padding(horizontal = 16.dp)
                .clickable {
                    isExpanded = false
                    Logd(TAG, "ic_delete: ${selected.size}")
                    LocalDeleteModal.deleteEpisodesWarnLocal(activity, selected)
                }, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete), "")
                Text(stringResource(id = R.string.delete_episode_label))
            }
        },
        {
            Row(modifier = Modifier.padding(horizontal = 16.dp)
                .clickable {
                    isExpanded = false
                    Logd(TAG, "ic_download: ${selected.size}")
                    for (episode in selected) {
                        if (episode.media != null && episode.feed != null && !episode.feed!!.isLocalFeed) DownloadServiceInterface.get()
                            ?.download(activity, episode)
                    }
                }, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_download), "")
                Text(stringResource(id = R.string.download_label))
            }
        },
        {
            Row(modifier = Modifier.padding(horizontal = 16.dp)
                .clickable {
                    isExpanded = false
                    Logd(TAG, "ic_mark_played: ${selected.size}")
                    setPlayState(Episode.PlayState.UNSPECIFIED.code, false, *selected.toTypedArray())
                }, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_mark_played), "")
                Text(stringResource(id = R.string.toggle_played_label))
            }
        },
        {
            Row(modifier = Modifier.padding(horizontal = 16.dp)
                .clickable {
                    isExpanded = false
                    Logd(TAG, "ic_playlist_remove: ${selected.size}")
                    removeFromQueue(*selected.toTypedArray())
                }, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_remove), "")
                Text(stringResource(id = R.string.remove_from_queue_label))
            }
        },
        {
            Row(modifier = Modifier.padding(horizontal = 16.dp)
                .clickable {
                    isExpanded = false
                    Logd(TAG, "ic_playlist_play: ${selected.size}")
                    Queues.addToQueue(true, *selected.toTypedArray())
                }, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "")
                Text(stringResource(id = R.string.add_to_queue_label))
            }
        },
        {
            Row(modifier = Modifier.padding(horizontal = 16.dp)
                .clickable {
                    isExpanded = false
                    Logd(TAG, "ic_playlist_play: ${selected.size}")
                    PutToQueueDialog(activity, selected).show()
                }, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "")
                Text(stringResource(id = R.string.put_in_queue_label))
            }
        },
        {
            Row(modifier = Modifier.padding(horizontal = 16.dp)
                .clickable {
                    isExpanded = false
                    Logd(TAG, "ic_star: ${selected.size}")
                    for (item in selected) {
                        Episodes.setFavorite(item, null)
                    }
                }, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_star), "")
                Text(stringResource(id = R.string.toggle_favorite_label))
            }
        },
    )
    val scrollState = rememberScrollState()
    Column(modifier = modifier.verticalScroll(scrollState), verticalArrangement = Arrangement.Bottom) {
        if (isExpanded) options.forEachIndexed { _, button ->
            FloatingActionButton(modifier = Modifier.padding(start = 4.dp, bottom = 6.dp).height(50.dp),
                backgroundColor = Color.LightGray,
                onClick = {}) { button() }
        }
        FloatingActionButton(backgroundColor = Color.Green,
            onClick = { isExpanded = !isExpanded }) { Icon(Icons.Filled.Edit, "Edit") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeLazyColumn(activity: MainActivity, episodes: SnapshotStateList<Episode>, leftSwipeCB: (Episode) -> Unit, rightSwipeCB: (Episode) -> Unit, actionButton_: ((Episode)->EpisodeActionButton)? = null) {
    val TAG = "EpisodeLazyColumn"
    var selectMode by remember { mutableStateOf(false) }
//    val selectedIds = remember { mutableSetOf<Long>() }
    var selectedSize by remember { mutableStateOf(0) }
    val selected = remember { mutableStateListOf<Episode>() }
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    var longPressIndex by remember { mutableIntStateOf(-1) }

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(state = lazyListState,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(episodes) { index, episode ->
                var positionState by remember { mutableStateOf(episode.media?.position?:0) }
                var playedState by remember { mutableStateOf(episode.isPlayed()) }
                var farvoriteState by remember { mutableStateOf(episode.isFavorite) }
                var inProgressState by remember { mutableStateOf(episode.isInProgress) }

                var episodeMonitor: Job? by remember { mutableStateOf(null) }
                var mediaMonitor: Job? by remember { mutableStateOf(null) }
                if (episodeMonitor == null) {
                    episodeMonitor = CoroutineScope(Dispatchers.Default).launch {
                        val item_ = realm.query(Episode::class).query("id == ${episode.id}").first()
                        val episodeFlow = item_.asFlow()
                        episodeFlow.collect { changes: SingleQueryChange<Episode> ->
                            when (changes) {
                                is UpdatedObject -> {
                                    Logd(TAG, "episodeMonitor UpdatedObject ${changes.obj.title} ${changes.changedFields.joinToString()}")
                                    playedState = changes.obj.isPlayed()
                                    farvoriteState = changes.obj.isFavorite
//                                    episodes[index] = changes.obj     // direct assignment doesn't update member like media??
                                    changes.obj.copyStates(episodes[index])
                                    episodes.removeAt(index)
                                    episodes.add(index, changes.obj)
                                }
                                else -> {}
                            }
                        }
                    }
                }
                if (mediaMonitor == null) {
                    mediaMonitor = CoroutineScope(Dispatchers.Default).launch {
                        val item_ = realm.query(Episode::class).query("id == ${episode.id}").first()
                        val episodeFlow = item_.asFlow(listOf("media.*"))
                        episodeFlow.collect { changes: SingleQueryChange<Episode> ->
                            when (changes) {
                                is UpdatedObject -> {
                                    Logd(TAG, "mediaMonitor UpdatedObject ${changes.obj.title} ${changes.changedFields.joinToString()}")
                                    positionState = changes.obj.media?.position?:0
                                    inProgressState = changes.obj.isInProgress
//                                    episodes[index] = changes.obj     // direct assignment doesn't update member like media??
                                    changes.obj.copyStates(episodes[index])
                                    episodes.removeAt(index)
                                    episodes.add(index, changes.obj)
                                }
                                else -> {}
                            }
                        }
                    }
                }
                DisposableEffect(Unit) {
                    onDispose {
                        episodeMonitor?.cancel()
                        mediaMonitor?.cancel()
                    }
                }
                if (episodes[index].stopMonitoring.value) {
                    episodeMonitor?.cancel()
                    mediaMonitor?.cancel()
                }
                val velocityTracker = remember { VelocityTracker() }
                val offsetX = remember { Animatable(0f) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { velocityTracker.resetTracking() },
                                onHorizontalDrag = { change, dragAmount ->
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    coroutineScope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                                },
                                onDragEnd = {
                                    coroutineScope.launch {
                                        val velocity = velocityTracker.calculateVelocity().x
                                        if (velocity > 1000f || velocity < -1000f) {
                                            if (velocity > 0) rightSwipeCB(episodes[index])
                                            else leftSwipeCB(episodes[index])
                                        }
                                        offsetX.animateTo(
                                            targetValue = 0f, // Back to the initial position
                                            animationSpec = tween(500) // Adjust animation duration as needed
                                        )
                                    }
                                }
                            )
                        }
                        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                ) {
                    var isSelected by remember { mutableStateOf(false) }
                    LaunchedEffect(key1 = selectMode, key2 = selectedSize) {
                        isSelected = selectMode && episode in selected
//                        Logd(TAG, "LaunchedEffect $index $isSelected ${selected.size}")
                    }
                    fun toggleSelected() {
                        isSelected = !isSelected
                        if (isSelected) {
//                            selectedIds.add(episode.id)
                            selected.add(episodes[index])
                        } else {
//                            selectedIds.remove(episode.id)
                            selected.remove(episodes[index])
                        }
                    }
                    val textColor = MaterialTheme.colors.onSurface
                    Row (Modifier.background(if (isSelected) MaterialTheme.colors.secondary else MaterialTheme.colors.surface)) {
                        if (false) {
                            val typedValue = TypedValue()
                            LocalContext.current.theme.resolveAttribute(R.attr.dragview_background, typedValue, true)
                            Icon(painter = painterResource(typedValue.resourceId), tint = textColor,
                                contentDescription = "drag handle",
                                modifier = Modifier.width(16.dp).align(Alignment.CenterVertically))
                        }
                        ConstraintLayout(modifier = Modifier.width(56.dp).height(56.dp)) {
                            val (imgvCover, checkMark) = createRefs()
                            val imgLoc = ImageResourceUtils.getEpisodeListImageLocation(episode)
                            AsyncImage(model = imgLoc, contentDescription = "imgvCover",
                                placeholder = painterResource(R.mipmap.ic_launcher),
                                modifier = Modifier.width(56.dp).height(56.dp)
                                    .constrainAs(imgvCover) {
                                        top.linkTo(parent.top)
                                        bottom.linkTo(parent.bottom)
                                        start.linkTo(parent.start)
                                    }.clickable(onClick = {
                                        Logd(TAG, "icon clicked!")
                                        if (selectMode) toggleSelected()
                                        else activity.loadChildFragment(FeedInfoFragment.newInstance(episode.feed!!))
                                    }))
                            val alpha = if (playedState) 1.0f else 0f
                            if (playedState) Icon(painter = painterResource(R.drawable.ic_check), tint = textColor, contentDescription = "played_mark",
                                modifier = Modifier.background(Color.Green).alpha(alpha).constrainAs(checkMark) {
                                    bottom.linkTo(parent.bottom)
                                    end.linkTo(parent.end)
                                })
                        }
                        Column(Modifier.weight(1f).padding(start = 6.dp, end = 6.dp)
                            .combinedClickable(onClick = {
                                Logd(TAG, "clicked: ${episode.title}")
                                if (selectMode) toggleSelected()
                                else activity.loadChildFragment(EpisodeInfoFragment.newInstance(episode))
                            }, onLongClick = {
                                selectMode = !selectMode
                                isSelected = selectMode
                                if (selectMode) {
//                                    selectedIds.add(episode.id)
                                    selected.add(episodes[index])
//                                    selectedSize = selectedIds.size
                                    longPressIndex = index
                                } else {
//                                    selectedIds.clear()
                                    selectedSize = 0
                                    longPressIndex = -1
                                }
                                Logd(TAG, "long clicked: ${episode.title}")
                            })) {
                            Row {
                                if (episode.media?.getMediaType() == MediaType.VIDEO)
                                    Icon(painter = painterResource(R.drawable.ic_videocam), tint = textColor, contentDescription = "isVideo", modifier = Modifier.width(14.dp).height(14.dp))
                                if (farvoriteState)
                                    Icon(painter = painterResource(R.drawable.ic_star), tint = textColor, contentDescription = "isFavorite", modifier = Modifier.width(14.dp).height(14.dp))
                                if (episode.inQueueState.value)
                                    Icon(painter = painterResource(R.drawable.ic_playlist_play), tint = textColor, contentDescription = "ivInPlaylist", modifier = Modifier.width(14.dp).height(14.dp))
                                val dateSizeText = " · " + formatAbbrev(LocalContext.current, episode.getPubDate()) + " · " + if((episode.media?.size?:0) > 0) Formatter.formatShortFileSize(LocalContext.current, episode.media!!.size) else ""
                                Text(dateSizeText, color = textColor, style = MaterialTheme.typography.body2)
                            }
                            Text(episode.title?:"", color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (InTheatre.isCurMedia(episode.media) || inProgressState) {
                                val pos = positionState
                                val dur = remember(episode, episode.media) { episode.media!!.getDuration()}
                                val prog = if (dur > 0 && pos >= 0 && dur >= pos) 1.0f * pos / dur else 0f
                                Row {
                                    Text(DurationConverter.getDurationStringLong(pos), color = textColor, style = MaterialTheme.typography.caption)
                                    LinearProgressIndicator(progress = prog, modifier = Modifier.weight(1f).height(4.dp).align(Alignment.CenterVertically))
                                    Text(DurationConverter.getDurationStringLong(dur), color = textColor, style = MaterialTheme.typography.caption)
                                }
                            }
                        }
                        var actionButton by remember { mutableStateOf(if (actionButton_ == null) EpisodeActionButton.forItem(episodes[index]) else actionButton_(episodes[index])) }
                        var showAltActionsDialog by remember { mutableStateOf(false) }
                        val dls = remember { DownloadServiceInterface.get() }
                        var dlPercent by remember { mutableIntStateOf(0) }
                        fun isDownloading(): Boolean {
                            return episodes[index].downloadState.value > DownloadStatus.State.UNKNOWN.ordinal && episodes[index].downloadState.value < DownloadStatus.State.COMPLETED.ordinal
                        }
                        if (actionButton_ == null) {
                            LaunchedEffect(episodes[index].downloadState.value) {
                                if (isDownloading()) dlPercent = dls?.getProgress(episodes[index].media!!.downloadUrl!!) ?: 0
//                                Logd(TAG, "downloadState: ${episodes[index].downloadState.value} ${episode.media?.downloaded} $dlPercent")
                                actionButton = EpisodeActionButton.forItem(episodes[index])
                            }
                            LaunchedEffect(episodes[index].isPlayingState.value) {
                                Logd(TAG, "$index isPlayingState: ${episode.isPlayingState.value}")
                                actionButton = EpisodeActionButton.forItem(episodes[index])
                            }
                        }
                        Box(modifier = Modifier.width(40.dp).height(40.dp).padding(end = 10.dp).align(Alignment.CenterVertically).pointerInput(Unit) {
                            detectTapGestures(onLongPress = { showAltActionsDialog = true }, onTap = {
                                actionButton.onClick(activity)
                            })
                        }, contentAlignment = Alignment.Center) {
                            Icon(painter = painterResource(actionButton.getDrawable()), tint = textColor, contentDescription = null, modifier = Modifier.width(28.dp).height(32.dp))
                            if (isDownloading() && dlPercent >= 0) CircularProgressIndicator(progress = 0.01f * dlPercent, strokeWidth = 4.dp, color = textColor)
                        }
                        if (showAltActionsDialog) actionButton.AltActionsDialog(activity, showAltActionsDialog, onDismiss = { showAltActionsDialog = false })
                    }
                }
            }
        }
        if (selectMode) {
            Row(modifier = Modifier.align(Alignment.TopEnd).width(150.dp).height(45.dp).background(Color.LightGray), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = painterResource(R.drawable.baseline_arrow_upward_24), tint = Color.Black, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp).padding(end = 10.dp)
                    .clickable(onClick = {
//                        selectedIds.clear()
                        selected.clear()
                        for (i in 0..longPressIndex) {
//                            selectedIds.add(episodes[i].id)
                            selected.add(episodes[i])
                        }
                        selectedSize = selected.size
                        Logd(TAG, "selectedIds: ${selected.size}")
                    }))
                Icon(painter = painterResource(R.drawable.baseline_arrow_downward_24), tint = Color.Black, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp).padding(end = 10.dp)
                    .clickable(onClick = {
//                        selectedIds.clear()
                        selected.clear()
                        for (i in longPressIndex..episodes.size-1) {
//                            selectedIds.add(episodes[i].id)
                            selected.add(episodes[i])
                        }
                        selectedSize = selected.size
                        Logd(TAG, "selectedIds: ${selected.size}")
                    }))
                var selectAllRes by remember { mutableIntStateOf(R.drawable.ic_select_all) }
                Icon(painter = painterResource(selectAllRes), tint = Color.Black, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp)
                    .clickable(onClick = {
                       if (selectedSize != episodes.size) {
                           for (e in episodes) {
//                               selectedIds.add(e.id)
                               selected.add(e)
                           }
                           selectAllRes = R.drawable.ic_select_none
                       } else {
//                           selectedIds.clear()
                           selected.clear()
                           selectAllRes = R.drawable.ic_select_all
                       }
                        selectedSize = selected.size
                        Logd(TAG, "selectedIds: ${selected.size}")
                    }))
            }
            EpisodeSpeedDial(activity, selected.toMutableStateList(), modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 16.dp, start = 16.dp))
        }
    }
}
