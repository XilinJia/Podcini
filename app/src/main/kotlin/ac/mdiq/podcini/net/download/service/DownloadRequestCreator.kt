package ac.mdiq.podcini.net.download.service

import ac.mdiq.podcini.net.download.serviceinterface.DownloadRequest
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.FilesUtils.feedfilePath
import ac.mdiq.podcini.storage.utils.FilesUtils.findUnusedFile
import ac.mdiq.podcini.storage.utils.FilesUtils.getFeedfileName
import ac.mdiq.podcini.storage.utils.FilesUtils.getMediafilePath
import ac.mdiq.podcini.storage.utils.FilesUtils.getMediafilename
import ac.mdiq.podcini.util.Logd
import java.io.File

/**
 * Creates download requests that can be sent to the DownloadService.
 */
object DownloadRequestCreator {
    private val TAG: String = DownloadRequestCreator::class.simpleName ?: "Anonymous"

    @JvmStatic
    fun create(feed: Feed): DownloadRequest.Builder {
        val dest = File(feedfilePath, getFeedfileName(feed))
        if (dest.exists()) dest.delete()

        Logd(TAG, "Requesting download feed from url " + feed.downloadUrl)

        val username = feed.preferences?.username
        val password = feed.preferences?.password

        return DownloadRequest.Builder(dest.toString(), feed)
            .withAuthentication(username, password)
            .lastModified(feed.lastUpdate)
    }

    @JvmStatic
    fun create(media: EpisodeMedia): DownloadRequest.Builder {
        Logd(TAG, "create: ${media.fileUrl} ${media.episode?.title}")
        val pdFile = if (media.fileUrl != null) File(media.fileUrl!!) else null
        val partiallyDownloadedFileExists = pdFile?.exists() ?: false
        var dest: File
        dest = if (partiallyDownloadedFileExists) pdFile!! else File(getMediafilePath(media), getMediafilename(media))

        if (dest.exists() && !partiallyDownloadedFileExists) dest = findUnusedFile(dest)!!

        Logd(TAG, "Requesting download media from url " + media.downloadUrl)

        val feed = media.episodeOrFetch()?.feed
        val username = feed?.preferences?.username
        val password = feed?.preferences?.password

        return DownloadRequest.Builder(dest.toString(), media).withAuthentication(username, password)
    }
}
