package ac.mdiq.podcini.ui.statistics.years


import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.StatisticsFragmentBinding
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBReader.MonthlyStatisticsItem
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
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

/**
 * Displays the yearly statistics screen
 */
class YearsStatisticsFragment : Fragment() {
    private var disposable: Disposable? = null
    private var yearStatisticsList: RecyclerView? = null
    private var progressBar: ProgressBar? = null
    private var listAdapter: YearStatisticsListAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View {
        val binding = StatisticsFragmentBinding.inflate(inflater)
//        val root = inflater.inflate(R.layout.statistics_fragment, container, false)
        yearStatisticsList = binding.statisticsList
        progressBar = binding.progressBar
        listAdapter = YearStatisticsListAdapter(requireContext())
        yearStatisticsList?.layoutManager = LinearLayoutManager(context)
        yearStatisticsList?.adapter = listAdapter
        EventBus.getDefault().register(this)
        refreshStatistics()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        EventBus.getDefault().unregister(this)
        disposable?.dispose()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun statisticsEvent(event: ac.mdiq.podcini.util.event.StatisticsEvent?) {
        refreshStatistics()
    }

    @Deprecated("Deprecated in Java")
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.statistics_reset).setVisible(true)
        menu.findItem(R.id.statistics_filter).setVisible(false)
    }

    private fun refreshStatistics() {
        progressBar!!.visibility = View.VISIBLE
        yearStatisticsList!!.visibility = View.GONE
        loadStatistics()
    }

    private fun loadStatistics() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Observable.fromCallable { DBReader.getMonthlyTimeStatistics() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result: List<MonthlyStatisticsItem> ->
                listAdapter!!.update(result)
                progressBar!!.visibility = View.GONE
                yearStatisticsList!!.visibility = View.VISIBLE
            }, { error: Throwable? -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    companion object {
        private val TAG: String = YearsStatisticsFragment::class.java.simpleName
    }
}
