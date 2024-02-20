package ac.mdiq.podcini.adapter

import ac.mdiq.podcini.activity.MainActivity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.discovery.PodcastSearchResult
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
            holder = Holder()
            holder.imageView = convertView.findViewById(R.id.discovery_cover)
            convertView.tag = holder
        } else {
            holder = convertView.tag as Holder
        }

        val podcast: PodcastSearchResult? = getItem(position)
        holder.imageView!!.contentDescription = podcast?.title

        Glide.with(mainActivityRef.get()!!)
            .load(podcast?.imageUrl)
            .apply(RequestOptions()
                .placeholder(R.color.light_gray)
                .transform(FitCenter(), RoundedCorners((8 * mainActivityRef.get()!!.resources.displayMetrics.density).toInt()))
                .dontAnimate())
            .into(holder.imageView!!)

        return convertView!!
    }

    internal class Holder {
        var imageView: ImageView? = null
    }
}
