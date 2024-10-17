package ac.mdiq.podcini.ui.actions

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.utils.NetworkUtils
import ac.mdiq.podcini.playback.PlaybackServiceStarter
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.preferences.UserPreferences.isStreamOverDownload
import ac.mdiq.podcini.playback.base.InTheatre.isCurrentlyPlaying
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.preferences.UsageStatistics
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.videoPlayMode
import ac.mdiq.podcini.receiver.MediaButtonReceiver
import ac.mdiq.podcini.storage.database.Episodes
import ac.mdiq.podcini.storage.database.RealmDB
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.utils.AudioMediaTools
import ac.mdiq.podcini.storage.utils.FilesUtils
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.Companion.videoMode
import ac.mdiq.podcini.ui.fragment.FeedEpisodesFragment
import ac.mdiq.podcini.ui.utils.LocalDeleteModal
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.IntentUtils
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.DialogInterface
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.text.HtmlCompat
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import java.io.File
import java.util.*
import kotlin.math.max
import kotlin.math.min

abstract class EpisodeActionButton internal constructor(@JvmField var item: Episode) {
    val TAG = this::class.simpleName ?: "ItemActionButton"

    open val visibility: Boolean
        get() = true

    var processing: Float = -1f

    val actionState = mutableIntStateOf(0)

    abstract fun getLabel(): Int

    abstract fun getDrawable(): Int

    abstract fun onClick(context: Context)

    @Composable
    fun AltActionsDialog(context: Context, showDialog: Boolean, onDismiss: () -> Unit) {
        if (showDialog) {
            Dialog(onDismissRequest = onDismiss) {
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp)) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val label = getLabel()
                        Logd(TAG, "button label: $label")
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
            Logd("ItemActionButton", "forItem: local feed: ${episode.feed?.isLocalFeed} downloaded: ${media.downloaded} playing: ${isCurrentlyPlaying(media)}  ${episode.title} ")
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
            if ((media as? EpisodeMedia)?.forceVideo == true || (item?.feed?.preferences?.videoModePolicy != VideoMode.AUDIO_ONLY
                    && videoPlayMode != VideoMode.AUDIO_ONLY.code && videoMode != VideoMode.AUDIO_ONLY
                    && media.getMediaType() == MediaType.VIDEO))
                context.startActivity(getPlayerActivityIntent(context, MediaType.VIDEO))
        }
    }
}

class VisitWebsiteActionButton(item: Episode) : EpisodeActionButton(item) {
    override val visibility: Boolean
        get() = if (item.link.isNullOrEmpty()) false else true

    override fun getLabel(): Int {
        return R.string.visit_website_label
    }

    override fun getDrawable(): Int {
        return R.drawable.ic_web
    }

    override fun onClick(context: Context) {
        if (!item.link.isNullOrEmpty()) IntentUtils.openInBrowser(context, item.link!!)
        actionState.value = getLabel()
    }
}

class CancelDownloadActionButton(item: Episode) : EpisodeActionButton(item) {
    @StringRes
    override fun getLabel(): Int {
        return R.string.cancel_download_label
    }

    @DrawableRes
    override fun getDrawable(): Int {
        return R.drawable.ic_cancel
    }

    @UnstableApi
    override fun onClick(context: Context) {
        val media = item.media
        if (media != null) DownloadServiceInterface.get()?.cancel(context, media)
        if (UserPreferences.isEnableAutodownload) {
            val item_ = RealmDB.upsertBlk(item) {
                it.disableAutoDownload()
            }
            EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item_))
        }
        actionState.value = getLabel()
    }
}

class PlayActionButton(item: Episode) : EpisodeActionButton(item) {
    override fun getLabel(): Int {
        return R.string.play_label
    }

    override fun getDrawable(): Int {
        return R.drawable.ic_play_24dp
    }

    @UnstableApi
    override fun onClick(context: Context) {
        Logd("PlayActionButton", "onClick called")
        val media = item.media
        if (media == null) {
            Toast.makeText(context, R.string.no_media_label, Toast.LENGTH_LONG).show()
            return
        }
        if (!media.fileExists()) {
            Toast.makeText(context, R.string.error_file_not_found, Toast.LENGTH_LONG).show()
            notifyMissingEpisodeMediaFile(context, media)
            return
        }
        if (PlaybackService.playbackService?.isServiceReady() == true && InTheatre.isCurMedia(media)) {
            PlaybackService.playbackService?.mPlayer?.resume()
            PlaybackService.playbackService?.taskManager?.restartSleepTimer()
        } else {
            PlaybackService.clearCurTempSpeed()
            PlaybackServiceStarter(context, media).callEvenIfRunning(true).start()
            EventFlow.postEvent(FlowEvent.PlayEvent(item))
        }
        playVideoIfNeeded(context, media)
        actionState.value = getLabel()
    }

    /**
     * Notifies the database about a missing EpisodeMedia file. This method will correct the EpisodeMedia object's
     * values in the DB and send a FeedItemEvent.
     */
    fun notifyMissingEpisodeMediaFile(context: Context, media: EpisodeMedia) {
        Logd(TAG, "notifyMissingEpisodeMediaFile called")
        Log.i(TAG, "The feedmanager was notified about a missing episode. It will update its database now.")
        val episode = RealmDB.realm.query(Episode::class).query("id == media.id").first().find()
//        val episode = media.episodeOrFetch()
        if (episode != null) {
            val episode_ = RealmDB.upsertBlk(episode) {
//                it.media = media
                it.media?.downloaded = false
                it.media?.fileUrl = null
            }
            EventFlow.postEvent(FlowEvent.EpisodeMediaEvent.removed(episode_))
        }
        EventFlow.postEvent(FlowEvent.MessageEvent(context.getString(R.string.error_file_not_found)))
    }
}

class DeleteActionButton(item: Episode) : EpisodeActionButton(item) {

    override val visibility: Boolean
        get() {
            if (item.media != null && (item.media!!.downloaded || item.feed?.isLocalFeed == true)) return true
            return false
        }

    override fun getLabel(): Int {
        return R.string.delete_label
    }
    override fun getDrawable(): Int {
        return R.drawable.ic_delete
    }
    @UnstableApi
    override fun onClick(context: Context) {
        LocalDeleteModal.deleteEpisodesWarnLocal(context, listOf(item))
        actionState.value = getLabel()
    }
}

class PauseActionButton(item: Episode) : EpisodeActionButton(item) {
    override fun getLabel(): Int {
        return R.string.pause_label
    }
    override fun getDrawable(): Int {
        return R.drawable.ic_pause
    }
    @UnstableApi
    override fun onClick(context: Context) {
        Logd("PauseActionButton", "onClick called")
        val media = item.media ?: return

        if (isCurrentlyPlaying(media)) context.sendBroadcast(MediaButtonReceiver.createIntent(context,
            KeyEvent.KEYCODE_MEDIA_PAUSE))
//        EventFlow.postEvent(FlowEvent.PlayEvent(item, Action.END))
        actionState.value = getLabel()
    }
}

class DownloadActionButton(item: Episode) : EpisodeActionButton(item) {
    override val visibility: Boolean
        get() = if (item.feed?.isLocalFeed == true) false else true

    override fun getLabel(): Int {
        return R.string.download_label
    }

    override fun getDrawable(): Int {
        return R.drawable.ic_download
    }

    override fun onClick(context: Context) {
        if (shouldNotDownload(item.media)) return
        UsageStatistics.logAction(UsageStatistics.ACTION_DOWNLOAD)
        if (NetworkUtils.isEpisodeDownloadAllowed) DownloadServiceInterface.get()?.downloadNow(context, item, false)
        else {
            val builder = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.confirm_mobile_download_dialog_title)
                .setPositiveButton(R.string.confirm_mobile_download_dialog_download_later) { _: DialogInterface?, _: Int ->
                    DownloadServiceInterface.get()?.downloadNow(context, item, false) }
                .setNeutralButton(R.string.confirm_mobile_download_dialog_allow_this_time) { _: DialogInterface?, _: Int ->
                    DownloadServiceInterface.get()?.downloadNow(context, item, true) }
                .setNegativeButton(R.string.cancel_label, null)
            if (NetworkUtils.isNetworkRestricted && NetworkUtils.isVpnOverWifi) builder.setMessage(R.string.confirm_mobile_download_dialog_message_vpn)
            else builder.setMessage(R.string.confirm_mobile_download_dialog_message)

            builder.show()
        }
        actionState.value = getLabel()
    }

    private fun shouldNotDownload(media: EpisodeMedia?): Boolean {
        if (media?.downloadUrl == null) return true
        val isDownloading = DownloadServiceInterface.get()?.isDownloadingEpisode(media.downloadUrl!!)?:false
        return isDownloading || media.downloaded
    }
}

class StreamActionButton(item: Episode) : EpisodeActionButton(item) {
    override fun getLabel(): Int {
        return R.string.stream_label
    }

    override fun getDrawable(): Int {
        return R.drawable.ic_stream
    }

    @UnstableApi
    override fun onClick(context: Context) {
        if (item.media == null) return
//        Logd("StreamActionButton", "item.feed: ${item.feedId}")
        val media = if (item.feedId != null) item.media!! else RemoteMedia(item)
        UsageStatistics.logAction(UsageStatistics.ACTION_STREAM)
        if (!NetworkUtils.isStreamingAllowed) {
            StreamingConfirmationDialog(context, media).show()
            return
        }
        stream(context, media)
        actionState.value = getLabel()
    }

    class StreamingConfirmationDialog(private val context: Context, private val playable: Playable) {
        @UnstableApi
        fun show() {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.stream_label)
                .setMessage(R.string.confirm_mobile_streaming_notification_message)
                .setPositiveButton(R.string.confirm_mobile_streaming_button_once) { _: DialogInterface?, _: Int -> stream(context, playable) }
                .setNegativeButton(R.string.confirm_mobile_streaming_button_always) { _: DialogInterface?, _: Int ->
                    NetworkUtils.isAllowMobileStreaming = true
                    stream(context, playable)
                }
                .setNeutralButton(R.string.cancel_label, null)
                .show()
        }
    }

    companion object {
        fun stream(context: Context, media: Playable) {
            if (media !is EpisodeMedia || !InTheatre.isCurMedia(media)) PlaybackService.clearCurTempSpeed()
            PlaybackServiceStarter(context, media).shouldStreamThisTime(true).callEvenIfRunning(true).start()
            if (media is EpisodeMedia && media.episode != null) EventFlow.postEvent(FlowEvent.PlayEvent(media.episode!!))
            playVideoIfNeeded(context, media)
        }
    }
}

class TTSActionButton(item: Episode) : EpisodeActionButton(item) {

    private var readerText: String? = null

    override val visibility: Boolean
        get() = if (item.link.isNullOrEmpty()) false else true

    override fun getLabel(): Int {
        return R.string.TTS_label
    }
    override fun getDrawable(): Int {
        return R.drawable.text_to_speech
    }

    @OptIn(UnstableApi::class) override fun onClick(context: Context) {
        Logd("TTSActionButton", "onClick called")
        if (item.link.isNullOrEmpty()) {
            Toast.makeText(context, R.string.episode_has_no_content, Toast.LENGTH_LONG).show()
            return
        }
        processing = 0.01f
        item.setBuilding()
        EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item))
        RealmDB.runOnIOScope {
            if (item.transcript == null) {
                val url = item.link!!
                val htmlSource = NetworkUtils.fetchHtmlSource(url)
                val article = Readability4J(item.link!!, htmlSource).parse()
                readerText = article.textContent
                item = RealmDB.upsertBlk(item) {
                    it.setTranscriptIfLonger(article.contentWithDocumentsCharsetOrUtf8)
                }
//                persistEpisode(item)
                Logd(TAG,
                    "readability4J: ${readerText?.substring(max(0, readerText!!.length - 100), readerText!!.length)}")
            } else readerText = HtmlCompat.fromHtml(item.transcript!!, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
            processing = 0.1f
            EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item))
            if (!readerText.isNullOrEmpty()) {
                while (!FeedEpisodesFragment.ttsReady) runBlocking { delay(100) }

                processing = 0.15f
                EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item))
                while (FeedEpisodesFragment.ttsWorking) runBlocking { delay(100) }
                FeedEpisodesFragment.ttsWorking = true
                if (item.feed?.language != null) {
                    val result = FeedEpisodesFragment.tts?.setLanguage(Locale(item.feed!!.language!!))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w(TAG, "TTS language not supported ${item.feed!!.language} $result")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context,
                                context.getString(R.string.language_not_supported_by_tts) + " ${item.feed!!.language} $result",
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }

                var j = 0
                val mediaFile = File(FilesUtils.getMediafilePath(item), FilesUtils.getMediafilename(item))
                FeedEpisodesFragment.tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        j++
                        Logd(TAG, "onDone ${mediaFile.length()} $utteranceId")
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String) {
                        Log.e(TAG, "onError utterance error: $utteranceId")
                        Log.e(TAG, "onError $readerText")
                    }
                    override fun onError(utteranceId: String, errorCode: Int) {
                        Log.e(TAG, "onError1 utterance error: $utteranceId $errorCode")
                        Log.e(TAG, "onError1 $readerText")
                    }
                })

                Logd(TAG, "readerText: ${readerText?.length}")
                var startIndex = 0
                var i = 0
                val parts = mutableListOf<String>()
                val chunkLength = TextToSpeech.getMaxSpeechInputLength()
                var status = TextToSpeech.ERROR
                while (startIndex < readerText!!.length) {
                    Logd(TAG, "working on chunk $i $startIndex")
                    val endIndex = minOf(startIndex + chunkLength, readerText!!.length)
                    val chunk = readerText!!.substring(startIndex, endIndex)
                    val tempFile = File.createTempFile("tts_temp_${i}_", ".wav")
                    parts.add(tempFile.absolutePath)
                    status =
                        FeedEpisodesFragment.tts?.synthesizeToFile(chunk, null, tempFile, tempFile.absolutePath) ?: 0
                    Logd(TAG, "status: $status chunk: ${chunk.substring(0, min(80, chunk.length))}")
                    if (status == TextToSpeech.ERROR) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context,
                                "Error generating audio file $tempFile.absolutePath",
                                Toast.LENGTH_LONG).show()
                        }
                        break
                    }
                    startIndex += chunkLength
                    i++
                    while (i - j > 0) runBlocking { delay(100) }
                    processing = 0.15f + 0.7f * startIndex / readerText!!.length
                    EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item))
                }
                processing = 0.85f
                EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item))
                if (status == TextToSpeech.SUCCESS) {
                    AudioMediaTools.mergeAudios(parts.toTypedArray(), mediaFile.absolutePath, null)

                    val mFilename = mediaFile.absolutePath
                    Logd(TAG, "saving TTS to file $mFilename")
                    val media = EpisodeMedia(item, null, 0, "audio/*")
                    media.fileUrl = mFilename
//                    media.downloaded = true
                    media.setIsDownloaded()
                    item = RealmDB.upsertBlk(item) {
                        it.media = media
                        it.setTranscriptIfLonger(readerText)
                    }
//                    persistEpisode(item)
                }
                for (p in parts) {
                    val f = File(p)
                    f.delete()
                }
                FeedEpisodesFragment.ttsWorking = false
            } else withContext(Dispatchers.Main) { Toast.makeText(context, R.string.episode_has_no_content, Toast.LENGTH_LONG).show() }

            item.setPlayed(false)
            processing = 1f
            EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item))
            actionState.value = getLabel()
        }
    }
}

class PlayLocalActionButton(item: Episode) : EpisodeActionButton(item) {
    override fun getLabel(): Int {
        return R.string.play_label
    }
    override fun getDrawable(): Int {
        return R.drawable.ic_play_24dp
    }
    @UnstableApi
    override fun onClick(context: Context) {
        Logd("PlayLocalActionButton", "onClick called")
        val media = item.media
        if (media == null) {
            Toast.makeText(context, R.string.no_media_label, Toast.LENGTH_LONG).show()
            return
        }
        if (PlaybackService.playbackService?.isServiceReady() == true && InTheatre.isCurMedia(media)) {
            PlaybackService.playbackService?.mPlayer?.resume()
            PlaybackService.playbackService?.taskManager?.restartSleepTimer()
        } else {
            PlaybackService.clearCurTempSpeed()
            PlaybackServiceStarter(context, media).callEvenIfRunning(true).start()
            EventFlow.postEvent(FlowEvent.PlayEvent(item))
        }
        if (media.getMediaType() == MediaType.VIDEO) context.startActivity(getPlayerActivityIntent(context,
            MediaType.VIDEO))
        actionState.value = getLabel()
    }
}

class MarkAsPlayedActionButton(item: Episode) : EpisodeActionButton(item) {
    override val visibility: Boolean
        get() = if (item.isPlayed()) false else true

    override fun getLabel(): Int {
        return (if (item.media != null) R.string.mark_read_label else R.string.mark_read_no_media_label)
    }

    override fun getDrawable(): Int {
        return R.drawable.ic_check
    }

    @UnstableApi
    override fun onClick(context: Context) {
        if (!item.isPlayed()) Episodes.setPlayState(Episode.PlayState.PLAYED.code, true, item)
        actionState.value = getLabel()
    }

}