package de.danoeh.antennapod.core.util

import android.content.Context
import android.util.Log
import androidx.core.app.ShareCompat.IntentBuilder
import androidx.core.content.FileProvider
import de.danoeh.antennapod.core.R
import de.danoeh.antennapod.core.util.Converter.getDurationStringLong
import de.danoeh.antennapod.core.util.FeedItemUtil.getLinkWithFallback
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import java.io.File
import java.net.URLEncoder

/** Utility methods for sharing data  */
object ShareUtils {
    private const val TAG = "ShareUtils"

    @JvmStatic
    fun shareLink(context: Context, text: String) {
        val intent = IntentBuilder(context)
            .setType("text/plain")
            .setText(text)
            .setChooserTitle(R.string.share_url_label)
            .createChooserIntent()
        context.startActivity(intent)
    }

    @JvmStatic
    fun shareFeedLink(context: Context, feed: Feed) {
        val text = """
             ${feed.title}
             
             https://antennapod.org/deeplink/subscribe/?url=${URLEncoder.encode(feed.download_url)}&title=${
            URLEncoder.encode(feed.title)
        }
             """.trimIndent()
        shareLink(context, text)
    }

    @JvmStatic
    fun hasLinkToShare(item: FeedItem?): Boolean {
        return getLinkWithFallback(item) != null
    }

    @JvmStatic
    fun shareMediaDownloadLink(context: Context, media: FeedMedia) {
        if (!media.download_url.isNullOrEmpty()) shareLink(context, media.download_url!!)
    }

    @JvmStatic
    fun shareFeedItemLinkWithDownloadLink(context: Context, item: FeedItem, withPosition: Boolean) {
        var text: String? = item.feed?.title + ": " + item.title
        var pos = 0
        if (item.media != null && withPosition) {
            text += """
                
                ${context.resources.getString(R.string.share_starting_position_label)}: 
                """.trimIndent()
            pos = item.media!!.getPosition()
            text += getDurationStringLong(pos)
        }

        if (hasLinkToShare(item)) {
            text += """
                
                
                ${context.resources.getString(R.string.share_dialog_episode_website_label)}: 
                """.trimIndent()
            text += getLinkWithFallback(item)
        }

        if (item.media != null && item.media!!.download_url != null) {
            text += """
                
                
                ${context.resources.getString(R.string.share_dialog_media_file_label)}: 
                """.trimIndent()
            text += item.media!!.download_url
            if (withPosition) {
                text += "#t=" + pos / 1000
            }
        }
        shareLink(context, text!!)
    }

    @JvmStatic
    fun shareFeedItemFile(context: Context, media: FeedMedia) {
        val fileUri = FileProvider.getUriForFile(context, context.getString(R.string.provider_authority),
            File(media.getLocalMediaUrl()))

        IntentBuilder(context)
            .setType(media.mime_type)
            .addStream(fileUri)
            .setChooserTitle(R.string.share_file_label)
            .startChooser()

        Log.e(TAG, "shareFeedItemFile called")
    }
}
