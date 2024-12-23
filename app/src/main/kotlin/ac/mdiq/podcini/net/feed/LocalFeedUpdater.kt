package ac.mdiq.podcini.net.feed

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadError
import ac.mdiq.podcini.net.feed.parser.media.id3.ID3ReaderException
import ac.mdiq.podcini.net.feed.parser.media.id3.Id3MetadataReader
import ac.mdiq.podcini.net.feed.parser.media.vorbis.VorbisCommentMetadataReader
import ac.mdiq.podcini.net.feed.parser.media.vorbis.VorbisCommentReaderException
import ac.mdiq.podcini.net.feed.parser.utils.DateUtils
import ac.mdiq.podcini.net.feed.parser.utils.MimeTypeUtils
import ac.mdiq.podcini.storage.database.Feeds
import ac.mdiq.podcini.storage.database.LogsAndStats
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.Episode.MediaMetadataRetrieverCompat
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.annotation.VisibleForTesting
import androidx.documentfile.provider.DocumentFile

import org.apache.commons.io.input.CountingInputStream
import java.io.BufferedInputStream
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

object LocalFeedUpdater {
    private val TAG: String = LocalFeedUpdater::class.simpleName ?: "Anonymous"

    @JvmField
    val PREFERRED_FEED_IMAGE_FILENAMES: Array<String> = arrayOf("folder.jpg", "Folder.jpg", "folder.png", "Folder.png")

     @JvmStatic
    fun updateFeed(feed: Feed, context: Context, updaterProgressListener: UpdaterProgressListener?) {
        if (feed.downloadUrl.isNullOrEmpty()) return
        try {
            val uriString = feed.downloadUrl!!.replace(Feed.PREFIX_LOCAL_FOLDER, "")
            val documentFolder = DocumentFile.fromTreeUri(context, Uri.parse(uriString))
                ?: throw IOException("Unable to retrieve document tree. Try re-connecting the folder on the podcast info page.")
            if (!documentFolder.exists() || !documentFolder.canRead())
                throw IOException("Cannot read local directory. Try re-connecting the folder on the podcast info page.")

            tryUpdateFeed(feed, context, documentFolder.uri, updaterProgressListener)
            if (mustReportDownloadSuccessful(feed)) reportSuccess(feed)
        } catch (e: Exception) {
            e.printStackTrace()
            reportError(feed, e.message)
        }
    }

     @JvmStatic
    @VisibleForTesting
    @Throws(IOException::class)
    fun tryUpdateFeed(feed: Feed, context: Context, folderUri: Uri?, updaterProgressListener: UpdaterProgressListener?) {
        var feed = feed
        //make sure it is the latest 'version' of this feed from the db (all items etc)
        feed = Feeds.updateFeed(context, feed, false)?: feed

        // list files in feed folder
        val allFiles = FastDocumentFile.list(context, folderUri)
        val mediaFiles: MutableList<FastDocumentFile> = ArrayList()
        val mediaFileNames: MutableSet<String> = HashSet()
        for (file in allFiles) {
            val mimeType = MimeTypeUtils.getMimeType(file.type, file.uri.toString()) ?: continue
            val mediaType = MediaType.fromMimeType(mimeType)
            if (mediaType == MediaType.AUDIO || mediaType == MediaType.VIDEO) {
                mediaFiles.add(file)
                mediaFileNames.add(file.name)
            }
        }

        // add new files to feed and update item data
        val newItems = feed.episodes
        for (i in mediaFiles.indices) {
            val oldItem = feedContainsFile(feed, mediaFiles[i].name)
            val newItem = createFeedItem(feed, mediaFiles[i], context)
            oldItem?.updateFromOther(newItem) ?: newItems.add(newItem)
            updaterProgressListener?.onLocalFileScanned(i, mediaFiles.size)
        }
        // remove feed items without corresponding file
        val it = newItems.iterator()
        while (it.hasNext()) {
            val feedItem = it.next()
            if (!mediaFileNames.contains(feedItem.link)) it.remove()
        }
        if (folderUri != null) feed.imageUrl = getImageUrl(allFiles, folderUri)
        if (feed.preferences != null) feed.preferences!!.autoDownload = false
        feed.description = context.getString(R.string.local_feed_description)
        feed.author = context.getString(R.string.local_folder)
        Feeds.updateFeed(context, feed, true)
    }

    /**
     * Returns the image URL for the local feed.
     */
    fun getImageUrl(files: List<FastDocumentFile>, folderUri: Uri): String {
        // look for special file names
        for (iconLocation in PREFERRED_FEED_IMAGE_FILENAMES) {
            for (file in files) {
                if (iconLocation == file.name) return file.uri.toString()
            }
        }
        // use the first image in the folder if existing
        for (file in files) {
            val mime = file.type
            if (mime.startsWith("image/jpeg") || mime.startsWith("image/png")) return file.uri.toString()
        }
        // use default icon as fallback
        return Feed.PREFIX_GENERATIVE_COVER + folderUri
    }

    private fun feedContainsFile(feed: Feed, filename: String): Episode? {
        val items = feed.episodes
        for (i in items) if (i.link == filename) return i
        return null
    }

    private fun createFeedItem(feed: Feed, file: FastDocumentFile, context: Context): Episode {
        val item = Episode(0L, file.name, UUID.randomUUID().toString(), file.name, Date(file.lastModified), PlayState.UNPLAYED.code, feed)
        item.disableAutoDownload()
        val size = file.length
        val media = item.fillMedia(0, 0, size, file.type, file.uri.toString(), file.uri.toString(), false, null, 0, 0)
        for (existingItem in feed.episodes) {
            if (existingItem.downloadUrl == file.uri.toString() && file.length == existingItem.size) {
                // We found an old file that we already scanned. Re-use metadata.
                item.updateFromOther(existingItem)
                return item
            }
        }
        // Did not find existing item. Scan metadata.
        try { loadMetadata(item, file, context) } catch (e: Exception) { item.setDescriptionIfLonger(e.message) }
        return item
    }

    private fun loadMetadata(item: Episode, file: FastDocumentFile, context: Context) {
        MediaMetadataRetrieverCompat().use { mediaMetadataRetriever ->
            mediaMetadataRetriever.setDataSource(context, file.uri)
            val dateStr = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            if (!dateStr.isNullOrEmpty() && "19040101T000000.000Z" != dateStr) {
                try {
                    val simpleDateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.getDefault())
                    item.pubDate = simpleDateFormat.parse(dateStr)?.time ?: 0L
                } catch (e: ParseException) {
                    val date = DateUtils.parse(dateStr)
                    if (date != null) item.pubDate = date.time
                }
            }
            val title = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (!title.isNullOrEmpty()) item.title = title
            val durationStr = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            item.duration = (durationStr!!.toLong().toInt())
            item.hasEmbeddedPicture = (mediaMetadataRetriever.embeddedPicture != null)
            try {
                context.contentResolver.openInputStream(file.uri).use { inputStream ->
                    val reader = Id3MetadataReader(CountingInputStream(BufferedInputStream(inputStream)))
                    reader.readInputStream()
                    item.setDescriptionIfLonger(reader.comment)
                }
            } catch (e: IOException) {
                Logd(TAG, "Unable to parse ID3 of " + file.uri + ": " + e.message)
                try {
                    context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                        val reader = VorbisCommentMetadataReader(inputStream)
                        reader.readInputStream()
                        item.setDescriptionIfLonger(reader.description)
                    }
                } catch (e2: IOException) { Logd(TAG, "Unable to parse vorbis comments of " + file.uri + ": " + e2.message)
                } catch (e2: VorbisCommentReaderException) { Logd(TAG, "Unable to parse vorbis comments of " + file.uri + ": " + e2.message) }
            } catch (e: ID3ReaderException) {
                Logd(TAG, "Unable to parse ID3 of " + file.uri + ": " + e.message)
                try {
                    context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                        val reader = VorbisCommentMetadataReader(inputStream)
                        reader.readInputStream()
                        item.setDescriptionIfLonger(reader.description)
                    }
                } catch (e2: IOException) { Logd(TAG, "Unable to parse vorbis comments of " + file.uri + ": " + e2.message)
                } catch (e2: VorbisCommentReaderException) { Logd(TAG, "Unable to parse vorbis comments of " + file.uri + ": " + e2.message) }
            }
        }
    }

     private fun reportError(feed: Feed, reasonDetailed: String?) {
        val status = DownloadResult(feed.id, feed.title?:"", DownloadError.ERROR_IO_ERROR, false, reasonDetailed?:"")
        LogsAndStats.addDownloadStatus(status)
        Feeds.persistFeedLastUpdateFailed(feed, true)
    }

    /**
     * Reports a successful download status.
     */
     private fun reportSuccess(feed: Feed) {
        val status = DownloadResult(feed.id, feed.title?:"", DownloadError.SUCCESS, true, "")
        LogsAndStats.addDownloadStatus(status)
        Feeds.persistFeedLastUpdateFailed(feed, false)
    }

    /**
     * Answers if reporting success is needed for the given feed.
     */
    private fun mustReportDownloadSuccessful(feed: Feed): Boolean {
        val downloadResults = LogsAndStats.getFeedDownloadLog(feed.id).toMutableList()
        // report success if never reported before
        if (downloadResults.isEmpty()) return true
        downloadResults.sortWith { downloadStatus1: DownloadResult, downloadStatus2: DownloadResult ->
            downloadStatus1.getCompletionDate().compareTo(downloadStatus2.getCompletionDate())
        }
        val lastDownloadResult = downloadResults[downloadResults.size - 1]
        // report success if the last update was not successful
        // (avoid logging success again if the last update was ok)
        return !lastDownloadResult.isSuccessful
    }

    fun interface UpdaterProgressListener {
        fun onLocalFileScanned(scanned: Int, totalFiles: Int)
    }

    /**
     * Android's DocumentFile is slow because every single method call queries the ContentResolver.
     * This queries the ContentResolver a single time with all the information.
     */
    class FastDocumentFile(val name: String, val type: String, val uri: Uri, val length: Long, val lastModified: Long) {
        companion object {
            @JvmStatic
            fun list(context: Context, folderUri: Uri?): List<FastDocumentFile> {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, DocumentsContract.getDocumentId(folderUri))
                val cursor = context.contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)
                val list = ArrayList<FastDocumentFile>()
                while (cursor!!.moveToNext()) {
                    val id = cursor.getString(0)
                    val uri = DocumentsContract.buildDocumentUriUsingTree(folderUri, id)
                    val name = cursor.getString(1)
                    val size = cursor.getLong(2)
                    val lastModified = cursor.getLong(3)
                    val mimeType = cursor.getString(4)
                    list.add(FastDocumentFile(name, mimeType, uri, size, lastModified))
                }
                cursor.close()
                return list
            }
        }
    }
}
