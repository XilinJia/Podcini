package ac.mdiq.podcini.ui.statistics


import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.StatisticsFragmentBinding
import ac.mdiq.podcini.databinding.StatisticsListitemBarchartBinding
import ac.mdiq.podcini.databinding.StatisticsYearListitemBinding
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.MonthlyStatisticsItem
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Displays the yearly statistics screen
 */
class YearsStatisticsFragment : Fragment() {
    private var _binding: StatisticsFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var yearStatisticsList: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var listAdapter: YearStatisticsListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View {
        _binding = StatisticsFragmentBinding.inflate(inflater)
        yearStatisticsList = binding.statisticsList
        progressBar = binding.progressBar
        listAdapter = YearStatisticsListAdapter(requireContext())
        yearStatisticsList.layoutManager = LinearLayoutManager(context)
        yearStatisticsList.adapter = listAdapter
        refreshStatistics()

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
                    is FlowEvent.StatisticsEvent -> refreshStatistics()
                    else -> {}
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.statistics_reset).setVisible(true)
        menu.findItem(R.id.statistics_filter).setVisible(false)
    }

    private fun refreshStatistics() {
        progressBar.visibility = View.VISIBLE
        yearStatisticsList.visibility = View.GONE
        loadStatistics()
    }

    private fun loadStatistics() {
        lifecycleScope.launch {
            try {
                val result: List<MonthlyStatisticsItem> = withContext(Dispatchers.IO) {
                    getMonthlyTimeStatistics()
                }
                listAdapter.update(result)
                progressBar.visibility = View.GONE
                yearStatisticsList.visibility = View.VISIBLE
            } catch (error: Throwable) {
                // This also runs on the Main thread
                Log.e(TAG, Log.getStackTraceString(error))
            }
        }
    }

    private fun getMonthlyTimeStatistics(): List<MonthlyStatisticsItem> {
        Logd(TAG, "getMonthlyTimeStatistics called")
        val months: MutableList<MonthlyStatisticsItem> = ArrayList()
        val medias = realm.query(EpisodeMedia::class).query("lastPlayedTime > 0 AND playedDuration > 0").find()
        val groupdMedias = medias.groupBy {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = it.lastPlayedTime
            "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH) + 1}"
        }
        val orderedGroupedItems = groupdMedias.toList().sortedBy {
            val (key, _) = it
            val year = key.substringBefore("-").toInt()
            val month = key.substringAfter("-").toInt()
            year * 12 + month
        }.toMap()
        for (key in orderedGroupedItems.keys) {
            val v = orderedGroupedItems[key] ?: continue
            val episode = MonthlyStatisticsItem()
            episode.year = key.substringBefore("-").toInt()
            episode.month = key.substringAfter("-").toInt()
            var dur = 0L
            for (m in v) {
                dur += m.playedDuration
            }
            episode.timePlayed = dur
            months.add(episode)
        }
        return months
    }

    /**
     * Adapter for the yearly playback statistics list.
     */
    class YearStatisticsListAdapter(val context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val statisticsData: MutableList<MonthlyStatisticsItem> = ArrayList()
        private val yearlyAggregate: MutableList<MonthlyStatisticsItem?> = ArrayList()

        override fun getItemCount(): Int {
            return yearlyAggregate.size + 1
        }

        override fun getItemViewType(position: Int): Int {
            return if (position == 0) TYPE_HEADER else TYPE_FEED
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(context)
            if (viewType == TYPE_HEADER) {
                return HeaderHolder(inflater.inflate(R.layout.statistics_listitem_barchart, parent, false))
            }
            return StatisticsHolder(inflater.inflate(R.layout.statistics_year_listitem, parent, false))
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
            if (getItemViewType(position) == TYPE_HEADER) {
                val holder = h as HeaderHolder
                holder.barChart.setData(statisticsData)
            } else {
                val holder = h as StatisticsHolder
                val statsItem = yearlyAggregate[position - 1]
                holder.year.text = String.format(Locale.getDefault(), "%d ", statsItem!!.year)
                holder.hours.text = String.format(Locale.getDefault(),
                    "%.1f ",
                    statsItem.timePlayed / 3600000.0f) + context.getString(R.string.time_hours)
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        fun update(statistics: List<MonthlyStatisticsItem>) {
            var lastYear = if (statistics.isNotEmpty()) statistics[0].year else 0
            var lastDataPoint = if (statistics.isNotEmpty()) (statistics[0].month - 1) + lastYear * 12 else 0
            var yearSum: Long = 0
            yearlyAggregate.clear()
            statisticsData.clear()
            for (statistic in statistics) {
                if (statistic.year != lastYear) {
                    val yearAggregate = MonthlyStatisticsItem()
                    yearAggregate.year = lastYear
                    yearAggregate.timePlayed = yearSum
                    yearlyAggregate.add(yearAggregate)
                    yearSum = 0
                    lastYear = statistic.year
                }
                yearSum += statistic.timePlayed
                while (lastDataPoint + 1 < (statistic.month - 1) + statistic.year * 12) {
                    lastDataPoint++
                    val item = MonthlyStatisticsItem()
                    item.year = lastDataPoint / 12
                    item.month = lastDataPoint % 12 + 1
                    statisticsData.add(item) // Compensate for months without playback
                }
                statisticsData.add(statistic)
                lastDataPoint = (statistic.month - 1) + statistic.year * 12
            }
            val yearAggregate = MonthlyStatisticsItem()
            yearAggregate.year = lastYear
            yearAggregate.timePlayed = yearSum
            yearlyAggregate.add(yearAggregate)
            yearlyAggregate.reverse()
            notifyDataSetChanged()
        }

        internal class HeaderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val binding = StatisticsListitemBarchartBinding.bind(itemView)
            var barChart: BarChartView = binding.barChart
        }

        internal class StatisticsHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val binding = StatisticsYearListitemBinding.bind(itemView)
            var year: TextView = binding.yearLabel
            var hours: TextView = binding.hoursLabel
        }

        companion object {
            private const val TYPE_HEADER = 0
            private const val TYPE_FEED = 1
        }
    }

    companion object {
        private val TAG: String = YearsStatisticsFragment::class.simpleName ?: "Anonymous"
    }
}
