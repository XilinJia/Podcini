package ac.mdiq.podcini.storage

import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.storage.algorithms.AutoCleanups.performAutoCleanup
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.util.config.ApplicationCallbacks
import ac.mdiq.podcini.util.config.ClientConfig
import android.app.Application
import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.util.*

/**
 * Test class for DBTasks.
 */
@RunWith(RobolectricTestRunner::class)
open class DbCleanupTests {
    private var cleanupAlgorithm = 0
    lateinit var context: Context
    private lateinit var destFolder: File

    init {
        setCleanupAlgorithm(UserPreferences.EPISODE_CLEANUP_DEFAULT)
    }

    protected fun setCleanupAlgorithm(cleanupAlgorithm: Int) {
        this.cleanupAlgorithm = cleanupAlgorithm
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        destFolder = File(context.cacheDir, "DbCleanupTests")
        destFolder.mkdir()
        cleanupDestFolder(destFolder)
        Assert.assertNotNull(destFolder)
        Assert.assertTrue(destFolder.exists())
        Assert.assertTrue(destFolder.canWrite())

        val prefEdit = PreferenceManager.getDefaultSharedPreferences(context.applicationContext).edit()
        prefEdit.putString(UserPreferences.Prefs.prefEpisodeCacheSize.name, EPISODE_CACHE_SIZE.toString())
        prefEdit.putString(UserPreferences.Prefs.prefEpisodeCleanup.name, cleanupAlgorithm.toString())
        prefEdit.putBoolean(UserPreferences.Prefs.prefEnableAutoDl.name, true)
        prefEdit.commit()

        UserPreferences.init(context)
//        init(context)

        val app = context as Application?
        ClientConfig.applicationCallbacks = Mockito.mock(ApplicationCallbacks::class.java)
        Mockito.`when`(ClientConfig.applicationCallbacks?.getApplicationInstance()).thenReturn(app)
    }

    @After
    fun tearDown() {
        cleanupDestFolder(destFolder)
        Assert.assertTrue(destFolder.delete())

//        DBWriter.tearDownTests()
//        PodDBAdapter.tearDownTests()
    }

    private fun cleanupDestFolder(destFolder: File?) {
        for (f in destFolder!!.listFiles()!!) {
            Assert.assertTrue(f.delete())
        }
    }

    @Test
    @Throws(IOException::class)
    fun testPerformAutoCleanupShouldDelete() {
        val numItems = EPISODE_CACHE_SIZE * 2

        val feed = Feed("url", null, "title")
        val items: MutableList<Episode> = ArrayList()
        feed.episodes.addAll(items)
        val files: MutableList<File> = ArrayList()
        populateItems(numItems, feed, items, files, PlayState.PLAYED.code, false, false)

        performAutoCleanup(context)
        for (i in files.indices) {
            if (i < EPISODE_CACHE_SIZE) {
                Assert.assertTrue(files[i].exists())
            } else {
                Assert.assertFalse(files[i].exists())
            }
        }
    }

    @Throws(IOException::class)
    fun populateItems(numItems: Int, feed: Feed, items: MutableList<Episode>, files: MutableList<File>, itemState: Int, addToQueue: Boolean, addToFavorites: Boolean) {
        for (i in 0 until numItems) {
            val itemDate = Date((numItems - i).toLong())
            var playbackCompletionDate: Date? = null
            if (itemState == PlayState.PLAYED.code) {
                playbackCompletionDate = itemDate
            }
            val item = Episode(0, "title", "id$i", "link", itemDate, itemState, feed)

            val f = File(destFolder, "file $i")
            Assert.assertTrue(f.createNewFile())
            files.add(f)
            item.setMedia(EpisodeMedia(0, item, 1, 0, 1L, "m",
                f.absolutePath, "url", true, playbackCompletionDate, 0, 0))
            items.add(item)
        }

//        val adapter = getInstance()
//        adapter.open()
//        adapter.setCompleteFeed(feed)
//        if (addToQueue) adapter.setQueue(items)
//        if (addToFavorites) adapter.setFavorites(items)
//        adapter.close()

        Assert.assertTrue(feed.id != 0L)
        for (item in items) {
            Assert.assertTrue(item.id != 0L)
            Assert.assertTrue(item.media!!.id != 0L)
        }
    }

    @Test
    @Throws(IOException::class)
    open fun testPerformAutoCleanupHandleUnplayed() {
        val numItems = EPISODE_CACHE_SIZE * 2

        val feed = Feed("url", null, "title")
        val items: MutableList<Episode> = ArrayList()
        feed.episodes.addAll(items)
        val files: MutableList<File> = ArrayList()
        populateItems(numItems, feed, items, files, PlayState.UNPLAYED.code, false, false)

        performAutoCleanup(context)
        for (file in files) {
            Assert.assertTrue(file.exists())
        }
    }

    @Test
    @Throws(IOException::class)
    open fun testPerformAutoCleanupShouldNotDeleteBecauseInQueue() {
        val numItems = EPISODE_CACHE_SIZE * 2

        val feed = Feed("url", null, "title")
        val items: MutableList<Episode> = ArrayList()
        feed.episodes.addAll(items)
        val files: MutableList<File> = ArrayList()
        populateItems(numItems, feed, items, files, PlayState.PLAYED.code, true, false)

        performAutoCleanup(context)
        for (file in files) {
            Assert.assertTrue(file.exists())
        }
    }

    /**
     * Reproduces a bug where DBTasks.performAutoCleanup(android.content.Context) would use the ID
     * of the FeedItem in the call to DBWriter.deleteFeedMediaOfItem instead of the ID of the EpisodeMedia.
     * This would cause the wrong item to be deleted.
     */
    @Test
    @Throws(IOException::class)
    open fun testPerformAutoCleanupShouldNotDeleteBecauseInQueue_withFeedsWithNoMedia() {
        // add feed with no enclosures so that item ID != media ID
        DbTestUtils.saveFeedlist(1, 10, false)

        // add candidate for performAutoCleanup
        val feeds = DbTestUtils.saveFeedlist(1, 1, true)
        val m: EpisodeMedia = feeds[0].episodes[0].media!!
        m.downloaded = (true)
        m.fileUrl = ("file")
//        val adapter = getInstance()
//        adapter.open()
//        adapter.setMedia(m)
//        adapter.close()

        testPerformAutoCleanupShouldNotDeleteBecauseInQueue()
    }

    @Test
    @Throws(IOException::class)
    fun testPerformAutoCleanupShouldNotDeleteBecauseFavorite() {
        val numItems = EPISODE_CACHE_SIZE * 2

        val feed = Feed("url", null, "title")
        val items: MutableList<Episode> = ArrayList()
        feed.episodes.addAll(items)
        val files: MutableList<File> = ArrayList()
        populateItems(numItems, feed, items, files, PlayState.PLAYED.code, false, true)

        performAutoCleanup(context)
        for (file in files) {
            Assert.assertTrue(file.exists())
        }
    }

    companion object {
        const val EPISODE_CACHE_SIZE: Int = 5
    }
}
