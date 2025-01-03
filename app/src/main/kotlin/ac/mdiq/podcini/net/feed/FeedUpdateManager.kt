package ac.mdiq.podcini.net.feed

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadError
import ac.mdiq.podcini.net.download.service.DefaultDownloaderFactory
import ac.mdiq.podcini.net.download.service.DownloadRequest
import ac.mdiq.podcini.net.download.service.DownloadRequestCreator.create
import ac.mdiq.podcini.net.feed.parser.FeedHandler
import ac.mdiq.podcini.net.feed.parser.FeedHandler.FeedHandlerResult
import ac.mdiq.podcini.net.utils.NetworkUtils.isAllowMobileFeedRefresh
import ac.mdiq.podcini.net.utils.NetworkUtils.isFeedRefreshAllowed
import ac.mdiq.podcini.net.utils.NetworkUtils.isNetworkRestricted
import ac.mdiq.podcini.net.utils.NetworkUtils.isVpnOverWifi
import ac.mdiq.podcini.net.utils.NetworkUtils.networkAvailable
import ac.mdiq.podcini.net.utils.NetworkUtils.prepareUrl
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.storage.algorithms.AutoDownloads.autodownloadEpisodeMedia
import ac.mdiq.podcini.storage.database.Episodes.episodeFromStreamInfoItem
import ac.mdiq.podcini.storage.database.Feeds
import ac.mdiq.podcini.storage.database.LogsAndStats
import ac.mdiq.podcini.storage.database.RealmDB.unmanaged
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.VolumeAdaptionSetting
import ac.mdiq.podcini.storage.utils.StorageUtils.feedfilePath
import ac.mdiq.podcini.ui.utils.NotificationUtils
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.config.ClientConfigurator
import ac.mdiq.vista.extractor.InfoItem
import ac.mdiq.vista.extractor.Vista
import ac.mdiq.vista.extractor.channel.ChannelInfo
import ac.mdiq.vista.extractor.channel.tabs.ChannelTabInfo
import ac.mdiq.vista.extractor.exceptions.ExtractionException
import ac.mdiq.vista.extractor.playlist.PlaylistInfo
import ac.mdiq.vista.extractor.stream.StreamInfoItem
import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import androidx.work.Constraints.Builder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import javax.xml.parsers.ParserConfigurationException

object FeedUpdateManager {
    private val TAG: String = FeedUpdateManager::class.simpleName ?: "Anonymous"

    const val WORK_TAG_FEED_UPDATE: String = "feedUpdate"
    private const val WORK_ID_FEED_UPDATE = "ac.mdiq.podcini.service.download.FeedUpdateWorker"
    private const val WORK_ID_FEED_UPDATE_MANUAL = "feedUpdateManual"
    const val EXTRA_FEED_ID: String = "feed_id"
    const val EXTRA_NEXT_PAGE: String = "next_page"
    const val EXTRA_FULL_UPDATE: String = "full_update"
    const val EXTRA_EVEN_ON_MOBILE: String = "even_on_mobile"

    private val updateInterval: Long
        get() = getPref(AppPreferences.AppPrefs.prefAutoUpdateIntervall, "12").toInt().toLong()

    private val isAutoUpdateDisabled: Boolean
        get() = updateInterval == 0L

    /**
     * Start / restart periodic auto feed refresh
     * @param context Context
     */
    @JvmStatic
    fun restartUpdateAlarm(context: Context, replace: Boolean) {
        if (isAutoUpdateDisabled) WorkManager.getInstance(context).cancelUniqueWork(WORK_ID_FEED_UPDATE)
        else {
            val workRequest: PeriodicWorkRequest = PeriodicWorkRequest.Builder(FeedUpdateWorker::class.java, updateInterval, TimeUnit.HOURS)
                .setConstraints(Builder().setRequiredNetworkType(if (isAllowMobileFeedRefresh) NetworkType.CONNECTED else NetworkType.UNMETERED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_ID_FEED_UPDATE,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, workRequest)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun runOnce(context: Context, feed: Feed? = null, nextPage: Boolean = false, fullUpdate: Boolean = false) {
        val workRequest: OneTimeWorkRequest.Builder = OneTimeWorkRequest.Builder(FeedUpdateWorker::class.java)
            .setInitialDelay(0L, TimeUnit.MILLISECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(WORK_TAG_FEED_UPDATE)
        if (feed == null || !feed.isLocalFeed) workRequest.setConstraints(Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())

        val builder = Data.Builder()
        builder.putBoolean(EXTRA_EVEN_ON_MOBILE, true)
        if (fullUpdate) builder.putBoolean(EXTRA_FULL_UPDATE, true)
        if (feed != null) {
            builder.putLong(EXTRA_FEED_ID, feed.id)
            builder.putBoolean(EXTRA_NEXT_PAGE, nextPage)
        }
        workRequest.setInputData(builder.build())
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_ID_FEED_UPDATE_MANUAL, ExistingWorkPolicy.REPLACE, workRequest.build())
    }

    @JvmStatic
    @JvmOverloads
    fun runOnceOrAsk(context: Context, feed: Feed? = null, fullUpdate: Boolean = false) {
        Logd(TAG, "Run auto update immediately in background.")
        when {
            feed != null && feed.isLocalFeed -> runOnce(context, feed, fullUpdate = fullUpdate)
            !networkAvailable() -> EventFlow.postEvent(FlowEvent.MessageEvent(context.getString(R.string.download_error_no_connection)))
            isFeedRefreshAllowed -> runOnce(context, feed, fullUpdate = fullUpdate)
            else -> confirmMobileRefresh(context, feed)
        }
    }

    private fun confirmMobileRefresh(context: Context, feed: Feed?) {
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.feed_refresh_title)
            .setPositiveButton(R.string.confirm_mobile_streaming_button_once) { _: DialogInterface?, _: Int -> runOnce(context, feed) }
            .setNeutralButton(R.string.confirm_mobile_streaming_button_always) { _: DialogInterface?, _: Int ->
                isAllowMobileFeedRefresh = true
                runOnce(context, feed)
            }
            .setNegativeButton(R.string.no, null)
        if (isNetworkRestricted && isVpnOverWifi) builder.setMessage(R.string.confirm_mobile_feed_refresh_dialog_message_vpn)
        else builder.setMessage(R.string.confirm_mobile_feed_refresh_dialog_message)

        builder.show()
    }

    class FeedUpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        private val notificationManager = NotificationManagerCompat.from(context)

        override fun doWork(): Result {
            ClientConfigurator.initialize(applicationContext)
            val feedsToUpdate: MutableList<Feed>
            val feedId = inputData.getLong(EXTRA_FEED_ID, -1L)
            var allAreLocal = true
            var force = false
            if (feedId == -1L) { // Update all
                feedsToUpdate = Feeds.getFeedList().toMutableList()
                val itr = feedsToUpdate.iterator()
                while (itr.hasNext()) {
                    val feed = itr.next()
                    if (feed.keepUpdated == false) itr.remove()
                    if (!feed.isLocalFeed) allAreLocal = false
                }
                feedsToUpdate.shuffle() // If the worker gets cancelled early, every feed has a chance to be updated
            } else {
                val feed = Feeds.getFeed(feedId) ?: return Result.success()
                Logd(TAG, "doWork updating single feed: ${feed.title} ${feed.downloadUrl}")
                if (!feed.isLocalFeed) allAreLocal = false
                feedsToUpdate = mutableListOf(feed)
//                feedsToUpdate.add(feed) // Needs to be updatable, so no singletonList
                force = true
            }
            if (!inputData.getBoolean(EXTRA_EVEN_ON_MOBILE, false) && !allAreLocal) {
                if (!networkAvailable() || !isFeedRefreshAllowed) {
                    Logd(TAG, "Blocking automatic update")
                    return Result.retry()
                }
            }
            val fullUpdate = inputData.getBoolean(EXTRA_FULL_UPDATE, false)
            refreshFeeds(feedsToUpdate, force, fullUpdate)
            notificationManager.cancel(R.id.notification_updating_feeds)
            autodownloadEpisodeMedia(applicationContext, feedsToUpdate.toList())
            feedsToUpdate.clear()
            return Result.success()
        }
        private fun createNotification(titles: List<String>?): Notification {
            val context = applicationContext
            var contentText = ""
            var bigText: String? = ""
            if (titles != null) {
                contentText = context.resources.getQuantityString(R.plurals.downloads_left, titles.size, titles.size)
                bigText = titles.joinToString("\n") { "• $it" }
            }
            return NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID.downloading.name)
                .setContentTitle(context.getString(R.string.download_notification_title_feeds))
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setSmallIcon(R.drawable.ic_notification_sync)
                .setOngoing(true)
                .addAction(R.drawable.ic_cancel, context.getString(R.string.cancel_label), WorkManager.getInstance(context).createCancelPendingIntent(id))
                .build()
        }

        override fun getForegroundInfoAsync(): ListenableFuture<ForegroundInfo> {
            return Futures.immediateFuture(ForegroundInfo(R.id.notification_updating_feeds, createNotification(null)))
        }
        
        private fun refreshFeeds(feedsToUpdate: MutableList<Feed>, force: Boolean, fullUpdate: Boolean) {
            if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this.applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
//            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                Log.e(TAG, "refreshFeeds: require POST_NOTIFICATIONS permission")
//            Toast.makeText(applicationContext, R.string.notification_permission_text, Toast.LENGTH_LONG).show()
                return
            }
            val titles = feedsToUpdate.map { it.title ?: "No title" }.toMutableList()
            var i = 0
            while (i < feedsToUpdate.size) {
                if (isStopped) return
                notificationManager.notify(R.id.notification_updating_feeds, createNotification(titles))
                val feed = unmanaged(feedsToUpdate[i++])
                try {
                    Logd(TAG, "updating local feed? ${feed.isLocalFeed} ${feed.title}")
                    when {
                        feed.isLocalFeed -> LocalFeedUpdater.updateFeed(feed, applicationContext, null)
                        feed.type == Feed.FeedType.YOUTUBE.name -> refreshYoutubeFeed(feed, fullUpdate)
                        else -> refreshFeed(feed, force, fullUpdate)
                    }
                } catch (e: Exception) {
                    Logd(TAG, "update failed ${e.message}")
                    Feeds.persistFeedLastUpdateFailed(feed, true)
                    val status = DownloadResult(feed.id, feed.title?:"", DownloadError.ERROR_IO_ERROR, false, e.message?:"")
                    LogsAndStats.addDownloadStatus(status)
                }
                titles.removeAt(0)
            }
        }
        private fun refreshYoutubeFeed(feed: Feed, fullUpdate: Boolean) {
            if (feed.downloadUrl.isNullOrEmpty()) return
            val url = feed.downloadUrl!!
            val newestItem = feed.mostRecentItem
            Logd(TAG, "newestItem: ${newestItem?.getPubDate()} ${newestItem?.title}")
//            val oldestItem = feed.oldestItem
            try {
                val service = try { Vista.getService("YouTube") } catch (e: ExtractionException) { throw ExtractionException("YouTube service not found") }
                val uURL = URL(url)
                if (uURL.path.startsWith("/channel")) {
                    val channelInfo = ChannelInfo.getInfo(service, url)
                    Logd(TAG, "refreshYoutubeFeed channelInfo: $channelInfo ${channelInfo.tabs.size}")
                    if (channelInfo.tabs.isEmpty()) return
                    try {
                        val channelTabInfo = ChannelTabInfo.getInfo(service, channelInfo.tabs.first())
                        Logd(TAG, "refreshYoutubeFeed result1: $channelTabInfo ${channelTabInfo.relatedItems.size}")
                        var infoItems = channelTabInfo.relatedItems
                        var nextPage = channelTabInfo.nextPage
                        val eList: RealmList<Episode> = realmListOf()
                        while (infoItems.isNotEmpty()) {
                            for (r_ in infoItems) {
                                val r = r_ as StreamInfoItem
                                if (r.infoType != InfoItem.InfoType.STREAM) continue
                                Logd(TAG, "item: ${r.uploadDate?.date()?.time} ${r.name}")
                                if ((r.uploadDate?.date()?.time ?: Date(0)) > (newestItem?.getPubDate() ?: Date(0)))
                                    eList.add(episodeFromStreamInfoItem(r))
                                else {
                                    nextPage = null
                                    break
                                }
                            }
                            if (nextPage == null) break
                            try {
                                val page = ChannelTabInfo.getMoreItems(service, channelInfo.tabs.first(), nextPage)
                                nextPage = page.nextPage
                                infoItems = page.items
                                Logd(TAG, "refreshYoutubeFeed more infoItems: ${infoItems.size}")
                            } catch (e: Throwable) {
                                Logd(TAG, "refreshYoutubeFeed ChannelTabInfo.getMoreItems error: ${e.message}")
                                break
                            }
                        }
                        val feed_ = Feed(url, null)
                        feed_.type = Feed.FeedType.YOUTUBE.name
                        feed_.hasVideoMedia = true
                        feed_.title = channelInfo.name
                        feed_.fileUrl = File(feedfilePath, feed_.getFeedfileName()).toString()
                        feed_.description = channelInfo.description
                        feed_.author = channelInfo.parentChannelName
                        feed_.imageUrl = if (channelInfo.avatars.isNotEmpty()) channelInfo.avatars.first().url else null
                        feed_.episodes = eList
                        if (fullUpdate) Feeds.updateFeed(applicationContext, feed_) else Feeds.updateFeedSimple(feed_)
                    } catch (e: Throwable) { Logd(TAG, "refreshYoutubeFeed channel error1 ${e.message}") }
                } else if (uURL.path.startsWith("/playlist")) {
                    val playlistInfo = PlaylistInfo.getInfo(service, url) ?: return
                    val eList: RealmList<Episode> = realmListOf()
                    try {
                        var infoItems = playlistInfo.relatedItems
                        var nextPage = playlistInfo.nextPage
                        while (infoItems.isNotEmpty()) {
                            for (r in infoItems) {
                                if (r.infoType != InfoItem.InfoType.STREAM) continue
                                if ((r.uploadDate?.date()?.time ?: Date(0)) > (newestItem?.getPubDate() ?: Date(0)))
                                    eList.add(episodeFromStreamInfoItem(r))
                                else {
                                    nextPage = null
                                    break
                                }
                            }
                            if (nextPage == null) break
                            try {
                                val page = PlaylistInfo.getMoreItems(service, url, nextPage) ?: break
                                nextPage = page.nextPage
                                infoItems = page.items
                                Logd(TAG, "more infoItems: ${infoItems.size}")
                            } catch (e: Throwable) {
                                Logd(TAG, "PlaylistInfo.getMoreItems error: ${e.message}")
                                break
                            }
                        }
                        val feed_ = Feed(url, null)
                        feed_.type = Feed.FeedType.YOUTUBE.name
                        feed_.hasVideoMedia = true
                        feed_.title = playlistInfo.name
                        feed_.fileUrl = File(feedfilePath, feed_.getFeedfileName()).toString()
                        feed_.description = playlistInfo.description?.content ?: ""
                        feed_.author = playlistInfo.uploaderName
                        feed_.imageUrl = if (playlistInfo.thumbnails.isNotEmpty()) playlistInfo.thumbnails.first().url else null
                        feed_.episodes = eList
                        if (fullUpdate) Feeds.updateFeed(applicationContext, feed_) else Feeds.updateFeedSimple(feed_)
                    } catch (e: Throwable) { Logd(TAG, "refreshYoutubeFeed playlist error1 ${e.message}") }
                } else {
                    // channel tabs other than videos
                    val pathSegments = uURL.path.split("/")
                    val channelUrl = "https://www.youtube.com/channel/${pathSegments[1]}"
                    Logd(TAG, "channelUrl: $channelUrl")
                    val channelInfo = ChannelInfo.getInfo(service, channelUrl)
                    Logd(TAG, "refreshYoutubeFeed channelInfo: $channelInfo ${channelInfo.tabs.size}")
                    if (channelInfo.tabs.isEmpty()) return
                    var index = -1
                    for (i in channelInfo.tabs.indices) {
                        val url_ = prepareUrl(channelInfo.tabs[i].url)
                        if (feed.downloadUrl == url_) {
                            index = i
                            break
                        }
                    }
                    if (index < 0) return
                    try {
                        val channelTabInfo = ChannelTabInfo.getInfo(service, channelInfo.tabs[index])
                        Logd(TAG, "refreshYoutubeFeed result1: $channelTabInfo ${channelTabInfo.relatedItems.size}")
                        var infoItems = channelTabInfo.relatedItems
                        var nextPage = channelTabInfo.nextPage
                        val eList: RealmList<Episode> = realmListOf()
                        while (infoItems.isNotEmpty()) {
                            for (r_ in infoItems) {
                                val r = r_ as StreamInfoItem
                                if (r.infoType != InfoItem.InfoType.STREAM) continue
                                Logd(TAG, "item: ${r.uploadDate?.date()?.time} ${r.name}")
                                if ((r.uploadDate?.date()?.time ?: Date(0)) > (newestItem?.getPubDate() ?: Date(0)))
                                    eList.add(episodeFromStreamInfoItem(r))
                                else {
                                    nextPage = null
                                    break
                                }
                            }
                            if (nextPage == null) break
                            try {
                                val page = ChannelTabInfo.getMoreItems(service, channelInfo.tabs[index], nextPage)
                                nextPage = page.nextPage
                                infoItems = page.items
                                Logd(TAG, "refreshYoutubeFeed more infoItems: ${infoItems.size}")
                            } catch (e: Throwable) {
                                Logd(TAG, "refreshYoutubeFeed ChannelTabInfo.getMoreItems error: ${e.message}")
                                break
                            }
                        }
                        Logd(TAG, "refreshYoutubeFeed eList.size: ${eList.size}")
                        val feed_ = Feed(url, null)
                        feed_.type = Feed.FeedType.YOUTUBE.name
                        feed_.hasVideoMedia = true
                        feed_.title = channelInfo.name
                        feed_.fileUrl = File(feedfilePath, feed_.getFeedfileName()).toString()
                        feed_.description = channelInfo.description
                        feed_.author = channelInfo.parentChannelName
                        feed_.imageUrl = if (channelInfo.avatars.isNotEmpty()) channelInfo.avatars.first().url else null
                        feed_.episodes = eList
                        if (fullUpdate) Feeds.updateFeed(applicationContext, feed_) else Feeds.updateFeedSimple(feed_)
                    } catch (e: Throwable) { Logd(TAG, "refreshYoutubeFeed channel error2 ${e.message}") }
                }
            } catch (e: Throwable) { Logd(TAG, "refreshYoutubeFeed error ${e.message}") }
        }
        
        @Throws(Exception::class)
        fun refreshFeed(feed: Feed, force: Boolean, fullUpdate: Boolean) {
            val nextPage = (inputData.getBoolean(EXTRA_NEXT_PAGE, false) && feed.nextPageLink != null)
            if (nextPage) feed.pageNr += 1
            val builder = create(feed)
            builder.setForce(force || feed.lastUpdateFailed)
            if (nextPage) builder.source = feed.nextPageLink
            val request = builder.build()
            val downloader = DefaultDownloaderFactory().create(request) ?: throw Exception("Unable to create downloader")
            downloader.call()
            if (!downloader.result.isSuccessful) {
                if (downloader.cancelled || downloader.result.reason == DownloadError.ERROR_DOWNLOAD_CANCELLED) return
                Logd(TAG, "update failed: unsuccessful cancelled?")
                Feeds.persistFeedLastUpdateFailed(feed, true)
                LogsAndStats.addDownloadStatus(downloader.result)
                return
            }
            val feedUpdateTask = FeedUpdateTask(applicationContext, request)
            val success = if (fullUpdate) feedUpdateTask.run() else feedUpdateTask.runSimple()
            if (!success) {
                Logd(TAG, "update failed: unsuccessful")
                Feeds.persistFeedLastUpdateFailed(feed, true)
                LogsAndStats.addDownloadStatus(feedUpdateTask.downloadStatus)
                return
            }
            if (request.feedfileId == null) return  // No download logs for new subscriptions
            // we create a 'successful' download log if the feed's last refresh failed
            val log = LogsAndStats.getFeedDownloadLog(request.feedfileId)
            if (log.isNotEmpty() && !log[0].isSuccessful) LogsAndStats.addDownloadStatus(feedUpdateTask.downloadStatus)
            if (!request.source.isNullOrEmpty()) {
                when {
                    !downloader.permanentRedirectUrl.isNullOrEmpty() -> Feeds.updateFeedDownloadURL(request.source, downloader.permanentRedirectUrl!!)
                    feedUpdateTask.redirectUrl.isNotEmpty() && feedUpdateTask.redirectUrl != request.source ->
                        Feeds.updateFeedDownloadURL(request.source, feedUpdateTask.redirectUrl)
                }
            }
        }

        class FeedParserTask(private val request: DownloadRequest) : Callable<FeedHandlerResult?> {
            var downloadStatus: DownloadResult
                private set
            var isSuccessful: Boolean = true
                private set

            init {
                downloadStatus = DownloadResult(request.title?:"", 0L, request.feedfileType, false,
                    DownloadError.ERROR_REQUEST_ERROR, Date(), "Unknown error: Status not set")
            }
            override fun call(): FeedHandlerResult? {
                Logd(TAG, "in FeedParserTask call()")
                val feed = Feed(request.source, request.lastModified)
                feed.fileUrl = request.destination
                feed.id = request.feedfileId
                feed.fillPreferences(false, Feed.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, request.username, request.password)
//                if (feed.preferences == null) feed.preferences = FeedPreferences(feed.id, false, FeedPreferences.AutoDeleteAction.GLOBAL,
//                    VolumeAdaptionSetting.OFF, request.username, request.password)
                if (request.arguments != null) feed.pageNr = request.arguments.getInt(DownloadRequest.REQUEST_ARG_PAGE_NR, 0)
                var reason: DownloadError? = null
                var reasonDetailed: String? = null
                val feedHandler = FeedHandler()
                var result: FeedHandlerResult? = null
                try {
                    result = feedHandler.parseFeed(feed)
                    Logd(TAG,  "Parsed ${feed.title}")
                    checkFeedData(feed)
//            TODO: what the shit is this??
                    if (feed.imageUrl.isNullOrEmpty()) feed.imageUrl = Feed.PREFIX_GENERATIVE_COVER + feed.downloadUrl
                } catch (e: SAXException) {
                    isSuccessful = false
                    e.printStackTrace()
                    reason = DownloadError.ERROR_PARSER_EXCEPTION
                    reasonDetailed = e.message
                } catch (e: IOException) {
                    isSuccessful = false
                    e.printStackTrace()
                    reason = DownloadError.ERROR_PARSER_EXCEPTION
                    reasonDetailed = e.message
                } catch (e: ParserConfigurationException) {
                    isSuccessful = false
                    e.printStackTrace()
                    reason = DownloadError.ERROR_PARSER_EXCEPTION
                    reasonDetailed = e.message
                } catch (e: FeedHandler.UnsupportedFeedtypeException) {
                    e.printStackTrace()
                    isSuccessful = false
                    reason = DownloadError.ERROR_UNSUPPORTED_TYPE
                    if ("html".equals(e.rootElement, ignoreCase = true)) reason = DownloadError.ERROR_UNSUPPORTED_TYPE_HTML
                    reasonDetailed = e.message
                } catch (e: InvalidFeedException) {
                    e.printStackTrace()
                    isSuccessful = false
                    reason = DownloadError.ERROR_PARSER_EXCEPTION
                    reasonDetailed = e.message
                } finally {
                    val feedFile = File(request.destination?:"junk")
                    if (feedFile.exists()) {
                        val deleted = feedFile.delete()
                        Logd(TAG, "Deletion of file '" + feedFile.absolutePath + "' " + (if (deleted) "successful" else "FAILED"))
                    }
                }
                if (isSuccessful) {
                    downloadStatus = DownloadResult(feed.id, feed.getTextIdentifier()?:"", DownloadError.SUCCESS, isSuccessful, reasonDetailed?:"")
                    return result
                }
                downloadStatus = DownloadResult(feed.id, feed.getTextIdentifier()?:"", reason?: DownloadError.ERROR_NOT_FOUND, isSuccessful, reasonDetailed?:"")
                return null
            }
            /**
             * Checks if the feed was parsed correctly.
             */
            @Throws(InvalidFeedException::class)
            private fun checkFeedData(feed: Feed) {
                if (feed.title == null) throw InvalidFeedException("Feed has no title")
                for (item in feed.episodes) {
                    if (item.title == null) throw InvalidFeedException("Item has no title: $item")
                }
            }

            /**
             * Thrown if a feed has invalid attribute values.
             */
            class InvalidFeedException(message: String?) : Exception(message) {
                companion object {
                    private const val serialVersionUID = 1L
                }
            }
        }

        class FeedUpdateTask(private val context: Context, request: DownloadRequest) {
            private val task = FeedParserTask(request)
            private var feedHandlerResult: FeedHandlerResult? = null
            val downloadStatus: DownloadResult
                get() = task.downloadStatus
            val redirectUrl: String
                get() = feedHandlerResult?.redirectUrl?:""

            fun run(): Boolean {
                feedHandlerResult = task.call()
                if (!task.isSuccessful) return false
                Feeds.updateFeed(context, feedHandlerResult!!.feed, false)
                return true
            }

            fun runSimple(): Boolean {
                feedHandlerResult = task.call()
                if (!task.isSuccessful) return false
                Feeds.updateFeedSimple(feedHandlerResult!!.feed)
                return true
            }

        }
    }
}
