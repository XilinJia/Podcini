package ac.mdiq.podcini.ui.actions.actionbutton

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.PlaybackServiceStarter
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.preferences.UsageStatistics
import ac.mdiq.podcini.preferences.UsageStatistics.logAction
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.playback.MediaType
import ac.mdiq.podcini.storage.model.playback.RemoteMedia
import ac.mdiq.podcini.ui.dialog.StreamingConfirmationDialog
import ac.mdiq.podcini.util.NetworkUtils.isStreamingAllowed
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.content.Context
import androidx.media3.common.util.UnstableApi

class StreamActionButton(item: FeedItem) : ItemActionButton(item) {
    override fun getLabel(): Int {
        return R.string.stream_label
    }

    override fun getDrawable(): Int {
        return R.drawable.ic_stream
    }

    @UnstableApi override fun onClick(context: Context) {
        if (item.media == null) return
//        Log.d("StreamActionButton", "item.feed: ${item.feedId}")
        val media = if (item.feedId > 0) item.media!! else RemoteMedia(item)
        logAction(UsageStatistics.ACTION_STREAM)

        if (!isStreamingAllowed) {
            StreamingConfirmationDialog(context, media).show()
            return
        }

        PlaybackServiceStarter(context, media)
            .shouldStreamThisTime(true)
            .callEvenIfRunning(true)
            .start()
        EventFlow.postEvent(FlowEvent.StartPlayEvent(item))

        if (media.getMediaType() == MediaType.VIDEO) context.startActivity(getPlayerActivityIntent(context, MediaType.VIDEO))
    }
}
