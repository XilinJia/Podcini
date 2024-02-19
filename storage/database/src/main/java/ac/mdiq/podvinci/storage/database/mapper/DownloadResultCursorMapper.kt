package ac.mdiq.podvinci.storage.database.mapper

import android.database.Cursor
import ac.mdiq.podvinci.model.download.DownloadError.Companion.fromCode
import ac.mdiq.podvinci.model.download.DownloadResult
import ac.mdiq.podvinci.storage.database.PodDBAdapter
import java.util.*

/**
 * Converts a [Cursor] to a [DownloadResult] object.
 */
object DownloadResultCursorMapper {
    /**
     * Create a [DownloadResult] instance from a database row (cursor).
     */
    @JvmStatic
    fun convert(cursor: Cursor): DownloadResult {
        val indexId = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_ID)
        val indexTitle = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_DOWNLOADSTATUS_TITLE)
        val indexFeedFile = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_FEEDFILE)
        val indexFileFileType = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_FEEDFILETYPE)
        val indexSuccessful = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_SUCCESSFUL)
        val indexReason = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_REASON)
        val indexCompletionDate = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_COMPLETION_DATE)
        val indexReasonDetailed = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_REASON_DETAILED)

        return DownloadResult(
            cursor.getLong(indexId),
            cursor.getString(indexTitle),
            cursor.getLong(indexFeedFile),
            cursor.getInt(indexFileFileType),
            cursor.getInt(indexSuccessful) > 0,
            fromCode(cursor.getInt(indexReason)),
            Date(cursor.getLong(indexCompletionDate)),
            if (!cursor.isNull(indexReasonDetailed)) cursor.getString(indexReasonDetailed) else ""
        )
    }
}
