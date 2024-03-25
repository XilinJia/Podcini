package ac.mdiq.podcini.ui.statistics.subscriptions


import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.StatisticsFragmentBinding
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBReader.StatisticsResult
import ac.mdiq.podcini.storage.StatisticsItem
import ac.mdiq.podcini.ui.statistics.StatisticsFragment
import ac.mdiq.podcini.util.event.StatisticsEvent
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.math.max
import kotlin.math.min

/**
 * Displays the 'playback statistics' screen
 */
class SubscriptionStatisticsFragment : Fragment() {
    private var _binding: StatisticsFragmentBinding? = null
    private val binding get() = _binding!!

    private var disposable: Disposable? = null
    private var statisticsResult: StatisticsResult? = null

    private lateinit var feedStatisticsList: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var listAdapter: PlaybackStatisticsListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View {
        _binding = StatisticsFragmentBinding.inflate(inflater)
        feedStatisticsList = binding.statisticsList
        progressBar = binding.progressBar
        listAdapter = PlaybackStatisticsListAdapter(this)
        feedStatisticsList.setLayoutManager(LinearLayoutManager(context))
        feedStatisticsList.setAdapter(listAdapter)
        EventBus.getDefault().register(this)
        refreshStatistics()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        EventBus.getDefault().unregister(this)
        disposable?.dispose()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun statisticsEvent(event: StatisticsEvent?) {
        refreshStatistics()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.statistics_reset).setVisible(true)
        menu.findItem(R.id.statistics_filter).setVisible(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.statistics_filter) {
            if (statisticsResult != null) {
                StatisticsFilterDialog(requireContext(), statisticsResult!!.oldestDate).show()
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
        if (disposable != null) {
            disposable!!.dispose()
        }
        val prefs = requireContext().getSharedPreferences(StatisticsFragment.PREF_NAME, Context.MODE_PRIVATE)
        val includeMarkedAsPlayed = prefs.getBoolean(StatisticsFragment.PREF_INCLUDE_MARKED_PLAYED, false)
        val timeFilterFrom = prefs.getLong(StatisticsFragment.PREF_FILTER_FROM, 0)
        val timeFilterTo = prefs.getLong(StatisticsFragment.PREF_FILTER_TO, Long.MAX_VALUE)
        disposable = Observable.fromCallable {
            val statisticsData = DBReader.getStatistics(
                includeMarkedAsPlayed, timeFilterFrom, timeFilterTo)
            statisticsData.feedTime.sortWith { item1: StatisticsItem, item2: StatisticsItem ->
                item2.timePlayed.compareTo(item1.timePlayed)
            }
            statisticsData
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result: StatisticsResult ->
                statisticsResult = result
                // When "from" is "today", set it to today
                listAdapter.setTimeFilter(includeMarkedAsPlayed, max(
                    min(timeFilterFrom.toDouble(), System.currentTimeMillis().toDouble()), result.oldestDate.toDouble())
                    .toLong(),
                    min(timeFilterTo.toDouble(), System.currentTimeMillis().toDouble()).toLong())
                listAdapter.update(result.feedTime)
                progressBar.visibility = View.GONE
                feedStatisticsList.visibility = View.VISIBLE
            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    companion object {
        private val TAG: String = SubscriptionStatisticsFragment::class.java.simpleName
    }
}
