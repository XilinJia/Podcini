package ac.mdiq.podcini.feed

import ac.mdiq.podcini.storage.utils.DurationConverter.durationStringShortToMs
import ac.mdiq.podcini.storage.model.FeedAutoDownloadFilter
import ac.mdiq.podcini.storage.model.Episode
import org.junit.Assert
import org.junit.Test

class FeedAutoDownloadFilterTest {
    @Test
    fun testNullFilter() {
        val filter = FeedAutoDownloadFilter()
        val item = Episode()
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
        val filter = FeedAutoDownloadFilter(includeFilter, "")
        val item = Episode()
        item.title = ("Hello world")

        val item2 = Episode()
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
        val filter = FeedAutoDownloadFilter("", excludeFilter)
        val item = Episode()
        item.title = ("Hello world")

        val item2 = Episode()
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
        val filter = FeedAutoDownloadFilter(includeFilter, "")
        val item = Episode()
        item.title = ("hello world")

        val item2 = Episode()
        item2.title = ("Two three words")

        val item3 = Episode()
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
        val filter = FeedAutoDownloadFilter("", excludeFilter)
        val item = Episode()
        item.title = ("hello world")

        val item2 = Episode()
        item2.title = ("One three words")

        val item3 = Episode()
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
        val filter = FeedAutoDownloadFilter(includeFilter, excludeFilter)

        val download = Episode()
        download.title = ("Hello everyone!")
        // because, while it has words from the include filter it also has exclude words
        val doNotDownload = Episode()
        doNotDownload.title = ("I dislike the world")
        // because it has no words from the include filter
        val doNotDownload2 = Episode()
        doNotDownload2.title = ("no words to include")

        Assert.assertTrue(filter.hasExcludeFilter())
        Assert.assertTrue(filter.hasIncludeFilter())
        Assert.assertTrue(filter.shouldAutoDownload(download))
        Assert.assertFalse(filter.shouldAutoDownload(doNotDownload))
        Assert.assertFalse(filter.shouldAutoDownload(doNotDownload2))
    }

    @Test
    fun testMinimalDurationFilter() {
        val download = Episode()
        download.title = ("Hello friend!")
        val downloadMedia = FeedMediaMother.anyFeedMedia()
        downloadMedia.setDuration(durationStringShortToMs("05:00", false))
        download.setMedia(downloadMedia)
        // because duration of the media in unknown
        val download2 = Episode()
        download2.title = ("Hello friend!")
        val unknownDurationMedia = FeedMediaMother.anyFeedMedia()
        download2.setMedia(unknownDurationMedia)
        // because it is not long enough
        val doNotDownload = Episode()
        doNotDownload.title = ("Hello friend!")
        val doNotDownloadMedia = FeedMediaMother.anyFeedMedia()
        doNotDownloadMedia.setDuration(durationStringShortToMs("02:00", false))
        doNotDownload.setMedia(doNotDownloadMedia)

        val minimalDurationFilter = 3 * 60
        val filter = FeedAutoDownloadFilter("", "", minimalDurationFilter)

        Assert.assertTrue(filter.hasMinimalDurationFilter())
        Assert.assertTrue(filter.shouldAutoDownload(download))
        Assert.assertFalse(filter.shouldAutoDownload(doNotDownload))
        Assert.assertTrue(filter.shouldAutoDownload(download2))
    }
}
