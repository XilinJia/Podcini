package de.danoeh.antennapod.core.util.comparator

import de.danoeh.antennapod.model.feed.Chapter

class ChapterStartTimeComparator : Comparator<Chapter> {
    override fun compare(lhs: Chapter, rhs: Chapter): Int {
        return java.lang.Long.compare(lhs.start, rhs.start)
    }
}
