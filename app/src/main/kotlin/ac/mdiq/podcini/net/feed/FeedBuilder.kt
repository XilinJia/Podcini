package ac.mdiq.podcini.net.feed

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.DownloadRequestCreator.create
import ac.mdiq.podcini.net.download.service.Downloader
import ac.mdiq.podcini.net.download.service.HttpDownloader
import ac.mdiq.podcini.net.feed.parser.FeedHandler
import ac.mdiq.podcini.net.utils.UrlChecker.prepareUrl
import ac.mdiq.podcini.storage.database.Episodes.episodeFromStreamInfoItem
import ac.mdiq.podcini.storage.database.Feeds.updateFeed
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.FilesUtils.feedfilePath
import ac.mdiq.podcini.storage.utils.FilesUtils.getFeedfileName
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.error.DownloadErrorLabel.from
import ac.mdiq.vista.extractor.InfoItem
import ac.mdiq.vista.extractor.Vista
import ac.mdiq.vista.extractor.channel.ChannelInfo
import ac.mdiq.vista.extractor.channel.tabs.ChannelTabInfo
import ac.mdiq.vista.extractor.exceptions.ExtractionException
import ac.mdiq.vista.extractor.playlist.PlaylistInfo
import ac.mdiq.vista.extractor.stream.StreamInfoItem
import android.content.Context
import android.util.Log
import io.realm.kotlin.ext.realmListOf
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class FeedBuilder(val context: Context, val showError: (String?, String)->Unit) {
    private val TAG = "DirectSubscribe"

    var feedSource: String = ""
    var selectedDownloadUrl: String? = null
    private var downloader: Downloader? = null

    fun startFeedBuilding(url: String, username: String?, password: String?, handleFeed: (Feed, Map<String, String>)->Unit) {
        if (feedSource == "VistaGuide" || url.contains("youtube.com")) {
            feedSource = "VistaGuide"
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val service = try { Vista.getService("YouTube") } catch (e: ExtractionException) { throw ExtractionException("YouTube service not found") }
                    selectedDownloadUrl = prepareUrl(url)
                    val feed_ = Feed(selectedDownloadUrl, null)
                    feed_.isBuilding = true
                    feed_.id = Feed.newId()
                    feed_.type = Feed.FeedType.YOUTUBE.name
                    feed_.hasVideoMedia = true
                    feed_.fileUrl = File(feedfilePath, getFeedfileName(feed_)).toString()
                    val eList: MutableList<Episode> = mutableListOf()

                    val uURL = URL(url)
//                    if (url.startsWith("https://youtube.com/playlist?") || url.startsWith("https://music.youtube.com/playlist?")) {
                    if (uURL.path.startsWith("/playlist") || uURL.path.startsWith("/playlist")) {
                        val playlistInfo = PlaylistInfo.getInfo(Vista.getService(0), url) ?: return@launch
                        feed_.title = playlistInfo.name
                        feed_.description = playlistInfo.description?.content ?: ""
                        feed_.author = playlistInfo.uploaderName
                        feed_.imageUrl = if (playlistInfo.thumbnails.isNotEmpty()) playlistInfo.thumbnails.first().url else null
                        feed_.episodes = realmListOf()
                        var infoItems = playlistInfo.relatedItems
                        var nextPage = playlistInfo.nextPage
                        Logd(TAG, "infoItems: ${infoItems.size}")
                        CoroutineScope(Dispatchers.IO).launch {
                            while (infoItems.isNotEmpty()) {
                                eList.clear()
                                for (r in infoItems) {
                                    Logd(TAG, "startFeedBuilding relatedItem: $r")
                                    if (r.infoType != InfoItem.InfoType.STREAM) continue
                                    val e = episodeFromStreamInfoItem(r)
                                    e.feed = feed_
                                    e.feedId = feed_.id
                                    eList.add(e)
                                }
                                feed_.episodes.addAll(eList)
                                if (nextPage == null || feed_.episodes.size > 1000) break
                                try {
                                    val page = PlaylistInfo.getMoreItems(service, url, nextPage) ?: break
                                    nextPage = page.nextPage
                                    infoItems = page.items
                                    Logd(TAG, "more infoItems: ${infoItems.size}")
                                } catch (e: Throwable) {
                                    Logd(TAG, "PlaylistInfo.getMoreItems error: ${e.message}")
                                    withContext(Dispatchers.Main) { showError(e.message, "") }
                                    break
                                }
                            }
                            feed_.isBuilding = false
                        }
                        withContext(Dispatchers.Main) { handleFeed(feed_, mapOf()) }
                    } else {
                        val channelInfo = ChannelInfo.getInfo(service, url)
                        Logd(TAG, "startFeedBuilding result: $channelInfo ${channelInfo.tabs.size}")
                        if (channelInfo.tabs.isEmpty()) {
                            withContext(Dispatchers.Main) { showError("Channel is empty", "") }
                            return@launch
                        }
                        try {
                            val channelTabInfo = ChannelTabInfo.getInfo(service, channelInfo.tabs.first())
                            Logd(TAG, "startFeedBuilding result1: $channelTabInfo ${channelTabInfo.relatedItems.size}")
                            feed_.title = channelInfo.name
                            feed_.description = channelInfo.description
                            feed_.author = channelInfo.parentChannelName
                            feed_.imageUrl = if (channelInfo.avatars.isNotEmpty()) channelInfo.avatars.first().url else null
                            feed_.episodes = realmListOf()

                            var infoItems = channelTabInfo.relatedItems
                            var nextPage = channelTabInfo.nextPage
                            Logd(TAG, "infoItems: ${infoItems.size}")
                            CoroutineScope(Dispatchers.IO).launch {
                                while (infoItems.isNotEmpty()) {
                                    eList.clear()
                                    for (r in infoItems) {
                                        Logd(TAG, "startFeedBuilding relatedItem: $r")
                                        if (r.infoType != InfoItem.InfoType.STREAM) continue
                                        val e = episodeFromStreamInfoItem(r as StreamInfoItem)
                                        e.feed = feed_
                                        e.feedId = feed_.id
                                        eList.add(e)
                                    }
                                    feed_.episodes.addAll(eList)
                                    if (nextPage == null || feed_.episodes.size > 1000) break
                                    try {
                                        val page = ChannelTabInfo.getMoreItems(service, channelInfo.tabs.first(), nextPage)
                                        nextPage = page.nextPage
                                        infoItems = page.items
                                        Logd(TAG, "more infoItems: ${infoItems.size}")
                                    } catch (e: Throwable) {
                                        Logd(TAG, "ChannelTabInfo.getMoreItems error: ${e.message}")
                                        withContext(Dispatchers.Main) { showError(e.message, "") }
                                        break
                                    }
                                }
                                feed_.isBuilding = false
                            }
                            withContext(Dispatchers.Main) { handleFeed(feed_, mapOf()) }
                        } catch (e: Throwable) {
                            Logd(TAG, "startFeedBuilding error1 ${e.message}")
                            withContext(Dispatchers.Main) { showError(e.message, "") }
                        }
                    }
                } catch (e: Throwable) {
                    Logd(TAG, "startFeedBuilding error ${e.message}")
                    withContext(Dispatchers.Main) { showError(e.message, "") }
                }
            }
            return
        }

//        handle normal podcast source
        when (val urlType = htmlOrXml(url)) {
            "HTML" -> {
                val doc = Jsoup.connect(url).get()
                val linkElements = doc.select("link[type=application/rss+xml]")
//                TODO: should show all as options
                for (element in linkElements) {
                    val rssUrl = element.attr("href")
                    Logd(TAG, "RSS URL: $rssUrl")
                    startFeedBuilding(rssUrl, username, password) {feed, map -> handleFeed(feed, map) }
                }
            }
            "XML" -> {}
            else -> {
                Log.e(TAG, "unknown url type $urlType")
                showError("unknown url type $urlType", "")
                return
            }
        }
        selectedDownloadUrl = prepareUrl(url)
        val request = create(Feed(selectedDownloadUrl, null))
            .withAuthentication(username, password)
            .withInitiatedByUser(true)
            .build()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                downloader = HttpDownloader(request)
                downloader?.call()
                val status = downloader?.result
                when {
                    request.destination == null || status == null -> return@launch
                    status.isSuccessful -> {
                        try {
                            val result = doParseFeed(request.destination)
                            if (result != null) withContext(Dispatchers.Main) { handleFeed(result.feed, result.alternateFeedUrls) }
                        } catch (e: Throwable) {
                            Logd(TAG, "Feed parser exception: " + Log.getStackTraceString(e))
                            withContext(Dispatchers.Main) { showError(e.message, "") }
                        }
                    }
                    else -> withContext(Dispatchers.Main) { showError(context.getString(from(status.reason)), status.reasonDetailed) }
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
                withContext(Dispatchers.Main) { showError(e.message, "") }
            }
        }
    }

    /**
     * Try to parse the feed.
     * @return  The FeedHandlerResult if successful.
     * Null if unsuccessful but we started another attempt.
     * @throws Exception If unsuccessful but we do not know a resolution.
     */
    @Throws(Exception::class)
    private fun doParseFeed(destination: String): FeedHandler.FeedHandlerResult? {
        val destinationFile = File(destination)
        return try {
            val feed = Feed(selectedDownloadUrl, null)
            feed.isBuilding = true
            feed.fileUrl = destination
            val result = FeedHandler().parseFeed(feed)
            feed.isBuilding = false
            result
        } catch (e: FeedHandler.UnsupportedFeedtypeException) {
            Logd(TAG, "Unsupported feed type detected")
            if ("html".equals(e.rootElement, ignoreCase = true)) {
                if (selectedDownloadUrl != null) {
//                    val doc = Jsoup.connect(selectedDownloadUrl).get()
//                    val linkElements = doc.select("link[type=application/rss+xml]")
//                    for (element in linkElements) {
//                        val rssUrl = element.attr("href")
//                        Log.d(TAG, "RSS URL: $rssUrl")
//                        val rc = destinationFile.delete()
//                        Log.d(TAG, "Deleted feed source file. Result: $rc")
//                        startFeedDownload(rssUrl)
//                        return null
//                    }
//                    val dialogShown = showFeedDiscoveryDialog(destinationFile, selectedDownloadUrl!!)
//                    if (dialogShown) null // Should not display an error message
//                    else throw FeedHandler.UnsupportedFeedtypeException(getString(R.string.download_error_unsupported_type_html))
                    throw FeedHandler.UnsupportedFeedtypeException(context.getString(R.string.download_error_unsupported_type_html))
                } else null
            } else throw e
        } catch (e: Exception) {
            Log.e(TAG, Log.getStackTraceString(e))
            throw e
        } finally {
            val rc = destinationFile.delete()
            Logd(TAG, "Deleted feed source file. Result: $rc")
        }
    }

    private fun htmlOrXml(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        var type: String? = null
        try { type = connection.contentType } catch (e: IOException) {
            Log.e(TAG, "Error connecting to URL", e)
            showError(e.message, "")
        } finally { connection.disconnect() }
        if (type == null) return null
        Logd(TAG, "connection type: $type")
        return when {
            type.contains("html", ignoreCase = true) -> "HTML"
            type.contains("xml", ignoreCase = true) -> "XML"
            else -> type
        }
    }

    fun subscribe(feed: Feed) {
        while (feed.isBuilding) {
            runBlocking { delay(200) }
        }
        feed.id = 0L
        for (item in feed.episodes) {
            item.id = 0L
            item.media?.id = 0L
            item.feedId = null
            item.feed = feed
            val media = item.media
            media?.episode = item
        }
        val fo = updateFeed(context, feed, false)
//        if (fo?.downloadUrl != null || fo?.link != null) {
//            val fLog = SubscriptionLog(fo.id, fo.title?:"", fo.downloadUrl?:"", fo.link?:"", SubscriptionLog.Type.Feed.name)
//            upsertBlk(fLog) {}
//        }
        Logd(TAG, "fo.id: ${fo?.id} feed.id: ${feed.id}")
    }

}