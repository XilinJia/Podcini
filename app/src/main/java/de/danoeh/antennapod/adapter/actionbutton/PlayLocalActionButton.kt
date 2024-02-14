package de.danoeh.antennapod.adapter.actionbutton

import android.content.Context
import androidx.media3.common.util.UnstableApi
import de.danoeh.antennapod.R
import de.danoeh.antennapod.core.service.playback.PlaybackService.Companion.getPlayerActivityIntent
import de.danoeh.antennapod.core.util.playback.PlaybackServiceStarter
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.playback.MediaType

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
