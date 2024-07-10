package ac.mdiq.podcini.ui.actions.actionbutton

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.ui.utils.LocalDeleteModal.deleteEpisodesWarnLocal
import android.content.Context
import android.view.View
import androidx.media3.common.util.UnstableApi

class DeleteActionButton(item: Episode) : EpisodeActionButton(item) {
    override fun getLabel(): Int {
        return R.string.delete_label
    }
    override fun getDrawable(): Int {
        return R.drawable.ic_delete
    }
    @UnstableApi override fun onClick(context: Context) {
        deleteEpisodesWarnLocal(context, listOf(item))
    }

    override val visibility: Int
        get() {
            if (item.media != null && (item.media!!.downloaded || item.feed?.isLocalFeed == true)) return View.VISIBLE
            return View.INVISIBLE
        }
}
