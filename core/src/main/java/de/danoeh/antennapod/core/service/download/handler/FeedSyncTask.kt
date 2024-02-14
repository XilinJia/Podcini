package de.danoeh.antennapod.core.service.download.handler

import android.content.Context
import androidx.media3.common.util.UnstableApi
import de.danoeh.antennapod.core.storage.DBTasks
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.net.download.serviceinterface.DownloadRequest
import de.danoeh.antennapod.parser.feed.FeedHandlerResult

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
