package de.danoeh.antennapod.ui.statistics.subscriptions

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.danoeh.antennapod.core.storage.DBReader
import de.danoeh.antennapod.core.storage.DBReader.StatisticsResult
import de.danoeh.antennapod.core.storage.StatisticsItem
import de.danoeh.antennapod.event.StatisticsEvent
import de.danoeh.antennapod.ui.statistics.R
import de.danoeh.antennapod.ui.statistics.StatisticsFragment
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Displays the 'playback statistics' screen
 */
class SubscriptionStatisticsFragment : Fragment() {
    private var disposable: Disposable? = null
    private var feedStatisticsList: RecyclerView? = null
    private var progressBar: ProgressBar? = null
    private var listAdapter: PlaybackStatisticsListAdapter? = null
    private var statisticsResult: StatisticsResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.statistics_fragment, container, false)
        feedStatisticsList = root.findViewById(R.id.statistics_list)
        progressBar = root.findViewById(R.id.progressBar)
        listAdapter = PlaybackStatisticsListAdapter(this)
        feedStatisticsList?.setLayoutManager(LinearLayoutManager(context))
        feedStatisticsList?.setAdapter(listAdapter)
        EventBus.getDefault().register(this)
        return root
    }

    override fun onStart() {
        super.onStart()
        refreshStatistics()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        EventBus.getDefault().unregister(this)
        if (disposable != null) {
            disposable!!.dispose()
        }
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
        progressBar!!.visibility = View.VISIBLE
        feedStatisticsList!!.visibility = View.GONE
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
            statisticsData.feedTime.sortWith(Comparator { item1: StatisticsItem, item2: StatisticsItem ->
                item2.timePlayed.compareTo(item1.timePlayed)
            })
            statisticsData
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result: StatisticsResult ->
                statisticsResult = result
                // When "from" is "today", set it to today
                listAdapter!!.setTimeFilter(includeMarkedAsPlayed, max(
                    min(timeFilterFrom.toDouble(), System.currentTimeMillis().toDouble()), result.oldestDate.toDouble())
                    .toLong(),
                    min(timeFilterTo.toDouble(), System.currentTimeMillis().toDouble()).toLong())
                listAdapter!!.update(result.feedTime)
                progressBar!!.visibility = View.GONE
                feedStatisticsList!!.visibility = View.VISIBLE
            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    companion object {
        private val TAG: String = SubscriptionStatisticsFragment::class.java.simpleName
    }
}
