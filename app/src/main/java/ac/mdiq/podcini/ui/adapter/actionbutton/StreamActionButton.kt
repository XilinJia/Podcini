package ac.mdiq.podcini.ui.adapter.actionbutton

import android.content.Context
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.UsageStatistics
import ac.mdiq.podcini.preferences.UsageStatistics.logAction
import ac.mdiq.podcini.service.playback.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.util.NetworkUtils.isStreamingAllowed
import ac.mdiq.podcini.playback.PlaybackServiceStarter
import ac.mdiq.podcini.ui.dialog.StreamingConfirmationDialog
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.playback.MediaType
import ac.mdiq.podcini.storage.model.playback.RemoteMedia

class StreamActionButton(item: FeedItem) : ItemActionButton(item) {
    override fun getLabel(): Int {
        return R.string.stream_label
    }
    override fun getDrawable(): Int {
        return R.drawable.ic_stream
    }
    @UnstableApi override fun onClick(context: Context) {
//        TODO: test
        if (item.media == null) return

        val media = RemoteMedia(item)
        logAction(UsageStatistics.ACTION_STREAM)

        if (!isStreamingAllowed) {
            StreamingConfirmationDialog(context, media).show()
            return
        }
        PlaybackServiceStarter(context, media)
            .shouldStreamThisTime(true)
            .callEvenIfRunning(true)
            .start()

        if (media.getMediaType() == MediaType.VIDEO) {
            context.startActivity(getPlayerActivityIntent(context, media))
        }
    }
}
