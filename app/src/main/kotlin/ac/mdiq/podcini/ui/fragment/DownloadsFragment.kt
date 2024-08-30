package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.MultiSelectSpeedDialBinding
import ac.mdiq.podcini.databinding.SimpleListFragmentBinding
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
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
import ac.mdiq.podcini.ui.actions.EpisodeMultiSelectHandler
import ac.mdiq.podcini.ui.actions.actionbutton.DeleteActionButton
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.adapter.EpisodesAdapter
import ac.mdiq.podcini.ui.adapter.SelectableAdapter
import ac.mdiq.podcini.ui.dialog.EpisodeSortDialog
import ac.mdiq.podcini.ui.dialog.SwitchQueueDialog
import ac.mdiq.podcini.ui.utils.EmptyViewHandler
import ac.mdiq.podcini.ui.utils.LiftOnScrollListener
import ac.mdiq.podcini.ui.view.EpisodeViewHolder
import ac.mdiq.podcini.ui.view.EpisodesRecyclerView
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.leinardi.android.speeddial.SpeedDialActionItem
import com.leinardi.android.speeddial.SpeedDialView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

/**
 * Displays all completed downloads and provides a button to delete them.
 */
@UnstableApi class DownloadsFragment : Fragment(), SelectableAdapter.OnSelectModeListener, Toolbar.OnMenuItemClickListener {

    private var _binding: SimpleListFragmentBinding? = null
    private val binding get() = _binding!!

    private var runningDownloads: Set<String> = HashSet()
    private var episodes: MutableList<Episode> = mutableListOf()

    private lateinit var adapter: DownloadsListAdapter
    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: EpisodesRecyclerView
    private lateinit var swipeActions: SwipeActions
    private lateinit var speedDialView: SpeedDialView
    private lateinit var emptyView: EmptyViewHandler
    
    private var displayUpArrow = false

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SimpleListFragmentBinding.inflate(inflater)

        Logd(TAG, "fragment onCreateView")
        toolbar = binding.toolbar
        toolbar.setTitle(R.string.downloads_label)
        toolbar.inflateMenu(R.menu.downloads_completed)
        toolbar.setOnMenuItemClickListener(this)
        toolbar.setOnLongClickListener {
            recyclerView.scrollToPosition(5)
            recyclerView.post { recyclerView.smoothScrollToPosition(0) }
            false
        }
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

        (activity as MainActivity).setupToolbarToggle(toolbar, displayUpArrow)

        recyclerView = binding.recyclerView
        recyclerView.setRecycledViewPool((activity as MainActivity).recycledViewPool)
        adapter = DownloadsListAdapter()
        adapter.setOnSelectModeListener(this)
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(LiftOnScrollListener(binding.appbar))

        swipeActions = SwipeActions(this, TAG).attachTo(recyclerView)
        lifecycle.addObserver(swipeActions)
        swipeActions.setFilter(EpisodeFilter(EpisodeFilter.States.downloaded.name))
        refreshSwipeTelltale()
        binding.leftActionIcon.setOnClickListener {
            swipeActions.showDialog()
        }
        binding.rightActionIcon.setOnClickListener {
            swipeActions.showDialog()
        }

        val animator: RecyclerView.ItemAnimator? = recyclerView.itemAnimator
        if (animator is SimpleItemAnimator) animator.supportsChangeAnimations = false

        binding.progLoading.visibility = View.VISIBLE

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
                if (open && adapter.selectedCount == 0) {
                    (activity as MainActivity).showSnackbarAbovePlayer(R.string.no_items_selected, Snackbar.LENGTH_SHORT)
                    speedDialView.close()
                }
            }
        })
        speedDialView.setOnActionSelectedListener { actionItem: SpeedDialActionItem ->
            adapter.selectedItems.let {
                EpisodeMultiSelectHandler((activity as MainActivity), actionItem.id).handleAction(it)
            }
            adapter.endSelectMode()
            true
        }
        if (arguments != null && requireArguments().getBoolean(ARG_SHOW_LOGS, false))
            DownloadLogFragment().show(childFragmentManager, null)

        addEmptyView()
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
        loadItems()
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
        val recyclerView =  binding.recyclerView
        val childCount = recyclerView.childCount
        for (i in 0 until childCount) {
            val child = recyclerView.getChildAt(i)
            val viewHolder = recyclerView.getChildViewHolder(child) as? EpisodeViewHolder
            viewHolder?.stopDBMonitor()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        _binding = null
        adapter.endSelectMode()
        adapter.clearData()
        toolbar.setOnMenuItemClickListener(null)
        toolbar.setOnLongClickListener(null)
        episodes = mutableListOf()

        super.onDestroyView()
    }

    @UnstableApi override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
//            R.id.refresh_item -> FeedUpdateManager.runOnceOrAsk(requireContext())
            R.id.action_download_logs -> DownloadLogFragment().show(childFragmentManager, null)
            R.id.action_search -> (activity as MainActivity).loadChildFragment(SearchFragment.newInstance())
            R.id.downloads_sort -> DownloadsSortDialog().show(childFragmentManager, "SortDialog")
            R.id.switch_queue -> SwitchQueueDialog(activity as MainActivity).show()
            R.id.reconsile -> reconsile()
            else -> return false
        }
        return true
    }

    private val nameEpisodeMap: MutableMap<String, Episode> = mutableMapOf()
    private val filesRemoved: MutableList<String> = mutableListOf()
    private fun reconsile() {
        runOnIOScope {
            val items = realm.query(Episode::class).query("media.episode == nil").find()
            Logd(TAG, "number of episode with null backlink: ${items.size}")
            for (item in items) {
                upsert(item) {
                    it.media!!.episode = it
                }
            }
            nameEpisodeMap.clear()
            episodes.forEach { e ->
                var fileUrl = e.media?.fileUrl ?: return@forEach
                fileUrl = fileUrl.substring(fileUrl.lastIndexOf('/') + 1)
                Logd(TAG, "reconsile: fileUrl: $fileUrl")
                nameEpisodeMap[fileUrl] = e
            }
            val mediaDir = requireContext().getExternalFilesDir("media") ?: return@runOnIOScope
            mediaDir.listFiles()?.forEach { file -> traverse(file, mediaDir) }
            Logd(TAG, "reconsile: end, episodes missing file: ${nameEpisodeMap.size}")
            if (nameEpisodeMap.isNotEmpty()) {
                for (e in nameEpisodeMap.values) {
                    upsertBlk(e) {
                        it.media?.setfileUrlOrNull(null)
                    }
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
        for (downloadUrl in event.urls) {
            val pos = EpisodeUtil.indexOfItemWithDownloadUrl(episodes.toList(), downloadUrl)
            if (pos >= 0) adapter.notifyItemChangedCompat(pos)
        }
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

    private fun addEmptyView() {
        emptyView = EmptyViewHandler(requireContext())
        emptyView.setIcon(R.drawable.ic_download)
        emptyView.setTitle(R.string.no_comp_downloads_head_label)
        emptyView.setMessage(R.string.no_comp_downloads_label)
        emptyView.attachToRecyclerView(recyclerView)
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
        if (size > 0) {
//            adapter.setDummyViews(0)
            adapter.updateItems(episodes)
        }
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
        if (size > 0) {
//            adapter.setDummyViews(0)
            adapter.updateItems(episodes)
        }
        refreshInfoBar()
    }

    private fun refreshSwipeTelltale() {
        if (swipeActions.actions?.left != null) binding.leftActionIcon.setImageResource(swipeActions.actions!!.left!!.getActionIcon())
        if (swipeActions.actions?.right != null) binding.rightActionIcon.setImageResource(swipeActions.actions!!.right!!.getActionIcon())
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
                        val downloadedItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.downloaded.name), sortOrder)
                        if (runningDownloads.isEmpty()) episodes = downloadedItems.toMutableList()
                        else {
                            val mediaUrls: MutableList<String> = ArrayList()
                            for (url in runningDownloads) {
                                if (EpisodeUtil.indexOfItemWithDownloadUrl(downloadedItems, url) != -1) continue
                                mediaUrls.add(url)
                            }
                            val currentDownloads = getEpisdesWithUrl(mediaUrls).toMutableList()
                            currentDownloads.addAll(downloadedItems)
                            episodes = currentDownloads
                        }
                    }
                    withContext(Dispatchers.Main) {
//                    adapter.setDummyViews(0)
                        binding.progLoading.visibility = View.GONE
                        adapter.updateItems(episodes)
                        refreshInfoBar()
                    }
                } catch (e: Throwable) {
//                adapter.setDummyViews(0)
                    adapter.updateItems(mutableListOf())
                    Log.e(TAG, Log.getStackTraceString(e))
                } finally {
                    loadItemsRunning = false
                }
            }
        }
    }

    private fun getEpisdesWithUrl(urls: List<String?>?): List<Episode> {
        Logd(TAG, "getEpisdesWithUrl() called ")
        if (urls == null) return listOf()
        val episodes: MutableList<Episode> = mutableListOf()
        for (url in urls) {
            if (url == null) continue
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
        binding.infoBar.text = info
    }

    override fun onStartSelectMode() {
        swipeActions.detach()
        speedDialView.visibility = View.VISIBLE
    }

    override fun onEndSelectMode() {
        speedDialView.close()
        speedDialView.visibility = View.GONE
        swipeActions.attachTo(recyclerView)
    }

    @UnstableApi private inner class DownloadsListAdapter : EpisodesAdapter(activity as MainActivity) {
        @UnstableApi override fun afterBindViewHolder(holder: EpisodeViewHolder, pos: Int) {
            if (holder.episode != null && !inActionMode()) {
                if (holder.episode!!.isDownloaded) {
                    val item = getItem(pos) ?: return
                    val actionButton = DeleteActionButton(item)
                    actionButton.configure(holder.secondaryActionButton, holder.secondaryActionIcon, requireContext())
                }
            }
        }
    }

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

    companion object {
        val TAG = DownloadsFragment::class.simpleName ?: "Anonymous"

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
    }
}
