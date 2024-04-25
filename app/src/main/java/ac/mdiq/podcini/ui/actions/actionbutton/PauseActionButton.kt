package ac.mdiq.podcini.ui.actions.actionbutton

import android.content.Context
import android.view.KeyEvent
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.R
import ac.mdiq.podcini.receiver.MediaButtonReceiver.Companion.createIntent
import ac.mdiq.podcini.util.PlaybackStatus.isCurrentlyPlaying
import ac.mdiq.podcini.storage.model.feed.FeedItem

class PauseActionButton(item: FeedItem) : ItemActionButton(item) {
    override fun getLabel(): Int {
        return R.string.pause_label
    }
    override fun getDrawable(): Int {
        return R.drawable.ic_pause
    }
    @UnstableApi override fun onClick(context: Context) {
        val media = item.media ?: return

        if (isCurrentlyPlaying(media)) context.sendBroadcast(createIntent(context, KeyEvent.KEYCODE_MEDIA_PAUSE))
    }
}
