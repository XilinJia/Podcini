package de.danoeh.antennapod.core.storage

import android.R.attr
import de.danoeh.antennapod.model.feed.Feed
import kotlin.math.abs


class NavDrawerData(@JvmField val items: List<DrawerItem>,
                    @JvmField val queueSize: Int,
                    @JvmField val numNewItems: Int,
                    val numDownloadedItems: Int,
                    val feedCounters: Map<Long, Int>,
                    val reclaimableSpace: Int
) {
    abstract class DrawerItem(val type: Type, var id: Long) {
        enum class Type {
            TAG, FEED
        }

        var layer: Int = 0

        abstract val title: String?

        abstract val counter: Int
    }

    // Keep IDs >0 but make room for many feeds
    class TagDrawerItem(val name: String) : DrawerItem(Type.TAG, abs(attr.name.hashCode().toLong()) shl 20) {
        val children: MutableList<DrawerItem> = ArrayList()
        var isOpen: Boolean = false

        override val title: String
            get() = name

        override val counter: Int
            get() {
                var sum = 0
                for (item in children) {
                    sum += item.counter
                }
                return sum
            }
    }

    class FeedDrawerItem(val feed: Feed, id: Long, override val counter: Int) : DrawerItem(Type.FEED, id) {
        override val title: String?
            get() = feed.title
    }
}
