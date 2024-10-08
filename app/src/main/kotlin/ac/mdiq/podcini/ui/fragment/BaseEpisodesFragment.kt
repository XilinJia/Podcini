package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.BaseEpisodesListFragmentBinding
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.utils.EpisodeUtil
import ac.mdiq.podcini.ui.actions.SwipeAction
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.*
import ac.mdiq.podcini.ui.utils.EmptyViewHandler
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@UnstableApi
abstract class BaseEpisodesFragment : Fragment(), Toolbar.OnMenuItemClickListener {
    val TAG = this::class.simpleName ?: "Anonymous"

    @JvmField
    protected var page: Int = 1
    private var displayUpArrow = false

    var _binding: BaseEpisodesListFragmentBinding? = null
    protected val binding get() = _binding!!

    protected var infoBarText = mutableStateOf("")
    private var leftActionState = mutableStateOf<SwipeAction?>(null)
    private var rightActionState = mutableStateOf<SwipeAction?>(null)

    lateinit var emptyView: EmptyViewHandler
    lateinit var toolbar: MaterialToolbar
    lateinit var swipeActions: SwipeActions

    val episodes = mutableListOf<Episode>()
    private val vms = mutableStateListOf<EpisodeVM>()

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)

        _binding = BaseEpisodesListFragmentBinding.inflate(inflater)
        Logd(TAG, "fragment onCreateView")

        toolbar = binding.toolbar
        toolbar.setOnMenuItemClickListener(this)
//        toolbar.setOnLongClickListener {
//            recyclerView.scrollToPosition(5)
//            recyclerView.post { recyclerView.smoothScrollToPosition(0) }
//            false
//        }
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

        (activity as MainActivity).setupToolbarToggle(toolbar, displayUpArrow)

//        recyclerView.setRecycledViewPool((activity as MainActivity).recycledViewPool)
//        recyclerView.addOnScrollListener(LiftOnScrollListener(binding.appbar))

        swipeActions = SwipeActions(this, TAG)
        lifecycle.addObserver(swipeActions)
        binding.lazyColumn.setContent {
            CustomTheme(requireContext()) {
                Column {
                    InforBar(infoBarText, leftAction = leftActionState, rightAction = rightActionState, actionConfig = {swipeActions.showDialog()})
                    EpisodeLazyColumn(
                        activity as MainActivity, vms = vms,
                        leftSwipeCB = {
                            if (leftActionState.value == null) swipeActions.showDialog()
                            else leftActionState.value?.performAction(it, this@BaseEpisodesFragment, swipeActions.filter ?: EpisodeFilter())
                        },
                        rightSwipeCB = {
                            if (rightActionState.value == null) swipeActions.showDialog()
                            else rightActionState.value?.performAction(it, this@BaseEpisodesFragment, swipeActions.filter ?: EpisodeFilter())
                        },
                    )
                }
            }
        }

        swipeActions.setFilter(getFilter())
        refreshSwipeTelltale()

        createListAdaptor()

        emptyView = EmptyViewHandler(requireContext())
        emptyView.setIcon(R.drawable.ic_feed)
        emptyView.setTitle(R.string.no_all_episodes_head_label)
        emptyView.setMessage(R.string.no_all_episodes_label)
        emptyView.hide()

        return binding.root
    }

    open fun createListAdaptor() {}

    override fun onStart() {
        super.onStart()
        procFlowEvents()
        loadItems()
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
    }

//    override fun onPause() {
//        super.onPause()
////        recyclerView.saveScrollPosition(getPrefName())
////        unregisterForContextMenu(recyclerView)
//    }

    @Deprecated("Deprecated in Java")
    @UnstableApi override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (super.onOptionsItemSelected(item)) return true
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
            val pos: Int = EpisodeUtil.indexOfItemWithDownloadUrl(episodes, url)
            if (pos >= 0) {
//                episodes[pos].downloadState.value = event.map[url]?.state ?: DownloadStatus.State.UNKNOWN.ordinal
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

    private fun refreshSwipeTelltale() {
        leftActionState.value = swipeActions.actions?.left
        rightActionState.value = swipeActions.actions?.right
    }

    private var loadItemsRunning = false
    fun loadItems() {
        if (!loadItemsRunning) {
            loadItemsRunning = true
            Logd(TAG, "loadItems() called")
            lifecycleScope.launch {
                try {
                    val data = withContext(Dispatchers.IO) {
                        Pair(loadData().toMutableList(), loadTotalItemCount())
                    }
                    val restoreScrollPosition = episodes.isEmpty()
                    episodes.clear()
                    episodes.addAll(data.first)
                    withContext(Dispatchers.Main) {
                        vms.clear()
                        for (e in data.first) { vms.add(EpisodeVM(e)) }
//                        if (restoreScrollPosition) recyclerView.restoreScrollPosition(getPrefName())
                        updateToolbar()
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, Log.getStackTraceString(e))
                } finally {
                    loadItemsRunning = false
                }
            }
        }
    }

    protected abstract fun loadData(): List<Episode>

    protected abstract fun loadTotalItemCount(): Int

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
        const val EPISODES_PER_PAGE: Int = 50
    }
}
