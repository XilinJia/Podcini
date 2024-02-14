package de.danoeh.antennapod.parser.feed

import de.danoeh.antennapod.model.feed.Chapter
import org.json.JSONException
import org.json.JSONObject

object PodcastIndexChapterParser {
    fun parse(jsonStr: String?): List<Chapter>? {
        try {
            val chapters: MutableList<Chapter> = ArrayList()
            val obj = JSONObject(jsonStr)
            val objChapters = obj.getJSONArray("chapters")
            for (i in 0 until objChapters.length()) {
                val jsonObject = objChapters.getJSONObject(i)
                val startTime = jsonObject.optInt("startTime", 0)
                val title = jsonObject.optString("title")
                val link = jsonObject.optString("url")
                val img = jsonObject.optString("img")
                chapters.add(Chapter(startTime * 1000L, title, link, img))
            }
            return chapters
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return null
    }
}
