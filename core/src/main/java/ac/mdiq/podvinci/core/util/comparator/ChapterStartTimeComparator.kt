package ac.mdiq.podvinci.core.util.comparator

import ac.mdiq.podvinci.model.feed.Chapter

class ChapterStartTimeComparator : Comparator<Chapter> {
    override fun compare(lhs: Chapter, rhs: Chapter): Int {
        return java.lang.Long.compare(lhs.start, rhs.start)
    }
}
