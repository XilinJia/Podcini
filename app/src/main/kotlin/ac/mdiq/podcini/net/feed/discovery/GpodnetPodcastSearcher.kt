package ac.mdiq.podcini.net.feed.discovery

import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.net.sync.SynchronizationCredentials
import ac.mdiq.podcini.net.sync.gpoddernet.GpodnetService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GpodnetPodcastSearcher : PodcastSearcher {

    override suspend fun search1(query: String): List<PodcastSearchResult?>? {
        return try {
            val service = GpodnetService(getHttpClient(),
                SynchronizationCredentials.hosturl, SynchronizationCredentials.deviceID ?: "",
                SynchronizationCredentials.username ?: "", SynchronizationCredentials.password ?: "")
            val gpodnetPodcasts = withContext(Dispatchers.IO) {
                service.searchPodcasts(query, 0)
            }
            val results: MutableList<PodcastSearchResult?> = ArrayList()
            for (podcast in gpodnetPodcasts) {
                results.add(PodcastSearchResult.fromGpodder(podcast))
            }
            results
        } catch (e: GpodnetService.GpodnetServiceException) {
            e.printStackTrace()
            throw e
        }
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
        get() = "Gpodder.net"
}
