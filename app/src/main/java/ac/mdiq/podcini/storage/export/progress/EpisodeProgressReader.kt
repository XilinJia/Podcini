package ac.mdiq.podcini.storage.export.progress

import ac.mdiq.podcini.net.sync.SyncService
import ac.mdiq.podcini.net.sync.SyncService.Companion.isValidGuid
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.model.EpisodeAction.Companion.readFromJsonObject
import ac.mdiq.podcini.storage.DBReader.getFeedItemByGuidOrEpisodeUrl
import ac.mdiq.podcini.storage.DBReader.loadAdditionalFeedItemListData
import ac.mdiq.podcini.storage.DBWriter.persistItemList
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.util.FeedItemUtil.hasAlmostEnded
import ac.mdiq.podcini.util.Logd
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import org.json.JSONArray
import java.io.Reader

/** Reads OPML documents.  */
object EpisodeProgressReader {
    private const val TAG = "EpisodeProgressReader"

    @OptIn(UnstableApi::class)
    fun readDocument(reader: Reader) {
        val jsonString = reader.readText()
        val jsonArray = JSONArray(jsonString)
        val remoteActions = mutableListOf<EpisodeAction>()
        for (i in 0 until jsonArray.length()) {
            val jsonAction = jsonArray.getJSONObject(i)
            Logd(TAG, "Loaded EpisodeActions message: $i $jsonAction")
            val action = readFromJsonObject(jsonAction)
            if (action != null) remoteActions.add(action)
        }
        if (remoteActions.isEmpty()) return

        val updatedItems: MutableList<FeedItem> = ArrayList()
        for (action in remoteActions) {
            val result = processEpisodeAction(action) ?: continue
            updatedItems.add(result.second)
        }
        loadAdditionalFeedItemListData(updatedItems)
        persistItemList(updatedItems)

        Logd(TAG, "Parsing finished.")
        return
    }

    private fun processEpisodeAction(action: EpisodeAction): Pair<Long, FeedItem>? {
        val guid = if (isValidGuid(action.guid)) action.guid else null
        val feedItem = getFeedItemByGuidOrEpisodeUrl(guid, action.episode?:"")
        if (feedItem == null) {
            Log.i(SyncService.TAG, "Unknown feed item: $action")
            return null
        }
        if (feedItem.media == null) {
            Log.i(SyncService.TAG, "Feed item has no media: $action")
            return null
        }
        var idRemove = 0L
        feedItem.media!!.setPosition(action.position * 1000)
        if (hasAlmostEnded(feedItem.media!!)) {
            Logd(SyncService.TAG, "Marking as played: $action")
            feedItem.setPlayed(true)
            feedItem.media!!.setPosition(0)
            idRemove = feedItem.id
        } else Logd(SyncService.TAG, "Setting position: $action")

        return Pair(idRemove, feedItem)
    }
}
