package de.test.podcini.ui

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import ac.mdiq.podcini.event.FeedListUpdateEvent
import ac.mdiq.podcini.event.QueueEvent.Companion.setQueue
import ac.mdiq.podcini.model.feed.Feed
import ac.mdiq.podcini.model.feed.FeedItem
import ac.mdiq.podcini.model.feed.FeedMedia
import ac.mdiq.podcini.storage.database.PodDBAdapter.Companion.deleteDatabase
import ac.mdiq.podcini.storage.database.PodDBAdapter.Companion.getInstance
import de.test.podcini.util.service.download.HTTPBin
import de.test.podcini.util.syndication.feedgenerator.Rss2Generator
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.greenrobot.eventbus.EventBus
import org.junit.Assert
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

/**
 * Utility methods for UI tests.
 * Starts a web server that hosts feeds, episodes and images.
 */
class UITestUtils(private val context: Context) {
    private var testFileName = "3sec.mp3"
    private var hostTextOnlyFeeds = false
    private val server = HTTPBin()
    private var destDir: File? = null
    private var hostedFeedDir: File? = null
    private var hostedMediaDir: File? = null

    val hostedFeeds: MutableList<Feed> = ArrayList()

    @Throws(IOException::class)
    fun setup() {
        destDir = File(context.filesDir, "test/UITestUtils")
        destDir!!.mkdirs()
        hostedFeedDir = File(destDir, "hostedFeeds")
        hostedFeedDir!!.mkdir()
        hostedMediaDir = File(destDir, "hostedMediaDir")
        hostedMediaDir!!.mkdir()
        Assert.assertTrue(destDir!!.exists())
        Assert.assertTrue(hostedFeedDir!!.exists())
        Assert.assertTrue(hostedMediaDir!!.exists())
        server.start()
    }

    @Throws(IOException::class)
    fun tearDown() {
        FileUtils.deleteDirectory(destDir)
        FileUtils.deleteDirectory(hostedMediaDir)
        FileUtils.deleteDirectory(hostedFeedDir)
        server.stop()

        if (localFeedDataAdded) {
            deleteDatabase()
        }
    }

    @Throws(IOException::class)
    fun hostFeed(feed: Feed): String {
        val feedFile = File(hostedFeedDir, feed.title?:"")
        val out = FileOutputStream(feedFile)
        val generator = Rss2Generator()
        generator.writeFeed(feed, out, "UTF-8", 0)
        out.close()
        val id = server.serveFile(feedFile)
        Assert.assertTrue(id != -1)
        return String.format(Locale.US, "%s/files/%d", server.baseUrl, id)
    }

    private fun hostFile(file: File): String {
        val id = server.serveFile(file)
        Assert.assertTrue(id != -1)
        return String.format(Locale.US, "%s/files/%d", server.baseUrl, id)
    }

    @Throws(IOException::class)
    private fun newMediaFile(name: String): File {
        val mediaFile = File(hostedMediaDir, name)
        if (mediaFile.exists()) {
            mediaFile.delete()
        }
        Assert.assertFalse(mediaFile.exists())

        val inVal = InstrumentationRegistry.getInstrumentation().context
            .assets.open(testFileName)
        Assert.assertNotNull(inVal)

        val out = FileOutputStream(mediaFile)
        IOUtils.copy(inVal, out)
        out.close()

        return mediaFile
    }

    private var feedDataHosted = false

    /**
     * Adds feeds, images and episodes to the webserver for testing purposes.
     */
    @Throws(IOException::class)
    fun addHostedFeedData() {
        check(!feedDataHosted) { "addHostedFeedData was called twice on the same instance" }
        for (i in 0 until NUM_FEEDS) {
            val feed = Feed(0, null, "Title $i", "http://example.com/$i", "Description of feed $i",
                "http://example.com/pay/feed$i", "author $i", "en", Feed.TYPE_RSS2, "feed$i", null, null,
                "http://example.com/feed/src/$i", false)

            // create items
            val items: MutableList<FeedItem> = ArrayList()
            for (j in 0 until NUM_ITEMS_PER_FEED) {
                val item = FeedItem(j.toLong(), "Feed " + (i + 1) + ": Item " + (j + 1), "item$j",
                    "http://example.com/feed$i/item/$j", Date(), FeedItem.UNPLAYED, feed)
                items.add(item)

                if (!hostTextOnlyFeeds) {
                    val mediaFile = newMediaFile("feed-$i-episode-$j.mp3")
                    item.setMedia(FeedMedia(j.toLong(),
                        item,
                        0,
                        0,
                        mediaFile.length(),
                        "audio/mp3",
                        null,
                        hostFile(mediaFile),
                        false,
                        null,
                        0,
                        0))
                }
            }
            feed.items = items
            feed.download_url = hostFeed(feed)
            hostedFeeds.add(feed)
        }
        feedDataHosted = true
    }


    private var localFeedDataAdded = false


    /**
     * Adds feeds, images and episodes to the local database. This method will also call addHostedFeedData if it has not
     * been called yet.
     *
     * Adds one item of each feed to the queue and to the playback history.
     *
     * This method should NOT be called if the testing class wants to download the hosted feed data.
     *
     * @param downloadEpisodes true if episodes should also be marked as downloaded.
     */
    @Throws(Exception::class)
    fun addLocalFeedData(downloadEpisodes: Boolean) {
        if (localFeedDataAdded) {
            Log.w(TAG, "addLocalFeedData was called twice on the same instance")
            // might be a flaky test, this is actually not that severe
            return
        }
        if (!feedDataHosted) {
            addHostedFeedData()
        }

        val queue: MutableList<FeedItem> = ArrayList()
        for (feed in hostedFeeds) {
            feed.setDownloaded(true)
            if (downloadEpisodes) {
                for (item in feed.items) {
                    if (item.hasMedia()) {
                        val media = item.media
                        val fileId = StringUtils.substringAfter(media!!.download_url, "files/").toInt()
                        media.setFile_url(server.accessFile(fileId)?.absolutePath)
                        media.setDownloaded(true)
                    }
                }
            }

            queue.add(feed.items[0])
            if (feed.items[1].hasMedia()) {
                feed.items[1].media!!.setPlaybackCompletionDate(Date())
            }
        }
        localFeedDataAdded = true

        val adapter = getInstance()
        adapter.open()
        adapter.setCompleteFeed(*hostedFeeds.toTypedArray<Feed>())
        adapter.setQueue(queue)
        adapter.close()
        EventBus.getDefault().post(FeedListUpdateEvent(hostedFeeds))
        EventBus.getDefault().post(setQueue(queue))
    }

    fun setMediaFileName(filename: String) {
        testFileName = filename
    }

    fun setHostTextOnlyFeeds(hostTextOnlyFeeds: Boolean) {
        this.hostTextOnlyFeeds = hostTextOnlyFeeds
    }

    companion object {
        private val TAG: String = UITestUtils::class.java.simpleName

        private const val NUM_FEEDS = 5
        private const val NUM_ITEMS_PER_FEED = 10
    }
}
