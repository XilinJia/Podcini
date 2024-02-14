package de.danoeh.antennapod.adapter

import android.content.Context
import android.text.TextUtils
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
import de.danoeh.antennapod.R
import de.danoeh.antennapod.adapter.ChaptersListAdapter.ChapterHolder
import de.danoeh.antennapod.core.util.Converter.getDurationStringLocalized
import de.danoeh.antennapod.core.util.Converter.getDurationStringLong
import de.danoeh.antennapod.core.util.IntentUtils.openInBrowser
import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.model.feed.EmbeddedChapterImage
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.ui.common.CircularProgressBar
import kotlin.math.max
import kotlin.math.min

class ChaptersListAdapter(private val context: Context, private val callback: Callback?) :
    RecyclerView.Adapter<ChapterHolder?>() {
    private var media: Playable? = null
    private var currentChapterIndex = -1
    private var currentChapterPosition: Long = -1
    private var hasImages = false

    fun setMedia(media: Playable) {
        this.media = media
        hasImages = false
        for (chapter in media.getChapters()) {
            if (!TextUtils.isEmpty(chapter.imageUrl)) {
                hasImages = true
            }
        }
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: ChapterHolder, position: Int) {
        val sc = getItem(position)
        if (sc == null) {
            holder.title.text = "Error"
            return
        }
        holder.title.text = sc.title
        holder.start.text = getDurationStringLong(sc
            .start.toInt())
        val duration = if (position + 1 < media!!.getChapters().size) {
            media!!.getChapters()[position + 1].start - sc.start
        } else {
            media!!.getDuration() - sc.start
        }
        holder.duration.text = context.getString(R.string.chapter_duration,
            getDurationStringLocalized(context, duration.toInt().toLong()))

        if (TextUtils.isEmpty(sc.link)) {
            holder.link.visibility = View.GONE
        } else {
            holder.link.visibility = View.VISIBLE
            holder.link.text = sc.link
            holder.link.setOnClickListener { v: View? ->
                if (sc.link!=null) openInBrowser(context, sc.link!!)
            }
        }
        holder.secondaryActionIcon.setImageResource(R.drawable.ic_play_48dp)
        holder.secondaryActionButton.contentDescription = context.getString(R.string.play_chapter)
        holder.secondaryActionButton.setOnClickListener { v: View? ->
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
            if (TextUtils.isEmpty(sc.imageUrl)) {
                Glide.with(context).clear(holder.image)
            } else {
                if (media != null) Glide.with(context)
                    .load(EmbeddedChapterImage.getModelFor(media!!, position))
                    .apply(RequestOptions()
                        .dontAnimate()
                        .transform(FitCenter(), RoundedCorners((4 * context.resources.displayMetrics.density).toInt())))
                    .into(holder.image)
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
        if (media == null) {
            return 0
        }
        return media!!.getChapters().size
    }

    class ChapterHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.txtvTitle)
        val start: TextView = itemView.findViewById(R.id.txtvStart)
        val link: TextView = itemView.findViewById(R.id.txtvLink)
        val duration: TextView = itemView.findViewById(R.id.txtvDuration)
        val image: ImageView = itemView.findViewById(R.id.imgvCover)
        val secondaryActionButton: View = itemView.findViewById(R.id.secondaryActionButton)
        val secondaryActionIcon: ImageView = itemView.findViewById(R.id.secondaryActionIcon)
        val progressBar: CircularProgressBar = itemView.findViewById(R.id.secondaryActionProgress)
    }

    fun notifyChapterChanged(newChapterIndex: Int) {
        currentChapterIndex = newChapterIndex
        currentChapterPosition = getItem(newChapterIndex).start
        notifyDataSetChanged()
    }

    fun notifyTimeChanged(timeMs: Long) {
        currentChapterPosition = timeMs
        // Passing an argument prevents flickering.
        // See EpisodeItemListAdapter.notifyItemChangedCompat.
        notifyItemChanged(currentChapterIndex, "foo")
    }

    fun getItem(position: Int): Chapter {
        return media!!.getChapters()[position]
    }

    interface Callback {
        fun onPlayChapterButtonClicked(position: Int)
    }
}
