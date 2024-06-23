package ac.mdiq.podcini.net.feed.discovery

import ac.mdiq.podcini.net.feed.FeedUrlNotFoundException
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import okhttp3.Request.Builder
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.regex.Pattern

class ItunesPodcastSearcher : PodcastSearcher {

    override suspend fun search1(query: String): List<PodcastSearchResult?>? {
        val encodedQuery = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            // this won't ever be thrown
            query
        }
        val formattedUrl = String.format(ITUNES_API_URL, encodedQuery)

        val client = getHttpClient()
        val httpReq: Builder = Builder().url(formattedUrl)
        val podcasts: MutableList<PodcastSearchResult?> = ArrayList()
        try {
            val response = client.newCall(httpReq.build()).execute()

            if (response.isSuccessful) {
                val resultString = response.body!!.string()
                val result = JSONObject(resultString)
                val j = result.getJSONArray("results")

                for (i in 0 until j.length()) {
                    val podcastJson = j.getJSONObject(i)
                    val podcast = PodcastSearchResult.fromItunes(podcastJson)
                    if (podcast.feedUrl != null) podcasts.add(podcast)
                }
            } else {
                throw IOException(response.toString())
            }
        } catch (e: IOException) {
            throw e
        } catch (e: JSONException) {
            throw e
        }
        return podcasts
    }

    override suspend fun lookupUrl1(url: String): String {
        val pattern = Pattern.compile(PATTERN_BY_ID)
        val matcher = pattern.matcher(url)
        val lookupUrl = if (matcher.find()) "https://itunes.apple.com/lookup?id=" + matcher.group(1) else url
        val client = getHttpClient()
        val httpReq = Builder().url(lookupUrl).build()
        val response = client.newCall(httpReq).execute()
        if (!response.isSuccessful) {
            throw IOException(response.toString())
        }
        val resultString = response.body!!.string()
        val result = JSONObject(resultString)
        val results = result.getJSONArray("results").getJSONObject(0)
        val feedUrlName = "feedUrl"
        if (!results.has(feedUrlName)) {
            val artistName = results.getString("artistName")
            val trackName = results.getString("trackName")
            throw FeedUrlNotFoundException(artistName, trackName)
        }
        return results.getString(feedUrlName)
    }

    override fun urlNeedsLookup(url: String): Boolean {
        return url.contains("itunes.apple.com") || url.matches(PATTERN_BY_ID.toRegex())
    }

    override val name: String
        get() = "Apple"

    companion object {
        private const val ITUNES_API_URL = "https://itunes.apple.com/search?media=podcast&term=%s"
        private const val PATTERN_BY_ID = ".*/podcasts\\.apple\\.com/.*/podcast/.*/id(\\d+).*"
    }
}
