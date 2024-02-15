package ac.mdiq.podvinci.core.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.format.Formatter
import android.util.Log
import ac.mdiq.podvinci.core.R
import ac.mdiq.podvinci.storage.database.PodDBAdapter
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel

object DatabaseTransporter {
    private const val TAG = "DatabaseExporter"
    private const val TEMP_DB_NAME = PodDBAdapter.DATABASE_NAME + "_tmp"

    @Throws(IOException::class)
    fun exportToDocument(uri: Uri?, context: Context) {
        var pfd: ParcelFileDescriptor? = null
        var fileOutputStream: FileOutputStream? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(uri!!, "wt")
            fileOutputStream = FileOutputStream(pfd!!.fileDescriptor)
            exportToStream(fileOutputStream, context)
        } catch (e: IOException) {
            Log.e(TAG, Log.getStackTraceString(e))
            throw e
        } finally {
            IOUtils.closeQuietly(fileOutputStream)

            if (pfd != null) {
                try {
                    pfd.close()
                } catch (e: IOException) {
                    Log.d(TAG, "Unable to close ParcelFileDescriptor")
                }
            }
        }
    }

    @Throws(IOException::class)
    fun exportToStream(outFileStream: FileOutputStream, context: Context) {
        var src: FileChannel? = null
        var dst: FileChannel? = null
        try {
            val currentDB = context.getDatabasePath(PodDBAdapter.DATABASE_NAME)

            if (currentDB.exists()) {
                src = FileInputStream(currentDB).channel
                dst = outFileStream.channel
                val srcSize = src.size()
                dst.transferFrom(src, 0, srcSize)

                val newDstSize = dst.size()
                if (newDstSize != srcSize) {
                    throw IOException(String.format(
                        "Unable to write entire database. Expected to write %s, but wrote %s.",
                        Formatter.formatShortFileSize(context, srcSize),
                        Formatter.formatShortFileSize(context, newDstSize)))
                }
            } else {
                throw IOException("Can not access current database")
            }
        } catch (e: IOException) {
            Log.e(TAG, Log.getStackTraceString(e))
            throw e
        } finally {
            IOUtils.closeQuietly(src)
            IOUtils.closeQuietly(dst)
        }
    }

    @Throws(IOException::class)
    fun importBackup(inputUri: Uri?, context: Context) {
        var inputStream: InputStream? = null
        try {
            val tempDB = context.getDatabasePath(TEMP_DB_NAME)
            inputStream = context.contentResolver.openInputStream(inputUri!!)
            FileUtils.copyInputStreamToFile(inputStream, tempDB)

            val db = SQLiteDatabase.openDatabase(tempDB.absolutePath,
                null, SQLiteDatabase.OPEN_READONLY)
            if (db.version > PodDBAdapter.VERSION) {
                throw IOException(context.getString(R.string.import_no_downgrade))
            }
            db.close()

            val currentDB = context.getDatabasePath(PodDBAdapter.DATABASE_NAME)
            val success = currentDB.delete()
            if (!success) {
                throw IOException("Unable to delete old database")
            }
            FileUtils.moveFile(tempDB, currentDB)
        } catch (e: IOException) {
            Log.e(TAG, Log.getStackTraceString(e))
            throw e
        } catch (e: SQLiteException) {
            Log.e(TAG, Log.getStackTraceString(e))
            throw e
        } finally {
            IOUtils.closeQuietly(inputStream)
        }
    }
}
