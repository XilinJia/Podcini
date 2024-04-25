package ac.mdiq.podcini.ui.actions.actionbutton

import android.content.Context
import android.view.View
import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.ui.view.LocalDeleteModal.showLocalFeedDeleteWarningIfNecessary
import androidx.media3.common.util.UnstableApi

class DeleteActionButton(item: FeedItem) : ItemActionButton(item) {
    override fun getLabel(): Int {
        return R.string.delete_label
    }
    override fun getDrawable(): Int {
        return R.drawable.ic_delete
    }
    @UnstableApi override fun onClick(context: Context) {
        val media = item.media ?: return
        showLocalFeedDeleteWarningIfNecessary(context, listOf(item)) { DBWriter.deleteFeedMediaOfItem(context, media.id) }
    }

    override val visibility: Int
        get() {
            if (item.media != null && (item.media!!.isDownloaded() || item.feed?.isLocalFeed == true)) return View.VISIBLE
            return View.INVISIBLE
        }
}
