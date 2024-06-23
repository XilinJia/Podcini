package ac.mdiq.podcini.net.feed.discovery

object PodcastSearcherRegistry {
    @get:Synchronized
    var searchProviders: MutableList<SearcherInfo> = mutableListOf()
        get() {
            if (field.isEmpty()) {
                field = ArrayList()
                field.add(SearcherInfo(CombinedSearcher(), 1.0f))
                field.add(SearcherInfo(GpodnetPodcastSearcher(), 0.0f))
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
                return searchProviderInfo.searcher.lookupUrl1(url)
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
