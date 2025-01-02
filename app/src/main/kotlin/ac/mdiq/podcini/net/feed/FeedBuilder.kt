package ac.mdiq.podcini.net.feed

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.DownloadRequestCreator.create
import ac.mdiq.podcini.net.download.service.Downloader
import ac.mdiq.podcini.net.download.service.HttpDownloader
import ac.mdiq.podcini.net.feed.parser.FeedHandler
import ac.mdiq.podcini.net.utils.NetworkUtils.prepareUrl
import ac.mdiq.podcini.storage.database.Episodes.episodeFromStreamInfoItem
import ac.mdiq.podcini.storage.database.Feeds.updateFeed
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.StorageUtils.feedfilePath
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.error.DownloadErrorLabel.from
import ac.mdiq.vista.extractor.InfoItem
import ac.mdiq.vista.extractor.Vista
import ac.mdiq.vista.extractor.channel.ChannelInfo
import ac.mdiq.vista.extractor.channel.tabs.ChannelTabInfo
import ac.mdiq.vista.extractor.playlist.PlaylistInfo
import ac.mdiq.vista.extractor.stream.StreamInfoItem
import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.realm.kotlin.ext.realmListOf
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.Throws

class FeedBuilder(val context: Context, val showError: (String?, String)->Unit) {
    private val TAG = "FeedBuilder"

    private val service by lazy { Vista.getService("YouTube") }
    private val ytTabsMap: MutableMap<Int, String> = mutableMapOf()
    private lateinit var channelInfo: ChannelInfo

    var feedSource: String = ""
    var selectedDownloadUrl: String? = null
    private var downloader: Downloader? = null

    private var urlInit: String = ""

    fun isYoutube(url: String): Boolean {
        urlInit = url
        val isit = (feedSource == "VistaGuide" || url.contains("youtube.com"))
        if (isit) feedSource = "VistaGuide"
        return isit
    }

    fun isYoutubeChannel(): Boolean {
        val uURL = URL(urlInit)
        return !uURL.path.startsWith("/playlist")
    }

    fun youtubeChannelValidTabs(): Int {
        channelInfo = ChannelInfo.getInfo(service, urlInit)
        var count = 0
        for (i in channelInfo.tabs.indices) {
            val t = channelInfo.tabs[i]
            var url = t.url
            Logd(TAG, "url: $url ${t.originalUrl} ${t.baseUrl}")
            if (!url.startsWith("http")) url = urlInit + url
            val uURL = URL(url)
            val urlEnd = uURL.path.split("/").last()
            if (urlEnd != "playlists" && urlEnd != "shorts") count++
        }
        return count
    }

    @Composable
    fun ConfirmYTChannelTabsDialog(onDismissRequest: () -> Unit, handleFeed: (Feed, Map<String, String>)->Unit) {
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
            title = { Text(stringResource(R.string.choose_tab), style = CustomTextStyles.titleCustom) },
            text = {
                Column {
                    val selectedId = remember { mutableStateOf<Int?>(null) }
                    for (i in channelInfo.tabs.indices) {
                        val t = channelInfo.tabs[i]
                        var url = t.url
                        Logd(TAG, "ConfirmYTChannelTabsDialog url: $url ${t.originalUrl} ${t.baseUrl}")
                        if (!url.startsWith("http")) url = urlInit + url
                        val uURL = URL(url)
                        val urlEnd = uURL.path.split("/").last()
                        if (urlEnd != "playlists" && urlEnd != "shorts") Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 30.dp)) {
                                var checked by remember { mutableStateOf(false) }
                                Checkbox(checked = selectedId.value == i, onCheckedChange = {
                                    selectedId.value = if (selectedId.value == i) null else i
                                    checked = it
                                    if (checked) ytTabsMap[i] = urlEnd else ytTabsMap.remove(i)
                                })
                                Text(text = urlEnd, style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 10.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        for (i in ytTabsMap.keys) {
                            Logd(TAG, "Subscribing $i ${channelInfo.tabs[i].url}")
                            buildYTChannel(i, ytTabsMap[i]!!) { feed_, map -> handleFeed(feed_, map) }
                        }
                    }
                    onDismissRequest()
                }) { Text(text = "Confirm") }
            },
            dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    suspend fun buildYTPlaylist(handleFeed: (Feed, Map<String, String>)->Unit) {
        try {
            val url = urlInit
            val playlistInfo = PlaylistInfo.getInfo(service, url) ?: return
            selectedDownloadUrl = prepareUrl(url)
            Logd(TAG, "selectedDownloadUrl: $selectedDownloadUrl url: $url")
            val feed_ = Feed(selectedDownloadUrl, null)
            feed_.isBuilding = true
            feed_.id = Feed.newId()
            feed_.type = Feed.FeedType.YOUTUBE.name
            feed_.hasVideoMedia = true
            feed_.fileUrl = File(feedfilePath, feed_.getFeedfileName()).toString()
            val eList: MutableList<Episode> = mutableListOf()
            feed_.title = playlistInfo.name
            feed_.description = playlistInfo.description?.content ?: ""
            feed_.author = playlistInfo.uploaderName
            feed_.imageUrl = if (playlistInfo.thumbnails.isNotEmpty()) playlistInfo.thumbnails.first().url else null
            feed_.episodes = realmListOf()
            var infoItems = playlistInfo.relatedItems
            var nextPage = playlistInfo.nextPage
            Logd(TAG, "infoItems: ${infoItems.size}")
            val map = mutableMapOf<String, Episode>()
            CoroutineScope(Dispatchers.IO).launch {
                var count = 0
                while (infoItems.isNotEmpty()) {
                    eList.clear()
                    for (r in infoItems) {
                        Logd(TAG, "startFeedBuilding relatedItem: $r")
                        if (r.infoType != InfoItem.InfoType.STREAM) continue
                        count++
//                        if (map[r.url] != null) continue
                        val e = episodeFromStreamInfoItem(r)
                        map[r.url] = e
                        e.feed = feed_
                        e.feedId = feed_.id
                        eList.add(e)
                    }
                    feed_.episodes.addAll(eList)
                    if (nextPage == null || count > 2000) break
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
                map.clear()
            }
            withContext(Dispatchers.Main) { handleFeed(feed_, mapOf()) }
        } catch (e: Throwable) {
            Logd(TAG, "startFeedBuilding error ${e.message}")
            withContext(Dispatchers.Main) { showError(e.message, "") }
        }
    }

    suspend fun buildYTChannel(index: Int, title: String, handleFeed: (Feed, Map<String, String>)->Unit) {
        Logd(TAG, "startFeedBuilding result: $channelInfo ${channelInfo.tabs.size}")
        var url = channelInfo.tabs[index].url
        if (!url.startsWith("http")) url = urlInit
        try {
            selectedDownloadUrl = prepareUrl(url)
            Logd(TAG, "selectedDownloadUrl: $selectedDownloadUrl url: $url")
            val channelTabInfo = ChannelTabInfo.getInfo(service, channelInfo.tabs[index])
            Logd(TAG, "startFeedBuilding result1: $channelTabInfo ${channelTabInfo.relatedItems.size}")
            val feed_ = Feed(selectedDownloadUrl, null)
            feed_.isBuilding = true
            feed_.id = Feed.newId()
            feed_.type = Feed.FeedType.YOUTUBE.name
            feed_.hasVideoMedia = true
            feed_.fileUrl = File(feedfilePath, feed_.getFeedfileName()).toString()
            val eList: MutableList<Episode> = mutableListOf()
            feed_.title = channelInfo.name + " " + title
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
                    if (nextPage == null || feed_.episodes.size > 2000) break
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

    fun buildPodcast(url: String, username: String?, password: String?, handleFeed: (Feed, Map<String, String>)->Unit) {
        when (val urlType = htmlOrXml(url)) {
            "HTML" -> {
                val doc = Jsoup.connect(url).get()
                val linkElements = doc.select("link[type=application/rss+xml]")
//                TODO: should show all as options
                for (element in linkElements) {
                    val rssUrl = element.attr("href")
                    Logd(TAG, "RSS URL: $rssUrl")
                    buildPodcast(rssUrl, username, password) {feed, map -> handleFeed(feed, map) }
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
        val request = create(Feed(selectedDownloadUrl, null)).withAuthentication(username, password).withInitiatedByUser(true).build()
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
        while (feed.isBuilding) runBlocking { delay(200) }
        feed.id = 0L
        for (item in feed.episodes) {
            item.id = 0L
            item.feedId = null
            item.feed = feed
        }
        val fo = updateFeed(context, feed, false)
//        if (fo?.downloadUrl != null || fo?.link != null) {
//            val fLog = SubscriptionLog(fo.id, fo.title?:"", fo.downloadUrl?:"", fo.link?:"", SubscriptionLog.Type.Feed.name)
//            upsertBlk(fLog) {}
//        }
        Logd(TAG, "fo.id: ${fo?.id} feed.id: ${feed.id}")
    }

    /**
     *
     * @return true if a FeedDiscoveryDialog is shown, false otherwise (e.g., due to no feed found).
     */
//    private fun showFeedDiscoveryDialog(feedFile: File, baseUrl: String): Boolean {
//        val fd = FeedDiscoverer()
//        val urlsMap: Map<String, String>
//        try {
//            urlsMap = fd.findLinks(feedFile, baseUrl)
//            if (urlsMap.isEmpty()) return false
//        } catch (e: IOException) {
//            e.printStackTrace()
//            return false
//        }
//
//        if (isRemoving || isPaused) return false
//        val titles: MutableList<String?> = ArrayList()
//        val urls: List<String> = ArrayList(urlsMap.keys)
//        for (url in urls) {
//            titles.add(urlsMap[url])
//        }
//        if (urls.size == 1) {
//            // Skip dialog and display the item directly
//            feeds = getFeedList()
//            subscribe.startFeedBuilding(urls[0]) {feed, map -> showFeedInformation(feed, map) }
//            return true
//        }
//        val adapter = ArrayAdapter(requireContext(), R.layout.ellipsize_start_listitem, R.id.txtvTitle, titles)
//        val onClickListener = DialogInterface.OnClickListener { dialog: DialogInterface, which: Int ->
//            val selectedUrl = urls[which]
//            dialog.dismiss()
//            feeds = getFeedList()
//            subscribe.startFeedBuilding(selectedUrl) {feed, map -> showFeedInformation(feed, map) }
//        }
//        val ab = MaterialAlertDialogBuilder(requireContext())
//            .setTitle(R.string.feeds_label)
//            .setCancelable(true)
//            .setOnCancelListener { _: DialogInterface? ->/*                finish() */ }
//            .setAdapter(adapter, onClickListener)
//        requireActivity().runOnUiThread {
//            if (dialog != null && dialog!!.isShowing) dialog!!.dismiss()
//            dialog = ab.show()
//        }
//        return true
//    }

    /**
     * Finds RSS/Atom URLs in a HTML document using the auto-discovery techniques described here:
     * http://www.rssboard.org/rss-autodiscovery
     * http://blog.whatwg.org/feed-autodiscovery
     */
//    class FeedDiscoverer {
//        /**
//         * Discovers links to RSS and Atom feeds in the given File which must be a HTML document.
//         * @return A map which contains the feed URLs as keys and titles as values (the feed URL is also used as a title if
//         * a title cannot be found).
//         */
//        @Throws(IOException::class)
//        fun findLinks(inVal: File, baseUrl: String): Map<String, String> {
//            return findLinks(Jsoup.parse(inVal), baseUrl)
//        }
//        /**
//         * Discovers links to RSS and Atom feeds in the given File which must be a HTML document.
//         * @return A map which contains the feed URLs as keys and titles as values (the feed URL is also used as a title if
//         * a title cannot be found).
//         */
//        fun findLinks(inVal: String, baseUrl: String): Map<String, String> {
//            return findLinks(Jsoup.parse(inVal), baseUrl)
//        }
//        private fun findLinks(document: Document, baseUrl: String): Map<String, String> {
//            val res: MutableMap<String, String> = ArrayMap()
//            val links = document.head().getElementsByTag("link")
//            for (link in links) {
//                val rel = link.attr("rel")
//                val href = link.attr("href")
//                if (href.isNotEmpty() && (rel == "alternate" || rel == "feed")) {
//                    val type = link.attr("type")
//                    if (type == MIME_RSS || type == MIME_ATOM) {
//                        val title = link.attr("title")
//                        val processedUrl = processURL(baseUrl, href)
//                        if (processedUrl != null) res[processedUrl] = title.ifEmpty { href }
//                    }
//                }
//            }
//            return res
//        }
//        private fun processURL(baseUrl: String, strUrl: String): String? {
//            val uri = Uri.parse(strUrl)
//            if (uri.isRelative) {
//                val res = Uri.parse(baseUrl).buildUpon().path(strUrl).build()
//                return res?.toString()
//            } else return strUrl
//        }
//        companion object {
//            private const val MIME_RSS = "application/rss+xml"
//            private const val MIME_ATOM = "application/atom+xml"
//        }
//    }

}