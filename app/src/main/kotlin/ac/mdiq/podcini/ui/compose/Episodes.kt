package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.storage.database.Episodes
import ac.mdiq.podcini.storage.database.Episodes.setPlayState
import ac.mdiq.podcini.storage.database.Queues
import ac.mdiq.podcini.storage.database.Queues.removeFromQueue
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.MediaType
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.storage.utils.ImageResourceUtils
import ac.mdiq.podcini.ui.actions.handler.EpisodeMultiSelectHandler.PutToQueueDialog
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeAction
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.adapter.EpisodesAdapter.EpisodeInfoFragment
import ac.mdiq.podcini.ui.fragment.FeedInfoFragment
import ac.mdiq.podcini.ui.utils.LocalDeleteModal
import ac.mdiq.podcini.ui.view.EpisodeViewHolder
import ac.mdiq.podcini.ui.view.EpisodeViewHolder.Companion
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter.formatAbbrev
import android.text.format.Formatter
import android.util.TypedValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import kotlinx.coroutines.*
import kotlin.math.roundToInt

@Composable
fun InforBar(text: MutableState<String>, leftAction: MutableState<SwipeAction?>, rightAction: MutableState<SwipeAction?>, actionConfig: () -> Unit) {
    val textColor = MaterialTheme.colors.onSurface
    Logd("InforBar", "textState: ${text.value}")
    Row {
        Image(painter = painterResource(leftAction.value?.getActionIcon() ?:R.drawable.ic_questionmark), contentDescription = "left_action_icon",
            Modifier.width(24.dp).height(24.dp).clickable(onClick = actionConfig))
        Image(painter = painterResource(R.drawable.baseline_arrow_left_alt_24), contentDescription = "left_arrow", Modifier.width(24.dp).height(24.dp))
        Spacer(modifier = Modifier.weight(1f))
        Text(text.value, color = textColor, style = MaterialTheme.typography.body2)
        Spacer(modifier = Modifier.weight(1f))
        Image(painter = painterResource(R.drawable.baseline_arrow_right_alt_24), contentDescription = "right_arrow", Modifier.width(24.dp).height(24.dp))
        Image(painter = painterResource(rightAction.value?.getActionIcon() ?:R.drawable.ic_questionmark), contentDescription = "right_action_icon",
            Modifier.width(24.dp).height(24.dp).clickable(onClick = actionConfig))
    }
}

@Composable
fun EpisodeSpeedDialOptions(activity: MainActivity, selected: List<Episode>): List<@Composable () -> Unit> {
    return listOf<@Composable () -> Unit>(
        { Row(modifier = Modifier.padding(horizontal = 16.dp)
            .clickable {
                Logd("EpisodeSpeedDialActions", "ic_delete: ${selected.size}")
                LocalDeleteModal.deleteEpisodesWarnLocal(activity, selected)
            }
        ) {
            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete), "")
            Text(stringResource(id = R.string.delete_episode_label))
        } },
        { Row(modifier = Modifier.padding(horizontal = 16.dp)
            .clickable {
                Logd("EpisodeSpeedDialActions", "ic_download: ${selected.size}")
                for (episode in selected) {
                    if (episode.media != null && episode.feed != null && !episode.feed!!.isLocalFeed) DownloadServiceInterface.get()?.download(activity, episode)
                }
            }
        ) {
            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_download), "")
            Text(stringResource(id = R.string.download_label))
        } },
        { Row(modifier = Modifier.padding(horizontal = 16.dp)
            .clickable {
                Logd("EpisodeSpeedDialActions", "ic_mark_played: ${selected.size}")
                setPlayState(Episode.PlayState.UNSPECIFIED.code, false, *selected.toTypedArray())
            }
        ) {
            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_mark_played), "")
            Text(stringResource(id = R.string.toggle_played_label))
        } },
        { Row(modifier = Modifier.padding(horizontal = 16.dp)
            .clickable {
                Logd("EpisodeSpeedDialActions", "ic_playlist_remove: ${selected.size}")
                removeFromQueue(*selected.toTypedArray())
            }
        ) {
            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_remove), "")
            Text(stringResource(id = R.string.remove_from_queue_label))
        } },
        { Row(modifier = Modifier.padding(horizontal = 16.dp)
            .clickable {
                Logd("EpisodeSpeedDialActions", "ic_playlist_play: ${selected.size}")
                Queues.addToQueue(true, *selected.toTypedArray())
            }
        ) {
            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "")
            Text(stringResource(id = R.string.add_to_queue_label))
        } },
        { Row(modifier = Modifier.padding(horizontal = 16.dp)
            .clickable {
                Logd("EpisodeSpeedDialActions", "ic_playlist_play: ${selected.size}")
                PutToQueueDialog(activity, selected).show()
            }
        ) {
            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "")
            Text(stringResource(id = R.string.put_in_queue_label))
        } },
        { Row(modifier = Modifier.padding(horizontal = 16.dp)
            .clickable {
                Logd("EpisodeSpeedDialActions", "ic_star: ${selected.size}")
                for (item in selected) {
                    Episodes.setFavorite(item, null)
                }
            }
        ) {
            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_star), "")
            Text(stringResource(id = R.string.toggle_favorite_label))
        } },
    )
}

@Composable
fun EpisodeLazyColumn(activity: MainActivity, episodes: SnapshotStateList<Episode>, leftActionCB: (Episode) -> Unit, rightActionCB: (Episode) -> Unit) {
    var selectMode by remember { mutableStateOf(false) }
    val selectedIds by remember { mutableStateOf(mutableSetOf<Long>()) }
    val selected = remember { mutableListOf<Episode>()}
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(episodes) { index, episode ->
                val offsetX = remember { Animatable(0f) }
                val velocityTracker = remember { VelocityTracker() }
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
                                            if (velocity > 0) {
                                                Logd("EpisodeLazyColumn","Fling to the right with velocity: $velocity")
                                                rightActionCB(episode)
                                            } else {
                                                Logd("EpisodeLazyColumn","Fling to the left with velocity: $velocity")
                                                leftActionCB(episode)
                                            }
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
                    isSelected = selectMode && episode.id in selectedIds
                    EpisodeRow(episode, mutableStateOf(isSelected),
                        onClick = {
                            Logd("EpisodeLazyColumn", "clicked: ${episode.title}")
                            if (selectMode) {
                                isSelected = !isSelected
                                if (isSelected) {
                                    selectedIds.add(episode.id)
                                    selected.add(episode)
                                } else {
                                    selectedIds.remove(episode.id)
                                    selected.remove(episode)
                                }
                            } else activity.loadChildFragment(EpisodeInfoFragment.newInstance(episode))
                        },
                        onLongClick = {
                            selectMode = !selectMode
                            if (selectMode) {
                                isSelected = true
                                selectedIds.add(episode.id)
                                selected.add(episode)
                            } else {
                                isSelected = false
                                selectedIds.clear()
                            }
                            Logd("EpisodeLazyColumn", "long clicked: ${episode.title}")
                        },
                        iconOnClick = {
                            Logd("EpisodeLazyColumn", "icon clicked!")
                            if (selectMode) {
                                isSelected = !isSelected
                                if (isSelected) {
                                    selectedIds.add(episode.id)
                                    selected.add(episode)
                                } else {
                                    selectedIds.remove(episode.id)
                                    selected.remove(episode)
                                }
                            } else activity.loadChildFragment(FeedInfoFragment.newInstance(episode.feed!!))
                        })
                }
            }
        }
        if (selectMode) SpeedDial(
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 16.dp, start = 16.dp),
            mainButtonIcon = { Icon(Icons.Filled.Edit, "Edit") },
            fabButtons = EpisodeSpeedDialOptions(activity, selected),
            onMainButtonClick = { },
            onFabButtonClick = { index ->
                Logd("EpisodeLazyColumn", "onFabButtonClick: $index }")
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeRow(episode_: Episode, isSelected: MutableState<Boolean>, onClick: () -> Unit, onLongClick: () -> Unit, iconOnClick: () -> Unit = {}) {
    var episode = episode_
    val textColor = MaterialTheme.colors.onSurface
    var positionState by remember(episode) { mutableStateOf(episode.media?.position?:0) }
    var playedState by remember { mutableStateOf(episode.isPlayed()) }
    var farvoriteState by remember { mutableStateOf(episode.isFavorite) }
    var inProgressState by remember { mutableStateOf(episode.isInProgress) }

    var episodeMonitor: Job? by remember { mutableStateOf(null) }
    var mediaMonitor: Job? by remember { mutableStateOf(null) }
    if (episodeMonitor == null) {
        val item_ = realm.query(Episode::class).query("id == ${episode.id}").first()
        episodeMonitor = CoroutineScope(Dispatchers.Default).launch {
            val episodeFlow = item_.asFlow()
            episodeFlow.collect { changes: SingleQueryChange<Episode> ->
                when (changes) {
                    is UpdatedObject -> {
                        Logd("EpisodeRow", "episodeMonitor UpdatedObject ${changes.obj.title} ${changes.changedFields.joinToString()}")
                        episode = changes.obj
                        playedState = episode.isPlayed()
                        farvoriteState = episode.isFavorite
//                        withContext(Dispatchers.Main) {
////                            bind(changes.obj)
////                            if (posIndex >= 0) refreshAdapterPosCallback?.invoke(posIndex, changes.obj)
//                        }
                    }
                    else -> {}
                }
            }
        }
    }
    if (mediaMonitor == null) {
        val item_ = realm.query(Episode::class).query("id == ${episode.id}").first()
        mediaMonitor = CoroutineScope(Dispatchers.Default).launch {
            val episodeFlow = item_.asFlow(listOf("media.*"))
            episodeFlow.collect { changes: SingleQueryChange<Episode> ->
                when (changes) {
                    is UpdatedObject -> {
                        Logd("EpisodeRow", "mediaMonitor UpdatedObject ${changes.obj.title} ${changes.changedFields.joinToString()}")
                        episode = changes.obj
                        positionState = episode.media?.position?:0
                        inProgressState = episode.isInProgress
//                        withContext(Dispatchers.Main) {
////                            updatePlaybackPositionNew(changes.obj)
//////                                bind(changes.obj)
////                            if (posIndex >= 0) refreshAdapterPosCallback?.invoke(posIndex, changes.obj)
//                        }
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
    Row (Modifier.background(if (isSelected.value) MaterialTheme.colors.secondary else MaterialTheme.colors.surface)) {
        if (false) {
            val typedValue = TypedValue()
            LocalContext.current.theme.resolveAttribute(R.attr.dragview_background, typedValue, true)
            Image(painter = painterResource(typedValue.resourceId),
                contentDescription = "drag handle",
                modifier = Modifier.width(16.dp).align(Alignment.CenterVertically))
        }
        ConstraintLayout(modifier = Modifier.width(56.dp).height(56.dp)) {
            Logd("EpisodeRow", "playedState: $playedState")
            val (image1, image2) = createRefs()
            val imgLoc = ImageResourceUtils.getEpisodeListImageLocation(episode)
            AsyncImage(model = imgLoc, contentDescription = "imgvCover",
                    Modifier.width(56.dp)
                        .height(56.dp)
                        .clickable(onClick = iconOnClick)
                        .constrainAs(image1) {
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        start.linkTo(parent.start)
            })
            val alpha = if (playedState) 1.0f else 0f
            if (playedState) Image(painter = painterResource(R.drawable.ic_check), contentDescription = "played_mark",
                Modifier.background(Color.Green).alpha(alpha).constrainAs(image2) {
                    bottom.linkTo(parent.bottom)
                    end.linkTo(parent.end)
                })
        }
        Column(Modifier.weight(1f).padding(start = 6.dp, end = 6.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
            Row {
                if (episode.media?.getMediaType() == MediaType.VIDEO)
                    Image(painter = painterResource(R.drawable.ic_videocam), contentDescription = "isVideo", Modifier.width(14.dp).height(14.dp))
                if (farvoriteState)
                    Image(painter = painterResource(R.drawable.ic_star), contentDescription = "isFavorite", Modifier.width(14.dp).height(14.dp))
                if (curQueue.contains(episode))
                    Image(painter = painterResource(R.drawable.ic_playlist_play), contentDescription = "ivInPlaylist", Modifier.width(14.dp).height(14.dp))
                Text("·", color = textColor)
                Text(formatAbbrev(LocalContext.current, episode.getPubDate()), color = textColor, style = MaterialTheme.typography.body2)
                Text("·", color = textColor)
                Text(if((episode.media?.size?:0) > 0) Formatter.formatShortFileSize(LocalContext.current, episode.media!!.size) else "", color = textColor, style = MaterialTheme.typography.body2)
            }
            Text(episode.title?:"", color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (InTheatre.isCurMedia(episode.media) || inProgressState) {
                val pos = positionState
                val dur = remember(episode, episode.media) { episode.media!!.getDuration()}
                val prog = if (dur > 0 && pos >= 0 && dur >= pos) 1.0f * pos / dur else 0f
                Row {
                    Text(DurationConverter.getDurationStringLong(pos), color = textColor)
                    LinearProgressIndicator(
                        progress = prog,
                        modifier = Modifier.weight(1f).height(4.dp).align(Alignment.CenterVertically)
                    )
                    Text(DurationConverter.getDurationStringLong(dur), color = textColor)
                }
            }
        }
        IconButton(
            onClick = { /* Do something */ },
            Modifier.align(Alignment.CenterVertically)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = "Delete"
            )
        }
    }
}