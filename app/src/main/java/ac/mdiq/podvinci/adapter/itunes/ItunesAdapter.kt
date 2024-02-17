package ac.mdiq.podvinci.adapter.itunes

import ac.mdiq.podvinci.activity.MainActivity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.net.discovery.PodcastSearchResult
import androidx.media3.common.util.UnstableApi

class ItunesAdapter(
        /**
         * Related Context
         */
        private val context: Context, objects: List<PodcastSearchResult>
) : ArrayAdapter<PodcastSearchResult?>(context, 0, objects) {
    /**
     * List holding the podcasts found in the search
     */
    private val data: List<PodcastSearchResult> = objects

    @UnstableApi override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        //Current podcast
        val podcast: PodcastSearchResult = data[position]

        //ViewHolder
        val viewHolder: PodcastViewHolder

        //Resulting view
        val view: View

        //Handle view holder stuff
        if (convertView == null) {
            view = (context as MainActivity).layoutInflater.inflate(R.layout.itunes_podcast_listitem, parent, false)
            viewHolder = PodcastViewHolder(view)
            view.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as PodcastViewHolder
        }

        // Set the title
        viewHolder.titleView.text = podcast.title
        if (podcast.author != null && podcast.author!!.trim { it <= ' ' }.isNotEmpty()) {
            viewHolder.authorView.text = podcast.author
            viewHolder.authorView.visibility = View.VISIBLE
        } else if (podcast.feedUrl != null && !podcast.feedUrl!!.contains("itunes.apple.com")) {
            viewHolder.authorView.text = podcast.feedUrl
            viewHolder.authorView.visibility = View.VISIBLE
        } else {
            viewHolder.authorView.visibility = View.GONE
        }

        //Update the empty imageView with the image from the feed
        Glide.with(context)
            .load(podcast.imageUrl)
            .apply(RequestOptions()
                .placeholder(R.color.light_gray)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .transform(FitCenter(),
                    RoundedCorners((4 * context.resources.displayMetrics.density).toInt()))
                .dontAnimate())
            .into(viewHolder.coverView)

        //Feed the grid view
        return view
    }

    /**
     * View holder object for the GridView
     */
    internal class PodcastViewHolder(view: View) {
        /**
         * ImageView holding the Podcast image
         */
        val coverView: ImageView = view.findViewById(R.id.imgvCover)

        /**
         * TextView holding the Podcast title
         */
        val titleView: TextView = view.findViewById(R.id.txtvTitle)

        val authorView: TextView = view.findViewById(R.id.txtvAuthor)
    }
}
