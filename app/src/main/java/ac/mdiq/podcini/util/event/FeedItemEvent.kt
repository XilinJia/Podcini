package ac.mdiq.podcini.util.event

import ac.mdiq.podcini.storage.model.feed.FeedItem


// TODO: this appears not being posted
class FeedItemEvent(@JvmField val items: List<FeedItem>) {
    companion object {
        fun updated(items: List<FeedItem>): FeedItemEvent {
            return FeedItemEvent(items)
        }

        @JvmStatic
        fun updated(vararg items: FeedItem): FeedItemEvent {
            return FeedItemEvent(listOf(*items))
        }
    }
}
