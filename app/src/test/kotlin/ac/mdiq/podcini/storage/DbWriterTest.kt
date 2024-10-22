package ac.mdiq.podcini.storage

import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.download.serviceinterface.DownloadServiceInterfaceTestStub
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.database.Episodes.setCompletionDate
import ac.mdiq.podcini.storage.database.Episodes.deleteEpisodes
import ac.mdiq.podcini.storage.database.Episodes.deleteEpisodeMedia
import ac.mdiq.podcini.storage.database.Episodes.getEpisode
import ac.mdiq.podcini.storage.database.Episodes.getEpisodeMedia
import ac.mdiq.podcini.storage.database.Episodes.persistEpisode
import ac.mdiq.podcini.storage.database.Episodes.persistEpisodeMedia
import ac.mdiq.podcini.storage.database.Episodes.shouldDeleteRemoveFromQueue
import ac.mdiq.podcini.storage.database.Feeds.deleteFeedSync
import ac.mdiq.podcini.storage.database.Queues
import ac.mdiq.podcini.storage.database.Queues.addToQueue
import ac.mdiq.podcini.storage.database.Queues.clearQueue
import ac.mdiq.podcini.storage.database.Queues.enqueueLocation
import ac.mdiq.podcini.storage.database.Queues.moveInQueue
import ac.mdiq.podcini.storage.database.Queues.removeFromQueue
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.config.ApplicationCallbacks
import ac.mdiq.podcini.util.config.ClientConfig
import android.app.Application
import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        UserPreferences.init(context)
//        init(context)

        val app = context as Application?
        ClientConfig.applicationCallbacks = Mockito.mock(ApplicationCallbacks::class.java)
        Mockito.`when`(ClientConfig.applicationCallbacks?.getApplicationInstance()).thenReturn(app)
        DownloadServiceInterface.setImpl(DownloadServiceInterfaceTestStub())

        // create new database
//        PodDBAdapter.init(context)
//        deleteDatabase()
//        val adapter = getInstance()
//        adapter.open()
//        adapter.close()

        val prefEdit = PreferenceManager.getDefaultSharedPreferences(
            context.applicationContext).edit()
        prefEdit.putBoolean(UserPreferences.Prefs.prefDeleteRemovesFromQueue.name, true).commit()
    }

    @After
    fun tearDown() {
//        PodDBAdapter.tearDownTests()
//        DBWriter.tearDownTests()

        val testDir = context.getExternalFilesDir(TEST_FOLDER) ?: return
        val files = testDir.listFiles() ?: return
        for (f in files) {
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
        val items: MutableList<Episode> = ArrayList()
        feed.episodes.addAll(items)
        val item = Episode(0, "Item", "Item", "url", Date(), PlayState.PLAYED.code, feed)
        items.add(item)
        val media = EpisodeMedia(0, item, duration, 1, 1, "mime_type",
            "dummy path", "download_url", true, null, 0, 0)
        item.setMedia(media)

        runBlocking {
            val job = persistEpisode(item)
            withTimeout(TIMEOUT*1000) { job.join() }
        }

        media.setPosition(position)
        media.setLastPlayedTime(lastPlayedTime)
        media.playedDuration = playedDuration

        if (item.media != null) {
            runBlocking {
                val job = persistEpisodeMedia(item.media!!)
                withTimeout(TIMEOUT * 1000) { job.join() }
            }
        }

        val itemFromDb = getEpisode(item.id)
        val mediaFromDb = itemFromDb!!.media

        Assert.assertEquals(position.toLong(), mediaFromDb!!.getPosition().toLong())
        Assert.assertEquals(lastPlayedTime, mediaFromDb.getLastPlayedTime())
        Assert.assertEquals(playedDuration.toLong(), mediaFromDb.playedDuration.toLong())
        Assert.assertEquals(duration.toLong(), mediaFromDb.getDuration().toLong())
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeedMediaOfItemFileExists() {
        val dest = File(context.getExternalFilesDir(TEST_FOLDER), "testFile")

        Assert.assertTrue(dest.createNewFile())

        val feed = Feed("url", null, "title")
        val items: MutableList<Episode> = ArrayList()
        feed.episodes.addAll(items)
        val item = Episode(0, "Item", "Item", "url", Date(), PlayState.PLAYED.code, feed)

        var media: EpisodeMedia? = EpisodeMedia(0, item, 1, 1, 1, "mime_type",
            dest.absolutePath, "download_url", true, null, 0, 0)
        item.setMedia(media)

        items.add(item)

//        val adapter = getInstance()
//        adapter.open()
//        adapter.setCompleteFeed(feed)
//        adapter.close()
        Assert.assertTrue(media!!.id != 0L)
        Assert.assertTrue(item.id != 0L)

        runBlocking {
            val job = deleteEpisodeMedia(context, item)
            withTimeout(TIMEOUT*1000) { job.join() }
        }
        media = getEpisodeMedia(media.id)
        Assert.assertNotNull(media)
        Assert.assertFalse(dest.exists())
        Assert.assertFalse(media!!.downloaded)
        Assert.assertNull(media.fileUrl)
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeedMediaOfItemRemoveFromQueue() {
        Assert.assertTrue(shouldDeleteRemoveFromQueue())

        val dest = File(context.getExternalFilesDir(TEST_FOLDER), "testFile")

        Assert.assertTrue(dest.createNewFile())

        val feed = Feed("url", null, "title")
        val items: MutableList<Episode> = ArrayList()
        feed.episodes.addAll(items)
        val item = Episode(0, "Item", "Item", "url", Date(), PlayState.UNPLAYED.code, feed)

        var media: EpisodeMedia? = EpisodeMedia(0, item, 1, 1, 1, "mime_type",
            dest.absolutePath, "download_url", true, null, 0, 0)
        item.setMedia(media)

        items.add(item)
        var queue: MutableList<Episode> = mutableListOf()
        queue.add(item)

//        val adapter = getInstance()
//        adapter.open()
//        adapter.setCompleteFeed(feed)
//        adapter.setQueue(queue)
//        adapter.close()
        Assert.assertTrue(media!!.id != 0L)
        Assert.assertTrue(item.id != 0L)
        queue = curQueue.episodes
        Assert.assertTrue(queue.size != 0)

        deleteEpisodeMedia(context, item)
        Awaitility.await().timeout(2, TimeUnit.SECONDS).until { !dest.exists() }
        media = getEpisodeMedia(media.id)
        Assert.assertNotNull(media)
        Assert.assertFalse(dest.exists())
        Assert.assertFalse(media!!.downloaded)
        Assert.assertNull(media.fileUrl)
        Awaitility.await().timeout(2, TimeUnit.SECONDS).until { curQueue.episodes.isEmpty() }
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeed() {
        val destFolder = context.getExternalFilesDir(TEST_FOLDER)
        Assert.assertNotNull(destFolder)

        val feed = Feed("url", null, "title")
        feed.episodes.clear()

        val itemFiles: MutableList<File> = ArrayList()
        // create items with downloaded media files
        for (i in 0..9) {
            val item = Episode(0, "Item $i", "Item$i", "url", Date(), PlayState.PLAYED.code, feed)
            feed.episodes.add(item)

            val enc = File(destFolder, "file $i")
            Assert.assertTrue(enc.createNewFile())

            itemFiles.add(enc)
            val media = EpisodeMedia(0, item, 1, 1, 1, "mime_type",
                enc.absolutePath, "download_url", true, null, 0, 0)
            item.setMedia(media)
        }

//        var adapter = getInstance()
//        adapter.open()
//        adapter.setCompleteFeed(feed)
//        adapter.close()

        Assert.assertTrue(feed.id != 0L)
        for (item in feed.episodes) {
            Assert.assertTrue(item.id != 0L)
            Assert.assertTrue(item.media!!.id != 0L)
        }

        runBlocking {
            deleteFeedSync(context, feed.id)
        }

        // check if files still exist
        for (f in itemFiles) {
            Assert.assertFalse(f.exists())
        }

//        adapter = getInstance()
//        adapter.open()
//        var c = adapter.getFeedCursor(feed.id)
//        Assert.assertEquals(0, c.count.toLong())
//        c.close()
//        for (item in feed.items) {
//            c = adapter.getFeedItemCursor(item.id.toString())
//            Assert.assertEquals(0, c.count.toLong())
//            c.close()
//            c = adapter.getSingleFeedMediaCursor(item.media!!.id)
//            Assert.assertEquals(0, c.count.toLong())
//            c.close()
//        }
//        adapter.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeedNoItems() {
        val destFolder = context.getExternalFilesDir(TEST_FOLDER)
        Assert.assertNotNull(destFolder)

        val feed = Feed("url", null, "title")
        feed.episodes.clear()
        feed.imageUrl = ("url")

//        var adapter = getInstance()
//        adapter.open()
//        adapter.setCompleteFeed(feed)
//        adapter.close()

        Assert.assertTrue(feed.id != 0L)

        runBlocking {
            deleteFeedSync(context, feed.id)
        }

//        adapter = getInstance()
//        adapter.open()
//        val c = adapter.getFeedCursor(feed.id)
//        Assert.assertEquals(0, c.count.toLong())
//        c.close()
//        adapter.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeedNoFeedMedia() {
        val destFolder = context.getExternalFilesDir(TEST_FOLDER)
        Assert.assertNotNull(destFolder)

        val feed = Feed("url", null, "title")
        feed.episodes.clear()

        feed.imageUrl = ("url")

        // create items
        for (i in 0..9) {
            val item = Episode(0, "Item $i", "Item$i", "url", Date(), PlayState.PLAYED.code, feed)
            feed.episodes.add(item)
        }

//        var adapter = getInstance()
//        adapter.open()
//        adapter.setCompleteFeed(feed)
//        adapter.close()

        Assert.assertTrue(feed.id != 0L)
        for (item in feed.episodes) {
            Assert.assertTrue(item.id != 0L)
        }

        runBlocking {
            deleteFeedSync(context, feed.id)
        }

//        adapter = getInstance()
//        adapter.open()
//        var c = adapter.getFeedCursor(feed.id)
//        Assert.assertEquals(0, c.count.toLong())
//        c.close()
//        for (item in feed.items) {
//            c = adapter.getFeedItemCursor(item.id.toString())
//            Assert.assertEquals(0, c.count.toLong())
//            c.close()
//        }
//        adapter.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeedWithQueueItems() {
        val destFolder = context.getExternalFilesDir(TEST_FOLDER)
        Assert.assertNotNull(destFolder)

        val feed = Feed("url", null, "title")
        feed.episodes.clear()

        feed.imageUrl = ("url")

        // create items with downloaded media files
        for (i in 0..9) {
            val item = Episode(0, "Item $i", "Item$i", "url", Date(), PlayState.PLAYED.code, feed)
            feed.episodes.add(item)
            val enc = File(destFolder, "file $i")
            val media = EpisodeMedia(0, item, 1, 1, 1, "mime_type",
                enc.absolutePath, "download_url", false, null, 0, 0)
            item.setMedia(media)
        }

//        val adapter = getInstance()
//        adapter.open()
//        adapter.setCompleteFeed(feed)
//        adapter.close()

        Assert.assertTrue(feed.id != 0L)
        for (item in feed.episodes) {
            Assert.assertTrue(item.id != 0L)
            Assert.assertTrue(item.media!!.id != 0L)
        }

        val queue: List<Episode> = feed.episodes.toList()
//        adapter.open()
//        adapter.setQueue(queue)
//
//        val queueCursor = adapter.queueIDCursor
//        Assert.assertEquals(queue.size.toLong(), queueCursor.count.toLong())
//        queueCursor.close()
//
//        adapter.close()
        runBlocking {
            deleteFeedSync(context, feed.id)
        }
//        adapter.open()
//
//        var c = adapter.getFeedCursor(feed.id)
//        Assert.assertEquals(0, c.count.toLong())
//        c.close()
//        for (item in feed.items) {
//            c = adapter.getFeedItemCursor(item.id.toString())
//            Assert.assertEquals(0, c.count.toLong())
//            c.close()
//            c = adapter.getSingleFeedMediaCursor(item.media!!.id)
//            Assert.assertEquals(0, c.count.toLong())
//            c.close()
//        }
//        c = adapter.queueCursor
//        Assert.assertEquals(0, c.count.toLong())
//        c.close()
//        adapter.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeedNoDownloadedFiles() {
        val destFolder = context.getExternalFilesDir(TEST_FOLDER)
        Assert.assertNotNull(destFolder)

        val feed = Feed("url", null, "title")
        feed.episodes.clear()

        feed.imageUrl = ("url")

        // create items with downloaded media files
        for (i in 0..9) {
            val item = Episode(0, "Item $i", "Item$i", "url", Date(), PlayState.PLAYED.code, feed)
            feed.episodes.add(item)
            val enc = File(destFolder, "file $i")
            val media = EpisodeMedia(0, item, 1, 1, 1, "mime_type",
                enc.absolutePath, "download_url", false, null, 0, 0)
            item.setMedia(media)
        }

//        var adapter = getInstance()
//        adapter.open()
//        adapter.setCompleteFeed(feed)
//        adapter.close()

        Assert.assertTrue(feed.id != 0L)
        for (item in feed.episodes) {
            Assert.assertTrue(item.id != 0L)
            Assert.assertTrue(item.media!!.id != 0L)
        }

        runBlocking {
            deleteFeedSync(context, feed.id)
        }

//        adapter = getInstance()
//        adapter.open()
//        var c = adapter.getFeedCursor(feed.id)
//        Assert.assertEquals(0, c.count.toLong())
//        c.close()
//        for (item in feed.items) {
//            c = adapter.getFeedItemCursor(item.id.toString())
//            Assert.assertEquals(0, c.count.toLong())
//            c.close()
//            c = adapter.getSingleFeedMediaCursor(item.media!!.id)
//            Assert.assertEquals(0, c.count.toLong())
//            c.close()
//        }
//        adapter.close()
    }

    @Test
    @Throws(Exception::class)
    fun testDeleteFeedItems() {
        val feed = Feed("url", null, "title")
        feed.episodes.clear()
        feed.imageUrl = ("url")

        // create items
        for (i in 0..9) {
            val item = Episode(0, "Item $i", "Item$i", "url", Date(), PlayState.PLAYED.code, feed)
            item.setMedia(EpisodeMedia(item, "", 0, ""))
            feed.episodes.add(item)
        }

//        var adapter = getInstance()
//        adapter.open()
//        adapter.setCompleteFeed(feed)
//        adapter.close()

        val itemsToDelete: List<Episode> = feed.episodes.subList(0, 2)
        runBlocking {
            val job = deleteEpisodes(context, itemsToDelete)
            withTimeout(TIMEOUT*1000) { job.join() }
        }

//        adapter = getInstance()
//        adapter.open()
//        for (i in 0 until feed.items.size) {
//            val feedItem: FeedItem = feed.items[i]
//            val c = adapter.getFeedItemCursor(feedItem.id.toString())
//            if (i < 2) Assert.assertEquals(0, c.count.toLong())
//            else Assert.assertEquals(1, c.count.toLong())
//            c.close()
//        }
//        adapter.close()
    }

    private fun playbackHistorySetup(playbackCompletionDate: Date?): EpisodeMedia {
        val feed = Feed("url", null, "title")
        feed.episodes.clear()
        val item = Episode(0, "title", "id", "link", Date(), PlayState.PLAYED.code, feed)
        val media = EpisodeMedia(0, item, 10, 0, 1, "mime", null,
            "url", false, playbackCompletionDate, 0, 0)
        feed.episodes.add(item)
        item.setMedia(media)
//        val adapter = getInstance()
//        adapter.open()
//        adapter.setCompleteFeed(feed)
//        adapter.close()
        Assert.assertTrue(media.id != 0L)
        return media
    }

    @Test
    @Throws(Exception::class)
    fun testAddItemToPlaybackHistoryNotPlayedYet() {
        val media: EpisodeMedia = playbackHistorySetup(null)
        val item_ = media.episodeOrFetch()
        if (item_ != null) {
            runBlocking {
                val job = setCompletionDate(item_)
                withTimeout(TIMEOUT * 1000) { job.join() }
            }
        }
        Assert.assertNotNull(media)
        Assert.assertNotNull(media.playbackCompletionDate)
    }

    @Test
    @Throws(Exception::class)
    fun testAddItemToPlaybackHistoryAlreadyPlayed() {
        val oldDate: Long = 0

        val media: EpisodeMedia = playbackHistorySetup(Date(oldDate))
        val item_ = media.episodeOrFetch()
        if (item_ != null) {
            runBlocking {
                val job = setCompletionDate(item_)
                withTimeout(TIMEOUT*1000) { job.join() }
            }
        }

        Assert.assertNotNull(media)
        Assert.assertNotNull(media.playbackCompletionDate)
        Assert.assertNotEquals(media.playbackCompletionDate!!.time, oldDate)
    }

    @Throws(Exception::class)
    private fun queueTestSetupMultipleItems(numItems: Int): Feed {
        enqueueLocation = Queues.EnqueueLocation.BACK
        val feed = Feed("url", null, "title")
        feed.episodes.clear()
        for (i in 0 until numItems) {
            val item = Episode(0, "title $i", "id $i", "link $i", Date(), PlayState.PLAYED.code, feed)
            item.setMedia(EpisodeMedia(item, "", 0, ""))
            feed.episodes.add(item)
        }

//        val adapter = getInstance()
//        adapter.open()
//        adapter.setCompleteFeed(feed)
//        adapter.close()

        for (item in feed.episodes) {
            Assert.assertTrue(item.id != 0L)
        }
        val futures: MutableList<Future<*>> = ArrayList()
        // TODO:
//        for (item in feed.items) {
//            futures.add(addQueueItem(context, item))
//        }
//        for (f in futures) {
//            f[TIMEOUT, TimeUnit.SECONDS]
//        }
        return feed
    }

    @Test
    @Throws(Exception::class)
    fun testAddQueueItemSingleItem() {
        val feed = Feed("url", null, "title")
        feed.episodes.clear()
        val item = Episode(0, "title", "id", "link", Date(), PlayState.PLAYED.code, feed)
        item.setMedia(EpisodeMedia(item, "", 0, ""))
        feed.episodes.add(item)

//        var adapter = getInstance()
//        adapter.open()
//        adapter.setCompleteFeed(feed)
//        adapter.close()

        Assert.assertTrue(item.id != 0L)
        runBlocking {
            val job = addToQueue(item)
            withTimeout(TIMEOUT*1000) { job.join() }
        }

//        adapter = getInstance()
//        adapter.open()
//        val cursor = adapter.queueIDCursor
//        Assert.assertTrue(cursor.moveToFirst())
//        Assert.assertEquals(item.id, cursor.getLong(0))
//        cursor.close()
//        adapter.close()
    }

    @Test
    @Throws(Exception::class)
    fun testAddQueueItemSingleItemAlreadyInQueue() {
        val feed = Feed("url", null, "title")
        feed.episodes.clear()
        val item = Episode(0, "title", "id", "link", Date(), PlayState.PLAYED.code, feed)
        item.setMedia(EpisodeMedia(item, "", 0, ""))
        feed.episodes.add(item)

//        var adapter = getInstance()
//        adapter.open()
//        adapter.setCompleteFeed(feed)
//        adapter.close()

        Assert.assertTrue(item.id != 0L)
        runBlocking {
            val job = addToQueue(item)
            withTimeout(TIMEOUT*1000) { job.join() }
        }

//        adapter = getInstance()
//        adapter.open()
//        var cursor = adapter.queueIDCursor
//        Assert.assertTrue(cursor.moveToFirst())
//        Assert.assertEquals(item.id, cursor.getLong(0))
//        cursor.close()
//        adapter.close()

        runBlocking {
            val job = addToQueue(item)
            withTimeout(TIMEOUT*1000) { job.join() }
        }
//        adapter = getInstance()
//        adapter.open()
//        cursor = adapter.queueIDCursor
//        Assert.assertTrue(cursor.moveToFirst())
//        Assert.assertEquals(item.id, cursor.getLong(0))
//        Assert.assertEquals(1, cursor.count.toLong())
//        cursor.close()
//        adapter.close()
    }

    @Test
    @Throws(Exception::class)
    fun testAddQueueItemMultipleItems() {
//        val numItems = 10
//        val feed = queueTestSetupMultipleItems(numItems)
//        val adapter = getInstance()
//        adapter.open()
//        val cursor = adapter.queueIDCursor
//        Assert.assertTrue(cursor.moveToFirst())
//        Assert.assertEquals(numItems.toLong(), cursor.count.toLong())
//        val expectedIds = getIdList(feed.items)
//        val actualIds: MutableList<Long> = ArrayList()
//        for (i in 0 until numItems) {
//            Assert.assertTrue(cursor.moveToPosition(i))
//            actualIds.add(cursor.getLong(0))
//        }
//        cursor.close()
//        adapter.close()
//        Assert.assertEquals("Bulk add to queue: result order should be the same as the order given", expectedIds, actualIds)
    }

    @Test
    @Throws(Exception::class)
    fun testClearQueue() {
        val numItems = 10

        queueTestSetupMultipleItems(numItems)
        runBlocking {
            val job = clearQueue()
            withTimeout(TIMEOUT*1000) { job.join() }
        }
//        val adapter = getInstance()
//        adapter.open()
//        val cursor = adapter.queueIDCursor
//        Assert.assertFalse(cursor.moveToFirst())
//        cursor.close()
//        adapter.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRemoveQueueItem() {
        val numItems = 10
        val feed = createTestFeed(numItems)

        for (removeIndex in 0 until numItems) {
            val item: Episode = feed.episodes[removeIndex]
//            var adapter = getInstance()
//            adapter.open()
//            adapter.setQueue(feed.items.toList())
//            adapter.close()

            runBlocking {
                val job = removeFromQueue(item)
                withTimeout(TIMEOUT*1000) { job.join() }
            }
//            adapter = getInstance()
//            adapter.open()
//            val queue = adapter.queueIDCursor
//            Assert.assertEquals((numItems - 1).toLong(), queue.count.toLong())
//            for (i in 0 until queue.count) {
//                Assert.assertTrue(queue.moveToPosition(i))
//                val queueID = queue.getLong(0)
//                Assert.assertTrue(queueID != item.id) // removed item is no longer in queue
//                var idFound = false
//                for (other in feed.items) { // items that were not removed are still in the queue
//                    idFound = idFound or (other.id == queueID)
//                }
//                Assert.assertTrue(idFound)
//            }
//            queue.close()
//            adapter.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testRemoveQueueItemMultipleItems() {
        val numItems = 5
        val numInQueue = numItems - 1 // the last one not in queue for boundary condition
        val feed = createTestFeed(numItems)

//        val itemsToAdd: List<FeedItem> = feed.items.subList(0, numInQueue)
//        withPodDB { adapter: PodDBAdapter? -> adapter!!.setQueue(itemsToAdd) }

        // Actual tests
        //

        // Use array rather than List to make codes more succinct
        val itemIds = toItemIds(feed.episodes).toTypedArray<Long>()

        runBlocking {
            val job = removeFromQueue(feed.episodes[1], feed.episodes[3])
            withTimeout(TIMEOUT*1000) { job.join() }
        }
        assertQueueByItemIds("Average case - 2 items removed successfully", itemIds[0], itemIds[2])

        runBlocking {
            val job = removeFromQueue()
            withTimeout(TIMEOUT*1000) { job.join() }
        }
        assertQueueByItemIds("Boundary case - no items supplied. queue should see no change", itemIds[0], itemIds[2])

        runBlocking {
            val job = removeFromQueue( feed.episodes[0], feed.episodes[4])
            withTimeout(TIMEOUT*1000) { job.join() }
        }
        assertQueueByItemIds("Boundary case - items not in queue ignored", itemIds[2])

        runBlocking {
            val job = removeFromQueue( feed.episodes[2])
            withTimeout(TIMEOUT*1000) { job.join() }
        }
        assertQueueByItemIds("Boundary case - invalid itemIds ignored") // the queue is empty
    }

    @Test
    @Throws(Exception::class)
    fun testMoveQueueItem() {
        val numItems = 10
        val feed = Feed("url", null, "title")
        feed.episodes.clear()
        for (i in 0 until numItems) {
            val item = Episode(0, "title $i", "id $i", "link $i",
                Date(), PlayState.PLAYED.code, feed)
            item.setMedia(EpisodeMedia(item, "", 0, ""))
            feed.episodes.add(item)
        }

//        var adapter = getInstance()
//        adapter.open()
//        adapter.setCompleteFeed(feed)
//        adapter.close()

        for (item in feed.episodes) {
            Assert.assertTrue(item.id != 0L)
        }
        for (from in 0 until numItems) {
            for (to in 0 until numItems) {
                if (from == to) continue

                Logd(TAG, String.format(Locale.US, "testMoveQueueItem: From=%d, To=%d", from, to))
                val fromID: Long = feed.episodes[from].id

//                adapter = getInstance()
//                adapter.open()
//                adapter.setQueue(feed.items)
//                adapter.close()

                runBlocking {
                    val job = moveInQueue(from, to, false)
                    withTimeout(TIMEOUT*1000) { job.join() }
                }
//                adapter = getInstance()
//                adapter.open()
//                val queue = adapter.queueIDCursor
//                Assert.assertEquals(numItems.toLong(), queue.count.toLong())
//                Assert.assertTrue(queue.moveToPosition(from))
//                Assert.assertNotEquals(fromID, queue.getLong(0))
//                Assert.assertTrue(queue.moveToPosition(to))
//                Assert.assertEquals(fromID, queue.getLong(0))
//
//                queue.close()
//                adapter.close()
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testRemoveAllNewFlags() {
        val numItems = 10
        val feed = Feed("url", null, "title")
        feed.episodes.clear()
        for (i in 0 until numItems) {
            val item = Episode(0, "title $i", "id $i", "link $i", Date(), PlayState.NEW.code, feed)
            item.setMedia(EpisodeMedia(item, "", 0, ""))
            feed.episodes.add(item)
        }

//        val adapter = getInstance()
//        adapter.open()
//        adapter.setCompleteFeed(feed)
//        adapter.close()

        Assert.assertTrue(feed.id != 0L)
        for (item in feed.episodes) {
            Assert.assertTrue(item.id != 0L)
        }

//        runBlocking { removeAllNewFlags().join() }
        val loadedItems = feed.episodes
        for (item in loadedItems) {
            Assert.assertFalse(item.isNew)
        }
    }

    companion object {
        private val TAG: String = DbWriterTest::class.simpleName ?: "Anonymous"
        private const val TEST_FOLDER = "testDBWriter"
        private const val TIMEOUT = 5L

        private fun createTestFeed(numItems: Int): Feed {
            val feed = Feed("url", null, "title")
            feed.episodes.clear()
            for (i in 0 until numItems) {
                val item = Episode(0, "title $i", "id $i", "link $i", Date(), PlayState.PLAYED.code, feed)
                item.setMedia(EpisodeMedia(item, "", 0, ""))
                feed.episodes.add(item)
            }

//            withPodDB { adapter: PodDBAdapter? -> adapter!!.setCompleteFeed(feed) }

            for (item in feed.episodes) {
                Assert.assertTrue(item.id != 0L)
            }
            return feed
        }

//        private fun withPodDB(action: Consumer<PodDBAdapter?>) {
////            val adapter = getInstance()
////            try {
////                adapter.open()
////                action.accept(adapter)
////            } finally {
////                adapter.close()
////            }
//        }

        private fun assertQueueByItemIds(message: String, vararg itemIdsExpected: Long) {
            val queue = curQueue.episodes
            val itemIdsActualList = toItemIds(queue)
            val itemIdsExpectedList: MutableList<Long> = ArrayList(itemIdsExpected.size)
            for (id in itemIdsExpected) {
                itemIdsExpectedList.add(id)
            }

            Assert.assertEquals(message, itemIdsExpectedList, itemIdsActualList)
        }

        private fun toItemIds(items: List<Episode>): List<Long> {
            val itemIds: MutableList<Long> = ArrayList(items.size)
            for (item in items) {
                itemIds.add(item.id)
            }
            return itemIds
        }
    }
}
