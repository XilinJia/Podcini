package ac.mdiq.podcini.preferences.screens

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.PodciniApp.Companion.forceRestart
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.sync.SyncService.Companion.isValidGuid
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.model.EpisodeAction.Companion.readFromJsonObject
import ac.mdiq.podcini.net.sync.model.SyncServiceException
import ac.mdiq.podcini.preferences.*
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlElement
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlWriter
import ac.mdiq.podcini.storage.database.Episodes.getEpisodeByGuidOrUrl
import ac.mdiq.podcini.storage.database.Episodes.getEpisodes
import ac.mdiq.podcini.storage.database.Episodes.hasAlmostEnded
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.utils.FileNameGenerator.generateFileName
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.OpmlImportSelectionDialog
import ac.mdiq.podcini.ui.compose.TitleSummaryActionColumn
import ac.mdiq.podcini.util.Logd
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.format.Formatter
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ShareCompat.IntentBuilder
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.json.JSONArray
import java.io.*
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.Throws

@Composable
fun ImportExportPreferencesScreen(activity: PreferenceActivity) {
    val TAG = "ImportExportPreferencesScreen"
    val backupDirName = "Podcini-Backups"
    var prefsDirName = "Podcini-Prefs"
    val mediaFilesDirName = "Podcini-MediaFiles"

    class PreferencesTransporter {
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
    class MediaFilesTransporter {
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
                        it.media?.fileUrl = destFile.absolutePath
                        it.media?.setIsDownloaded()
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
    class EpisodeProgressReader {
        fun readDocument(reader: Reader) {
            val jsonString = reader.readText()
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val jsonAction = jsonArray.getJSONObject(i)
                Logd(TAG, "Loaded EpisodeActions message: $i $jsonAction")
                val action = readFromJsonObject(jsonAction) ?: continue
                Logd(TAG, "processing action: $action")
                val result = processEpisodeAction(action) ?: continue
//                upsertBlk(result.second) {}
            }
        }
        private fun processEpisodeAction(action: EpisodeAction): Pair<Long, Episode>? {
            val guid = if (isValidGuid(action.guid)) action.guid else null
            var feedItem = getEpisodeByGuidOrUrl(guid, action.episode?:"", false) ?: return null
            if (feedItem.media == null) {
                Logd(TAG, "Feed item has no media: $action")
                return null
            }
            var idRemove = 0L
            feedItem = upsertBlk(feedItem) {
                it.media!!.startPosition = action.started * 1000
                it.media!!.setPosition(action.position * 1000)
                it.media!!.playedDuration = action.playedDuration * 1000
                it.media!!.setLastPlayedTime(action.timestamp!!.time)
                it.rating = if (action.isFavorite) Rating.SUPER.code else Rating.UNRATED.code
                it.playState = action.playState
                if (hasAlmostEnded(it.media!!)) {
                    Logd(TAG, "Marking as played: $action")
                    it.setPlayed(true)
                    it.media!!.setPosition(0)
                    idRemove = it.id
                } else Logd(TAG, "Setting position: $action")
            }
            return Pair(idRemove, feedItem)
        }
    }
    class EpisodesProgressWriter : ExportWriter {
        @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
        override fun writeDocument(feeds: List<Feed>, writer: Writer, context: Context) {
            Logd(TAG, "Starting to write document")
            val queuedEpisodeActions: MutableList<EpisodeAction> = mutableListOf()
            val pausedItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.paused.name), EpisodeSortOrder.DATE_NEW_OLD)
            val readItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.played.name), EpisodeSortOrder.DATE_NEW_OLD)
            val favoriteItems = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.superb.name), EpisodeSortOrder.DATE_NEW_OLD)
            val comItems = mutableSetOf<Episode>()
            comItems.addAll(pausedItems)
            comItems.addAll(readItems)
            comItems.addAll(favoriteItems)
            Logd(TAG, "Save state for all " + comItems.size + " played episodes")
            for (item in comItems) {
                val media = item.media ?: continue
                val played = EpisodeAction.Builder(item, EpisodeAction.PLAY)
                    .timestamp(Date(media.getLastPlayedTime()))
                    .started(media.startPosition / 1000)
                    .position(media.getPosition() / 1000)
                    .playedDuration(media.playedDuration / 1000)
                    .total(media.getDuration() / 1000)
                    .isFavorite(item.isSUPER)
                    .playState(item.playState)
                    .build()
                queuedEpisodeActions.add(played)
            }
            if (queuedEpisodeActions.isNotEmpty()) {
                try {
                    Logd(TAG, "Saving ${queuedEpisodeActions.size} actions: ${StringUtils.join(queuedEpisodeActions, ", ")}")
                    val list = JSONArray()
                    for (episodeAction in queuedEpisodeActions) {
                        val obj = episodeAction.writeToJsonObject()
                        if (obj != null) {
                            Logd(TAG, "saving EpisodeAction: $obj")
                            list.put(obj)
                        }
                    }
                    writer.write(list.toString())
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw SyncServiceException(e)
                }
            }
            Logd(TAG, "Finished writing document")
        }
        override fun fileExtension(): String {
            return "json"
        }
    }
    class FavoritesWriter : ExportWriter {
        private val FAVORITE_TEMPLATE = "html-export-favorites-item-template.html"
        private val FEED_TEMPLATE = "html-export-feed-template.html"
        private val UTF_8 = "UTF-8"
        @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
        override fun writeDocument(feeds: List<Feed>, writer: Writer, context: Context) {
            Logd(TAG, "Starting to write document")
            val templateStream = context.assets.open("html-export-template.html")
            var template = IOUtils.toString(templateStream, UTF_8)
            template = template.replace("\\{TITLE\\}".toRegex(), "Favorites")
            val templateParts = template.split("\\{FEEDS\\}".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val favTemplateStream = context.assets.open(FAVORITE_TEMPLATE)
            val favTemplate = IOUtils.toString(favTemplateStream, UTF_8)
            val feedTemplateStream = context.assets.open(FEED_TEMPLATE)
            val feedTemplate = IOUtils.toString(feedTemplateStream, UTF_8)
            val allFavorites = getEpisodes(0, Int.MAX_VALUE, EpisodeFilter(EpisodeFilter.States.superb.name), EpisodeSortOrder.DATE_NEW_OLD)
            val favoritesByFeed = buildFeedMap(allFavorites)
            writer.append(templateParts[0])
            for (feedId in favoritesByFeed.keys) {
                val favorites: List<Episode> = favoritesByFeed[feedId]!!
                if (favorites[0].feed == null) continue
                writer.append("<li><div>\n")
                writeFeed(writer, favorites[0].feed!!, feedTemplate)
                writer.append("<ul>\n")
                for (item in favorites) writeFavoriteItem(writer, item, favTemplate)
                writer.append("</ul></div></li>\n")
            }
            writer.append(templateParts[1])
            Logd(TAG, "Finished writing document")
        }
        /**
         * Group favorite episodes by feed, sorting them by publishing date in descending order.
         * @param favoritesList `List` of all favorite episodes.
         * @return A `Map` favorite episodes, keyed by feed ID.
         */
        private fun buildFeedMap(favoritesList: List<Episode>): Map<Long, MutableList<Episode>> {
            val feedMap: MutableMap<Long, MutableList<Episode>> = TreeMap()
            for (item in favoritesList) {
                var feedEpisodes = feedMap[item.feedId]
                if (feedEpisodes == null) {
                    feedEpisodes = ArrayList()
                    if (item.feedId != null) feedMap[item.feedId!!] = feedEpisodes
                }
                feedEpisodes.add(item)
            }
            return feedMap
        }
        @Throws(IOException::class)
        private fun writeFeed(writer: Writer, feed: Feed, feedTemplate: String) {
            val feedInfo = feedTemplate
                .replace("{FEED_IMG}", feed.imageUrl?:"")
                .replace("{FEED_TITLE}", feed.title?:" No title")
                .replace("{FEED_LINK}", feed.link?: "")
                .replace("{FEED_WEBSITE}", feed.downloadUrl?:"")
            writer.append(feedInfo)
        }
        @Throws(IOException::class)
        private fun writeFavoriteItem(writer: Writer, item: Episode, favoriteTemplate: String) {
            var favItem = favoriteTemplate.replace("{FAV_TITLE}", item.title!!.trim { it <= ' ' })
            favItem = if (item.link != null) favItem.replace("{FAV_WEBSITE}", item.link!!)
            else favItem.replace("{FAV_WEBSITE}", "")
            favItem =
                if (item.media != null && item.media!!.downloadUrl != null) favItem.replace("{FAV_MEDIA}", item.media!!.downloadUrl!!)
                else favItem.replace("{FAV_MEDIA}", "")
            writer.append(favItem)
        }
        override fun fileExtension(): String {
            return "html"
        }
    }
    class HtmlWriter : ExportWriter {
        /**
         * Takes a list of feeds and a writer and writes those into an HTML document.
         */
        @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
        override fun writeDocument(feeds: List<Feed>, writer: Writer, context: Context) {
            Logd(TAG, "Starting to write document")
            val templateStream = context.assets.open("html-export-template.html")
            var template = IOUtils.toString(templateStream, "UTF-8")
            template = template.replace("\\{TITLE\\}".toRegex(), "Subscriptions")
            val templateParts = template.split("\\{FEEDS\\}".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            writer.append(templateParts[0])
            for (feed in feeds) {
                writer.append("<li><div><img src=\"")
                writer.append(feed.imageUrl)
                writer.append("\" /><p>")
                writer.append(feed.title)
                writer.append(" <span><a href=\"")
                writer.append(feed.link)
                writer.append("\">Website</a> • <a href=\"")
                writer.append(feed.downloadUrl)
                writer.append("\">Feed</a></span></p></div></li>\n")
            }
            writer.append(templateParts[1])
            Logd(TAG, "Finished writing document")
        }
        override fun fileExtension(): String {
            return "html"
        }
    }

    var showProgress by remember { mutableStateOf(false) }
    fun isJsonFile(uri: Uri): Boolean {
        val fileName = uri.lastPathSegment ?: return false
        return fileName.endsWith(".json", ignoreCase = true)
    }
    fun isRealmFile(uri: Uri): Boolean {
        val fileName = uri.lastPathSegment ?: return false
        return fileName.trim().endsWith(".realm", ignoreCase = true)
    }
    fun isComboDir(uri: Uri): Boolean {
        val fileName = uri.lastPathSegment ?: return false
        return fileName.contains(backupDirName, ignoreCase = true)
    }
    fun showExportSuccessSnackbar(uri: Uri?, mimeType: String?) {
        Snackbar.make(activity.findViewById(android.R.id.content), R.string.export_success_title, Snackbar.LENGTH_LONG)
            .setAction(R.string.share_label) { IntentBuilder(activity).setType(mimeType).addStream(uri!!).setChooserTitle(R.string.share_label).startChooser() }
            .show()
    }
    fun dateStampFilename(fname: String): String {
        return String.format(fname, SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    }
    val showImporSuccessDialog = remember { mutableStateOf(false) }
    ComfirmDialog(titleRes = R.string.successful_import_label, message = stringResource(R.string.import_ok), showDialog = showImporSuccessDialog, cancellable = false) { forceRestart() }

    val showImporErrortDialog = remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf("") }
    ComfirmDialog(titleRes = R.string.import_export_error_label, message = importErrorMessage, showDialog = showImporErrortDialog) {}

    fun exportWithWriter(exportWriter: ExportWriter, uri: Uri?, exportType: ExportTypes) {
        val context: Context? = activity
        showProgress = true
        if (uri == null) {
            activity.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val output = ExportWorker(exportWriter, activity).exportFile()
                    withContext(Dispatchers.Main) {
                        val fileUri = FileProvider.getUriForFile(context!!.applicationContext, context.getString(R.string.provider_authority), output!!)
                        showExportSuccessSnackbar(fileUri, exportType.contentType)
                    }
                } catch (e: Exception) {
                    showProgress = false
                    importErrorMessage = e.message?:"Reason unknown"
                    showImporErrortDialog.value = true
                } finally { showProgress = false }
            }
        } else {
            activity.lifecycleScope.launch(Dispatchers.IO) {
                val worker = DocumentFileExportWorker(exportWriter, context!!, uri)
                try {
                    val output = worker.exportFile()
                    withContext(Dispatchers.Main) { showExportSuccessSnackbar(output.uri, exportType.contentType) }
                } catch (e: Exception) {
                    showProgress = false
                    importErrorMessage = e.message?:"Reason unknown"
                    showImporErrortDialog.value = true
                } finally { showProgress = false }
            }
        }
    }

    val chooseOpmlExportPathLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data == null) return@rememberLauncherForActivityResult
        val uri = result.data!!.data!!
        exportWithWriter(OpmlWriter(), uri, ExportTypes.OPML)
    }
    val chooseHtmlExportPathLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data == null) return@rememberLauncherForActivityResult
        val uri = result.data!!.data!!
        exportWithWriter(HtmlWriter(), uri, ExportTypes.HTML)
    }
    val chooseFavoritesExportPathLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data == null) return@rememberLauncherForActivityResult
        val uri = result.data!!.data!!
        exportWithWriter(FavoritesWriter(), uri, ExportTypes.FAVORITES)
    }
    val chooseProgressExportPathLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data == null) return@rememberLauncherForActivityResult
        val uri = result.data!!.data!!
        exportWithWriter(EpisodesProgressWriter(), uri, ExportTypes.PROGRESS)
    }
    val restoreProgressLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data?.data == null) return@rememberLauncherForActivityResult
        val uri = result.data!!.data
        uri?.let {
            if (isJsonFile(uri)) {
                showProgress = true
                activity.lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val inputStream: InputStream? = activity.contentResolver.openInputStream(uri)
                            val reader = BufferedReader(InputStreamReader(inputStream))
                            EpisodeProgressReader().readDocument(reader)
                            reader.close()
                        }
                        withContext(Dispatchers.Main) {
                            showImporSuccessDialog.value = true
//                                showImportSuccessDialog()
                            showProgress = false
                        }
                    } catch (e: Throwable) {
                        showProgress = false
                        importErrorMessage = e.message?:"Reason unknown"
                        showImporErrortDialog.value = true
                    }
                }
            } else {
                val message = activity.getString(R.string.import_file_type_toast) + ".json"
                showProgress = false
                importErrorMessage = message
                showImporErrortDialog.value = true
            }
        }
    }
    var showOpmlImportSelectionDialog by remember { mutableStateOf(false) }
    val readElements = remember { mutableStateListOf<OpmlElement>() }
    val chooseOpmlImportPathLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        Logd(TAG, "chooseOpmlImportPathResult: uri: $uri")
        OpmlTransporter.startImport(activity, uri) {
            readElements.addAll(it)
            Logd(TAG, "readElements: ${readElements.size}")
        }
//        showImportSuccessDialog()
        showOpmlImportSelectionDialog = true
    }

    var comboRootUri by remember { mutableStateOf<Uri?>(null) }
    val comboDic = remember { mutableStateMapOf<String, Boolean>() }
    var showComboImportDialog by remember { mutableStateOf(false) }
    if (showComboImportDialog) {
        AlertDialog(onDismissRequest = { showComboImportDialog = false },
            title = { Text(stringResource(R.string.pref_select_properties), style = CustomTextStyles.titleCustom) },
            text = {
                Column {
                    comboDic.keys.forEach { option ->
                        if (option != "Media files" || comboDic["Database"] != true) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = comboDic[option] == true, onCheckedChange = {
                                comboDic[option] = it
                                if (option == "Database" && it) comboDic["Media files"] = false
                            })
                            Text(option, modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (comboDic["Media files"] != null && comboDic["Database"] == true) Text(stringResource(R.string.pref_import_media_files_later), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = comboRootUri!!
                    showProgress = true
                    activity.lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val rootFile = DocumentFile.fromTreeUri(activity, uri)
                                if (rootFile != null && rootFile.isDirectory) {
                                    Logd(TAG, "comboDic[\"Preferences\"] ${comboDic["Preferences"]}")
                                    Logd(TAG, "comboDic[\"Media files\"] ${comboDic["Media files"]}")
                                    Logd(TAG, "comboDic[\"Database\"] ${comboDic["Database"]}")
                                    for (child in rootFile.listFiles()) {
                                        if (child.isDirectory) {
                                            if (child.name == prefsDirName) {
                                                if (comboDic["Preferences"] == true) PreferencesTransporter().importBackup(child.uri, activity)
                                            } else if (child.name == mediaFilesDirName) {
                                                if (comboDic["Media files"] == true) MediaFilesTransporter().importBackup(child.uri, activity)
                                            }
                                        } else if (isRealmFile(child.uri) && comboDic["Database"] == true) DatabaseTransporter().importBackup(child.uri, activity)
                                    }
                                }
                            }
                            withContext(Dispatchers.Main) {
                                showImporSuccessDialog.value = true
                                showProgress = false
                            }
                        } catch (e: Throwable) {
                            showProgress = false
                            importErrorMessage = e.message?:"Reason unknown"
                            showImporErrortDialog.value = true
                        }
                    }
                    showComboImportDialog = false
                }) { Text(text = "OK") }
            },
            dismissButton = { TextButton(onClick = { showComboImportDialog = false }) { Text(text = "Cancel") } }
        )
    }
    var showComboExportDialog by remember { mutableStateOf(false) }
    if (showComboExportDialog) {
        AlertDialog(onDismissRequest = { showComboExportDialog = false },
            title = { Text(stringResource(R.string.pref_select_properties), style = CustomTextStyles.titleCustom) },
            text = {
                Column {
                    comboDic.keys.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = comboDic[option] == true, onCheckedChange = { comboDic[option] = it })
                            Text(option, modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = comboRootUri!!
                    showProgress = true
                    activity.lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            val chosenDir = DocumentFile.fromTreeUri(activity, uri) ?: throw IOException("Destination directory is not valid")
                            val exportSubDir = chosenDir.createDirectory(dateStampFilename("$backupDirName-%s")) ?: throw IOException("Error creating subdirectory $backupDirName")
                            val subUri: Uri = exportSubDir.uri
                            if (comboDic["Preferences"] == true) PreferencesTransporter().exportToDocument(subUri, activity)
                            if (comboDic["Media files"] == true) MediaFilesTransporter().exportToDocument(subUri, activity)
                            if (comboDic["Database"] == true) {
                                val realmFile = exportSubDir.createFile("application/octet-stream", "backup.realm")
                                if (realmFile != null) DatabaseTransporter().exportToDocument(realmFile.uri, activity)
                            }
                        }
                        withContext(Dispatchers.Main) { showProgress = false }
                    }
                    showComboExportDialog = false
                }) { Text(text = "OK") }
            },
            dismissButton = { TextButton(onClick = { showComboExportDialog = false }) { Text(text = "Cancel") } }
        )
    }

    val restoreComboLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data?.data == null) return@rememberLauncherForActivityResult
        val uri = result.data!!.data!!
        if (isComboDir(uri)) {
            val rootFile = DocumentFile.fromTreeUri(activity, uri)
            if (rootFile != null && rootFile.isDirectory) {
                comboDic.clear()
                for (child in rootFile.listFiles()) {
                    Logd(TAG, "restoreComboLauncher child: ${child.isDirectory} ${child.name} ${child.uri} ")
                    if (child.isDirectory) {
                        if (child.name == prefsDirName) comboDic["Preferences"] = true
                        else if (child.name == mediaFilesDirName) comboDic["Media files"] = false
                    } else if (isRealmFile(child.uri)) comboDic["Database"] = true
                }
            }
            comboRootUri = uri
            showComboImportDialog = true
        } else {
            val message = activity.getString(R.string.import_directory_toast) + backupDirName
            showProgress = false
            importErrorMessage = message
            showImporErrortDialog.value = true
        }
    }
    val backupComboLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            val uri: Uri? = it.data?.data
            if (uri != null) {
                comboDic.clear()
                comboDic["Database"] = true
                comboDic["Preferences"] = true
                comboDic["Media files"] = true
                comboRootUri = uri
                showComboExportDialog = true
            }
        }
    }

    fun launchExportCombos() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        backupComboLauncher.launch(intent)
    }

    fun openExportPathPicker(exportType: ExportTypes, result: ActivityResultLauncher<Intent>, writer: ExportWriter) {
        val title = dateStampFilename(exportType.outputNameTemplate)
        val intentPickAction = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(exportType.contentType)
            .putExtra(Intent.EXTRA_TITLE, title)
        try {
            result.launch(intentPickAction)
            return
        } catch (e: ActivityNotFoundException) { Log.e(TAG, "No activity found. Should never happen...") }
        // If we are using a SDK lower than API 21 or the implicit intent failed fallback to the legacy export process
        exportWithWriter(writer, null, exportType)
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    if (showProgress) {
        Dialog(onDismissRequest = { showProgress = false }) {
            Surface(modifier = Modifier.size(100.dp), shape = RoundedCornerShape(8.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = {0.7f}, strokeWidth = 10.dp, color = textColor, modifier = Modifier.size(50.dp).align(Alignment.TopCenter))
                    Text("Loading...", color = textColor, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
    val scrollState = rememberScrollState()
//        supportActionBar?.setTitle(R.string.import_export_pref)
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
        TitleSummaryActionColumn(R.string.combo_export_label, R.string.combo_export_summary) { launchExportCombos() }
        val showComboImportDialog = remember { mutableStateOf(false) }
        ComfirmDialog(titleRes = R.string.combo_import_label, message = stringResource(R.string.combo_import_warning), showDialog = showComboImportDialog) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            restoreComboLauncher.launch(intent)
        }
        TitleSummaryActionColumn(R.string.combo_import_label, R.string.combo_import_summary) { showComboImportDialog.value = true }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().height(2.dp).padding(top = 20.dp, bottom = 20.dp))
        TitleSummaryActionColumn(R.string.opml_export_label, R.string.opml_export_summary) { openExportPathPicker(ExportTypes.OPML, chooseOpmlExportPathLauncher, OpmlWriter()) }
        if (showOpmlImportSelectionDialog) OpmlImportSelectionDialog(readElements) { showOpmlImportSelectionDialog = false }
        TitleSummaryActionColumn(R.string.opml_import_label, R.string.opml_import_summary) {
            try { chooseOpmlImportPathLauncher.launch("*/*") } catch (e: ActivityNotFoundException) { Log.e(TAG, "No activity found. Should never happen...") } }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().height(2.dp).padding(top = 20.dp, bottom = 20.dp))
        TitleSummaryActionColumn(R.string.progress_export_label, R.string.progress_export_summary) { openExportPathPicker(ExportTypes.PROGRESS, chooseProgressExportPathLauncher, EpisodesProgressWriter()) }
        val showProgressImportDialog = remember { mutableStateOf(false) }
        ComfirmDialog(titleRes = R.string.progress_import_label, message = stringResource(R.string.progress_import_warning), showDialog = showProgressImportDialog) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.setType("*/*")
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            restoreProgressLauncher.launch(intent)
        }
        TitleSummaryActionColumn(R.string.progress_import_label, R.string.progress_import_summary) { showProgressImportDialog.value = true }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().height(2.dp).padding(top = 20.dp, bottom = 20.dp))
        TitleSummaryActionColumn(R.string.html_export_label, R.string.html_export_summary) { openExportPathPicker(ExportTypes.HTML, chooseHtmlExportPathLauncher, HtmlWriter()) }
        TitleSummaryActionColumn(R.string.favorites_export_label, R.string.favorites_export_summary) { openExportPathPicker(ExportTypes.FAVORITES, chooseFavoritesExportPathLauncher, FavoritesWriter()) }
    }
}
