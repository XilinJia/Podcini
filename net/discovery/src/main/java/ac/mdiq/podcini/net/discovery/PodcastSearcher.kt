package ac.mdiq.podcini.net.discovery

import io.reactivex.Single

interface PodcastSearcher {
    fun search(query: String): Single<List<PodcastSearchResult?>?>?

    fun lookupUrl(resultUrl: String): Single<String>?

    fun urlNeedsLookup(resultUrl: String): Boolean

    val name: String?
}
