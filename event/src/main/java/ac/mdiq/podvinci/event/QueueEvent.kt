package ac.mdiq.podvinci.event

import ac.mdiq.podvinci.model.feed.FeedItem

class QueueEvent private constructor(@JvmField val action: Action,
                                     @JvmField val item: FeedItem?,
                                     @JvmField val items: List<FeedItem>?,
                                     @JvmField val position: Int
) {
    enum class Action {
        ADDED, ADDED_ITEMS, SET_QUEUE, REMOVED, IRREVERSIBLE_REMOVED, CLEARED, DELETED_MEDIA, SORTED, MOVED
    }


    companion object {
        @JvmStatic
        fun added(item: FeedItem?, position: Int): QueueEvent {
            return QueueEvent(Action.ADDED, item, null, position)
        }

        @JvmStatic
        fun setQueue(queue: List<FeedItem>?): QueueEvent {
            return QueueEvent(Action.SET_QUEUE, null, queue, -1)
        }

        @JvmStatic
        fun removed(item: FeedItem?): QueueEvent {
            return QueueEvent(Action.REMOVED, item, null, -1)
        }

        @JvmStatic
        fun irreversibleRemoved(item: FeedItem?): QueueEvent {
            return QueueEvent(Action.IRREVERSIBLE_REMOVED, item, null, -1)
        }

        @JvmStatic
        fun cleared(): QueueEvent {
            return QueueEvent(Action.CLEARED, null, null, -1)
        }

        @JvmStatic
        fun sorted(sortedQueue: List<FeedItem>?): QueueEvent {
            return QueueEvent(Action.SORTED, null, sortedQueue, -1)
        }

        @JvmStatic
        fun moved(item: FeedItem?, newPosition: Int): QueueEvent {
            return QueueEvent(Action.MOVED, item, null, newPosition)
        }
    }
}
