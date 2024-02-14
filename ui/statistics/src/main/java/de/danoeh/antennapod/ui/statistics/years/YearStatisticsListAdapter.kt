package de.danoeh.antennapod.ui.statistics.years

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.danoeh.antennapod.core.storage.DBReader.MonthlyStatisticsItem
import de.danoeh.antennapod.ui.statistics.R
import java.util.*

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
        var barChart: BarChartView = itemView.findViewById(R.id.barChart)
    }

    internal class StatisticsHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var year: TextView = itemView.findViewById(R.id.yearLabel)
        var hours: TextView = itemView.findViewById(R.id.hoursLabel)
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_FEED = 1
    }
}
