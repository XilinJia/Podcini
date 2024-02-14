package de.danoeh.antennapod.core.storage

import de.danoeh.antennapod.core.storage.DBTasks.performAutoCleanup
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.storage.preferences.UserPreferences
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

/**
 * Tests that the APFavoriteCleanupAlgorithm is working correctly.
 */
@RunWith(RobolectricTestRunner::class)
class ExceptFavoriteCleanupAlgorithmTest : DbCleanupTests() {
    private val numberOfItems = EPISODE_CACHE_SIZE * 2

    init {
        setCleanupAlgorithm(UserPreferences.EPISODE_CLEANUP_EXCEPT_FAVORITE)
    }

    @Test
    @Throws(IOException::class)
    override fun testPerformAutoCleanupHandleUnplayed() {
        val feed = Feed("url", null, "title")
        val items: MutableList<FeedItem> = ArrayList()
        feed.items = (items)
        val files: MutableList<File> = ArrayList()
        populateItems(numberOfItems, feed, items, files, FeedItem.UNPLAYED, false, false)

        performAutoCleanup(context)
        for (i in files.indices) {
            if (i < EPISODE_CACHE_SIZE) {
                Assert.assertTrue("Only enough items should be deleted", files[i].exists())
            } else {
                Assert.assertFalse("Expected episode to be deleted", files[i].exists())
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testPerformAutoCleanupDeletesQueued() {
        val feed = Feed("url", null, "title")
        val items: MutableList<FeedItem> = ArrayList()
        feed.items = (items)
        val files: MutableList<File> = ArrayList()
        populateItems(numberOfItems, feed, items, files, FeedItem.UNPLAYED, true, false)

        performAutoCleanup(context)
        for (i in files.indices) {
            if (i < EPISODE_CACHE_SIZE) {
                Assert.assertTrue("Only enough items should be deleted", files[i].exists())
            } else {
                Assert.assertFalse("Queued episodes should be deleted", files[i].exists())
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testPerformAutoCleanupSavesFavorited() {
        val feed = Feed("url", null, "title")
        val items: MutableList<FeedItem> = ArrayList()
        feed.items = (items)
        val files: MutableList<File> = ArrayList()
        populateItems(numberOfItems, feed, items, files, FeedItem.UNPLAYED, false, true)

        performAutoCleanup(context)
        for (i in files.indices) {
            Assert.assertTrue("Favorite episodes should should not be deleted", files[i].exists())
        }
    }

    @Throws(IOException::class)
    override fun testPerformAutoCleanupShouldNotDeleteBecauseInQueue() {
        // Yes it should
    }

    @Throws(IOException::class)
    override fun testPerformAutoCleanupShouldNotDeleteBecauseInQueue_withFeedsWithNoMedia() {
        // Yes it should
    }
}
