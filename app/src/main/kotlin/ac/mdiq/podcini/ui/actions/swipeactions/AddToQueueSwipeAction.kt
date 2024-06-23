package ac.mdiq.podcini.ui.actions.swipeactions

import android.content.Context
import androidx.fragment.app.Fragment
import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.Queues.addToQueue
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.utils.EpisodeFilter
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

class AddToQueueSwipeAction : SwipeAction {
    override fun getId(): String {
        return SwipeAction.ADD_TO_QUEUE
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_playlist_play
    }

    override fun getActionColor(): Int {
        return androidx.appcompat.R.attr.colorAccent
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.add_to_queue_label)
    }

    @OptIn(UnstableApi::class)
    override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
        addToQueue( true, item)
//        else RemoveFromQueueSwipeAction().performAction(item, fragment, filter)
    }

    override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
        return filter.showQueued || filter.showNew
    }
}
