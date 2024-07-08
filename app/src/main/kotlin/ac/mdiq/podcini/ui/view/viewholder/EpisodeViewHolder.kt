package ac.mdiq.podcini.ui.view.viewholder

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.FeeditemlistItemBinding
import ac.mdiq.podcini.storage.utils.ImageResourceUtils
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Episode.Companion.BUILDING
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Feed.Companion.PREFIX_GENERATIVE_COVER
import ac.mdiq.podcini.storage.model.MediaType
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.ui.actions.actionbutton.EpisodeActionButton
import ac.mdiq.podcini.ui.actions.actionbutton.TTSActionButton
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.utils.CoverLoader
import ac.mdiq.podcini.ui.utils.ThemeUtils
import ac.mdiq.podcini.ui.view.CircularProgressBar
import ac.mdiq.podcini.util.Converter
import ac.mdiq.podcini.util.DateFormatter
import ac.mdiq.podcini.util.Logd
import android.text.Layout
import android.text.format.Formatter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.cardview.widget.CardView
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.elevation.SurfaceColors
import kotlin.math.max

/**
 * Holds the view which shows FeedItems.
 */
@UnstableApi
open class EpisodeViewHolder(private val activity: MainActivity, parent: ViewGroup)
    : RecyclerView.ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.feeditemlist_item, parent, false)) {

    val binding: FeeditemlistItemBinding = FeeditemlistItemBinding.bind(itemView)

    private val container: View = binding.container
    @JvmField
    val dragHandle: ImageView = binding.dragHandle
    private val placeholder: TextView = binding.txtvPlaceholder
    private val cover: ImageView = binding.imgvCover
    private val title: TextView = binding.txtvTitle
    protected val pubDate: TextView = binding.txtvPubDate
    private val position: TextView = binding.txtvPosition
    private val duration: TextView = binding.txtvDuration
    private val size: TextView = binding.size
    @JvmField
    val isInQueue: ImageView = binding.ivInPlaylist
    private val isVideo: ImageView = binding.ivIsVideo
    private val isFavorite: ImageView = binding.isFavorite
    private val progressBar: ProgressBar = binding.progressBar

    private var actionButton: EpisodeActionButton? = null

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

    var episode: Episode? = null

    val isCurMedia: Boolean
        get() = InTheatre.isCurMedia(this.episode?.media)

    init {
        title.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
        itemView.tag = this
    }

    fun bind(item: Episode) {
//        Logd(TAG, "in bind: ${item.title} ${item.isFavorite} ${item.isPlayed()}")
        this.episode = item
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

        setPubDate(item)

        isFavorite.visibility = if (item.isFavorite) View.VISIBLE else View.GONE
        isInQueue.visibility = if (curQueue.isInQueue(item)) View.VISIBLE else View.GONE
        container.alpha = if (item.isPlayed()) 0.75f else 1.0f

        val newButton = EpisodeActionButton.forItem(item)
//        Logd(TAG, "Trying to bind button ${actionButton?.TAG} ${newButton.TAG} ${item.title}")
        // not using a new button to ensure valid progress values, for TTS audio generation
        if (!(actionButton?.TAG == TTSActionButton::class.simpleName && newButton.TAG == TTSActionButton::class.simpleName)) {
            actionButton = newButton
            actionButton?.configure(secondaryActionButton, secondaryActionIcon, activity)
            secondaryActionButton.isFocusable = false
        }

//        Log.d(TAG, "bind called ${item.media}")
        when {
            item.media != null -> bind(item.media!!)
            //            for generating TTS files for episode without media
            item.playState == BUILDING -> {
                secondaryActionProgress.setPercentage(actionButton!!.processing, item)
                secondaryActionProgress.setIndeterminate(false)
            }
            else -> {
                secondaryActionProgress.setPercentage(0f, item)
                secondaryActionProgress.setIndeterminate(false)
                isVideo.visibility = View.GONE
                progressBar.visibility = View.GONE
                duration.visibility = View.GONE
                position.visibility = View.GONE
                itemView.setBackgroundResource(ThemeUtils.getDrawableFromAttr(activity, androidx.appcompat.R.attr.selectableItemBackground))
            }
        }

        if (coverHolder.visibility == View.VISIBLE) {
            cover.setImageDrawable(null)
            val imgLoc = ImageResourceUtils.getEpisodeListImageLocation(item)
//            Logd(TAG, "imgLoc $imgLoc ${item.feed?.imageUrl} ${item.title}")
            if (!imgLoc.isNullOrBlank() && !imgLoc.contains(PREFIX_GENERATIVE_COVER))
                CoverLoader(activity)
                    .withUri(imgLoc)
                    .withFallbackUri(item.feed?.imageUrl)
                    .withPlaceholderView(placeholder)
                    .withCoverView(cover)
                    .load()
            else {
                Logd(TAG, "setting cover to ic_launcher")
                cover.setImageDrawable(AppCompatResources.getDrawable(activity, R.drawable.ic_launcher_foreground))
            }
        }
    }

    open fun setPubDate(item: Episode) {
        pubDate.text = DateFormatter.formatAbbrev(activity, item.getPubDate())
        pubDate.setContentDescription(DateFormatter.formatForAccessibility(item.getPubDate()))
    }

    private fun bind(media: EpisodeMedia) {
        isVideo.visibility = if (media.getMediaType() == MediaType.VIDEO) View.VISIBLE else View.GONE
        duration.visibility = if (media.getDuration() > 0) View.VISIBLE else View.GONE

        if (isCurMedia) {
            val density: Float = activity.resources.displayMetrics.density
            itemView.setBackgroundColor(SurfaceColors.getColorForElevation(activity, 8 * density))
        } else itemView.setBackgroundResource(ThemeUtils.getDrawableFromAttr(activity, androidx.appcompat.R.attr.selectableItemBackground))

        val dls = DownloadServiceInterface.get()
        when {
            media.downloadUrl != null && dls?.isDownloadingEpisode(media.downloadUrl!!) == true -> {
                val percent: Float = 0.01f * dls.getProgress(media.downloadUrl!!)
                secondaryActionProgress.setPercentage(max(percent, 0.01f), this.episode)
                secondaryActionProgress.setIndeterminate(dls.isEpisodeQueued(media.downloadUrl!!))
            }
            media.downloaded -> {
                secondaryActionProgress.setPercentage(1f, this.episode) // Do not animate 100% -> 0%
                secondaryActionProgress.setIndeterminate(false)
            }
            else -> {
                secondaryActionProgress.setPercentage(0f, this.episode) // Animate X% -> 0%
                secondaryActionProgress.setIndeterminate(false)
            }
        }

        duration.text = Converter.getDurationStringLong(media.getDuration())
        duration.setContentDescription(activity.getString(R.string.chapter_duration,
            Converter.getDurationStringLocalized(activity, media.getDuration().toLong())))
        if (isCurMedia || this.episode?.isInProgress == true) {
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
//            TODO: might be better to do it elsewhere
//            NetworkUtils.isEpisodeHeadDownloadAllowed && !media.checkedOnSizeButUnknown() -> {
//                size.text = "{fa-spinner}"
////                Iconify.addIcons(size)
//                MediaSizeLoader.getFeedMediaSizeObservable(media).subscribe(Consumer<Long?> { sizeValue: Long? ->
//                    if (sizeValue == null) return@Consumer
//                    if (sizeValue > 0) size.text = Formatter.formatShortFileSize(activity, sizeValue)
//                    else size.text = ""
//                }) { error: Throwable? ->
//                    size.text = ""
//                    Log.e(TAG, Log.getStackTraceString(error))
//                }
//            }
            else -> size.text = ""
        }
    }

    fun bindDummy() {
        this.episode = Episode()
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
        itemView.setBackgroundResource(ThemeUtils.getDrawableFromAttr(activity, androidx.appcompat.R.attr.selectableItemBackground))
        placeholder.text = ""
        if (coverHolder.visibility == View.VISIBLE) {
            CoverLoader(activity)
                .withResource(R.color.medium_gray)
                .withPlaceholderView(placeholder)
                .withCoverView(cover)
                .load()
        }
    }

    fun updatePlaybackPositionNew(item: Episode) {
        Logd(TAG, "updatePlaybackPositionNew called")
        this.episode = item
        val currentPosition = item.media?.position ?: 0
        val timeDuration = item.media?.duration ?: 0
        progressBar.progress = (100.0 * currentPosition / timeDuration).toInt()
        position.text = Converter.getDurationStringLong(currentPosition)

        val remainingTime = max((timeDuration - currentPosition).toDouble(), 0.0).toInt()
        if (currentPosition == Playable.INVALID_TIME || timeDuration == Playable.INVALID_TIME) {
            Log.w(TAG, "Could not react to position observer update because of invalid time")
            return
        }
        if (UserPreferences.shouldShowRemainingTime()) duration.text = (if (remainingTime > 0) "-" else "") + Converter.getDurationStringLong(remainingTime)
        else duration.text = Converter.getDurationStringLong(timeDuration)
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
        private val TAG: String = EpisodeViewHolder::class.simpleName ?: "Anonymous"
    }
}
