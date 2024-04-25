package ac.mdiq.podcini.ui.actions

import ac.mdiq.podcini.ui.activity.MainActivity
import android.util.Log
import androidx.annotation.PluralsRes
import com.google.android.material.snackbar.Snackbar
import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.util.LongList
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.ui.view.LocalDeleteModal
import androidx.media3.common.util.UnstableApi

@UnstableApi
class EpisodeMultiSelectActionHandler(private val activity: MainActivity, private val actionId: Int) {
    private var totalNumItems = 0
    private var snackbar: Snackbar? = null

    fun handleAction(items: List<FeedItem>) {
        when (actionId) {
            R.id.add_to_favorite_batch -> markFavorite(items)
            R.id.add_to_queue_batch -> queueChecked(items)
            R.id.remove_from_queue_batch -> removeFromQueueChecked(items)
            R.id.mark_read_batch -> markedCheckedPlayed(items)
            R.id.mark_unread_batch -> markedCheckedUnplayed(items)
            R.id.download_batch -> downloadChecked(items)
            R.id.delete_batch -> LocalDeleteModal.showLocalFeedDeleteWarningIfNecessary(activity, items) { deleteChecked(items) }
            else -> Log.e(TAG, "Unrecognized speed dial action item. Do nothing. id=$actionId")
        }
    }

    private fun queueChecked(items: List<FeedItem>) {
        // Check if an episode actually contains any media files before adding it to queue
        val toQueue = LongList(items.size)
        for (episode in items) {
            if (episode.hasMedia()) toQueue.add(episode.id)
        }
        DBWriter.addQueueItem(activity, true, *toQueue.toArray())
        showMessage(R.plurals.added_to_queue_batch_label, toQueue.size())
    }

    private fun removeFromQueueChecked(items: List<FeedItem>) {
        val checkedIds = getSelectedIds(items)
        DBWriter.removeQueueItem(activity, true, *checkedIds)
        showMessage(R.plurals.removed_from_queue_batch_label, checkedIds.size)
    }

    private fun markedCheckedPlayed(items: List<FeedItem>) {
        val checkedIds = getSelectedIds(items)
        DBWriter.markItemPlayed(FeedItem.PLAYED, *checkedIds)
        showMessage(R.plurals.marked_read_batch_label, checkedIds.size)
    }

    private fun markedCheckedUnplayed(items: List<FeedItem>) {
        val checkedIds = getSelectedIds(items)
        DBWriter.markItemPlayed(FeedItem.UNPLAYED, *checkedIds)
        showMessage(R.plurals.marked_unread_batch_label, checkedIds.size)
    }

    private fun markFavorite(items: List<FeedItem>) {
        for (item in items) {
            DBWriter.addFavoriteItem(item)
        }
        showMessage(R.plurals.marked_favorite_batch_label, items.size)
    }

    private fun downloadChecked(items: List<FeedItem>) {
        // download the check episodes in the same order as they are currently displayed
        for (episode in items) {
            if (episode.hasMedia() && episode.feed != null && !episode.feed!!.isLocalFeed) DownloadServiceInterface.get()?.download(activity, episode)
        }
        showMessage(R.plurals.downloading_batch_label, items.size)
    }

    private fun deleteChecked(items: List<FeedItem>) {
        var countHasMedia = 0
        for (feedItem in items) {
            if (feedItem.hasMedia() && feedItem.media!!.isDownloaded()) {
                countHasMedia++
                DBWriter.deleteFeedMediaOfItem(activity, feedItem.media!!.id)
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

    private fun getSelectedIds(items: List<FeedItem>): LongArray {
        val checkedIds = LongArray(items.size)
        for (i in items.indices) {
            checkedIds[i] = items[i].id
        }
        return checkedIds
    }

    companion object {
        private const val TAG = "EpisodeSelectHandler"
    }
}
