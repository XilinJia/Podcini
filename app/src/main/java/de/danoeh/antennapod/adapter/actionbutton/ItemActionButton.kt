package de.danoeh.antennapod.adapter.actionbutton

import android.content.Context
import android.view.View
import android.widget.ImageView
import androidx.media3.common.util.UnstableApi
import de.danoeh.antennapod.core.util.PlaybackStatus.isCurrentlyPlaying
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.storage.preferences.UserPreferences.isStreamOverDownload

abstract class ItemActionButton internal constructor(@JvmField var item: FeedItem) {
    abstract fun getLabel(): Int

    abstract fun getDrawable(): Int

    abstract fun onClick(context: Context)

    open val visibility: Int
        get() = View.VISIBLE

    fun configure(button: View, icon: ImageView, context: Context) {
        button.visibility = visibility
        button.contentDescription = context.getString(getLabel())
        button.setOnClickListener { view: View? -> onClick(context) }
        icon.setImageResource(getDrawable())
    }

    @UnstableApi companion object {
        fun forItem(item: FeedItem): ItemActionButton {
            val media = item.media ?: return MarkAsPlayedActionButton(item)

            val isDownloadingMedia = if (media.download_url==null) false else DownloadServiceInterface.get()?.isDownloadingEpisode(media.download_url!!)?:false
            return if (isCurrentlyPlaying(media)) {
                PauseActionButton(item)
            } else if (item.feed != null && item.feed!!.isLocalFeed) {
                PlayLocalActionButton(item)
            } else if (media.isDownloaded()) {
                PlayActionButton(item)
            } else if (isDownloadingMedia) {
                CancelDownloadActionButton(item)
            } else if (isStreamOverDownload) {
                StreamActionButton(item)
            } else {
                DownloadActionButton(item)
            }
        }
    }
}
