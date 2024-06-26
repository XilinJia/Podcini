package de.test.podcini.ui

import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import ac.mdiq.podcini.storage.model.Feed
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Test for the UITestUtils. Makes sure that all URLs are reachable and that the class does not cause any crashes.
 */
@MediumTest
class UITestUtilsTest {
    private var uiTestUtils: UITestUtils? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        uiTestUtils = UITestUtils(InstrumentationRegistry.getInstrumentation().targetContext)
        uiTestUtils!!.setup()
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        uiTestUtils!!.tearDown()
    }

    @Test
    @Throws(Exception::class)
    fun testAddHostedFeeds() {
        uiTestUtils!!.addHostedFeedData()
        val feeds: List<Feed> = uiTestUtils!!.hostedFeeds
        Assert.assertNotNull(feeds)
        Assert.assertFalse(feeds.isEmpty())

        for (feed in feeds) {
            testUrlReachable(feed.downloadUrl)
            for (item in feed.episodes) {
                if (item.media != null) {
                    testUrlReachable(item.media!!.downloadUrl)
                }
            }
        }
    }

    @Throws(Exception::class)
    fun testUrlReachable(strUtl: String?) {
        val url = URL(strUtl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connect()
        val rc = conn.responseCode
        Assert.assertEquals(HttpURLConnection.HTTP_OK.toLong(), rc.toLong())
        conn.disconnect()
    }

    @Throws(Exception::class)
    private fun addLocalFeedDataCheck(downloadEpisodes: Boolean) {
        uiTestUtils!!.addLocalFeedData(downloadEpisodes)
        Assert.assertNotNull(uiTestUtils!!.hostedFeeds)
        Assert.assertFalse(uiTestUtils!!.hostedFeeds.isEmpty())

        for (feed in uiTestUtils!!.hostedFeeds) {
            Assert.assertTrue(feed.id != 0L)
            for (item in feed.episodes) {
                Assert.assertTrue(item.id != 0L)
                if (item.media != null) {
                    Assert.assertTrue(item.media!!.id != 0L)
                    if (downloadEpisodes) {
                        Assert.assertTrue(item.media!!.downloaded)
                        Assert.assertNotNull(item.media!!.fileUrl)
                        val file = File(item.media!!.fileUrl!!)
                        Assert.assertTrue(file.exists())
                    }
                }
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testAddLocalFeedDataNoDownload() {
        addLocalFeedDataCheck(false)
    }

    @Test
    @Throws(Exception::class)
    fun testAddLocalFeedDataDownload() {
        addLocalFeedDataCheck(true)
    }
}
