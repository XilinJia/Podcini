package ac.mdiq.podcini.feed

import ac.mdiq.podcini.feed.FeedMediaMother.anyFeedMedia
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class FeedMediaTest {
    private var media: FeedMedia? = null

    @Before
    fun setUp() {
        media = anyFeedMedia()
    }

    /**
     * Downloading a media from a not new and not played item should not change the item state.
     */
    @Test
    fun testDownloadMediaOfNotNewAndNotPlayedItem_unchangedItemState() {
        val item = Mockito.mock(FeedItem::class.java)
        Mockito.`when`(item.isNew).thenReturn(false)
        Mockito.`when`(item.isPlayed()).thenReturn(false)

        media!!.setItem(item)
        media!!.setDownloaded(true)

        Mockito.verify(item, Mockito.never()).setNew()
        Mockito.verify(item, Mockito.never()).setPlayed(true)
        Mockito.verify(item, Mockito.never()).setPlayed(false)
    }

    /**
     * Downloading a media from a played item (thus not new) should not change the item state.
     */
    @Test
    fun testDownloadMediaOfPlayedItem_unchangedItemState() {
        val item = Mockito.mock(FeedItem::class.java)
        Mockito.`when`(item.isNew).thenReturn(false)
        Mockito.`when`(item.isPlayed()).thenReturn(true)

        media!!.setItem(item)
        media!!.setDownloaded(true)

        Mockito.verify(item, Mockito.never()).setNew()
        Mockito.verify(item, Mockito.never()).setPlayed(true)
        Mockito.verify(item, Mockito.never()).setPlayed(false)
    }

    /**
     * Downloading a media from a new item (thus not played) should change the item to not played.
     */
    @Test
    fun testDownloadMediaOfNewItem_changedToNotPlayedItem() {
        val item = Mockito.mock(FeedItem::class.java)
        Mockito.`when`(item.isNew).thenReturn(true)
        Mockito.`when`(item.isPlayed()).thenReturn(false)

        media!!.setItem(item)
        media!!.setDownloaded(true)

        Mockito.verify(item).setPlayed(false)
        Mockito.verify(item, Mockito.never()).setNew()
        Mockito.verify(item, Mockito.never()).setPlayed(true)
    }
}
