package ac.mdiq.podcini.ui.fragment

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Shows all episodes (possibly filtered by user).
 */
@UnstableApi class RemoteEpisodesFragment : BaseEpisodesFragment() {

    private val episodeList: MutableList<Episode> = mutableListOf()

    @UnstableApi override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        Logd(TAG, "fragment onCreateView")

        toolbar.inflateMenu(R.menu.episodes)
        toolbar.setTitle(R.string.episodes_label)
        updateToolbar()
        adapter.setOnSelectModeListener(null)
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

    fun setEpisodes(episodeList_: MutableList<Episode>) {
        episodeList.clear()
        episodeList.addAll(episodeList_)
    }

    override fun loadData(): List<Episode> {
        if (episodeList.isEmpty()) return listOf()
        return episodeList.subList(0, min(episodeList.size-1, page * EPISODES_PER_PAGE))
    }

    override fun loadMoreData(page: Int): List<Episode> {
        val offset = (page - 1) * EPISODES_PER_PAGE
        if (offset >= episodeList.size) return listOf()
        val toIndex = offset + EPISODES_PER_PAGE
        return episodeList.subList(offset, min(episodeList.size, toIndex))
    }

    override fun loadTotalItemCount(): Int {
        return episodeList.size
    }

    override fun getPrefName(): String {
        return PREF_NAME
    }

    override fun updateToolbar() {
        binding.toolbar.menu.findItem(R.id.episodes_sort).setVisible(false)
        binding.toolbar.menu.findItem(R.id.refresh_item).setVisible(false)
        binding.toolbar.menu.findItem(R.id.action_search).setVisible(false)
        binding.toolbar.menu.findItem(R.id.action_favorites).setVisible(false)
        binding.toolbar.menu.findItem(R.id.filter_items).setVisible(false)
    }

    @OptIn(UnstableApi::class) override fun onMenuItemClick(item: MenuItem): Boolean {
        if (super.onOptionsItemSelected(item)) return true

        when (item.itemId) {
            else -> return false
        }
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
                    is FlowEvent.AllEpisodesFilterEvent -> page = 1
                    else -> {}
                }
            }
        }
    }

    companion object {
        const val PREF_NAME: String = "EpisodesListFragment"

        fun newInstance(episodes: MutableList<Episode>): RemoteEpisodesFragment {
            val i = RemoteEpisodesFragment()
            i.setEpisodes(episodes)
            return i
        }

    }
}
