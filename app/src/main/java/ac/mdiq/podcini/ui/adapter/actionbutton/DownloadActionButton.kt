package ac.mdiq.podcini.ui.adapter.actionbutton

import android.content.Context
import android.content.DialogInterface
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.UsageStatistics
import ac.mdiq.podcini.preferences.UsageStatistics.logAction
import ac.mdiq.podcini.util.NetworkUtils.isEpisodeDownloadAllowed
import ac.mdiq.podcini.util.NetworkUtils.isNetworkRestricted
import ac.mdiq.podcini.util.NetworkUtils.isVpnOverWifi
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface

class DownloadActionButton(item: FeedItem) : ItemActionButton(item) {
    override fun getLabel(): Int {
        return R.string.download_label
    }
    override fun getDrawable(): Int {
        return R.drawable.ic_download
    }
    override val visibility: Int
        get() = if (item.feed?.isLocalFeed == true) View.INVISIBLE else View.VISIBLE

    override fun onClick(context: Context) {
        val media = item.media
        if (media == null || shouldNotDownload(media)) {
            return
        }

        logAction(UsageStatistics.ACTION_DOWNLOAD)

        if (isEpisodeDownloadAllowed) {
            DownloadServiceInterface.get()?.downloadNow(context, item, false)
        } else {
            val builder = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.confirm_mobile_download_dialog_title)
                .setPositiveButton(R.string.confirm_mobile_download_dialog_download_later
                ) { _: DialogInterface?, _: Int -> DownloadServiceInterface.get()?.downloadNow(context, item, false) }
                .setNeutralButton(R.string.confirm_mobile_download_dialog_allow_this_time
                ) { _: DialogInterface?, _: Int -> DownloadServiceInterface.get()?.downloadNow(context, item, true) }
                .setNegativeButton(R.string.cancel_label, null)
            if (isNetworkRestricted && isVpnOverWifi) {
                builder.setMessage(R.string.confirm_mobile_download_dialog_message_vpn)
            } else {
                builder.setMessage(R.string.confirm_mobile_download_dialog_message)
            }

            builder.show()
        }
    }

    private fun shouldNotDownload(media: FeedMedia): Boolean {
        if (media.download_url == null) return true
        val isDownloading = DownloadServiceInterface.get()?.isDownloadingEpisode(media.download_url!!)?:false
        return isDownloading || media.isDownloaded()
    }
}
