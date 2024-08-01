package ac.mdiq.podcini.ui.actions.swipeactions

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.storage.database.Episodes.setPlayState
import ac.mdiq.podcini.storage.database.Queues.removeFromQueue
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.content.Context
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job

class RemoveFromQueueSwipeAction : SwipeAction {
    override fun getId(): String {
        return SwipeAction.REMOVE_FROM_QUEUE
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_playlist_remove
    }

    override fun getActionColor(): Int {
        return androidx.appcompat.R.attr.colorAccent
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.remove_from_queue_label)
    }

    @OptIn(UnstableApi::class) override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
        val position: Int = curQueue.episodes.indexOf(item)
        removeFromQueue(item)
        if (willRemove(filter, item)) {
            (fragment.requireActivity() as MainActivity).showSnackbarAbovePlayer(fragment.resources.getQuantityString(R.plurals.removed_from_queue_batch_label, 1, 1), Snackbar.LENGTH_LONG)
                .setAction(fragment.getString(R.string.undo)) {
                    addToQueueAt(item, position)
                }
        }
    }

    override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
        return filter.showQueued || filter.showNotQueued
    }

    /**
     * Inserts a Episode in the queue at the specified index. The 'read'-attribute of the Episode will be set to
     * true. If the Episode is already in the queue, the queue will not be modified.
     * @param episode                the Episode that should be added to the queue.
     * @param index               Destination index. Must be in range 0..queue.size()
     * @throws IndexOutOfBoundsException if index < 0 || index >= queue.size()
     */
    @UnstableApi
    fun addToQueueAt(episode: Episode, index: Int) : Job {
        return runOnIOScope {
            if (curQueue.episodeIds.contains(episode.id)) return@runOnIOScope
            if (episode.isNew) setPlayState(Episode.UNPLAYED, false, episode)
            curQueue = upsert(curQueue) {
                it.episodeIds.add(index, episode.id)
                it.update()
            }
//            curQueue.episodes.add(index, episode)
            EventFlow.postEvent(FlowEvent.QueueEvent.added(episode, index))
//            if (performAutoDownload) autodownloadEpisodeMedia(context)
        }
    }
}
