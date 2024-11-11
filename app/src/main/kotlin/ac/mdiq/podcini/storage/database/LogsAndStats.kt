package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.utils.DownloadResultComparator
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import kotlinx.coroutines.Job

object LogsAndStats {
    private val TAG: String = LogsAndStats::class.simpleName ?: "Anonymous"

    fun getFeedDownloadLog(feedId: Long): List<DownloadResult> {
        Logd(TAG, "getFeedDownloadLog() called with: $feedId")
        val dlog = realm.query(DownloadResult::class).query("feedfileId == $0", feedId).find().toMutableList()
        dlog.sortWith(DownloadResultComparator())
        return realm.copyFromRealm(dlog)
    }

    fun addDownloadStatus(status: DownloadResult?): Job {
        Logd(TAG, "addDownloadStatus called")
        return runOnIOScope {
            if (status != null) {
                if (status.id == 0L) status.setId()
                upsert(status) {}
                EventFlow.postEvent(FlowEvent.DownloadLogEvent())
            }
        }
    }
}