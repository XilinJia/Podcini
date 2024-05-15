package ac.mdiq.podcini.net.download.service.handler

import ac.mdiq.podcini.feed.parser.FeedHandlerResult
import android.util.Log
import ac.mdiq.podcini.util.InvalidFeedException
import ac.mdiq.podcini.storage.model.download.DownloadError
import ac.mdiq.podcini.storage.model.download.DownloadResult
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.storage.model.feed.FeedPreferences
import ac.mdiq.podcini.storage.model.feed.VolumeAdaptionSetting
import ac.mdiq.podcini.net.download.serviceinterface.DownloadRequest
import ac.mdiq.podcini.feed.parser.UnsupportedFeedtypeException
import ac.mdiq.podcini.util.Logd
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.Callable
import javax.xml.parsers.ParserConfigurationException

class FeedParserTask(private val request: DownloadRequest) : Callable<FeedHandlerResult?> {
    var downloadStatus: DownloadResult
        private set
    var isSuccessful: Boolean = true
        private set

    init {
        downloadStatus = DownloadResult(0, request.title?:"", 0, request.feedfileType, false,
            DownloadError.ERROR_REQUEST_ERROR, Date(), "Unknown error: Status not set")
    }

    override fun call(): FeedHandlerResult? {
        val feed = Feed(request.source, request.lastModified)
        feed.file_url = request.destination
        feed.id = request.feedfileId
        feed.setDownloaded(true)
        feed.preferences = FeedPreferences(0, false, FeedPreferences.AutoDeleteAction.GLOBAL,
            VolumeAdaptionSetting.OFF, FeedPreferences.NewEpisodesAction.GLOBAL, request.username, request.password)
        if (request.arguments != null) feed.pageNr = request.arguments.getInt(DownloadRequest.REQUEST_ARG_PAGE_NR, 0)

        var reason: DownloadError? = null
        var reasonDetailed: String? = null
        val feedHandler = ac.mdiq.podcini.feed.parser.FeedHandler()

        var result: FeedHandlerResult? = null
        try {
            result = feedHandler.parseFeed(feed)
            Logd(TAG, feed.title + " parsed")
            checkFeedData(feed)
//            TODO: what the shit is this??
            if (feed.imageUrl.isNullOrEmpty()) feed.imageUrl = Feed.PREFIX_GENERATIVE_COVER + feed.download_url
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
        } catch (e: UnsupportedFeedtypeException) {
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
            downloadStatus = DownloadResult(feed, feed.getHumanReadableIdentifier()?:"", DownloadError.SUCCESS, isSuccessful, reasonDetailed?:"")
            return result
        } else {
            downloadStatus = DownloadResult(feed, feed.getHumanReadableIdentifier()?:"", reason?:DownloadError.ERROR_NOT_FOUND,
                isSuccessful, reasonDetailed?:"")
            return null
        }
    }

    /**
     * Checks if the feed was parsed correctly.
     */
    @Throws(InvalidFeedException::class)
    private fun checkFeedData(feed: Feed) {
        if (feed.title == null) throw InvalidFeedException("Feed has no title")
        checkFeedItems(feed)
    }

    @Throws(InvalidFeedException::class)
    private fun checkFeedItems(feed: Feed) {
        for (item in feed.items) {
            if (item.title == null) throw InvalidFeedException("Item has no title: $item")
        }
    }

    companion object {
        private const val TAG = "FeedParserTask"
    }
}
