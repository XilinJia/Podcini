package ac.mdiq.podcini.feed

import ac.mdiq.podcini.feed.FeedItemMother.anyFeedItemWithImage
import ac.mdiq.podcini.storage.model.Episode
import junit.framework.TestCase.assertEquals
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat

class EpisodeTest {
    private var original: Episode? = null
    private var changedEpisode: Episode? = null

    @Before
    fun setUp() {
        original = anyFeedItemWithImage()
        changedEpisode = anyFeedItemWithImage()
    }

    @Test
    fun testUpdateFromOther_feedItemImageDownloadUrlChanged() {
        setNewFeedItemImageDownloadUrl()
        original!!.updateFromOther(changedEpisode!!)
        assertFeedItemImageWasUpdated()
    }

    @Test
    fun testUpdateFromOther_feedItemImageRemoved() {
        feedItemImageRemoved()
        original!!.updateFromOther(changedEpisode!!)
        assertFeedItemImageWasNotUpdated()
    }

    @Test
    fun testUpdateFromOther_feedItemImageAdded() {
        original!!.imageUrl = (null)
        setNewFeedItemImageDownloadUrl()
        original!!.updateFromOther(changedEpisode!!)
        assertFeedItemImageWasUpdated()
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateFromOther_dateChanged() {
        val originalDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("1952-03-11 00:00:00")
        val changedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("1952-03-11 00:42:42")
        original!!.setPubDate(originalDate)
        changedEpisode!!.setPubDate(changedDate)
        original!!.updateFromOther(changedEpisode!!)
        assertEquals(changedDate.time, original!!.getPubDate()!!.time)
    }

    /**
     * Test that a played item loses that state after being marked as new.
     */
    @Test
    fun testMarkPlayedItemAsNew_itemNotPlayed() {
        original!!.setPlayed(true)
        original!!.setNew()

        Assert.assertFalse(original!!.isPlayed())
    }

    /**
     * Test that a new item loses that state after being marked as played.
     */
    @Test
    fun testMarkNewItemAsPlayed_itemNotNew() {
        original!!.setNew()
        original!!.setPlayed(true)

        Assert.assertFalse(original!!.isNew)
    }

    /**
     * Test that a new item loses that state after being marked as not played.
     */
    @Test
    fun testMarkNewItemAsNotPlayed_itemNotNew() {
        original!!.setNew()
        original!!.setPlayed(false)

        Assert.assertFalse(original!!.isNew)
    }

    private fun setNewFeedItemImageDownloadUrl() {
        changedEpisode!!.imageUrl = ("http://example.com/new_picture")
    }

    private fun feedItemImageRemoved() {
        changedEpisode!!.imageUrl = (null)
    }

    private fun assertFeedItemImageWasUpdated() {
        assertEquals(original!!.imageUrl, changedEpisode!!.imageUrl)
    }

    private fun assertFeedItemImageWasNotUpdated() {
        assertEquals(anyFeedItemWithImage().imageUrl, original!!.imageUrl)
    }

    /**
     * If one of `description` or `content:encoded` is null, use the other one.
     */
    @Test
    fun testShownotesNullValues() {
        testShownotes(null, TEXT_LONG)
        testShownotes(TEXT_LONG, null)
    }

    /**
     * If `description` is reasonably longer than `content:encoded`, use `description`.
     */
    @Test
    fun testShownotesLength() {
        testShownotes(TEXT_SHORT, TEXT_LONG)
        testShownotes(TEXT_LONG, TEXT_SHORT)
    }

    /**
     * Checks if the shownotes equal TEXT_LONG, using the given `description` and `content:encoded`.
     *
     * @param description Description of the feed item
     * @param contentEncoded `content:encoded` of the feed item
     */
    private fun testShownotes(description: String?, contentEncoded: String?) {
        val item = Episode()
        item.setDescriptionIfLonger(description)
        item.setDescriptionIfLonger(contentEncoded)
        Assert.assertEquals(TEXT_LONG, item.description)
    }

    companion object {
        private const val TEXT_LONG = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."
        private const val TEXT_SHORT = "Lorem ipsum"
    }
}
