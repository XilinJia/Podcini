package ac.mdiq.podcini.ui.actions.actionbutton

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.preferences.UserPreferences.isStreamOverDownload
import ac.mdiq.podcini.playback.base.InTheatre.isCurrentlyPlaying
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.preferences.UserPreferences.videoPlayMode
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.Companion.videoMode
import ac.mdiq.podcini.ui.compose.CustomTheme
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.util.UnstableApi

abstract class EpisodeActionButton internal constructor(@JvmField var item: Episode) {
    val TAG = this::class.simpleName ?: "ItemActionButton"

    open val visibility: Int
        get() = View.VISIBLE

    var processing: Float = -1f

    val actionState = mutableIntStateOf(0)

    abstract fun getLabel(): Int

    abstract fun getDrawable(): Int

    abstract fun onClick(context: Context)

//    fun configure(button: View, icon: ImageView, context: Context) {
//        button.visibility = visibility
//        button.contentDescription = context.getString(getLabel())
//        button.setOnClickListener { onClick(context) }
//        button.setOnLongClickListener {
//            val composeView = ComposeView(context).apply {
//                setContent {
//                    val showDialog = remember { mutableStateOf(true) }
//                    CustomTheme(context) { AltActionsDialog(context,  showDialog.value, onDismiss = { showDialog.value = false }) }
//                }
//            }
//            (button as? ViewGroup)?.addView(composeView)
//            true
//        }
//        icon.setImageResource(getDrawable())
//    }

    @Composable
    fun AltActionsDialog(context: Context, showDialog: Boolean, onDismiss: () -> Unit) {
        if (showDialog) {
            Dialog(onDismissRequest = onDismiss) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp)) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val label = getLabel()
                        if (label != R.string.play_label && label != R.string.pause_label && label != R.string.download_label) {
                            IconButton(onClick = {
                                PlayActionButton(item).onClick(context)
                                onDismiss()
                            }) { Image(painter = painterResource(R.drawable.ic_play_24dp), contentDescription = "Play") }
                        }
                        if (label != R.string.stream_label && label != R.string.play_label && label != R.string.pause_label && label != R.string.delete_label) {
                            IconButton(onClick = {
                                StreamActionButton(item).onClick(context)
                                onDismiss()
                            }) { Image(painter = painterResource(R.drawable.ic_stream), contentDescription = "Stream") }
                        }
                        if (label != R.string.download_label && label != R.string.play_label && label != R.string.delete_label) {
                            IconButton(onClick = {
                                DownloadActionButton(item).onClick(context)
                                onDismiss()
                            }) { Image(painter = painterResource(R.drawable.ic_download), contentDescription = "Download") }
                        }
                        if (label != R.string.delete_label && label != R.string.download_label && label != R.string.stream_label) {
                            IconButton(onClick = {
                                DeleteActionButton(item).onClick(context)
                                onDismiss()
                            }) { Image(painter = painterResource(R.drawable.ic_delete), contentDescription = "Delete") }
                        }
                        if (label != R.string.visit_website_label) {
                            IconButton(onClick = {
                                VisitWebsiteActionButton(item).onClick(context)
                                onDismiss()
                            }) { Image(painter = painterResource(R.drawable.ic_web), contentDescription = "Web") }
                        }
                    }
                }
            }
        }
    }

    @UnstableApi
    companion object {
        fun forItem(episode: Episode): EpisodeActionButton {
            val media = episode.media ?: return TTSActionButton(episode)
            val isDownloadingMedia = when (media.downloadUrl) {
                null -> false
                else -> DownloadServiceInterface.get()?.isDownloadingEpisode(media.downloadUrl!!)?:false
            }
//            Logd("ItemActionButton", "forItem: ${episode.feedId} ${episode.feed?.isLocalFeed} ${media.downloaded} ${isCurrentlyPlaying(media)}  ${episode.title} ")
            return when {
                isCurrentlyPlaying(media) -> PauseActionButton(episode)
                episode.feed != null && episode.feed!!.isLocalFeed -> PlayLocalActionButton(episode)
                media.downloaded -> PlayActionButton(episode)
                isDownloadingMedia -> CancelDownloadActionButton(episode)
                isStreamOverDownload || episode.feed == null || episode.feedId == null || episode.feed?.type == Feed.FeedType.YOUTUBE.name
                        || episode.feed?.preferences?.prefStreamOverDownload == true -> StreamActionButton(episode)
                else -> DownloadActionButton(episode)
            }
        }

        fun playVideoIfNeeded(context: Context, media: Playable) {
            val item = (media as? EpisodeMedia)?.episode
            if (item?.feed?.preferences?.videoModePolicy != VideoMode.AUDIO_ONLY
                    && videoPlayMode != VideoMode.AUDIO_ONLY.code && videoMode != VideoMode.AUDIO_ONLY
                    && media.getMediaType() == MediaType.VIDEO)
                context.startActivity(getPlayerActivityIntent(context, MediaType.VIDEO))
        }
    }
}
