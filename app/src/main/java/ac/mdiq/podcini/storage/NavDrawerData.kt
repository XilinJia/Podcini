package ac.mdiq.podcini.storage

import ac.mdiq.podcini.storage.model.feed.Feed

class NavDrawerData(@JvmField val items: List<DrawerItem>,
                    @JvmField val queueSize: Int,
                    @JvmField val numNewItems: Int,
                    val numDownloadedItems: Int,
                    val feedCounters: Map<Long, Int>,
                    val reclaimableSpace: Int
) {
    abstract class DrawerItem(val type: Type, var id: Long) {
        enum class Type {
            FEED
        }

        var layer: Int = 0

        abstract val title: String?

        open val producer: String = ""

        abstract val counter: Int
    }

    class FeedDrawerItem(val feed: Feed, id: Long, override val counter: Int) : DrawerItem(Type.FEED, id) {
        override val title: String?
            get() = feed.title

        override val producer: String
            get() = feed.author?:""
    }
}
