package de.danoeh.antennapod.ui.statistics

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import de.danoeh.antennapod.core.storage.StatisticsItem
import de.danoeh.antennapod.ui.statistics.PieChartView.PieChartData

/**
 * Parent Adapter for the playback and download statistics list.
 */
abstract class StatisticsListAdapter protected constructor(@JvmField protected val context: Context) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var statisticsData: List<StatisticsItem>? = null
    @JvmField
    protected var pieChartData: PieChartData? = null

    override fun getItemCount(): Int {
        return statisticsData!!.size + 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) TYPE_HEADER else TYPE_FEED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(context)
        if (viewType == TYPE_HEADER) {
            return HeaderHolder(inflater.inflate(R.layout.statistics_listitem_total, parent, false))
        }
        return StatisticsHolder(inflater.inflate(R.layout.statistics_listitem, parent, false))
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == TYPE_HEADER) {
            val holder = h as HeaderHolder
            holder.pieChart.setData(pieChartData)
            holder.totalTime.text = headerValue
            holder.totalText.text = headerCaption
        } else {
            val holder = h as StatisticsHolder
            val statsItem = statisticsData!![position - 1]
            Glide.with(context)
                .load(statsItem.feed.imageUrl)
                .apply(RequestOptions()
                    .placeholder(R.color.light_gray)
                    .error(R.color.light_gray)
                    .fitCenter()
                    .dontAnimate())
                .into(holder.image)

            holder.title.text = statsItem.feed.title
            holder.chip.setTextColor(pieChartData!!.getColorOfItem(position - 1))
            onBindFeedViewHolder(holder, statsItem)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun update(statistics: List<StatisticsItem>?) {
        statisticsData = statistics
        pieChartData = generateChartData(statistics)
        notifyDataSetChanged()
    }

    internal class HeaderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var totalTime: TextView = itemView.findViewById(R.id.total_time)
        var pieChart: PieChartView = itemView.findViewById(R.id.pie_chart)
        var totalText: TextView = itemView.findViewById(R.id.total_description)
    }

    class StatisticsHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var image: ImageView = itemView.findViewById(R.id.imgvCover)
        var title: TextView = itemView.findViewById(R.id.txtvTitle)
        @JvmField
        var value: TextView = itemView.findViewById(R.id.txtvValue)
        var chip: TextView = itemView.findViewById(R.id.chip)
    }

    protected abstract val headerCaption: String?

    protected abstract val headerValue: String?

    protected abstract fun generateChartData(statisticsData: List<StatisticsItem>?): PieChartData?

    protected abstract fun onBindFeedViewHolder(holder: StatisticsHolder?, item: StatisticsItem?)

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_FEED = 1
    }
}
