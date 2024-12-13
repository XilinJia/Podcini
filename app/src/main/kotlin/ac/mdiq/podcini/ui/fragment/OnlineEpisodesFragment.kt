package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.ui.actions.SwipeAction
import ac.mdiq.podcini.ui.actions.SwipeActions
import ac.mdiq.podcini.ui.actions.SwipeActions.NoActionSwipeAction
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.compose.EpisodeVM
import ac.mdiq.podcini.ui.compose.InforBar
import ac.mdiq.podcini.ui.compose.stopMonitor
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment

class OnlineEpisodesFragment: Fragment() {
    private var displayUpArrow = false

    private var infoBarText = mutableStateOf("")
    private var leftActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
    private var rightActionState = mutableStateOf<SwipeAction>(NoActionSwipeAction())
    lateinit var swipeActions: SwipeActions

    val episodes = mutableListOf<Episode>()
    val vms = mutableStateListOf<EpisodeVM>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        Logd(TAG, "fragment onCreateView")
        displayUpArrow = parentFragmentManager.backStackEntryCount != 0
        if (savedInstanceState != null) displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)

        swipeActions = SwipeActions(this, TAG)
        lifecycle.addObserver(swipeActions)

        val composeView = ComposeView(requireContext()).apply {
            setContent {
                CustomTheme(requireContext()) {
                    OpenDialog()
                    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
                        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                            InforBar(infoBarText, leftAction = leftActionState, rightAction = rightActionState, actionConfig = { swipeActions.showDialog() })
                            EpisodeLazyColumn(activity as MainActivity, vms = vms,
                                leftSwipeCB = {
                                    if (leftActionState.value is NoActionSwipeAction) swipeActions.showDialog()
                                    else leftActionState.value.performAction(it, this@OnlineEpisodesFragment)
                                },
                                rightSwipeCB = {
                                    if (rightActionState.value is NoActionSwipeAction) swipeActions.showDialog()
                                    else rightActionState.value.performAction(it, this@OnlineEpisodesFragment)
                                },
                            )
                        }
                    }
                }
            }
        }
        refreshSwipeTelltale()
        return composeView
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        TopAppBar(title = { Text(stringResource(R.string.online_episodes_label)) },
            navigationIcon = if (displayUpArrow) {
                { IconButton(onClick = { parentFragmentManager.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            } else {
                { IconButton(onClick = { (activity as? MainActivity)?.openDrawer() }) { Icon(Icons.Filled.Menu, contentDescription = "Open Drawer") } }
            }
        )
    }

    @Composable
    fun OpenDialog() {}

    override fun onStart() {
        super.onStart()
        stopMonitor(vms)
        vms.clear()
        for (e in episodes) { vms.add(EpisodeVM(e, TAG)) }
        infoBarText.value = "${episodes.size} episodes"
    }

    override fun onDestroyView() {
        Logd(TAG, "onDestroyView")
        episodes.clear()
        stopMonitor(vms)
        vms.clear()
        super.onDestroyView()
    }

    private fun refreshSwipeTelltale() {
        leftActionState.value = swipeActions.actions.left[0]
        rightActionState.value = swipeActions.actions.right[0]
    }

    fun setEpisodes(episodeList_: MutableList<Episode>) {
        episodes.clear()
        episodes.addAll(episodeList_)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
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
