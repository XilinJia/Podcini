package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.ui.actions.SwipeAction
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.actions.SwipeActions.Companion.SwipeActionsSettingDialog
import ac.mdiq.podcini.ui.actions.SwipeActions.NoActionSwipeAction
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.*
import ac.mdiq.podcini.util.Logd
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import kotlin.math.min

class OnlineEpisodesFragment: Fragment() {

    class OnlineEpisodesVM {
        internal var displayUpArrow = false

        internal var infoBarText = mutableStateOf("")
        internal lateinit var swipeActions: SwipeActions
        internal var leftActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
        internal var rightActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())

        internal var showSwipeActionsDialog by mutableStateOf(false)

        internal val episodes = mutableListOf<Episode>()
        internal val vms = mutableStateListOf<EpisodeVM>()
    }

    private val vm = OnlineEpisodesVM()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        Logd(TAG, "fragment onCreateView")
        vm.displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) vm.displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

        vm.swipeActions = SwipeActions(this, TAG)
        vm.leftActionState.value = vm.swipeActions.actions.left[0]
        vm.rightActionState.value = vm.swipeActions.actions.right[0]
        lifecycle.addObserver(vm.swipeActions)

        val composeView = ComposeView(requireContext()).apply { setContent { CustomTheme(requireContext()) { OnlineEpisodesScreen() } } }
        refreshSwipeTelltale()
        return composeView
    }

    @Composable
    fun OnlineEpisodesScreen() {
        if (vm.showSwipeActionsDialog) SwipeActionsSettingDialog(vm.swipeActions, onDismissRequest = { vm.showSwipeActionsDialog = false }) { actions ->
            vm.swipeActions.actions = actions
            refreshSwipeTelltale()
        }
        vm.swipeActions.ActionOptionsDialog()
        Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                InforBar(vm.infoBarText, leftAction = vm.leftActionState, rightAction = vm.rightActionState, actionConfig = { vm.showSwipeActionsDialog = true })
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
                )
            }
        }
    }

    fun buildMoreItems() {
        val nextItems = (vm.vms.size until min(vm.vms.size + VMS_CHUNK_SIZE, vm.episodes.size)).map { EpisodeVM(vm.episodes[it], FeedEpisodesFragment.Companion.TAG) }
        if (nextItems.isNotEmpty()) vm.vms.addAll(nextItems)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        TopAppBar(title = { Text(stringResource(R.string.online_episodes_label)) },
            navigationIcon = if (vm.displayUpArrow) {
                { IconButton(onClick = { parentFragmentManager.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            } else {
                { IconButton(onClick = { (activity as? MainActivity)?.openDrawer() }) { Icon(Icons.Filled.Menu, contentDescription = "Open Drawer") } }
            }
        )
    }

    override fun onStart() {
        super.onStart()
        stopMonitor(vm.vms)
        vm.vms.clear()
        buildMoreItems()
//        for (e in episodes) { vms.add(EpisodeVM(e, TAG)) }
        vm.infoBarText.value = "${vm.episodes.size} episodes"
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        vm.episodes.clear()
        stopMonitor(vm.vms)
        vm.vms.clear()
        super.onDestroyView()
    }

    private fun refreshSwipeTelltale() {
        vm.leftActionState.value = vm.swipeActions.actions.left[0]
        vm.rightActionState.value = vm.swipeActions.actions.right[0]
    }

    fun setEpisodes(episodeList_: MutableList<Episode>) {
        vm.episodes.clear()
        vm.episodes.addAll(episodeList_)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, vm.displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    companion object {
        val TAG = OnlineEpisodesFragment::class.simpleName ?: "Anonymous"

        private const val KEY_UP_ARROW = "up_arrow"
        const val PREF_NAME: String = "RemoteEpisodesFragment"

        fun newInstance(episodes: MutableList<Episode>): OnlineEpisodesFragment {
            val i = OnlineEpisodesFragment()
            i.setEpisodes(episodes)
            return i
        }
    }
}
