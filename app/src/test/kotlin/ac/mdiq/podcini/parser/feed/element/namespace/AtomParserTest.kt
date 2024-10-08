package ac.mdiq.podcini.feed.parser.element.namespace

import ac.mdiq.podcini.storage.model.Feed
import junit.framework.TestCase.assertEquals
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.RobolectricTestRunner
import java.util.*

/**
 * Tests for Atom feeds in FeedHandler.
 */
@RunWith(RobolectricTestRunner::class)
class AtomParserTest {
    @Test
    @Throws(Exception::class)
    fun testAtomBasic() {
        val feedFile = FeedParserTestHelper.getFeedFile("feed-atom-testAtomBasic.xml")
        val feed = FeedParserTestHelper.runFeedParser(feedFile)
        Assert.assertEquals(Feed.FeedType.ATOM1.name, feed.type)
        Assert.assertEquals("title", feed.title)
        Assert.assertEquals("http://example.com/feed", feed.identifier)
        Assert.assertEquals("http://example.com", feed.link)
        Assert.assertEquals("This is the description", feed.description)
        Assert.assertEquals("http://example.com/payment", feed.paymentLinks!![0].url)
        Assert.assertEquals("http://example.com/picture", feed.imageUrl)
        Assert.assertEquals(10, feed.episodes!!.size.toLong())
        for (i in feed.episodes!!.indices) {
            val item = feed.episodes!![i]
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
    fun testEmptyRelLinks() {
        val feedFile = FeedParserTestHelper.getFeedFile("feed-atom-testEmptyRelLinks.xml")
        val feed = FeedParserTestHelper.runFeedParser(feedFile)
        Assert.assertEquals(Feed.FeedType.ATOM1.name, feed.type)
        Assert.assertEquals("title", feed.title)
        Assert.assertEquals("http://example.com/feed", feed.identifier)
        Assert.assertEquals("http://example.com", feed.link)
        Assert.assertEquals("This is the description", feed.description)
        Assert.assertNull(feed.paymentLinks)
        Assert.assertEquals("http://example.com/picture", feed.imageUrl)
        Assert.assertEquals(1, feed.episodes!!.size.toLong())

        // feed entry
        val item = feed.episodes!![0]
        Assert.assertEquals("http://example.com/item-0", item.identifier)
        Assert.assertEquals("item-0", item.title)
        Assert.assertNull(item.description)
        Assert.assertEquals("http://example.com/items/0", item.link)
        assertEquals(Date(0), item.getPubDate())
        Assert.assertNull(item.paymentLink)
        Assert.assertEquals("http://example.com/picture", item.imageLocation)
        // media
        Assert.assertFalse(item.media != null)
        // chapters
        Assert.assertNull(item.chapters)
    }

    @Test
    @Throws(Exception::class)
    fun testLogoWithWhitespace() {
        val feedFile = FeedParserTestHelper.getFeedFile("feed-atom-testLogoWithWhitespace.xml")
        val feed = FeedParserTestHelper.runFeedParser(feedFile)
        Assert.assertEquals("title", feed.title)
        Assert.assertEquals("http://example.com/feed", feed.identifier)
        Assert.assertEquals("http://example.com", feed.link)
        Assert.assertEquals("This is the description", feed.description)
        Assert.assertEquals("http://example.com/payment", feed.paymentLinks!![0].url)
        Assert.assertEquals("https://example.com/image.png", feed.imageUrl)
        Assert.assertEquals(0, feed.episodes!!.size.toLong())
    }
}
