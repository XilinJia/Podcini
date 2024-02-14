package de.danoeh.antennapod.ui.statistics.subscriptions

import android.text.format.DateFormat
import android.view.View
import androidx.fragment.app.Fragment
import de.danoeh.antennapod.core.storage.StatisticsItem
import de.danoeh.antennapod.core.util.Converter.shortLocalizedDuration
import de.danoeh.antennapod.ui.statistics.PieChartView.PieChartData
import de.danoeh.antennapod.ui.statistics.R
import de.danoeh.antennapod.ui.statistics.StatisticsListAdapter
import de.danoeh.antennapod.ui.statistics.feed.FeedStatisticsDialogFragment.Companion.newInstance
import java.text.SimpleDateFormat
import java.util.*

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

    override fun generateChartData(statisticsData: List<StatisticsItem>?): PieChartData? {
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
