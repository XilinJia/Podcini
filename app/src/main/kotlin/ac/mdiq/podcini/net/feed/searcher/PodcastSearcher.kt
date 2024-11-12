package ac.mdiq.podcini.net.feed.searcher

interface PodcastSearcher {

    fun urlNeedsLookup(url: String): Boolean

    suspend fun search(query: String): List<PodcastSearchResult>

    suspend fun lookupUrl(url: String): String

    val name: String?
}
