package ac.mdiq.podvinci.adapter.actionbutton

import android.content.Context
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.core.service.playback.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podvinci.core.storage.DBTasks
import ac.mdiq.podvinci.core.util.playback.PlaybackServiceStarter
import ac.mdiq.podvinci.model.feed.FeedItem
import ac.mdiq.podvinci.model.playback.MediaType

class PlayActionButton(item: FeedItem) : ItemActionButton(item) {
    override fun getLabel(): Int {
        return R.string.play_label
    }
    override fun getDrawable(): Int {
        return R.drawable.ic_play_24dp
    }
    @UnstableApi override fun onClick(context: Context) {
        val media = item.media ?: return
        if (!media.fileExists()) {
            DBTasks.notifyMissingFeedMediaFile(context, media)
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
