package ac.mdiq.podcini.core.util.comparator

import ac.mdiq.podcini.model.download.DownloadResult

/** Compares the completion date of two DownloadResult objects.  */
class DownloadResultComparator : Comparator<DownloadResult> {
    override fun compare(lhs: DownloadResult, rhs: DownloadResult): Int {
        return rhs.getCompletionDate().compareTo(lhs.getCompletionDate())
    }
}
