package ac.mdiq.podcini.ui.actions

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.SelectQueueDialogBinding
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.storage.database.Episodes
import ac.mdiq.podcini.storage.database.Episodes.setPlayState
import ac.mdiq.podcini.storage.database.Queues
import ac.mdiq.podcini.storage.database.Queues.addToQueueSync
import ac.mdiq.podcini.storage.database.Queues.removeFromAllQueuesQuiet
import ac.mdiq.podcini.storage.database.Queues.removeFromQueue
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.utils.LocalDeleteModal
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import android.app.Activity
import android.content.DialogInterface
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioButton
import androidx.annotation.PluralsRes
import androidx.media3.common.util.UnstableApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.runBlocking
import java.lang.ref.WeakReference


@UnstableApi
class EpisodeMultiSelectHandler(private val activity: MainActivity, private val actionId: Int) {
    private var totalNumItems = 0
    private var snackbar: Snackbar? = null

    fun handleAction(items: List<Episode>) {
        when (actionId) {
            R.id.toggle_favorite_batch -> toggleFavorite(items)
            R.id.add_to_queue_batch -> queueChecked(items)
            R.id.put_in_queue_batch -> PutToQueueDialog(activity, items).show()
            R.id.remove_from_queue_batch -> removeFromQueueChecked(items)
            R.id.toggle_played_batch -> {
                setPlayState(Episode.PlayState.UNSPECIFIED.code, false, *items.toTypedArray())
//                showMessage(R.plurals.marked_read_batch_label, items.size)
            }
            R.id.download_batch -> downloadChecked(items)
            R.id.delete_batch -> deleteChecked(items)
            else -> Log.e(TAG, "Unrecognized speed dial action item. Do nothing. id=$actionId")
        }
    }

    private fun queueChecked(items: List<Episode>) {
        // Check if an episode actually contains any media files before adding it to queue
//        val toQueue = mutableListOf<Long>()
//        for (episode in items) {
//            if (episode.media != null) toQueue.add(episode.id)
//        }
        Queues.addToQueue(true, *items.toTypedArray())
        showMessage(R.plurals.added_to_queue_batch_label, items.size)
    }

    private fun removeFromQueueChecked(items: List<Episode>) {
//        val checkedIds = getSelectedIds(items)
        removeFromQueue(*items.toTypedArray())
        showMessage(R.plurals.removed_from_queue_batch_label, items.size)
    }

    private fun toggleFavorite(items: List<Episode>) {
        for (item in items) {
            Episodes.setFavorite(item, null)
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
        LocalDeleteModal.deleteEpisodesWarnLocal(activity, items)
        showMessage(R.plurals.deleted_multi_episode_batch_label, items.size)
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

//    private fun getSelectedIds(items: List<Episode>): List<Long> {
//        val checkedIds = mutableListOf<Long>()
//        for (i in items.indices) {
//            checkedIds.add(items[i].id)
//        }
//        return checkedIds
//    }

    class PutToQueueDialog(activity: Activity, val items: List<Episode>) {
        private val activityRef: WeakReference<Activity> = WeakReference(activity)

        fun show() {
            val activity = activityRef.get() ?: return
            val binding = SelectQueueDialogBinding.inflate(LayoutInflater.from(activity))
            binding.removeCheckbox.visibility = View.VISIBLE

            val queues = realm.query(PlayQueue::class).find()
            for (i in queues.indices) {
                val radioButton = RadioButton(activity)
                radioButton.text = queues[i].name
                radioButton.textSize = 20f
                radioButton.tag = i
                binding.radioGroup.addView(radioButton)
            }
            var toQueue: PlayQueue = curQueue
            binding.radioGroup.setOnCheckedChangeListener { group, checkedId ->
                val radioButton = group.findViewById<RadioButton>(checkedId)
                val selectedIndex = radioButton.tag as Int
                toQueue = queues[selectedIndex]
            }
            val dialog = MaterialAlertDialogBuilder(activity)
                .setView(binding.root)
                .setTitle(R.string.put_in_queue_label)
                .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
//                    val queues = realm.query(PlayQueue::class).find()
                    if (binding.removeCheckbox.isChecked) {
                        val toRemove = mutableSetOf<Long>()
                        val toRemoveCur = mutableListOf<Episode>()
                        items.forEach { e ->
                            if (curQueue.contains(e)) toRemoveCur.add(e)
                        }
                        items.forEach { e ->
                            for (q in queues) {
                                if (q.contains(e)) {
                                    toRemove.add(e.id)
                                    break
                                }
                            }
                        }
                        if (toRemove.isNotEmpty()) runBlocking { removeFromAllQueuesQuiet(toRemove.toList()) }
                        if (toRemoveCur.isNotEmpty()) EventFlow.postEvent(FlowEvent.QueueEvent.removed(toRemoveCur))
                    }
                    items.forEach { e ->
                        runBlocking { addToQueueSync(false, e, toQueue) }
                    }
                }
                .setNegativeButton(R.string.cancel_label, null)
            dialog.show()
        }
    }

    companion object {
        private val TAG: String = EpisodeMultiSelectHandler::class.simpleName ?: "Anonymous"
    }
}
