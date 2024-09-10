package ac.mdiq.podcini.ui.actions.actionbutton

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.utils.NetworkUtils.isAllowMobileStreaming
import ac.mdiq.podcini.net.utils.NetworkUtils.isStreamingAllowed
import ac.mdiq.podcini.playback.PlaybackServiceStarter
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.clearCurTempSpeed
import ac.mdiq.podcini.preferences.UsageStatistics
import ac.mdiq.podcini.preferences.UsageStatistics.logAction
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.storage.model.RemoteMedia
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import android.content.Context
import android.content.DialogInterface
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class StreamActionButton(item: Episode) : EpisodeActionButton(item) {
    override fun getLabel(): Int {
        return R.string.stream_label
    }

    override fun getDrawable(): Int {
        return R.drawable.ic_stream
    }

    @UnstableApi override fun onClick(context: Context) {
        if (item.media == null) return
//        Logd("StreamActionButton", "item.feed: ${item.feedId}")
        val media = if (item.feedId != null) item.media!! else RemoteMedia(item)
        logAction(UsageStatistics.ACTION_STREAM)
        if (!isStreamingAllowed) {
            StreamingConfirmationDialog(context, media).show()
            return
        }
        stream(context, media)
    }

    class StreamingConfirmationDialog(private val context: Context, private val playable: Playable) {
        @UnstableApi
        fun show() {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.stream_label)
                .setMessage(R.string.confirm_mobile_streaming_notification_message)
                .setPositiveButton(R.string.confirm_mobile_streaming_button_once) { _: DialogInterface?, _: Int -> stream(context, playable) }
                .setNegativeButton(R.string.confirm_mobile_streaming_button_always) { _: DialogInterface?, _: Int ->
                    isAllowMobileStreaming = true
                    stream(context, playable)
                }
                .setNeutralButton(R.string.cancel_label, null)
                .show()
        }
    }

    companion object {

        fun stream(context: Context, media: Playable) {
            if (media !is EpisodeMedia || !InTheatre.isCurMedia(media)) clearCurTempSpeed()
            PlaybackServiceStarter(context, media).shouldStreamThisTime(true).callEvenIfRunning(true).start()
            if (media is EpisodeMedia && media.episode != null) EventFlow.postEvent(FlowEvent.PlayEvent(media.episode!!))
            playVideoIfNeeded(context, media)
        }
    }
}
