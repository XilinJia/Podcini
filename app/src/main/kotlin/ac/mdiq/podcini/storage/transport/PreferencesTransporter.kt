package ac.mdiq.podcini.storage.transport

import ac.mdiq.podcini.BuildConfig
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.*

object PreferencesTransporter {
    private val TAG: String = PreferencesTransporter::class.simpleName ?: "Anonymous"

    @Throws(IOException::class)
    fun exportToDocument(uri: Uri, context: Context) {
        try {
            val chosenDir = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Destination directory is not valid")
            val exportSubDir = chosenDir.createDirectory("Podcini-Prefs") ?: throw IOException("Error creating subdirectory Podcini-Prefs")
            val sharedPreferencesDir = context.applicationContext.filesDir.parentFile?.listFiles { file ->
                file.name.startsWith("shared_prefs")
            }?.firstOrNull()
            if (sharedPreferencesDir != null) {
                sharedPreferencesDir.listFiles()!!.forEach { file ->
                    val destFile = exportSubDir.createFile("text/xml", file.name)
                    if (destFile != null) copyFile(file, destFile, context)
                }
            } else {
                Log.e("Error", "shared_prefs directory not found")
            }
        } catch (e: IOException) {
            Log.e(TAG, Log.getStackTraceString(e))
            throw e
        } finally { }
    }

    private fun copyFile(sourceFile: File, destFile: DocumentFile, context: Context) {
        try {
            val inputStream = FileInputStream(sourceFile)
            val outputStream = context.contentResolver.openOutputStream(destFile.uri)
            if (outputStream != null) copyStream(inputStream, outputStream)
            inputStream.close()
            outputStream?.close()
        } catch (e: IOException) {
            Log.e("Error", "Error copying file: $e")
            throw e
        }
    }

    private fun copyFile(sourceFile: DocumentFile, destFile: File, context: Context) {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceFile.uri)
            val outputStream = FileOutputStream(destFile)
            if (inputStream != null) copyStream(inputStream, outputStream)
            inputStream?.close()
            outputStream.close()
        } catch (e: IOException) {
            Log.e("Error", "Error copying file: $e")
            throw e
        }
    }

    private fun copyStream(inputStream: InputStream, outputStream: OutputStream) {
        val buffer = ByteArray(1024)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
        }
    }

    @Throws(IOException::class)
    fun importBackup(uri: Uri, context: Context) {
        try {
            val exportedDir = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Backup directory is not valid")
            val sharedPreferencesDir = context.applicationContext.filesDir.parentFile?.listFiles { file ->
                file.name.startsWith("shared_prefs")
            }?.firstOrNull()
            if (sharedPreferencesDir != null) {
                sharedPreferencesDir.listFiles()?.forEach { file ->
//                    val prefName = file.name.substring(0, file.name.lastIndexOf('.'))
                    file.delete()
                }
            } else {
                Log.e("Error", "shared_prefs directory not found")
            }
            val files = exportedDir.listFiles()
            var hasPodciniRPrefs = false
            for (file in files) {
                if (file?.isFile == true && file.name?.endsWith(".xml") == true && file.name!!.contains("podcini.R")) {
                    hasPodciniRPrefs = true
                    break
                }
            }
            for (file in files) {
                if (file?.isFile == true && file.name?.endsWith(".xml") == true) {
                    var destName = file.name!!
//                  for importing from Podcini version 5 and below
                    if (!hasPodciniRPrefs) {
                        when {
                            destName.contains("podcini") -> destName = destName.replace("podcini", "podcini.R")
                            destName.contains("EpisodeItemListRecyclerView") -> destName = destName.replace("EpisodeItemListRecyclerView", "EpisodesRecyclerView")
                        }
                    }
                    when {
//                  for debug version importing release version
                        BuildConfig.DEBUG && !destName.contains(".debug") -> destName = destName.replace("podcini.R", "podcini.R.debug")
//                  for release version importing debug version
                        !BuildConfig.DEBUG && destName.contains(".debug") -> destName = destName.replace(".debug", "")
                    }
                    val destFile = File(sharedPreferencesDir, destName)
                    copyFile(file, destFile, context)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, Log.getStackTraceString(e))
            throw e
        } finally { }

    }
}
