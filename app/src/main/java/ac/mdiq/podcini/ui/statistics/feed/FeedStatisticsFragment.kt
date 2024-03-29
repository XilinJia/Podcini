package ac.mdiq.podcini.ui.statistics.feed

import ac.mdiq.podcini.databinding.FeedStatisticsBinding
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.StatisticsItem
import ac.mdiq.podcini.util.Converter.shortLocalizedDuration
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.*

class FeedStatisticsFragment : Fragment() {
    private var _binding: FeedStatisticsBinding? = null
    private val binding get() = _binding!!

    private var feedId: Long = 0
    private var disposable: Disposable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View {
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
        disposable =
            Observable.fromCallable {
                val statisticsData = DBReader.getStatistics(true, 0, Long.MAX_VALUE)
                statisticsData.feedTime.sortWith { item1: StatisticsItem, item2: StatisticsItem ->
                    java.lang.Long.compare(item2.timePlayed,
                        item1.timePlayed)
                }

                for (statisticsItem in statisticsData.feedTime) {
                    if (statisticsItem.feed.id == feedId) {
                        return@fromCallable statisticsItem
                    }
                }
                null
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ s: StatisticsItem? -> this.showStats(s) }, { obj: Throwable -> obj.printStackTrace() })
    }

    private fun showStats(s: StatisticsItem?) {
        binding.startedTotalLabel.text = String.format(Locale.getDefault(), "%d / %d",
            s!!.episodesStarted, s.episodes)
        binding.timePlayedLabel.text =
            shortLocalizedDuration(requireContext(), s.timePlayed)
        binding.totalDurationLabel.text =
            shortLocalizedDuration(requireContext(), s.time)
        binding.onDeviceLabel.text = String.format(Locale.getDefault(), "%d", s.episodesDownloadCount)
        binding.spaceUsedLabel.text = Formatter.formatShortFileSize(context, s.totalDownloadSize)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        disposable?.dispose()
    }

    companion object {
        private const val EXTRA_FEED_ID = "ac.mdiq.podcini.extra.feedId"
        private const val EXTRA_DETAILED = "ac.mdiq.podcini.extra.detailed"

        @JvmStatic
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
