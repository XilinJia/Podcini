package ac.mdiq.podcini.net.feed.searcher

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.net.download.service.PodciniHttpClient
import ac.mdiq.podcini.net.feed.FeedUrlNotFoundException
import ac.mdiq.podcini.net.feed.searcher.PodcastSearchResult.Companion.fromChannelInfoItem
import ac.mdiq.podcini.util.config.ClientConfig
import ac.mdiq.vista.extractor.Vista
import ac.mdiq.vista.extractor.channel.ChannelInfoItem
import ac.mdiq.vista.extractor.exceptions.ExtractionException
import ac.mdiq.vista.extractor.search.SearchInfo
import android.util.Log
import de.mfietz.fyydlin.FyydClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.*
import java.util.regex.Pattern

object PodcastSearcherRegistry {
    @get:Synchronized
    var searchProviders: MutableList<SearcherInfo> = mutableListOf()
        get() {
            if (field.isEmpty()) {
                field = ArrayList()
                field.add(SearcherInfo(CombinedSearcher(), 1.0f))
                field.add(SearcherInfo(VistaGuidePodcastSearcher(), 1.0f))
//                field.add(SearcherInfo(GpodnetPodcastSearcher(), 0.0f))
                field.add(SearcherInfo(FyydPodcastSearcher(), 1.0f))
                field.add(SearcherInfo(ItunesPodcastSearcher(), 1.0f))
                field.add(SearcherInfo(PodcastIndexPodcastSearcher(), 1.0f))
            }
            return field
        }
        private set

    suspend fun lookupUrl1(url: String): String {
        for (searchProviderInfo in searchProviders) {
            if (searchProviderInfo.searcher.javaClass != CombinedSearcher::class.java && searchProviderInfo.searcher.urlNeedsLookup(url))
                return searchProviderInfo.searcher.lookupUrl(url)
        }
        return url
    }

    fun urlNeedsLookup(url: String): Boolean {
        for (searchProviderInfo in searchProviders) {
            if (searchProviderInfo.searcher.javaClass != CombinedSearcher::class.java && searchProviderInfo.searcher.urlNeedsLookup(url)) return true
        }
        return false
    }

    class SearcherInfo(val searcher: PodcastSearcher, val weight: Float)
}

class PodcastIndexPodcastSearcher : PodcastSearcher {
    override suspend fun search(query: String): List<PodcastSearchResult> {
        val encodedQuery = try { withContext(Dispatchers.IO) { URLEncoder.encode(query, "UTF-8") } } catch (e: UnsupportedEncodingException) { query }
        val formattedUrl = String.format(SEARCH_API_URL, encodedQuery)
        val podcasts: MutableList<PodcastSearchResult> = ArrayList()
        try {
            val client = PodciniHttpClient.getHttpClient()
            val response = client.newCall(buildAuthenticatedRequest(formattedUrl)).execute()
            if (response.isSuccessful) {
                val resultString = response.body!!.string()
                val result = JSONObject(resultString)
                val j = result.getJSONArray("feeds")

                for (i in 0 until j.length()) {
                    val podcastJson = j.getJSONObject(i)
                    val podcast = PodcastSearchResult.fromPodcastIndex(podcastJson)
                    if (podcast.feedUrl != null) podcasts.add(podcast)
                }
            } else throw IOException(response.toString())
        } catch (e: IOException) { throw e
        } catch (e: JSONException) { throw e }
        return podcasts
    }

    override val name: String
        get() = "Podcast Index"

    private fun buildAuthenticatedRequest(url: String): Request {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        val now = Date()
        calendar.time = now
        val secondsSinceEpoch = calendar.timeInMillis / 1000L
        val apiHeaderTime = secondsSinceEpoch.toString()
        val data4Hash = BuildConfig.PODCASTINDEX_API_KEY + BuildConfig.PODCASTINDEX_API_SECRET + apiHeaderTime
        val hashString = sha1(data4Hash) ?:""

        val httpReq: Request.Builder = Request.Builder()
            .addHeader("X-Auth-Date", apiHeaderTime)
            .addHeader("X-Auth-Key", BuildConfig.PODCASTINDEX_API_KEY)
            .addHeader("Authorization", hashString)
            .addHeader("User-Agent", ClientConfig.USER_AGENT ?:"")
            .url(url)
        return httpReq.build()
    }

    companion object {
        private const val SEARCH_API_URL = "https://api.podcastindex.org/api/1.0/search/byterm?q=%s"

        private fun sha1(clearString: String): String? {
            try {
                val messageDigest = MessageDigest.getInstance("SHA-1")
                messageDigest.update(clearString.toByteArray(charset("UTF-8")))
                return toHex(messageDigest.digest())
            } catch (ignored: Exception) {
                ignored.printStackTrace()
                return null
            }
        }

        private fun toHex(bytes: ByteArray): String {
            val buffer = StringBuilder()
            for (b in bytes) buffer.append(String.format(Locale.getDefault(), "%02x", b))
            return buffer.toString()
        }
    }
}

class VistaGuidePodcastSearcher : PodcastSearcher {
    override suspend fun search(query: String): List<PodcastSearchResult> {
        val service = try { Vista.getService("YouTube") } catch (e: ExtractionException) { throw ExtractionException("YouTube service not found") }
        try {
            val searchInfo = SearchInfo.getInfo(service, service.getSearchQHFactory().fromQuery(query, listOf("channels"), ""))
            val podResults: MutableList<PodcastSearchResult> = mutableListOf()
            for (ch in searchInfo.relatedItems) podResults.add(fromChannelInfoItem(ch as ChannelInfoItem))
            return podResults
        } catch (e: Throwable) { Log.e("VistaGuidePodcastSearcher", "error: ${e.message}") }
        return listOf()
    }
    override val name: String
        get() = "VistaGuide"
}

class FyydPodcastSearcher : PodcastSearcher {
    private val client = FyydClient(PodciniHttpClient.getHttpClient())

    override suspend fun search(query: String): List<PodcastSearchResult> {
        val response = withContext(Dispatchers.IO) { client.searchPodcasts(query, 10).blockingGet() }
        val searchResults = ArrayList<PodcastSearchResult>()
        if (response.data.isNotEmpty()) {
            for (searchHit in response.data) {
                val podcast = PodcastSearchResult.fromFyyd(searchHit)
                searchResults.add(podcast)
            }
        }
        return searchResults
    }
    override val name: String
        get() = "fyyd"
}

//class GpodnetPodcastSearcher : PodcastSearcher {
//    override suspend fun search(query: String): List<PodcastSearchResult> {
//        return try {
//            val service = GpodnetService(PodciniHttpClient.getHttpClient(),
//                SynchronizationCredentials.hosturl, SynchronizationCredentials.deviceID ?: "",
//                SynchronizationCredentials.username ?: "", SynchronizationCredentials.password ?: "")
//            val gpodnetPodcasts = withContext(Dispatchers.IO) { service.searchPodcasts(query, 0) }
//            val results: MutableList<PodcastSearchResult> = ArrayList()
//            for (podcast in gpodnetPodcasts) results.add(PodcastSearchResult.fromGpodder(podcast))
//            results
//        } catch (e: GpodnetService.GpodnetServiceException) {
//            e.printStackTrace()
//            throw e
//        }
//    }
//    override val name: String
//        get() = "Gpodder.net"
//}

class ItunesPodcastSearcher : PodcastSearcher {
    override suspend fun search(query: String): List<PodcastSearchResult> {
        val encodedQuery = try { withContext(Dispatchers.IO) { URLEncoder.encode(query, "UTF-8") } } catch (e: UnsupportedEncodingException) { query }
        val formattedUrl = String.format(ITUNES_API_URL, encodedQuery)

        val client = PodciniHttpClient.getHttpClient()
        val httpReq: Request.Builder = Request.Builder().url(formattedUrl)
        val podcasts: MutableList<PodcastSearchResult> = ArrayList()
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
            } else throw IOException(response.toString())
        } catch (e: IOException) { throw e
        } catch (e: JSONException) { throw e }
        return podcasts
    }

    override suspend fun lookupUrl(url: String): String {
        val pattern = Pattern.compile(PATTERN_BY_ID)
        val matcher = pattern.matcher(url)
        val lookupUrl = if (matcher.find()) "https://itunes.apple.com/lookup?id=" + matcher.group(1) else url
        val client = PodciniHttpClient.getHttpClient()
        val httpReq = Request.Builder().url(lookupUrl).build()
        val response = client.newCall(httpReq).execute()
        if (!response.isSuccessful) throw IOException(response.toString())
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