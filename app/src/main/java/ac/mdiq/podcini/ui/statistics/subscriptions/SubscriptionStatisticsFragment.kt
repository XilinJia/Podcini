package ac.mdiq.podcini.ui.statistics.subscriptions


import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.StatisticsFragmentBinding
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBReader.MonthlyStatisticsItem
import ac.mdiq.podcini.storage.DBReader.StatisticsResult
import ac.mdiq.podcini.storage.StatisticsItem
import ac.mdiq.podcini.ui.statistics.StatisticsFragment
import ac.mdiq.podcini.ui.statistics.years.YearsStatisticsFragment
import ac.mdiq.podcini.ui.statistics.years.YearsStatisticsFragment.Companion
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Displays the 'playback statistics' screen
 */
class SubscriptionStatisticsFragment : Fragment() {
    private var _binding: StatisticsFragmentBinding? = null
    private val binding get() = _binding!!

//    private var disposable: Disposable? = null
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
//        disposable?.dispose()
    }

    private fun procFlowEvents() {
        lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                when (event) {
                    is FlowEvent.StatisticsEvent -> refreshStatistics()
                    else -> {}
                }
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.statistics_reset).setVisible(true)
        menu.findItem(R.id.statistics_filter).setVisible(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.statistics_filter) {
            if (statisticsResult != null) {
                val dialog = object: DatesFilterDialog(requireContext(), statisticsResult!!.oldestDate) {
                    override fun initParams() {
                        prefs = requireContext().getSharedPreferences(StatisticsFragment.PREF_NAME, Context.MODE_PRIVATE)
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
//        disposable?.dispose()

        val prefs = requireContext().getSharedPreferences(StatisticsFragment.PREF_NAME, Context.MODE_PRIVATE)
        val includeMarkedAsPlayed = prefs.getBoolean(StatisticsFragment.PREF_INCLUDE_MARKED_PLAYED, false)
        val timeFilterFrom = prefs.getLong(StatisticsFragment.PREF_FILTER_FROM, 0)
        val timeFilterTo = prefs.getLong(StatisticsFragment.PREF_FILTER_TO, Long.MAX_VALUE)

//        disposable = Observable.fromCallable {
//            val statisticsData = DBReader.getStatistics(
//                includeMarkedAsPlayed, timeFilterFrom, timeFilterTo)
//            statisticsData.feedTime.sortWith { item1: StatisticsItem, item2: StatisticsItem ->
//                item2.timePlayed.compareTo(item1.timePlayed)
//            }
//            statisticsData
//        }
//            .subscribeOn(Schedulers.io())
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribe({ result: StatisticsResult ->
//                statisticsResult = result
//                // When "from" is "today", set it to today
//                listAdapter.setTimeFilter(includeMarkedAsPlayed, max(
//                    min(timeFilterFrom.toDouble(), System.currentTimeMillis().toDouble()), result.oldestDate.toDouble())
//                    .toLong(),
//                    min(timeFilterTo.toDouble(), System.currentTimeMillis().toDouble()).toLong())
//                listAdapter.update(result.feedTime)
//                progressBar.visibility = View.GONE
//                feedStatisticsList.visibility = View.VISIBLE
//            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })

        lifecycleScope.launch {
            try {
                val statisticsData = withContext(Dispatchers.IO) {
                    val data = DBReader.getStatistics(includeMarkedAsPlayed, timeFilterFrom, timeFilterTo)
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

    companion object {
        private val TAG: String = SubscriptionStatisticsFragment::class.java.simpleName
    }
}
