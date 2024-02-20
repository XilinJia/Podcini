package ac.mdiq.podcini.adapter.actionbutton

import android.content.Context
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.R
import ac.mdiq.podcini.core.service.playback.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.core.util.playback.PlaybackServiceStarter
import ac.mdiq.podcini.model.feed.FeedItem
import ac.mdiq.podcini.model.playback.MediaType

class PlayLocalActionButton(item: FeedItem?) : ItemActionButton(item!!) {
    override fun getLabel(): Int {
        return R.string.play_label
    }
    override fun getDrawable(): Int {
        return R.drawable.ic_play_24dp
    }
    @UnstableApi override fun onClick(context: Context) {
        val media = item.media ?: return

        PlaybackServiceStarter(context, media)
            .callEvenIfRunning(true)
            .start()

        if (media.getMediaType() == MediaType.VIDEO) {
            context.startActivity(getPlayerActivityIntent(context, media))
        }
    }
}
