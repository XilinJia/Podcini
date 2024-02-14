package de.danoeh.antennapod.adapter.actionbutton

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.media3.common.util.UnstableApi
import de.danoeh.antennapod.R
import de.danoeh.antennapod.core.storage.DBWriter
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface
import de.danoeh.antennapod.storage.preferences.UserPreferences.isEnableAutodownload

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
