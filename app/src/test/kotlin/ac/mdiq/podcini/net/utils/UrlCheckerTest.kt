package ac.mdiq.podcini.net.utils

import ac.mdiq.podcini.net.utils.UrlChecker.prepareUrl
import ac.mdiq.podcini.net.utils.UrlChecker.urlEquals
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.UnsupportedEncodingException

/**
 * Test class for [UrlChecker]
 */
@RunWith(RobolectricTestRunner::class)
class UrlCheckerTest {
    @Test
    fun testCorrectURLHttp() {
        val inVal = "http://example.com"
        val out = prepareUrl(inVal)
        Assert.assertEquals(inVal, out)
    }

    @Test
    fun testCorrectURLHttps() {
        val inVal = "https://example.com"
        val out = prepareUrl(inVal)
        Assert.assertEquals(inVal, out)
    }

    @Test
    fun testMissingProtocol() {
        val inVal = "example.com"
        val out = prepareUrl(inVal)
        Assert.assertEquals("http://example.com", out)
    }

    @Test
    fun testFeedProtocol() {
        val inVal = "feed://example.com"
        val out = prepareUrl(inVal)
        Assert.assertEquals("http://example.com", out)
    }

    @Test
    fun testPcastProtocolNoScheme() {
        val inVal = "pcast://example.com"
        val out = prepareUrl(inVal)
        Assert.assertEquals("http://example.com", out)
    }

    @Test
    fun testItpcProtocol() {
        val inVal = "itpc://example.com"
        val out = prepareUrl(inVal)
        Assert.assertEquals("http://example.com", out)
    }

    @Test
    fun testItpcProtocolWithScheme() {
        val inVal = "itpc://https://example.com"
        val out = prepareUrl(inVal)
        Assert.assertEquals("https://example.com", out)
    }

    @Test
    fun testWhiteSpaceUrlShouldNotAppend() {
        val inVal = "\n http://example.com \t"
        val out = prepareUrl(inVal)
        Assert.assertEquals("http://example.com", out)
    }

    @Test
    fun testWhiteSpaceShouldAppend() {
        val inVal = "\n example.com \t"
        val out = prepareUrl(inVal)
        Assert.assertEquals("http://example.com", out)
    }

    @Test
    fun testPodciniSubscribeProtocolNoScheme() {
        val inVal = "podcini-subscribe://example.com"
        val out = prepareUrl(inVal)
        Assert.assertEquals("http://example.com", out)
    }

    @Test
    fun testPcastProtocolWithScheme() {
        val inVal = "pcast://https://example.com"
        val out = prepareUrl(inVal)
        Assert.assertEquals("https://example.com", out)
    }

    @Test
    fun testPodciniSubscribeProtocolWithScheme() {
        val inVal = "podcini-subscribe://https://example.com"
        val out = prepareUrl(inVal)
        Assert.assertEquals("https://example.com", out)
    }

    @Test
    @Throws(UnsupportedEncodingException::class)
    fun testPodciniSubscribeDeeplink() {
        val feed = "http://example.org/podcast.rss"
//        Assert.assertEquals(feed, prepareUrl("https://podcini.org/deeplink/subscribe?url=$feed"))
//        Assert.assertEquals(feed, prepareUrl("http://podcini.org/deeplink/subscribe?url=$feed"))
//        Assert.assertEquals(feed, prepareUrl("http://podcini.org/deeplink/subscribe/?url=$feed"))
//        Assert.assertEquals(feed, prepareUrl("https://www.podcini.org/deeplink/subscribe?url=$feed"))
//        Assert.assertEquals(feed, prepareUrl("http://www.podcini.org/deeplink/subscribe?url=$feed"))
//        Assert.assertEquals(feed, prepareUrl("http://www.podcini.org/deeplink/subscribe/?url=$feed"))
//        Assert.assertEquals(feed, prepareUrl("http://www.podcini.org/deeplink/subscribe?url="
//                + URLEncoder.encode(feed, "UTF-8")))
//        Assert.assertEquals(feed, prepareUrl("http://www.podcini.org/deeplink/subscribe?url="
//                + "example.org/podcast.rss"))
    }

    @Test
    fun testProtocolRelativeUrlIsAbsolute() {
        val inVal = "https://example.com"
        val inBase = "http://examplebase.com"
        val out = prepareUrl(inVal, inBase)
        Assert.assertEquals(inVal, out)
    }

    @Test
    fun testProtocolRelativeUrlIsRelativeHttps() {
        val inVal = "//example.com"
        val inBase = "https://examplebase.com"
        val out = prepareUrl(inVal, inBase)
        Assert.assertEquals("https://example.com", out)
    }

    @Test
    fun testProtocolRelativeUrlIsHttpsWithApSubscribeProtocol() {
        val inVal = "//example.com"
        val inBase = "podcini-subscribe://https://examplebase.com"
        val out = prepareUrl(inVal, inBase)
        Assert.assertEquals("https://example.com", out)
    }

    @Test
    fun testProtocolRelativeUrlBaseUrlNull() {
        val inVal = "example.com"
        val out = prepareUrl(inVal, null)
        Assert.assertEquals("http://example.com", out)
    }

    @Test
    fun testUrlEqualsSame() {
        Assert.assertTrue(urlEquals("https://www.example.com/test", "https://www.example.com/test"))
        Assert.assertTrue(urlEquals("https://www.example.com/test", "https://www.example.com/test/"))
        Assert.assertTrue(urlEquals("https://www.example.com/test", "https://www.example.com//test"))
        Assert.assertTrue(urlEquals("https://www.example.com", "https://www.example.com/"))
        Assert.assertTrue(urlEquals("https://www.example.com", "http://www.example.com"))
        Assert.assertTrue(urlEquals("http://www.example.com/", "https://www.example.com/"))
        Assert.assertTrue(urlEquals("https://www.example.com/?id=42", "https://www.example.com/?id=42"))
        Assert.assertTrue(urlEquals("https://example.com/podcast%20test", "https://example.com/podcast test"))
        Assert.assertTrue(urlEquals("https://example.com/?a=podcast%20test", "https://example.com/?a=podcast test"))
        Assert.assertTrue(urlEquals("https://example.com/?", "https://example.com/"))
        Assert.assertTrue(urlEquals("https://example.com/?", "https://example.com"))
        Assert.assertTrue(urlEquals("https://Example.com", "https://example.com"))
        Assert.assertTrue(urlEquals("https://example.com/test", "https://example.com/Test"))
    }

    @Test
    fun testUrlEqualsDifferent() {
        Assert.assertFalse(urlEquals("https://www.example.com/test", "https://www.example2.com/test"))
        Assert.assertFalse(urlEquals("https://www.example.com/test", "https://www.example.de/test"))
        Assert.assertFalse(urlEquals("https://example.com/", "https://otherpodcast.example.com/"))
        Assert.assertFalse(urlEquals("https://www.example.com/?id=42&a=b", "https://www.example.com/?id=43&a=b"))
        Assert.assertFalse(urlEquals("https://example.com/podcast%25test", "https://example.com/podcast test"))
    }
}
