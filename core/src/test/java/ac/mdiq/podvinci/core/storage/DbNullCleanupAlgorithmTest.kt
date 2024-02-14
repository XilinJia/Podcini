package ac.mdiq.podvinci.core.storage

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import ac.mdiq.podvinci.core.storage.DBTasks.performAutoCleanup
import ac.mdiq.podvinci.model.feed.Feed
import ac.mdiq.podvinci.model.feed.FeedItem
import ac.mdiq.podvinci.model.feed.FeedMedia
import ac.mdiq.podvinci.storage.database.PodDBAdapter
import ac.mdiq.podvinci.storage.database.PodDBAdapter.Companion.deleteDatabase
import ac.mdiq.podvinci.storage.database.PodDBAdapter.Companion.getInstance
import ac.mdiq.podvinci.storage.preferences.UserPreferences
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.util.*

/**
 * Tests that the APNullCleanupAlgorithm is working correctly.
 */
@RunWith(RobolectricTestRunner::class)
class DbNullCleanupAlgorithmTest {
    private var context: Context? = null

    private var destFolder: File? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        destFolder = context!!.externalCacheDir
        cleanupDestFolder(destFolder)
        Assert.assertNotNull(destFolder)
        Assert.assertTrue(destFolder!!.exists())
        Assert.assertTrue(destFolder!!.canWrite())

        // create new database
        PodDBAdapter.init(context!!)
        deleteDatabase()
        val adapter = getInstance()
        adapter!!.open()
        adapter.close()

        val prefEdit = PreferenceManager.getDefaultSharedPreferences(context!!.applicationContext).edit()
        prefEdit.putString(UserPreferences.PREF_EPISODE_CACHE_SIZE, EPISODE_CACHE_SIZE.toString())
        prefEdit.putString(UserPreferences.PREF_EPISODE_CLEANUP, UserPreferences.EPISODE_CLEANUP_NULL.toString())
        prefEdit.commit()

        UserPreferences.init(context!!)
    }

    @After
    fun tearDown() {
        DBWriter.tearDownTests()
        deleteDatabase()
        PodDBAdapter.tearDownTests()

        cleanupDestFolder(destFolder)
        Assert.assertTrue(destFolder!!.delete())
    }

    private fun cleanupDestFolder(destFolder: File?) {
        for (f in destFolder!!.listFiles()) {
            Assert.assertTrue(f.delete())
        }
    }

    /**
     * A test with no items in the queue, but multiple items downloaded.
     * The null algorithm should never delete any items, even if they're played and not in the queue.
     */
    @Test
    @Throws(IOException::class)
    fun testPerformAutoCleanupShouldNotDelete() {
        val numItems = EPISODE_CACHE_SIZE * 2

        val feed = Feed("url", null, "title")
        val items: MutableList<FeedItem> = ArrayList()
        feed.items = (items)
        val files: MutableList<File> = ArrayList()
        for (i in 0 until numItems) {
            val item = FeedItem(0, "title", "id$i", "link", Date(), FeedItem.PLAYED, feed)

            val f = File(destFolder, "file $i")
            Assert.assertTrue(f.createNewFile())
            files.add(f)
            item.setMedia(FeedMedia(0, item, 1, 0, 1L, "m", f.absolutePath, "url", true,
                Date((numItems - i).toLong()), 0, 0))
            items.add(item)
        }

        val adapter = getInstance()
        adapter!!.open()
        adapter.setCompleteFeed(feed)
        adapter.close()

        Assert.assertTrue(feed.id != 0L)
        for (item in items) {
            Assert.assertTrue(item.id != 0L)
            Assert.assertTrue(item.media!!.id != 0L)
        }
        performAutoCleanup(context)
        for (i in files.indices) {
            Assert.assertTrue(files[i].exists())
        }
    }

    companion object {
        private const val EPISODE_CACHE_SIZE = 5
    }
}
