package ac.mdiq.podvinci.core.service.download.handler

import android.content.Context
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podvinci.core.storage.DBTasks
import ac.mdiq.podvinci.model.download.DownloadResult
import ac.mdiq.podvinci.model.feed.Feed
import ac.mdiq.podvinci.net.download.serviceinterface.DownloadRequest
import ac.mdiq.podvinci.parser.feed.FeedHandlerResult

@UnstableApi
class FeedSyncTask(private val context: Context, request: DownloadRequest) {
    var savedFeed: Feed? = null
        private set
    private val task = FeedParserTask(request)
    private var feedHandlerResult: FeedHandlerResult? = null

    fun run(): Boolean {
        feedHandlerResult = task.call()
        if (!task.isSuccessful) {
            return false
        }

        savedFeed = DBTasks.updateFeed(context, feedHandlerResult!!.feed, false)
        return true
    }

    val downloadStatus: DownloadResult
        get() = task.downloadStatus

    val redirectUrl: String
        get() = feedHandlerResult?.redirectUrl?:""
}
