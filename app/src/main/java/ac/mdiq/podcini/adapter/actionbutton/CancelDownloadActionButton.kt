package ac.mdiq.podcini.adapter.actionbutton

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.R
import ac.mdiq.podcini.core.storage.DBWriter
import ac.mdiq.podcini.model.feed.FeedItem
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.storage.preferences.UserPreferences.isEnableAutodownload

class CancelDownloadActionButton(item: FeedItem) : ItemActionButton(item) {
    @StringRes
    override fun getLabel(): Int {
        return R.string.cancel_download_label
    }

    @DrawableRes
    override fun getDrawable(): Int {
        return R.drawable.ic_cancel
    }

    @UnstableApi override fun onClick(context: Context) {
        val media = item.media
        if (media != null) DownloadServiceInterface.get()?.cancel(context, media)
        if (isEnableAutodownload) {
            item.disableAutoDownload()
            DBWriter.setFeedItem(item)
        }
    }
}
