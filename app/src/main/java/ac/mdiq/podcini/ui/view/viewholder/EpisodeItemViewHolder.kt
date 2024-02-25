package ac.mdiq.podcini.ui.view.viewholder

import ac.mdiq.podcini.ui.activity.MainActivity
import android.os.Build
import android.text.Layout
import android.text.format.Formatter
import android.util.Log
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
import com.joanzapata.iconify.Iconify
import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.adapter.CoverLoader
import ac.mdiq.podcini.feed.util.ImageResourceUtils
import ac.mdiq.podcini.util.DateFormatter
import ac.mdiq.podcini.util.NetworkUtils
import ac.mdiq.podcini.util.PlaybackStatus
import ac.mdiq.podcini.net.download.MediaSizeLoader
import ac.mdiq.podcini.playback.event.PlaybackPositionEvent
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.storage.model.playback.MediaType
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.ui.common.CircularProgressBar
import ac.mdiq.podcini.ui.common.ThemeUtils
import io.reactivex.functions.Consumer
import kotlin.math.max

/**
 * Holds the view which shows FeedItems.
 */
@UnstableApi
class EpisodeItemViewHolder(private val activity: MainActivity, parent: ViewGroup?) :
    RecyclerView.ViewHolder(LayoutInflater.from(activity).inflate(R.layout.feeditemlist_item, parent, false)) {

    private val container: View = itemView.findViewById(R.id.container)
    @JvmField
    val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)
    private val placeholder: TextView = itemView.findViewById(R.id.txtvPlaceholder)
    private val cover: ImageView = itemView.findViewById(R.id.imgvCover)
    private val title: TextView = itemView.findViewById(R.id.txtvTitle)
    private val pubDate: TextView
    private val position: TextView
    private val duration: TextView
    private val size: TextView
    val isInbox: ImageView
    @JvmField
    val isInQueue: ImageView
    private val isVideo: ImageView
    val isFavorite: ImageView
    private val progressBar: ProgressBar
    @JvmField
    val secondaryActionButton: View
    @JvmField
    val secondaryActionIcon: ImageView
    private val secondaryActionProgress: CircularProgressBar
    private val separatorIcons: TextView
    private val leftPadding: View
    @JvmField
    val coverHolder: CardView

    private var item: FeedItem? = null

    init {
        if (Build.VERSION.SDK_INT >= 23) {
            title.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
        }
        pubDate = itemView.findViewById(R.id.txtvPubDate)
        position = itemView.findViewById(R.id.txtvPosition)
        duration = itemView.findViewById(R.id.txtvDuration)
        progressBar = itemView.findViewById(R.id.progressBar)
        isInQueue = itemView.findViewById(R.id.ivInPlaylist)
        isVideo = itemView.findViewById(R.id.ivIsVideo)
        isInbox = itemView.findViewById(R.id.statusInbox)
        isFavorite = itemView.findViewById(R.id.isFavorite)
        size = itemView.findViewById(R.id.size)
        separatorIcons = itemView.findViewById(R.id.separatorIcons)
        secondaryActionProgress = itemView.findViewById(R.id.secondaryActionProgress)
        secondaryActionButton = itemView.findViewById(R.id.secondaryActionButton)
        secondaryActionIcon = itemView.findViewById(R.id.secondaryActionIcon)
        coverHolder = itemView.findViewById(R.id.coverHolder)
        leftPadding = itemView.findViewById(R.id.left_padding)
        itemView.tag = this
    }

    fun bind(item: FeedItem) {
        this.item = item
        placeholder.text = item.feed?.title
        title.text = item.title
        if (item.isPlayed()) {
            leftPadding.contentDescription = item.title + ". " + activity.getString(R.string.is_played)
        } else {
            leftPadding.contentDescription = item.title
        }
        pubDate.text = DateFormatter.formatAbbrev(activity, item.getPubDate())
        pubDate.setContentDescription(DateFormatter.formatForAccessibility(item.getPubDate()))
        isInbox.visibility = if (item.isNew) View.VISIBLE else View.GONE
        isFavorite.visibility = if (item.isTagged(FeedItem.TAG_FAVORITE)) View.VISIBLE else View.GONE
        isInQueue.visibility = if (item.isTagged(FeedItem.TAG_QUEUE)) View.VISIBLE else View.GONE
        container.alpha = if (item.isPlayed()) 0.5f else 1.0f

        val actionButton: ac.mdiq.podcini.ui.adapter.actionbutton.ItemActionButton = ac.mdiq.podcini.ui.adapter.actionbutton.ItemActionButton.forItem(item)
        actionButton.configure(secondaryActionButton, secondaryActionIcon, activity)
        secondaryActionButton.isFocusable = false

        if (item.media != null) {
            bind(item.media!!)
        } else {
            secondaryActionProgress.setPercentage(0f, item)
            secondaryActionProgress.setIndeterminate(false)
            isVideo.visibility = View.GONE
            progressBar.visibility = View.GONE
            duration.visibility = View.GONE
            position.visibility = View.GONE
            itemView.setBackgroundResource(ThemeUtils.getDrawableFromAttr(activity, R.attr.selectableItemBackground))
        }

        if (coverHolder.visibility == View.VISIBLE) {
            CoverLoader(activity)
                .withUri(ImageResourceUtils.getEpisodeListImageLocation(item))
                .withFallbackUri(item.feed?.imageUrl)
                .withPlaceholderView(placeholder)
                .withCoverView(cover)
                .load()
        }
    }

    private fun bind(media: FeedMedia) {
        isVideo.visibility = if (media.getMediaType() == MediaType.VIDEO) View.VISIBLE else View.GONE
        duration.visibility = if (media.getDuration() > 0) View.VISIBLE else View.GONE

        if (PlaybackStatus.isCurrentlyPlaying(media)) {
            val density: Float = activity.resources.displayMetrics.density
            itemView.setBackgroundColor(SurfaceColors.getColorForElevation(activity, 8 * density))
        } else {
            itemView.setBackgroundResource(ThemeUtils.getDrawableFromAttr(activity, R.attr.selectableItemBackground))
        }

        val dls = DownloadServiceInterface.get()
        if (media.download_url != null && dls?.isDownloadingEpisode(media.download_url!!) == true) {
            val percent: Float = 0.01f * dls.getProgress(media.download_url!!)
            secondaryActionProgress.setPercentage(max(percent.toDouble(), 0.01).toFloat(), item)
            secondaryActionProgress.setIndeterminate(dls.isEpisodeQueued(media.download_url!!))
        } else if (media.isDownloaded()) {
            secondaryActionProgress.setPercentage(1f, item) // Do not animate 100% -> 0%
            secondaryActionProgress.setIndeterminate(false)
        } else {
            secondaryActionProgress.setPercentage(0f, item) // Animate X% -> 0%
            secondaryActionProgress.setIndeterminate(false)
        }

        duration.text = ac.mdiq.podcini.util.Converter.getDurationStringLong(media.getDuration())
        duration.setContentDescription(activity.getString(R.string.chapter_duration,
            ac.mdiq.podcini.util.Converter.getDurationStringLocalized(activity, media.getDuration().toLong())))
        if (PlaybackStatus.isPlaying(item?.media) || item?.isInProgress == true) {
            val progress: Int = (100.0 * media.getPosition() / media.getDuration()).toInt()
            val remainingTime = max((media.getDuration() - media.getPosition()).toDouble(), 0.0).toInt()
            progressBar.progress = progress
            position.text = ac.mdiq.podcini.util.Converter.getDurationStringLong(media.getPosition())
            position.setContentDescription(activity.getString(R.string.position,
                ac.mdiq.podcini.util.Converter.getDurationStringLocalized(activity, media.getPosition().toLong())))
            progressBar.visibility = View.VISIBLE
            position.visibility = View.VISIBLE
            if (UserPreferences.shouldShowRemainingTime()) {
                duration.text = (if ((remainingTime > 0)) "-" else "") + ac.mdiq.podcini.util.Converter.getDurationStringLong(remainingTime)
                duration.setContentDescription(activity.getString(R.string.chapter_duration,
                    ac.mdiq.podcini.util.Converter.getDurationStringLocalized(activity, (media.getDuration() - media.getPosition()).toLong())))
            }
        } else {
            progressBar.visibility = View.GONE
            position.visibility = View.GONE
        }

        if (media.size > 0) {
            size.text = Formatter.formatShortFileSize(activity, media.size)
        } else if (NetworkUtils.isEpisodeHeadDownloadAllowed && !media.checkedOnSizeButUnknown()) {
            size.text = "{fa-spinner}"
            Iconify.addIcons(size)
            MediaSizeLoader.getFeedMediaSizeObservable(media).subscribe(
                Consumer<Long?> { sizeValue: Long? ->
                    if (sizeValue == null) return@Consumer
                    if (sizeValue > 0) {
                        size.text = Formatter.formatShortFileSize(activity, sizeValue)
                    } else {
                        size.text = ""
                    }
                }) { error: Throwable? ->
                size.text = ""
                Log.e(TAG, Log.getStackTraceString(error))
            }
        } else {
            size.text = ""
        }
    }

    fun bindDummy() {
        item = FeedItem()
        container.alpha = 0.1f
        secondaryActionIcon.setImageDrawable(null)
        isInbox.visibility = View.VISIBLE
        isVideo.visibility = View.GONE
        isFavorite.visibility = View.GONE
        isInQueue.visibility = View.GONE
        title.text = "███████"
        pubDate.text = "████"
        duration.text = "████"
        secondaryActionProgress.setPercentage(0f, null)
        secondaryActionProgress.setIndeterminate(false)
        progressBar.visibility = View.GONE
        position.visibility = View.GONE
        dragHandle.visibility = View.GONE
        size.text = ""
        itemView.setBackgroundResource(ThemeUtils.getDrawableFromAttr(activity, R.attr.selectableItemBackground))
        placeholder.text = ""
        if (coverHolder.visibility == View.VISIBLE) {
            CoverLoader(activity)
                .withResource(R.color.medium_gray)
                .withPlaceholderView(placeholder)
                .withCoverView(cover)
                .load()
        }
    }

    private fun updateDuration(event: PlaybackPositionEvent) {
        if (feedItem?.media != null) {
            feedItem!!.media!!.setPosition(event.position)
            feedItem!!.media!!.setDuration(event.duration)
        }
        val currentPosition: Int = event.position
        val timeDuration: Int = event.duration
        val remainingTime = max((timeDuration - currentPosition).toDouble(), 0.0).toInt()
        //        Log.d(TAG, "currentPosition " + Converter.getDurationStringLong(currentPosition));
        if (currentPosition == Playable.INVALID_TIME || timeDuration == Playable.INVALID_TIME) {
            Log.w(TAG, "Could not react to position observer update because of invalid time")
            return
        }
        if (UserPreferences.shouldShowRemainingTime()) {
            duration.text = (if (remainingTime > 0) "-" else "") + ac.mdiq.podcini.util.Converter.getDurationStringLong(remainingTime)
        } else {
            duration.text = ac.mdiq.podcini.util.Converter.getDurationStringLong(timeDuration)
        }
    }

    val feedItem: FeedItem?
        get() = item

    val isCurrentlyPlayingItem: Boolean
        get() = item?.media != null && PlaybackStatus.isCurrentlyPlaying(item?.media)

    fun notifyPlaybackPositionUpdated(event: PlaybackPositionEvent) {
        progressBar.progress = (100.0 * event.position / event.duration).toInt()
        position.text = ac.mdiq.podcini.util.Converter.getDurationStringLong(event.position)
        updateDuration(event)
        duration.visibility = View.VISIBLE // Even if the duration was previously unknown, it is now known
    }

    /**
     * Hides the separator dot between icons and text if there are no icons.
     */
    fun hideSeparatorIfNecessary() {
        val hasIcons = isInbox.visibility == View.VISIBLE ||
                isInQueue.visibility == View.VISIBLE ||
                isVideo.visibility == View.VISIBLE ||
                isFavorite.visibility == View.VISIBLE ||
                isInbox.visibility == View.VISIBLE
        separatorIcons.visibility = if (hasIcons) View.VISIBLE else View.GONE
    }

    companion object {
        private const val TAG = "EpisodeItemViewHolder"
    }
}
