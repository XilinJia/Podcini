package ac.mdiq.podcini.ui.actions.actionbutton

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.preferences.UserPreferences.isEnableAutodownload
import ac.mdiq.podcini.storage.database.Episodes.persistEpisode

class CancelDownloadActionButton(item: Episode) : EpisodeActionButton(item) {
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
            persistEpisode(item)
        }
    }
}
