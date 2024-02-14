package de.danoeh.antennapod.event

import de.danoeh.antennapod.model.feed.FeedItem


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
