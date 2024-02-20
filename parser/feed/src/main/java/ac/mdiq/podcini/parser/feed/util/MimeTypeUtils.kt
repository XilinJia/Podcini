package ac.mdiq.podcini.parser.feed.util

import android.webkit.MimeTypeMap
import org.apache.commons.io.FilenameUtils

/**
 * Utility class for handling MIME-Types of enclosures.
 */
object MimeTypeUtils {
    const val OCTET_STREAM: String = "application/octet-stream"

    // based on https://developer.android.com/guide/topics/media/media-formats
    val AUDIO_FILE_EXTENSIONS: Set<String> = HashSet(mutableListOf(
        "3gp", "aac", "amr", "flac", "imy", "m4a", "m4b", "mid", "mkv", "mp3", "mp4", "mxmf", "oga",
        "ogg", "ogx", "opus", "ota", "rtttl", "rtx", "wav", "xmf"
    ))

    val VIDEO_FILE_EXTENSIONS: Set<String> = HashSet(mutableListOf(
        "3gp", "mkv", "mp4", "ogg", "ogv", "ogx", "webm"
    ))

    @JvmStatic
    fun getMimeType(type: String?, filename: String?): String? {
        if (isMediaFile(type) && OCTET_STREAM != type) {
            return type
        }
        val filenameType = getMimeTypeFromUrl(filename)
        if (isMediaFile(filenameType)) {
            return filenameType
        }
        return type
    }

    @JvmStatic
    fun isMediaFile(type: String?): Boolean {
        return if (type == null) {
            false
        } else {
            (type.startsWith("audio/")
                    || type.startsWith("video/")) || type == "application/ogg" || type == "application/octet-stream"
        }
    }

    @JvmStatic
    fun isImageFile(type: String?): Boolean {
        return type?.startsWith("image/") ?: false
    }

    /**
     * Should be used if mime-type of enclosure tag is not supported. This
     * method will return the mime-type of the file extension.
     */
    private fun getMimeTypeFromUrl(url: String?): String? {
        if (url == null) {
            return null
        }
        val extension = FilenameUtils.getExtension(url)
        val mapResult = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        if (mapResult != null) {
            return mapResult
        }

        if (AUDIO_FILE_EXTENSIONS.contains(extension)) {
            return "audio/*"
        } else if (VIDEO_FILE_EXTENSIONS.contains(extension)) {
            return "video/*"
        }
        return null
    }
}
