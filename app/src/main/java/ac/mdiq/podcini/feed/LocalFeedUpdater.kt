package ac.mdiq.podcini.feed

import ac.mdiq.podcini.R
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.DBTasks
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.util.FastDocumentFile
import ac.mdiq.podcini.storage.model.MediaMetadataRetrieverCompat
import ac.mdiq.podcini.storage.model.download.DownloadError
import ac.mdiq.podcini.storage.model.download.DownloadResult
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.storage.model.playback.MediaType
import ac.mdiq.podcini.feed.parser.util.DateUtils
import ac.mdiq.podcini.feed.parser.util.MimeTypeUtils
import ac.mdiq.podcini.feed.parser.media.id3.ID3ReaderException
import ac.mdiq.podcini.feed.parser.media.id3.Id3MetadataReader
import ac.mdiq.podcini.feed.parser.media.vorbis.VorbisCommentMetadataReader
import ac.mdiq.podcini.feed.parser.media.vorbis.VorbisCommentReaderException
import org.apache.commons.io.input.CountingInputStream
import java.io.BufferedInputStream
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

object LocalFeedUpdater {
    private const val TAG = "LocalFeedUpdater"

    @JvmField
    val PREFERRED_FEED_IMAGE_FILENAMES: Array<String> = arrayOf("folder.jpg", "Folder.jpg", "folder.png", "Folder.png")

    @UnstableApi @JvmStatic
    fun updateFeed(feed: Feed, context: Context,
                   updaterProgressListener: UpdaterProgressListener?
    ) {
        if (feed.download_url.isNullOrEmpty()) return
        try {
            val uriString = feed.download_url!!.replace(Feed.PREFIX_LOCAL_FOLDER, "")
            val documentFolder = DocumentFile.fromTreeUri(context, Uri.parse(uriString))
                ?: throw IOException("Unable to retrieve document tree. "
                        + "Try re-connecting the folder on the podcast info page.")
            if (!documentFolder.exists() || !documentFolder.canRead()) {
                throw IOException("Cannot read local directory. "
                        + "Try re-connecting the folder on the podcast info page.")
            }
            tryUpdateFeed(feed, context, documentFolder.uri, updaterProgressListener)

            if (mustReportDownloadSuccessful(feed)) {
                reportSuccess(feed)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            reportError(feed, e.message)
        }
    }

    @UnstableApi @JvmStatic
    @VisibleForTesting
    @Throws(IOException::class)
    fun tryUpdateFeed(feed: Feed, context: Context, folderUri: Uri?,
                      updaterProgressListener: UpdaterProgressListener?
    ) {
        var feed = feed
        //make sure it is the latest 'version' of this feed from the db (all items etc)
        feed = DBTasks.updateFeed(context, feed, false)?: feed

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
        val newItems = feed.items
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
            if (!mediaFileNames.contains(feedItem.link)) {
                it.remove()
            }
        }

        if (folderUri != null) feed.imageUrl = getImageUrl(allFiles, folderUri)

        if (feed.preferences != null) feed.preferences!!.autoDownload = false
        feed.description = context.getString(R.string.local_feed_description)
        feed.author = context.getString(R.string.local_folder)

        DBTasks.updateFeed(context, feed, true)
    }

    /**
     * Returns the image URL for the local feed.
     */
    fun getImageUrl(files: List<FastDocumentFile>, folderUri: Uri): String {
        // look for special file names
        for (iconLocation in PREFERRED_FEED_IMAGE_FILENAMES) {
            for (file in files) {
                if (iconLocation == file.name) {
                    return file.uri.toString()
                }
            }
        }

        // use the first image in the folder if existing
        for (file in files) {
            val mime = file.type
            if (mime.startsWith("image/jpeg") || mime.startsWith("image/png")) {
                return file.uri.toString()
            }
        }

        // use default icon as fallback
        return Feed.PREFIX_GENERATIVE_COVER + folderUri
    }

    private fun feedContainsFile(feed: Feed, filename: String): FeedItem? {
        val items = feed.items
        for (i in items) {
            if (i.media != null && i.link == filename) {
                return i
            }
        }
        return null
    }

    private fun createFeedItem(feed: Feed, file: FastDocumentFile, context: Context): FeedItem {
        val item = FeedItem(0, file.name, UUID.randomUUID().toString(),
            file.name, Date(file.lastModified), FeedItem.UNPLAYED, feed)
        item.disableAutoDownload()

        val size = file.length
        val media = FeedMedia(0, item, 0, 0, size, file.type,
            file.uri.toString(), file.uri.toString(), false, null, 0, 0)
        item.media = media

        for (existingItem in feed.items) {
            if (existingItem.media != null && existingItem.media!!
                        .download_url == file.uri.toString() && file.length == existingItem.media!!
                        .size) {
                // We found an old file that we already scanned. Re-use metadata.
                item.updateFromOther(existingItem)
                return item
            }
        }

        // Did not find existing item. Scan metadata.
        try {
            loadMetadata(item, file, context)
        } catch (e: Exception) {
            item.setDescriptionIfLonger(e.message)
        }
        return item
    }

    private fun loadMetadata(item: FeedItem, file: FastDocumentFile, context: Context) {
        MediaMetadataRetrieverCompat().use { mediaMetadataRetriever ->
            mediaMetadataRetriever.setDataSource(context, file.uri)
            val dateStr = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            if (!dateStr.isNullOrEmpty() && "19040101T000000.000Z" != dateStr) {
                try {
                    val simpleDateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.getDefault())
                    item.pubDate = simpleDateFormat.parse(dateStr)
                } catch (parseException: ParseException) {
                    val date = DateUtils.parse(dateStr)
                    if (date != null) {
                        item.pubDate = date
                    }
                }
            }

            val title = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (!title.isNullOrEmpty()) {
                item.title = title
            }

            val durationStr = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            item.media!!.setDuration(durationStr!!.toLong().toInt())

            item.media!!.setHasEmbeddedPicture(mediaMetadataRetriever.embeddedPicture != null)
            try {
                context.contentResolver.openInputStream(file.uri).use { inputStream ->
                    val reader = Id3MetadataReader(
                        CountingInputStream(BufferedInputStream(inputStream)))
                    reader.readInputStream()
                    item.setDescriptionIfLonger(reader.comment)
                }
            } catch (e: IOException) {
                Log.d(TAG, "Unable to parse ID3 of " + file.uri + ": " + e.message)

                try {
                    context.contentResolver.openInputStream(file.uri).use { inputStream ->
                        val reader = VorbisCommentMetadataReader(inputStream)
                        reader.readInputStream()
                        item.setDescriptionIfLonger(reader.description)
                    }
                } catch (e2: IOException) {
                    Log.d(TAG, "Unable to parse vorbis comments of " + file.uri + ": " + e2.message)
                } catch (e2: VorbisCommentReaderException) {
                    Log.d(TAG, "Unable to parse vorbis comments of " + file.uri + ": " + e2.message)
                }
            } catch (e: ID3ReaderException) {
                Log.d(TAG, "Unable to parse ID3 of " + file.uri + ": " + e.message)

                try {
                    context.contentResolver.openInputStream(file.uri).use { inputStream ->
                        val reader = VorbisCommentMetadataReader(inputStream)
                        reader.readInputStream()
                        item.setDescriptionIfLonger(reader.description)
                    }
                } catch (e2: IOException) {
                    Log.d(TAG, "Unable to parse vorbis comments of " + file.uri + ": " + e2.message)
                } catch (e2: VorbisCommentReaderException) {
                    Log.d(TAG, "Unable to parse vorbis comments of " + file.uri + ": " + e2.message)
                }
            }
        }
    }

    @UnstableApi private fun reportError(feed: Feed, reasonDetailed: String?) {
        val status = DownloadResult(feed, feed.title?:"",
            DownloadError.ERROR_IO_ERROR, false, reasonDetailed?:"")
        DBWriter.addDownloadStatus(status)
        DBWriter.setFeedLastUpdateFailed(feed.id, true)
    }

    /**
     * Reports a successful download status.
     */
    @UnstableApi private fun reportSuccess(feed: Feed) {
        val status = DownloadResult(feed, feed.title?:"", DownloadError.SUCCESS, true, "")
        DBWriter.addDownloadStatus(status)
        DBWriter.setFeedLastUpdateFailed(feed.id, false)
    }

    /**
     * Answers if reporting success is needed for the given feed.
     */
    private fun mustReportDownloadSuccessful(feed: Feed): Boolean {
        val downloadResults = DBReader.getFeedDownloadLog(feed.id).toMutableList()

        if (downloadResults.isEmpty()) {
            // report success if never reported before
            return true
        }

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
}
