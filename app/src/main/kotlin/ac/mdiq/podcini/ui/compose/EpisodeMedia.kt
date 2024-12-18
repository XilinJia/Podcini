package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.utils.DurationConverter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun SkipDialog(direction: SkipDirection, onDismissRequest: ()->Unit, callBack: (Int)->Unit) {
    val titleRes = if (direction == SkipDirection.SKIP_FORWARD) R.string.pref_fast_forward else R.string.pref_rewind
    var interval by remember { mutableStateOf((if (direction == SkipDirection.SKIP_FORWARD) UserPreferences.fastForwardSecs else UserPreferences.rewindSecs).toString()) }
    AlertDialog(onDismissRequest = { onDismissRequest() },
        title = { Text(stringResource(titleRes), style = CustomTextStyles.titleCustom) },
        text = {
            TextField(value = interval,  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Companion.Number), label = { Text("seconds") }, singleLine = true,
                onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) interval = it })
        },
        confirmButton = {
            TextButton(onClick = {
                if (interval.isNotBlank()) {
                    val value = interval.toInt()
                    if (direction == SkipDirection.SKIP_FORWARD) UserPreferences.fastForwardSecs = value
                    else UserPreferences.rewindSecs = value
                    callBack(value)
                    onDismissRequest()
                }
            }) { Text(text = "OK") }
        },
        dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(text = "Cancel") } }
    )
}

enum class SkipDirection {
    SKIP_FORWARD, SKIP_REWIND
}

@Composable
fun ChaptersDialog(media: EpisodeMedia, onDismissRequest: () -> Unit) {
    val lazyListState = rememberLazyListState()
    val chapters = media.getChapters()
    val textColor = MaterialTheme.colorScheme.onSurface
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            Column(modifier = Modifier.Companion.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.chapters_label))
                var currentChapterIndex by remember { mutableIntStateOf(-1) }
                LazyColumn(state = lazyListState, modifier = Modifier.Companion.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chapters.size, key = { index -> chapters[index].start }) { index ->
                        val ch = chapters[index]
                        Row(verticalAlignment = Alignment.Companion.CenterVertically, modifier = Modifier.Companion.fillMaxWidth()) {
//                            if (!ch.imageUrl.isNullOrEmpty()) {
//                                val imgUrl = ch.imageUrl
//                                AsyncImage(model = imgUrl, contentDescription = "imgvCover",
//                                    placeholder = painterResource(R.mipmap.ic_launcher),
//                                    error = painterResource(R.mipmap.ic_launcher),
//                                    modifier = Modifier.width(56.dp).height(56.dp))
//                            }
                            Column(modifier = Modifier.Companion.weight(1f)) {
                                Text(DurationConverter.getDurationStringLong(ch.start.toInt()), color = textColor)
                                Text(ch.title ?: "No title", color = textColor, fontWeight = FontWeight.Companion.Bold)
//                                Text(ch.link?: "")
                                val duration = if (index + 1 < chapters.size) chapters[index + 1].start - ch.start
                                else media.getDuration() - ch.start
                                Text(stringResource(R.string.chapter_duration0) + DurationConverter.getDurationStringLocalized(LocalContext.current, duration), color = textColor)
                            }
                            val playRes = if (index == currentChapterIndex) R.drawable.ic_replay else R.drawable.ic_play_48dp
                            Icon(imageVector = ImageVector.Companion.vectorResource(playRes), tint = textColor, contentDescription = "play button",
                                modifier = Modifier.Companion.width(28.dp).height(32.dp).clickable {
                                    if (MediaPlayerBase.Companion.status != PlayerStatus.PLAYING) PlaybackService.Companion.playPause()
                                    PlaybackService.Companion.seekTo(ch.start.toInt())
                                    currentChapterIndex = index
                                })
                        }
                    }
                }
            }
        }
    }
}