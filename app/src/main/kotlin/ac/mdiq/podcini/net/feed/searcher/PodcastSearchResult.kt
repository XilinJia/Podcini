package ac.mdiq.podcini.net.feed.searcher

import ac.mdiq.podcini.net.sync.gpoddernet.model.GpodnetPodcast
import ac.mdiq.vista.extractor.channel.ChannelInfoItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import de.mfietz.fyydlin.SearchHit
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class PodcastSearchResult private constructor(
        val title: String,
        val imageUrl: String?,
        val feedUrl: String?,
        val author: String?,
        val count: Int?,
        val update: String?,
        val subscriberCount: Int,
        val source: String) {

    // feedId will be positive if already subscribed
    var feedId by mutableLongStateOf(0L)

    companion object {
        fun dummy(): PodcastSearchResult {
            return PodcastSearchResult("", "", "", "", 0, "", -1, "dummy")
        }

        /**
         * Constructs a Podcast instance from a iTunes search result
         * @param json object holding the podcast information
         * @throws JSONException
         */
        fun fromItunes(json: JSONObject): PodcastSearchResult {
            val title = json.optString("collectionName", "")
            val imageUrl: String? = json.optString("artworkUrl100").takeIf { it.isNotEmpty() }
            val feedUrl: String? = json.optString("feedUrl").takeIf { it.isNotEmpty() }
            val author: String? = json.optString("artistName").takeIf { it.isNotEmpty() }
            return PodcastSearchResult(title, imageUrl, feedUrl, author, null, null, -1, "Itunes")
        }

        /**
         * Constructs a Podcast instance from iTunes toplist entry
         *
         * @param json object holding the podcast information
         * @throws JSONException
         */
        @Throws(JSONException::class)
        fun fromItunesToplist(json: JSONObject): PodcastSearchResult {
            val title = json.getJSONObject("title").getString("label")
            var imageUrl: String? = null
            val images = json.getJSONArray("im:image")
            var i = 0
            while (imageUrl == null && i < images.length()) {
                val image = images.getJSONObject(i)
                val height = image.getJSONObject("attributes").getString("height")
                if (height.toInt() >= 100) imageUrl = image.getString("label")
                i++
            }
            val feedUrl = "https://itunes.apple.com/lookup?id=" + json.getJSONObject("id").getJSONObject("attributes").getString("im:id")

            var author: String? = null
            try { author = json.getJSONObject("im:artist").getString("label")
            } catch (e: Exception) {/* Some feeds have empty artist */ }
            return PodcastSearchResult(title, imageUrl, feedUrl, author, null, null, -1, "Toplist")
        }

        fun fromFyyd(searchHit: SearchHit): PodcastSearchResult {
            return PodcastSearchResult(searchHit.title, searchHit.thumbImageURL, searchHit.xmlUrl, searchHit.author, null, null, -1, "Fyyd")
        }

        fun fromGpodder(searchHit: GpodnetPodcast): PodcastSearchResult {
            return PodcastSearchResult(searchHit.title, searchHit.logoUrl, searchHit.url, searchHit.author, null, null, -1, "GPodder")
        }

        fun fromPodcastIndex(json: JSONObject): PodcastSearchResult {
            val title = json.optString("title", "")
            val imageUrl: String? = json.optString("image").takeIf { it.isNotEmpty() }
            val feedUrl: String? = json.optString("url").takeIf { it.isNotEmpty() }
            val author: String? = json.optString("author").takeIf { it.isNotEmpty() }
            var count: Int? = json.optInt("episodeCount", -1)
            if (count != null && count < 0) count = null
            val updateInt: Int = json.optInt("lastUpdateTime", -1)
            var update: String? = null
            if (updateInt > 0) {
                val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                update = format.format(updateInt.toLong() * 1000)
            }
            return PodcastSearchResult(title, imageUrl, feedUrl, author, count, update, -1, "PodcastIndex")
        }

        fun fromChannelInfoItem(info: ChannelInfoItem): PodcastSearchResult {
            val title = info.name
            val imageUrl: String? = if (info.thumbnails.isNotEmpty()) info.thumbnails[0].url else null
            val feedUrl = info.url
            val author = ""
            val count: Int = info.streamCount.toInt()
            val updateInt = -1
            val update: String? = null
            val subscriberCount = info.subscriberCount.toInt()
            return PodcastSearchResult(title, imageUrl, feedUrl, author, count, update, subscriberCount,"VistaGuide")
        }
    }
}
