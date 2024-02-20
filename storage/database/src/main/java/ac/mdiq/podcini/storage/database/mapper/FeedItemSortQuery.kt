package ac.mdiq.podcini.storage.database.mapper

import ac.mdiq.podcini.model.feed.SortOrder
import ac.mdiq.podcini.storage.database.PodDBAdapter

object FeedItemSortQuery {
    @JvmStatic
    fun generateFrom(sortOrder: SortOrder?): String {
        var sortQuery = ""
        sortQuery = when (sortOrder) {
            SortOrder.EPISODE_TITLE_A_Z -> PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + PodDBAdapter.KEY_TITLE + " " + "ASC"
            SortOrder.EPISODE_TITLE_Z_A -> PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + PodDBAdapter.KEY_TITLE + " " + "DESC"
            SortOrder.DATE_OLD_NEW -> PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + PodDBAdapter.KEY_PUBDATE + " " + "ASC"
            SortOrder.DATE_NEW_OLD -> PodDBAdapter.TABLE_NAME_FEED_ITEMS + "." + PodDBAdapter.KEY_PUBDATE + " " + "DESC"
            SortOrder.DURATION_SHORT_LONG -> PodDBAdapter.TABLE_NAME_FEED_MEDIA + "." + PodDBAdapter.KEY_DURATION + " " + "ASC"
            SortOrder.DURATION_LONG_SHORT -> PodDBAdapter.TABLE_NAME_FEED_MEDIA + "." + PodDBAdapter.KEY_DURATION + " " + "DESC"
            SortOrder.SIZE_SMALL_LARGE -> PodDBAdapter.TABLE_NAME_FEED_MEDIA + "." + PodDBAdapter.KEY_SIZE + " " + "ASC"
            SortOrder.SIZE_LARGE_SMALL -> PodDBAdapter.TABLE_NAME_FEED_MEDIA + "." + PodDBAdapter.KEY_SIZE + " " + "DESC"
            else -> ""
        }
        return sortQuery
    }
}