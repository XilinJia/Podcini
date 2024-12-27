package ac.mdiq.podcini.storage.utils

import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.storage.model.Chapter
import ac.mdiq.podcini.util.Logd
import android.util.Log
import okhttp3.CacheControl
import okhttp3.Request
import okhttp3.Request.Builder
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import kotlin.math.abs

/**
 * Utility class for getting chapter data from media files.
 */
object ChapterUtils {
    private val TAG: String = ChapterUtils::class.simpleName ?: "Anonymous"

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
