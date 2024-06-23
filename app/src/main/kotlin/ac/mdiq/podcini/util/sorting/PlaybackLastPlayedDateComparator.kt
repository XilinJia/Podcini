package ac.mdiq.podcini.util.sorting

import ac.mdiq.podcini.storage.model.Episode

class PlaybackLastPlayedDateComparator : Comparator<Episode> {
    override fun compare(lhs: Episode, rhs: Episode): Int {
        if (lhs.media?.getLastPlayedTime() != null && rhs.media?.getLastPlayedTime() != null)
            return rhs.media!!.getLastPlayedTime().compareTo(lhs.media!!.getLastPlayedTime())

        return 0
    }
}
