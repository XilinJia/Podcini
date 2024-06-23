package ac.mdiq.podcini.ui.actions.swipeactions

import android.content.Context
import androidx.fragment.app.Fragment
import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.actions.menuhandler.EpisodeMenuHandler.markReadWithUndo
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.utils.EpisodeFilter

class TogglePlaybackStateSwipeAction : SwipeAction {
    override fun getId(): String {
        return SwipeAction.TOGGLE_PLAYED
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_mark_played
    }

    override fun getActionColor(): Int {
        return R.attr.icon_gray
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.toggle_played_label)
    }

    override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
        val newState = if (item.playState == Episode.UNPLAYED) Episode.PLAYED else Episode.UNPLAYED
        markReadWithUndo(fragment, item, newState, willRemove(filter, item))
    }

    override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
        return if (item.playState == Episode.NEW) filter.showPlayed || filter.showNew
        else filter.showUnplayed || filter.showPlayed || filter.showNew
    }
}
