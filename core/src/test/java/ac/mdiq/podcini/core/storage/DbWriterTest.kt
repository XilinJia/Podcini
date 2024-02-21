package ac.mdiq.podcini.core.storage

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.util.Consumer
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import ac.mdiq.podcini.core.ApplicationCallbacks
import ac.mdiq.podcini.core.ClientConfig
import ac.mdiq.podcini.core.preferences.PlaybackPreferences.Companion.init
import ac.mdiq.podcini.core.storage.DBReader.getFeedItem
import ac.mdiq.podcini.core.storage.DBReader.getFeedItemList
import ac.mdiq.podcini.core.storage.DBReader.getFeedMedia
import ac.mdiq.podcini.core.storage.DBReader.getQueue
import ac.mdiq.podcini.core.storage.DBWriter.addItemToPlaybackHistory
import ac.mdiq.podcini.core.storage.DBWriter.addQueueItem
import ac.mdiq.podcini.core.storage.DBWriter.clearQueue
import ac.mdiq.podcini.core.storage.DBWriter.deleteFeed
import ac.mdiq.podcini.core.storage.DBWriter.deleteFeedItems
import ac.mdiq.podcini.core.storage.DBWriter.deleteFeedMediaOfItem
import ac.mdiq.podcini.core.storage.DBWriter.moveQueueItem
import ac.mdiq.podcini.core.storage.DBWriter.removeAllNewFlags
import ac.mdiq.podcini.core.storage.DBWriter.removeQueueItem
import ac.mdiq.podcini.core.storage.DBWriter.setFeedItem
import ac.mdiq.podcini.core.storage.DBWriter.setFeedMediaPlaybackInformation
import ac.mdiq.podcini.core.util.FeedItemUtil.getIdList
import ac.mdiq.podcini.model.feed.Feed
import ac.mdiq.podcini.model.feed.FeedItem
import ac.mdiq.podcini.model.feed.FeedMedia
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterface
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterfaceStub
import ac.mdiq.podcini.storage.database.PodDBAdapter
import ac.mdiq.podcini.storage.database.PodDBAdapter.Companion.deleteDatabase
import ac.mdiq.podcini.storage.database.PodDBAdapter.Companion.getInstance
import ac.mdiq.podcini.storage.preferences.UserPreferences
import ac.mdiq.podcini.storage.preferences.UserPreferences.enqueueLocation
import ac.mdiq.podcini.storage.preferences.UserPreferences.shouldDeleteRemoveFromQueue
import org.awaitility.Awaitility
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Test class for [DBWriter].
 */
@RunWith(RobolectricTestRunner::class)
class DbWriterTest {
    private var context: Context? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        UserPreferences.init(context!!)
        init(context)

        val app = context as Application?
        ClientConfig.applicationCallbacks = Mockito.mock(ApplicationCallbacks::class.java)
        Mockito.`when`(ClientConfig.applicationCallbacks?.getApplicationInstance()).thenReturn(app)
        DownloadServiceInterface.setImpl(DownloadServiceInterfaceStub())

        // create new database
        PodDBAdapter.init(context!!)
        deleteDatabase()
        val adapter = getInstance()
        adapter.open()
        adapter.close()

        val prefEdit = PreferenceManager.getDefaultSharedPreferences(
            context!!.applicationContext).edit()
        prefEdit.putBoolean(UserPreferences.PREF_DELETE_REMOVES_FROM_QUEUE, true).commit()
    }

    @After
    fun tearDown() {
        PodDBAdapter.tearDownTests()
        DBWriter.tearDownTests()

        val testDir = context!!.getExternalFilesDir(TEST_FOLDER)
        Assert.assertNotNull(testDir)
        for (f in testDir!!.listFiles()) {
            f.delete()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testSetFeedMediaPlaybackInformation() {
        val position = 50
        val lastPlayedTime: Long = 1000
        val playedDuration = 60
        val duration = 100

        val feed = Feed("url", null, "title")
        val items: MutableList<FeedItem> = ArrayList()
        feed.items = (items)
        val item = FeedItem(0, "Item", "Item", "url", Date(), FeedItem.PLAYED, feed)
        items.add(item)
        val media = FeedMedia(0, item, duration, 1, 1, "mime_type",
            "dummy path", "download_url", true, null, 0, 0)
        item.setMedia(media)

        setFeedItem(item)[TIMEOUT, TimeUnit.SECONDS]

        media.setPosition(position)
        media.setLastPlayedTime(lastPlayedTime)
        media.playedDuration = playedDuration

        setFeedMediaPlaybackInformation(item.media)[TIMEOUT, TimeUnit.SECONDS]

        val itemFromDb = getFeedItem(item.id)
        val mediaFromDb = itemFromDb!!.media

        Assert.assertEquals(position.toLong(), mediaFromDb!!.getPosition().toLong())
        Assert.assertEquals(lastPlayedTime, mediaFromDb.getLastPlayedTime())
        Assert.assertEquals(playedDuration.toLong(), mediaFromDb.playedDuration.toLong())
        Assert.assertEquals(duration.toLong(), mediaFromDb.getDuration().toLong())
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeedMediaOfItemFileExists() {
        val dest = File(context!!.getExternalFilesDir(TEST_FOLDER), "testFile")

        Assert.assertTrue(dest.createNewFile())

        val feed = Feed("url", null, "title")
        val items: MutableList<FeedItem> = ArrayList()
        feed.items = (items)
        val item = FeedItem(0, "Item", "Item", "url", Date(), FeedItem.PLAYED, feed)

        var media: FeedMedia? = FeedMedia(0, item, 1, 1, 1, "mime_type",
            dest.absolutePath, "download_url", true, null, 0, 0)
        item.setMedia(media)

        items.add(item)

        val adapter = getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()
        Assert.assertTrue(media!!.id != 0L)
        Assert.assertTrue(item.id != 0L)

        deleteFeedMediaOfItem(context!!, media.id)[TIMEOUT, TimeUnit.SECONDS]
        media = getFeedMedia(media.id)
        Assert.assertNotNull(media)
        Assert.assertFalse(dest.exists())
        Assert.assertFalse(media!!.isDownloaded())
        Assert.assertNull(media.getFile_url())
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeedMediaOfItemRemoveFromQueue() {
        Assert.assertTrue(shouldDeleteRemoveFromQueue())

        val dest = File(context!!.getExternalFilesDir(TEST_FOLDER), "testFile")

        Assert.assertTrue(dest.createNewFile())

        val feed = Feed("url", null, "title")
        val items: MutableList<FeedItem> = ArrayList()
        feed.items = (items)
        val item = FeedItem(0, "Item", "Item", "url", Date(), FeedItem.UNPLAYED, feed)

        var media: FeedMedia? = FeedMedia(0, item, 1, 1, 1, "mime_type",
            dest.absolutePath, "download_url", true, null, 0, 0)
        item.setMedia(media)

        items.add(item)
        var queue: MutableList<FeedItem> = mutableListOf()
        queue.add(item)

        val adapter = getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.setQueue(queue)
        adapter.close()
        Assert.assertTrue(media!!.id != 0L)
        Assert.assertTrue(item.id != 0L)
        queue = getQueue().toMutableList()
        Assert.assertTrue(queue.size != 0)

        deleteFeedMediaOfItem(context!!, media.id)
        Awaitility.await().timeout(2, TimeUnit.SECONDS).until { !dest.exists() }
        media = getFeedMedia(media.id)
        Assert.assertNotNull(media)
        Assert.assertFalse(dest.exists())
        Assert.assertFalse(media!!.isDownloaded())
        Assert.assertNull(media.getFile_url())
        Awaitility.await().timeout(2, TimeUnit.SECONDS).until { getQueue().isEmpty() }
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeed() {
        val destFolder = context!!.getExternalFilesDir(TEST_FOLDER)
        Assert.assertNotNull(destFolder)

        val feed = Feed("url", null, "title")
        feed.items = mutableListOf()

        val itemFiles: MutableList<File> = ArrayList()
        // create items with downloaded media files
        for (i in 0..9) {
            val item = FeedItem(0, "Item $i", "Item$i", "url", Date(), FeedItem.PLAYED, feed)
            feed.items.add(item)

            val enc = File(destFolder, "file $i")
            Assert.assertTrue(enc.createNewFile())

            itemFiles.add(enc)
            val media = FeedMedia(0, item, 1, 1, 1, "mime_type",
                enc.absolutePath, "download_url", true, null, 0, 0)
            item.setMedia(media)
        }

        var adapter = getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        Assert.assertTrue(feed.id != 0L)
        for (item in feed.items) {
            Assert.assertTrue(item.id != 0L)
            Assert.assertTrue(item.media!!.id != 0L)
        }

        deleteFeed(context!!, feed.id)[TIMEOUT, TimeUnit.SECONDS]

        // check if files still exist
        for (f in itemFiles) {
            Assert.assertFalse(f.exists())
        }

        adapter = getInstance()
        adapter.open()
        var c = adapter.getFeedCursor(feed.id)
        Assert.assertEquals(0, c.count.toLong())
        c.close()
        for (item in feed.items) {
            c = adapter.getFeedItemCursor(item.id.toString())
            Assert.assertEquals(0, c.count.toLong())
            c.close()
            c = adapter.getSingleFeedMediaCursor(item.media!!.id)
            Assert.assertEquals(0, c.count.toLong())
            c.close()
        }
        adapter.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeedNoItems() {
        val destFolder = context!!.getExternalFilesDir(TEST_FOLDER)
        Assert.assertNotNull(destFolder)

        val feed = Feed("url", null, "title")
        feed.items = mutableListOf()
        feed.imageUrl = ("url")

        var adapter = getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        Assert.assertTrue(feed.id != 0L)

        deleteFeed(context!!, feed.id)[TIMEOUT, TimeUnit.SECONDS]

        adapter = getInstance()
        adapter.open()
        val c = adapter.getFeedCursor(feed.id)
        Assert.assertEquals(0, c.count.toLong())
        c.close()
        adapter.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeedNoFeedMedia() {
        val destFolder = context!!.getExternalFilesDir(TEST_FOLDER)
        Assert.assertNotNull(destFolder)

        val feed = Feed("url", null, "title")
        feed.items = mutableListOf()

        feed.imageUrl = ("url")

        // create items
        for (i in 0..9) {
            val item = FeedItem(0, "Item $i", "Item$i", "url", Date(), FeedItem.PLAYED, feed)
            feed.items.add(item)
        }

        var adapter = getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        Assert.assertTrue(feed.id != 0L)
        for (item in feed.items) {
            Assert.assertTrue(item.id != 0L)
        }

        deleteFeed(context!!, feed.id)[TIMEOUT, TimeUnit.SECONDS]

        adapter = getInstance()
        adapter.open()
        var c = adapter.getFeedCursor(feed.id)
        Assert.assertEquals(0, c.count.toLong())
        c.close()
        for (item in feed.items) {
            c = adapter.getFeedItemCursor(item.id.toString())
            Assert.assertEquals(0, c.count.toLong())
            c.close()
        }
        adapter.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeedWithQueueItems() {
        val destFolder = context!!.getExternalFilesDir(TEST_FOLDER)
        Assert.assertNotNull(destFolder)

        val feed = Feed("url", null, "title")
        feed.items = mutableListOf()

        feed.imageUrl = ("url")

        // create items with downloaded media files
        for (i in 0..9) {
            val item = FeedItem(0, "Item $i", "Item$i", "url", Date(), FeedItem.PLAYED, feed)
            feed.items.add(item)
            val enc = File(destFolder, "file $i")
            val media = FeedMedia(0, item, 1, 1, 1, "mime_type",
                enc.absolutePath, "download_url", false, null, 0, 0)
            item.setMedia(media)
        }

        val adapter = getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        Assert.assertTrue(feed.id != 0L)
        for (item in feed.items) {
            Assert.assertTrue(item.id != 0L)
            Assert.assertTrue(item.media!!.id != 0L)
        }

        val queue: List<FeedItem> = feed.items.toList()
        adapter.open()
        adapter.setQueue(queue)

        val queueCursor = adapter.queueIDCursor
        Assert.assertEquals(queue.size.toLong(), queueCursor.count.toLong())
        queueCursor.close()

        adapter.close()
        deleteFeed(context!!, feed.id)[TIMEOUT, TimeUnit.SECONDS]
        adapter.open()

        var c = adapter.getFeedCursor(feed.id)
        Assert.assertEquals(0, c.count.toLong())
        c.close()
        for (item in feed.items) {
            c = adapter.getFeedItemCursor(item.id.toString())
            Assert.assertEquals(0, c.count.toLong())
            c.close()
            c = adapter.getSingleFeedMediaCursor(item.media!!.id)
            Assert.assertEquals(0, c.count.toLong())
            c.close()
        }
        c = adapter.queueCursor
        Assert.assertEquals(0, c.count.toLong())
        c.close()
        adapter.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeedNoDownloadedFiles() {
        val destFolder = context!!.getExternalFilesDir(TEST_FOLDER)
        Assert.assertNotNull(destFolder)

        val feed = Feed("url", null, "title")
        feed.items = mutableListOf()

        feed.imageUrl = ("url")

        // create items with downloaded media files
        for (i in 0..9) {
            val item = FeedItem(0, "Item $i", "Item$i", "url", Date(), FeedItem.PLAYED, feed)
            feed.items.add(item)
            val enc = File(destFolder, "file $i")
            val media = FeedMedia(0, item, 1, 1, 1, "mime_type",
                enc.absolutePath, "download_url", false, null, 0, 0)
            item.setMedia(media)
        }

        var adapter = getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        Assert.assertTrue(feed.id != 0L)
        for (item in feed.items) {
            Assert.assertTrue(item.id != 0L)
            Assert.assertTrue(item.media!!.id != 0L)
        }

        deleteFeed(context!!, feed.id)[TIMEOUT, TimeUnit.SECONDS]

        adapter = getInstance()
        adapter.open()
        var c = adapter.getFeedCursor(feed.id)
        Assert.assertEquals(0, c.count.toLong())
        c.close()
        for (item in feed.items) {
            c = adapter.getFeedItemCursor(item.id.toString())
            Assert.assertEquals(0, c.count.toLong())
            c.close()
            c = adapter.getSingleFeedMediaCursor(item.media!!.id)
            Assert.assertEquals(0, c.count.toLong())
            c.close()
        }
        adapter.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeedItems() {
        val feed = Feed("url", null, "title")
        feed.items = mutableListOf()
        feed.imageUrl = ("url")

        // create items
        for (i in 0..9) {
            val item = FeedItem(0, "Item $i", "Item$i", "url", Date(), FeedItem.PLAYED, feed)
            item.setMedia(FeedMedia(item, "", 0, ""))
            feed.items.add(item)
        }

        var adapter = getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        val itemsToDelete: List<FeedItem> = feed.items.subList(0, 2)
        deleteFeedItems(context!!, itemsToDelete)[TIMEOUT, TimeUnit.SECONDS]

        adapter = getInstance()
        adapter.open()
        for (i in 0 until feed.items.size) {
            val feedItem: FeedItem = feed.items[i]
            val c = adapter.getFeedItemCursor(feedItem.id.toString())
            if (i < 2) {
                Assert.assertEquals(0, c.count.toLong())
            } else {
                Assert.assertEquals(1, c.count.toLong())
            }
            c.close()
        }
        adapter.close()
    }

    private fun playbackHistorySetup(playbackCompletionDate: Date?): FeedMedia {
        val feed = Feed("url", null, "title")
        feed.items = mutableListOf()
        val item = FeedItem(0, "title", "id", "link", Date(), FeedItem.PLAYED, feed)
        val media = FeedMedia(0, item, 10, 0, 1, "mime", null,
            "url", false, playbackCompletionDate, 0, 0)
        feed.items.add(item)
        item.setMedia(media)
        val adapter = getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()
        Assert.assertTrue(media.id != 0L)
        return media
    }

    @Test
    @Throws(Exception::class)
    fun testAddItemToPlaybackHistoryNotPlayedYet() {
        var media: FeedMedia? = playbackHistorySetup(null)
        addItemToPlaybackHistory(media)[TIMEOUT, TimeUnit.SECONDS]
        val adapter = getInstance()
        adapter.open()
        media = getFeedMedia(media!!.id)
        adapter.close()

        Assert.assertNotNull(media)
        Assert.assertNotNull(media!!.getPlaybackCompletionDate())
    }

    @Test
    @Throws(Exception::class)
    fun testAddItemToPlaybackHistoryAlreadyPlayed() {
        val oldDate: Long = 0

        var media: FeedMedia? = playbackHistorySetup(Date(oldDate))
        addItemToPlaybackHistory(media)[TIMEOUT, TimeUnit.SECONDS]
        val adapter = getInstance()
        adapter.open()
        media = getFeedMedia(media!!.id)
        adapter.close()

        Assert.assertNotNull(media)
        Assert.assertNotNull(media!!.getPlaybackCompletionDate())
        Assert.assertNotEquals(media.getPlaybackCompletionDate()!!.time, oldDate)
    }

    @Throws(Exception::class)
    private fun queueTestSetupMultipleItems(numItems: Int): Feed {
        enqueueLocation = UserPreferences.EnqueueLocation.BACK
        val feed = Feed("url", null, "title")
        feed.items = mutableListOf()
        for (i in 0 until numItems) {
            val item = FeedItem(0, "title $i", "id $i", "link $i", Date(), FeedItem.PLAYED, feed)
            item.setMedia(FeedMedia(item, "", 0, ""))
            feed.items.add(item)
        }

        val adapter = getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        for (item in feed.items) {
            Assert.assertTrue(item.id != 0L)
        }
        val futures: MutableList<Future<*>> = ArrayList()
        for (item in feed.items) {
            futures.add(addQueueItem(context, item))
        }
        for (f in futures) {
            f[TIMEOUT, TimeUnit.SECONDS]
        }
        return feed
    }

    @Test
    @Throws(Exception::class)
    fun testAddQueueItemSingleItem() {
        val feed = Feed("url", null, "title")
        feed.items = mutableListOf()
        val item = FeedItem(0, "title", "id", "link", Date(), FeedItem.PLAYED, feed)
        item.setMedia(FeedMedia(item, "", 0, ""))
        feed.items.add(item)

        var adapter = getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        Assert.assertTrue(item.id != 0L)
        addQueueItem(context, item)[TIMEOUT, TimeUnit.SECONDS]

        adapter = getInstance()
        adapter.open()
        val cursor = adapter.queueIDCursor
        Assert.assertTrue(cursor.moveToFirst())
        Assert.assertEquals(item.id, cursor.getLong(0))
        cursor.close()
        adapter.close()
    }

    @Test
    @Throws(Exception::class)
    fun testAddQueueItemSingleItemAlreadyInQueue() {
        val feed = Feed("url", null, "title")
        feed.items = mutableListOf()
        val item = FeedItem(0, "title", "id", "link", Date(), FeedItem.PLAYED, feed)
        item.setMedia(FeedMedia(item, "", 0, ""))
        feed.items.add(item)

        var adapter = getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        Assert.assertTrue(item.id != 0L)
        addQueueItem(context, item)[TIMEOUT, TimeUnit.SECONDS]

        adapter = getInstance()
        adapter.open()
        var cursor = adapter.queueIDCursor
        Assert.assertTrue(cursor.moveToFirst())
        Assert.assertEquals(item.id, cursor.getLong(0))
        cursor.close()
        adapter.close()

        addQueueItem(context, item)[TIMEOUT, TimeUnit.SECONDS]
        adapter = getInstance()
        adapter.open()
        cursor = adapter.queueIDCursor
        Assert.assertTrue(cursor.moveToFirst())
        Assert.assertEquals(item.id, cursor.getLong(0))
        Assert.assertEquals(1, cursor.count.toLong())
        cursor.close()
        adapter.close()
    }

    @Test
    @Throws(Exception::class)
    fun testAddQueueItemMultipleItems() {
        val numItems = 10
        val feed = queueTestSetupMultipleItems(numItems)
        val adapter = getInstance()
        adapter.open()
        val cursor = adapter.queueIDCursor
        Assert.assertTrue(cursor.moveToFirst())
        Assert.assertEquals(numItems.toLong(), cursor.count.toLong())
        val expectedIds = getIdList(feed.items)
        val actualIds: MutableList<Long> = ArrayList()
        for (i in 0 until numItems) {
            Assert.assertTrue(cursor.moveToPosition(i))
            actualIds.add(cursor.getLong(0))
        }
        cursor.close()
        adapter.close()
        Assert.assertEquals("Bulk add to queue: result order should be the same as the order given",
            expectedIds, actualIds)
    }

    @Test
    @Throws(Exception::class)
    fun testClearQueue() {
        val numItems = 10

        queueTestSetupMultipleItems(numItems)
        clearQueue()[TIMEOUT, TimeUnit.SECONDS]
        val adapter = getInstance()
        adapter.open()
        val cursor = adapter.queueIDCursor
        Assert.assertFalse(cursor.moveToFirst())
        cursor.close()
        adapter.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRemoveQueueItem() {
        val numItems = 10
        val feed = createTestFeed(numItems)

        for (removeIndex in 0 until numItems) {
            val item: FeedItem = feed.items[removeIndex]
            var adapter = getInstance()
            adapter.open()
            adapter.setQueue(feed.items.toList())
            adapter.close()

            removeQueueItem(context!!, false, item)[TIMEOUT, TimeUnit.SECONDS]
            adapter = getInstance()
            adapter.open()
            val queue = adapter.queueIDCursor
            Assert.assertEquals((numItems - 1).toLong(), queue.count.toLong())
            for (i in 0 until queue.count) {
                Assert.assertTrue(queue.moveToPosition(i))
                val queueID = queue.getLong(0)
                Assert.assertTrue(queueID != item.id) // removed item is no longer in queue
                var idFound = false
                for (other in feed.items) { // items that were not removed are still in the queue
                    idFound = idFound or (other.id == queueID)
                }
                Assert.assertTrue(idFound)
            }
            queue.close()
            adapter.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testRemoveQueueItemMultipleItems() {
        val numItems = 5
        val numInQueue = numItems - 1 // the last one not in queue for boundary condition
        val feed = createTestFeed(numItems)

        val itemsToAdd: List<FeedItem> = feed.items.subList(0, numInQueue)
        withPodDB { adapter: PodDBAdapter? -> adapter!!.setQueue(itemsToAdd) }

        // Actual tests
        //

        // Use array rather than List to make codes more succinct
        val itemIds = toItemIds(feed.items).toTypedArray<Long>()

        removeQueueItem(context!!, false,
            itemIds[1], itemIds[3])[TIMEOUT, TimeUnit.SECONDS]
        assertQueueByItemIds("Average case - 2 items removed successfully",
            itemIds[0], itemIds[2])

        removeQueueItem(context!!, false)[TIMEOUT, TimeUnit.SECONDS]
        assertQueueByItemIds("Boundary case - no items supplied. queue should see no change",
            itemIds[0], itemIds[2])

        removeQueueItem(context!!, false,
            itemIds[0], itemIds[4], -1L)[TIMEOUT, TimeUnit.SECONDS]
        assertQueueByItemIds("Boundary case - items not in queue ignored",
            itemIds[2])

        removeQueueItem(context!!, false,
            itemIds[2], -1L)[TIMEOUT, TimeUnit.SECONDS]
        assertQueueByItemIds("Boundary case - invalid itemIds ignored") // the queue is empty
    }

    @Test
    @Throws(Exception::class)
    fun testMoveQueueItem() {
        val numItems = 10
        val feed = Feed("url", null, "title")
        feed.items = mutableListOf()
        for (i in 0 until numItems) {
            val item = FeedItem(0, "title $i", "id $i", "link $i",
                Date(), FeedItem.PLAYED, feed)
            item.setMedia(FeedMedia(item, "", 0, ""))
            feed.items.add(item)
        }

        var adapter = getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        for (item in feed.items) {
            Assert.assertTrue(item.id != 0L)
        }
        for (from in 0 until numItems) {
            for (to in 0 until numItems) {
                if (from == to) {
                    continue
                }
                Log.d(TAG, String.format(Locale.US, "testMoveQueueItem: From=%d, To=%d", from, to))
                val fromID: Long = feed.items[from].id

                adapter = getInstance()
                adapter.open()
                adapter.setQueue(feed.items)
                adapter.close()

                moveQueueItem(from, to, false)[TIMEOUT, TimeUnit.SECONDS]
                adapter = getInstance()
                adapter.open()
                val queue = adapter.queueIDCursor
                Assert.assertEquals(numItems.toLong(), queue.count.toLong())
                Assert.assertTrue(queue.moveToPosition(from))
                Assert.assertNotEquals(fromID, queue.getLong(0))
                Assert.assertTrue(queue.moveToPosition(to))
                Assert.assertEquals(fromID, queue.getLong(0))

                queue.close()
                adapter.close()
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testRemoveAllNewFlags() {
        val numItems = 10
        val feed = Feed("url", null, "title")
        feed.items = mutableListOf()
        for (i in 0 until numItems) {
            val item = FeedItem(0, "title $i", "id $i", "link $i",
                Date(), FeedItem.NEW, feed)
            item.setMedia(FeedMedia(item, "", 0, ""))
            feed.items.add(item)
        }

        val adapter = getInstance()
        adapter.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        Assert.assertTrue(feed.id != 0L)
        for (item in feed.items) {
            Assert.assertTrue(item.id != 0L)
        }

        removeAllNewFlags().get()
        val loadedItems = getFeedItemList(feed)
        for (item in loadedItems) {
            Assert.assertFalse(item.isNew)
        }
    }

    companion object {
        private const val TAG = "DBWriterTest"
        private const val TEST_FOLDER = "testDBWriter"
        private const val TIMEOUT = 5L

        private fun createTestFeed(numItems: Int): Feed {
            val feed = Feed("url", null, "title")
            feed.items = mutableListOf()
            for (i in 0 until numItems) {
                val item = FeedItem(0, "title $i", "id $i", "link $i",
                    Date(), FeedItem.PLAYED, feed)
                item.setMedia(FeedMedia(item, "", 0, ""))
                feed.items.add(item)
            }

            withPodDB { adapter: PodDBAdapter? -> adapter!!.setCompleteFeed(feed) }

            for (item in feed.items) {
                Assert.assertTrue(item.id != 0L)
            }
            return feed
        }

        private fun withPodDB(action: Consumer<PodDBAdapter?>) {
            val adapter = getInstance()
            try {
                adapter.open()
                action.accept(adapter)
            } finally {
                adapter.close()
            }
        }

        private fun assertQueueByItemIds(message: String, vararg itemIdsExpected: Long) {
            val queue = getQueue()
            val itemIdsActualList = toItemIds(queue)
            val itemIdsExpectedList: MutableList<Long> = ArrayList(itemIdsExpected.size)
            for (id in itemIdsExpected) {
                itemIdsExpectedList.add(id)
            }

            Assert.assertEquals(message, itemIdsExpectedList, itemIdsActualList)
        }

        private fun toItemIds(items: List<FeedItem>): List<Long> {
            val itemIds: MutableList<Long> = ArrayList(items.size)
            for (item in items) {
                itemIds.add(item.id)
            }
            return itemIds
        }
    }
}
