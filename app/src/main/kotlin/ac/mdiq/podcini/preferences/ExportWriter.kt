package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.storage.model.Feed
import android.content.Context
import java.io.IOException
import java.io.Writer

interface ExportWriter {
    @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    fun writeDocument(feeds: List<Feed?>?, writer: Writer?, context: Context)

    fun fileExtension(): String?
}