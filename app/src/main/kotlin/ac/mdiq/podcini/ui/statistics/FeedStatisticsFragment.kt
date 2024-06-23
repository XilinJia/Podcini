package ac.mdiq.podcini.ui.statistics

import ac.mdiq.podcini.databinding.FeedStatisticsBinding
import ac.mdiq.podcini.storage.database.LogsAndStats.getStatistics
import ac.mdiq.podcini.storage.model.StatisticsItem
import ac.mdiq.podcini.util.Converter.shortLocalizedDuration
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class FeedStatisticsFragment : Fragment() {
    private var _binding: FeedStatisticsBinding? = null
    private val binding get() = _binding!!

    private var feedId: Long = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        feedId = requireArguments().getLong(EXTRA_FEED_ID)
        _binding = FeedStatisticsBinding.inflate(inflater)

        if (!requireArguments().getBoolean(EXTRA_DETAILED)) {
            for (i in 0 until binding.root.childCount) {
                val child = binding.root.getChildAt(i)
                if ("detailed" == child.tag) {
                    child.visibility = View.GONE
                }
            }
        }

        loadStatistics()
        return binding.root
    }

    private fun loadStatistics() {
        lifecycleScope.launch {
            try {
                val statisticsData = withContext(Dispatchers.IO) {
                    val data = getStatistics(true, 0, Long.MAX_VALUE)
                    data.feedTime.sortWith { item1: StatisticsItem, item2: StatisticsItem ->
                        item2.timePlayed.compareTo(item1.timePlayed)
                    }
                    for (statisticsItem in data.feedTime) {
                        if (statisticsItem.feed.id == feedId) return@withContext statisticsItem
                    }
                    null
                }
                showStats(statisticsData)
            } catch (error: Throwable) {
                error.printStackTrace()
            }
        }
    }

    private fun showStats(s: StatisticsItem?) {
        binding.startedTotalLabel.text = String.format(Locale.getDefault(), "%d / %d", s!!.episodesStarted, s.episodes)
        binding.timePlayedLabel.text = shortLocalizedDuration(requireContext(), s.timePlayed)
        binding.totalDurationLabel.text = shortLocalizedDuration(requireContext(), s.time)
        binding.onDeviceLabel.text = String.format(Locale.getDefault(), "%d", s.episodesDownloadCount)
        binding.spaceUsedLabel.text = Formatter.formatShortFileSize(context, s.totalDownloadSize)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        private const val EXTRA_FEED_ID = "ac.mdiq.podcini.extra.feedId"
        private const val EXTRA_DETAILED = "ac.mdiq.podcini.extra.detailed"

        fun newInstance(feedId: Long, detailed: Boolean): FeedStatisticsFragment {
            val fragment = FeedStatisticsFragment()
            val arguments = Bundle()
            arguments.putLong(EXTRA_FEED_ID, feedId)
            arguments.putBoolean(EXTRA_DETAILED, detailed)
            fragment.arguments = arguments
            return fragment
        }
    }
}
