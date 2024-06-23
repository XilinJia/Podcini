package ac.mdiq.podcini.storage.transport

import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.format.Formatter
import android.util.Log
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import java.io.*
import java.nio.channels.FileChannel

object DatabaseTransporter {
    private val TAG: String = DatabaseTransporter::class.simpleName ?: "Anonymous"

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
                    Logd(TAG, "Unable to close ParcelFileDescriptor")
                }
            }
        }
    }

    @Throws(IOException::class)
    fun exportToStream(outFileStream: FileOutputStream, context: Context) {
        var src: FileChannel? = null
        var dst: FileChannel? = null
        try {
            val realmPath = realm.configuration.path
            Logd(TAG, "exportToStream realmPath: $realmPath")
            val currentDB = File(realmPath)

            if (currentDB.exists()) {
                src = FileInputStream(currentDB).channel
                dst = outFileStream.channel
                val srcSize = src.size()
                dst.transferFrom(src, 0, srcSize)

                val newDstSize = dst.size()
                if (newDstSize != srcSize)
                    throw IOException(String.format("Unable to write entire database. Expected to write %s, but wrote %s.", Formatter.formatShortFileSize(context, srcSize), Formatter.formatShortFileSize(context, newDstSize)))
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
        val TEMP_DB_NAME = "temp.realm"
        var inputStream: InputStream? = null
        try {
            val tempDB = context.getDatabasePath(TEMP_DB_NAME)
            inputStream = context.contentResolver.openInputStream(inputUri!!)
            FileUtils.copyInputStreamToFile(inputStream, tempDB)

            val realmPath = realm.configuration.path
            val currentDB = File(realmPath)
            val success = currentDB.delete()
            if (!success) throw IOException("Unable to delete old database")

            FileUtils.moveFile(tempDB, currentDB)
        } catch (e: IOException) {
            Log.e(TAG, Log.getStackTraceString(e))
            throw e
        } finally {
            IOUtils.closeQuietly(inputStream)
        }
    }
}
