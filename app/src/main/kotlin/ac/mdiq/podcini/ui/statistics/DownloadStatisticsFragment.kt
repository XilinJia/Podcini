package ac.mdiq.podcini.ui.statistics


import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.StatisticsFragmentBinding
import ac.mdiq.podcini.storage.database.LogsAndStats.getStatistics
import ac.mdiq.podcini.storage.model.StatisticsItem
import ac.mdiq.podcini.ui.statistics.PieChartView.PieChartData
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.format.Formatter
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Displays the 'download statistics' screen
 */
class DownloadStatisticsFragment : Fragment() {

    private var _binding: StatisticsFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var downloadStatisticsList: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var listAdapter: DownloadStatisticsListAdapter


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = StatisticsFragmentBinding.inflate(inflater)
        downloadStatisticsList = binding.statisticsList
        progressBar = binding.progressBar
        listAdapter = DownloadStatisticsListAdapter(requireContext(), this)
        downloadStatisticsList.layoutManager = LinearLayoutManager(context)
        downloadStatisticsList.adapter = listAdapter
        refreshDownloadStatistics()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Deprecated("Deprecated in Java")
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.statistics_reset).setVisible(false)
        menu.findItem(R.id.statistics_filter).setVisible(false)
    }

    private fun refreshDownloadStatistics() {
        progressBar.visibility = View.VISIBLE
        downloadStatisticsList.visibility = View.GONE
        loadStatistics()
    }

    private fun loadStatistics() {
        lifecycleScope.launch {
            try {
                val statisticsData = withContext(Dispatchers.IO) {
                    val data = getStatistics(false, 0, Long.MAX_VALUE)
                    data.feedTime.sortWith { item1: StatisticsItem, item2: StatisticsItem ->
                        item2.totalDownloadSize.compareTo(item1.totalDownloadSize)
                    }
                    data
                }
                listAdapter.update(statisticsData.feedTime)
                progressBar.visibility = View.GONE
                downloadStatisticsList.visibility = View.VISIBLE
            } catch (error: Throwable) {
                Log.e(TAG, Log.getStackTraceString(error))
            }
        }
    }

    /**
     * Adapter for the download statistics list.
     */
    class DownloadStatisticsListAdapter(context: Context, private val fragment: Fragment) : StatisticsListAdapter(context!!) {
        override val headerCaption: String
            get() = context.getString(R.string.total_size_downloaded_podcasts)

        override val headerValue: String
            get() = Formatter.formatShortFileSize(context, pieChartData!!.sum.toLong())

        override fun generateChartData(statisticsData: List<StatisticsItem>?): PieChartData {
            val dataValues = FloatArray(statisticsData!!.size)
            for (i in statisticsData.indices) {
                val item = statisticsData[i]
                dataValues[i] = item.totalDownloadSize.toFloat()
            }
            return PieChartData(dataValues)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindFeedViewHolder(holder: StatisticsHolder?, item: StatisticsItem?) {
            holder!!.value.text = (Formatter.formatShortFileSize(context, item!!.totalDownloadSize)
                    + " • "
                    + String.format(Locale.getDefault(), "%d%s",
                item.episodesDownloadCount, context.getString(R.string.episodes_suffix)))

            holder.itemView.setOnClickListener { v: View? ->
                val yourDialogFragment = FeedStatisticsDialogFragment.newInstance(
                    item.feed.id, item.feed.title)
                yourDialogFragment.show(fragment.childFragmentManager.beginTransaction(), "DialogFragment")
            }
        }
    }

    companion object {
        private val TAG: String = DownloadStatisticsFragment::class.simpleName ?: "Anonymous"
    }
}
