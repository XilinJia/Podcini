package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.FileNameGenerator.generateFileName
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.format.Formatter
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.FileChannel

class PreferencesTransporter(val prefsDirName: String) {
    val TAG = "PreferencesTransporter"

    @Throws(IOException::class)
    fun exportToDocument(uri: Uri, context: Context) {
        try {
            val chosenDir = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Destination directory is not valid")
            val exportSubDir = chosenDir.createDirectory(prefsDirName) ?: throw IOException("Error creating subdirectory $prefsDirName")
            val sharedPreferencesDir = context.applicationContext.filesDir.parentFile?.listFiles { file -> file.name.startsWith("shared_prefs") }?.firstOrNull()
            if (sharedPreferencesDir != null) {
                sharedPreferencesDir.listFiles()!!.forEach { file ->
                    val destFile = exportSubDir.createFile("text/xml", file.name)
                    if (destFile != null) copyFile(file, destFile, context)
                }
            } else Log.e("Error", "shared_prefs directory not found")
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
        while (inputStream.read(buffer).also { bytesRead = it } != -1) outputStream.write(buffer, 0, bytesRead)
    }
    @Throws(IOException::class)
    fun importBackup(uri: Uri, context: Context) {
        try {
            val exportedDir = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Backup directory is not valid")
            val sharedPreferencesDir = context.applicationContext.filesDir.parentFile?.listFiles { file -> file.name.startsWith("shared_prefs") }?.firstOrNull()
            if (sharedPreferencesDir != null) sharedPreferencesDir.listFiles()?.forEach { file -> file.delete() }
            else Log.e("Error", "shared_prefs directory not found")
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
                    if (destName.contains("PlayerWidgetPrefs")) continue
//                  for importing from Podcini version 5 and below
                    if (!hasPodciniRPrefs) {
                        when {
                            destName.contains("podcini") -> destName = destName.replace("podcini", "podcini.R")
                            destName.contains("EpisodeItemListRecyclerView") -> destName = destName.replace("EpisodeItemListRecyclerView", "EpisodesRecyclerView")
                        }
                    }
                    when {
                        BuildConfig.DEBUG && !destName.contains(".debug") -> destName = destName.replace("podcini.R", "podcini.R.debug")
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

class MediaFilesTransporter(val mediaFilesDirName: String) {
    val TAG = "MediaFilesTransporter"

    var feed: Feed? = null
    private val nameFeedMap: MutableMap<String, Feed> = mutableMapOf()
    private val nameEpisodeMap: MutableMap<String, Episode> = mutableMapOf()
    @Throws(IOException::class)
    fun exportToDocument(uri: Uri, context: Context) {
        try {
            val mediaDir = context.getExternalFilesDir("media") ?: return
            val chosenDir = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Destination directory is not valid")
            val exportSubDir = chosenDir.createDirectory(mediaFilesDirName) ?: throw IOException("Error creating subdirectory $mediaFilesDirName")
            mediaDir.listFiles()?.forEach { file -> copyRecursive(context, file, mediaDir, exportSubDir) }
        } catch (e: IOException) {
            Log.e(TAG, Log.getStackTraceString(e))
            throw e
        } finally { }
    }
    private fun copyRecursive(context: Context, srcFile: File, srcRootDir: File, destRootDir: DocumentFile) {
        val relativePath = srcFile.absolutePath.substring(srcRootDir.absolutePath.length+1)
        if (srcFile.isDirectory) {
            val dirFiles = srcFile.listFiles()
            if (!dirFiles.isNullOrEmpty()) {
                val destDir = destRootDir.findFile(relativePath) ?: destRootDir.createDirectory(relativePath) ?: return
                dirFiles.forEach { file -> copyRecursive(context, file, srcFile, destDir) }
            }
        } else {
            val destFile = destRootDir.createFile("application/octet-stream", relativePath) ?: return
            copyFile(srcFile, destFile, context)
        }
    }
    private fun copyFile(sourceFile: File, destFile: DocumentFile, context: Context) {
        try {
            val outputStream = context.contentResolver.openOutputStream(destFile.uri) ?: return
            val inputStream = FileInputStream(sourceFile)
            copyStream(inputStream, outputStream)
            inputStream.close()
            outputStream.close()
        } catch (e: IOException) {
            Log.e("Error", "Error copying file: $e")
            throw e
        }
    }
    private fun copyRecursive(context: Context, srcFile: DocumentFile, srcRootDir: DocumentFile, destRootDir: File) {
        val relativePath = srcFile.uri.path?.substring(srcRootDir.uri.path!!.length+1) ?: return
        if (srcFile.isDirectory) {
            Logd(TAG, "copyRecursive folder title: $relativePath")
            feed = nameFeedMap[relativePath] ?: return
            Logd(TAG, "copyRecursive found feed: ${feed?.title}")
            nameEpisodeMap.clear()
            feed!!.episodes.forEach { e -> if (!e.title.isNullOrEmpty()) nameEpisodeMap[generateFileName(e.title!!)] = e }
            val destFile = File(destRootDir, relativePath)
            if (!destFile.exists()) destFile.mkdirs()
            srcFile.listFiles().forEach { file -> copyRecursive(context, file, srcFile, destFile) }
        } else {
            val nameParts = relativePath.split(".")
            if (nameParts.size < 3) return
            val ext = nameParts[nameParts.size-1]
            val title = nameParts.dropLast(2).joinToString(".")
            Logd(TAG, "copyRecursive file title: $title")
            val episode = nameEpisodeMap[title] ?: return
            Logd(TAG, "copyRecursive found episode: ${episode.title}")
            val destName = "$title.${episode.id}.$ext"
            val destFile = File(destRootDir, destName)
            if (!destFile.exists()) {
                Logd(TAG, "copyRecursive copying file to: ${destFile.absolutePath}")
                copyFile(srcFile, destFile, context)
                upsertBlk(episode) {
                    it.fileUrl = destFile.absolutePath
                    it.setIsDownloaded()
                }
            }
        }
    }
    private fun copyFile(sourceFile: DocumentFile, destFile: File, context: Context) {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceFile.uri) ?: return
            val outputStream = FileOutputStream(destFile)
            copyStream(inputStream, outputStream)
            inputStream.close()
            outputStream.close()
        } catch (e: IOException) {
            Log.e("Error", "Error copying file: $e")
            throw e
        }
    }
    private fun copyStream(inputStream: InputStream, outputStream: OutputStream) {
        val buffer = ByteArray(1024)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) outputStream.write(buffer, 0, bytesRead)
    }
    @Throws(IOException::class)
    fun importBackup(uri: Uri, context: Context) {
        try {
            val exportedDir = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Backup directory is not valid")
            if (exportedDir.name?.contains(mediaFilesDirName) != true) return
            val mediaDir = context.getExternalFilesDir("media") ?: return
            val fileList = exportedDir.listFiles()
            if (fileList.isNotEmpty()) {
                val feeds = getFeedList()
                feeds.forEach { f -> if (!f.title.isNullOrEmpty()) nameFeedMap[generateFileName(f.title!!)] = f }
                fileList.forEach { file -> copyRecursive(context, file, exportedDir, mediaDir) }
            }
        } catch (e: IOException) {
            Log.e(TAG, Log.getStackTraceString(e))
            throw e
        } finally {
            nameFeedMap.clear()
            nameEpisodeMap.clear()
            feed = null
        }
    }
}

class DatabaseTransporter {
    val TAG = "DatabaseTransporter"

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
            if (pfd != null) try { pfd.close() } catch (e: IOException) { Logd(TAG, "Unable to close ParcelFileDescriptor") }
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
            } else throw IOException("Can not access current database")
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
        } finally { IOUtils.closeQuietly(inputStream) }
    }
}
