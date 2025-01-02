package ac.mdiq.podcini.net.download.service


import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.StorageUtils.feedfilePath
import ac.mdiq.podcini.util.Logd
import android.util.Log
import java.io.File

/**
 * Creates download requests that can be sent to the DownloadService.
 */
object DownloadRequestCreator {
    private val TAG: String = DownloadRequestCreator::class.simpleName ?: "Anonymous"

    @JvmStatic
    fun create(feed: Feed): DownloadRequest.Builder {
        val dest = File(feedfilePath, feed.getFeedfileName())
        if (dest.exists()) dest.delete()
        Logd(TAG, "Requesting download feed from url " + feed.downloadUrl)
        val username = feed.username
        val password = feed.password
        return DownloadRequest.Builder(dest.toString(), feed).withAuthentication(username, password).lastModified(feed.lastUpdate)
    }

    @JvmStatic
    fun create(media: Episode): DownloadRequest.Builder {
        Logd(TAG, "create: ${media.fileUrl} ${media.title}")
        val destUriString = media.getMediaFileUriString() ?: ""
        Logd(TAG, "destUriString: $destUriString")
        if (destUriString.isEmpty()) {
            Log.e(TAG, "destUriString is empty")
        }
        val feed = media.feed
        val username = feed?.username
        val password = feed?.password
        return DownloadRequest.Builder(destUriString, media).withAuthentication(username, password)
    }
}
