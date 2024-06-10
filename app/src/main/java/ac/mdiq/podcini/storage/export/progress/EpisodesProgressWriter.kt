package ac.mdiq.podcini.storage.export.progress

import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.model.SyncServiceException
import ac.mdiq.podcini.storage.DBReader.getEpisodes
import ac.mdiq.podcini.storage.export.ExportWriter
import ac.mdiq.podcini.storage.model.feed.Feed
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedItemFilter
import ac.mdiq.podcini.storage.model.feed.SortOrder
import ac.mdiq.podcini.util.Logd
import android.content.Context
import org.apache.commons.lang3.StringUtils
import org.json.JSONArray
import java.io.IOException
import java.io.Writer
import java.util.*

/** Writes saved favorites to file.  */
class EpisodesProgressWriter : ExportWriter {

    @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    override fun writeDocument(feeds: List<Feed?>?, writer: Writer?, context: Context) {
        Logd(TAG, "Starting to write document")
        val queuedEpisodeActions: MutableList<EpisodeAction> = mutableListOf()
        val pausedItems = getEpisodes(0, Int.MAX_VALUE, FeedItemFilter(FeedItemFilter.PAUSED), SortOrder.DATE_NEW_OLD)
        val readItems = getEpisodes(0, Int.MAX_VALUE, FeedItemFilter(FeedItemFilter.PLAYED), SortOrder.DATE_NEW_OLD)
        val comItems = mutableSetOf<FeedItem>()
        comItems.addAll(pausedItems)
        comItems.addAll(readItems)
        Logd(TAG, "Save state for all " + comItems.size + " played episodes")
        for (item in comItems) {
            val media = item.media ?: continue
            val played = EpisodeAction.Builder(item, EpisodeAction.PLAY)
                .timestamp(Date(media.getLastPlayedTime()))
                .started(media.getPosition() / 1000)
                .position(media.getPosition() / 1000)
                .total(media.getDuration() / 1000)
                .build()
            queuedEpisodeActions.add(played)
        }

        if (queuedEpisodeActions.isNotEmpty()) {
            try {
                Logd(TAG, "Saving ${queuedEpisodeActions.size} actions: ${StringUtils.join(queuedEpisodeActions, ", ")}")
                val list = JSONArray()
                for (episodeAction in queuedEpisodeActions) {
                    val obj = episodeAction.writeToJsonObject()
                    if (obj != null) {
                        Logd(TAG, "saving EpisodeAction: $obj")
                        list.put(obj)
                    }
                }
                writer?.write(list.toString())
            } catch (e: Exception) {
                e.printStackTrace()
                throw SyncServiceException(e)
            }
        }
        Logd(TAG, "Finished writing document")
    }

    override fun fileExtension(): String {
        return "json"
    }

    companion object {
        private const val TAG = "EpisodesProgressWriter"
    }
}
