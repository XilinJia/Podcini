package ac.mdiq.podcini.core.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import ac.mdiq.podcini.core.feed.ChapterMerger.merge
import ac.mdiq.podcini.core.service.download.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.core.storage.DBReader
import ac.mdiq.podcini.core.util.comparator.ChapterStartTimeComparator
import ac.mdiq.podcini.model.feed.Chapter
import ac.mdiq.podcini.model.feed.FeedMedia
import ac.mdiq.podcini.model.playback.Playable
import ac.mdiq.podcini.parser.feed.PodcastIndexChapterParser
import ac.mdiq.podcini.parser.media.id3.ChapterReader
import ac.mdiq.podcini.parser.media.id3.ID3ReaderException
import ac.mdiq.podcini.parser.media.vorbis.VorbisCommentChapterReader
import ac.mdiq.podcini.parser.media.vorbis.VorbisCommentReaderException
import okhttp3.CacheControl
import okhttp3.Request
import okhttp3.Request.Builder
import okhttp3.Response
import org.apache.commons.io.input.CountingInputStream
import java.io.*
import java.util.*

/**
 * Utility class for getting chapter data from media files.
 */
object ChapterUtils {
    private const val TAG = "ChapterUtils"

    @JvmStatic
    fun getCurrentChapterIndex(media: Playable?, position: Int): Int {
        if (media?.getChapters() == null || media.getChapters().isEmpty()) {
            return -1
        }
        val chapters = media.getChapters()
        for (i in chapters.indices) {
            if (chapters[i].start > position) {
                return i - 1
            }
        }
        return chapters.size - 1
    }

    @JvmStatic
    fun loadChapters(playable: Playable, context: Context, forceRefresh: Boolean) {
        if (playable.getChapters().isNotEmpty() && !forceRefresh) {
            // Already loaded
            return
        }

        var chaptersFromDatabase: List<Chapter>? = null
        var chaptersFromPodcastIndex: List<Chapter>? = null
        if (playable is FeedMedia) {
            if (playable.getItem() == null) {
                playable.setItem(DBReader.getFeedItem(playable.itemId))
            }
            val item = playable.getItem()
            if (item != null) {
                if (item.hasChapters()) {
                    chaptersFromDatabase = DBReader.loadChaptersOfFeedItem(item)
                }

                if (!item.podcastIndexChapterUrl.isNullOrEmpty()) {
                    chaptersFromPodcastIndex = loadChaptersFromUrl(item.podcastIndexChapterUrl!!, forceRefresh)
                }
            }
        }

        val chaptersFromMediaFile = loadChaptersFromMediaFile(playable, context)
        val chaptersMergePhase1 = merge(chaptersFromDatabase, chaptersFromMediaFile)
        val chapters = merge(chaptersMergePhase1, chaptersFromPodcastIndex)
        if (chapters == null) {
            // Do not try loading again. There are no chapters.
            playable.setChapters(listOf())
        } else {
            playable.setChapters(chapters)
        }
    }

    fun loadChaptersFromMediaFile(playable: Playable, context: Context): List<Chapter> {
        try {
            openStream(playable, context).use { inVal ->
                val chapters = readId3ChaptersFrom(inVal)
                if (chapters.isNotEmpty()) {
                    Log.i(TAG, "Chapters loaded")
                    return chapters
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Unable to load ID3 chapters: " + e.message)
        } catch (e: ID3ReaderException) {
            Log.e(TAG, "Unable to load ID3 chapters: " + e.message)
        }

        try {
            openStream(playable, context).use { inVal ->
                val chapters = readOggChaptersFromInputStream(inVal)
                if (chapters.isNotEmpty()) {
                    Log.i(TAG, "Chapters loaded")
                    return chapters
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Unable to load vorbis chapters: " + e.message)
        } catch (e: VorbisCommentReaderException) {
            Log.e(TAG, "Unable to load vorbis chapters: " + e.message)
        }
        return listOf()
    }

    @Throws(IOException::class)
    private fun openStream(playable: Playable, context: Context): CountingInputStream {
        if (playable.localFileAvailable()) {
            if (playable.getLocalMediaUrl() == null) {
                throw IOException("No local url")
            }
            val source = File(playable.getLocalMediaUrl()?:"")
            if (!source.exists()) {
                throw IOException("Local file does not exist")
            }
            return CountingInputStream(BufferedInputStream(FileInputStream(source)))
        } else if (playable.getStreamUrl() != null && playable.getStreamUrl()!!.startsWith(ContentResolver.SCHEME_CONTENT)) {
            val uri = Uri.parse(playable.getStreamUrl())
            return CountingInputStream(BufferedInputStream(context.contentResolver.openInputStream(uri)))
        } else {
            val request: Request = Builder().url(playable.getStreamUrl()?:"").build()
            val response = getHttpClient().newCall(request).execute()
            if (response.body == null) {
                throw IOException("Body is null")
            }
            return CountingInputStream(BufferedInputStream(response.body!!.byteStream()))
        }
    }

    fun loadChaptersFromUrl(url: String, forceRefresh: Boolean): List<Chapter>? {
        if (forceRefresh) {
            return loadChaptersFromUrl(url, CacheControl.FORCE_NETWORK)
        }
        val cachedChapters = loadChaptersFromUrl(url, CacheControl.FORCE_CACHE)
        if (cachedChapters == null || cachedChapters.size <= 1) {
            // Some publishers use one dummy chapter before actual chapters are available
            return loadChaptersFromUrl(url, CacheControl.FORCE_NETWORK)
        }
        return cachedChapters
    }

    private fun loadChaptersFromUrl(url: String, cacheControl: CacheControl): List<Chapter>? {
        var response: Response? = null
        try {
            val request: Request = Builder().url(url).cacheControl(cacheControl).build()
            response = getHttpClient().newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                return PodcastIndexChapterParser.parse(response.body!!.string())
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            response?.close()
        }
        return null
    }

    @Throws(IOException::class, ID3ReaderException::class)
    private fun readId3ChaptersFrom(inVal: CountingInputStream): List<Chapter> {
        val reader = ChapterReader(inVal)
        reader.readInputStream()
        var chapters = reader.getChapters()
        chapters = chapters.sortedWith(ChapterStartTimeComparator())
        enumerateEmptyChapterTitles(chapters)
        if (!chaptersValid(chapters)) {
            Log.i(TAG, "Chapter data was invalid")
            return emptyList()
        }
        return chapters
    }

    @Throws(VorbisCommentReaderException::class)
    private fun readOggChaptersFromInputStream(input: InputStream): List<Chapter> {
        val reader = VorbisCommentChapterReader(BufferedInputStream(input))
        reader.readInputStream()
        var chapters = reader.getChapters()
        chapters = chapters.sortedWith(ChapterStartTimeComparator())
        enumerateEmptyChapterTitles(chapters)
        if (chaptersValid(chapters)) {
            return chapters
        }
        return emptyList()
    }

    /**
     * Makes sure that chapter does a title and an item attribute.
     */
    private fun enumerateEmptyChapterTitles(chapters: List<Chapter>) {
        for (i in chapters.indices) {
            val c = chapters[i]
            if (c.title == null) {
                c.title = i.toString()
            }
        }
    }

    private fun chaptersValid(chapters: List<Chapter>): Boolean {
        if (chapters.isEmpty()) {
            return false
        }
        for (c in chapters) {
            if (c.start < 0) {
                return false
            }
        }
        return true
    }
}
