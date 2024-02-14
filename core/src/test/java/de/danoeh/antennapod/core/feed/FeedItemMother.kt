package de.danoeh.antennapod.core.feed

import de.danoeh.antennapod.model.feed.FeedItem
import java.util.*

internal object FeedItemMother {
    private const val IMAGE_URL = "http://example.com/image"

    @JvmStatic
    fun anyFeedItemWithImage(): FeedItem {
        val item = FeedItem(0, "Item", "Item", "url", Date(), FeedItem.PLAYED, FeedMother.anyFeed())
        item.imageUrl = (IMAGE_URL)
        return item
    }
}
