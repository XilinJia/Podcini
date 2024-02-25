package ac.mdiq.podcini.net.discovery

import ac.mdiq.podcini.feed.FeedUrlNotFoundException
import ac.mdiq.podcini.service.download.PodciniHttpClient.getHttpClient
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.Request.Builder
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.regex.Pattern

class ItunesPodcastSearcher : PodcastSearcher {
    override fun search(query: String): Single<List<PodcastSearchResult?>?> {
        return Single.create<List<PodcastSearchResult?>?> { subscriber: SingleEmitter<List<PodcastSearchResult?>?> ->
            val encodedQuery = try {
                URLEncoder.encode(query, "UTF-8")
            } catch (e: UnsupportedEncodingException) {
                // this won't ever be thrown
                query
            }
            val formattedUrl = String.format(ITUNES_API_URL, encodedQuery)

            val client = getHttpClient()
            val httpReq: Builder = Builder()
                .url(formattedUrl)
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
                        if (podcast.feedUrl != null) {
                            podcasts.add(podcast)
                        }
                    }
                } else {
                    subscriber.onError(IOException(response.toString()))
                }
            } catch (e: IOException) {
                subscriber.onError(e)
            } catch (e: JSONException) {
                subscriber.onError(e)
            }
            subscriber.onSuccess(podcasts)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun lookupUrl(url: String): Single<String> {
        val pattern = Pattern.compile(PATTERN_BY_ID)
        val matcher = pattern.matcher(url)
        val lookupUrl = if (matcher.find()) ("https://itunes.apple.com/lookup?id=" + matcher.group(1)) else url
        return Single.create<String?> { emitter: SingleEmitter<String?> ->
            val client = getHttpClient()
            val httpReq: Builder = Builder().url(lookupUrl)
            try {
                val response = client.newCall(httpReq.build()).execute()
                if (response.isSuccessful) {
                    val resultString = response.body!!.string()
                    val result = JSONObject(resultString)
                    val results = result.getJSONArray("results").getJSONObject(0)
                    val feedUrlName = "feedUrl"
                    if (!results.has(feedUrlName)) {
                        val artistName = results.getString("artistName")
                        val trackName = results.getString("trackName")
                        emitter.onError(FeedUrlNotFoundException(artistName, trackName))
                        return@create
                    }
                    val feedUrl = results.getString(feedUrlName)
                    emitter.onSuccess(feedUrl)
                } else {
                    emitter.onError(IOException(response.toString()))
                }
            } catch (e: IOException) {
                emitter.onError(e)
            } catch (e: JSONException) {
                emitter.onError(e)
            }
        }
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
