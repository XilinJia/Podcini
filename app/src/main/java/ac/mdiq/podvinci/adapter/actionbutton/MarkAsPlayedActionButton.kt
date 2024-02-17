package ac.mdiq.podvinci.adapter.actionbutton

import android.content.Context
import android.view.View
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.core.storage.DBWriter
import ac.mdiq.podvinci.model.feed.FeedItem
import androidx.media3.common.util.UnstableApi

class MarkAsPlayedActionButton(item: FeedItem) : ItemActionButton(item) {
    override fun getLabel(): Int {
        return (if (item.hasMedia()) R.string.mark_read_label else R.string.mark_read_no_media_label)
    }
    override fun getDrawable(): Int {
        return R.drawable.ic_check
    }
    @UnstableApi override fun onClick(context: Context) {
        if (!item.isPlayed()) {
            DBWriter.markItemPlayed(item, FeedItem.PLAYED, true)
        }
    }

    override val visibility: Int
        get() = if (item.isPlayed()) View.INVISIBLE else View.VISIBLE
}