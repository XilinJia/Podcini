package ac.mdiq.podcini.util

import ac.mdiq.podcini.storage.utils.EpisodeUtil.getIdList
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class EpisodeUtilTest(private val msg: String,
                      private val feedLink: String,
                      private val itemLink: String,
                      private val expected: String
) {
    // Test the getIds() method
    @Test
    fun testGetIds() {
        val feedItemsList: MutableList<Episode> = ArrayList(5)
        val idList: MutableList<Int> = ArrayList()

        idList.add(980)
        idList.add(324)
        idList.add(226)
        idList.add(164)
        idList.add(854)

        for (i in 0..4) {
            val item = createFeedItem(feedLink, itemLink)
            item.id = idList[i].toLong()
            feedItemsList.add(item)
        }

        val actual = getIdList(feedItemsList)

        // covers edge case for getIds() method
        val emptyList: List<Episode> = ArrayList()
        val testEmptyList = getIdList(emptyList)
        Assert.assertEquals(msg, 0, testEmptyList.size.toLong())
        Assert.assertEquals(msg, 980, actual[0])
        Assert.assertEquals(msg, 324, actual[1])
        Assert.assertEquals(msg, 226, actual[2])
        Assert.assertEquals(msg, 164, actual[3])
        Assert.assertEquals(msg, 854, actual[4])
    }

    // Tests the Null value for getLinkWithFallback() method
    @Test
    fun testLinkWithFallbackNullValue() {
        val actual = null
        Assert.assertEquals(msg, null, actual)
    }

    @Test
    fun testLinkWithFallback() {
        val actual = createFeedItem(feedLink, itemLink).getLinkWithFallback()
        Assert.assertEquals(msg, expected, actual)
    }

    companion object {
        private const val FEED_LINK = "http://example.com"
        private const val ITEM_LINK = "http://example.com/feedItem1"

        @Parameterized.Parameters
        fun data(): Collection<Array<Any?>> {
            return listOf(arrayOf("average", FEED_LINK, ITEM_LINK, ITEM_LINK),
                arrayOf("null item link - fallback to feed", FEED_LINK, null, FEED_LINK),
                arrayOf("empty item link - same as null", FEED_LINK, "", FEED_LINK),
                arrayOf("blank item link - same as null", FEED_LINK, "  ", FEED_LINK),
                arrayOf("fallback, but feed link is null too", null, null, null),
                arrayOf("fallback - but empty feed link - same as null", "", null, null),
                arrayOf("fallback - but blank feed link - same as null", "  ", null, null))
        }

        private fun createFeedItem(feedLink: String, itemLink: String): Episode {
            val feed = Feed()
            feed.link = (feedLink)
            val episode = Episode()
            episode.link = (itemLink)
            episode.feed = (feed)
            feed.episodes.add(episode)
            return episode
        }
    }
}
