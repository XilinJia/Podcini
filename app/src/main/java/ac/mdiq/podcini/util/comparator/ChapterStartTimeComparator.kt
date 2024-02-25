package ac.mdiq.podcini.util.comparator

import ac.mdiq.podcini.storage.model.feed.Chapter

class ChapterStartTimeComparator : Comparator<Chapter> {
    override fun compare(lhs: Chapter, rhs: Chapter): Int {
        return lhs.start.compareTo(rhs.start)
    }
}
