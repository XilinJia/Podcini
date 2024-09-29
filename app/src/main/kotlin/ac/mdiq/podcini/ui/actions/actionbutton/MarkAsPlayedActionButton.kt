package ac.mdiq.podcini.ui.actions.actionbutton

import android.content.Context
import android.view.View
import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.Episodes.setPlayState
import ac.mdiq.podcini.storage.model.Episode
import androidx.media3.common.util.UnstableApi

class MarkAsPlayedActionButton(item: Episode) : EpisodeActionButton(item) {
    override val visibility: Int
        get() = if (item.isPlayed()) View.INVISIBLE else View.VISIBLE

    override fun getLabel(): Int {
        return (if (item.media != null) R.string.mark_read_label else R.string.mark_read_no_media_label)
    }

    override fun getDrawable(): Int {
        return R.drawable.ic_check
    }

    @UnstableApi override fun onClick(context: Context) {
        if (!item.isPlayed()) setPlayState(Episode.PlayState.PLAYED.code, true, item)
        actionState.value = getLabel()
    }

}
