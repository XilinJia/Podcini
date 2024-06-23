package ac.mdiq.podcini.net.download.service

import ac.mdiq.podcini.net.download.serviceinterface.DownloadRequest
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.utils.FileNameGenerator
import ac.mdiq.podcini.util.Logd
import android.webkit.URLUtil
import io.realm.kotlin.ext.isManaged
import org.apache.commons.io.FilenameUtils
import java.io.File

/**
 * Creates download requests that can be sent to the DownloadService.
 */
object DownloadRequestCreator {
    private val TAG: String = DownloadRequestCreator::class.simpleName ?: "Anonymous"
    private const val FEED_DOWNLOADPATH = "cache/"
    private const val MEDIA_DOWNLOADPATH = "media/"

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

        val feed = media.episode?.feed
        val username = feed?.preferences?.username
        val password = feed?.preferences?.password

        return DownloadRequest.Builder(dest.toString(), media).withAuthentication(username, password)
    }

    private fun findUnusedFile(dest: File): File? {
        // find different name
        var newDest: File? = null
        for (i in 1 until Int.MAX_VALUE) {
            val newName = (FilenameUtils.getBaseName(dest.name) + "-" + i + FilenameUtils.EXTENSION_SEPARATOR + FilenameUtils.getExtension(dest.name))
            Logd(TAG, "Testing filename $newName")
            newDest = File(dest.parent, newName)
            if (!newDest.exists()) {
                Logd(TAG, "File doesn't exist yet. Using $newName")
                break
            }
        }
        return newDest
    }

    private val feedfilePath: String
        get() = UserPreferences.getDataFolder(FEED_DOWNLOADPATH).toString() + "/"

    private fun getFeedfileName(feed: Feed): String {
        var filename = feed.downloadUrl
        if (!feed.title.isNullOrEmpty()) filename = feed.title

        if (filename == null) return ""
        return "feed-" + FileNameGenerator.generateFileName(filename) + feed.id
    }

    private fun getMediafilePath(media: EpisodeMedia): String {
        val item = media.episode ?: return ""
        Logd(TAG, "item managed: ${item?.isManaged()}")
        val title = item.feed?.title?:return ""
        val mediaPath = (MEDIA_DOWNLOADPATH + FileNameGenerator.generateFileName(title))
        return UserPreferences.getDataFolder(mediaPath).toString() + "/"
    }

    private fun getMediafilename(media: EpisodeMedia): String {
        var titleBaseFilename = ""

        // Try to generate the filename by the item title
        if (media.episode?.title != null) {
            val title = media.episode!!.title!!
            titleBaseFilename = FileNameGenerator.generateFileName(title)
        }

        val urlBaseFilename = URLUtil.guessFileName(media.downloadUrl, null, media.mimeType)

        var baseFilename: String
        baseFilename = if (titleBaseFilename != "") titleBaseFilename else urlBaseFilename
        val filenameMaxLength = 220
        if (baseFilename.length > filenameMaxLength) baseFilename = baseFilename.substring(0, filenameMaxLength)

        return (baseFilename + FilenameUtils.EXTENSION_SEPARATOR + media.id + FilenameUtils.EXTENSION_SEPARATOR + FilenameUtils.getExtension(urlBaseFilename))
    }

    fun getMediafilePath(item: Episode): String {
        val title = item.feed?.title?:return ""
        val mediaPath = (MEDIA_DOWNLOADPATH + FileNameGenerator.generateFileName(title))
        return UserPreferences.getDataFolder(mediaPath).toString() + "/"
    }

    fun getMediafilename(item: Episode): String {
        var titleBaseFilename = ""

        // Try to generate the filename by the item title
        if (item.title != null) {
            val title = item.title!!
            titleBaseFilename = FileNameGenerator.generateFileName(title)
        }

//        val urlBaseFilename = URLUtil.guessFileName(media.download_url, null, media.mime_type)

        var baseFilename: String
        baseFilename = if (titleBaseFilename != "") titleBaseFilename else "NoTitle"
        val filenameMaxLength = 220
        if (baseFilename.length > filenameMaxLength) baseFilename = baseFilename.substring(0, filenameMaxLength)

        return (baseFilename + FilenameUtils.EXTENSION_SEPARATOR + "noid" + FilenameUtils.EXTENSION_SEPARATOR + "wav")
    }

}
