package ac.mdiq.podcini.util

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.util.Converter.getDurationStringLong
import android.content.Context
import android.util.Log
import androidx.core.app.ShareCompat.IntentBuilder
import androidx.core.content.FileProvider
import java.io.File
import java.net.URLEncoder

/** Utility methods for sharing data  */
object ShareUtils {
    private val TAG: String = ShareUtils::class.simpleName ?: "Anonymous"

    @JvmStatic
    fun shareLink(context: Context, text: String) {
        val intent = IntentBuilder(context)
            .setType("text/plain")
            .setText(text)
            .setChooserTitle(R.string.share_url_label)
            .createChooserIntent()
        context.startActivity(intent)
    }

//    fun shareFeedLink(context: Context, feed: Feed) {
//        val text = """
//             ${feed.title}
//
//             https://podcini.org/deeplink/subscribe/?url=${URLEncoder.encode(feed.download_url)}&title=${
//            URLEncoder.encode(feed.title)
//        }
//             """.trimIndent()
//        shareLink(context, text)
//    }

    @JvmStatic
    fun shareFeedLink(context: Context, feed: Feed) {
        val text = """
             ${feed.title}
             
             ${URLEncoder.encode(feed.downloadUrl)}&title=${URLEncoder.encode(feed.title)}
             """.trimIndent()
        shareLink(context, text)
    }

    @JvmStatic
    fun hasLinkToShare(item: Episode?): Boolean {
        return item?.getLinkWithFallback() != null
    }

    @JvmStatic
    fun shareMediaDownloadLink(context: Context, media: EpisodeMedia) {
        if (!media.downloadUrl.isNullOrEmpty()) shareLink(context, media.downloadUrl!!)
    }

    @JvmStatic
    fun shareFeedItemLinkWithDownloadLink(context: Context, item: Episode, withPosition: Boolean) {
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
            text += item.getLinkWithFallback()
        }

        if (item.media != null && item.media!!.downloadUrl != null) {
            text += """
                
                
                ${context.resources.getString(R.string.share_dialog_media_file_label)}: 
                """.trimIndent()
            text += item.media!!.downloadUrl
            if (withPosition) {
                text += "#t=" + pos / 1000
            }
        }
        shareLink(context, text!!)
    }

    @JvmStatic
    fun shareFeedItemFile(context: Context, media: EpisodeMedia) {
        val lurl = media.getLocalMediaUrl()
        if (lurl.isNullOrEmpty()) return

        val fileUri = FileProvider.getUriForFile(context, context.getString(R.string.provider_authority), File(lurl))

        IntentBuilder(context)
            .setType(media.mimeType)
            .addStream(fileUri)
            .setChooserTitle(R.string.share_file_label)
            .startChooser()

        Log.e(TAG, "shareFeedItemFile called")
    }
}
