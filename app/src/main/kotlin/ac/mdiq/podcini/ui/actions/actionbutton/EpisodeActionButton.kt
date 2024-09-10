package ac.mdiq.podcini.ui.actions.actionbutton

import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.preferences.UserPreferences.isStreamOverDownload
import ac.mdiq.podcini.playback.base.InTheatre.isCurrentlyPlaying
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.getPlayerActivityIntent
import ac.mdiq.podcini.preferences.UserPreferences.videoPlayMode
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.ui.activity.VideoplayerActivity.Companion.videoMode
import android.content.Context
import android.view.View
import android.widget.ImageView
import androidx.media3.common.util.UnstableApi

abstract class EpisodeActionButton internal constructor(@JvmField var item: Episode) {
    val TAG = this::class.simpleName ?: "ItemActionButton"

    open val visibility: Int
        get() = View.VISIBLE

    var processing: Float = -1f

    abstract fun getLabel(): Int

    abstract fun getDrawable(): Int

    abstract fun onClick(context: Context)

    fun configure(button: View, icon: ImageView, context: Context) {
        button.visibility = visibility
        button.contentDescription = context.getString(getLabel())
        button.setOnClickListener { onClick(context) }
        icon.setImageResource(getDrawable())
    }

    @UnstableApi companion object {

        fun forItem(episode: Episode): EpisodeActionButton {
            val media = episode.media ?: return TTSActionButton(episode)
            val isDownloadingMedia = when (media.downloadUrl) {
                null -> false
                else -> DownloadServiceInterface.get()?.isDownloadingEpisode(media.downloadUrl!!)?:false
            }
//            Logd("ItemActionButton", "forItem: ${episode.feedId} ${episode.feed?.isLocalFeed} ${media.downloaded} ${isCurrentlyPlaying(media)}  ${episode.title} ")
            return when {
//                media.getMediaType() == MediaType.FLASH -> VisitWebsiteActionButton(episode)
                isCurrentlyPlaying(media) -> PauseActionButton(episode)
                episode.feed != null && episode.feed!!.isLocalFeed -> PlayLocalActionButton(episode)
                media.downloaded -> PlayActionButton(episode)
                isDownloadingMedia -> CancelDownloadActionButton(episode)
                isStreamOverDownload || episode.feed == null || episode.feedId == null || episode.feed?.type == Feed.FeedType.YOUTUBE.name
                        || episode.feed?.preferences?.prefStreamOverDownload == true -> StreamActionButton(episode)
                else -> DownloadActionButton(episode)
            }
        }

        fun playVideoIfNeeded(context: Context, media: Playable) {
            val item = (media as? EpisodeMedia)?.episode
            if (item?.feed?.preferences?.videoModePolicy != VideoMode.AUDIO_ONLY
                    && videoPlayMode != VideoMode.AUDIO_ONLY.code && videoMode != VideoMode.AUDIO_ONLY
                    && media.getMediaType() == MediaType.VIDEO)
                context.startActivity(getPlayerActivityIntent(context, MediaType.VIDEO))
        }
    }
}
