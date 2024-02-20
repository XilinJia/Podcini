package ac.mdiq.podcini.event

import ac.mdiq.podcini.model.feed.FeedItem


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
