package de.danoeh.antennapod.core.feed

import de.danoeh.antennapod.core.util.Converter.durationStringShortToMs
import de.danoeh.antennapod.model.feed.FeedFilter
import de.danoeh.antennapod.model.feed.FeedItem
import org.junit.Assert
import org.junit.Test

class FeedFilterTest {
    @Test
    fun testNullFilter() {
        val filter = FeedFilter()
        val item = FeedItem()
        item.title = ("Hello world")

        Assert.assertFalse(filter.excludeOnly())
        Assert.assertFalse(filter.includeOnly())
        Assert.assertEquals("", filter.excludeFilterRaw)
        Assert.assertEquals("", filter.includeFilterRaw)
        Assert.assertTrue(filter.shouldAutoDownload(item))
    }

    @Test
    fun testBasicIncludeFilter() {
        val includeFilter = "Hello"
        val filter = FeedFilter(includeFilter, "")
        val item = FeedItem()
        item.title = ("Hello world")

        val item2 = FeedItem()
        item2.title = ("Don't include me")

        Assert.assertFalse(filter.excludeOnly())
        Assert.assertTrue(filter.includeOnly())
        Assert.assertEquals("", filter.excludeFilterRaw)
        Assert.assertEquals(includeFilter, filter.includeFilterRaw)
        Assert.assertTrue(filter.shouldAutoDownload(item))
        Assert.assertFalse(filter.shouldAutoDownload(item2))
    }

    @Test
    fun testBasicExcludeFilter() {
        val excludeFilter = "Hello"
        val filter = FeedFilter("", excludeFilter)
        val item = FeedItem()
        item.title = ("Hello world")

        val item2 = FeedItem()
        item2.title = ("Item2")

        Assert.assertTrue(filter.excludeOnly())
        Assert.assertFalse(filter.includeOnly())
        Assert.assertEquals(excludeFilter, filter.excludeFilterRaw)
        Assert.assertEquals("", filter.includeFilterRaw)
        Assert.assertFalse(filter.shouldAutoDownload(item))
        Assert.assertTrue(filter.shouldAutoDownload(item2))
    }

    @Test
    fun testComplexIncludeFilter() {
        val includeFilter = "Hello \n\"Two words\""
        val filter = FeedFilter(includeFilter, "")
        val item = FeedItem()
        item.title = ("hello world")

        val item2 = FeedItem()
        item2.title = ("Two three words")

        val item3 = FeedItem()
        item3.title = ("One two words")

        Assert.assertFalse(filter.excludeOnly())
        Assert.assertTrue(filter.includeOnly())
        Assert.assertEquals("", filter.excludeFilterRaw)
        Assert.assertEquals(includeFilter, filter.includeFilterRaw)
        Assert.assertTrue(filter.shouldAutoDownload(item))
        Assert.assertFalse(filter.shouldAutoDownload(item2))
        Assert.assertTrue(filter.shouldAutoDownload(item3))
    }

    @Test
    fun testComplexExcludeFilter() {
        val excludeFilter = "Hello \"Two words\""
        val filter = FeedFilter("", excludeFilter)
        val item = FeedItem()
        item.title = ("hello world")

        val item2 = FeedItem()
        item2.title = ("One three words")

        val item3 = FeedItem()
        item3.title = ("One two words")

        Assert.assertTrue(filter.excludeOnly())
        Assert.assertFalse(filter.includeOnly())
        Assert.assertEquals(excludeFilter, filter.excludeFilterRaw)
        Assert.assertEquals("", filter.includeFilterRaw)
        Assert.assertFalse(filter.shouldAutoDownload(item))
        Assert.assertTrue(filter.shouldAutoDownload(item2))
        Assert.assertFalse(filter.shouldAutoDownload(item3))
    }

    @Test
    fun testComboFilter() {
        val includeFilter = "Hello world"
        val excludeFilter = "dislike"
        val filter = FeedFilter(includeFilter, excludeFilter)

        val download = FeedItem()
        download.title = ("Hello everyone!")
        // because, while it has words from the include filter it also has exclude words
        val doNotDownload = FeedItem()
        doNotDownload.title = ("I dislike the world")
        // because it has no words from the include filter
        val doNotDownload2 = FeedItem()
        doNotDownload2.title = ("no words to include")

        Assert.assertTrue(filter.hasExcludeFilter())
        Assert.assertTrue(filter.hasIncludeFilter())
        Assert.assertTrue(filter.shouldAutoDownload(download))
        Assert.assertFalse(filter.shouldAutoDownload(doNotDownload))
        Assert.assertFalse(filter.shouldAutoDownload(doNotDownload2))
    }

    @Test
    fun testMinimalDurationFilter() {
        val download = FeedItem()
        download.title = ("Hello friend!")
        val downloadMedia = FeedMediaMother.anyFeedMedia()
        downloadMedia.setDuration(durationStringShortToMs("05:00", false))
        download.setMedia(downloadMedia)
        // because duration of the media in unknown
        val download2 = FeedItem()
        download2.title = ("Hello friend!")
        val unknownDurationMedia = FeedMediaMother.anyFeedMedia()
        download2.setMedia(unknownDurationMedia)
        // because it is not long enough
        val doNotDownload = FeedItem()
        doNotDownload.title = ("Hello friend!")
        val doNotDownloadMedia = FeedMediaMother.anyFeedMedia()
        doNotDownloadMedia.setDuration(durationStringShortToMs("02:00", false))
        doNotDownload.setMedia(doNotDownloadMedia)

        val minimalDurationFilter = 3 * 60
        val filter = FeedFilter("", "", minimalDurationFilter)

        Assert.assertTrue(filter.hasMinimalDurationFilter())
        Assert.assertTrue(filter.shouldAutoDownload(download))
        Assert.assertFalse(filter.shouldAutoDownload(doNotDownload))
        Assert.assertTrue(filter.shouldAutoDownload(download2))
    }
}
