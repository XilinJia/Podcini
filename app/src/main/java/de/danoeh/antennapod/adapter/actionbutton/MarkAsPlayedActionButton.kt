package de.danoeh.antennapod.adapter.actionbutton

import android.content.Context
import android.view.View
import de.danoeh.antennapod.R
import de.danoeh.antennapod.core.storage.DBWriter
import de.danoeh.antennapod.model.feed.FeedItem

class MarkAsPlayedActionButton(item: FeedItem) : ItemActionButton(item) {
    override fun getLabel(): Int {
        return (if (item.hasMedia()) R.string.mark_read_label else R.string.mark_read_no_media_label)
    }
    override fun getDrawable(): Int {
        return R.drawable.ic_check
    }
    override fun onClick(context: Context) {
        if (!item.isPlayed()) {
            DBWriter.markItemPlayed(item, FeedItem.PLAYED, true)
        }
    }

    override val visibility: Int
        get() = if ((item.isPlayed())) View.INVISIBLE else View.VISIBLE
}
