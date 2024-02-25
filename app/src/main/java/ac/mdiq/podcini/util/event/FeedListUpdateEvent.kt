package ac.mdiq.podcini.util.event

import ac.mdiq.podcini.storage.model.feed.Feed

class FeedListUpdateEvent {
    private val feeds: MutableList<Long> = ArrayList()

    constructor(feeds: List<Feed>) {
        for (feed in feeds) {
            this.feeds.add(feed.id)
        }
    }

    constructor(feed: Feed) {
        feeds.add(feed.id)
    }

    constructor(feedId: Long) {
        feeds.add(feedId)
    }

    fun contains(feed: Feed): Boolean {
        return feeds.contains(feed.id)
    }
}
