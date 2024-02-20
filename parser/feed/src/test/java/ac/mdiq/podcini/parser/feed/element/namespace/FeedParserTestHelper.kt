package ac.mdiq.podcini.parser.feed.element.namespace

import ac.mdiq.podcini.model.feed.Feed
import ac.mdiq.podcini.parser.feed.FeedHandler
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
        return File(FeedParserTestHelper::class.java.classLoader.getResource(fileName).file)
    }

    /**
     * Runs the feed parser on the given file.
     */
    @JvmStatic
    @Throws(Exception::class)
    fun runFeedParser(feedFile: File): Feed {
        val handler = FeedHandler()
        val parsedFeed = Feed("http://example.com/feed", null)
        parsedFeed.setFile_url(feedFile.absolutePath)
        parsedFeed.setDownloaded(true)
        handler.parseFeed(parsedFeed)
        return parsedFeed
    }
}
