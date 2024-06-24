package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import ac.mdiq.podcini.util.sorting.DownloadResultComparator
import kotlinx.coroutines.Job
import java.util.*

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
                realm.write {
                    copyToRealm(status)
                }
                EventFlow.postEvent(FlowEvent.DownloadLogEvent())
            }
        }
    }

    /**
     * Searches the DB for statistics.
     * @return The list of statistics objects
     */
    fun getStatistics(includeMarkedAsPlayed: Boolean, timeFilterFrom: Long, timeFilterTo: Long): StatisticsResult {
        Logd(TAG, "getStatistics called")

        val medias = realm.query(EpisodeMedia::class).find()
        val groupdMedias = medias.groupBy {
            it.episode?.feedId ?: 0L
        }
        val result = StatisticsResult()
        result.oldestDate = Long.MAX_VALUE
        for (fid in groupdMedias.keys) {
            val feed = getFeed(fid, false) ?: continue
            val episodes = feed.episodes.size.toLong()
            var feedPlayedTime = 0L
            var feedTotalTime = 0L
            var episodesStarted = 0L
            var totalDownloadSize = 0L
            var episodesDownloadCount = 0L
            for (m in groupdMedias[fid]!!) {
                if (m.lastPlayedTime > 0 && m.lastPlayedTime < result.oldestDate) result.oldestDate = m.lastPlayedTime
                feedTotalTime += m.duration
                if (m.lastPlayedTime in timeFilterFrom..<timeFilterTo) feedPlayedTime += m.playedDuration
                if (includeMarkedAsPlayed) {
                    if ((m.playbackCompletionTime > 0 && m.playedDuration > 0) || m.episode?.playState == Episode.PLAYED || m.position > 0)
                        episodesStarted += 1
                } else {
                    if (m.playbackCompletionTime > 0 && m.playedDuration > 0) episodesStarted += 1
                }
                if (m.downloaded) {
                    episodesDownloadCount += 1
                    totalDownloadSize += m.size
                }
                result.feedTime.add(StatisticsItem(feed, feedTotalTime, feedPlayedTime, episodes, episodesStarted, totalDownloadSize, episodesDownloadCount))
            }
        }
        return result
    }
}