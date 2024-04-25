package ac.mdiq.podcini.net.discovery

import android.text.TextUtils
import android.util.Log
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.*
import java.util.concurrent.CountDownLatch

class CombinedSearcher : PodcastSearcher {
    override fun search(query: String): Single<List<PodcastSearchResult?>?> {
        val disposables = ArrayList<Disposable>()
        val singleResults: MutableList<List<PodcastSearchResult?>?> = ArrayList(
            Collections.nCopies<List<PodcastSearchResult?>?>(PodcastSearcherRegistry.searchProviders.size, null))
        val latch = CountDownLatch(PodcastSearcherRegistry.searchProviders.size)
        for (i in PodcastSearcherRegistry.searchProviders.indices) {
            val searchProviderInfo = PodcastSearcherRegistry.searchProviders[i]
            val searcher = searchProviderInfo.searcher
            if (searchProviderInfo.weight <= 0.00001f || searcher.javaClass == CombinedSearcher::class.java) {
                latch.countDown()
                continue
            }
            disposables.add(searcher.search(query).subscribe({ e: List<PodcastSearchResult?>? ->
                singleResults[i] = e
                latch.countDown()
            }, { throwable: Throwable? ->
                Log.d(TAG, Log.getStackTraceString(throwable))
                latch.countDown()
            }
            ))
        }

        return Single.create<List<PodcastSearchResult?>?> { subscriber: SingleEmitter<List<PodcastSearchResult?>?> ->
            latch.await()
            val results = weightSearchResults(singleResults)
            subscriber.onSuccess(results)
        }
            .doOnDispose {
                for (disposable in disposables) {
                    disposable.dispose()
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    private fun weightSearchResults(singleResults: List<List<PodcastSearchResult?>?>): List<PodcastSearchResult?> {
        val resultRanking = HashMap<String?, Float>()
        val urlToResult = HashMap<String?, PodcastSearchResult?>()
        for (i in singleResults.indices) {
            val providerPriority = PodcastSearcherRegistry.searchProviders[i].weight
            val providerResults = singleResults[i] ?: continue
            for (position in providerResults.indices) {
                val result = providerResults[position]
                urlToResult[result!!.feedUrl] = result

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

        val results: MutableList<PodcastSearchResult?> = ArrayList()
        for ((key) in sortedResults) {
            results.add(urlToResult[key])
        }
        return results
    }

    override fun lookupUrl(url: String): Single<String> {
        return PodcastSearcherRegistry.lookupUrl(url)
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
            return TextUtils.join(", ", names)
        }

    companion object {
        private const val TAG = "CombinedSearcher"
    }
}
