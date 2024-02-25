package ac.mdiq.podcini.feed

import android.util.Log
import ac.mdiq.podcini.storage.model.feed.Chapter
import kotlin.math.abs

object ChapterMerger {
    private const val TAG = "ChapterMerger"

    /**
     * This method might modify the input data.
     */
    fun merge(chapters1: List<Chapter>?, chapters2: List<Chapter>?): List<Chapter>? {
        Log.d(TAG, "Merging chapters")
        if (chapters1 == null) {
            return chapters2
        } else if (chapters2 == null) {
            return chapters1
        } else if (chapters2.size > chapters1.size) {
            return chapters2
        } else if (chapters2.size < chapters1.size) {
            return chapters1
        } else {
            // Merge chapter lists of same length. Store in chapters2 array.
            // In case the lists can not be merged, return chapters1 array.
            for (i in chapters2.indices) {
                val chapterTarget = chapters2[i]
                val chapterOther = chapters1[i]

                if (abs((chapterTarget.start - chapterOther.start).toDouble()) > 1000) {
                    Log.e(TAG, "Chapter lists are too different. Cancelling merge.")
                    return if (score(chapters1) > score(chapters2)) chapters1 else chapters2
                }

                if (chapterTarget.imageUrl.isNullOrEmpty()) {
                    chapterTarget.imageUrl = chapterOther.imageUrl
                }
                if (chapterTarget.link.isNullOrEmpty()) {
                    chapterTarget.link = chapterOther.link
                }
                if (chapterTarget.title.isNullOrEmpty()) {
                    chapterTarget.title = chapterOther.title
                }
            }
            return chapters2
        }
    }

    /**
     * Tries to give a score that can determine which list of chapters a user might want to see.
     */
    private fun score(chapters: List<Chapter>): Int {
        var score = 0
        for (chapter in chapters) {
            score = (score
                    + (if (chapter.title.isNullOrEmpty()) 0 else 1)
                    + (if (chapter.link.isNullOrEmpty()) 0 else 1)
                    + (if (chapter.imageUrl.isNullOrEmpty()) 0 else 1))
        }
        return score
    }
}
