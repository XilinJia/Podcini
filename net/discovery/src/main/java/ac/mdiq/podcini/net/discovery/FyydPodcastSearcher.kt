package ac.mdiq.podcini.net.discovery

import ac.mdiq.podcini.core.service.download.PodciniHttpClient.getHttpClient
import de.mfietz.fyydlin.FyydClient
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class FyydPodcastSearcher : PodcastSearcher {
    private val client = FyydClient(getHttpClient())

    override fun search(query: String): Single<List<PodcastSearchResult?>?> {
        return Single.create { subscriber: SingleEmitter<List<PodcastSearchResult?>?> ->
            val response = client.searchPodcasts(
                query, 10)
                .subscribeOn(Schedulers.io())
                .blockingGet()
            val searchResults = ArrayList<PodcastSearchResult?>()

            if (response.data.isNotEmpty()) {
                for (searchHit in response.data) {
                    val podcast = PodcastSearchResult.fromFyyd(searchHit)
                    searchResults.add(podcast)
                }
            }
            subscriber.onSuccess(searchResults)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun lookupUrl(url: String): Single<String> {
        return Single.just(url)
    }

    override fun urlNeedsLookup(url: String): Boolean {
        return false
    }

    override val name: String
        get() = "fyyd"
}
