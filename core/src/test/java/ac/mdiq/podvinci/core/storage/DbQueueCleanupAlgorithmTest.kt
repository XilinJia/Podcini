package ac.mdiq.podvinci.core.storage

import ac.mdiq.podvinci.core.storage.DBTasks.performAutoCleanup
import ac.mdiq.podvinci.model.feed.Feed
import ac.mdiq.podvinci.model.feed.FeedItem
import ac.mdiq.podvinci.storage.preferences.UserPreferences
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

/**
 * Tests that the APQueueCleanupAlgorithm is working correctly.
 */
@RunWith(RobolectricTestRunner::class)
class DbQueueCleanupAlgorithmTest : DbCleanupTests() {
    init {
        setCleanupAlgorithm(UserPreferences.EPISODE_CLEANUP_QUEUE)
    }

    /**
     * For APQueueCleanupAlgorithm we expect even unplayed episodes to be deleted if needed
     * if they aren't in the queue.
     */
    @Test
    @Throws(IOException::class)
    override fun testPerformAutoCleanupHandleUnplayed() {
        val numItems = EPISODE_CACHE_SIZE * 2

        val feed = Feed("url", null, "title")
        val items: MutableList<FeedItem> = ArrayList()
        feed.items = (items)
        val files: MutableList<File> = ArrayList()
        populateItems(numItems, feed, items, files, FeedItem.UNPLAYED, false, false)

        performAutoCleanup(context)
        for (i in files.indices) {
            if (i < EPISODE_CACHE_SIZE) {
                Assert.assertTrue(files[i].exists())
            } else {
                Assert.assertFalse(files[i].exists())
            }
        }
    }
}
