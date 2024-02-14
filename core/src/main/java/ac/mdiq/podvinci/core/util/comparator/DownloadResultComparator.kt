package ac.mdiq.podvinci.core.util.comparator

import ac.mdiq.podvinci.model.download.DownloadResult

/** Compares the completion date of two DownloadResult objects.  */
class DownloadResultComparator : Comparator<DownloadResult> {
    override fun compare(lhs: DownloadResult, rhs: DownloadResult): Int {
        return rhs.getCompletionDate().compareTo(lhs.getCompletionDate())
    }
}
