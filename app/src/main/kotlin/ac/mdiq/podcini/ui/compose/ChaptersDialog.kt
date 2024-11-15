package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playPause
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.seekTo
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.storage.utils.DurationConverter.getDurationStringLocalized
import ac.mdiq.podcini.storage.utils.DurationConverter.getDurationStringLong
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun ChaptersDialog(media: Playable, onDismissRequest: () -> Unit) {
    val lazyListState = rememberLazyListState()
    val chapters = media.getChapters()
    val textColor = MaterialTheme.colorScheme.onSurface
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.Yellow)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.chapters_label))
                var currentChapterIndex by remember { mutableIntStateOf(-1) }
                LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chapters.size, key = {index -> chapters[index].start}) { index ->
                        val ch = chapters[index]
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
//                            if (!ch.imageUrl.isNullOrEmpty()) {
//                                val imgUrl = ch.imageUrl
//                                AsyncImage(model = imgUrl, contentDescription = "imgvCover",
//                                    placeholder = painterResource(R.mipmap.ic_launcher),
//                                    error = painterResource(R.mipmap.ic_launcher),
//                                    modifier = Modifier.width(56.dp).height(56.dp))
//                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(getDurationStringLong(ch.start.toInt()), color = textColor)
                                Text(ch.title ?: "No title", color = textColor, fontWeight = FontWeight.Bold)
//                                Text(ch.link?: "")
                                val duration = if (index + 1 < chapters.size) chapters[index + 1].start - ch.start
                                else media.getDuration() - ch.start
                                Text(stringResource(R.string.chapter_duration0) + getDurationStringLocalized(LocalContext.current, duration), color = textColor)
                            }
                            val playRes = if (index == currentChapterIndex) R.drawable.ic_replay else R.drawable.ic_play_48dp
                            Icon(imageVector = ImageVector.vectorResource(playRes), tint = textColor, contentDescription = "play button",
                                modifier = Modifier.width(28.dp).height(32.dp).clickable {
                                    if (MediaPlayerBase.status != PlayerStatus.PLAYING) playPause()
                                    seekTo(ch.start.toInt())
                                    currentChapterIndex = index
                                })
                        }
                    }
                }
            }
        }
    }
}