package ac.mdiq.podcini.storage.database.mapper

import android.database.Cursor
import ac.mdiq.podcini.model.feed.FeedMedia
import ac.mdiq.podcini.storage.database.PodDBAdapter
import java.lang.Boolean
import java.util.*

/**
 * Converts a [Cursor] to a [FeedMedia] object.
 */
object FeedMediaCursorMapper {
    /**
     * Create a [FeedMedia] instance from a database row (cursor).
     */
    @JvmStatic
    fun convert(cursor: Cursor): FeedMedia {
        val indexId = cursor.getColumnIndexOrThrow(PodDBAdapter.SELECT_KEY_MEDIA_ID)
        val indexPlaybackCompletionDate = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_PLAYBACK_COMPLETION_DATE)
        val indexDuration = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_DURATION)
        val indexPosition = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_POSITION)
        val indexSize = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_SIZE)
        val indexMimeType = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_MIME_TYPE)
        val indexFileUrl = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_FILE_URL)
        val indexDownloadUrl = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_DOWNLOAD_URL)
        val indexDownloaded = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_DOWNLOADED)
        val indexPlayedDuration = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_PLAYED_DURATION)
        val indexLastPlayedTime = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_LAST_PLAYED_TIME)

        val mediaId = cursor.getLong(indexId)
        var playbackCompletionDate: Date? = null
        val playbackCompletionTime = cursor.getLong(indexPlaybackCompletionDate)
        if (playbackCompletionTime > 0) {
            playbackCompletionDate = Date(playbackCompletionTime)
        }
        val hasEmbeddedPicture =
            when (cursor.getInt(cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_HAS_EMBEDDED_PICTURE))) {
                1 -> Boolean.TRUE
                0 -> Boolean.FALSE
                else -> null
            }
        return FeedMedia(
            mediaId,
            null,
            cursor.getInt(indexDuration),
            cursor.getInt(indexPosition),
            cursor.getLong(indexSize),
            cursor.getString(indexMimeType),
            cursor.getString(indexFileUrl),
            cursor.getString(indexDownloadUrl),
            cursor.getInt(indexDownloaded) > 0,
            playbackCompletionDate,
            cursor.getInt(indexPlayedDuration),
            hasEmbeddedPicture,
            cursor.getLong(indexLastPlayedTime)
        )
    }
}
