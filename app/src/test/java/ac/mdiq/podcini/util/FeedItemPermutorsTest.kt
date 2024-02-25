package ac.mdiq.podcini.util

import ac.mdiq.podcini.util.FeedItemPermutors.getPermutor
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.storage.model.feed.SortOrder
import org.junit.Assert
import org.junit.Test
import java.util.*

/**
 * Test class for FeedItemPermutors.
 */
class FeedItemPermutorsTest {
    @Test
    fun testEnsureNonNullPermutors() {
        for (sortOrder in SortOrder.entries) {
            Assert.assertNotNull("The permutor for SortOrder $sortOrder is unexpectedly null",
                getPermutor(sortOrder))
        }
    }

    @Test
    fun testPermutorForRule_EPISODE_TITLE_ASC() {
        val permutor = getPermutor(SortOrder.EPISODE_TITLE_A_Z)

        val itemList = testList
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 1, 2, 3)) // after sorting
    }

    @Test
    fun testPermutorForRule_EPISODE_TITLE_ASC_NullTitle() {
        val permutor = getPermutor(SortOrder.EPISODE_TITLE_A_Z)

        val itemList = testList.toMutableList()
        itemList[2].title = (null)
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 2, 1, 3)) // after sorting
    }


    @Test
    fun testPermutorForRule_EPISODE_TITLE_DESC() {
        val permutor = getPermutor(SortOrder.EPISODE_TITLE_Z_A)

        val itemList = testList
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 3, 2, 1)) // after sorting
    }

    @Test
    fun testPermutorForRule_DATE_ASC() {
        val permutor = getPermutor(SortOrder.DATE_OLD_NEW)

        val itemList = testList
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 1, 2, 3)) // after sorting
    }

    @Test
    fun testPermutorForRule_DATE_ASC_NulPubDatel() {
        val permutor = getPermutor(SortOrder.DATE_OLD_NEW)

        val itemList = testList
        itemList[2] // itemId 2
            .setPubDate(null)
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 2, 1, 3)) // after sorting
    }

    @Test
    fun testPermutorForRule_DATE_DESC() {
        val permutor = getPermutor(SortOrder.DATE_NEW_OLD)

        val itemList = testList
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 3, 2, 1)) // after sorting
    }

    @Test
    fun testPermutorForRule_DURATION_ASC() {
        val permutor = getPermutor(SortOrder.DURATION_SHORT_LONG)

        val itemList = testList
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 1, 2, 3)) // after sorting
    }

    @Test
    fun testPermutorForRule_DURATION_DESC() {
        val permutor = getPermutor(SortOrder.DURATION_LONG_SHORT)

        val itemList = testList
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 3, 2, 1)) // after sorting
    }

    @Test
    fun testPermutorForRule_size_asc() {
        val permutor = getPermutor(SortOrder.SIZE_SMALL_LARGE)

        val itemList = testList
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 1, 2, 3)) // after sorting
    }

    @Test
    fun testPermutorForRule_size_desc() {
        val permutor = getPermutor(SortOrder.SIZE_LARGE_SMALL)

        val itemList = testList
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 3, 2, 1)) // after sorting
    }

    @Test
    fun testPermutorForRule_DURATION_DESC_NullMedia() {
        val permutor = getPermutor(SortOrder.DURATION_LONG_SHORT)

        val itemList = testList
        itemList[1] // itemId 3
            .setMedia(null)
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 2, 1, 3)) // after sorting
    }

    @Test
    fun testPermutorForRule_FEED_TITLE_ASC() {
        val permutor = getPermutor(SortOrder.FEED_TITLE_A_Z)

        val itemList = testList
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 1, 2, 3)) // after sorting
    }

    @Test
    fun testPermutorForRule_FEED_TITLE_DESC() {
        val permutor = getPermutor(SortOrder.FEED_TITLE_Z_A)

        val itemList = testList
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 3, 2, 1)) // after sorting
    }

    @Test
    fun testPermutorForRule_FEED_TITLE_DESC_NullTitle() {
        val permutor = getPermutor(SortOrder.FEED_TITLE_Z_A)

        val itemList = testList
        itemList[1].feed!!.title = (null)
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 2, 1, 3)) // after sorting
    }

    private val testList: MutableList<FeedItem>
        /**
         * Generates a list with test data.
         */
        get() {
            val itemList: MutableList<FeedItem> = ArrayList()

            val calendar = Calendar.getInstance()
            calendar[2019, 0] = 1 // January 1st
            val feed1 = Feed(null, null, "Feed title 1")
            val feedItem1 = FeedItem(1, "Title 1", null, null, calendar.time, 0, feed1)
            val feedMedia1 = FeedMedia(0, feedItem1, 1000, 0, 100, null, null, null, true, null, 0, 0)
            feedItem1.setMedia(feedMedia1)
            itemList.add(feedItem1)

            calendar[2019, 2] = 1 // March 1st
            val feed2 = Feed(null, null, "Feed title 3")
            val feedItem2 = FeedItem(3, "Title 3", null, null, calendar.time, 0, feed2)
            val feedMedia2 = FeedMedia(0, feedItem2, 3000, 0, 300, null, null, null, true, null, 0, 0)
            feedItem2.setMedia(feedMedia2)
            itemList.add(feedItem2)

            calendar[2019, 1] = 1 // February 1st
            val feed3 = Feed(null, null, "Feed title 2")
            val feedItem3 = FeedItem(2, "Title 2", null, null, calendar.time, 0, feed3)
            val feedMedia3 = FeedMedia(0, feedItem3, 2000, 0, 200, null, null, null, true, null, 0, 0)
            feedItem3.setMedia(feedMedia3)
            itemList.add(feedItem3)

            return itemList
        }

    /**
     * Checks if both lists have the same size and the same ID order.
     *
     * @param itemList Item list.
     * @param ids      List of IDs.
     * @return `true` if both lists have the same size and the same ID order.
     */
    private fun checkIdOrder(itemList: List<FeedItem>, vararg ids: Long): Boolean {
        if (itemList.size != ids.size) {
            return false
        }

        for (i in ids.indices) {
            if (itemList[i].id != ids[i]) {
                return false
            }
        }
        return true
    }
}
