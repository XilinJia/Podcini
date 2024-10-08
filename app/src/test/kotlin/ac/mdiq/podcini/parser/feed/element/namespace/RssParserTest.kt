package ac.mdiq.podcini.feed.parser.element.namespace

import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.MediaType
import ac.mdiq.podcini.feed.parser.element.namespace.FeedParserTestHelper.getFeedFile
import ac.mdiq.podcini.feed.parser.element.namespace.FeedParserTestHelper.runFeedParser
import junit.framework.TestCase.assertEquals
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.RobolectricTestRunner
import java.util.*

/**
 * Tests for RSS feeds in FeedHandler.
 */
@RunWith(RobolectricTestRunner::class)
class RssParserTest {
    @Test
    @Throws(Exception::class)
    fun testRss2Basic() {
        val feedFile = getFeedFile("feed-rss-testRss2Basic.xml")
        val feed = runFeedParser(feedFile)
        Assert.assertEquals(Feed.FeedType.RSS.name, feed.type)
        Assert.assertEquals("title", feed.title)
        Assert.assertEquals("en", feed.language)
        Assert.assertEquals("http://example.com", feed.link)
        Assert.assertEquals("This is the description", feed.description)
        Assert.assertEquals("http://example.com/payment", feed.paymentLinks[0].url)
        Assert.assertEquals("http://example.com/picture", feed.imageUrl)
        Assert.assertEquals(10, feed.episodes.size.toLong())
        for (i in feed.episodes.indices) {
            val item = feed.episodes[i]
            Assert.assertEquals("http://example.com/item-$i", item.identifier)
            Assert.assertEquals("item-$i", item.title)
            Assert.assertNull(item.description)
            Assert.assertEquals("http://example.com/items/$i", item.link)
            assertEquals(Date((i * 60000).toLong()), item.getPubDate())
            Assert.assertNull(item.paymentLink)
            Assert.assertEquals("http://example.com/picture", item.imageLocation)
            // media
            Assert.assertTrue(item.media != null)
            val media = item.media
            Assert.assertEquals("http://example.com/media-$i", media!!.downloadUrl)
            Assert.assertEquals((1024 * 1024).toLong(), media.size)
            Assert.assertEquals("audio/mp3", media.mimeType)
            // chapters
            Assert.assertNull(item.chapters)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testImageWithWhitespace() {
        val feedFile = getFeedFile("feed-rss-testImageWithWhitespace.xml")
        val feed = runFeedParser(feedFile)
        Assert.assertEquals("title", feed.title)
        Assert.assertEquals("http://example.com", feed.link)
        Assert.assertEquals("This is the description", feed.description)
        Assert.assertEquals("http://example.com/payment", feed.paymentLinks[0].url)
        Assert.assertEquals("https://example.com/image.png", feed.imageUrl)
        Assert.assertEquals(0, feed.episodes.size.toLong())
    }

    @Test
    @Throws(Exception::class)
    fun testMediaContentMime() {
        val feedFile = getFeedFile("feed-rss-testMediaContentMime.xml")
        val feed = runFeedParser(feedFile)
        Assert.assertEquals("title", feed.title)
        Assert.assertEquals("http://example.com", feed.link)
        Assert.assertEquals("This is the description", feed.description)
        Assert.assertEquals("http://example.com/payment", feed.paymentLinks[0].url)
        Assert.assertNull(feed.imageUrl)
        Assert.assertEquals(1, feed.episodes.size.toLong())
        val feedItem = feed.episodes[0]
        Assert.assertEquals(MediaType.VIDEO, feedItem.media!!.getMediaType())
        Assert.assertEquals("https://www.example.com/file.mp4", feedItem.media!!.downloadUrl)
    }

    @Test
    @Throws(Exception::class)
    fun testMultipleFundingTags() {
        val feedFile = getFeedFile("feed-rss-testMultipleFundingTags.xml")
        val feed = runFeedParser(feedFile)
        Assert.assertEquals(3, feed.paymentLinks.size.toLong())
        Assert.assertEquals("Text 1", feed.paymentLinks[0].content)
        Assert.assertEquals("https://example.com/funding1", feed.paymentLinks[0].url)
        Assert.assertEquals("Text 2", feed.paymentLinks[1].content)
        Assert.assertEquals("https://example.com/funding2", feed.paymentLinks[1].url)
        Assert.assertTrue(feed.paymentLinks[2].content.isNullOrBlank())
        Assert.assertEquals("https://example.com/funding3", feed.paymentLinks[2].url)
    }

    @Test
    @Throws(Exception::class)
    fun testUnsupportedElements() {
        val feedFile = getFeedFile("feed-rss-testUnsupportedElements.xml")
        val feed = runFeedParser(feedFile)
        Assert.assertEquals(1, feed.episodes.size.toLong())
        Assert.assertEquals("item-0", feed.episodes[0].title)
    }
}
