package de.danoeh.antennapod.net.discovery

import io.reactivex.Single

object PodcastSearcherRegistry {
    @Suppress("UNNECESSARY_SAFE_CALL")
    @get:Synchronized
    var searchProviders: MutableList<SearcherInfo> = mutableListOf()
        get() {
            if (field.isEmpty()) {
                field = ArrayList()
                field?.add(SearcherInfo(CombinedSearcher(), 1.0f))
                field?.add(SearcherInfo(GpodnetPodcastSearcher(), 0.0f))
                field?.add(SearcherInfo(FyydPodcastSearcher(), 1.0f))
                field?.add(SearcherInfo(ItunesPodcastSearcher(), 1.0f))
                field?.add(SearcherInfo(PodcastIndexPodcastSearcher(), 1.0f))
            }
            return field
        }
        private set

    fun lookupUrl(url: String): Single<String>? {
        for (searchProviderInfo in searchProviders) {
            if (searchProviderInfo.searcher.javaClass != CombinedSearcher::class.java
                    && searchProviderInfo.searcher.urlNeedsLookup(url)) {

                return searchProviderInfo.searcher.lookupUrl(url)
            }
        }
        return Single.just(url)
    }

    fun urlNeedsLookup(url: String): Boolean {
        for (searchProviderInfo in searchProviders) {
            if (searchProviderInfo.searcher.javaClass != CombinedSearcher::class.java
                    && searchProviderInfo.searcher.urlNeedsLookup(url)) {

                return true
            }
        }
        return false
    }

    class SearcherInfo(val searcher: PodcastSearcher, val weight: Float)
}
