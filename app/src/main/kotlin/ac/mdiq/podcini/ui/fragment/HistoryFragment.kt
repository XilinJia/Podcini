package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.storage.utils.EpisodesPermutors.getPermutor
import ac.mdiq.podcini.ui.dialog.ConfirmationDialog
import ac.mdiq.podcini.ui.dialog.DatesFilterDialog
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.min

class HistoryFragment : BaseEpisodesFragment() {
//    private var sortOrder : EpisodeSortOrder = EpisodeSortOrder.PLAYED_DATE_NEW_OLD
    private var startDate : Long = 0L
    private var endDate : Long = Date().time
    private var allHistory: List<Episode> = listOf()

    override fun getPrefName(): String {
        return TAG
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        Logd(TAG, "fragment onCreateView")
        sortOrder = EpisodeSortOrder.PLAYED_DATE_NEW_OLD
        toolbar.inflateMenu(R.menu.playback_history)
        toolbar.setTitle(R.string.playback_history_label)
        updateToolbar()
        return root
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
    }

    override fun onDestroyView() {
        allHistory = listOf()
        super.onDestroyView()
    }

    override fun onSort(order: EpisodeSortOrder) {
//        EventFlow.postEvent(FlowEvent.HistoryEvent(sortOrder))
        sortOrder = order
        loadItems()
        updateToolbar()
    }

     override fun onMenuItemClick(item: MenuItem): Boolean {
        if (super.onOptionsItemSelected(item)) return true
        when (item.itemId) {
            R.id.episodes_sort -> showSortDialog = true
            R.id.filter_items -> {
                val dialog = object: DatesFilterDialog(requireContext(), 0L) {
                    override fun initParams() {
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.YEAR, -1) // subtract 1 year
                        timeFilterFrom = calendar.timeInMillis
                        showMarkPlayed = false
                    }
                    override fun callback(timeFilterFrom: Long, timeFilterTo: Long, includeMarkedAsPlayed: Boolean) {
                        EventFlow.postEvent(FlowEvent.HistoryEvent(sortOrder, timeFilterFrom, timeFilterTo))
                    }
                }
                dialog.show()
            }
            R.id.clear_history_item -> {
                val conDialog: ConfirmationDialog = object : ConfirmationDialog(requireContext(), R.string.clear_history_label, R.string.clear_playback_history_msg) {
                    override fun onConfirmButtonPressed(dialog: DialogInterface) {
                        dialog.dismiss()
                        clearHistory()
                    }
                }
                conDialog.createNewDialog().show()
            }
            else -> return false
        }
        return true
    }

    override fun updateToolbar() {
        // Not calling super, as we do not have a refresh button that could be updated
        toolbar.menu.findItem(R.id.episodes_sort).isVisible = episodes.isNotEmpty()
        toolbar.menu.findItem(R.id.filter_items).isVisible = episodes.isNotEmpty()
        toolbar.menu.findItem(R.id.clear_history_item).isVisible = episodes.isNotEmpty()

        swipeActions.setFilter(getFilter())
        var info = "${episodes.size} episodes"
        if (getFilter().properties.isNotEmpty()) {
            info += " - ${getString(R.string.filtered_label)}"
        }
        infoBarText.value = info
    }

    private var eventSink: Job?     = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    private fun procFlowEvents() {
        if (eventSink != null) return
        eventSink = lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.HistoryEvent -> {
                        sortOrder = event.sortOrder
                        if (event.startDate > 0) startDate = event.startDate
                        endDate = event.endDate
                        loadItems()
                        updateToolbar()
                    }
                    else -> {}
                }
            }
        }
    }

    private var loadItemsRunning = false
    override fun loadData(): List<Episode> {
        if (!loadItemsRunning) {
            loadItemsRunning = true
            allHistory = getHistory(0, Int.MAX_VALUE, startDate, endDate, sortOrder).toMutableList()
            loadItemsRunning = false
        }
        if (allHistory.isEmpty()) return listOf()
        return allHistory
    }

    override fun loadTotalItemCount(): Int {
        return getNumberOfPlayed().toInt()
    }

    fun clearHistory() : Job {
        Logd(TAG, "clearHistory called")
        return runOnIOScope {
            val episodes = realm.query(Episode::class).query("media.playbackCompletionTime > 0 || media.lastPlayedTime > 0").find()
            for (e in episodes) {
                upsert(e) {
                    it.media?.playbackCompletionDate = null
                    it.media?.lastPlayedTime = 0
                }
            }
            EventFlow.postEvent(FlowEvent.HistoryEvent())
        }
    }

    companion object {
        val TAG = HistoryFragment::class.simpleName ?: "Anonymous"

        fun getNumberOfPlayed(): Long {
            Logd(TAG, "getNumberOfPlayed called")
            return realm.query(EpisodeMedia::class).query("lastPlayedTime > 0 || playbackCompletionTime > 0").count().find()
        }

        /**
         * Loads the playback history from the database. A FeedItem is in the playback history if playback of the correpsonding episode
         * has been played ot completed at least once.
         * @param limit The maximum number of episodes to return.
         * @return The playback history. The FeedItems are sorted by their media's playbackCompletionDate in descending order.
         */
        fun getHistory(offset: Int, limit: Int, start: Long = 0L, end: Long = Date().time,
                       sortOrder: EpisodeSortOrder = EpisodeSortOrder.PLAYED_DATE_NEW_OLD): List<Episode> {
            Logd(TAG, "getHistory() called")
            val medias = realm.query(EpisodeMedia::class).query("(playbackCompletionTime > 0) OR (lastPlayedTime > \$0 AND lastPlayedTime <= \$1)", start, end).find()
            var episodes: MutableList<Episode> = mutableListOf()
            for (m in medias) {
                val item_ = m.episodeOrFetch()
                if (item_ != null) episodes.add(item_)
                else Logd(TAG, "getHistory: media has null episode: ${m.id}")
            }
            getPermutor(sortOrder).reorder(episodes)
            if (offset > 0 && episodes.size > offset) episodes = episodes.subList(offset, min(episodes.size, offset+limit))
            return episodes
        }
    }
}
