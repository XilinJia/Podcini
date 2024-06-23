package ac.mdiq.podcini.net.feed.discovery

import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import de.mfietz.fyydlin.FyydClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FyydPodcastSearcher : PodcastSearcher {
    private val client = FyydClient(getHttpClient())

    override suspend fun search1(query: String): List<PodcastSearchResult?>? {
        val response = withContext(Dispatchers.IO) {
            client.searchPodcasts(query, 10).blockingGet()
        }
        val searchResults = ArrayList<PodcastSearchResult?>()

        if (response.data.isNotEmpty()) {
            for (searchHit in response.data) {
                val podcast = PodcastSearchResult.fromFyyd(searchHit)
                searchResults.add(podcast)
            }
        }
        return searchResults
    }

//    override fun lookupUrl(url: String): Single<String> {
//        return Single.just(url)
//    }

    override suspend fun lookupUrl1(resultUrl: String): String {
        return resultUrl
    }

    override fun urlNeedsLookup(url: String): Boolean {
        return false
    }

    override val name: String
        get() = "fyyd"
}
