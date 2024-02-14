package de.danoeh.antennapod.core.service.download

import android.util.Log
import android.webkit.URLUtil
import de.danoeh.antennapod.core.util.FileNameGenerator
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.net.download.serviceinterface.DownloadRequest
import de.danoeh.antennapod.storage.preferences.UserPreferences
import org.apache.commons.io.FilenameUtils
import java.io.File

/**
 * Creates download requests that can be sent to the DownloadService.
 */
object DownloadRequestCreator {
    private const val TAG = "DownloadRequestCreat"
    private const val FEED_DOWNLOADPATH = "cache/"
    private const val MEDIA_DOWNLOADPATH = "media/"

    @JvmStatic
    fun create(feed: Feed): DownloadRequest.Builder {
        val dest = File(feedfilePath, getFeedfileName(feed))
        if (dest.exists()) {
            dest.delete()
        }
        Log.d(TAG, "Requesting download of url " + feed.download_url)

        val username = if ((feed.preferences != null)) feed.preferences!!.username else null
        val password = if ((feed.preferences != null)) feed.preferences!!.password else null

        return DownloadRequest.Builder(dest.toString(), feed)
            .withAuthentication(username, password)
            .lastModified(feed.lastUpdate)
    }

    @JvmStatic
    fun create(media: FeedMedia): DownloadRequest.Builder {
        val partiallyDownloadedFileExists =
            media.file_url != null && File(media.file_url).exists()
        var dest: File
        dest = if (partiallyDownloadedFileExists) {
            File(media.file_url)
        } else {
            File(getMediafilePath(media), getMediafilename(media))
        }

        if (dest.exists() && !partiallyDownloadedFileExists) {
            dest = findUnusedFile(dest)!!
        }
        Log.d(TAG, "Requesting download of url " + media.download_url)

        val username = if ((media.getItem()?.feed?.preferences != null)) media.getItem()!!.feed!!.preferences!!.username else null
        val password = if ((media.getItem()?.feed?.preferences != null)) media.getItem()!!.feed!!.preferences!!.password else null

        return DownloadRequest.Builder(dest.toString(), media).withAuthentication(username, password)
    }

    private fun findUnusedFile(dest: File): File? {
        // find different name
        var newDest: File? = null
        for (i in 1 until Int.MAX_VALUE) {
            val newName = (FilenameUtils.getBaseName(dest
                .name)
                    + "-"
                    + i
                    + FilenameUtils.EXTENSION_SEPARATOR
                    + FilenameUtils.getExtension(dest.name))
            Log.d(TAG, "Testing filename $newName")
            newDest = File(dest.parent, newName)
            if (!newDest.exists()) {
                Log.d(TAG, "File doesn't exist yet. Using $newName")
                break
            }
        }
        return newDest
    }

    private val feedfilePath: String
        get() = UserPreferences.getDataFolder(FEED_DOWNLOADPATH).toString() + "/"

    private fun getFeedfileName(feed: Feed): String {
        var filename = feed.download_url
        if (!feed.title.isNullOrEmpty()) {
            filename = feed.title
        }
        if (filename == null) return ""
        return "feed-" + FileNameGenerator.generateFileName(filename) + feed.id
    }

    private fun getMediafilePath(media: FeedMedia): String {
        val title = media.getItem()?.feed?.title?:return ""
        val mediaPath = (MEDIA_DOWNLOADPATH
                + FileNameGenerator.generateFileName(title))
        return UserPreferences.getDataFolder(mediaPath).toString() + "/"
    }

    private fun getMediafilename(media: FeedMedia): String {
        var titleBaseFilename = ""

        // Try to generate the filename by the item title
        if (media.getItem()?.title != null) {
            val title = media.getItem()!!.title!!
            titleBaseFilename = FileNameGenerator.generateFileName(title)
        }

        val urlBaseFilename = URLUtil.guessFileName(media.download_url, null, media.mime_type)

        var baseFilename: String
        baseFilename = if (titleBaseFilename != "") {
            titleBaseFilename
        } else {
            urlBaseFilename
        }
        val filenameMaxLength = 220
        if (baseFilename.length > filenameMaxLength) {
            baseFilename = baseFilename.substring(0, filenameMaxLength)
        }
        return (baseFilename + FilenameUtils.EXTENSION_SEPARATOR + media.id
                + FilenameUtils.EXTENSION_SEPARATOR + FilenameUtils.getExtension(urlBaseFilename))
    }
}
