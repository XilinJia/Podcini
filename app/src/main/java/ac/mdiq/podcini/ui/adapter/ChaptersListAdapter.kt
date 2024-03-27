package ac.mdiq.podcini.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.elevation.SurfaceColors
import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.SimplechapterItemBinding
import ac.mdiq.podcini.ui.adapter.ChaptersListAdapter.ChapterHolder
import ac.mdiq.podcini.util.Converter.getDurationStringLocalized
import ac.mdiq.podcini.util.Converter.getDurationStringLong
import ac.mdiq.podcini.util.IntentUtils.openInBrowser
import ac.mdiq.podcini.storage.model.feed.Chapter
import ac.mdiq.podcini.storage.model.feed.EmbeddedChapterImage
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.ui.common.CircularProgressBar
import kotlin.math.max
import kotlin.math.min

class ChaptersListAdapter(private val context: Context, private val callback: Callback?) : RecyclerView.Adapter<ChapterHolder>() {

    private var media: Playable? = null
    private var currentChapterIndex = -1
    private var currentChapterPosition: Long = -1
    private var hasImages = false

    fun setMedia(media: Playable) {
        this.media = media
        hasImages = false
        for (chapter in media.getChapters()) {
            if (!chapter.imageUrl.isNullOrEmpty()) {
                hasImages = true
            }
        }
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: ChapterHolder, position: Int) {
        val sc = getItem(position)?: return
        holder.title.text = sc.title
        holder.start.text = getDurationStringLong(sc.start.toInt())
        val duration = if (position + 1 < itemCount) {
            media!!.getChapters()[position + 1].start - sc.start
        } else {
            (media?.getDuration()?:0) - sc.start
        }
        holder.duration.text = context.getString(R.string.chapter_duration,
            getDurationStringLocalized(context, duration.toInt().toLong()))

        if (sc.link.isNullOrEmpty()) {
            holder.link.visibility = View.GONE
        } else {
            holder.link.visibility = View.VISIBLE
            holder.link.text = sc.link
            holder.link.setOnClickListener {
                if (sc.link!=null) openInBrowser(context, sc.link!!)
            }
        }
        holder.secondaryActionIcon.setImageResource(R.drawable.ic_play_48dp)
        holder.secondaryActionButton.contentDescription = context.getString(R.string.play_chapter)
        holder.secondaryActionButton.setOnClickListener {
            callback?.onPlayChapterButtonClicked(position)
        }

        if (position == currentChapterIndex) {
            val density = context.resources.displayMetrics.density
            holder.itemView.setBackgroundColor(SurfaceColors.getColorForElevation(context, 32 * density))
            var progress = ((currentChapterPosition - sc.start).toFloat()) / duration
            progress = max(progress.toDouble(), CircularProgressBar.MINIMUM_PERCENTAGE.toDouble()).toFloat()
            progress = min(progress.toDouble(), CircularProgressBar.MAXIMUM_PERCENTAGE.toDouble()).toFloat()
            holder.progressBar.setPercentage(progress, position)
            holder.secondaryActionIcon.setImageResource(R.drawable.ic_replay)
        } else {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
            holder.progressBar.setPercentage(0f, null)
        }

        if (hasImages) {
            holder.image.visibility = View.VISIBLE
            if (sc.imageUrl.isNullOrEmpty()) {
                Glide.with(context).clear(holder.image)
            } else {
                if (media != null) {
                    val imgUrl = EmbeddedChapterImage.getModelFor(media!!,position)
                    if (imgUrl != null) Glide.with(context)
                        .load(imgUrl)
                        .apply(RequestOptions()
                            .dontAnimate()
                            .transform(FitCenter(), RoundedCorners((4 * context.resources.displayMetrics.density).toInt())))
                        .into(holder.image)
                }
            }
        } else {
            holder.image.visibility = View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterHolder {
        val inflater = LayoutInflater.from(context)
        return ChapterHolder(inflater.inflate(R.layout.simplechapter_item, parent, false))
    }

    override fun getItemCount(): Int {
        return media?.getChapters()?.size?:0
    }

    class ChapterHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val binding: SimplechapterItemBinding = SimplechapterItemBinding.bind(itemView)
        val title: TextView = binding.txtvTitle
        val start: TextView = binding.txtvStart
        val link: TextView = binding.txtvLink
        val duration: TextView = binding.txtvDuration
        val image: ImageView = binding.imgvCover
        val secondaryActionButton: View = binding.secondaryActionLayout.secondaryAction
        val secondaryActionIcon: ImageView = binding.secondaryActionLayout.secondaryActionIcon
        val progressBar: CircularProgressBar = binding.secondaryActionLayout.secondaryActionProgress
    }

    fun notifyChapterChanged(newChapterIndex: Int) {
        currentChapterIndex = newChapterIndex
        currentChapterPosition = getItem(newChapterIndex)?.start?:0
        notifyDataSetChanged()
    }

    fun notifyTimeChanged(timeMs: Long) {
        currentChapterPosition = timeMs
        // Passing an argument prevents flickering.
        // See EpisodeItemListAdapter.notifyItemChangedCompat.
        notifyItemChanged(currentChapterIndex, "foo")
    }

    fun getItem(position: Int): Chapter? {
        val chapters = media?.getChapters()?: return null
        if (position < 0 || position >= chapters.size) return null
        return chapters[position]
    }

    interface Callback {
        fun onPlayChapterButtonClicked(position: Int)
    }
}
