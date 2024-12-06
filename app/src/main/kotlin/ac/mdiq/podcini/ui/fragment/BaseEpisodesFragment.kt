package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.ComposeFragmentBinding
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.storage.database.Episodes
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.ui.actions.EpisodeActionButton
import ac.mdiq.podcini.ui.actions.SwipeAction
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.actions.SwipeActions.NoActionSwipeAction
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.*
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class BaseEpisodesFragment : Fragment(), Toolbar.OnMenuItemClickListener {
    val TAG = this::class.simpleName ?: "Anonymous"

    var _binding: ComposeFragmentBinding? = null
    protected val binding get() = _binding!!

    private var displayUpArrow = false

    protected var infoBarText = mutableStateOf("")
    private var leftActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
    private var rightActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())

    lateinit var toolbar: MaterialToolbar
    lateinit var swipeActions: SwipeActions

    val episodes = mutableListOf<Episode>()
    protected val vms = mutableStateListOf<EpisodeVM>()
    var showFilterDialog by mutableStateOf(false)
    var showSortDialog by mutableStateOf(false)
    var sortOrder by mutableStateOf(EpisodeSortOrder.DATE_NEW_OLD)

    var actionButtonToPass by mutableStateOf<((Episode) -> EpisodeActionButton)?>(null)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)

        _binding = ComposeFragmentBinding.inflate(inflater)
        Logd(TAG, "fragment onCreateView")

        toolbar = binding.toolbar
        toolbar.setOnMenuItemClickListener(this)
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

        (activity as MainActivity).setupToolbarToggle(toolbar, displayUpArrow)

        swipeActions = SwipeActions(this, TAG)
        lifecycle.addObserver(swipeActions)
        binding.mainView.setContent {
            CustomTheme(requireContext()) {
                if (showFilterDialog) EpisodesFilterDialog(filter = getFilter(), filtersDisabled = filtersDisabled(),
                    onDismissRequest = { showFilterDialog = false } ) { onFilterChanged(it) }
                if (showSortDialog) EpisodeSortDialog(initOrder = sortOrder, onDismissRequest = {showSortDialog = false}) { order, _ -> onSort(order) }
                OpenDialog()

                Column {
                    InforBar(infoBarText, leftAction = leftActionState, rightAction = rightActionState, actionConfig = {swipeActions.showDialog()})
                    EpisodeLazyColumn(
                        activity as MainActivity, vms = vms,
                        leftSwipeCB = {
                            if (leftActionState.value is NoActionSwipeAction) swipeActions.showDialog()
                            else leftActionState.value.performAction(it, this@BaseEpisodesFragment)
                        },
                        rightSwipeCB = {
                            if (rightActionState.value is NoActionSwipeAction) swipeActions.showDialog()
                            else rightActionState.value.performAction(it, this@BaseEpisodesFragment)
                        },
                        actionButton_ = actionButtonToPass
                    )
                }
            }
        }

//        swipeActions.setFilter(getFilter())
        refreshSwipeTelltale()
        return binding.root
    }

    @Composable
    open fun OpenDialog() {}

    open fun onFilterChanged(filterValues: Set<String>) {}

    open fun filtersDisabled(): MutableSet<EpisodeFilter.EpisodesFilterGroup> {
        return mutableSetOf()
    }

    open fun onSort(order: EpisodeSortOrder) {}

    override fun onStart() {
        super.onStart()
        procFlowEvents()
        loadItems()
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
    }

     override fun onMenuItemClick(item: MenuItem): Boolean {
//        if (super.onMenuItemClick(item)) return true
        val itemId = item.itemId
        when (itemId) {
            R.id.action_search -> {
                (activity as MainActivity).loadChildFragment(SearchFragment.newInstance())
                return true
            }
            else -> return false
        }
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        _binding = null
        episodes.clear()
        vms.clear()
        super.onDestroyView()
    }

    private fun onKeyUp(event: KeyEvent) {
        if (!isAdded || !isVisible || !isMenuVisible) return
        when (event.keyCode) {
//            KeyEvent.KEYCODE_T -> recyclerView.smoothScrollToPosition(0)
//            KeyEvent.KEYCODE_B -> recyclerView.smoothScrollToPosition(adapter.itemCount)
            else -> {}
        }
    }

    private fun onEpisodeDownloadEvent(event: FlowEvent.EpisodeDownloadEvent) {
        if (loadItemsRunning) return
        for (url in event.urls) {
//            if (!event.isCompleted(url)) continue
            val pos: Int = Episodes.indexOfItemWithDownloadUrl(episodes, url)
            if (pos >= 0) {
                vms[pos].downloadState = event.map[url]?.state ?: DownloadStatus.State.UNKNOWN.ordinal
            }
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
                    is FlowEvent.SwipeActionsChangedEvent -> refreshSwipeTelltale()
                    is FlowEvent.EpisodeEvent -> onEpisodeEvent(event)
                    is FlowEvent.EpisodeMediaEvent -> onEpisodeMediaEvent(event)
                    is FlowEvent.HistoryEvent -> onHistoryEvent(event)
                    is FlowEvent.FeedListEvent, is FlowEvent.EpisodePlayedEvent, is FlowEvent.PlayerSettingsEvent, is FlowEvent.RatingEvent -> loadItems()
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
                Logd(TAG, "Received key event: $event")
                onKeyUp(event)
            }
        }
    }

    protected open fun onHistoryEvent(event: FlowEvent.HistoryEvent) {}

    protected open fun onEpisodeEvent(event: FlowEvent.EpisodeEvent) { }

    protected open fun onEpisodeMediaEvent(event: FlowEvent.EpisodeMediaEvent) {}

    private fun refreshSwipeTelltale() {
        leftActionState.value = swipeActions.actions.left[0]
        rightActionState.value = swipeActions.actions.right[0]
    }

    private var loadItemsRunning = false
    fun loadItems() {
        if (!loadItemsRunning) {
            loadItemsRunning = true
            Logd(TAG, "loadItems() called")
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        episodes.clear()
                        episodes.addAll(loadData())
                    }
                    withContext(Dispatchers.Main) {
                        vms.clear()
                        for (e in episodes) { vms.add(EpisodeVM(e)) }
                        updateToolbar()
                    }
                } catch (e: Throwable) { Log.e(TAG, Log.getStackTraceString(e))
                } finally { loadItemsRunning = false }
            }
        }
    }

    protected abstract fun loadData(): List<Episode>

    open fun getFilter(): EpisodeFilter {
        return EpisodeFilter.unfiltered()
    }

    protected abstract fun getPrefName(): String

    protected open fun updateToolbar() {}

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val KEY_UP_ARROW = "up_arrow"
    }
}
