package ac.mdiq.podcini.ui.adapter

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.OnlinePodcastListitemBinding
import ac.mdiq.podcini.net.feed.discovery.PodcastSearchResult
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.util.MiscFormatter.formatNumber
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import coil.load

class OnlineFeedsAdapter(private val context: Context, objects: List<PodcastSearchResult>) : ArrayAdapter<PodcastSearchResult>(context, 0, objects) {

//    List holding the podcasts found in the search
    private val data: List<PodcastSearchResult> = objects

    @UnstableApi override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val podcast: PodcastSearchResult = data[position]
        val viewHolder: PodcastViewHolder
        val view: View

        //Handle view holder stuff
        if (convertView == null) {
            view = (context as MainActivity).layoutInflater.inflate(R.layout.online_podcast_listitem, parent, false)
            viewHolder = PodcastViewHolder(view)
            view.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as PodcastViewHolder
        }

        // Set the title
        viewHolder.binding.txtvTitle.text = podcast.title
        when {
            !podcast.author.isNullOrBlank() -> {
                viewHolder.authorView.text = podcast.author.trim { it <= ' ' }
                viewHolder.authorView.visibility = View.VISIBLE
            }
            podcast.feedUrl != null && !podcast.feedUrl.contains("itunes.apple.com") -> {
                viewHolder.authorView.text = podcast.feedUrl
                viewHolder.authorView.visibility = View.VISIBLE
            }
            else -> viewHolder.authorView.visibility = View.GONE
        }
        viewHolder.binding.source.text = podcast.source + ": " + podcast.feedUrl

        if (podcast.count != null && podcast.count > 0) {
            viewHolder.countView.text = podcast.count.toString() + " episodes"
            viewHolder.countView.visibility = View.VISIBLE
        } else viewHolder.countView.visibility = View.GONE

        if (podcast.subscriberCount > 0) {
            viewHolder.subscribersView.text = formatNumber(podcast.subscriberCount) + " subscribers"
            viewHolder.binding.subscribersGroup.visibility = View.VISIBLE
        } else viewHolder.binding.subscribersGroup.visibility = View.GONE

        if (podcast.update != null) {
            viewHolder.updateView.text = podcast.update
            viewHolder.updateView.visibility = View.VISIBLE
        } else viewHolder.updateView.visibility = View.GONE

        viewHolder.binding.imgvCover.load(podcast.imageUrl) {
            placeholder(R.color.light_gray)
            error(R.mipmap.ic_launcher)
        }
        return view
    }

    internal class PodcastViewHolder(view: View) {
        val binding = OnlinePodcastListitemBinding.bind(view)

        val authorView: TextView = binding.txtvAuthor
        val subscribersView: TextView = binding.subscribers
        val countView: TextView = binding.count
        val updateView: TextView = binding.update
    }
}
