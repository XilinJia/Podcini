package ac.mdiq.podcini.ui.actions.actionbutton

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.utils.NetworkUtils.isAllowMobileStreaming
import ac.mdiq.podcini.playback.PlaybackServiceStarter
import ac.mdiq.podcini.preferences.UsageStatistics
import ac.mdiq.podcini.preferences.UsageStatistics.logAction
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.MediaType
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.storage.model.RemoteMedia
import ac.mdiq.podcini.net.utils.NetworkUtils.isStreamingAllowed
import ac.mdiq.podcini.playback.ServiceStatusHandler.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.preferences.UserPreferences.videoPlayMode
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.Companion.videoMode
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.VideoMode
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

        PlaybackServiceStarter(context, media)
            .shouldStreamThisTime(true)
            .callEvenIfRunning(true)
            .start()
        EventFlow.postEvent(FlowEvent.PlayEvent(item))

        if (item.feed?.preferences?.playAudioOnly != true && videoPlayMode != VideoMode.AUDIO_ONLY.mode && videoMode != VideoMode.AUDIO_ONLY
                && media.getMediaType() == MediaType.VIDEO) context.startActivity(getPlayerActivityIntent(context, MediaType.VIDEO))
    }

    class StreamingConfirmationDialog(private val context: Context, private val playable: Playable) {
        @UnstableApi
        fun show() {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.stream_label)
                .setMessage(R.string.confirm_mobile_streaming_notification_message)
                .setPositiveButton(R.string.confirm_mobile_streaming_button_once) { _: DialogInterface?, _: Int -> stream() }
                .setNegativeButton(R.string.confirm_mobile_streaming_button_always) { _: DialogInterface?, _: Int ->
                    isAllowMobileStreaming = true
                    stream()
                }
                .setNeutralButton(R.string.cancel_label, null)
                .show()
        }

        @UnstableApi
        private fun stream() {
            PlaybackServiceStarter(context, playable)
                .callEvenIfRunning(true)
                .shouldStreamThisTime(true)
                .start()
        }
    }
}
