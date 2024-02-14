package ac.mdiq.podvinci.core.storage

import android.app.Application
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import ac.mdiq.podvinci.core.ApplicationCallbacks
import ac.mdiq.podvinci.core.ClientConfig
import ac.mdiq.podvinci.core.preferences.PlaybackPreferences.Companion.init
import ac.mdiq.podvinci.core.storage.DBReader.getFeed
import ac.mdiq.podvinci.core.storage.DBTasks.updateFeed
import ac.mdiq.podvinci.model.feed.Feed
import ac.mdiq.podvinci.model.feed.FeedItem
import ac.mdiq.podvinci.model.feed.FeedMedia
import ac.mdiq.podvinci.storage.database.PodDBAdapter
import ac.mdiq.podvinci.storage.database.PodDBAdapter.Companion.deleteDatabase
import ac.mdiq.podvinci.storage.database.PodDBAdapter.Companion.getInstance
import ac.mdiq.podvinci.storage.preferences.UserPreferences
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import java.util.*

/**
 * Test class for [DBTasks].
 */
@RunWith(RobolectricTestRunner::class)
class DbTasksTest {
    private var context: Context? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        UserPreferences.init(context!!)
        init(context)

        val app = context as Application?
        ClientConfig.applicationCallbacks = Mockito.mock(ApplicationCallbacks::class.java)
        Mockito.`when`(ClientConfig.applicationCallbacks?.getApplicationInstance()).thenReturn(app)

        // create new database
        PodDBAdapter.init(context!!)
        deleteDatabase()
        val adapter = getInstance()
        adapter!!.open()
        adapter.close()
    }

    @After
    fun tearDown() {
        DBWriter.tearDownTests()
        PodDBAdapter.tearDownTests()
    }

    @Test
    fun testUpdateFeedNewFeed() {
        val numItems = 10

        val feed = Feed("url", null, "title")
        feed.items = mutableListOf()
        for (i in 0 until numItems) {
            feed.items?.add(FeedItem(0, "item $i", "id $i", "link $i",
                Date(), FeedItem.UNPLAYED, feed))
        }
        val newFeed = updateFeed(context!!, feed, false)

        Assert.assertEquals(feed.id, newFeed!!.id)
        Assert.assertTrue(feed.id != 0L)
        for (item in feed.items!!) {
            Assert.assertFalse(item.isPlayed())
            Assert.assertTrue(item.id != 0L)
        }
    }

    /** Two feeds with the same title, but different download URLs should be treated as different feeds.  */
    @Test
    fun testUpdateFeedSameTitle() {
        val feed1 = Feed("url1", null, "title")
        val feed2 = Feed("url2", null, "title")

        feed1.items = mutableListOf()
        feed2.items = mutableListOf()

        val savedFeed1 = updateFeed(context!!, feed1, false)
        val savedFeed2 = updateFeed(context!!, feed2, false)

        Assert.assertTrue(savedFeed1!!.id != savedFeed2!!.id)
    }

    @Test
    fun testUpdateFeedUpdatedFeed() {
        val numItemsOld = 10
        val numItemsNew = 10

        val feed = Feed("url", null, "title")
        feed.items = mutableListOf()
        for (i in 0 until numItemsOld) {
            feed.items?.add(FeedItem(0, "item $i", "id $i", "link $i",
                Date(i.toLong()), FeedItem.PLAYED, feed))
        }
        val adapter = getInstance()
        adapter!!.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        // ensure that objects have been saved in db, then reset
        Assert.assertTrue(feed.id != 0L)
        val feedID = feed.id
        feed.id = 0
        val itemIDs: MutableList<Long> = ArrayList()
        for (item in feed.items!!) {
            Assert.assertTrue(item.id != 0L)
            itemIDs.add(item.id)
            item.id = 0
        }

        for (i in numItemsOld until numItemsNew + numItemsOld) {
            feed.items?.add(0, FeedItem(0, "item $i", "id $i", "link $i",
                Date(i.toLong()), FeedItem.UNPLAYED, feed))
        }

        val newFeed = updateFeed(context!!, feed, false)
        Assert.assertNotSame(newFeed, feed)

        updatedFeedTest(newFeed, feedID, itemIDs, numItemsOld, numItemsNew)

        val feedFromDB = getFeed(newFeed!!.id)
        Assert.assertNotNull(feedFromDB)
        Assert.assertEquals(newFeed.id, feedFromDB!!.id)
        updatedFeedTest(feedFromDB, feedID, itemIDs, numItemsOld, numItemsNew)
    }

    @Test
    fun testUpdateFeedMediaUrlResetState() {
        val feed = Feed("url", null, "title")
        val item = FeedItem(0, "item", "id", "link", Date(), FeedItem.PLAYED, feed)
        feed.items = (mutableListOf(item))

        val adapter = getInstance()
        adapter!!.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        // ensure that objects have been saved in db, then reset
        Assert.assertTrue(feed.id != 0L)
        Assert.assertTrue(item.id != 0L)

        val media = FeedMedia(item, "url", 1024, "mime/type")
        item.setMedia(media)
        val list: MutableList<FeedItem> = ArrayList()
        list.add(item)
        feed.items = (list)

        val newFeed = updateFeed(context!!, feed, false)
        Assert.assertNotSame(newFeed, feed)

        val feedFromDB = getFeed(newFeed!!.id)
        val feedItemFromDB: FeedItem = feedFromDB!!.items!![0]
        Assert.assertTrue(feedItemFromDB.isNew)
    }

    @Test
    fun testUpdateFeedRemoveUnlistedItems() {
        val feed = Feed("url", null, "title")
        feed.items = mutableListOf()
        for (i in 0..9) {
            feed.items?.add(
                FeedItem(0, "item $i", "id $i", "link $i", Date(i.toLong()), FeedItem.PLAYED, feed))
        }
        val adapter = getInstance()
        adapter!!.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        // delete some items
        feed.items?.subList(0, 2)?.clear()
        val newFeed = updateFeed(context!!, feed, true)
        assertEquals(8, newFeed?.items?.size) // 10 - 2 = 8 items

        val feedFromDB = getFeed(newFeed!!.id)
        assertEquals(8, feedFromDB?.items?.size) // 10 - 2 = 8 items
    }

    @Test
    fun testUpdateFeedSetDuplicate() {
        val feed = Feed("url", null, "title")
        feed.items = mutableListOf()
        for (i in 0..9) {
            val item =
                FeedItem(0, "item $i", "id $i", "link $i", Date(i.toLong()), FeedItem.PLAYED, feed)
            val media = FeedMedia(item, "download url $i", 123, "media/mp3")
            item.setMedia(media)
            feed.items?.add(item)
        }
        val adapter = getInstance()
        adapter!!.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        // change the guid of the first item, but leave the download url the same
        val item = feed.getItemAtIndex(0)
        item.itemIdentifier = ("id 0-duplicate")
        item.title = ("item 0 duplicate")
        val newFeed = updateFeed(context!!, feed, false)
        assertEquals(10, newFeed?.items?.size) // id 1-duplicate replaces because the stream url is the same

        val feedFromDB = getFeed(newFeed!!.id)
        assertEquals(10, feedFromDB?.items?.size) // id1-duplicate should override id 1

        val updatedItem = feedFromDB!!.getItemAtIndex(9)
        assertEquals("item 0 duplicate", updatedItem.title)
        assertEquals("id 0-duplicate", updatedItem.itemIdentifier) // Should use the new ID for sync etc
    }


    private fun updatedFeedTest(newFeed: Feed?, feedID: Long, itemIDs: List<Long>, numItemsOld: Int, numItemsNew: Int) {
        Assert.assertEquals(feedID, newFeed!!.id)
        assertEquals(numItemsNew + numItemsOld, newFeed.items?.size)
        newFeed.items?.reverse()
        var lastDate = Date(0)
        for (i in 0 until numItemsOld) {
            val item: FeedItem = newFeed.items!![i]
            Assert.assertSame(newFeed, item.feed)
            Assert.assertEquals(itemIDs[i], item.id)
            Assert.assertTrue(item.isPlayed())
            Assert.assertTrue(item.getPubDate()!!.time >= lastDate.time)
            lastDate = item.pubDate!!
        }
        for (i in numItemsOld until numItemsNew + numItemsOld) {
            val item: FeedItem = newFeed.items!![i]
            Assert.assertSame(newFeed, item.feed)
            Assert.assertTrue(item.id != 0L)
            Assert.assertFalse(item.isPlayed())
            Assert.assertTrue(item.getPubDate()!!.time >= lastDate.time)
            lastDate = item.pubDate!!
        }
    }

    private fun createSavedFeed(title: String, numFeedItems: Int): Feed {
        val feed = Feed("url", null, title)

        if (numFeedItems > 0) {
            val items: MutableList<FeedItem> = ArrayList(numFeedItems)
            for (i in 1..numFeedItems) {
                val item = FeedItem(0, "item $i of $title", "id$title$i", "link",
                    Date(), FeedItem.UNPLAYED, feed)
                items.add(item)
            }
            feed.items = (items)
        }

        val adapter = getInstance()
        adapter!!.open()
        adapter.setCompleteFeed(feed)
        adapter.close()
        return feed
    }
}
