package ac.mdiq.podcini.storage

import ac.mdiq.podcini.util.comparator.FeedItemPubdateComparator
import ac.mdiq.podcini.storage.model.feed.Chapter
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.storage.database.PodDBAdapter.Companion.getInstance
import org.junit.Assert
import java.util.*

/**
 * Utility methods for DB* tests.
 */
internal object DbTestUtils {
    /**
     * Use this method when tests involve chapters.
     */
    /**
     * Use this method when tests don't involve chapters.
     */
    @JvmOverloads
    fun saveFeedlist(numFeeds: Int, numItems: Int, withMedia: Boolean,
                     withChapters: Boolean = false, numChapters: Int = 0
    ): List<Feed> {
        require(numFeeds > 0) { "numFeeds<=0" }
        require(numItems >= 0) { "numItems<0" }

        val feeds: MutableList<Feed> = ArrayList()
        val adapter = getInstance()
        adapter.open()
        for (i in 0 until numFeeds) {
            val f = Feed(0, null, "feed $i", "link$i", "descr", null, null,
                null, null, "id$i", null, null, "url$i", false)
            f.items = mutableListOf()
            for (j in 0 until numItems) {
                val item = FeedItem(0, "item $j", "id$j", "link$j", Date(),
                    FeedItem.PLAYED, f, withChapters)
                if (withMedia) {
                    val media = FeedMedia(item, "url$j", 1, "audio/mp3")
                    item.setMedia(media)
                }
                if (withChapters) {
                    val chapters: MutableList<Chapter> = ArrayList()
                    item.chapters = (chapters)
                    for (k in 0 until numChapters) {
                        chapters.add(Chapter(k.toLong(), "item $j chapter $k",
                            "http://example.com", "http://example.com/image.png"))
                    }
                }
                f.items.add(item)
            }
            f.items.sortWith(FeedItemPubdateComparator())
            adapter.setCompleteFeed(f)
            Assert.assertTrue(f.id != 0L)
            for (item in f.items) {
                Assert.assertTrue(item.id != 0L)
            }
            feeds.add(f)
        }
        adapter.close()

        return feeds
    }
}
