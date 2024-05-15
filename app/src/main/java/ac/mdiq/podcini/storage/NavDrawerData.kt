package ac.mdiq.podcini.storage

import ac.mdiq.podcini.storage.model.feed.Feed

class NavDrawerData(@JvmField val items: List<FeedDrawerItem>,
                    @JvmField val queueSize: Int,
                    @JvmField val numNewItems: Int,
                    val numDownloadedItems: Int,
                    val feedCounters: Map<Long, Int>,
                    val reclaimableSpace: Int,
                    @JvmField val numItems: Int,
                    @JvmField val numFeeds: Int) {

    class FeedDrawerItem(val feed: Feed, val id: Long, val counter: Int) {
         var layer: Int = 0

         val title: String?
            get() = feed.title

         val producer: String
            get() = feed.author?:""
    }
}
