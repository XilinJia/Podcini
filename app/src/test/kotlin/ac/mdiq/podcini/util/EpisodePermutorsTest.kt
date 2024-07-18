package ac.mdiq.podcini.util

import ac.mdiq.podcini.storage.utils.EpisodesPermutors.getPermutor
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import org.junit.Assert
import org.junit.Test
import java.util.*

/**
 * Test class for FeedItemPermutors.
 */
class EpisodePermutorsTest {
    @Test
    fun testEnsureNonNullPermutors() {
        for (sortOrder in EpisodeSortOrder.entries) {
            Assert.assertNotNull("The permutor for SortOrder $sortOrder is unexpectedly null",
                getPermutor(sortOrder))
        }
    }

    @Test
    fun testPermutorForRule_EPISODE_TITLE_ASC() {
        val permutor = getPermutor(EpisodeSortOrder.EPISODE_TITLE_A_Z)

        val itemList = testList
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 1, 2, 3)) // after sorting
    }

    @Test
    fun testPermutorForRule_EPISODE_TITLE_ASC_NullTitle() {
        val permutor = getPermutor(EpisodeSortOrder.EPISODE_TITLE_A_Z)

        val itemList = testList.toMutableList()
        itemList[2].title = (null)
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 2, 1, 3)) // after sorting
    }


    @Test
    fun testPermutorForRule_EPISODE_TITLE_DESC() {
        val permutor = getPermutor(EpisodeSortOrder.EPISODE_TITLE_Z_A)

        val itemList = testList
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 3, 2, 1)) // after sorting
    }

    @Test
    fun testPermutorForRule_DATE_ASC() {
        val permutor = getPermutor(EpisodeSortOrder.DATE_OLD_NEW)

        val itemList = testList
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 1, 2, 3)) // after sorting
    }

    @Test
    fun testPermutorForRule_DATE_ASC_NulPubDatel() {
        val permutor = getPermutor(EpisodeSortOrder.DATE_OLD_NEW)

        val itemList = testList
        itemList[2] // itemId 2
            .setPubDate(null)
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 2, 1, 3)) // after sorting
    }

    @Test
    fun testPermutorForRule_DATE_DESC() {
        val permutor = getPermutor(EpisodeSortOrder.DATE_NEW_OLD)

        val itemList = testList
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 3, 2, 1)) // after sorting
    }

    @Test
    fun testPermutorForRule_DURATION_ASC() {
        val permutor = getPermutor(EpisodeSortOrder.DURATION_SHORT_LONG)

        val itemList = testList
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 1, 2, 3)) // after sorting
    }

    @Test
    fun testPermutorForRule_DURATION_DESC() {
        val permutor = getPermutor(EpisodeSortOrder.DURATION_LONG_SHORT)

        val itemList = testList
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 3, 2, 1)) // after sorting
    }

    @Test
    fun testPermutorForRule_size_asc() {
        val permutor = getPermutor(EpisodeSortOrder.SIZE_SMALL_LARGE)

        val itemList = testList
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 1, 2, 3)) // after sorting
    }

    @Test
    fun testPermutorForRule_size_desc() {
        val permutor = getPermutor(EpisodeSortOrder.SIZE_LARGE_SMALL)

        val itemList = testList
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 3, 2, 1)) // after sorting
    }

    @Test
    fun testPermutorForRule_DURATION_DESC_NullMedia() {
        val permutor = getPermutor(EpisodeSortOrder.DURATION_LONG_SHORT)

        val itemList = testList
        itemList[1] // itemId 3
            .setMedia(null)
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 2, 1, 3)) // after sorting
    }

    @Test
    fun testPermutorForRule_FEED_TITLE_ASC() {
        val permutor = getPermutor(EpisodeSortOrder.FEED_TITLE_A_Z)

        val itemList = testList
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 1, 2, 3)) // after sorting
    }

    @Test
    fun testPermutorForRule_FEED_TITLE_DESC() {
        val permutor = getPermutor(EpisodeSortOrder.FEED_TITLE_Z_A)

        val itemList = testList
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 3, 2, 1)) // after sorting
    }

    @Test
    fun testPermutorForRule_FEED_TITLE_DESC_NullTitle() {
        val permutor = getPermutor(EpisodeSortOrder.FEED_TITLE_Z_A)

        val itemList = testList
        itemList[1].feed!!.title = (null)
        Assert.assertTrue(checkIdOrder(itemList, 1, 3, 2)) // before sorting
        permutor.reorder(itemList)
        Assert.assertTrue(checkIdOrder(itemList, 2, 1, 3)) // after sorting
    }

    private val testList: MutableList<Episode>
        /**
         * Generates a list with test data.
         */
        get() {
            val itemList: MutableList<Episode> = ArrayList()

            val calendar = Calendar.getInstance()
            calendar[2019, 0] = 1 // January 1st
            val feed1 = Feed(null, null, "Feed title 1")
            val episode1 = Episode(1, "Title 1", null, null, calendar.time, 0, feed1)
            val episodeMedia1 = EpisodeMedia(0, episode1, 1000, 0, 100, null, null, null, true, null, 0, 0)
            episode1.setMedia(episodeMedia1)
            itemList.add(episode1)

            calendar[2019, 2] = 1 // March 1st
            val feed2 = Feed(null, null, "Feed title 3")
            val episode2 = Episode(3, "Title 3", null, null, calendar.time, 0, feed2)
            val episodeMedia2 = EpisodeMedia(0, episode2, 3000, 0, 300, null, null, null, true, null, 0, 0)
            episode2.setMedia(episodeMedia2)
            itemList.add(episode2)

            calendar[2019, 1] = 1 // February 1st
            val feed3 = Feed(null, null, "Feed title 2")
            val episode3 = Episode(2, "Title 2", null, null, calendar.time, 0, feed3)
            val episodeMedia3 = EpisodeMedia(0, episode3, 2000, 0, 200, null, null, null, true, null, 0, 0)
            episode3.setMedia(episodeMedia3)
            itemList.add(episode3)

            return itemList
        }

    /**
     * Checks if both lists have the same size and the same ID order.
     *
     * @param itemList Item list.
     * @param ids      List of IDs.
     * @return `true` if both lists have the same size and the same ID order.
     */
    private fun checkIdOrder(itemList: List<Episode>, vararg ids: Long): Boolean {
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
