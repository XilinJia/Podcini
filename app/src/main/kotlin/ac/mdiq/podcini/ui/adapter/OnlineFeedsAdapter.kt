package ac.mdiq.podcini.ui.adapter

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.OnlinePodcastListitemBinding
import ac.mdiq.podcini.net.feed.discovery.PodcastSearchResult
import ac.mdiq.podcini.ui.activity.MainActivity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import coil.load

class OnlineFeedsAdapter(private val context: Context, objects: List<PodcastSearchResult>)
    : ArrayAdapter<PodcastSearchResult?>(context, 0, objects) {

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
        viewHolder.titleView.text = podcast.title
        when {
            !podcast.author.isNullOrBlank() -> {
                viewHolder.authorView.text = podcast.author.trim { it <= ' ' }
                viewHolder.authorView.visibility = View.VISIBLE
            }
            podcast.feedUrl != null && !podcast.feedUrl.contains("itunes.apple.com") -> {
                viewHolder.authorView.text = podcast.feedUrl
                viewHolder.authorView.visibility = View.VISIBLE
            }
            else -> viewHolder.authorView.visibility = View.INVISIBLE
        }
        viewHolder.source.text = podcast.source + ": " + podcast.feedUrl
        if (podcast.count != null) {
            viewHolder.countView.text = podcast.count.toString() + " episodes"
            viewHolder.countView.visibility = View.VISIBLE
        } else viewHolder.countView.visibility = View.INVISIBLE

        if (podcast.update != null) {
            viewHolder.updateView.text = podcast.update
            viewHolder.updateView.visibility = View.VISIBLE
        } else viewHolder.updateView.visibility = View.INVISIBLE

        //Update the empty imageView with the image from the feed
//        if (!podcast.imageUrl.isNullOrBlank()) Glide.with(context)
//            .load(podcast.imageUrl)
//            .apply(RequestOptions()
//                .placeholder(R.color.light_gray)
//                .diskCacheStrategy(DiskCacheStrategy.NONE)
//                .transform(FitCenter(),
//                    RoundedCorners((4 * context.resources.displayMetrics.density).toInt()))
//                .dontAnimate())
//            .into(viewHolder.coverView)

        viewHolder.coverView.load(podcast.imageUrl) {
            placeholder(R.color.light_gray)
            error(R.mipmap.ic_launcher)
        }
        return view
    }

    internal class PodcastViewHolder(view: View) {
        val binding = OnlinePodcastListitemBinding.bind(view)

        val coverView: ImageView = binding.imgvCover

        val titleView: TextView = binding.txtvTitle

        val authorView: TextView = binding.txtvAuthor

        val countView: TextView = binding.count

        val updateView: TextView = binding.update

        val source: TextView = binding.source
    }
}
