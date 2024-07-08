package ac.mdiq.podcini.ui.actions.swipeactions

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter

class NoActionSwipeAction : SwipeAction {
    override fun getId(): String {
        return SwipeAction.NO_ACTION
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_questionmark
    }

    override fun getActionColor(): Int {
        return R.attr.icon_red
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.no_action_label)
    }

    @UnstableApi override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {}

    override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
        return false
    }
}
