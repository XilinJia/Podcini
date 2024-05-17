package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.model.download.DownloadResult
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.util.DownloadErrorLabel.from
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DownloadLogDetailsDialog(context: Context, status: DownloadResult) : MaterialAlertDialogBuilder(context) {
    init {
        var url = "unknown"
        when (status.feedfileType) {
            FeedMedia.FEEDFILETYPE_FEEDMEDIA -> {
                val media = DBReader.getFeedMedia(status.feedfileId)
                if (media != null) url = media.download_url?:""
            }
            Feed.FEEDFILETYPE_FEED -> {
                val feed = DBReader.getFeed(status.feedfileId)
                if (feed != null) url = feed.download_url?:""
            }
        }

        var message = context.getString(R.string.download_successful)
        if (!status.isSuccessful) message = status.reasonDetailed

        val messageFull = context.getString(R.string.download_log_details_message,
            context.getString(from(status.reason)), message, url)
        setTitle(R.string.download_error_details)
        setMessage(messageFull)
        setPositiveButton("OK", null)
        setNeutralButton(R.string.copy_to_clipboard) { _, _ ->
            val clipboard = getContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(context.getString(R.string.download_error_details), messageFull)
            clipboard.setPrimaryClip(clip)
            if (Build.VERSION.SDK_INT < 32) EventFlow.postEvent(FlowEvent.MessageEvent(context.getString(R.string.copied_to_clipboard)))
        }
    }

    override fun show(): AlertDialog {
        val dialog = super.show()
        (dialog.findViewById<View>(R.id.message) as? TextView)?.setTextIsSelectable(true)
        return dialog
    }
}
