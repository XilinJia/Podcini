package ac.mdiq.podvinci.adapter.actionbutton

import android.content.Context
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.core.preferences.UsageStatistics
import ac.mdiq.podvinci.core.preferences.UsageStatistics.logAction
import ac.mdiq.podvinci.core.service.playback.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podvinci.core.util.NetworkUtils.isStreamingAllowed
import ac.mdiq.podvinci.core.util.playback.PlaybackServiceStarter
import ac.mdiq.podvinci.dialog.StreamingConfirmationDialog
import ac.mdiq.podvinci.model.feed.FeedItem
import ac.mdiq.podvinci.model.playback.MediaType

class StreamActionButton(item: FeedItem) : ItemActionButton(item) {
    override fun getLabel(): Int {
        return R.string.stream_label
    }
    override fun getDrawable(): Int {
        return R.drawable.ic_stream
    }
    @UnstableApi override fun onClick(context: Context) {
        val media = item.media ?: return
        logAction(UsageStatistics.ACTION_STREAM)

        if (!isStreamingAllowed) {
            StreamingConfirmationDialog(context, media).show()
            return
        }
        PlaybackServiceStarter(context, media)
            .callEvenIfRunning(true)
            .start()

        if (media.getMediaType() == MediaType.VIDEO) {
            context.startActivity(getPlayerActivityIntent(context, media))
        }
    }
}
