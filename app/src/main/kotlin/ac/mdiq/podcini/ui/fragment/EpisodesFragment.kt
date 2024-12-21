package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadStatus
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
import ac.mdiq.podcini.ui.actions.EpisodeActionButton
import ac.mdiq.podcini.ui.actions.SwipeAction
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.actions.SwipeActions.Companion.SwipeActionsSettingDialog
import ac.mdiq.podcini.ui.actions.SwipeActions.NoActionSwipeAction
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.*
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import java.io.File
import java.util.*
import kotlin.math.min

class EpisodesFragment : Fragment() {
    val prefs: SharedPreferences by lazy { requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }

    private var displayUpArrow = false

    private var infoBarText = mutableStateOf("")
    private var leftActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
    private var rightActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
    private var showSwipeActionsDialog by mutableStateOf(false)

    lateinit var swipeActions: SwipeActions

    val episodes = mutableListOf<Episode>()
    private val vms = mutableStateListOf<EpisodeVM>()
    var showFilterDialog by mutableStateOf(false)
    var showSortDialog by mutableStateOf(false)
    var sortOrder by mutableStateOf(EpisodeSortOrder.DATE_NEW_OLD)
    private var showDatesFilter by mutableStateOf(false)

    var actionButtonToPass by mutableStateOf<((Episode) -> EpisodeActionButton)?>(null)

    private val spinnerTexts = QuickAccess.entries.map { it.name }
    private var curIndex by mutableIntStateOf(0)

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
        super.onCreateView(inflater, container, savedInstanceState)
        Logd(TAG, "fragment onCreateView")

        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

        swipeActions = SwipeActions(this, TAG)
        leftActionState.value = swipeActions.actions.left[0]
        rightActionState.value = swipeActions.actions.right[0]
        lifecycle.addObserver(swipeActions)

        val composeView = ComposeView(requireContext()).apply {
            setContent {
                CustomTheme(requireContext()) {
                    OpenDialog()
                    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
                        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                            InforBar(infoBarText, leftAction = leftActionState, rightAction = rightActionState, actionConfig = { showSwipeActionsDialog = true  })
                            EpisodeLazyColumn(
                                activity as MainActivity, vms = vms,
                                leftSwipeCB = {
                                    if (leftActionState.value is NoActionSwipeAction) showSwipeActionsDialog = true
                                    else leftActionState.value.performAction(it)
                                },
                                rightSwipeCB = {
                                    if (rightActionState.value is NoActionSwipeAction) showSwipeActionsDialog = true
                                    else rightActionState.value.performAction(it)
                                },
                                actionButton_ = actionButtonToPass
                            )
                        }
                    }
                }
            }
        }
        refreshSwipeTelltale()
        curIndex = prefs.getInt("curIndex", 0)
        sortOrder = episodesSortOrder
        updateToolbar()
        return composeView
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
        loadItems()
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
//        _binding = null
        episodes.clear()
        stopMonitor(vms)
        vms.clear()
        super.onDestroyView()
    }

//    private fun onKeyUp(event: KeyEvent) {
//        if (!isAdded || !isVisible || !isMenuVisible) return
//        when (event.keyCode) {
////            KeyEvent.KEYCODE_T -> recyclerView.smoothScrollToPosition(0)
////            KeyEvent.KEYCODE_B -> recyclerView.smoothScrollToPosition(adapter.itemCount)
//            else -> {}
//        }
//    }

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
        for (url in event.urls) {
//            if (!event.isCompleted(url)) continue
            val pos: Int = Episodes.indexOfItemWithDownloadUrl(episodes, url)
            if (pos >= 0) vms[pos].downloadState = event.map[url]?.state ?: DownloadStatus.State.UNKNOWN.ordinal
        }
    }

    private var eventSink: Job? = null
    private var eventStickySink: Job? = null
    private var eventKeySink: Job?     = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
        eventStickySink?.cancel()
        eventStickySink = null
        eventKeySink?.cancel()
        eventKeySink = null
    }
    private fun procFlowEvents() {
        if (eventSink == null) eventSink = lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
//                    is FlowEvent.SwipeActionsChangedEvent -> refreshSwipeTelltale()
                    is FlowEvent.EpisodeEvent -> onEpisodeEvent(event)
                    is FlowEvent.EpisodeMediaEvent -> onEpisodeMediaEvent(event)
                    is FlowEvent.HistoryEvent -> onHistoryEvent(event)
                    is FlowEvent.FeedListEvent, is FlowEvent.EpisodePlayedEvent, is FlowEvent.RatingEvent -> loadItems()
                    else -> {}
                }
            }
        }
        if (eventStickySink == null) eventStickySink = lifecycleScope.launch {
            EventFlow.stickyEvents.collectLatest { event ->
                Logd(TAG, "Received sticky event: ${event.TAG}")
                when (event) {
                    is FlowEvent.EpisodeDownloadEvent -> onEpisodeDownloadEvent(event)
//                    is FlowEvent.FeedUpdatingEvent -> onFeedUpdateRunningEvent(event)
                    else -> {}
                }
            }
        }
        if (eventKeySink == null) eventKeySink = lifecycleScope.launch {
            EventFlow.keyEvents.collectLatest { event ->
                Logd(TAG, "Received key event: $event, ignored")
//                onKeyUp(event)
            }
        }
    }

    private fun refreshSwipeTelltale() {
        leftActionState.value = swipeActions.actions.left[0]
        rightActionState.value = swipeActions.actions.right[0]
    }

    private var loadJob: Job? = null
    fun loadItems() {
        Logd(TAG, "loadItems() called")
        if (loadJob != null) {
            loadJob?.cancel()
            stopMonitor(vms)
            vms.clear()
        }
        loadJob = lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    episodes.clear()
                    episodes.addAll(loadData())
                }
                withContext(Dispatchers.Main) {
                    stopMonitor(vms)
                    vms.clear()
                    for (e in episodes) { vms.add(EpisodeVM(e, TAG)) }
                    updateToolbar()
                }
            } catch (e: Throwable) { Log.e(TAG, Log.getStackTraceString(e)) }
        }.apply { invokeOnCompletion { loadJob = null } }
    }

    @Composable
    fun OpenDialog() {
        if (showSwipeActionsDialog) SwipeActionsSettingDialog(swipeActions, onDismissRequest = { showSwipeActionsDialog = false }) { actions ->
            swipeActions.actions = actions
            refreshSwipeTelltale()
        }
        if (showFilterDialog) EpisodesFilterDialog(filter = getFilter(), filtersDisabled = filtersDisabled(),
            onDismissRequest = { showFilterDialog = false }) { onFilterChanged(it) }
        if (showSortDialog) EpisodeSortDialog(initOrder = sortOrder, onDismissRequest = { showSortDialog = false }) { order, _ -> onSort(order) }
        swipeActions.ActionOptionsDialog()
        ComfirmDialog(titleRes = R.string.clear_history_label, message = stringResource(R.string.clear_playback_history_msg), showDialog = showClearHistoryDialog) { clearHistory() }
        if (showDatesFilter) DatesFilterDialogCompose(inclPlayed = false, oldestDate = 0L, onDismissRequest = { showDatesFilter = false} ) {timeFilterFrom, timeFilterTo, _ ->
            EventFlow.postEvent(FlowEvent.HistoryEvent(sortOrder, timeFilterFrom, timeFilterTo))
        }
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

    fun loadData(): List<Episode> {
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

    fun getFilter(): EpisodeFilter {
        return EpisodeFilter(prefFilterEpisodes)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        TopAppBar(title = {
            SpinnerExternalSet(items = spinnerTexts, selectedIndex = curIndex) { index: Int ->
                Logd(QueuesFragment.Companion.TAG, "Item selected: $index")
                curIndex = index
                prefs.edit().putInt("curIndex", index).apply()
                actionButtonToPass = if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name)  {it -> DeleteActionButton(it) } else null
                loadItems()
            }
        },
            navigationIcon = if (displayUpArrow) {
                { IconButton(onClick = { parentFragmentManager.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            } else {
                { IconButton(onClick = { (activity as? MainActivity)?.openDrawer() }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_feed), contentDescription = "Open Drawer") } }
            },
            actions = {
                IconButton(onClick = { (activity as MainActivity).loadChildFragment(SearchFragment.newInstance())
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                IconButton(onClick = { showSortDialog = true
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.arrows_sort), contentDescription = "sort") }
                if (vms.isNotEmpty() && spinnerTexts[curIndex] == QuickAccess.All.name) IconButton(onClick = { showFilterDialog = true
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter), contentDescription = "filter") }
                if (vms.isNotEmpty() && spinnerTexts[curIndex] == QuickAccess.History.name) IconButton(onClick = { showDatesFilter = true
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter), contentDescription = "filter") }
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (vms.isNotEmpty() && spinnerTexts[curIndex] == QuickAccess.History.name)
                        DropdownMenuItem(text = { Text(stringResource(R.string.clear_history_label)) }, onClick = {
                            showClearHistoryDialog.value = true
                            expanded = false
                        })
                    if (vms.isNotEmpty() && spinnerTexts[curIndex] == QuickAccess.Downloaded.name)
                        DropdownMenuItem(text = { Text(stringResource(R.string.reconcile_label)) }, onClick = {
                            reconcile()
                            expanded = false
                        })
                    if (vms.isNotEmpty() && spinnerTexts[curIndex] == QuickAccess.New.name)
                        DropdownMenuItem(text = { Text(stringResource(R.string.clear_new_label)) }, onClick = {
                            clearNew()
                            expanded = false
                        })
                }
            }
        )
    }

    var progressing by mutableStateOf(false)
    fun updateToolbar() {
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

    fun onFilterChanged(filterValues: Set<String>) {
        if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name || spinnerTexts[curIndex] == QuickAccess.All.name) {
            val fSet = filterValues.toMutableSet()
            if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name) fSet.add(EpisodeFilter.States.downloaded.name)
            prefFilterEpisodes = StringUtils.join(fSet, ",")
            loadItems()
        }
    }

    fun onSort(order: EpisodeSortOrder) {
        episodesSortOrder = order
        loadItems()
    }

    fun filtersDisabled(): MutableSet<EpisodeFilter.EpisodesFilterGroup> {
        return if (spinnerTexts[curIndex] == QuickAccess.Downloaded.name) mutableSetOf(EpisodeFilter.EpisodesFilterGroup.DOWNLOADED, EpisodeFilter.EpisodesFilterGroup.MEDIA)
        else mutableSetOf()
    }

    fun onHistoryEvent(event: FlowEvent.HistoryEvent) {
        if (spinnerTexts[curIndex] == QuickAccess.History.name) {
            sortOrder = event.sortOrder
            if (event.startDate > 0) startDate = event.startDate
            endDate = event.endDate
            loadItems()
            updateToolbar()
        }
    }

    fun onEpisodeEvent(event: FlowEvent.EpisodeEvent) {
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
                        vms.add(pos, EpisodeVM(item, TAG))
                    }
                }
            }
            updateToolbar()
        }
    }

    fun onEpisodeMediaEvent(event: FlowEvent.EpisodeMediaEvent) {
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
                        vms.add(pos, EpisodeVM(item, TAG))
                    }
                }
            }
            updateToolbar()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    enum class QuickAccess {
        New, Planned, Repeats, Liked, Commented, Downloaded, History, All
    }

    companion object {
        val TAG = EpisodesFragment::class.simpleName ?: "Anonymous"
        const val PREF_NAME: String = "PrefEpisodesFragment"
        private const val KEY_UP_ARROW = "up_arrow"
    }
}
