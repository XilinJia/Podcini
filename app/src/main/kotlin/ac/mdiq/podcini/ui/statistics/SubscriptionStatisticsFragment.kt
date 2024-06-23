package ac.mdiq.podcini.ui.statistics


import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.StatisticsFragmentBinding
import ac.mdiq.podcini.storage.database.LogsAndStats.getStatistics
import ac.mdiq.podcini.storage.model.StatisticsResult
import ac.mdiq.podcini.storage.model.StatisticsItem
import ac.mdiq.podcini.ui.dialog.DatesFilterDialog
import ac.mdiq.podcini.ui.statistics.PieChartView.PieChartData
import ac.mdiq.podcini.ui.statistics.StatisticsFragment.Companion.prefs
import ac.mdiq.podcini.ui.statistics.FeedStatisticsDialogFragment.Companion.newInstance
import ac.mdiq.podcini.util.Converter.shortLocalizedDuration
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.*
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Displays the 'playback statistics' screen
 */
class SubscriptionStatisticsFragment : Fragment() {
    private var _binding: StatisticsFragmentBinding? = null
    private val binding get() = _binding!!
    private var statisticsResult: StatisticsResult? = null

    private lateinit var feedStatisticsList: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var listAdapter: PlaybackStatisticsListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = StatisticsFragmentBinding.inflate(inflater)
        feedStatisticsList = binding.statisticsList
        progressBar = binding.progressBar
        listAdapter = PlaybackStatisticsListAdapter(this)
        feedStatisticsList.setLayoutManager(LinearLayoutManager(context))
        feedStatisticsList.setAdapter(listAdapter)
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
        menu.findItem(R.id.statistics_filter).setVisible(true)
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.statistics_filter) {
            if (statisticsResult != null) {
                val dialog = object: DatesFilterDialog(requireContext(), statisticsResult!!.oldestDate) {
                    override fun initParams() {
                        prefs = StatisticsFragment.prefs
                        includeMarkedAsPlayed = prefs!!.getBoolean(StatisticsFragment.PREF_INCLUDE_MARKED_PLAYED, false)
                        timeFilterFrom = prefs!!.getLong(StatisticsFragment.PREF_FILTER_FROM, 0)
                        timeFilterTo = prefs!!.getLong(StatisticsFragment.PREF_FILTER_TO, Long.MAX_VALUE)
                    }
                    override fun callback(timeFilterFrom: Long, timeFilterTo: Long, includeMarkedAsPlayed: Boolean) {
                        prefs!!.edit()
                            .putBoolean(StatisticsFragment.PREF_INCLUDE_MARKED_PLAYED, includeMarkedAsPlayed)
                            .putLong(StatisticsFragment.PREF_FILTER_FROM, timeFilterFrom)
                            .putLong(StatisticsFragment.PREF_FILTER_TO, timeFilterTo)
                            .apply()
                        EventFlow.postEvent(FlowEvent.StatisticsEvent())
                    }
                }
                dialog.show()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun refreshStatistics() {
        progressBar.visibility = View.VISIBLE
        feedStatisticsList.visibility = View.GONE
        loadStatistics()
    }

    private fun loadStatistics() {
        val includeMarkedAsPlayed = prefs!!.getBoolean(StatisticsFragment.PREF_INCLUDE_MARKED_PLAYED, false)
        val timeFilterFrom = prefs!!.getLong(StatisticsFragment.PREF_FILTER_FROM, 0)
        val timeFilterTo = prefs!!.getLong(StatisticsFragment.PREF_FILTER_TO, Long.MAX_VALUE)

        lifecycleScope.launch {
            try {
                val statisticsData = withContext(Dispatchers.IO) {
                    val data = getStatistics(includeMarkedAsPlayed, timeFilterFrom, timeFilterTo)
                    data.feedTime.sortWith { item1: StatisticsItem, item2: StatisticsItem ->
                        item2.timePlayed.compareTo(item1.timePlayed)
                    }
                    data
                }
                statisticsResult = statisticsData
                // When "from" is "today", set it to today
                listAdapter.setTimeFilter(includeMarkedAsPlayed,
                    max(min(timeFilterFrom.toDouble(), System.currentTimeMillis().toDouble()), statisticsData.oldestDate.toDouble()).toLong(),
                    min(timeFilterTo.toDouble(), System.currentTimeMillis().toDouble()).toLong())
                listAdapter.update(statisticsData.feedTime)
                progressBar.visibility = View.GONE
                feedStatisticsList.visibility = View.VISIBLE
            } catch (error: Throwable) {
                // This also runs on the Main thread
                Log.e(TAG, Log.getStackTraceString(error))
            }
        }
    }

    /**
     * Adapter for the playback statistics list.
     */
    class PlaybackStatisticsListAdapter(private val fragment: Fragment) : StatisticsListAdapter(
        fragment.requireContext()) {
        private var timeFilterFrom: Long = 0
        private var timeFilterTo = Long.MAX_VALUE
        private var includeMarkedAsPlayed = false

        fun setTimeFilter(includeMarkedAsPlayed: Boolean, timeFilterFrom: Long, timeFilterTo: Long) {
            this.includeMarkedAsPlayed = includeMarkedAsPlayed
            this.timeFilterFrom = timeFilterFrom
            this.timeFilterTo = timeFilterTo
        }

        override val headerCaption: String
            get() {
                if (includeMarkedAsPlayed) {
                    return context.getString(R.string.statistics_counting_total)
                }
                val skeleton = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMM yyyy")
                val dateFormat = SimpleDateFormat(skeleton, Locale.getDefault())
                val dateFrom = dateFormat.format(Date(timeFilterFrom))
                // FilterTo is first day of next month => Subtract one day
                val dateTo = dateFormat.format(Date(timeFilterTo - 24L * 3600000L))
                return context.getString(R.string.statistics_counting_range, dateFrom, dateTo)
            }

        override val headerValue: String
            get() = shortLocalizedDuration(context, pieChartData!!.sum.toLong())

        override fun generateChartData(statisticsData: List<StatisticsItem>?): PieChartData {
            val dataValues = FloatArray(statisticsData!!.size)
            for (i in statisticsData.indices) {
                val item = statisticsData[i]
                dataValues[i] = item.timePlayed.toFloat()
            }
            return PieChartData(dataValues)
        }

        override fun onBindFeedViewHolder(holder: StatisticsHolder?, statsItem: StatisticsItem?) {
            val time = statsItem!!.timePlayed
            holder!!.value.text = shortLocalizedDuration(context, time)

            holder.itemView.setOnClickListener { v: View? ->
                val yourDialogFragment = newInstance(
                    statsItem.feed.id, statsItem.feed.title)
                yourDialogFragment.show(fragment.childFragmentManager.beginTransaction(), "DialogFragment")
            }
        }
    }

    companion object {
        private val TAG: String = SubscriptionStatisticsFragment::class.simpleName ?: "Anonymous"
    }
}
