package ac.mdiq.podcini.ui.actions

import ac.mdiq.podcini.ui.activity.MainActivity
import android.util.Log
import androidx.annotation.PluralsRes
import com.google.android.material.snackbar.Snackbar
import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.storage.database.Episodes
import ac.mdiq.podcini.storage.database.Episodes.deleteMediaOfEpisode
import ac.mdiq.podcini.storage.database.Episodes.setPlayState
import ac.mdiq.podcini.storage.database.Queues
import ac.mdiq.podcini.storage.database.Queues.removeFromQueue
import ac.mdiq.podcini.ui.utils.LocalDeleteModal
import androidx.media3.common.util.UnstableApi


@UnstableApi
class EpisodeMultiSelectHandler(private val activity: MainActivity, private val actionId: Int) {
    private var totalNumItems = 0
    private var snackbar: Snackbar? = null

    fun handleAction(items: List<Episode>) {
        when (actionId) {
            R.id.add_to_favorite_batch -> markFavorite(items, true)
            R.id.remove_favorite_batch -> markFavorite(items, false)
            R.id.add_to_queue_batch -> queueChecked(items)
            R.id.remove_from_queue_batch -> removeFromQueueChecked(items)
            R.id.mark_read_batch -> {
                setPlayState(Episode.PLAYED, false, *items.toTypedArray())
                showMessage(R.plurals.marked_read_batch_label, items.size)
            }
            R.id.mark_unread_batch -> {
                setPlayState(Episode.UNPLAYED, false, *items.toTypedArray())
                showMessage(R.plurals.marked_unread_batch_label, items.size)
            }
            R.id.download_batch -> downloadChecked(items)
            R.id.delete_batch -> LocalDeleteModal.showLocalFeedDeleteWarningIfNecessary(activity, items) { deleteChecked(items) }
            else -> Log.e(TAG, "Unrecognized speed dial action item. Do nothing. id=$actionId")
        }
    }

    private fun queueChecked(items: List<Episode>) {
        // Check if an episode actually contains any media files before adding it to queue
        val toQueue = mutableListOf<Long>()
        for (episode in items) {
            if (episode.media != null) toQueue.add(episode.id)
        }
        Queues.addToQueue(true, *items.toTypedArray())
        showMessage(R.plurals.added_to_queue_batch_label, toQueue.size)
    }

    private fun removeFromQueueChecked(items: List<Episode>) {
        val checkedIds = getSelectedIds(items)
        removeFromQueue(activity, *items.toTypedArray())
        showMessage(R.plurals.removed_from_queue_batch_label, checkedIds.size)
    }

//    private fun markedCheckedPlayed(items: List<FeedItem>) {
////        val checkedIds = getSelectedIds(items)
//        DBWriter.markItemsPlayed(FeedItem.PLAYED, *items.toTypedArray())
//        showMessage(R.plurals.marked_read_batch_label, items.size)
//    }
//
//    private fun markedCheckedUnplayed(items: List<FeedItem>) {
////        val checkedIds = getSelectedIds(items)
//        DBWriter.markItemsPlayed(FeedItem.UNPLAYED, *items.toTypedArray())
//        showMessage(R.plurals.marked_unread_batch_label, items.size)
//    }

    private fun markFavorite(items: List<Episode>, stat: Boolean) {
        for (item in items) {
            Episodes.setFavorite(item, true)
        }
        showMessage(R.plurals.marked_favorite_batch_label, items.size)
    }

    private fun downloadChecked(items: List<Episode>) {
        // download the check episodes in the same order as they are currently displayed
        for (episode in items) {
            if (episode.media != null && episode.feed != null && !episode.feed!!.isLocalFeed) DownloadServiceInterface.get()?.download(activity, episode)
        }
        showMessage(R.plurals.downloading_batch_label, items.size)
    }

    private fun deleteChecked(items: List<Episode>) {
        var countHasMedia = 0
        for (feedItem in items) {
            if (feedItem.media != null && feedItem.media!!.downloaded) {
                countHasMedia++
                deleteMediaOfEpisode(activity, feedItem)
            }
        }
        showMessage(R.plurals.deleted_multi_episode_batch_label, countHasMedia)
    }

    private fun showMessage(@PluralsRes msgId: Int, numItems: Int) {
        totalNumItems += numItems
        activity.runOnUiThread {
            val text: String = activity.resources.getQuantityString(msgId, totalNumItems, totalNumItems)
            if (snackbar != null) {
                snackbar?.setText(text)
                snackbar?.show() // Resets the timeout
            } else snackbar = activity.showSnackbarAbovePlayer(text, Snackbar.LENGTH_LONG)
        }
    }

    private fun getSelectedIds(items: List<Episode>): List<Long> {
        val checkedIds = mutableListOf<Long>()
        for (i in items.indices) {
            checkedIds.add(items[i].id)
        }
        return checkedIds
    }

    companion object {
        private val TAG: String = EpisodeMultiSelectHandler::class.simpleName ?: "Anonymous"
    }
}
