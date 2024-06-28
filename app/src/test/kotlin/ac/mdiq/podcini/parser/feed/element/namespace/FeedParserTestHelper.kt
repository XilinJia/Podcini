package ac.mdiq.podcini.feed.parser.element.namespace

import ac.mdiq.podcini.net.feed.parser.FeedHandler
import ac.mdiq.podcini.storage.model.Feed
import java.io.File

/**
 * Tests for FeedHandler.
 */
object FeedParserTestHelper {
    /**
     * Returns the File object for a file in the resources folder.
     */
    @JvmStatic
    fun getFeedFile(fileName: String): File {
        return File(FeedParserTestHelper::class.java.classLoader?.getResource(fileName)?.file?:"")
    }

    /**
     * Runs the feed parser on the given file.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun runFeedParser(feedFile: File): Feed {
        val handler = FeedHandler()
        val parsedFeed = Feed("http://example.com/feed", null)
        parsedFeed.fileUrl = (feedFile.absolutePath)
        handler.parseFeed(parsedFeed)
        return parsedFeed
    }
}
