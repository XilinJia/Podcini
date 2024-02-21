package ac.mdiq.podcini.core.util.comparator

import ac.mdiq.podcini.model.feed.Chapter

class ChapterStartTimeComparator : Comparator<Chapter> {
    override fun compare(lhs: Chapter, rhs: Chapter): Int {
        return lhs.start.compareTo(rhs.start)
    }
}
