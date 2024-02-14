package de.danoeh.antennapod.net.discovery

import de.danoeh.antennapod.core.service.download.AntennapodHttpClient.getHttpClient
import de.danoeh.antennapod.core.sync.SynchronizationCredentials
import de.danoeh.antennapod.net.sync.gpoddernet.GpodnetService
import de.danoeh.antennapod.net.sync.gpoddernet.GpodnetServiceException
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class GpodnetPodcastSearcher : PodcastSearcher {
    override fun search(query: String): Single<List<PodcastSearchResult?>?>? {
        return Single.create { subscriber: SingleEmitter<List<PodcastSearchResult?>?> ->
            try {
                val service = GpodnetService(getHttpClient(),
                    SynchronizationCredentials.hosturl, SynchronizationCredentials.deviceID ?: "",
                    SynchronizationCredentials.username ?: "", SynchronizationCredentials.password ?: "")
                val gpodnetPodcasts = service.searchPodcasts(query, 0)
                val results: MutableList<PodcastSearchResult?> = ArrayList()
                for (podcast in gpodnetPodcasts) {
                    results.add(PodcastSearchResult.fromGpodder(podcast))
                }
                subscriber.onSuccess(results)
            } catch (e: GpodnetServiceException) {
                e.printStackTrace()
                subscriber.onError(e)
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun lookupUrl(url: String): Single<String>? {
        return Single.just(url)
    }

    override fun urlNeedsLookup(url: String): Boolean {
        return false
    }

    override val name: String
        get() = "Gpodder.net"
}
