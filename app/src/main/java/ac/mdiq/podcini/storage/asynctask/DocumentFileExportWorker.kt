package ac.mdiq.podcini.storage.asynctask

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import ac.mdiq.podcini.storage.export.ExportWriter
import ac.mdiq.podcini.storage.DBReader
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.Charset

/**
 * Writes an OPML file into the user selected export directory in the background.
 */
class DocumentFileExportWorker(private val exportWriter: ExportWriter, private val context: Context, private val outputFileUri: Uri) {

    fun exportObservable(): Observable<DocumentFile?> {
        val output = DocumentFile.fromSingleUri(context, outputFileUri)
        return Observable.create { subscriber: ObservableEmitter<DocumentFile?> ->
            var outputStream: OutputStream? = null
            var writer: OutputStreamWriter? = null
            try {
                if (output == null) throw IOException()
                val uri = output.uri
                outputStream = context.contentResolver.openOutputStream(uri, "wt")
                if (outputStream == null) throw IOException()
                writer = OutputStreamWriter(outputStream, Charset.forName("UTF-8"))
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
                if (outputStream != null) {
                    try {
                        outputStream.close()
                    } catch (e: IOException) {
                        subscriber.onError(e)
                    }
                }
                subscriber.onComplete()
            }
        }
    }
}
