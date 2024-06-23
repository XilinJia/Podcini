package ac.mdiq.podcini.storage.transport

import ac.mdiq.podcini.net.sync.SyncService.Companion.isValidGuid
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.model.EpisodeAction.Companion.readFromJsonObject
import ac.mdiq.podcini.storage.database.Episodes
import ac.mdiq.podcini.storage.database.Episodes.getEpisodeByGuidOrUrl
import ac.mdiq.podcini.storage.database.Episodes.persistEpisodes
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.utils.EpisodeUtil.hasAlmostEnded
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
            val action = readFromJsonObject(jsonAction) ?: continue
            remoteActions.add(action)
        }
        if (remoteActions.isEmpty()) return

        val updatedItems: MutableList<Episode> = ArrayList()
        for (action in remoteActions) {
            Logd(TAG, "processing action: $action")
            val result = processEpisodeAction(action) ?: continue
            updatedItems.add(result.second)
        }
//        loadAdditionalFeedItemListData(updatedItems)
//        need to do it the sync way
        for (episode in updatedItems) {
            upsertBlk(episode) {}
        }
        Logd(TAG, "Parsing finished.")
        return
    }

    private fun processEpisodeAction(action: EpisodeAction): Pair<Long, Episode>? {
        val guid = if (isValidGuid(action.guid)) action.guid else null
        val feedItem = getEpisodeByGuidOrUrl(guid, action.episode?:"")
        if (feedItem == null) {
            Log.i(TAG, "Unknown feed item: $action")
            return null
        }
        if (feedItem.media == null) {
            Log.i(TAG, "Feed item has no media: $action")
            return null
        }
        var idRemove = 0L
        feedItem.media!!.setPosition(action.position * 1000)
        feedItem.media!!.setLastPlayedTime(action.timestamp!!.time)
        feedItem.isFavorite = action.isFavorite
        feedItem.playState = action.playState
        if (hasAlmostEnded(feedItem.media!!)) {
            Logd(TAG, "Marking as played: $action")
            feedItem.setPlayed(true)
            feedItem.media!!.setPosition(0)
            idRemove = feedItem.id
        } else Logd(TAG, "Setting position: $action")

        return Pair(idRemove, feedItem)
    }
}
