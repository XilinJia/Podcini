package de.danoeh.antennapod.adapter.actionbutton

import android.content.Context
import android.view.View
import de.danoeh.antennapod.R
import de.danoeh.antennapod.core.storage.DBWriter
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.view.LocalDeleteModal.showLocalFeedDeleteWarningIfNecessary

class DeleteActionButton(item: FeedItem) : ItemActionButton(item) {
    override fun getLabel(): Int {
        return R.string.delete_label
    }
    override fun getDrawable(): Int {
        return R.drawable.ic_delete
    }
    override fun onClick(context: Context) {
        val media = item.media ?: return

        showLocalFeedDeleteWarningIfNecessary(context, listOf(item)
        ) { DBWriter.deleteFeedMediaOfItem(context, media.id) }
    }

    override val visibility: Int
        get() {
            if (item.media != null && (item.media!!.isDownloaded() || item.feed?.isLocalFeed == true)) {
                return View.VISIBLE
            }

            return View.INVISIBLE
        }
}
