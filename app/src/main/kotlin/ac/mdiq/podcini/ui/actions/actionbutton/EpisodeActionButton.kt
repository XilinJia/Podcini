package ac.mdiq.podcini.ui.actions.actionbutton

import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.preferences.UserPreferences.isStreamOverDownload
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.utils.MediaType
import ac.mdiq.podcini.playback.base.InTheatre.isCurrentlyPlaying
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.playback.base.InTheatre.curMedia
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.view.View
import android.widget.ImageView
import androidx.media3.common.util.UnstableApi

abstract class EpisodeActionButton internal constructor(@JvmField var item: Episode) {
    val TAG = this::class.simpleName ?: "ItemActionButton"

    abstract fun getLabel(): Int

    abstract fun getDrawable(): Int

    abstract fun onClick(context: Context)

    open val visibility: Int
        get() = View.VISIBLE

    var processing: Float = -1f

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
//            Logd("ItemActionButton", "forItem: ${episode.feedId} ${episode.feed?.isLocalFeed} ${media.downloaded} ${isCurrentlyPlaying(media)} ${curMedia is EpisodeMedia} ${media.id == (curMedia as? EpisodeMedia)?.id} ${episode.title} ")
            return when {
                media.getMediaType() == MediaType.FLASH -> VisitWebsiteActionButton(episode)
                isCurrentlyPlaying(media) -> PauseActionButton(episode)
                episode.feed != null && episode.feed!!.isLocalFeed -> PlayLocalActionButton(episode)
                media.downloaded -> PlayActionButton(episode)
                isDownloadingMedia -> CancelDownloadActionButton(episode)
                isStreamOverDownload || episode.feed == null || episode.feedId == null -> StreamActionButton(episode)
                else -> DownloadActionButton(episode)
            }
        }
    }
}
