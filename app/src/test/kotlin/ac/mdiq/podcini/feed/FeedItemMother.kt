package ac.mdiq.podcini.feed

import ac.mdiq.podcini.storage.model.Episode
import java.util.*

internal object FeedItemMother {
    private const val IMAGE_URL = "http://example.com/image"

    @JvmStatic
    fun anyFeedItemWithImage(): Episode {
        val item = Episode(0, "Item", "Item", "url", Date(), Episode.PLAYED, FeedMother.anyFeed())
        item.imageUrl = (IMAGE_URL)
        return item
    }
}
