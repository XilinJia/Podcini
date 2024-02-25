package ac.mdiq.podcini.util.event

import ac.mdiq.podcini.storage.model.feed.FeedItem

class QueueEvent private constructor(@JvmField val action: ac.mdiq.podcini.util.event.QueueEvent.Action,
                                     @JvmField val item: FeedItem?,
                                     @JvmField val items: List<FeedItem>,
                                     @JvmField val position: Int
) {
    enum class Action {
        ADDED, ADDED_ITEMS, SET_QUEUE, REMOVED, IRREVERSIBLE_REMOVED, CLEARED, DELETED_MEDIA, SORTED, MOVED
    }


    companion object {
        @JvmStatic
        fun added(item: FeedItem, position: Int): ac.mdiq.podcini.util.event.QueueEvent {
            return ac.mdiq.podcini.util.event.QueueEvent(ac.mdiq.podcini.util.event.QueueEvent.Action.ADDED,
                item,
                listOf(),
                position)
        }

        @JvmStatic
        fun setQueue(queue: List<FeedItem>): ac.mdiq.podcini.util.event.QueueEvent {
            return ac.mdiq.podcini.util.event.QueueEvent(ac.mdiq.podcini.util.event.QueueEvent.Action.SET_QUEUE,
                null,
                queue,
                -1)
        }

        @JvmStatic
        fun removed(item: FeedItem): ac.mdiq.podcini.util.event.QueueEvent {
            return ac.mdiq.podcini.util.event.QueueEvent(ac.mdiq.podcini.util.event.QueueEvent.Action.REMOVED,
                item,
                listOf(),
                -1)
        }

        @JvmStatic
        fun irreversibleRemoved(item: FeedItem): ac.mdiq.podcini.util.event.QueueEvent {
            return ac.mdiq.podcini.util.event.QueueEvent(ac.mdiq.podcini.util.event.QueueEvent.Action.IRREVERSIBLE_REMOVED,
                item,
                listOf(),
                -1)
        }

        @JvmStatic
        fun cleared(): ac.mdiq.podcini.util.event.QueueEvent {
            return ac.mdiq.podcini.util.event.QueueEvent(ac.mdiq.podcini.util.event.QueueEvent.Action.CLEARED,
                null,
                listOf(),
                -1)
        }

        @JvmStatic
        fun sorted(sortedQueue: List<FeedItem>): ac.mdiq.podcini.util.event.QueueEvent {
            return ac.mdiq.podcini.util.event.QueueEvent(ac.mdiq.podcini.util.event.QueueEvent.Action.SORTED,
                null,
                sortedQueue,
                -1)
        }

        @JvmStatic
        fun moved(item: FeedItem, newPosition: Int): ac.mdiq.podcini.util.event.QueueEvent {
            return ac.mdiq.podcini.util.event.QueueEvent(ac.mdiq.podcini.util.event.QueueEvent.Action.MOVED,
                item,
                listOf(),
                newPosition)
        }
    }
}
