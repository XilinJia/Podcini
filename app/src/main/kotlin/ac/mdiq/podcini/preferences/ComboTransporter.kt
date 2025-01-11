package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.StorageUtils.customMediaUriString
import ac.mdiq.podcini.storage.utils.StorageUtils.generateFileName
import ac.mdiq.podcini.storage.utils.StorageUtils.getMimeType
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
    fun exportToUri(uri: Uri, context: Context, move: Boolean = false, useSubDir: Boolean = true) {
        try {
            val chosenDir = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Destination directory is not valid")
            val exportSubDir = if (useSubDir) chosenDir.createDirectory(mediaFilesDirName) ?: throw IOException("Error creating subdirectory $mediaFilesDirName") else chosenDir
            val feeds = getFeedList()
            feeds.forEach { f -> if (!f.title.isNullOrEmpty()) nameFeedMap[generateFileName(f.title!!)] = f }
            if (customMediaUriString.isNotBlank()) {
                val customUri = Uri.parse(customMediaUriString)
                val directory = DocumentFile.fromTreeUri(getAppContext(), customUri) ?: throw IllegalArgumentException("Invalid tree URI: $customMediaUriString")
                directory.listFiles().forEach { file -> copyRecursive(context, file, directory, exportSubDir, move) }
            } else {
                val mediaDir = context.getExternalFilesDir("media") ?: return
                mediaDir.listFiles()?.forEach { file -> copyRecursive(context, file, mediaDir, exportSubDir, move) }
            }
        } catch (e: IOException) {
            Log.e(TAG, Log.getStackTraceString(e))
            throw e
        } finally { }
    }
    private fun copyRecursive(context: Context, srcFile: File, srcRootDir: File, destRootDir: DocumentFile, move: Boolean) {
        val relativePath = srcFile.absolutePath.substring(srcRootDir.absolutePath.length+1)
//        val relativePath = srcFile.name
        if (srcFile.isDirectory) {
            feed = nameFeedMap[relativePath] ?: return
            Logd(TAG, "copyRecursiveFD found feed: ${feed?.title}")
            nameEpisodeMap.clear()
            feed!!.episodes.forEach { e -> if (!e.title.isNullOrEmpty()) nameEpisodeMap[generateFileName(e.title!!)] = e }
            nameEpisodeMap.keys.forEach { Logd(TAG, "key: $it") }
            val dirFiles = srcFile.listFiles()
            Logd(TAG, "srcFile: ${srcFile.name} dirFiles: ${dirFiles?.size}")
            if (!dirFiles.isNullOrEmpty()) {
                val destDir = destRootDir.findFile(relativePath) ?: destRootDir.createDirectory(relativePath) ?: return
                dirFiles.forEach { file -> copyRecursive(context, file, srcFile, destDir, move) }
            }
            if (move) srcFile.delete()
        } else {
            val nameParts = relativePath.split(".")
            if (nameParts.size < 3) return
            val ext = nameParts[nameParts.size-1]
            val title = nameParts.dropLast(2).joinToString(".")
            Logd(TAG, "copyRecursiveFD file title: $title")
            val episode = nameEpisodeMap[title] ?: return
            Logd(TAG, "copyRecursiveFD found episode: ${episode.title}")
            val destName = "$title.${episode.id}.$ext"
            val destFile = destRootDir.createFile(getMimeType(destName), relativePath) ?: return
            Logd(TAG, "copyRecursiveFD copying file to: ${destFile.uri}")
            copyFile(srcFile, destFile, context, move)
            if (move) {
                upsertBlk(episode) {
                    it.fileUrl = destFile.uri.toString()
                    it.setIsDownloaded()
                }
            }
        }
    }
    private fun copyFile(sourceFile: File, destFile: DocumentFile, context: Context, move: Boolean) {
        try {
            val outputStream = context.contentResolver.openOutputStream(destFile.uri) ?: return
            val inputStream = FileInputStream(sourceFile)
            copyStream(inputStream, outputStream)
            inputStream.close()
            outputStream.close()
            if (move) sourceFile.delete()
        } catch (e: IOException) {
            Log.e("Error", "Error copying file: $e")
            throw e
        }
    }
    private fun copyRecursive(context: Context, srcFile: DocumentFile, srcRootDir: DocumentFile, destRootDir: DocumentFile, move: Boolean, onlyUpdateDB: Boolean = false) {
        val relativePath = srcFile.uri.path?.substring(srcRootDir.uri.path!!.length+1) ?: return
        if (srcFile.isDirectory) {
            Logd(TAG, "copyRecursiveDD folder title: $relativePath")
            feed = nameFeedMap[relativePath] ?: return
            Logd(TAG, "copyRecursiveDD found feed: ${feed?.title}")
            nameEpisodeMap.clear()
            feed!!.episodes.forEach { e -> if (!e.title.isNullOrEmpty()) nameEpisodeMap[generateFileName(e.title!!)] = e }
            nameEpisodeMap.keys.forEach { Logd(TAG, "key: $it") }
            val destDir = destRootDir.findFile(relativePath) ?: destRootDir.createDirectory(relativePath) ?: return
            val files = srcFile.listFiles()
            if (files.isNotEmpty()) files.forEach { file -> copyRecursive(context, file, srcFile, destDir, move) }
            if (!onlyUpdateDB && move) srcFile.delete()
        } else {
            val nameParts = relativePath.split(".")
            if (nameParts.size < 3) return
            val ext = nameParts[nameParts.size-1]
            val title = nameParts.dropLast(2).joinToString(".")
            Logd(TAG, "copyRecursiveDD file title: $title")
            val episode = nameEpisodeMap[title] ?: return
            Logd(TAG, "copyRecursiveDD found episode: ${episode.title}")
            val destName = "$title.${episode.id}.$ext"
            val destFile = destRootDir.createFile(getMimeType(destName), destName) ?: return
            Logd(TAG, "copyRecursiveDD copying file to: ${destFile.uri}")
            if (!onlyUpdateDB) copyFile(srcFile, destFile, context, move)
            upsertBlk(episode) {
                it.fileUrl = destFile.uri.toString()
                it.setIsDownloaded()
            }
        }
    }
    private fun copyFile(sourceFile: DocumentFile, destFile: DocumentFile, context: Context, move: Boolean) {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceFile.uri) ?: return
            val outputStream = context.contentResolver.openOutputStream(destFile.uri) ?: return
            copyStream(inputStream, outputStream)
            inputStream.close()
            outputStream.close()
            if (move) sourceFile.delete()
        } catch (e: IOException) {
            Log.e("Error", "Error copying file: $e")
            throw e
        }
    }
    private fun copyRecursive(context: Context, srcFile: DocumentFile, srcRootDir: DocumentFile, destRootDir: File, move: Boolean) {
        val relativePath = srcFile.uri.path?.substring(srcRootDir.uri.path!!.length+1) ?: return
        if (srcFile.isDirectory) {
            Logd(TAG, "copyRecursiveDF folder title: $relativePath")
            feed = nameFeedMap[relativePath] ?: return
            Logd(TAG, "copyRecursiveDF found feed: ${feed?.title}")
            nameEpisodeMap.clear()
            feed!!.episodes.forEach { e -> if (!e.title.isNullOrEmpty()) nameEpisodeMap[generateFileName(e.title!!)] = e }
            val destFile = File(destRootDir, relativePath)
            if (!destFile.exists()) destFile.mkdirs()
//            srcFile.listFiles().forEach { file -> copyRecursive(context, file, srcFile, destFile, move) }
            val files = srcFile.listFiles()
            if (files.isNotEmpty()) files.forEach { file -> copyRecursive(context, file, srcFile, destFile, move) }
            if (move) srcFile.delete()
        } else {
            val nameParts = relativePath.split(".")
            if (nameParts.size < 3) return
            val ext = nameParts[nameParts.size-1]
            val title = nameParts.dropLast(2).joinToString(".")
            Logd(TAG, "copyRecursiveDF file title: $title")
            val episode = nameEpisodeMap[title] ?: return
            Logd(TAG, "copyRecursiveDF found episode: ${episode.title}")
            val destName = "$title.${episode.id}.$ext"
            val destFile = File(destRootDir, destName)
            if (!destFile.exists()) {
                Logd(TAG, "copyRecursiveDF copying file to: ${destFile.absolutePath}")
                copyFile(srcFile, destFile, context, move)
                upsertBlk(episode) {
                    it.fileUrl = Uri.fromFile(destFile).toString()
                    Logd(TAG, "copyRecursiveDF fileUrl: ${it.fileUrl}")
                    it.setIsDownloaded()
                }
            }
        }
    }
    private fun copyFile(sourceFile: DocumentFile, destFile: File, context: Context, move: Boolean) {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceFile.uri) ?: return
            val outputStream = FileOutputStream(destFile)
            copyStream(inputStream, outputStream)
            inputStream.close()
            outputStream.close()
            if (move) sourceFile.delete()
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
    private fun copyRecursive(srcFile: File, srcRootDir: File, destRootDir: File, move: Boolean, onlyUpdateDB: Boolean = false) {
        val relativePath = srcFile.absolutePath.substring(srcRootDir.absolutePath.length+1)
//        val relativePath = srcFile.name
        if (srcFile.isDirectory) {
            feed = nameFeedMap[relativePath] ?: return
            Logd(TAG, "copyRecursiveFD found feed: ${feed?.title}")
            nameEpisodeMap.clear()
            feed!!.episodes.forEach { e -> if (!e.title.isNullOrEmpty()) nameEpisodeMap[generateFileName(e.title!!)] = e }
            nameEpisodeMap.keys.forEach { Logd(TAG, "key: $it") }
            val destFile = File(destRootDir, relativePath)
            if (!destFile.exists()) destFile.mkdirs()
            val files = srcFile.listFiles()
            if (!files.isNullOrEmpty()) files.forEach { file -> copyRecursive(file, srcFile, destFile, move) }
            if (!onlyUpdateDB && move) srcFile.delete()
        } else {
            val nameParts = relativePath.split(".")
            if (nameParts.size < 3) return
            val ext = nameParts[nameParts.size-1]
            val title = nameParts.dropLast(2).joinToString(".")
            Logd(TAG, "copyRecursiveFD file title: $title")
            val episode = nameEpisodeMap[title] ?: return
            Logd(TAG, "copyRecursiveFD found episode: ${episode.title}")
            val destName = "$title.${episode.id}.$ext"
            val destFile = File(destRootDir, destName)
            if (!destFile.exists()) {
                Logd(TAG, "copyRecursiveDF copying file to: ${destFile.absolutePath}")
                if (!onlyUpdateDB) copyFile(srcFile, destFile, move)
                upsertBlk(episode) {
                    it.fileUrl = Uri.fromFile(destFile).toString()
                    Logd(TAG, "copyRecursiveDF fileUrl: ${it.fileUrl}")
                    it.setIsDownloaded()
                }
            }
        }
    }
    private fun copyFile(sourceFile: File, destFile: File, move: Boolean) {
        try {
            val inputStream = FileInputStream(sourceFile)
            val outputStream = FileOutputStream(destFile)
            copyStream(inputStream, outputStream)
            inputStream.close()
            outputStream.close()
            if (move) sourceFile.delete()
        } catch (e: IOException) {
            Log.e("Error", "Error copying file: $e")
            throw e
        }
    }
    @Throws(IOException::class)
    fun importFromUri(uri: Uri, context: Context, move: Boolean = false, verify : Boolean = true) {
        try {
            val exportedDir = DocumentFile.fromTreeUri(context, uri) ?: throw IOException("Backup directory is not valid")
            if (verify && exportedDir.name?.contains(mediaFilesDirName) != true) return
            val fileList = exportedDir.listFiles()
            if (fileList.isNotEmpty()) {
                val feeds = getFeedList()
                feeds.forEach { f -> if (!f.title.isNullOrEmpty()) nameFeedMap[generateFileName(f.title!!)] = f }
                Logd(TAG, "importFromUri customMediaUriString: [$customMediaUriString]")
                if (customMediaUriString.isNotBlank()) {
                    val customUri = Uri.parse(customMediaUriString)
                    val directory = DocumentFile.fromTreeUri(getAppContext(), customUri) ?: throw IllegalArgumentException("Invalid tree URI: $customMediaUriString")
                    fileList.forEach { file -> copyRecursive(context, file, exportedDir, directory, move) }
                } else {
                    val mediaDir = context.getExternalFilesDir("media") ?: return
                    fileList.forEach { file -> copyRecursive(context, file, exportedDir, mediaDir, move) }
                }
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
    @Throws(IOException::class)
    fun updateDB(context: Context) {
        try {
            val feeds = getFeedList()
            feeds.forEach { f -> if (!f.title.isNullOrEmpty()) nameFeedMap[generateFileName(f.title!!)] = f }
            Logd(TAG, "importFromUri customMediaUriString: [$customMediaUriString]")
            if (customMediaUriString.isNotBlank()) {
                val customUri = Uri.parse(customMediaUriString)
                val directory = DocumentFile.fromTreeUri(getAppContext(), customUri) ?: throw IllegalArgumentException("Invalid tree URI: $customMediaUriString")
                val exportedDir = directory
                val fileList = exportedDir.listFiles()
                fileList.forEach { file -> copyRecursive(context, file, exportedDir, directory, false, true) }
            } else {
                val mediaDir = context.getExternalFilesDir("media") ?: return
                val exportedDir = mediaDir
                val fileList = exportedDir.listFiles()
                fileList?.forEach { file -> copyRecursive(file, exportedDir, mediaDir, false, true) }
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
