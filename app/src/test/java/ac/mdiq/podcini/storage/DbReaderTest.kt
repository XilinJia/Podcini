package ac.mdiq.podcini.storage

import androidx.test.platform.app.InstrumentationRegistry
import ac.mdiq.podcini.storage.DBReader.getEpisodes
import ac.mdiq.podcini.storage.DBReader.getFeedItem
import ac.mdiq.podcini.storage.DBReader.getFeedItemByGuidOrEpisodeUrl
import ac.mdiq.podcini.storage.DBReader.getFeedItemList
import ac.mdiq.podcini.storage.DBReader.getFeedList
import ac.mdiq.podcini.storage.DBReader.getFeedListDownloadUrls
import ac.mdiq.podcini.storage.DBReader.getNavDrawerData
import ac.mdiq.podcini.storage.DBReader.getPlaybackHistory
import ac.mdiq.podcini.storage.DBReader.getPlaybackHistoryLength
import ac.mdiq.podcini.storage.DBReader.getQueue
import ac.mdiq.podcini.storage.DBReader.getQueueIDList
import ac.mdiq.podcini.storage.DBReader.loadAdditionalFeedItemListData
import ac.mdiq.podcini.storage.DBReader.loadChaptersOfFeedItem
import ac.mdiq.podcini.storage.model.feed.*
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.DBWriter
import ac.mdiq.podcini.storage.database.PodDBAdapter
import ac.mdiq.podcini.storage.database.PodDBAdapter.Companion.deleteDatabase
import ac.mdiq.podcini.storage.database.PodDBAdapter.Companion.getInstance
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.preferences.UserPreferences.subscriptionsFilter
import junit.framework.TestCase.assertEquals
import org.junit.*
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Test class for DBReader.
 */
@RunWith(Enclosed::class)
class DbReaderTest {
    @Ignore("Not a test")
    open class TestBase {
        @Before
        fun setUp() {
            val context = InstrumentationRegistry.getInstrumentation().context
            UserPreferences.init(context)

            PodDBAdapter.init(context)
            deleteDatabase()
            val adapter = getInstance()
            adapter.open()
            adapter.close()
        }

        @After
        fun tearDown() {
            PodDBAdapter.tearDownTests()
            DBWriter.tearDownTests()
        }
    }

    @RunWith(RobolectricTestRunner::class)
    class SingleTests : TestBase() {
        @Test
        fun testGetFeedList() {
            val feeds = DbTestUtils.saveFeedlist(10, 0, false)
            val savedFeeds = getFeedList()
            Assert.assertNotNull(savedFeeds)
            Assert.assertEquals(feeds.size.toLong(), savedFeeds.size.toLong())
            for (i in feeds.indices) {
                Assert.assertEquals(feeds[i].id, savedFeeds[i].id)
            }
        }

        @Test
        fun testGetFeedListSortOrder() {
            val adapter = getInstance()
            adapter.open()

            val feed1 = Feed(0, null, "A", "link", "d", null, null, null, "rss", "A", null, "", "", true)
            val feed2 = Feed(0, null, "b", "link", "d", null, null, null, "rss", "b", null, "", "", true)
            val feed3 = Feed(0, null, "C", "link", "d", null, null, null, "rss", "C", null, "", "", true)
            val feed4 = Feed(0, null, "d", "link", "d", null, null, null, "rss", "d", null, "", "", true)
            adapter.setCompleteFeed(feed1)
            adapter.setCompleteFeed(feed2)
            adapter.setCompleteFeed(feed3)
            adapter.setCompleteFeed(feed4)
            Assert.assertTrue(feed1.id != 0L)
            Assert.assertTrue(feed2.id != 0L)
            Assert.assertTrue(feed3.id != 0L)
            Assert.assertTrue(feed4.id != 0L)

            adapter.close()

            val saved = getFeedList()
            Assert.assertNotNull(saved)
            Assert.assertEquals("Wrong size: ", 4, saved.size.toLong())

            Assert.assertEquals("Wrong id of feed 1: ", feed1.id, saved[0].id)
            Assert.assertEquals("Wrong id of feed 2: ", feed2.id, saved[1].id)
            Assert.assertEquals("Wrong id of feed 3: ", feed3.id, saved[2].id)
            Assert.assertEquals("Wrong id of feed 4: ", feed4.id, saved[3].id)
        }

        @Test
        fun testFeedListDownloadUrls() {
            val feeds = DbTestUtils.saveFeedlist(10, 0, false)
            val urls = getFeedListDownloadUrls()
            Assert.assertNotNull(urls)
            Assert.assertEquals(feeds.size.toLong(), urls.size.toLong())
            for (i in urls.indices) {
                assertEquals(urls[i], feeds[i].download_url)
            }
        }

        @Test
        fun testLoadFeedDataOfFeedItemlist() {
            val numFeeds = 10
            val numItems = 1
            val feeds = DbTestUtils.saveFeedlist(numFeeds, numItems, false)
            val items: MutableList<FeedItem> = mutableListOf()
            for (f in feeds) {
                for (item in f.items) {
                    item.feed = (null)
                    item.feedId = (f.id)
                    items.add(item)
                }
            }
            loadAdditionalFeedItemListData(items)
            for (i in 0 until numFeeds) {
                for (j in 0 until numItems) {
                    val item: FeedItem = feeds[i].items[j]
                    Assert.assertNotNull(item.feed)
                    assertEquals(feeds[i].id, item.feed!!.id)
                    assertEquals(item.feed!!.id, item.feedId)
                }
            }
        }

        @Test
        fun testGetFeedItemList() {
            val numFeeds = 1
            val numItems = 10
            val feed = DbTestUtils.saveFeedlist(numFeeds, numItems, false)[0]
            val items: List<FeedItem> = feed.items.toList()
            feed.items = mutableListOf()
            val savedItems = getFeedItemList(feed)
            Assert.assertNotNull(savedItems)
            Assert.assertEquals(items.size.toLong(), savedItems.size.toLong())
            for (i in savedItems.indices) {
                Assert.assertEquals(savedItems[i].id, items[i].id)
            }
        }

        private fun saveQueue(numItems: Int): List<FeedItem> {
            require(numItems > 0) { "numItems<=0" }
            val feeds = DbTestUtils.saveFeedlist(numItems, numItems, false)
            val allItems: MutableList<FeedItem> = ArrayList()
            for (f in feeds) {
                allItems.addAll(f.items)
            }
            // take random items from every feed
            val random = Random()
            val queue: MutableList<FeedItem> = ArrayList()
            while (queue.size < numItems) {
                val index = random.nextInt(numItems)
                if (!queue.contains(allItems[index])) {
                    queue.add(allItems[index])
                }
            }
            val adapter = getInstance()
            adapter.open()
            adapter.setQueue(queue)
            adapter.close()
            return queue
        }

        @Test
        fun testGetQueueIdList() {
            val numItems = 10
            val queue = saveQueue(numItems)
            val ids = getQueueIDList()
            Assert.assertNotNull(ids)
            Assert.assertEquals(ids.size().toLong(), queue.size.toLong())
            for (i in queue.indices) {
                Assert.assertTrue(ids.get(i) != 0L)
                Assert.assertEquals(ids.get(i), queue[i].id)
            }
        }

        @Test
        fun testGetQueue() {
            val numItems = 10
            val queue = saveQueue(numItems)
            val savedQueue = getQueue()
            Assert.assertNotNull(savedQueue)
            Assert.assertEquals(savedQueue.size.toLong(), queue.size.toLong())
            for (i in queue.indices) {
                Assert.assertTrue(savedQueue[i].id != 0L)
                Assert.assertEquals(savedQueue[i].id, queue[i].id)
            }
        }

        private fun saveDownloadedItems(numItems: Int): List<FeedItem> {
            require(numItems > 0) { "numItems<=0" }
            val feeds = DbTestUtils.saveFeedlist(numItems, numItems, true)
            val items: MutableList<FeedItem> = ArrayList()
            for (f in feeds) {
                items.addAll(f.items)
            }
            val downloaded: MutableList<FeedItem> = ArrayList()
            val random = Random()

            while (downloaded.size < numItems) {
                val i = random.nextInt(numItems)
                if (!downloaded.contains(items[i])) {
                    val item = items[i]
                    item.media!!.setDownloaded(true)
                    item.media!!.setFile_url("file$i")
                    downloaded.add(item)
                }
            }
            val adapter = getInstance()
            adapter.open()
            adapter.storeFeedItemlist(downloaded)
            adapter.close()
            return downloaded
        }

        @Test
        fun testGetDownloadedItems() {
            val numItems = 10
            val downloaded = saveDownloadedItems(numItems)
            val downloadedSaved = getEpisodes(0, Int.MAX_VALUE,
                FeedItemFilter(FeedItemFilter.DOWNLOADED), SortOrder.DATE_NEW_OLD)
            Assert.assertNotNull(downloadedSaved)
            Assert.assertEquals(downloaded.size.toLong(), downloadedSaved.size.toLong())
            for (item in downloadedSaved) {
                Assert.assertNotNull(item.media)
                Assert.assertTrue(item.media!!.isDownloaded())
                Assert.assertNotNull(item.media!!.download_url)
            }
        }

        private fun saveNewItems(numItems: Int): List<FeedItem> {
            val feeds = DbTestUtils.saveFeedlist(numItems, numItems, true)
            val items: MutableList<FeedItem> = ArrayList()
            for (f in feeds) {
                items.addAll(f.items)
            }
            val newItems: MutableList<FeedItem> = ArrayList()
            val random = Random()

            while (newItems.size < numItems) {
                val i = random.nextInt(numItems)
                if (!newItems.contains(items[i])) {
                    val item = items[i]
                    item.setNew()
                    newItems.add(item)
                }
            }
            val adapter = getInstance()
            adapter.open()
            adapter.storeFeedItemlist(newItems)
            adapter.close()
            return newItems
        }

        @Test
        fun testGetNewItemIds() {
            val numItems = 10

            val newItems = saveNewItems(numItems)
            val unreadIds = LongArray(newItems.size)
            for (i in newItems.indices) {
                unreadIds[i] = newItems[i].id
            }
            val newItemsSaved = getEpisodes(0, Int.MAX_VALUE,
                FeedItemFilter(FeedItemFilter.NEW), SortOrder.DATE_NEW_OLD)
            Assert.assertNotNull(newItemsSaved)
            Assert.assertEquals(newItemsSaved.size.toLong(), newItems.size.toLong())
            for (feedItem in newItemsSaved) {
                val savedId = feedItem.id
                var found = false
                for (id in unreadIds) {
                    if (id == savedId) {
                        found = true
                        break
                    }
                }
                Assert.assertTrue(found)
            }
        }

        @Test
        fun testGetPlaybackHistoryLength() {
            val totalItems = 100

            val feed = DbTestUtils.saveFeedlist(1, totalItems, true)[0]

            val adapter = getInstance()
            for (playedItems in mutableListOf(0, 1, 20, 100)) {
                adapter.open()
                for (i in 0 until playedItems) {
                    val m: FeedMedia = feed.items[i].media!!
                    m.setPlaybackCompletionDate(Date((i + 1).toLong()))

                    adapter.setFeedMediaPlaybackCompletionDate(m)
                }
                adapter.close()

                val len = getPlaybackHistoryLength()
                Assert.assertEquals("Wrong size: ", len.toInt().toLong(), playedItems.toLong())
            }
        }

        @Test
        fun testGetNavDrawerDataQueueEmptyNoUnreadItems() {
            val numFeeds = 10
            val numItems = 10
            DbTestUtils.saveFeedlist(numFeeds, numItems, true)
            val navDrawerData = getNavDrawerData(subscriptionsFilter)
            Assert.assertEquals(numFeeds.toLong(), navDrawerData.items.size.toLong())
            Assert.assertEquals(0, navDrawerData.numNewItems.toLong())
            Assert.assertEquals(0, navDrawerData.queueSize.toLong())
        }

        @Test
        fun testGetNavDrawerDataQueueNotEmptyWithUnreadItems() {
            val numFeeds = 10
            val numItems = 10
            val numQueue = 1
            val numNew = 2
            val feeds = DbTestUtils.saveFeedlist(numFeeds, numItems, true)
            val adapter = getInstance()
            adapter.open()
            for (i in 0 until numNew) {
                val item: FeedItem = feeds[0].items[i]
                item.setNew()
                adapter.setSingleFeedItem(item)
            }
            val queue: MutableList<FeedItem> = ArrayList()
            for (i in 0 until numQueue) {
                val item: FeedItem = feeds[1].items[i]
                queue.add(item)
            }
            adapter.setQueue(queue)

            adapter.close()

            val navDrawerData = getNavDrawerData(subscriptionsFilter)
            Assert.assertEquals(numFeeds.toLong(), navDrawerData.items.size.toLong())
            Assert.assertEquals(numNew.toLong(), navDrawerData.numNewItems.toLong())
            Assert.assertEquals(numQueue.toLong(), navDrawerData.queueSize.toLong())
        }

        @Test
        fun testGetFeedItemlistCheckChaptersFalse() {
            val feeds = DbTestUtils.saveFeedlist(10, 10, false, false, 0)
            for (feed in feeds) {
                for (item in feed.items) {
                    Assert.assertFalse(item.hasChapters())
                }
            }
        }

        @Test
        fun testGetFeedItemlistCheckChaptersTrue() {
            val feeds = DbTestUtils.saveFeedlist(10, 10, false, true, 10)
            for (feed in feeds) {
                for (item in feed.items) {
                    Assert.assertTrue(item.hasChapters())
                }
            }
        }

        @Test
        fun testLoadChaptersOfFeedItemNoChapters() {
            val feeds = DbTestUtils.saveFeedlist(1, 3, false, false, 0)
            DbTestUtils.saveFeedlist(1, 3, false, true, 3)
            for (feed in feeds) {
                for (item in feed.items) {
                    Assert.assertFalse(item.hasChapters())
                    item.chapters = loadChaptersOfFeedItem(item)?.toMutableList()
                    Assert.assertFalse(item.hasChapters())
                    Assert.assertNull(item.chapters)
                }
            }
        }

        @Test
        fun testLoadChaptersOfFeedItemWithChapters() {
            val numChapters = 3
            DbTestUtils.saveFeedlist(1, 3, false, false, 0)
            val feeds = DbTestUtils.saveFeedlist(1, 3, false, true, numChapters)
            for (feed in feeds) {
                for (item in feed.items) {
                    Assert.assertTrue(item.hasChapters())
                    item.chapters = loadChaptersOfFeedItem(item)?.toMutableList()
                    Assert.assertTrue(item.hasChapters())
                    Assert.assertNotNull(item.chapters)
                    assertEquals(numChapters, item.chapters!!.size)
                }
            }
        }

        @Test
        fun testGetItemWithChapters() {
            val numChapters = 3
            val feeds = DbTestUtils.saveFeedlist(1, 1, false, true, numChapters)
            val item1: FeedItem = feeds[0].items[0]
            val item2 = getFeedItem(item1.id)
            item2!!.chapters = loadChaptersOfFeedItem(item2)?.toMutableList()
            Assert.assertTrue(item2.hasChapters())
            assertEquals(item1.chapters, item2.chapters)
        }

        @Test
        fun testGetItemByEpisodeUrl() {
            val feeds = DbTestUtils.saveFeedlist(1, 1, true)
            val item1: FeedItem = feeds[0].items[0]
            val feedItemByEpisodeUrl = getFeedItemByGuidOrEpisodeUrl(null, item1.media!!.download_url!!)
            assertEquals(item1.itemIdentifier, feedItemByEpisodeUrl?.itemIdentifier)
        }

        @Test
        fun testGetItemByGuid() {
            val feeds = DbTestUtils.saveFeedlist(1, 1, true)
            val item1: FeedItem = feeds[0].items[0]

            val feedItemByGuid = getFeedItemByGuidOrEpisodeUrl(item1.itemIdentifier, item1.media?.download_url!!)
            assertEquals(item1.itemIdentifier, feedItemByGuid?.itemIdentifier)
        }
    }

    @RunWith(ParameterizedRobolectricTestRunner::class)
    class PlaybackHistoryTest(private val paramOffset: Int, private val paramLimit: Int) : TestBase() {
        @Test
        fun testGetPlaybackHistory() {
            val numItems = (paramLimit + 1) * 2
            val playedItems = paramLimit + 1
            val numReturnedItems = min(max((playedItems - paramOffset).toDouble(), 0.0), paramLimit.toDouble())
                .toInt()
            val numFeeds = 1

            val feed = DbTestUtils.saveFeedlist(numFeeds, numItems, true)[0]
            val ids = LongArray(playedItems)

            val adapter = getInstance()
            adapter.open()
            for (i in 0 until playedItems) {
                val m: FeedMedia = feed.items[i].media!!
                m.setPlaybackCompletionDate(Date((i + 1).toLong()))
                adapter.setFeedMediaPlaybackCompletionDate(m)
                ids[ids.size - 1 - i] = m.item!!.id
            }
            adapter.close()

            val saved = getPlaybackHistory(paramOffset, paramLimit)
            Assert.assertNotNull(saved)
            Assert.assertEquals(String.format("Wrong size with offset %d and limit %d: ",
                paramOffset, paramLimit),
                numReturnedItems.toLong(), saved.size.toLong())
            for (i in 0 until numReturnedItems) {
                val item = saved[i]
                Assert.assertNotNull(item.media!!.getPlaybackCompletionDate())
                Assert.assertEquals(String.format("Wrong sort order with offset %d and limit %d: ",
                    paramOffset, paramLimit),
                    item.id, ids[paramOffset + i])
            }
        }

        companion object {
            @ParameterizedRobolectricTestRunner.Parameters
            fun data(): Collection<Array<Any>> {
                val limits: List<Int> = mutableListOf(1, 20, 100)
                val offsets: List<Int> = mutableListOf(0, 10, 20)
                val rv = Array(limits.size * offsets.size) { arrayOf<Any>(2) }
                var i = 0
                for (offset in offsets) {
                    for (limit in limits) {
                        rv[i][0] = offset
                        rv[i][1] = limit
                        i++
                    }
                }

                return listOf(*rv)
            }
        }
    }
}
