package ac.mdiq.podcini.net.feed.discovery

interface PodcastSearcher {

    fun urlNeedsLookup(resultUrl: String): Boolean

    suspend fun search1(query: String): List<PodcastSearchResult?>?

    suspend fun lookupUrl1(resultUrl: String): String

    val name: String?
}
