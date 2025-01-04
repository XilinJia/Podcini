package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.storage.database.Episodes
import ac.mdiq.podcini.storage.database.Episodes.getEpisodes
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.storage.model.EpisodeSortOrder.Companion.getPermutor
import ac.mdiq.podcini.storage.utils.StorageUtils.customMediaUriString
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
import android.net.Uri
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
import androidx.documentfile.provider.DocumentFile
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

    class EpisodesVM {
        internal var displayUpArrow = false

        internal var infoBarText = mutableStateOf("")
        internal var leftActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
        internal var rightActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
        internal var showSwipeActionsDialog by mutableStateOf(false)

        lateinit var swipeActions: SwipeActions

        val episodes = mutableListOf<Episode>()
        internal val vms = mutableStateListOf<EpisodeVM>()
        var showFilterDialog by mutableStateOf(false)
        var showSortDialog by mutableStateOf(false)
        var sortOrder by mutableStateOf(EpisodeSortOrder.DATE_NEW_OLD)
        internal var showDatesFilter by mutableStateOf(false)

        var actionButtonToPass by mutableStateOf<((Episode) -> EpisodeActionButton)?>(null)

        internal val spinnerTexts = QuickAccess.entries.map { it.name }
        internal var curIndex by mutableIntStateOf(0)

        internal var startDate: Long = 0L
        internal var endDate: Long = Date().time

        internal val showClearHistoryDialog = mutableStateOf(false)

        internal var episodesSortOrder: EpisodeSortOrder
            get() = EpisodeSortOrder.fromCodeString(getPref(AppPrefs.prefEpisodesSort, "" + EpisodeSortOrder.DATE_NEW_OLD.code))
            set(s) {
                putPref(AppPrefs.prefEpisodesSort, "" + s.code)
            }
        internal var prefFilterEpisodes: String
            get() = getPref(AppPrefs.prefEpisodesFilter, "")
            set(filter) {
                putPref(AppPrefs.prefEpisodesFilter, filter)
            }
        internal var prefFilterDownloads: String
            get() = getPref(AppPrefs.prefDownloadsFilter, EpisodeFilter.States.downloaded.name)
            set(filter) {
                putPref(AppPrefs.prefDownloadsFilter, filter)
            }
    }

    private val vm = EpisodesVM()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        Logd(TAG, "fragment onCreateView")

        vm.displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) vm.displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

        vm.swipeActions = SwipeActions(this, TAG)
        vm.leftActionState.value = vm.swipeActions.actions.left[0]
        vm.rightActionState.value = vm.swipeActions.actions.right[0]
        lifecycle.addObserver(vm.swipeActions)

        val composeView = ComposeView(requireContext()).apply {
            setContent {
                CustomTheme(requireContext()) {
                    OpenDialog()
                    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
                        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                            InforBar(vm.infoBarText, leftAction = vm.leftActionState, rightAction = vm.rightActionState, actionConfig = { vm.showSwipeActionsDialog = true  })
                            EpisodeLazyColumn(activity as MainActivity, vms = vm.vms,
                                buildMoreItems = { buildMoreItems() },
                                leftSwipeCB = {
                                    if (vm.leftActionState.value is NoActionSwipeAction) vm.showSwipeActionsDialog = true
                                    else vm.leftActionState.value.performAction(it)
                                },
                                rightSwipeCB = {
                                    if (vm.rightActionState.value is NoActionSwipeAction) vm.showSwipeActionsDialog = true
                                    else vm.rightActionState.value.performAction(it)
                                },
                                actionButton_ = vm.actionButtonToPass
                            )
                        }
                    }
                }
            }
        }
        refreshSwipeTelltale()
        vm.curIndex = prefs.getInt("curIndex", 0)
        vm.sortOrder = vm.episodesSortOrder
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
        vm.episodes.clear()
        stopMonitor(vm.vms)
        vm.vms.clear()
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
            val pos: Int = Episodes.indexOfItemWithDownloadUrl(vm.episodes, url)
            if (pos >= 0) vm.vms[pos].downloadState = event.map[url]?.state ?: DownloadStatus.State.UNKNOWN.ordinal
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
        vm.leftActionState.value = vm.swipeActions.actions.left[0]
        vm.rightActionState.value = vm.swipeActions.actions.right[0]
    }

    private var loadJob: Job? = null
    fun loadItems() {
        Logd(TAG, "loadItems() called")
        if (loadJob != null) {
            loadJob?.cancel()
            stopMonitor(vm.vms)
            vm.vms.clear()
        }
        loadJob = lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    vm.episodes.clear()
                    vm.episodes.addAll(loadData())
                }
                withContext(Dispatchers.Main) {
                    stopMonitor(vm.vms)
                    vm.vms.clear()
                    buildMoreItems()
//                    for (e in vm.episodes) { vm.vms.add(EpisodeVM(e, TAG)) }
                    updateToolbar()
                }
            } catch (e: Throwable) { Log.e(TAG, Log.getStackTraceString(e)) }
        }.apply { invokeOnCompletion { loadJob = null } }
    }

    fun buildMoreItems() {
        val nextItems = (vm.vms.size until min(vm.vms.size + VMS_CHUNK_SIZE, vm.episodes.size)).map { EpisodeVM(vm.episodes[it], FeedEpisodesFragment.Companion.TAG) }
        if (nextItems.isNotEmpty()) vm.vms.addAll(nextItems)
    }

    @Composable
    fun OpenDialog() {
        if (vm.showSwipeActionsDialog) SwipeActionsSettingDialog(vm.swipeActions, onDismissRequest = { vm.showSwipeActionsDialog = false }) { actions ->
            vm.swipeActions.actions = actions
            refreshSwipeTelltale()
        }
        if (vm.showFilterDialog) EpisodesFilterDialog(filter = getFilter(), filtersDisabled = filtersDisabled(),
            onDismissRequest = { vm.showFilterDialog = false }) { onFilterChanged(it) }
        if (vm.showSortDialog) EpisodeSortDialog(initOrder = vm.sortOrder, onDismissRequest = { vm.showSortDialog = false }) { order, _ -> onSort(order) }
        vm.swipeActions.ActionOptionsDialog()
        ComfirmDialog(titleRes = R.string.clear_history_label, message = stringResource(R.string.clear_playback_history_msg), showDialog = vm.showClearHistoryDialog) { clearHistory() }
        if (vm.showDatesFilter) DatesFilterDialogCompose(inclPlayed = false, oldestDate = 0L, onDismissRequest = { vm.showDatesFilter = false} ) {timeFilterFrom, timeFilterTo, _ ->
            EventFlow.postEvent(FlowEvent.HistoryEvent(vm.sortOrder, timeFilterFrom, timeFilterTo))
        }
    }
    /**
     * Loads the playback history from the database. A FeedItem is in the playback history if playback of the correpsonding episode
     * has been played ot completed at least once.
     * @param limit The maximum number of vm.episodes to return.
     * @return The playback history. The FeedItems are sorted by their media's playbackCompletionDate in descending order.
     */
    fun getHistory(offset: Int, limit: Int, start: Long = 0L, end: Long = Date().time,
                   sortOrder: EpisodeSortOrder = EpisodeSortOrder.PLAYED_DATE_NEW_OLD): List<Episode> {
        Logd(TAG, "getHistory() called")
        val medias = realm.query(Episode::class).query("(playbackCompletionTime > 0) OR (lastPlayedTime > \$0 AND lastPlayedTime <= \$1)", start, end).find()
        var episodes: MutableList<Episode> = mutableListOf()
        for (m in medias) episodes.add(m)
        getPermutor(sortOrder).reorder(episodes)
        if (offset > 0 && episodes.size > offset) episodes = episodes.subList(offset, min(episodes.size, offset+limit))
        return episodes
    }

    fun loadData(): List<Episode> {
        return when (vm.spinnerTexts[vm.curIndex]) {
            QuickAccess.New.name -> getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.new.name), vm.episodesSortOrder, false)
            QuickAccess.Planned.name -> getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.soon.name, EpisodeFilter.States.later.name), vm.episodesSortOrder, false)
            QuickAccess.Repeats.name -> getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.again.name, EpisodeFilter.States.forever.name), vm.episodesSortOrder, false)
            QuickAccess.Liked.name -> getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.good.name, EpisodeFilter.States.superb.name), vm.episodesSortOrder, false)
            QuickAccess.Commented.name -> getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.has_comments.name), vm.episodesSortOrder, false)
            QuickAccess.History.name -> getHistory(0, Int.MAX_VALUE, sortOrder = vm.episodesSortOrder).toMutableList()
            QuickAccess.Downloaded.name -> getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(vm.prefFilterDownloads), vm.episodesSortOrder, false)
            QuickAccess.All.name -> getEpisodes(0, Int.MAX_VALUE, getFilter(), vm.episodesSortOrder, false)
            else -> getEpisodes(0, Int.MAX_VALUE, getFilter(), vm.episodesSortOrder, false)
        }
    }

    fun getFilter(): EpisodeFilter {
        return EpisodeFilter(vm.prefFilterEpisodes)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        TopAppBar(title = {
            SpinnerExternalSet(items = vm.spinnerTexts, selectedIndex = vm.curIndex) { index: Int ->
                Logd(QueuesFragment.Companion.TAG, "Item selected: $index")
                vm.curIndex = index
                prefs.edit().putInt("curIndex", index).apply()
                vm.actionButtonToPass = if (vm.spinnerTexts[vm.curIndex] == QuickAccess.Downloaded.name)  {it -> DeleteActionButton(it) } else null
                loadItems()
            }
        },
            navigationIcon = if (vm.displayUpArrow) {
                { IconButton(onClick = { parentFragmentManager.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            } else {
                { IconButton(onClick = { (activity as? MainActivity)?.openDrawer() }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_feed), contentDescription = "Open Drawer") } }
            },
            actions = {
                IconButton(onClick = { (activity as MainActivity).loadChildFragment(SearchFragment.newInstance())
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), contentDescription = "search") }
                IconButton(onClick = { vm.showSortDialog = true
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.arrows_sort), contentDescription = "sort") }
                if (vm.vms.isNotEmpty() && vm.spinnerTexts[vm.curIndex] == QuickAccess.All.name) IconButton(onClick = { vm.showFilterDialog = true
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter), contentDescription = "filter") }
                if (vm.vms.isNotEmpty() && vm.spinnerTexts[vm.curIndex] == QuickAccess.History.name) IconButton(onClick = { vm.showDatesFilter = true
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter), contentDescription = "filter") }
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (vm.vms.isNotEmpty() && vm.spinnerTexts[vm.curIndex] == QuickAccess.History.name)
                        DropdownMenuItem(text = { Text(stringResource(R.string.clear_history_label)) }, onClick = {
                            vm.showClearHistoryDialog.value = true
                            expanded = false
                        })
                    if (vm.vms.isNotEmpty() && vm.spinnerTexts[vm.curIndex] == QuickAccess.Downloaded.name)
                        DropdownMenuItem(text = { Text(stringResource(R.string.reconcile_label)) }, onClick = {
                            reconcile()
                            expanded = false
                        })
                    if (vm.vms.isNotEmpty() && vm.spinnerTexts[vm.curIndex] == QuickAccess.New.name)
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
        var info = "${vm.episodes.size} vm.episodes"
        if (vm.spinnerTexts[vm.curIndex] == QuickAccess.All.name && getFilter().properties.isNotEmpty()) info += " - ${getString(R.string.filtered_label)}"
        else if (vm.spinnerTexts[vm.curIndex] == QuickAccess.Downloaded.name && vm.episodes.isNotEmpty()) {
            var sizeMB: Long = 0
            for (item in vm.episodes) sizeMB += item.size
            info += " • " + (sizeMB / 1000000) + " MB"
        }
        if (progressing) info += " - ${getString(R.string.progressing_label)}"
        vm.infoBarText.value = info
    }

    private fun clearNew() {
        runOnIOScope {
            progressing = true
            for (e in vm.episodes) if (e.isNew) upsert(e) { it.setPlayed(false) }
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
        fun traverse(srcFile: File) {
            val filename = srcFile.name
            if (srcFile.isDirectory) {
                Logd(TAG, "traverse folder title: $filename")
                val dirFiles = srcFile.listFiles()
                dirFiles?.forEach { file -> traverse(file) }
            } else {
                Logd(TAG, "traverse: $srcFile")
                val episode = nameEpisodeMap.remove(filename)
                if (episode == null) {
                    Logd(TAG, "traverse: error: episode not exist in map: $filename")
                    filesRemoved.add(filename)
                    srcFile.delete()
                    return
                }
                Logd(TAG, "traverse found episode: ${episode.title}")
            }
        }
        fun traverse(srcFile: DocumentFile) {
            val filename = srcFile.name
            if (srcFile.isDirectory) {
                Logd(TAG, "traverse folder title: $filename")
                val dirFiles = srcFile.listFiles()
                dirFiles.forEach { file -> traverse(file) }
            } else {
                Logd(TAG, "traverse: $srcFile")
                val episode = nameEpisodeMap.remove(filename)
                if (episode == null) {
                    Logd(TAG, "traverse: error: episode not exist in map: $filename")
                    if (filename != null) filesRemoved.add(filename)
                    srcFile.delete()
                    return
                }
                Logd(TAG, "traverse found episode: ${episode.title}")
            }
        }
        runOnIOScope {
            progressing = true
//            val items = realm.query(Episode::class).query("media.episode == nil").find()
//            Logd(TAG, "number of episode with null backlink: ${items.size}")
            nameEpisodeMap.clear()
            for (e in vm.episodes) {
                var fileUrl = e.fileUrl ?: continue
                fileUrl = fileUrl.substring(fileUrl.lastIndexOf('/') + 1)
                Logd(TAG, "reconcile: fileUrl: $fileUrl")
                nameEpisodeMap[fileUrl] = e
            }
            if (customMediaUriString.isBlank()) {
                val mediaDir = requireContext().getExternalFilesDir("media") ?: return@runOnIOScope
                mediaDir.listFiles()?.forEach { file -> traverse(file) }
            } else {
                val customUri = Uri.parse(customMediaUriString)
                val baseDir = DocumentFile.fromTreeUri(getAppContext(), customUri)
                baseDir?.listFiles()?.forEach { file -> traverse(file) }
            }
            Logd(TAG, "reconcile: end, episodes missing file: ${nameEpisodeMap.size}")
            if (nameEpisodeMap.isNotEmpty()) for (e in nameEpisodeMap.values) upsertBlk(e) { it.setfileUrlOrNull(null) }
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
            while (realm.query(Episode::class).query("playbackCompletionTime > 0 || lastPlayedTime > 0").count().find() > 0) {
                realm.write {
                    val episodes = query(Episode::class).query("playbackCompletionTime > 0 || lastPlayedTime > 0").find()
                    for (e in episodes) {
                        e.playbackCompletionDate = null
                        e.lastPlayedTime = 0
                    }
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
        if (vm.spinnerTexts[vm.curIndex] == QuickAccess.Downloaded.name || vm.spinnerTexts[vm.curIndex] == QuickAccess.All.name) {
            val fSet = filterValues.toMutableSet()
            if (vm.spinnerTexts[vm.curIndex] == QuickAccess.Downloaded.name) fSet.add(EpisodeFilter.States.downloaded.name)
            vm.prefFilterEpisodes = StringUtils.join(fSet, ",")
            loadItems()
        }
    }

    fun onSort(order: EpisodeSortOrder) {
        vm.episodesSortOrder = order
        loadItems()
    }

    fun filtersDisabled(): MutableSet<EpisodeFilter.EpisodesFilterGroup> {
        return if (vm.spinnerTexts[vm.curIndex] == QuickAccess.Downloaded.name)
//            mutableSetOf(EpisodeFilter.EpisodesFilterGroup.DOWNLOADED, EpisodeFilter.EpisodesFilterGroup.MEDIA)
            mutableSetOf(EpisodeFilter.EpisodesFilterGroup.DOWNLOADED)
        else mutableSetOf()
    }

    fun onHistoryEvent(event: FlowEvent.HistoryEvent) {
        if (vm.spinnerTexts[vm.curIndex] == QuickAccess.History.name) {
            vm.sortOrder = event.sortOrder
            if (event.startDate > 0) vm.startDate = event.startDate
            vm.endDate = event.endDate
            loadItems()
            updateToolbar()
        }
    }

    fun onEpisodeEvent(event: FlowEvent.EpisodeEvent) {
        if (vm.spinnerTexts[vm.curIndex] == QuickAccess.Downloaded.name) {
            var i = 0
            val size: Int = event.episodes.size
            while (i < size) {
                val item: Episode = event.episodes[i++]
                val pos = Episodes.indexOfItemWithId(vm.episodes, item.id)
                if (pos >= 0) {
                    vm.episodes.removeAt(pos)
                    vm.vms.removeAt(pos)
                    if (item.downloaded) {
                        vm.episodes.add(pos, item)
                        vm.vms.add(pos, EpisodeVM(item, TAG))
                    }
                }
            }
            updateToolbar()
        }
    }

    fun onEpisodeMediaEvent(event: FlowEvent.EpisodeMediaEvent) {
        if (vm.spinnerTexts[vm.curIndex] == QuickAccess.Downloaded.name) {
            var i = 0
            val size: Int = event.episodes.size
            while (i < size) {
                val item: Episode = event.episodes[i++]
                val pos = Episodes.indexOfItemWithId(vm.episodes, item.id)
                if (pos >= 0) {
                    vm.episodes.removeAt(pos)
                    vm.vms.removeAt(pos)
                    if (item.downloaded) {
                        vm.episodes.add(pos, item)
                        vm.vms.add(pos, EpisodeVM(item, TAG))
                    }
                }
            }
            updateToolbar()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, vm.displayUpArrow)
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
