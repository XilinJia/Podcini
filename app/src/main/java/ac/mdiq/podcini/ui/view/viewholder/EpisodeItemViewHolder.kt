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
import ac.mdiq.podcini.databinding.FeeditemlistItemBinding
import ac.mdiq.podcini.ui.adapter.CoverLoader
import ac.mdiq.podcini.feed.util.ImageResourceUtils
import ac.mdiq.podcini.util.DateFormatter
import ac.mdiq.podcini.util.NetworkUtils
import ac.mdiq.podcini.util.PlaybackStatus
import ac.mdiq.podcini.net.download.MediaSizeLoader
import ac.mdiq.podcini.util.event.playback.PlaybackPositionEvent
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.storage.model.playback.MediaType
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.model.feed.Feed.Companion.PREFIX_GENERATIVE_COVER
import ac.mdiq.podcini.storage.model.feed.FeedItem.Companion.BUILDING
import ac.mdiq.podcini.ui.actions.actionbutton.ItemActionButton
import ac.mdiq.podcini.ui.actions.actionbutton.TTSActionButton
import ac.mdiq.podcini.ui.view.CircularProgressBar
import ac.mdiq.podcini.ui.utils.ThemeUtils
import ac.mdiq.podcini.util.Converter
import android.widget.LinearLayout
import io.reactivex.functions.Consumer
import kotlin.math.max

/**
 * Holds the view which shows FeedItems.
 */
@UnstableApi
class EpisodeItemViewHolder(private val activity: MainActivity, parent: ViewGroup?) :
    RecyclerView.ViewHolder(LayoutInflater.from(activity).inflate(R.layout.feeditemlist_item, parent, false)) {

    val binding: FeeditemlistItemBinding = FeeditemlistItemBinding.bind(itemView)

    private val container: View = binding.container
    @JvmField
    val dragHandle: ImageView = binding.dragHandle
    private val placeholder: TextView = binding.txtvPlaceholder
    private val cover: ImageView = binding.imgvCover
    private val title: TextView = binding.txtvTitle
    private val pubDate: TextView = binding.txtvPubDate
    private val position: TextView = binding.txtvPosition
    private val duration: TextView = binding.txtvDuration
    private val size: TextView = binding.size
    @JvmField
    val isInQueue: ImageView = binding.ivInPlaylist
    private val isVideo: ImageView = binding.ivIsVideo
    private val isFavorite: ImageView = binding.isFavorite
    private val progressBar: ProgressBar = binding.progressBar

    private var actionButton: ItemActionButton? = null

    @JvmField
    val secondaryActionButton: View = binding.secondaryActionButton.root
    @JvmField
    val secondaryActionIcon: ImageView = binding.secondaryActionButton.secondaryActionIcon
    private val secondaryActionProgress: CircularProgressBar = binding.secondaryActionButton.secondaryActionProgress
    private val separatorIcons: TextView = binding.separatorIcons
    private val leftPadding: View = binding.leftPadding
    @JvmField
    val coverHolder: CardView = binding.coverHolder
    @JvmField
    val infoCard: LinearLayout = binding.infoCard

    private var item: FeedItem? = null

    init {
        title.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
        itemView.tag = this
    }

    fun bind(item: FeedItem) {
        this.item = item
        placeholder.text = item.feed?.title
        title.text = item.title
        container.alpha = if (item.isPlayed()) 0.75f else 1.0f
        if (item.isPlayed()) {
            leftPadding.contentDescription = item.title + ". " + activity.getString(R.string.is_played)
            binding.playedMark.visibility = View.VISIBLE
            binding.playedMark.alpha = 1.0f
        } else {
            leftPadding.contentDescription = item.title
            binding.playedMark.visibility = View.GONE
        }
        pubDate.text = DateFormatter.formatAbbrev(activity, item.getPubDate())
        pubDate.setContentDescription(DateFormatter.formatForAccessibility(item.getPubDate()))
        isFavorite.visibility = if (item.isTagged(FeedItem.TAG_FAVORITE)) View.VISIBLE else View.GONE
        isInQueue.visibility = if (item.isTagged(FeedItem.TAG_QUEUE)) View.VISIBLE else View.GONE
        container.alpha = if (item.isPlayed()) 0.75f else 1.0f

        val newButton = ItemActionButton.forItem(item)
        Log.d(TAG, "bind ${actionButton?.TAG} ${newButton.TAG} ${item.title}")
        // not using a new button to ensure valid progress values
        if (!(actionButton?.TAG == TTSActionButton::class.simpleName && newButton.TAG == TTSActionButton::class.simpleName)) {
            actionButton = newButton
            actionButton?.configure(secondaryActionButton, secondaryActionIcon, activity)
            secondaryActionButton.isFocusable = false
        }

        Log.d(TAG, "bind called ${item.media}")
        if (item.media != null) {
            bind(item.media!!)
        } else if (item.playState == BUILDING) {
            //            for generating TTS files for episode without media
            secondaryActionProgress.setPercentage(actionButton!!.processing, item)
            secondaryActionProgress.setIndeterminate(false)
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
            val imgLoc = ImageResourceUtils.getEpisodeListImageLocation(item)
            Log.d(TAG, "imgLoc $imgLoc")
            if (!imgLoc.isNullOrBlank() && !imgLoc.contains(PREFIX_GENERATIVE_COVER)) CoverLoader(activity)
                .withUri(imgLoc)
                .withFallbackUri(item.feed?.imageUrl)
                .withPlaceholderView(placeholder)
                .withCoverView(cover)
                .load()
            else cover.setImageResource(R.mipmap.ic_launcher)
        }
    }

    private fun bind(media: FeedMedia) {
        isVideo.visibility = if (media.getMediaType() == MediaType.VIDEO) View.VISIBLE else View.GONE
        duration.visibility = if (media.getDuration() > 0) View.VISIBLE else View.GONE

        if (PlaybackStatus.isCurrentlyPlaying(media)) {
            val density: Float = activity.resources.displayMetrics.density
            itemView.setBackgroundColor(SurfaceColors.getColorForElevation(activity, 8 * density))
        } else itemView.setBackgroundResource(ThemeUtils.getDrawableFromAttr(activity, R.attr.selectableItemBackground))

        val dls = DownloadServiceInterface.get()
        when {
            media.download_url != null && dls?.isDownloadingEpisode(media.download_url!!) == true -> {
                val percent: Float = 0.01f * dls.getProgress(media.download_url!!)
                secondaryActionProgress.setPercentage(max(percent, 0.01f), item)
                secondaryActionProgress.setIndeterminate(dls.isEpisodeQueued(media.download_url!!))
            }
            media.isDownloaded() -> {
                secondaryActionProgress.setPercentage(1f, item) // Do not animate 100% -> 0%
                secondaryActionProgress.setIndeterminate(false)
            }
            else -> {
                secondaryActionProgress.setPercentage(0f, item) // Animate X% -> 0%
                secondaryActionProgress.setIndeterminate(false)
            }
        }

        duration.text = Converter.getDurationStringLong(media.getDuration())
        duration.setContentDescription(activity.getString(R.string.chapter_duration,
            Converter.getDurationStringLocalized(activity, media.getDuration().toLong())))
        if (PlaybackStatus.isPlaying(item?.media) || item?.isInProgress == true) {
            val progress: Int = (100.0 * media.getPosition() / media.getDuration()).toInt()
            val remainingTime = max((media.getDuration() - media.getPosition()).toDouble(), 0.0).toInt()
            progressBar.progress = progress
            position.text = Converter.getDurationStringLong(media.getPosition())
            position.setContentDescription(activity.getString(R.string.position,
                Converter.getDurationStringLocalized(activity, media.getPosition().toLong())))
            progressBar.visibility = View.VISIBLE
            position.visibility = View.VISIBLE
            if (UserPreferences.shouldShowRemainingTime()) {
                duration.text = (if ((remainingTime > 0)) "-" else "") + Converter.getDurationStringLong(remainingTime)
                duration.setContentDescription(activity.getString(R.string.chapter_duration,
                    Converter.getDurationStringLocalized(activity, (media.getDuration() - media.getPosition()).toLong())))
            }
        } else {
            progressBar.visibility = View.GONE
            position.visibility = View.GONE
        }

        when {
            media.size > 0 -> size.text = Formatter.formatShortFileSize(activity, media.size)
            NetworkUtils.isEpisodeHeadDownloadAllowed && !media.checkedOnSizeButUnknown() -> {
                size.text = "{fa-spinner}"
                Iconify.addIcons(size)
                MediaSizeLoader.getFeedMediaSizeObservable(media).subscribe(
                    Consumer<Long?> { sizeValue: Long? ->
                        if (sizeValue == null) return@Consumer
                        if (sizeValue > 0) size.text = Formatter.formatShortFileSize(activity, sizeValue)
                        else size.text = ""
                    }) { error: Throwable? ->
                    size.text = ""
                    Log.e(TAG, Log.getStackTraceString(error))
                }
            }
            else -> size.text = ""
        }
    }

    fun bindDummy() {
        item = FeedItem()
        container.alpha = 0.1f
        secondaryActionIcon.setImageDrawable(null)
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
        val media = feedItem?.media
        if (media != null) {
            media.setPosition(event.position)
            media.setDuration(event.duration)
        }
        val currentPosition: Int = event.position
        val timeDuration: Int = event.duration
        val remainingTime = max((timeDuration - currentPosition).toDouble(), 0.0).toInt()
        //        Log.d(TAG, "currentPosition " + Converter.getDurationStringLong(currentPosition));
        if (currentPosition == Playable.INVALID_TIME || timeDuration == Playable.INVALID_TIME) {
            Log.w(TAG, "Could not react to position observer update because of invalid time")
            return
        }
        if (UserPreferences.shouldShowRemainingTime()) duration.text = (if (remainingTime > 0) "-" else "") + Converter.getDurationStringLong(remainingTime)
        else duration.text = Converter.getDurationStringLong(timeDuration)
    }

    val feedItem: FeedItem?
        get() = item

    val isCurrentlyPlayingItem: Boolean
        get() = item?.media != null && PlaybackStatus.isCurrentlyPlaying(item?.media)

    fun notifyPlaybackPositionUpdated(event: PlaybackPositionEvent) {
        progressBar.progress = (100.0 * event.position / event.duration).toInt()
        position.text = Converter.getDurationStringLong(event.position)
        updateDuration(event)
        duration.visibility = View.VISIBLE // Even if the duration was previously unknown, it is now known
    }

    /**
     * Hides the separator dot between icons and text if there are no icons.
     */
    fun hideSeparatorIfNecessary() {
        val hasIcons = isInQueue.visibility == View.VISIBLE || isVideo.visibility == View.VISIBLE || isFavorite.visibility == View.VISIBLE
        separatorIcons.visibility = if (hasIcons) View.VISIBLE else View.GONE
    }

    companion object {
        private const val TAG = "EpisodeItemViewHolder"
    }
}
