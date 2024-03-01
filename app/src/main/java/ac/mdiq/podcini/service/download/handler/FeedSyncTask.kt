package ac.mdiq.podcini.service.download.handler

import ac.mdiq.podcini.feed.parser.FeedHandlerResult
import ac.mdiq.podcini.net.download.serviceinterface.DownloadRequest
import ac.mdiq.podcini.storage.DBTasks
import ac.mdiq.podcini.storage.model.download.DownloadResult
import ac.mdiq.podcini.storage.model.feed.Feed
import android.content.Context
import androidx.media3.common.util.UnstableApi

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
