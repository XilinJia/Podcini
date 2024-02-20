package ac.mdiq.podcini.core.util.syndication

import androidx.test.platform.app.InstrumentationRegistry
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/**
 * Test class for [FeedDiscoverer]
 */
@RunWith(RobolectricTestRunner::class)
class FeedDiscovererTest {
    private var fd: FeedDiscoverer? = null

    private var testDir: File? = null

    @Before
    fun setUp() {
        fd = FeedDiscoverer()
        testDir = File(InstrumentationRegistry
            .getInstrumentation().targetContext.filesDir, "FeedDiscovererTest")
        testDir!!.mkdir()
        Assert.assertTrue(testDir!!.exists())
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        FileUtils.deleteDirectory(testDir)
    }

    private fun createTestHtmlString(rel: String, type: String, href: String, title: String): String {
        return String.format("<html><head><title>Test</title><link rel=\"%s\" type=\"%s\" href=\"%s\" title=\"%s\"></head><body></body></html>",
            rel, type, href, title)
    }

    private fun createTestHtmlString(rel: String, type: String, href: String): String {
        return String.format("<html><head><title>Test</title><link rel=\"%s\" type=\"%s\" href=\"%s\"></head><body></body></html>",
            rel, type, href)
    }

    @Throws(Exception::class)
    private fun checkFindUrls(isAlternate: Boolean,
                              isRss: Boolean,
                              withTitle: Boolean,
                              isAbsolute: Boolean,
                              fromString: Boolean
    ) {
        val title = "Test title"
        val hrefAbs = "http://example.com/feed"
        val hrefRel = "/feed"
        val base = "http://example.com"

        val rel = if (isAlternate) "alternate" else "feed"
        val type = if (isRss) "application/rss+xml" else "application/atom+xml"
        val href = if (isAbsolute) hrefAbs else hrefRel

        val res: Map<String, String>
        val html = if (withTitle) createTestHtmlString(rel, type, href, title)
        else createTestHtmlString(rel, type, href)
        if (fromString) {
            res = fd!!.findLinks(html, base)
        } else {
            val testFile = File(testDir, "feed")
            val out = FileOutputStream(testFile)
            IOUtils.write(html, out, StandardCharsets.UTF_8)
            out.close()
            res = fd!!.findLinks(testFile, base)
        }

        Assert.assertNotNull(res)
        Assert.assertEquals(1, res.size.toLong())
        for (key in res.keys) {
            Assert.assertEquals(hrefAbs, key)
        }
        Assert.assertTrue(res.containsKey(hrefAbs))
        if (withTitle) {
            Assert.assertEquals(title, res[hrefAbs])
        } else {
            Assert.assertEquals(href, res[hrefAbs])
        }
    }

    @Test
    @Throws(Exception::class)
    fun testAlternateRSSWithTitleAbsolute() {
        checkFindUrls(true, true, true, true, true)
    }

    @Test
    @Throws(Exception::class)
    fun testAlternateRSSWithTitleRelative() {
        checkFindUrls(true, true, true, false, true)
    }

    @Test
    @Throws(Exception::class)
    fun testAlternateRSSNoTitleAbsolute() {
        checkFindUrls(true, true, false, true, true)
    }

    @Test
    @Throws(Exception::class)
    fun testAlternateRSSNoTitleRelative() {
        checkFindUrls(true, true, false, false, true)
    }

    @Test
    @Throws(Exception::class)
    fun testAlternateAtomWithTitleAbsolute() {
        checkFindUrls(true, false, true, true, true)
    }

    @Test
    @Throws(Exception::class)
    fun testFeedAtomWithTitleAbsolute() {
        checkFindUrls(false, false, true, true, true)
    }

    @Test
    @Throws(Exception::class)
    fun testAlternateRSSWithTitleAbsoluteFromFile() {
        checkFindUrls(true, true, true, true, false)
    }
}
