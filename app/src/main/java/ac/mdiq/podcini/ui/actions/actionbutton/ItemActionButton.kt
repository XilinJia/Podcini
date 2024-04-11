package ac.mdiq.podcini.ui.actions.actionbutton

import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.preferences.UserPreferences.isStreamOverDownload
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.playback.MediaType
import ac.mdiq.podcini.util.PlaybackStatus.isCurrentlyPlaying
import android.content.Context
import android.view.View
import android.widget.ImageView
import androidx.media3.common.util.UnstableApi

abstract class ItemActionButton internal constructor(@JvmField var item: FeedItem) {
    abstract fun getLabel(): Int

    abstract fun getDrawable(): Int

    abstract fun onClick(context: Context)

    open val visibility: Int
        get() = View.VISIBLE

    fun configure(button: View, icon: ImageView, context: Context) {
        button.visibility = visibility
        button.contentDescription = context.getString(getLabel())
        button.setOnClickListener { onClick(context) }
        icon.setImageResource(getDrawable())
    }

    @UnstableApi companion object {
        fun forItem(item: FeedItem): ItemActionButton {
            val media = item.media ?: return MarkAsPlayedActionButton(item)

            val isDownloadingMedia = when (media.download_url) {
                null -> false
                else -> DownloadServiceInterface.get()?.isDownloadingEpisode(media.download_url!!)?:false
            }
            return when {
                media.getMediaType() == MediaType.FLASH -> {
                    VisitWebsiteActionButton(item)
                }
                isCurrentlyPlaying(media) -> {
                    PauseActionButton(item)
                }
                item.feed != null && item.feed!!.isLocalFeed -> {
                    PlayLocalActionButton(item)
                }
                media.isDownloaded() -> {
                    PlayActionButton(item)
                }
                isDownloadingMedia -> {
                    CancelDownloadActionButton(item)
                }
                isStreamOverDownload || item.feed == null || item.feedId == 0L -> {
                    StreamActionButton(item)
                }
                else -> {
                    DownloadActionButton(item)
                }
            }
        }
    }
}
