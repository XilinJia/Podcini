package ac.mdiq.podcini.storage.asynctask

import android.content.Context
import android.util.Log
import ac.mdiq.podcini.storage.export.ExportWriter
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.preferences.UserPreferences.getDataFolder
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.Charset

/**
 * Writes an OPML file into the export directory in the background.
 */
class ExportWorker private constructor(private val exportWriter: ExportWriter,
                                       private val output: File,
                                       private val context: Context
) {
    constructor(exportWriter: ExportWriter, context: Context) : this(exportWriter, File(getDataFolder(EXPORT_DIR),
        DEFAULT_OUTPUT_NAME + "." + exportWriter.fileExtension()), context)

    fun exportObservable(): Observable<File?> {
        if (output.exists()) {
            val success = output.delete()
            Log.w(TAG, "Overwriting previously exported file: $success")
        }
        return Observable.create { subscriber: ObservableEmitter<File?> ->
            var writer: OutputStreamWriter? = null
            try {
                writer = OutputStreamWriter(FileOutputStream(output), Charset.forName("UTF-8"))
                exportWriter.writeDocument(DBReader.getFeedList(), writer, context)
                subscriber.onNext(output)
            } catch (e: IOException) {
                subscriber.onError(e)
            } finally {
                if (writer != null) {
                    try {
                        writer.close()
                    } catch (e: IOException) {
                        subscriber.onError(e)
                    }
                }
                subscriber.onComplete()
            }
        }
    }

    companion object {
        private const val EXPORT_DIR = "export/"
        private const val TAG = "ExportWorker"
        private const val DEFAULT_OUTPUT_NAME = "podcini-feeds"
    }
}
