package de.danoeh.antennapod.core.storage.mapper

import android.content.ContentValues
import androidx.test.platform.app.InstrumentationRegistry
import de.danoeh.antennapod.storage.database.PodDBAdapter
import de.danoeh.antennapod.storage.database.PodDBAdapter.Companion.getInstance
import de.danoeh.antennapod.storage.database.PodDBAdapter.Companion.init
import de.danoeh.antennapod.storage.database.PodDBAdapter.Companion.tearDownTests
import de.danoeh.antennapod.storage.database.mapper.FeedCursorMapper.convert
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeedCursorMapperTest {
    private var adapter: PodDBAdapter? = null

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().context

        init(context)
        adapter = getInstance()

        writeFeedToDatabase()
    }

    @After
    fun tearDown() {
        tearDownTests()
    }

    @Test
    fun testFromCursor() {
        adapter!!.allFeedsCursor.use { cursor ->
            cursor.moveToNext()
            val feed = convert(cursor)
            Assert.assertTrue(feed.id >= 0)
            Assert.assertEquals("feed custom title", feed.title)
            Assert.assertEquals("feed custom title", feed.getCustomTitle())
            assertEquals("feed link", feed.link)
            assertEquals("feed description", feed.description)
            Assert.assertEquals("feed payment link", feed.paymentLinks!![0].url)
            assertEquals("feed author", feed.author)
            assertEquals("feed language", feed.language)
            assertEquals("feed image url", feed.imageUrl)
            Assert.assertEquals("feed file url", feed.getFile_url())
            assertEquals("feed download url", feed.download_url)
            Assert.assertTrue(feed.isDownloaded())
            assertEquals("feed last update", feed.lastUpdate)
            assertEquals("feed type", feed.type)
            assertEquals("feed identifier", feed.feedIdentifier)
            Assert.assertTrue(feed.isPaged)
            assertEquals("feed next page link", feed.nextPageLink)
            Assert.assertTrue(feed.itemFilter!!.showUnplayed)
            Assert.assertEquals(1, feed.sortOrder!!.code.toLong())
            Assert.assertTrue(feed.hasLastUpdateFailed())
        }
    }

    /**
     * Insert test data to the database.
     * Uses raw database insert instead of adapter.setCompleteFeed() to avoid testing the Feed class
     * against itself.
     */
    private fun writeFeedToDatabase() {
        val values = ContentValues()
        values.put(PodDBAdapter.KEY_TITLE, "feed title")
        values.put(PodDBAdapter.KEY_CUSTOM_TITLE, "feed custom title")
        values.put(PodDBAdapter.KEY_LINK, "feed link")
        values.put(PodDBAdapter.KEY_DESCRIPTION, "feed description")
        values.put(PodDBAdapter.KEY_PAYMENT_LINK, "feed payment link")
        values.put(PodDBAdapter.KEY_AUTHOR, "feed author")
        values.put(PodDBAdapter.KEY_LANGUAGE, "feed language")
        values.put(PodDBAdapter.KEY_IMAGE_URL, "feed image url")

        values.put(PodDBAdapter.KEY_FILE_URL, "feed file url")
        values.put(PodDBAdapter.KEY_DOWNLOAD_URL, "feed download url")
        values.put(PodDBAdapter.KEY_DOWNLOADED, true)
        values.put(PodDBAdapter.KEY_LASTUPDATE, "feed last update")
        values.put(PodDBAdapter.KEY_TYPE, "feed type")
        values.put(PodDBAdapter.KEY_FEED_IDENTIFIER, "feed identifier")

        values.put(PodDBAdapter.KEY_IS_PAGED, true)
        values.put(PodDBAdapter.KEY_NEXT_PAGE_LINK, "feed next page link")
        values.put(PodDBAdapter.KEY_HIDE, "unplayed")
        values.put(PodDBAdapter.KEY_SORT_ORDER, "1")
        values.put(PodDBAdapter.KEY_LAST_UPDATE_FAILED, true)

        adapter!!.insertTestData(PodDBAdapter.TABLE_NAME_FEEDS, values)
    }
}
