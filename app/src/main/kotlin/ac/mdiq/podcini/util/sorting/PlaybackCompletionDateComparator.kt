package ac.mdiq.podcini.util.sorting

import ac.mdiq.podcini.storage.model.Episode

class PlaybackCompletionDateComparator : Comparator<Episode> {
    override fun compare(lhs: Episode, rhs: Episode): Int {
        if (lhs.media?.playbackCompletionDate != null && rhs.media?.playbackCompletionDate != null)
            return rhs.media!!.playbackCompletionDate!!.compareTo(lhs.media!!.playbackCompletionDate)

        return 0
    }
}
