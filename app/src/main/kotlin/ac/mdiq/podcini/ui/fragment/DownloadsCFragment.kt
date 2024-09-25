package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.DownloadsFragmentBinding
import ac.mdiq.podcini.databinding.MultiSelectSpeedDialBinding
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.appPrefs
import ac.mdiq.podcini.storage.database.Episodes.getEpisodes
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.storage.utils.EpisodeUtil
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeAction
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.compose.InforBar
import ac.mdiq.podcini.ui.dialog.EpisodeFilterDialog
import ac.mdiq.podcini.ui.dialog.EpisodeSortDialog
import ac.mdiq.podcini.ui.dialog.SwitchQueueDialog
import ac.mdiq.podcini.ui.utils.EmptyViewHandler
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.compose.runtime.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.google.android.material.appbar.MaterialToolbar
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import java.io.File
import java.util.*

/**
 * Displays all completed downloads and provides a button to delete them.
 */
@UnstableApi class DownloadsCFragment : Fragment(), Toolbar.OnMenuItemClickListener {

    private var _binding: DownloadsFragmentBinding? = null
    private val binding get() = _binding!!

    private var runningDownloads: Set<String> = HashSet()
    private var episodes = mutableStateListOf<Episode>()

    private var infoBarText = mutableStateOf("")
    var leftActionState = mutableStateOf<SwipeAction?>(null)
    var rightActionState = mutableStateOf<SwipeAction?>(null)

    private lateinit var toolbar: MaterialToolbar
//    private lateinit var recyclerView: EpisodesRecyclerView
    private lateinit var swipeActions: SwipeActions
    private lateinit var speedDialView: SpeedDialView
    private lateinit var emptyView: EmptyViewHandler
    
    private var displayUpArrow = false

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DownloadsFragmentBinding.inflate(inflater)

        Logd(TAG, "fragment onCreateView")
        toolbar = binding.toolbar
//        toolbar.setTitle(R.string.downloadsC_label)
        toolbar.setTitle("Preview only")
        toolbar.inflateMenu(R.menu.downloads_completed)
        toolbar.setOnMenuItemClickListener(this)
        toolbar.setOnLongClickListener {
//            recyclerView.scrollToPosition(5)
//            recyclerView.post { recyclerView.smoothScrollToPosition(0) }
            false
        }
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

        (activity as MainActivity).setupToolbarToggle(toolbar, displayUpArrow)

        swipeActions = SwipeActions(this, TAG)
        swipeActions.setFilter(EpisodeFilter(EpisodeFilter.States.downloaded.name))
        binding.infobar.setContent {
            CustomTheme(requireContext()) {
                InforBar(infoBarText, leftAction = leftActionState, rightAction = rightActionState, actionConfig = {swipeActions.showDialog()})
            }
        }

        binding.lazyColumn.setContent {
            CustomTheme(requireContext()) {
                EpisodeLazyColumn(activity as MainActivity, episodes = episodes,
                    leftActionCB = { leftActionState.value?.performAction(it, this, swipeActions.filter ?: EpisodeFilter())},
                    rightActionCB = { rightActionState.value?.performAction(it, this, swipeActions.filter ?: EpisodeFilter())})
            }
        }
//        recyclerView.setRecycledViewPool((activity as MainActivity).recycledViewPool)
//        adapter.setOnSelectModeListener(this)
//        recyclerView.addOnScrollListener(LiftOnScrollListener(binding.appbar))

//        swipeActions = SwipeActions(this, TAG).attachTo(recyclerView)
//        lifecycle.addObserver(swipeActions)
        refreshSwipeTelltale()
//        binding.leftActionIcon.setOnClickListener { swipeActions.showDialog() }
//        binding.rightActionIcon.setOnClickListener { swipeActions.showDialog() }

//        val animator: RecyclerView.ItemAnimator? = recyclerView.itemAnimator
//        if (animator is SimpleItemAnimator) animator.supportsChangeAnimations = false

        val multiSelectDial = MultiSelectSpeedDialBinding.bind(binding.root)
        speedDialView = multiSelectDial.fabSD
        speedDialView.overlayLayout = multiSelectDial.fabSDOverlay
        speedDialView.inflate(R.menu.episodes_apply_action_speeddial)
        speedDialView.removeActionItemById(R.id.download_batch)
        speedDialView.removeActionItemById(R.id.remove_from_queue_batch)
        speedDialView.setOnChangeListener(object : SpeedDialView.OnChangeListener {
            override fun onMainActionSelected(): Boolean {
                return false
            }
            override fun onToggleChanged(open: Boolean) {
//                if (open && adapter.selectedCount == 0) {
//                    (activity as MainActivity).showSnackbarAbovePlayer(R.string.no_items_selected, Snackbar.LENGTH_SHORT)
//                    speedDialView.close()
//                }
            }
        })
        speedDialView.setOnActionSelectedListener { actionItem: SpeedDialActionItem ->
//            adapter.selectedItems.let {
//                EpisodeMultiSelectHandler((activity as MainActivity), actionItem.id).handleAction(it)
//            }
//            adapter.endSelectMode()
            true
        }
        if (arguments != null && requireArguments().getBoolean(ARG_SHOW_LOGS, false)) DownloadLogFragment().show(childFragmentManager, null)

        addEmptyView()
        return binding.root
    }

    fun leftAction(episode: Episode) {
        swipeActions.actions?.left?.performAction(episode, this, EpisodeFilter())
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
        loadItems()
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
//        val recyclerView =  binding.recyclerView
//        val childCount = recyclerView.childCount
//        for (i in 0 until childCount) {
//            val child = recyclerView.getChildAt(i)
//            val viewHolder = recyclerView.getChildViewHolder(child) as? EpisodeViewHolder
//            viewHolder?.stopDBMonitor()
//        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        _binding = null
//        adapter.endSelectMode()
//        adapter.clearData()
        toolbar.setOnMenuItemClickListener(null)
        toolbar.setOnLongClickListener(null)
        episodes.clear()

        super.onDestroyView()
    }

    @UnstableApi override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.filter_items -> DownloadsFilterDialog.newInstance(getFilter()).show(childFragmentManager, null)
            R.id.action_download_logs -> DownloadLogFragment().show(childFragmentManager, null)
            R.id.action_search -> (activity as MainActivity).loadChildFragment(SearchFragment.newInstance())
            R.id.downloads_sort -> DownloadsSortDialog().show(childFragmentManager, "SortDialog")
            R.id.switch_queue -> SwitchQueueDialog(activity as MainActivity).show()
            R.id.reconcile -> reconcile()
            else -> return false
        }
        return true
    }

    private fun getFilter(): EpisodeFilter {
        return EpisodeFilter(prefFilterDownloads)
    }

    private val nameEpisodeMap: MutableMap<String, Episode> = mutableMapOf()
    private val filesRemoved: MutableList<String> = mutableListOf()
    private fun reconcile() {
        runOnIOScope {
            val items = realm.query(Episode::class).query("media.episode == nil").find()
            Logd(TAG, "number of episode with null backlink: ${items.size}")
            for (item in items) {
                upsert(item) { it.media!!.episode = it }
            }
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
            if (nameEpisodeMap.isNotEmpty()) {
                for (e in nameEpisodeMap.values) {
                    upsertBlk(e) { it.media?.setfileUrlOrNull(null) }
                }
            }
            loadItems()
            Logd(TAG, "Episodes reconsiled: ${nameEpisodeMap.size}\nFiles removed: ${filesRemoved.size}")
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Episodes reconsiled: ${nameEpisodeMap.size}\nFiles removed: ${filesRemoved.size}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun traverse(srcFile: File, srcRootDir: File) {
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

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
        val newRunningDownloads: MutableSet<String> = HashSet()
        for (url in event.urls) {
            if (DownloadServiceInterface.get()?.isDownloadingEpisode(url) == true) newRunningDownloads.add(url)
        }
        if (newRunningDownloads != runningDownloads) {
            runningDownloads = newRunningDownloads
            loadItems()
            return  // Refreshed anyway
        }
//        for (downloadUrl in event.urls) {
//            val pos = EpisodeUtil.indexOfItemWithDownloadUrl(episodes.toList(), downloadUrl)
//            if (pos >= 0) adapter.notifyItemChangedCompat(pos)
//        }
    }

    private var eventSink: Job?     = null
    private var eventStickySink: Job? = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
        eventStickySink?.cancel()
        eventStickySink = null
    }
    private fun procFlowEvents() {
        if (eventSink == null) eventSink = lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.EpisodeEvent -> onEpisodeEvent(event)
                    is FlowEvent.DownloadsFilterEvent -> onFilterChanged(event)
                    is FlowEvent.EpisodeMediaEvent -> onEpisodeMediaEvent(event)
                    is FlowEvent.PlayerSettingsEvent -> loadItems()
                    is FlowEvent.DownloadLogEvent -> loadItems()
                    is FlowEvent.QueueEvent -> loadItems()
                    is FlowEvent.SwipeActionsChangedEvent -> refreshSwipeTelltale()
                    is FlowEvent.EpisodeDownloadEvent -> onEpisodeDownloadEvent(event)
                    else -> {}
                }
            }
        }
//        if (eventStickySink == null) eventStickySink = lifecycleScope.launch {
//            EventFlow.stickyEvents.collectLatest { event ->
//                Logd(TAG, "Received sticky event: ${event.TAG}")
//                when (event) {
//                    is FlowEvent.EpisodeDownloadEvent -> onEpisodeDownloadEvent(event)
//                    else -> {}
//                }
//            }
//        }
    }

    private fun onFilterChanged(event: FlowEvent.DownloadsFilterEvent) {
        val fSet = event.filterValues?.toMutableSet() ?: mutableSetOf()
        fSet.add(EpisodeFilter.States.downloaded.name)
        prefFilterDownloads = StringUtils.join(fSet, ",")
        Logd(TAG, "onFilterChanged: $prefFilterDownloads")
        loadItems()
    }

    private fun addEmptyView() {
        emptyView = EmptyViewHandler(requireContext())
        emptyView.setIcon(R.drawable.ic_download)
        emptyView.setTitle(R.string.no_comp_downloads_head_label)
        emptyView.setMessage(R.string.no_comp_downloads_label)
//        emptyView.attachToRecyclerView(recyclerView)
    }

    private fun onEpisodeEvent(event: FlowEvent.EpisodeEvent) {
//        Logd(TAG, "onEpisodeEvent() called with ${event.TAG}")
        var i = 0
        val size: Int = event.episodes.size
        while (i < size) {
            val item: Episode = event.episodes[i++]
            val pos = EpisodeUtil.indexOfItemWithId(episodes, item.id)
            if (pos >= 0) {
                episodes.removeAt(pos)
                val media = item.media
                if (media != null && media.downloaded) episodes.add(pos, item)
            }
        }
//        have to do this as adapter.notifyItemRemoved(pos) when pos == 0 causes crash
//        if (size > 0) adapter.updateItems(episodes)
        refreshInfoBar()
    }

    private fun onEpisodeMediaEvent(event: FlowEvent.EpisodeMediaEvent) {
//        Logd(TAG, "onEpisodeEvent() called with ${event.TAG}")
        var i = 0
        val size: Int = event.episodes.size
        while (i < size) {
            val item: Episode = event.episodes[i++]
            val pos = EpisodeUtil.indexOfItemWithId(episodes, item.id)
            if (pos >= 0) {
                episodes.removeAt(pos)
                val media = item.media
                if (media != null && media.downloaded) episodes.add(pos, item)
            }
        }
//        have to do this as adapter.notifyItemRemoved(pos) when pos == 0 causes crash
//        if (size > 0) adapter.updateItems(episodes)
        refreshInfoBar()
    }

    private fun refreshSwipeTelltale() {
        leftActionState.value = swipeActions.actions?.left
        rightActionState.value = swipeActions.actions?.right
//        if (swipeActions.actions?.left != null) binding.leftActionIcon.setImageResource(swipeActions.actions!!.left!!.getActionIcon())
//        if (swipeActions.actions?.right != null) binding.rightActionIcon.setImageResource(swipeActions.actions!!.right!!.getActionIcon())
    }

    private var loadItemsRunning = false
    private fun loadItems() {
        emptyView.hide()
        if (!loadItemsRunning) {
            loadItemsRunning = true
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val sortOrder: EpisodeSortOrder? = downloadsSortedOrder
                        val filter = getFilter()
                        val downloadedItems = getEpisodes(0, Int.MAX_VALUE, filter, sortOrder)
                        if (runningDownloads.isEmpty()) {
                            episodes.clear()
                            episodes.addAll(downloadedItems)
                        } else {
                            val mediaUrls: MutableList<String> = ArrayList()
                            for (url in runningDownloads) {
                                if (EpisodeUtil.indexOfItemWithDownloadUrl(downloadedItems, url) != -1) continue
                                mediaUrls.add(url)
                            }
                            val currentDownloads = getEpisdesWithUrl(mediaUrls).toMutableList()
                            currentDownloads.addAll(downloadedItems)
                            episodes.clear()
                            episodes.addAll(currentDownloads)
                        }
                        episodes.retainAll { filter.matchesForQueues(it) }
                    }
                    withContext(Dispatchers.Main) {
//                    adapter.setDummyViews(0)
//                        adapter.updateItems(episodes)
                        refreshInfoBar()
                    }
                } catch (e: Throwable) {
//                adapter.setDummyViews(0)
//                    adapter.updateItems(mutableListOf())
                    Log.e(TAG, Log.getStackTraceString(e))
                } finally { loadItemsRunning = false }
            }
        }
    }

    private fun getEpisdesWithUrl(urls: List<String>): List<Episode> {
        Logd(TAG, "getEpisdesWithUrl() called ")
        if (urls.isEmpty()) return listOf()
        val episodes: MutableList<Episode> = mutableListOf()
        for (url in urls) {
            val media = realm.query(EpisodeMedia::class).query("downloadUrl == $0", url).first().find() ?: continue
            val item_ = media.episodeOrFetch()
            if (item_ != null) episodes.add(item_)
        }
        return realm.copyFromRealm(episodes)
    }

    private fun refreshInfoBar() {
        var info = String.format(Locale.getDefault(), "%d%s", episodes.size, getString(R.string.episodes_suffix))
        if (episodes.isNotEmpty()) {
            var sizeMB: Long = 0
            for (item in episodes) sizeMB += item.media?.size ?: 0
            info += " • " + (sizeMB / 1000000) + " MB"
        }
        Logd(TAG, "filter value: ${getFilter().values.size} ${getFilter().values.joinToString()}")
        if (getFilter().values.size > 1) info += " - ${getString(R.string.filtered_label)}"
//        binding.infoBar.text = info
        infoBarText.value = info
    }

//    override fun onStartSelectMode() {
//        swipeActions.detach()
//        speedDialView.visibility = View.VISIBLE
//    }
//
//    override fun onEndSelectMode() {
//        speedDialView.close()
//        speedDialView.visibility = View.GONE
////        swipeActions.attachTo(recyclerView)
//    }

    class DownloadsSortDialog : EpisodeSortDialog() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            sortOrder = downloadsSortedOrder
        }
        override fun onAddItem(title: Int, ascending: EpisodeSortOrder, descending: EpisodeSortOrder, ascendingIsDefault: Boolean) {
            if (ascending == EpisodeSortOrder.DATE_OLD_NEW
                    || ascending == EpisodeSortOrder.PLAYED_DATE_OLD_NEW
                    || ascending == EpisodeSortOrder.COMPLETED_DATE_OLD_NEW
                    || ascending == EpisodeSortOrder.DOWNLOAD_DATE_OLD_NEW
                    || ascending == EpisodeSortOrder.DURATION_SHORT_LONG
                    || ascending == EpisodeSortOrder.EPISODE_TITLE_A_Z
                    || ascending == EpisodeSortOrder.SIZE_SMALL_LARGE
                    || ascending == EpisodeSortOrder.FEED_TITLE_A_Z) {
                super.onAddItem(title, ascending, descending, ascendingIsDefault)
            }
        }
        override fun onSelectionChanged() {
            super.onSelectionChanged()
            downloadsSortedOrder = sortOrder
            EventFlow.postEvent(FlowEvent.DownloadLogEvent())
        }
    }

    class DownloadsFilterDialog : EpisodeFilterDialog() {
        override fun onFilterChanged(newFilterValues: Set<String>) {
            EventFlow.postEvent(FlowEvent.DownloadsFilterEvent(newFilterValues))
        }
        companion object {
            fun newInstance(filter: EpisodeFilter?): DownloadsFilterDialog {
                val dialog = DownloadsFilterDialog()
                dialog.filter = filter
                dialog.filtersDisabled.add(FeedItemFilterGroup.DOWNLOADED)
                dialog.filtersDisabled.add(FeedItemFilterGroup.MEDIA)
                return dialog
            }
        }
    }

    companion object {
        val TAG = DownloadsCFragment::class.simpleName ?: "Anonymous"

        const val ARG_SHOW_LOGS: String = "show_logs"
        private const val KEY_UP_ARROW = "up_arrow"

        //    the sort order for the downloads.
        var downloadsSortedOrder: EpisodeSortOrder?
            get() {
                val sortOrderStr = appPrefs.getString(UserPreferences.Prefs.prefDownloadSortedOrder.name, "" + EpisodeSortOrder.DATE_NEW_OLD.code)
                return EpisodeSortOrder.fromCodeString(sortOrderStr)
            }
            set(sortOrder) {
                appPrefs.edit().putString(UserPreferences.Prefs.prefDownloadSortedOrder.name, "" + sortOrder!!.code).apply()
            }

        var prefFilterDownloads: String
            get() = appPrefs.getString(UserPreferences.Prefs.prefDownloadsFilter.name, EpisodeFilter.States.downloaded.name) ?: EpisodeFilter.States.downloaded.name
            set(filter) {
                appPrefs.edit().putString(UserPreferences.Prefs.prefDownloadsFilter.name, filter).apply()
            }
    }
}
