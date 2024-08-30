package ac.mdiq.podcini.net.feed.discovery

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

class CombinedSearcher : PodcastSearcher {

    override suspend fun search(query: String): List<PodcastSearchResult> {
        val searchProviders = PodcastSearcherRegistry.searchProviders
        val searchResults = MutableList<List<PodcastSearchResult>>(searchProviders.size) { listOf() }

        // Using a supervisor scope to ensure that one failing child does not cancel others
        supervisorScope {
            val searchJobs = searchProviders.mapIndexed { index, searchProviderInfo ->
                val searcher = searchProviderInfo.searcher
                if (searchProviderInfo.weight > 0.00001f && searcher.javaClass != CombinedSearcher::class.java) {
                    async(Dispatchers.IO) {
                        try {
                            val results = searcher.search(query)
                            searchResults[index] = results
                        } catch (e: Throwable) {
                            Log.d(TAG, Log.getStackTraceString(e))
                        }
                    }
                } else {
                    null
                }
            }.filterNotNull() // Remove null jobs
            // Wait for all search jobs to complete
            searchJobs.awaitAll()
        }

        return weightSearchResults(searchResults)
    }

    private fun weightSearchResults(singleResults: List<List<PodcastSearchResult>>): List<PodcastSearchResult> {
        val resultRanking = HashMap<String?, Float>()
        val urlToResult = HashMap<String?, PodcastSearchResult>()
        for (i in singleResults.indices) {
            val providerPriority = PodcastSearcherRegistry.searchProviders[i].weight
            val providerResults = singleResults[i]
            for (position in providerResults.indices) {
                val result = providerResults[position]
                urlToResult[result.feedUrl] = result

                var ranking = 0f
                if (resultRanking.containsKey(result.feedUrl)) ranking = resultRanking[result.feedUrl]!!

                ranking += 1f / (position + 1f)
                resultRanking[result.feedUrl] = ranking * providerPriority
            }
        }
//        val sortedResults = mutableListOf<MutableMap.MutableEntry<String?, Float>>(resultRanking.entries)
        val sortedResults = resultRanking.entries.toMutableList()
        sortedResults.sortWith { o1: Map.Entry<String?, Float>, o2: Map.Entry<String?, Float> ->
            o2.value.toDouble().compareTo(o1.value.toDouble())
        }

        val results: MutableList<PodcastSearchResult> = ArrayList()
        for ((key) in sortedResults) {
            val v = urlToResult[key] ?: continue
            results.add(v)
        }
        return results
    }

//    override fun lookupUrl(url: String): Single<String> {
//        return PodcastSearcherRegistry.lookupUrl(url)
//    }

    override suspend fun lookupUrl(url: String): String {
        return PodcastSearcherRegistry.lookupUrl1(url)
    }

    override fun urlNeedsLookup(url: String): Boolean {
        return PodcastSearcherRegistry.urlNeedsLookup(url)
    }

    override val name: String
        get() {
            val names = ArrayList<String?>()
            for (i in PodcastSearcherRegistry.searchProviders.indices) {
                val searchProviderInfo = PodcastSearcherRegistry.searchProviders[i]
                val searcher = searchProviderInfo.searcher
                if (searchProviderInfo.weight > 0.00001f && searcher.javaClass != CombinedSearcher::class.java) names.add(searcher.name)
            }
            return names.joinToString()
        }

    companion object {
        private val TAG: String = CombinedSearcher::class.simpleName ?: "Anonymous"
    }
}
