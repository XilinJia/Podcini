package ac.mdiq.podvinci.core.feed

import android.text.TextUtils
import android.util.Log
import ac.mdiq.podvinci.model.feed.Chapter
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

                if (TextUtils.isEmpty(chapterTarget.imageUrl)) {
                    chapterTarget.imageUrl = chapterOther.imageUrl
                }
                if (TextUtils.isEmpty(chapterTarget.link)) {
                    chapterTarget.link = chapterOther.link
                }
                if (TextUtils.isEmpty(chapterTarget.title)) {
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
                    + (if (TextUtils.isEmpty(chapter.title)) 0 else 1)
                    + (if (TextUtils.isEmpty(chapter.link)) 0 else 1)
                    + (if (TextUtils.isEmpty(chapter.imageUrl)) 0 else 1))
        }
        return score
    }
}
