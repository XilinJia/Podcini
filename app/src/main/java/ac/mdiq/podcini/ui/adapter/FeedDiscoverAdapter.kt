package ac.mdiq.podcini.ui.adapter

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.QuickFeedDiscoveryItemBinding
import ac.mdiq.podcini.net.discovery.PodcastSearchResult
import ac.mdiq.podcini.ui.activity.MainActivity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import coil.load
import java.lang.ref.WeakReference

class FeedDiscoverAdapter(mainActivity: MainActivity) : BaseAdapter() {
    private val mainActivityRef: WeakReference<MainActivity> = WeakReference<MainActivity>(mainActivity)
    private val data: MutableList<PodcastSearchResult> = ArrayList()

    fun updateData(newData: List<PodcastSearchResult>) {
        data.clear()
        data.addAll(newData)
        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return data.size
    }

    override fun getItem(position: Int): PodcastSearchResult? {
        return if (position in data.indices) data[position] else null
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        val holder: Holder

        if (convertView == null) {
            convertView = View.inflate(mainActivityRef.get(), R.layout.quick_feed_discovery_item, null)
            val binding = QuickFeedDiscoveryItemBinding.bind(convertView)
            holder = Holder()
            holder.imageView = binding.discoveryCover
            convertView.tag = holder
        } else holder = convertView.tag as Holder

        val podcast: PodcastSearchResult? = getItem(position)
        holder.imageView!!.contentDescription = podcast?.title

//        if (!podcast?.imageUrl.isNullOrBlank()) Glide.with(mainActivityRef.get()!!)
//            .load(podcast?.imageUrl)
//            .apply(RequestOptions()
//                .placeholder(R.color.light_gray)
//                .transform(FitCenter(), RoundedCorners((8 * mainActivityRef.get()!!.resources.displayMetrics.density).toInt()))
//                .dontAnimate())
//            .into(holder.imageView!!)

        holder.imageView?.load(podcast?.imageUrl) {
            placeholder(R.color.light_gray)
            error(R.mipmap.ic_launcher)
        }
        return convertView!!
    }

    internal class Holder {
        var imageView: ImageView? = null
    }
}
