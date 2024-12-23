package ac.mdiq.podcini.storage.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.storage.model.Chapter

import ac.mdiq.podcini.net.feed.parser.media.id3.ChapterReader
import ac.mdiq.podcini.net.feed.parser.media.id3.ID3ReaderException
import ac.mdiq.podcini.net.feed.parser.media.vorbis.VorbisCommentChapterReader
import ac.mdiq.podcini.net.feed.parser.media.vorbis.VorbisCommentReaderException
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.util.Logd
import okhttp3.CacheControl
import okhttp3.Request
import okhttp3.Request.Builder
import okhttp3.Response
import org.apache.commons.io.input.CountingInputStream
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.util.*
import kotlin.math.abs

/**
 * Utility class for getting chapter data from media files.
 */
object ChapterUtils {
    private val TAG: String = ChapterUtils::class.simpleName ?: "Anonymous"

    @JvmStatic
    fun getCurrentChapterIndex(media: Episode?, position: Int): Int {
        val chapters = media?.chapters
        if (chapters.isNullOrEmpty()) return -1
        for (i in chapters.indices) if (chapters[i].start > position) return i - 1
        return chapters.size - 1
    }

    @JvmStatic
    fun loadChapters(playable: Episode, context: Context, forceRefresh: Boolean) {
        // Already loaded
        if (!forceRefresh) return
        var chaptersFromDatabase: List<Chapter>? = null
        var chaptersFromPodcastIndex: List<Chapter>? = null
        val item = playable
        if (item.chapters.isNotEmpty()) chaptersFromDatabase = item.chapters
        if (!item.podcastIndexChapterUrl.isNullOrEmpty()) chaptersFromPodcastIndex = loadChaptersFromUrl(item.podcastIndexChapterUrl!!, forceRefresh)

        val chaptersFromMediaFile = loadChaptersFromMediaFile(playable, context)
        val chaptersMergePhase1 = merge(chaptersFromDatabase, chaptersFromMediaFile)
        val chapters = merge(chaptersMergePhase1, chaptersFromPodcastIndex)
        Logd(TAG, "loadChapters chapters size: ${chapters?.size?:0} ${playable.getEpisodeTitle()}")
        if (chapters == null) playable.setChapters(listOf())    // Do not try loading again. There are no chapters.
        else playable.setChapters(chapters)
    }

    fun loadChaptersFromMediaFile(playable: Episode, context: Context): List<Chapter> {
        try {
            openStream(playable, context).use { inVal ->
                val chapters = readId3ChaptersFrom(inVal)
                if (chapters.isNotEmpty()) return chapters
            }
        } catch (e: IOException) { Log.e(TAG, "Unable to load ID3 chapters: " + e.message)
        } catch (e: ID3ReaderException) { Log.e(TAG, "Unable to load ID3 chapters: " + e.message) }

        try {
            openStream(playable, context).use { inVal ->
                val chapters = readOggChaptersFromInputStream(inVal)
                if (chapters.isNotEmpty()) return chapters
            }
        } catch (e: IOException) { Log.e(TAG, "Unable to load vorbis chapters: " + e.message)
        } catch (e: VorbisCommentReaderException) { Log.e(TAG, "Unable to load vorbis chapters: " + e.message) }
        return listOf()
    }

    @Throws(IOException::class)
    private fun openStream(playable: Episode, context: Context): CountingInputStream {
        if (playable.localFileAvailable()) {
            if (playable.fileUrl == null) throw IOException("No local url")
            val source = File(playable.fileUrl ?: "")
            if (!source.exists()) throw IOException("Local file does not exist")
            return CountingInputStream(BufferedInputStream(FileInputStream(source)))
        } else {
            val streamurl = playable.downloadUrl
            if (streamurl != null && streamurl.startsWith(ContentResolver.SCHEME_CONTENT)) {
                val uri = Uri.parse(streamurl)
                return CountingInputStream(BufferedInputStream(context.contentResolver.openInputStream(uri)))
            } else {
                if (streamurl.isNullOrEmpty()) throw IOException("stream url is null of empty")
                val request: Request = Builder().url(streamurl).build()
                val response = getHttpClient().newCall(request).execute()
                if (response.body == null) throw IOException("Body is null")
                return CountingInputStream(BufferedInputStream(response.body!!.byteStream()))
            }
        }
    }

    fun loadChaptersFromUrl(url: String, forceRefresh: Boolean): List<Chapter> {
        if (forceRefresh) return loadChaptersFromUrl(url, CacheControl.FORCE_NETWORK)
        val cachedChapters = loadChaptersFromUrl(url, CacheControl.FORCE_CACHE)
        // Some publishers use one dummy chapter before actual chapters are available
        if (cachedChapters.size <= 1) return loadChaptersFromUrl(url, CacheControl.FORCE_NETWORK)
        return cachedChapters
    }

    private fun loadChaptersFromUrl(url: String, cacheControl: CacheControl): List<Chapter> {
        var response: Response? = null
        try {
            val request: Request = Builder().url(url).cacheControl(cacheControl).build()
            response = getHttpClient().newCall(request).execute()
            if (response.isSuccessful && response.body != null) return parse(response.body!!.string())
        } catch (e: IOException) { e.printStackTrace()
        } finally { response?.close() }
        return listOf()
    }

    @Throws(IOException::class, ID3ReaderException::class)
    private fun readId3ChaptersFrom(inVal: CountingInputStream): List<Chapter> {
        val reader = ChapterReader(inVal)
        reader.readInputStream()
        var chapters = reader.getChapters()
        chapters = chapters.sortedWith(ChapterStartTimeComparator())
        enumerateEmptyChapterTitles(chapters)
        if (!chaptersValid(chapters)) {
            Logd(TAG, "Chapter data was invalid")
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
        if (chaptersValid(chapters)) return chapters
        return emptyList()
    }

    /**
     * Makes sure that chapter does a title and an item attribute.
     */
    private fun enumerateEmptyChapterTitles(chapters: List<Chapter>) {
        for (i in chapters.indices) {
            val c = chapters[i]
            if (c.title == null) c.title = i.toString()
        }
    }

    private fun chaptersValid(chapters: List<Chapter>): Boolean {
        if (chapters.isEmpty()) return false
        for (c in chapters) if (c.start < 0) return false
        return true
    }

    /**
     * This method might modify the input data.
     */
    fun merge(chapters1: List<Chapter>?, chapters2: List<Chapter>?): List<Chapter>? {
        Logd(TAG, "Merging chapters")
        when {
            chapters1 == null -> return chapters2
            chapters2 == null -> return chapters1
            chapters2.size > chapters1.size -> return chapters2
            chapters2.size < chapters1.size -> return chapters1
            else -> {
                // Merge chapter lists of same length. Store in chapters2 array.
                // In case the lists can not be merged, return chapters1 array.
                for (i in chapters2.indices) {
                    val chapterTarget = chapters2[i]
                    val chapterOther = chapters1[i]

                    if (abs((chapterTarget.start - chapterOther.start).toDouble()) > 1000) {
                        Log.e(TAG, "Chapter lists are too different. Cancelling merge.")
                        return if (score(chapters1) > score(chapters2)) chapters1 else chapters2
                    }

                    if (chapterTarget.imageUrl.isNullOrEmpty()) chapterTarget.imageUrl = chapterOther.imageUrl
                    if (chapterTarget.link.isNullOrEmpty()) chapterTarget.link = chapterOther.link
                    if (chapterTarget.title.isNullOrEmpty()) chapterTarget.title = chapterOther.title
                }
                return chapters2
            }
        }
    }

    /**
     * Tries to give a score that can determine which list of chapters a user might want to see.
     */
    private fun score(chapters: List<Chapter>): Int {
        var score = 0
        for (chapter in chapters) {
            score = (score
                    + (if (chapter.title.isNullOrEmpty()) 0 else 1)
                    + (if (chapter.link.isNullOrEmpty()) 0 else 1)
                    + (if (chapter.imageUrl.isNullOrEmpty()) 0 else 1))
        }
        return score
    }

    fun parse(jsonStr: String): List<Chapter> {
        try {
            val chapters: MutableList<Chapter> = ArrayList()
            val obj = JSONObject(jsonStr)
            val objChapters = obj.getJSONArray("chapters")
            for (i in 0 until objChapters.length()) {
                val jsonObject = objChapters.getJSONObject(i)
                val startTime = jsonObject.optInt("startTime", 0)
                val title = jsonObject.optString("title").takeIf { it.isNotEmpty() }
                val link = jsonObject.optString("url").takeIf { it.isNotEmpty() }
                val img = jsonObject.optString("img").takeIf { it.isNotEmpty() }
                chapters.add(Chapter(startTime * 1000L, title, link, img))
            }
            return chapters
        } catch (e: JSONException) { e.printStackTrace() }
        return listOf()
    }

    class ChapterStartTimeComparator : Comparator<Chapter> {
        override fun compare(lhs: Chapter, rhs: Chapter): Int {
            return lhs.start.compareTo(rhs.start)
        }
    }
}
