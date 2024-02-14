package ac.mdiq.podvinci.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podvinci.R
import ac.mdiq.podvinci.core.service.playback.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podvinci.core.util.DateFormatter.formatAbbrev
import ac.mdiq.podvinci.core.util.NetworkUtils.isStreamingAllowed
import ac.mdiq.podvinci.core.util.playback.PlaybackServiceStarter
import ac.mdiq.podvinci.core.util.syndication.HtmlToPlainText
import ac.mdiq.podvinci.dialog.StreamingConfirmationDialog
import ac.mdiq.podvinci.model.feed.FeedItem
import ac.mdiq.podvinci.model.playback.MediaType
import ac.mdiq.podvinci.model.playback.Playable
import ac.mdiq.podvinci.model.playback.RemoteMedia
import java.lang.Boolean
import kotlin.Int

/**
 * List adapter for showing a list of FeedItems with their title and description.
 */
class FeedItemlistDescriptionAdapter(context: Context?, resource: Int, objects: List<FeedItem?>?) :
    ArrayAdapter<FeedItem?>(
        context!!, resource, objects!!) {
    @UnstableApi override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        val holder: Holder

        val item = getItem(position)

        // Inflate layout
        if (convertView == null) {
            holder = Holder()
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            convertView = inflater.inflate(R.layout.itemdescription_listitem, parent, false)
            holder.title = convertView.findViewById(R.id.txtvTitle)
            holder.pubDate = convertView.findViewById(R.id.txtvPubDate)
            holder.description = convertView.findViewById(R.id.txtvDescription)
            holder.preview = convertView.findViewById(R.id.butPreview)

            convertView.tag = holder
        } else {
            holder = convertView.tag as Holder
        }

        holder.title!!.text = item!!.title
        holder.pubDate!!.text = formatAbbrev(context, item.pubDate)
        if (item.description != null) {
            val description = HtmlToPlainText.getPlainText(item.description)
                ?.replace("\n".toRegex(), " ")
                ?.replace("\\s+".toRegex(), " ")
                ?.trim { it <= ' ' }
            holder.description!!.text = description
            holder.description!!.maxLines = MAX_LINES_COLLAPSED
        }
        holder.description!!.tag = Boolean.FALSE // not expanded
        holder.preview!!.visibility = View.GONE
        holder.preview!!.setOnClickListener { v: View? ->
            if (item.media == null) {
                return@setOnClickListener
            }
            val playable: Playable = RemoteMedia(item)
            if (!isStreamingAllowed) {
                StreamingConfirmationDialog(context, playable).show()
                return@setOnClickListener
            }

            PlaybackServiceStarter(context, playable)
                .callEvenIfRunning(true)
                .start()
            if (playable.getMediaType() == MediaType.VIDEO) {
                context.startActivity(getPlayerActivityIntent(context, playable))
            }
        }
        convertView!!.setOnClickListener { v: View? ->
            if (holder.description!!.tag === Boolean.TRUE) {
                holder.description!!.maxLines = MAX_LINES_COLLAPSED
                holder.preview!!.visibility = View.GONE
                holder.description!!.tag = Boolean.FALSE
            } else {
                holder.description!!.maxLines = 30
                holder.description!!.tag = Boolean.TRUE

                holder.preview!!.visibility = if (item.media != null) View.VISIBLE else View.GONE
                holder.preview!!.setText(R.string.preview_episode)
            }
        }
        return convertView
    }

    internal class Holder {
        var title: TextView? = null
        var pubDate: TextView? = null
        var description: TextView? = null
        var preview: Button? = null
    }

    companion object {
        private const val MAX_LINES_COLLAPSED = 2
    }
}
