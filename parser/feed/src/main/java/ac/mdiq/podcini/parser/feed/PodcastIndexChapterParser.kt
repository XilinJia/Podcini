package ac.mdiq.podcini.parser.feed

import ac.mdiq.podcini.model.feed.Chapter
import org.json.JSONException
import org.json.JSONObject

object PodcastIndexChapterParser {
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
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return listOf()
    }
}
