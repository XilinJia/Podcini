package ac.mdiq.podcini.ui.statistics.years


import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.StatisticsFragmentBinding
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBReader.MonthlyStatisticsItem
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Displays the yearly statistics screen
 */
class YearsStatisticsFragment : Fragment() {
    private var _binding: StatisticsFragmentBinding? = null
    private val binding get() = _binding!!

//    private var disposable: Disposable? = null

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        
//        disposable?.dispose()
    }

    private fun procFlowEvents() {
        lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: $event")
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
//        disposable?.dispose()

//        disposable = Observable.fromCallable { DBReader.getMonthlyTimeStatistics() }
//            .subscribeOn(Schedulers.io())
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribe({ result: List<MonthlyStatisticsItem> ->
//                listAdapter.update(result)
//                progressBar.visibility = View.GONE
//                yearStatisticsList.visibility = View.VISIBLE
//            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })

        lifecycleScope.launch {
            try {
                val result: List<MonthlyStatisticsItem> = withContext(Dispatchers.IO) {
                    DBReader.getMonthlyTimeStatistics()
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

    companion object {
        private val TAG: String = YearsStatisticsFragment::class.java.simpleName
    }
}
