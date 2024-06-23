package ac.mdiq.podcini.ui.actions.swipeactions

import android.content.Context
import androidx.fragment.app.Fragment
import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.utils.EpisodeFilter

class ShowFirstSwipeDialogAction : SwipeAction {
    override fun getId(): String {
        return "SHOW_FIRST_SWIPE_DIALOG"
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_settings
    }

    override fun getActionColor(): Int {
        return R.attr.icon_gray
    }

    override fun getTitle(context: Context): String {
        return ""
    }

    override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
        //handled in SwipeActions
    }

    override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
        return false
    }
}
