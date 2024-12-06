package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.storage.database.Episodes
import ac.mdiq.podcini.storage.database.Episodes.getEpisodes
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.storage.model.EpisodeSortOrder.Companion.getPermutor
import ac.mdiq.podcini.ui.actions.DeleteActionButton
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.EpisodeVM
import ac.mdiq.podcini.ui.compose.SpinnerExternalSet
import ac.mdiq.podcini.ui.dialog.DatesFilterDialog
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import java.io.File
import java.util.*
import kotlin.math.min

class EpisodesFragment : BaseEpisodesFragment() {
    val prefs: SharedPreferences by lazy { requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }

    private val spinnerTexts = QuickAccess.entries.map { it.name }
    private var curIndex by mutableIntStateOf(0)
    private lateinit var spinnerView:  ComposeView

    private var startDate : Long = 0L
    private var endDate : Long = Date().time

    private val showClearHistoryDialog = mutableStateOf(false)

    private var episodesSortOrder: EpisodeSortOrder
        get() = EpisodeSortOrder.fromCodeString(appPrefs.getString(UserPreferences.Prefs.prefEpisodesSort.name, "" + EpisodeSortOrder.DATE_NEW_OLD.code))
        set(s) {
            appPrefs.edit().putString(UserPreferences.Prefs.prefEpisodesSort.name, "" + s.code).apply()
        }
    private var prefFilterEpisodes: String
        get() = appPrefs.getString(UserPreferences.Prefs.prefEpisodesFilter.name, "")?:""
        set(filter) {
            appPrefs.edit().putString(UserPreferences.Prefs.prefEpisodesFilter.name, filter).apply()
        }
    private var prefFilterDownloads: String
        get() = appPrefs.getString(UserPreferences.Prefs.prefDownloadsFilter.name, EpisodeFilter.States.downloaded.name) ?: EpisodeFilter.States.downloaded.name
        set(filter) {
            appPrefs.edit().putString(UserPreferences.Prefs.prefDownloadsFilter.name, filter).apply()
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        Logd(TAG, "fragment onCreateView")

        curIndex = prefs.getInt("curIndex", 0)
        spinnerView = ComposeView(requireContext()).apply {
            setContent {
                CustomTheme(requireContext()) {
                    SpinnerExternalSet(items = spinnerTexts, selectedIndex = curIndex) { index: Int ->
                        Logd(QueuesFragment.Companion.TAG, "Item selected: $index")
                        curIndex = index
                        prefs.edit().putInt("curIndex", index).apply()
                        actionButtonToPass = if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name)  {it -> DeleteActionButton(it) } else null
                        loadItems()
                    }
                }
            }
        }
        toolbar.addView(spinnerView)

        toolbar.inflateMenu(R.menu.episodes)
        sortOrder = episodesSortOrder
        updateToolbar()
        return root
    }

    @Composable
    override fun OpenDialog() {
        ComfirmDialog(titleRes = R.string.clear_history_label, message = stringResource(R.string.clear_playback_history_msg), showDialog = showClearHistoryDialog) { clearHistory() }
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

    override fun loadData(): List<Episode> {
        return when (spinnerTexts[curIndex]) {
            QuickAccess.New.name -> getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.new.name), episodesSortOrder, false)
            QuickAccess.Planned.name -> getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.soon.name, EpisodeFilter.States.later.name), episodesSortOrder, false)
            QuickAccess.Repeats.name -> getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.again.name, EpisodeFilter.States.forever.name), episodesSortOrder, false)
            QuickAccess.Liked.name -> getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.good.name, EpisodeFilter.States.superb.name), episodesSortOrder, false)
            QuickAccess.Commented.name -> getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.has_comments.name), episodesSortOrder, false)
            QuickAccess.History.name -> getHistory(0, Int.MAX_VALUE, sortOrder = episodesSortOrder).toMutableList()
            QuickAccess.Downloaded.name -> getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(prefFilterDownloads), episodesSortOrder, false)
            QuickAccess.All.name -> getEpisodes(0, Int.MAX_VALUE, getFilter(), episodesSortOrder, false)
            else -> getEpisodes(0, Int.MAX_VALUE, getFilter(), episodesSortOrder, false)
        }
    }

    override fun getFilter(): EpisodeFilter {
        return EpisodeFilter(prefFilterEpisodes)
    }

    override fun getPrefName(): String {
        return PREF_NAME
    }

    var progressing by mutableStateOf(false)
    override fun updateToolbar() {
        toolbar.menu.findItem(R.id.clear_new).isVisible = episodes.isNotEmpty() && spinnerTexts[curIndex] == QuickAccess.New.name
        toolbar.menu.findItem(R.id.filter_items).isVisible = episodes.isNotEmpty() && spinnerTexts[curIndex] == QuickAccess.All.name
        toolbar.menu.findItem(R.id.clear_history_item).isVisible = episodes.isNotEmpty() && spinnerTexts[curIndex] == QuickAccess.History.name
        toolbar.menu.findItem(R.id.reconcile).isVisible = episodes.isNotEmpty() && spinnerTexts[curIndex] == QuickAccess.Downloaded.name

        var info = "${episodes.size} episodes"
        if (spinnerTexts[curIndex] == QuickAccess.All.name && getFilter().properties.isNotEmpty()) info += " - ${getString(R.string.filtered_label)}"
        else if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name && episodes.isNotEmpty()) {
            var sizeMB: Long = 0
            for (item in episodes) sizeMB += item.media?.size ?: 0
            info += " • " + (sizeMB / 1000000) + " MB"
        }
        if (progressing) info += " - ${getString(R.string.progressing_label)}"
        infoBarText.value = info
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (super.onMenuItemClick(item)) return true

        when (item.itemId) {
            R.id.filter_items -> {
                if (spinnerTexts[curIndex] == QuickAccess.History.name) {
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
                } else showFilterDialog = true
            }
            R.id.episodes_sort -> showSortDialog = true
            R.id.clear_history_item -> showClearHistoryDialog.value = true
//                {
//                val conDialog: ConfirmationDialog = object : ConfirmationDialog(requireContext(), R.string.clear_history_label, R.string.clear_playback_history_msg) {
//                    override fun onConfirmButtonPressed(dialog: DialogInterface) {
//                        dialog.dismiss()
//                        clearHistory()
//                    }
//                }
//                conDialog.createNewDialog().show()
//            }
            R.id.reconcile -> reconcile()
            R.id.clear_new -> clearNew()
            else -> return false
        }
        return true
    }

    private fun clearNew() {
        runOnIOScope {
            progressing = true
            for (e in episodes) if (e.isNew) upsert(e) { it.setPlayed(false) }
            withContext(Dispatchers.Main) {
                progressing = false
                Toast.makeText(requireContext(), "History cleared", Toast.LENGTH_LONG).show()
            }
            loadItems()
        }
    }

    private val nameEpisodeMap: MutableMap<String, Episode> = mutableMapOf()
    private val filesRemoved: MutableList<String> = mutableListOf()
    private fun reconcile() {
        fun traverse(srcFile: File, srcRootDir: File) {
            val relativePath = srcFile.absolutePath.substring(srcRootDir.absolutePath.length+1)
            if (srcFile.isDirectory) {
                Logd(TAG, "traverse folder title: $relativePath")
                val dirFiles = srcFile.listFiles()
                dirFiles?.forEach { file -> traverse(file, srcFile) }
            } else {
                Logd(TAG, "traverse: $srcFile")
                val episode = nameEpisodeMap.remove(relativePath)
                if (episode == null) {
                    Logd(TAG, "traverse: error: episode not exist in map: $relativePath")
                    filesRemoved.add(relativePath)
                    srcFile.delete()
                    return
                }
                Logd(TAG, "traverse found episode: ${episode.title}")
            }
        }
        runOnIOScope {
            progressing = true
            val items = realm.query(Episode::class).query("media.episode == nil").find()
            Logd(TAG, "number of episode with null backlink: ${items.size}")
            for (item in items) if (item.media != null) upsert(item) { it.media!!.episode = it }
            nameEpisodeMap.clear()
            for (e in episodes) {
                var fileUrl = e.media?.fileUrl ?: continue
                fileUrl = fileUrl.substring(fileUrl.lastIndexOf('/') + 1)
                Logd(TAG, "reconcile: fileUrl: $fileUrl")
                nameEpisodeMap[fileUrl] = e
            }
            val mediaDir = requireContext().getExternalFilesDir("media") ?: return@runOnIOScope
            mediaDir.listFiles()?.forEach { file -> traverse(file, mediaDir) }
            Logd(TAG, "reconcile: end, episodes missing file: ${nameEpisodeMap.size}")
            if (nameEpisodeMap.isNotEmpty()) for (e in nameEpisodeMap.values) upsertBlk(e) { it.media?.setfileUrlOrNull(null) }
            loadItems()
            Logd(TAG, "Episodes reconsiled: ${nameEpisodeMap.size}\nFiles removed: ${filesRemoved.size}")
            withContext(Dispatchers.Main) {
                progressing = false
                Toast.makeText(requireContext(), "Episodes reconsiled: ${nameEpisodeMap.size}\nFiles removed: ${filesRemoved.size}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun clearHistory() : Job {
        Logd(TAG, "clearHistory called")
        return runOnIOScope {
            progressing = true
            val episodes = realm.query(Episode::class).query("media.playbackCompletionTime > 0 || media.lastPlayedTime > 0").find()
            for (e in episodes) {
                upsert(e) {
                    it.media?.playbackCompletionDate = null
                    it.media?.lastPlayedTime = 0
                }
            }
            withContext(Dispatchers.Main) {
                progressing = false
                Toast.makeText(requireContext(), "History cleared", Toast.LENGTH_LONG).show()
            }
            EventFlow.postEvent(FlowEvent.HistoryEvent())
        }
    }

    override fun onFilterChanged(filterValues: Set<String>) {
        if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name || spinnerTexts[curIndex] == QuickAccess.All.name) {
            val fSet = filterValues.toMutableSet()
            if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name) fSet.add(EpisodeFilter.States.downloaded.name)
            prefFilterEpisodes = StringUtils.join(fSet, ",")
            loadItems()
        }
    }

    override fun onSort(order: EpisodeSortOrder) {
        episodesSortOrder = order
        loadItems()
    }

    override fun filtersDisabled(): MutableSet<EpisodeFilter.EpisodesFilterGroup> {
        return if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name) mutableSetOf(EpisodeFilter.EpisodesFilterGroup.DOWNLOADED, EpisodeFilter.EpisodesFilterGroup.MEDIA)
        else mutableSetOf()
    }

    override fun onHistoryEvent(event: FlowEvent.HistoryEvent) {
        if (spinnerTexts[curIndex] == QuickAccess.History.name) {
            sortOrder = event.sortOrder
            if (event.startDate > 0) startDate = event.startDate
            endDate = event.endDate
            loadItems()
            updateToolbar()
        }
    }

    override fun onEpisodeEvent(event: FlowEvent.EpisodeEvent) {
        if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name) {
            var i = 0
            val size: Int = event.episodes.size
            while (i < size) {
                val item: Episode = event.episodes[i++]
                val pos = Episodes.indexOfItemWithId(episodes, item.id)
                if (pos >= 0) {
                    episodes.removeAt(pos)
                    vms.removeAt(pos)
                    val media = item.media
                    if (media != null && media.downloaded) {
                        episodes.add(pos, item)
                        vms.add(pos, EpisodeVM(item))
                    }
                }
            }
            updateToolbar()
        }
    }

    override fun onEpisodeMediaEvent(event: FlowEvent.EpisodeMediaEvent) {
        if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name) {
            var i = 0
            val size: Int = event.episodes.size
            while (i < size) {
                val item: Episode = event.episodes[i++]
                val pos = Episodes.indexOfItemWithId(episodes, item.id)
                if (pos >= 0) {
                    episodes.removeAt(pos)
                    vms.removeAt(pos)
                    val media = item.media
                    if (media != null && media.downloaded) {
                        episodes.add(pos, item)
                        vms.add(pos, EpisodeVM(item))
                    }
                }
            }
            updateToolbar()
        }
    }

    enum class QuickAccess {
        New, Planned, Repeats, Liked, Commented, Downloaded, History, All
    }

    companion object {
        val TAG = EpisodesFragment::class.simpleName ?: "Anonymous"
        const val PREF_NAME: String = "PrefEpisodesFragment"
    }
}
