package ac.mdiq.podcini.core.util.comparator

import ac.mdiq.podcini.model.feed.FeedItem

class PlaybackCompletionDateComparator : Comparator<FeedItem> {
    override fun compare(lhs: FeedItem, rhs: FeedItem): Int {
        if (lhs.media?.getPlaybackCompletionDate() != null && rhs.media?.getPlaybackCompletionDate() != null) {
            return rhs.media!!.getPlaybackCompletionDate()!!.compareTo(lhs.media!!.getPlaybackCompletionDate())
        }
        return 0
    }
}
