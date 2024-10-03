package ac.mdiq.podcini.net.feed

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadError
import ac.mdiq.podcini.net.download.service.DownloadRequestCreator.create
import ac.mdiq.podcini.net.download.service.Downloader
import ac.mdiq.podcini.net.download.service.HttpDownloader
import ac.mdiq.podcini.net.feed.parser.FeedHandler
import ac.mdiq.podcini.net.utils.UrlChecker.prepareUrl
import ac.mdiq.podcini.storage.database.Episodes.episodeFromStreamInfoItem
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
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
import android.app.Dialog
import android.content.Context
import android.util.Log
import androidx.annotation.UiThread
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// TODO: Extracted from OnlineFeedFragment, will do some merging later
class DirectSubscribe(val context: Context) {
    private val TAG = "DirectSubscribe"

    var feedSource: String = ""

    private var selectedDownloadUrl: String? = null
    private var feeds: List<Feed>? = null
    private var downloader: Downloader? = null
    private var username: String? = null
    private var password: String? = null

    private var dialog: Dialog? = null

    fun startFeedBuilding(url: String) {
        if (feedSource == "VistaGuide" || url.contains("youtube.com")) {
            feedSource = "VistaGuide"
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    feeds = getFeedList()
                    val service = try {
                        Vista.getService("YouTube")
                    } catch (e: ExtractionException) {
                        throw ExtractionException("YouTube service not found")
                    }
                    selectedDownloadUrl = prepareUrl(url)
                    val feed_ = Feed(selectedDownloadUrl, null)
                    feed_.id = Feed.newId()
                    feed_.type = Feed.FeedType.YOUTUBE.name
                    feed_.hasVideoMedia = true
                    feed_.fileUrl = File(feedfilePath, getFeedfileName(feed_)).toString()
                    val eList: RealmList<Episode> = realmListOf()

                    if (url.startsWith("https://youtube.com/playlist?") || url.startsWith("https://music.youtube.com/playlist?")) {
                        val playlistInfo = PlaylistInfo.getInfo(Vista.getService(0), url) ?: return@launch
                        feed_.title = playlistInfo.name
                        feed_.description = playlistInfo.description?.content ?: ""
                        feed_.author = playlistInfo.uploaderName
                        feed_.imageUrl =
                            if (playlistInfo.thumbnails.isNotEmpty()) playlistInfo.thumbnails.first().url else null
                        var infoItems = playlistInfo.relatedItems
                        var nextPage = playlistInfo.nextPage
                        Logd(TAG, "infoItems: ${infoItems.size}")
                        while (infoItems.isNotEmpty()) {
                            for (r in infoItems) {
                                Logd(TAG, "startFeedBuilding relatedItem: $r")
                                if (r.infoType != InfoItem.InfoType.STREAM) continue
                                val e = episodeFromStreamInfoItem(r)
                                e.feed = feed_
                                e.feedId = feed_.id
                                eList.add(e)
                            }
                            if (nextPage == null || eList.size > 500) break
                            try {
                                val page = PlaylistInfo.getMoreItems(service, url, nextPage) ?: break
                                nextPage = page.nextPage
                                infoItems = page.items
                                Logd(TAG, "more infoItems: ${infoItems.size}")
                            } catch (e: Throwable) {
                                Logd(TAG, "PlaylistInfo.getMoreItems error: ${e.message}")
                                withContext(Dispatchers.Main) { showErrorDialog(e.message, "") }
                                break
                            }
                        }
                        feed_.episodes = eList
//                        withContext(Dispatchers.Main) { showFeedInformation(feed_, mapOf()) }
                        subscribe(feed_)
                    } else {
                        val channelInfo = ChannelInfo.getInfo(service, url)
                        Logd(TAG, "startFeedBuilding result: $channelInfo ${channelInfo.tabs.size}")
                        if (channelInfo.tabs.isEmpty()) {
                            withContext(Dispatchers.Main) { showErrorDialog("Channel is empty", "") }
                            return@launch
                        }
                        try {
                            val channelTabInfo = ChannelTabInfo.getInfo(service, channelInfo.tabs.first())
                            Logd(TAG, "startFeedBuilding result1: $channelTabInfo ${channelTabInfo.relatedItems.size}")
                            feed_.title = channelInfo.name
                            feed_.description = channelInfo.description
                            feed_.author = channelInfo.parentChannelName
                            feed_.imageUrl =
                                if (channelInfo.avatars.isNotEmpty()) channelInfo.avatars.first().url else null

                            var infoItems = channelTabInfo.relatedItems
                            var nextPage = channelTabInfo.nextPage
                            Logd(TAG, "infoItems: ${infoItems.size}")
                            while (infoItems.isNotEmpty()) {
                                for (r in infoItems) {
                                    Logd(TAG, "startFeedBuilding relatedItem: $r")
                                    if (r.infoType != InfoItem.InfoType.STREAM) continue
                                    val e = episodeFromStreamInfoItem(r as StreamInfoItem)
                                    e.feed = feed_
                                    e.feedId = feed_.id
                                    eList.add(e)
                                }
                                if (nextPage == null || eList.size > 200) break
                                try {
                                    val page = ChannelTabInfo.getMoreItems(service, channelInfo.tabs.first(), nextPage)
                                    nextPage = page.nextPage
                                    infoItems = page.items
                                    Logd(TAG, "more infoItems: ${infoItems.size}")
                                } catch (e: Throwable) {
                                    Logd(TAG, "ChannelTabInfo.getMoreItems error: ${e.message}")
                                    withContext(Dispatchers.Main) { showErrorDialog(e.message, "") }
                                    break
                                }
                            }
                            feed_.episodes = eList
//                            withContext(Dispatchers.Main) { showFeedInformation(feed_, mapOf()) }
                            subscribe(feed_)
                        } catch (e: Throwable) {
                            Logd(TAG, "startFeedBuilding error1 ${e.message}")
                            withContext(Dispatchers.Main) { showErrorDialog(e.message, "") }
                        }
                    }
                } catch (e: Throwable) {
                    Logd(TAG, "startFeedBuilding error ${e.message}")
                    withContext(Dispatchers.Main) { showErrorDialog(e.message, "") }
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
                    startFeedBuilding(rssUrl)
                    return
                }
            }
            "XML" -> {}
            else -> {
                Log.e(TAG, "unknown url type $urlType")
                showErrorDialog("unknown url type $urlType", "")
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
                feeds = getFeedList()
                downloader = HttpDownloader(request)
                downloader?.call()
                val status = downloader?.result
                when {
                    request.destination == null || status == null -> return@launch
                    status.isSuccessful -> {
                        try {
                            val result = doParseFeed(request.destination)
//                            if (result != null) withContext(Dispatchers.Main) {
//                                showFeedInformation(result.feed, result.alternateFeedUrls)
//                            }
                            if (result != null) subscribe(result.feed)
                        } catch (e: Throwable) {
                            Logd(TAG, "Feed parser exception: " + Log.getStackTraceString(e))
                            withContext(Dispatchers.Main) { showErrorDialog(e.message, "") }
                        }
                    }
                    else -> withContext(Dispatchers.Main) {
                        when {
                            status.reason == DownloadError.ERROR_UNAUTHORIZED -> {
                                Logd(TAG, "status.reason: DownloadError.ERROR_UNAUTHORIZED")
//                                if (!isRemoving && !isPaused) {
//                                    if (username != null && password != null)
//                                        Toast.makeText(context, R.string.download_error_unauthorized, Toast.LENGTH_LONG).show()
//                                    if (downloader?.downloadRequest?.source != null) {
//                                        dialog = FeedViewAuthenticationDialog(context, R.string.authentication_notification_title, downloader!!.downloadRequest.source!!).create()
//                                        dialog?.show()
//                                    }
//                                }
                            }
                            else -> showErrorDialog(context.getString(from(status.reason)), status.reasonDetailed)
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
                withContext(Dispatchers.Main) { showErrorDialog(e.message, "") }
            }
        }
    }

    @UiThread
    private fun showErrorDialog(errorMsg: String?, details: String) {
        Logd(TAG, "error: ${errorMsg} \n details: $details")
//        if (!isRemoving && !isPaused) {
//            val builder = MaterialAlertDialogBuilder(context)
//            builder.setTitle(R.string.error_label)
//            if (errorMsg != null) {
//                val total = """
//                    $errorMsg
//
//                    $details
//                    """.trimIndent()
//                val errorMessage = SpannableString(total)
//                errorMessage.setSpan(ForegroundColorSpan(-0x77777778), errorMsg.length, total.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
//                builder.setMessage(errorMessage)
//            } else builder.setMessage(R.string.download_error_error_unknown)
//
//            builder.setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int -> dialog.cancel() }
////            if (intent.getBooleanExtra(ARG_WAS_MANUAL_URL, false)) {
////                builder.setNeutralButton(R.string.edit_url_menu) { _: DialogInterface?, _: Int -> editUrl() }
////            }
//            builder.setOnCancelListener {
////                setResult(RESULT_ERROR)
////                finish()
//            }
//            if (dialog != null && dialog!!.isShowing) dialog!!.dismiss()
//            dialog = builder.show()
//        }
    }

    @Throws(Exception::class)
    private fun doParseFeed(destination: String): FeedHandler.FeedHandlerResult? {
        val destinationFile = File(destination)
        return try {
            val feed = Feed(selectedDownloadUrl, null)
            feed.fileUrl = destination
            FeedHandler().parseFeed(feed)
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
            showErrorDialog(e.message, "")
        } finally { connection.disconnect() }
        if (type == null) return null
        Logd(TAG, "connection type: $type")
        return when {
            type.contains("html", ignoreCase = true) -> "HTML"
            type.contains("xml", ignoreCase = true) -> "XML"
            else -> type
        }
    }

    private fun subscribe(feed: Feed) {
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
        Logd(TAG, "fo.id: ${fo?.id} feed.id: ${feed.id}")
    }

//    private inner class FeedViewAuthenticationDialog(context: Context, titleRes: Int, private val feedUrl: String) :
//        AuthenticationDialog(context, titleRes, true, username, password) {
//        override fun onConfirmed(username: String, password: String) {
//            this@TestSubscribe.username = username
//            this@TestSubscribe.password = password
//            startFeedBuilding(feedUrl)
//        }
//    }
}