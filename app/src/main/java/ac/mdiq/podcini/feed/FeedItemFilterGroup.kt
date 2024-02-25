package ac.mdiq.podcini.feed

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter

enum class FeedItemFilterGroup(vararg values: ItemProperties) {
    PLAYED(ItemProperties(R.string.hide_played_episodes_label, FeedItemFilter.PLAYED),
        ItemProperties(R.string.not_played, FeedItemFilter.UNPLAYED)),
    PAUSED(ItemProperties(R.string.hide_paused_episodes_label, FeedItemFilter.PAUSED),
        ItemProperties(R.string.not_paused, FeedItemFilter.NOT_PAUSED)),
    FAVORITE(ItemProperties(R.string.hide_is_favorite_label, FeedItemFilter.IS_FAVORITE),
        ItemProperties(R.string.not_favorite, FeedItemFilter.NOT_FAVORITE)),
    MEDIA(ItemProperties(R.string.has_media, FeedItemFilter.HAS_MEDIA),
        ItemProperties(R.string.no_media, FeedItemFilter.NO_MEDIA)),
    QUEUED(ItemProperties(R.string.queued_label, FeedItemFilter.QUEUED),
        ItemProperties(R.string.not_queued_label, FeedItemFilter.NOT_QUEUED)),
    DOWNLOADED(ItemProperties(R.string.hide_downloaded_episodes_label, FeedItemFilter.DOWNLOADED),
        ItemProperties(R.string.hide_not_downloaded_episodes_label, FeedItemFilter.NOT_DOWNLOADED));

    @JvmField
    val values: Array<ItemProperties>

    init {
        this.values = values as Array<ItemProperties>
    }

    class ItemProperties(@JvmField val displayName: Int, @JvmField val filterId: String)
}
