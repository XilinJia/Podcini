package ac.mdiq.podcini.feed

import ac.mdiq.podcini.feed.FeedMother.anyFeed
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.storage.model.feed.SortOrder
import junit.framework.TestCase.assertEquals
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class FeedTest {
    private var original: Feed? = null
    private var changedFeed: Feed? = null

    @Before
    fun setUp() {
        original = anyFeed()
        changedFeed = anyFeed()
    }

    @Test
    @Throws(Exception::class)
    fun testCompareWithOther_feedImageDownloadUrlChanged() {
        setNewFeedImageDownloadUrl()
        feedHasChanged()
    }

    @Test
    @Throws(Exception::class)
    fun testCompareWithOther_sameFeedImage() {
        changedFeed!!.imageUrl =(FeedMother.IMAGE_URL)
        feedHasNotChanged()
    }

    @Test
    @Throws(Exception::class)
    fun testCompareWithOther_feedImageRemoved() {
        feedImageRemoved()
        feedHasNotChanged()
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateFromOther_feedImageDownloadUrlChanged() {
        setNewFeedImageDownloadUrl()
        original!!.updateFromOther(changedFeed!!)
        feedImageWasUpdated()
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateFromOther_feedImageRemoved() {
        feedImageRemoved()
        original!!.updateFromOther(changedFeed!!)
        feedImageWasNotUpdated()
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateFromOther_feedImageAdded() {
        feedHadNoImage()
        setNewFeedImageDownloadUrl()
        original!!.updateFromOther(changedFeed!!)
        feedImageWasUpdated()
    }

    @Test
    @Throws(Exception::class)
    fun testSetSortOrder_OnlyIntraFeedSortAllowed() {
        for (sortOrder in SortOrder.entries) {
            if (sortOrder.scope == SortOrder.Scope.INTRA_FEED) {
                original!!.sortOrder = sortOrder // should be okay
            } else {
                try {
                    original!!.sortOrder = sortOrder
                    Assert.fail("SortOrder $sortOrder should not be allowed on a feed")
                } catch (iae: IllegalArgumentException) {
                    // expected exception
                }
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testSetSortOrder_NullAllowed() {
        original!!.sortOrder = null // should be okay
    }

    private fun feedHasNotChanged() {
        Assert.assertFalse(original!!.compareWithOther(changedFeed!!))
    }

    private fun feedHadNoImage() {
        original!!.imageUrl =(null)
    }

    private fun setNewFeedImageDownloadUrl() {
        changedFeed!!.imageUrl =("http://example.com/new_picture")
    }

    private fun feedHasChanged() {
        Assert.assertTrue(original!!.compareWithOther(changedFeed!!))
    }

    private fun feedImageRemoved() {
        changedFeed!!.imageUrl =(null)
    }

    private fun feedImageWasUpdated() {
        assertEquals(original!!.imageUrl, changedFeed!!.imageUrl)
    }

    private fun feedImageWasNotUpdated() {
        assertEquals(anyFeed().imageUrl, original!!.imageUrl)
    }
}