package de.danoeh.antennapod.net.download.serviceinterface

import android.content.Context
import de.danoeh.antennapod.model.download.DownloadStatus
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia

abstract class DownloadServiceInterface {
    private var currentDownloads: Map<String, DownloadStatus> = HashMap()

    fun setCurrentDownloads(currentDownloads: Map<String, DownloadStatus>) {
        this.currentDownloads = currentDownloads
    }

    /**
     * Download immediately after user action.
     */
    abstract fun downloadNow(context: Context, item: FeedItem, ignoreConstraints: Boolean)

    /**
     * Download when device seems fit.
     */
    abstract fun download(context: Context, item: FeedItem)

    abstract fun cancel(context: Context, media: FeedMedia)

    abstract fun cancelAll(context: Context)

    fun isDownloadingEpisode(url: String): Boolean {
        return (currentDownloads.containsKey(url)
                && currentDownloads[url]!!.state != DownloadStatus.STATE_COMPLETED)
    }

    fun isEpisodeQueued(url: String): Boolean {
        return (currentDownloads.containsKey(url)
                && currentDownloads[url]!!.state == DownloadStatus.STATE_QUEUED)
    }

    fun getProgress(url: String): Int {
        return if (isDownloadingEpisode(url)) currentDownloads[url]!!.progress else -1
    }

    companion object {
        const val WORK_TAG: String = "episodeDownload"
        const val WORK_TAG_EPISODE_URL: String = "episodeUrl:"
        const val WORK_DATA_PROGRESS: String = "progress"
        const val WORK_DATA_MEDIA_ID: String = "media_id"
        const val WORK_DATA_WAS_QUEUED: String = "was_queued"
        private var impl: DownloadServiceInterface? = null
        fun get(): DownloadServiceInterface? {
            return impl
        }

        fun setImpl(impl: DownloadServiceInterface?) {
            Companion.impl = impl
        }
    }
}
