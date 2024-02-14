package ac.mdiq.podvinci.storage.database.mapper

import android.database.Cursor
import ac.mdiq.podvinci.model.feed.Chapter
import ac.mdiq.podvinci.storage.database.PodDBAdapter

/**
 * Converts a [Cursor] to a [Chapter] object.
 */
object ChapterCursorMapper {
    /**
     * Create a [Chapter] instance from a database row (cursor).
     */
    @JvmStatic
    fun convert(cursor: Cursor): Chapter {
        val indexId = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_ID)
        val indexTitle = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_TITLE)
        val indexStart = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_START)
        val indexLink = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_LINK)
        val indexImage = cursor.getColumnIndexOrThrow(PodDBAdapter.KEY_IMAGE_URL)

        val id = cursor.getLong(indexId)
        val title = cursor.getString(indexTitle)
        val start = cursor.getLong(indexStart)
        val link = cursor.getString(indexLink)
        val imageUrl = cursor.getString(indexImage)
        val chapter = Chapter(start, title, link, imageUrl)
        chapter.id = id
        return chapter
    }
}
