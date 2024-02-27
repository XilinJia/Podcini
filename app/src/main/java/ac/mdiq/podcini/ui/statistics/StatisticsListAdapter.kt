package ac.mdiq.podcini.ui.statistics


import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.StatisticsListitemBinding
import ac.mdiq.podcini.databinding.StatisticsListitemTotalBinding
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
import ac.mdiq.podcini.storage.StatisticsItem
import ac.mdiq.podcini.ui.statistics.PieChartView.PieChartData

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
        val binding = StatisticsListitemTotalBinding.bind(itemView)
        var totalTime: TextView = binding.totalTime
        var pieChart: PieChartView = binding.pieChart
        var totalText: TextView = binding.totalDescription
    }

    class StatisticsHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val binding = StatisticsListitemBinding.bind(itemView)
        var image: ImageView = binding.imgvCover
        var title: TextView = binding.txtvTitle
        @JvmField
        var value: TextView = binding.txtvValue
        var chip: TextView = binding.chip
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
