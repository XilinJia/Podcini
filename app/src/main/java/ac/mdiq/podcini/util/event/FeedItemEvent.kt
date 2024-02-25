package ac.mdiq.podcini.util.event

import ac.mdiq.podcini.storage.model.feed.FeedItem


class FeedItemEvent(@JvmField val items: List<FeedItem>) {
    companion object {
        fun updated(items: List<FeedItem>): ac.mdiq.podcini.util.event.FeedItemEvent {
            return ac.mdiq.podcini.util.event.FeedItemEvent(items)
        }

        @JvmStatic
        fun updated(vararg items: FeedItem): ac.mdiq.podcini.util.event.FeedItemEvent {
            return ac.mdiq.podcini.util.event.FeedItemEvent(listOf(*items))
        }
    }
}
