package ac.mdiq.podcini.core.service.download.handler

import android.content.Context
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.core.storage.DBTasks
import ac.mdiq.podcini.model.download.DownloadResult
import ac.mdiq.podcini.model.feed.Feed
import ac.mdiq.podcini.net.download.serviceinterface.DownloadRequest
import ac.mdiq.podcini.parser.feed.FeedHandlerResult

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
