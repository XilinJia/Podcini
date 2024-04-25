package ac.mdiq.podcini.util.event

import ac.mdiq.podcini.storage.model.feed.FeedItem

class QueueEvent private constructor(@JvmField val action: Action,
                                     @JvmField val item: FeedItem?,
                                     @JvmField val items: List<FeedItem>,
                                     @JvmField val position: Int) {

    enum class Action {
        ADDED, ADDED_ITEMS, SET_QUEUE, REMOVED, IRREVERSIBLE_REMOVED, CLEARED, DELETED_MEDIA, SORTED, MOVED
    }

    companion object {
        @JvmStatic
        fun added(item: FeedItem, position: Int): QueueEvent {
            return QueueEvent(Action.ADDED, item, listOf(), position)
        }

        @JvmStatic
        fun setQueue(queue: List<FeedItem>): QueueEvent {
            return QueueEvent(Action.SET_QUEUE, null, queue, -1)
        }

        @JvmStatic
        fun removed(item: FeedItem): QueueEvent {
            return QueueEvent(Action.REMOVED, item, listOf(), -1)
        }

        @JvmStatic
        fun irreversibleRemoved(item: FeedItem): QueueEvent {
            return QueueEvent(Action.IRREVERSIBLE_REMOVED, item, listOf(), -1)
        }

        @JvmStatic
        fun cleared(): QueueEvent {
            return QueueEvent(Action.CLEARED, null, listOf(), -1)
        }

        @JvmStatic
        fun sorted(sortedQueue: List<FeedItem>): QueueEvent {
            return QueueEvent(Action.SORTED, null, sortedQueue, -1)
        }

        @JvmStatic
        fun moved(item: FeedItem, newPosition: Int): QueueEvent {
            return QueueEvent(Action.MOVED, item, listOf(), newPosition)
        }
    }
}
