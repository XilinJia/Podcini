package ac.mdiq.podcini.ui.actions.actionbutton

import android.content.Context
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.playback.PlaybackServiceStarter
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.playback.MediaType

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

        if (media.getMediaType() == MediaType.VIDEO) context.startActivity(getPlayerActivityIntent(context, MediaType.VIDEO))
    }
}
