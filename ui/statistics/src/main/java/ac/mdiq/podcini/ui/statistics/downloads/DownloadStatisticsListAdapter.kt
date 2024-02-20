package ac.mdiq.podcini.ui.statistics.downloads

import android.annotation.SuppressLint
import android.content.Context
import android.text.format.Formatter
import android.view.View
import androidx.fragment.app.Fragment
import ac.mdiq.podcini.core.storage.StatisticsItem
import ac.mdiq.podcini.ui.statistics.PieChartView.PieChartData
import ac.mdiq.podcini.ui.statistics.R
import ac.mdiq.podcini.ui.statistics.StatisticsListAdapter
import ac.mdiq.podcini.ui.statistics.feed.FeedStatisticsDialogFragment
import java.util.*

/**
 * Adapter for the download statistics list.
 */
class DownloadStatisticsListAdapter(context: Context?, private val fragment: Fragment) : StatisticsListAdapter(
    context!!) {
    override val headerCaption: String
        get() = context.getString(R.string.total_size_downloaded_podcasts)

    override val headerValue: String
        get() = Formatter.formatShortFileSize(context, pieChartData!!.sum.toLong())

    override fun generateChartData(statisticsData: List<StatisticsItem>?): PieChartData? {
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
