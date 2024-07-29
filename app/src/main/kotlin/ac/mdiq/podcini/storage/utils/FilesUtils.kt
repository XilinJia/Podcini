package ac.mdiq.podcini.storage.utils

import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.util.Logd
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.URLUtil
import io.realm.kotlin.ext.isManaged
import org.apache.commons.io.FilenameUtils
import java.io.File
import java.io.IOException

@SuppressLint("StaticFieldLeak")
object FilesUtils {
    private val TAG: String = FilesUtils::class.simpleName ?: "Anonymous"

    private const val FEED_DOWNLOADPATH = "cache/"
    private const val MEDIA_DOWNLOADPATH = "media/"

    lateinit var context: Context

    fun findUnusedFile(dest: File): File? {
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

    val feedfilePath: String
        get() = getDataFolder(FEED_DOWNLOADPATH).toString() + "/"

    fun getFeedfileName(feed: Feed): String {
        var filename = feed.downloadUrl
        if (!feed.title.isNullOrEmpty()) filename = feed.title

        if (filename == null) return ""
        return "feed-" + FileNameGenerator.generateFileName(filename) + feed.id
    }

    fun getMediafilePath(media: EpisodeMedia): String {
        val item = media.episodeOrFetch() ?: return ""
        Logd(TAG, "item managed: ${item.isManaged()}")
        val title = item.feed?.title?:return ""
        val mediaPath = (MEDIA_DOWNLOADPATH + FileNameGenerator.generateFileName(title))
        return getDataFolder(mediaPath).toString() + "/"
    }

    fun getMediafilename(media: EpisodeMedia): String {
        var titleBaseFilename = ""

        // Try to generate the filename by the item title
        val item_ = media.episodeOrFetch()
        if (item_?.title != null) titleBaseFilename = FileNameGenerator.generateFileName(item_.title!!)

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
        return getDataFolder(mediaPath).toString() + "/"
    }

    fun getMediafilename(item: Episode): String {
        var titleBaseFilename = ""

        // Try to generate the filename by the item title
        if (item.title != null) {
            val title = item.title!!
            titleBaseFilename = FileNameGenerator.generateFileName(title)
        }

        var baseFilename: String
        baseFilename = if (titleBaseFilename != "") titleBaseFilename else "NoTitle"
        val filenameMaxLength = 220
        if (baseFilename.length > filenameMaxLength) baseFilename = baseFilename.substring(0, filenameMaxLength)

        return (baseFilename + FilenameUtils.EXTENSION_SEPARATOR + "noid" + FilenameUtils.EXTENSION_SEPARATOR + "wav")
    }

    fun getTypeDir(baseDirPath: String?, type: String?): File? {
        if (baseDirPath == null) return null

        val baseDir = File(baseDirPath)
        val typeDir = if (type == null) baseDir else File(baseDir, type)
        if (!typeDir.exists()) {
            if (!baseDir.canWrite()) {
                Log.e(TAG, "Base dir is not writable " + baseDir.absolutePath)
                return null
            }
            if (!typeDir.mkdirs()) {
                Log.e(TAG, "Could not create type dir " + typeDir.absolutePath)
                return null
            }
        }
        return typeDir
    }

    /**
     * Return the folder where the app stores all of its data. This method will
     * return the standard data folder if none has been set by the user.
     * @param type The name of the folder inside the data folder. May be null
     * when accessing the root of the data folder.
     * @return The data folder that has been requested or null if the folder could not be created.
     */
    fun getDataFolder(type: String?): File? {
        var dataFolder = getTypeDir(null, type)
        if (dataFolder == null || !dataFolder.canWrite()) {
            Logd(TAG, "User data folder not writable or not set. Trying default.")
            dataFolder = context.getExternalFilesDir(type)
        }
        if (dataFolder == null || !dataFolder.canWrite()) {
            Logd(TAG, "Default data folder not available or not writable. Falling back to internal memory.")
            dataFolder = getTypeDir(context.filesDir.absolutePath, type)
        }
        return dataFolder
    }

    /**
     * Create a .nomedia file to prevent scanning by the media scanner.
     */
    fun createNoMediaFile() {
        val f = File(context.getExternalFilesDir(null), ".nomedia")
        if (!f.exists()) {
            try {
                f.createNewFile()
            } catch (e: IOException) {
                Log.e(TAG, "Could not create .nomedia file")
                e.printStackTrace()
            }
            Logd(TAG, ".nomedia file created")
        }
    }
}