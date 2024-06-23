package ac.mdiq.podcini.util.sorting

import ac.mdiq.podcini.storage.model.Chapter

class ChapterStartTimeComparator : Comparator<Chapter> {
    override fun compare(lhs: Chapter, rhs: Chapter): Int {
        return lhs.start.compareTo(rhs.start)
    }
}
