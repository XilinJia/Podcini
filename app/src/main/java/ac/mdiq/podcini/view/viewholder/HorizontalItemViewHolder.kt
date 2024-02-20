package ac.mdiq.podcini.view.viewholder

import ac.mdiq.podcini.activity.MainActivity
import android.R.color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.elevation.SurfaceColors
import ac.mdiq.podcini.R
import ac.mdiq.podcini.adapter.CoverLoader
import ac.mdiq.podcini.adapter.actionbutton.ItemActionButton
import ac.mdiq.podcini.core.feed.util.ImageResourceUtils
import ac.mdiq.podcini.core.util.DateFormatter
import ac.mdiq.podcini.core.util.PlaybackStatus
import ac.mdiq.podcini.event.playback.PlaybackPositionEvent
import ac.mdiq.podcini.model.feed.FeedItem
import ac.mdiq.podcini.model.feed.FeedMedia
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.ui.common.CircularProgressBar
import ac.mdiq.podcini.ui.common.SquareImageView
import ac.mdiq.podcini.ui.common.ThemeUtils
import kotlin.math.max

class HorizontalItemViewHolder(private val activity: MainActivity, parent: ViewGroup?) :
    RecyclerView.ViewHolder(LayoutInflater.from(activity).inflate(R.layout.horizontal_itemlist_item, parent, false)) {

    @JvmField
    val card: CardView = itemView.findViewById(R.id.card)

    @JvmField
    val secondaryActionIcon: ImageView = itemView.findViewById(R.id.secondaryActionIcon)
    private val cover: SquareImageView = itemView.findViewById(R.id.cover)
    private val title: TextView = itemView.findViewById(R.id.titleLabel)
    private val date: TextView = itemView.findViewById(R.id.dateLabel)
    private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
    private val circularProgressBar: CircularProgressBar = itemView.findViewById(R.id.circularProgressBar)
    private val progressBarReplacementSpacer: View = itemView.findViewById(R.id.progressBarReplacementSpacer)

    private var item: FeedItem? = null

    init {
        itemView.tag = this
    }

    @UnstableApi fun bind(item: FeedItem) {
        this.item = item

        card.setAlpha(1.0f)
        val density: Float = activity.resources.displayMetrics.density
        card.setCardBackgroundColor(SurfaceColors.getColorForElevation(activity, 1 * density))
        CoverLoader(activity)
            .withUri(ImageResourceUtils.getEpisodeListImageLocation(item))
            .withFallbackUri(item.feed?.imageUrl)
            .withCoverView(cover)
            .load()
        title.text = item.title
        date.text = DateFormatter.formatAbbrev(activity, item.getPubDate())
        date.setContentDescription(DateFormatter.formatForAccessibility(item.getPubDate()))
        val actionButton: ItemActionButton = ItemActionButton.forItem(item)
        actionButton.configure(secondaryActionIcon, secondaryActionIcon, activity)
        secondaryActionIcon.isFocusable = false

        val media: FeedMedia? = item.media
        if (media == null) {
            circularProgressBar.setPercentage(0f, item)
            setProgressBar(false, 0f)
        } else {
            if (PlaybackStatus.isCurrentlyPlaying(media)) {
                card.setCardBackgroundColor(ThemeUtils.getColorFromAttr(activity, R.attr.colorSurfaceVariant))
            }

            if (media.getDuration() > 0 && media.getPosition() > 0) {
                setProgressBar(true, 100.0f * media.getPosition() / media.getDuration())
            } else {
                setProgressBar(false, 0f)
            }

            val dls = DownloadServiceInterface.get()
            if (media.download_url != null && dls?.isDownloadingEpisode(media.download_url!!) == true) {
                val percent: Float = 0.01f * dls.getProgress(media.download_url!!)
                circularProgressBar.setPercentage(max(percent.toDouble(), 0.01).toFloat(), item)
                circularProgressBar.setIndeterminate(dls.isEpisodeQueued(media.download_url!!))
            } else if (media.isDownloaded()) {
                circularProgressBar.setPercentage(1f, item) // Do not animate 100% -> 0%
                circularProgressBar.setIndeterminate(false)
            } else {
                circularProgressBar.setPercentage(0f, item) // Animate X% -> 0%
                circularProgressBar.setIndeterminate(false)
            }
        }
    }

    fun bindDummy() {
        card.setAlpha(0.1f)
        CoverLoader(activity)
            .withResource(color.transparent)
            .withCoverView(cover)
            .load()
        title.text = "████ █████"
        date.text = "███"
        secondaryActionIcon.setImageDrawable(null)
        circularProgressBar.setPercentage(0f, null)
        circularProgressBar.setIndeterminate(false)
        setProgressBar(true, 50f)
    }

    val isCurrentlyPlayingItem: Boolean
        @UnstableApi get() = item?.media != null && PlaybackStatus.isCurrentlyPlaying(item!!.media)

    fun notifyPlaybackPositionUpdated(event: PlaybackPositionEvent) {
        setProgressBar(true, 100.0f * event.position / event.duration)
    }

    private fun setProgressBar(visible: Boolean, progress: Float) {
        progressBar.visibility = if (visible) ViewGroup.VISIBLE else ViewGroup.GONE
        progressBarReplacementSpacer.visibility = if (visible) View.GONE else ViewGroup.VISIBLE
        progressBar.progress = max(5.0, progress.toDouble()).toInt() // otherwise invisible below the edge radius
    }
}
