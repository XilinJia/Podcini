package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.FilesUtils.getDataFolder
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.Charset

enum class ExportTypes(val contentType: String, val outputNameTemplate: String, @field:StringRes val labelResId: Int) {
    OPML("text/x-opml", "podcini-feeds-%s.opml", R.string.opml_export_label),
    OPML_SELECTED("text/x-opml", "podcini-feeds-selected-%s.opml", R.string.opml_export_label),
    HTML("text/html", "podcini-feeds-%s.html", R.string.html_export_label),
    FAVORITES("text/html", "podcini-favorites-%s.html", R.string.favorites_export_label),
    PROGRESS("text/x-json", "podcini-progress-%s.json", R.string.progress_export_label),
}

/**
 * Writes an OPML file into the export directory in the background.
 */
class ExportWorker private constructor(private val exportWriter: ExportWriter, private val output: File, private val context: Context) {
    constructor(exportWriter: ExportWriter, context: Context) : this(exportWriter, File(getDataFolder(EXPORT_DIR),
        DEFAULT_OUTPUT_NAME + "." + exportWriter.fileExtension()), context)
    suspend fun exportFile(feeds: List<Feed>? = null): File? {
        return withContext(Dispatchers.IO) {
            if (output.exists()) {
                val success = output.delete()
                Logd(TAG, "Overwriting previously exported file: $success")
            }
            var writer: OutputStreamWriter? = null
            try {
                writer = OutputStreamWriter(FileOutputStream(output), Charset.forName("UTF-8"))
                val feeds_ = feeds ?: getFeedList()
                Logd(TAG, "feeds_: ${feeds_.size}")
                exportWriter.writeDocument(feeds_, writer, context)
                output // return the output file
            } catch (e: IOException) {
                Log.e(TAG, "Error during file export", e)
                null // return null in case of error
            } finally { writer?.close() }
        }
    }
    companion object {
        private const val EXPORT_DIR = "export/"
        private val TAG: String = ExportWorker::class.simpleName ?: "Anonymous"
        private const val DEFAULT_OUTPUT_NAME = "podcini-feeds"
    }
}

class DocumentFileExportWorker(private val exportWriter: ExportWriter, private val context: Context, private val outputFileUri: Uri) {
    suspend fun exportFile(feeds: List<Feed>? = null): DocumentFile {
        return withContext(Dispatchers.IO) {
            val output = DocumentFile.fromSingleUri(context, outputFileUri)
            var outputStream: OutputStream? = null
            var writer: OutputStreamWriter? = null
            try {
                if (output == null) throw IOException()
                val uri = output.uri
                outputStream = context.contentResolver.openOutputStream(uri, "wt")
                if (outputStream == null) throw IOException()
                writer = OutputStreamWriter(outputStream, Charset.forName("UTF-8"))
                val feeds_ = feeds ?: getFeedList()
                Logd("DocumentFileExportWorker", "feeds_: ${feeds_.size}")
                exportWriter.writeDocument(feeds_, writer, context)
                output
            } catch (e: IOException) { throw e
            } finally {
                if (writer != null) try { writer.close() } catch (e: IOException) { throw e }
                if (outputStream != null) try { outputStream.close() } catch (e: IOException) { throw e }
            }
        }
    }
}
