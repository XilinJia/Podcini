package ac.mdiq.podcini.util.comparator

import ac.mdiq.podcini.storage.model.feed.FeedItem

class PlaybackLastPlayedDateComparator : Comparator<FeedItem> {
    override fun compare(lhs: FeedItem, rhs: FeedItem): Int {
        if (lhs.media?.getLastPlayedTime() != null && rhs.media?.getLastPlayedTime() != null)
            return rhs.media!!.getLastPlayedTime().compareTo(lhs.media!!.getLastPlayedTime())

        return 0
    }
}
