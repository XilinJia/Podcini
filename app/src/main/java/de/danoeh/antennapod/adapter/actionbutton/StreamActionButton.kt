package de.danoeh.antennapod.adapter.actionbutton

import android.content.Context
import androidx.media3.common.util.UnstableApi
import de.danoeh.antennapod.R
import de.danoeh.antennapod.core.preferences.UsageStatistics
import de.danoeh.antennapod.core.preferences.UsageStatistics.logAction
import de.danoeh.antennapod.core.service.playback.PlaybackService.Companion.getPlayerActivityIntent
import de.danoeh.antennapod.core.util.NetworkUtils.isStreamingAllowed
import de.danoeh.antennapod.core.util.playback.PlaybackServiceStarter
import de.danoeh.antennapod.dialog.StreamingConfirmationDialog
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.playback.MediaType

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
