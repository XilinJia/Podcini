package ac.mdiq.podcini.ui.view

import ac.mdiq.podcini.R
import ac.mdiq.podcini.databinding.FeeditemlistItemBinding
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Episode.PlayState
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Feed.Companion.PREFIX_GENERATIVE_COVER
import ac.mdiq.podcini.storage.model.MediaType
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.storage.utils.DurationConverter
import ac.mdiq.podcini.storage.utils.ImageResourceUtils
import ac.mdiq.podcini.ui.actions.actionbutton.EpisodeActionButton
import ac.mdiq.podcini.ui.actions.actionbutton.TTSActionButton
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.utils.CoverLoader
import ac.mdiq.podcini.ui.utils.ThemeUtils
import ac.mdiq.podcini.ui.utils.ThemeUtils.getColorFromAttr
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter.formatAbbrev
import ac.mdiq.podcini.util.MiscFormatter.formatForAccessibility
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
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.notifications.UpdatedObject
import kotlinx.coroutines.*
import kotlin.math.max

/**
 * Holds the view which shows FeedItems.
 */
@UnstableApi
open class EpisodeViewHolder(private val activity: MainActivity, parent: ViewGroup, var refreshAdapterPosCallback: ((Int, Episode) -> Unit)? = null)
    : RecyclerView.ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.feeditemlist_item, parent, false)) {

    val binding: FeeditemlistItemBinding = FeeditemlistItemBinding.bind(itemView)

    private val placeholder: TextView = binding.txtvPlaceholder
    private val cover: ImageView = binding.imgvCover
    private val title: TextView = binding.txtvTitle
    private val position: TextView = binding.txtvPosition
    private val duration: TextView = binding.txtvDuration
    private val isVideo: ImageView = binding.ivIsVideo
    private val progressBar: ProgressBar = binding.progressBar

    private var posIndex: Int = -1

    private var actionButton: EpisodeActionButton? = null
    private val secondaryActionProgress: CircularProgressBar = binding.secondaryActionButton.secondaryActionProgress

    protected val pubDate: TextView = binding.txtvPubDate

    @JvmField
    val dragHandle: ImageView = binding.dragHandle

    @JvmField
    val isInQueue: ImageView = binding.ivInPlaylist

    @JvmField
    val secondaryActionButton: View = binding.secondaryActionButton.root
    @JvmField
    val secondaryActionIcon: ImageView = binding.secondaryActionButton.secondaryActionIcon

    @JvmField
    val coverHolder: CardView = binding.coverHolder
    @JvmField
    val infoCard: LinearLayout = binding.infoCard

    var episode: Episode? = null

    private var episodeMonitor: Job? = null
    private var mediaMonitor: Job? = null
    private var notBond: Boolean = true

    private val isCurMedia: Boolean
        get() = InTheatre.isCurMedia(this.episode?.media)

    init {
        title.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_FULL)
        itemView.tag = this
    }

    fun bind(item: Episode) {
        if (episodeMonitor == null) {
            val item_ = realm.query(Episode::class).query("id == ${item.id}").first()
            episodeMonitor = CoroutineScope(Dispatchers.Default).launch {
                val episodeFlow = item_.asFlow()
                episodeFlow.collect { changes: SingleQueryChange<Episode> ->
                    when (changes) {
                        is UpdatedObject -> {
                            Logd(TAG, "episodeMonitor UpdatedObject ${changes.obj.title} ${changes.changedFields.joinToString()}")
                            withContext(Dispatchers.Main) {
                                bind(changes.obj)
                                if (posIndex >= 0) refreshAdapterPosCallback?.invoke(posIndex, changes.obj)
                            }
                        }
                        else -> {}
                    }
                }
//            return
            }
        }
        if (mediaMonitor == null) {
            val item_ = realm.query(Episode::class).query("id == ${item.id}").first()
            mediaMonitor = CoroutineScope(Dispatchers.Default).launch {
                val episodeFlow = item_.asFlow(listOf("media.*"))
                episodeFlow.collect { changes: SingleQueryChange<Episode> ->
                    when (changes) {
                        is UpdatedObject -> {
                            Logd(TAG, "mediaMonitor UpdatedObject ${changes.obj.title} ${changes.changedFields.joinToString()}")
                            withContext(Dispatchers.Main) {
                                updatePlaybackPositionNew(changes.obj)
//                                bind(changes.obj)
                                if (posIndex >= 0) refreshAdapterPosCallback?.invoke(posIndex, changes.obj)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
        this.episode = item
        placeholder.text = item.feed?.title
        title.text = item.title
        binding.container.alpha = if (item.isPlayed()) 0.7f else 1.0f
        binding.leftPadding.contentDescription = item.title
        binding.playedMark.visibility = View.GONE
        binding.txtvPubDate.setTextColor(getColorFromAttr(activity, com.google.android.material.R.attr.colorOnSurfaceVariant))
        when {
            item.isPlayed() -> {
                binding.leftPadding.contentDescription = item.title + ". " + activity.getString(R.string.is_played)
                binding.playedMark.visibility = View.VISIBLE
                binding.playedMark.alpha = 1.0f
            }
            item.isNew -> {
                binding.txtvPubDate.setTextColor(getColorFromAttr(activity, androidx.appcompat.R.attr.colorAccent))
            }
        }

        setPubDate(item)

        binding.isFavorite.visibility = if (item.isFavorite) View.VISIBLE else View.GONE
        isInQueue.visibility = if (curQueue.contains(item)) View.VISIBLE else View.GONE
//        container.alpha = if (item.isPlayed()) 0.7f else 1.0f

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
            item.playState == PlayState.BUILDING.code -> {
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

        if (notBond && coverHolder.visibility == View.VISIBLE) {
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
//            if (item.isNew) cover.setColorFilter(ContextCompat.getColor(activity, R.color.gradient_100), PorterDuff.Mode.MULTIPLY)
        }
        notBond = false
    }

    internal fun setPosIndex(index: Int) {
        posIndex = index
    }

    fun unbind() {
        Logd(TAG, "unbind ${title.text}")
        // Cancel coroutine here
        itemView.setOnClickListener(null)
        itemView.setOnCreateContextMenuListener(null)
        itemView.setOnLongClickListener(null)
        itemView.setOnTouchListener(null)
        secondaryActionButton.setOnClickListener(null)
        dragHandle.setOnTouchListener(null)
        coverHolder.setOnTouchListener(null)
        posIndex = -1
        episode = null
        notBond = true
        stopDBMonitor()
    }

    fun stopDBMonitor() {
        episodeMonitor?.cancel()
        episodeMonitor = null
        mediaMonitor?.cancel()
        mediaMonitor = null
    }

    open fun setPubDate(item: Episode) {
        pubDate.text = formatAbbrev(activity, item.getPubDate())
        pubDate.setContentDescription(formatForAccessibility(item.getPubDate()))
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

        duration.text = DurationConverter.getDurationStringLong(media.getDuration())
        duration.setContentDescription(activity.getString(R.string.chapter_duration,
            DurationConverter.getDurationStringLocalized(activity, media.getDuration().toLong())))
        if (isCurMedia || this.episode?.isInProgress == true) {
            val progress: Int = (100.0 * media.getPosition() / media.getDuration()).toInt()
            val remainingTime = max((media.getDuration() - media.getPosition()).toDouble(), 0.0).toInt()
            progressBar.progress = progress
            position.text = DurationConverter.getDurationStringLong(media.getPosition())
            position.setContentDescription(activity.getString(R.string.position,
                DurationConverter.getDurationStringLocalized(activity, media.getPosition().toLong())))
            progressBar.visibility = View.VISIBLE
            position.visibility = View.VISIBLE
            if (UserPreferences.shouldShowRemainingTime()) {
                duration.text = (if ((remainingTime > 0)) "-" else "") + DurationConverter.getDurationStringLong(remainingTime)
                duration.setContentDescription(activity.getString(R.string.chapter_duration,
                    DurationConverter.getDurationStringLocalized(activity, (media.getDuration() - media.getPosition()).toLong())))
            }
        } else {
            progressBar.visibility = View.GONE
            position.visibility = View.GONE
        }

        when {
            media.size > 0 -> binding.size.text = Formatter.formatShortFileSize(activity, media.size)
            else -> binding.size.text = ""
        }
    }

    fun updatePlaybackPositionNew(item: Episode) {
        Logd(TAG, "updatePlaybackPositionNew called")
        this.episode = item
        val currentPosition = item.media?.position ?: 0
        val timeDuration = item.media?.duration ?: 0
        progressBar.progress = (100.0 * currentPosition / timeDuration).toInt()
        position.text = DurationConverter.getDurationStringLong(currentPosition)

        val remainingTime = max((timeDuration - currentPosition).toDouble(), 0.0).toInt()
        if (currentPosition == Playable.INVALID_TIME || timeDuration == Playable.INVALID_TIME) {
            Log.w(TAG, "Could not react to position observer update because of invalid time")
            return
        }
        if (UserPreferences.shouldShowRemainingTime()) duration.text = (if (remainingTime > 0) "-" else "") + DurationConverter.getDurationStringLong(remainingTime)
        else duration.text = DurationConverter.getDurationStringLong(timeDuration)
        duration.visibility = View.VISIBLE // Even if the duration was previously unknown, it is now known
    }

    /**
     * Hides the separator dot between icons and text if there are no icons.
     */
    fun hideSeparatorIfNecessary() {
        val hasIcons = isInQueue.visibility == View.VISIBLE || isVideo.visibility == View.VISIBLE || binding.isFavorite.visibility == View.VISIBLE
        binding.separatorIcons.visibility = if (hasIcons) View.VISIBLE else View.GONE
    }

    companion object {
        private val TAG: String = EpisodeViewHolder::class.simpleName ?: "Anonymous"
    }
}
