package de.danoeh.antennapod.core.storage

import de.danoeh.antennapod.core.storage.FeedItemDuplicateGuesser.seemDuplicates
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import org.junit.Assert
import org.junit.Test
import java.util.*

/**
 * Test class for [FeedItemDuplicateGuesser].
 */
class FeedItemDuplicateGuesserTest {
    @Test
    fun testSameId() {
        Assert.assertTrue(seemDuplicates(
            item("id", "Title1", "example.com/episode1", 0, 5 * MINUTES, "audio/*"),
            item("id", "Title2", "example.com/episode2", 0, 20 * MINUTES, "video/*")))
    }

    @Test
    fun testDuplicateDownloadUrl() {
        Assert.assertTrue(seemDuplicates(
            item("id1", "Title1", "example.com/episode", 0, 5 * MINUTES, "audio/*"),
            item("id2", "Title2", "example.com/episode", 0, 5 * MINUTES, "audio/*")))
        Assert.assertFalse(seemDuplicates(
            item("id1", "Title1", "example.com/episode1", 0, 5 * MINUTES, "audio/*"),
            item("id2", "Title2", "example.com/episode2", 0, 5 * MINUTES, "audio/*")))
    }

    @Test
    fun testOtherAttributes() {
        Assert.assertTrue(seemDuplicates(
            item("id1", "Title", "example.com/episode1", 10, 5 * MINUTES, "audio/*"),
            item("id2", "Title", "example.com/episode2", 10, 5 * MINUTES, "audio/*")))
        Assert.assertTrue(seemDuplicates(
            item("id1", "Title", "example.com/episode1", 10, 5 * MINUTES, "audio/*"),
            item("id2", "Title", "example.com/episode2", 20, 6 * MINUTES, "audio/*")))
        Assert.assertFalse(seemDuplicates(
            item("id1", "Title", "example.com/episode1", 10, 5 * MINUTES, "audio/*"),
            item("id2", "Title", "example.com/episode2", 10, 5 * MINUTES, "video/*")))
        Assert.assertTrue(seemDuplicates(
            item("id1", "Title", "example.com/episode1", 10, 5 * MINUTES, "audio/mpeg"),
            item("id2", "Title", "example.com/episode2", 10, 5 * MINUTES, "audio/mp3")))
        Assert.assertFalse(seemDuplicates(
            item("id1", "Title", "example.com/episode1", 5 * DAYS, 5 * MINUTES, "audio/*"),
            item("id2", "Title", "example.com/episode2", 2 * DAYS, 5 * MINUTES, "audio/*")))
    }

    @Test
    fun testNoMediaType() {
        Assert.assertTrue(seemDuplicates(
            item("id1", "Title", "example.com/episode1", 2 * DAYS, 5 * MINUTES, ""),
            item("id2", "Title", "example.com/episode2", 2 * DAYS, 5 * MINUTES, "")))
    }

    private fun item(guid: String, title: String, downloadUrl: String,
                     date: Long, duration: Long, mime: String
    ): FeedItem {
        val item = FeedItem(0, title, guid, "link", Date(date), FeedItem.PLAYED, null)
        val media = FeedMedia(item, downloadUrl, duration, mime)
        item.setMedia(media)
        return item
    }

    companion object {
        private const val MINUTES = (1000 * 60).toLong()
        private const val DAYS = 24 * 60 * MINUTES
    }
}