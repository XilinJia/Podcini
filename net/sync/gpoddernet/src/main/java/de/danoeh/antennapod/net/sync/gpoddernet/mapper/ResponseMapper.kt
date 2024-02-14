package de.danoeh.antennapod.net.sync.gpoddernet.mapper

import de.danoeh.antennapod.net.sync.model.EpisodeAction
import de.danoeh.antennapod.net.sync.model.EpisodeAction.Companion.readFromJsonObject
import de.danoeh.antennapod.net.sync.model.EpisodeActionChanges
import de.danoeh.antennapod.net.sync.model.SubscriptionChanges
import org.json.JSONException
import org.json.JSONObject
import java.util.*

object ResponseMapper {
    @JvmStatic
    @Throws(JSONException::class)
    fun readSubscriptionChangesFromJsonObject(`object`: JSONObject): SubscriptionChanges {
        val added: MutableList<String> = LinkedList()
        val jsonAdded = `object`.getJSONArray("add")
        for (i in 0 until jsonAdded.length()) {
            var addedUrl = jsonAdded.getString(i)
            // gpodder escapes colons unnecessarily
            addedUrl = addedUrl.replace("%3A", ":")
            added.add(addedUrl)
        }

        val removed: MutableList<String> = LinkedList()
        val jsonRemoved = `object`.getJSONArray("remove")
        for (i in 0 until jsonRemoved.length()) {
            var removedUrl = jsonRemoved.getString(i)
            // gpodder escapes colons unnecessarily
            removedUrl = removedUrl.replace("%3A", ":")
            removed.add(removedUrl)
        }

        val timestamp = `object`.getLong("timestamp")
        return SubscriptionChanges(added, removed, timestamp)
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun readEpisodeActionsFromJsonObject(`object`: JSONObject): EpisodeActionChanges {
        val episodeActions: MutableList<EpisodeAction> = ArrayList()

        val timestamp = `object`.getLong("timestamp")
        val jsonActions = `object`.getJSONArray("actions")
        for (i in 0 until jsonActions.length()) {
            val jsonAction = jsonActions.getJSONObject(i)
            val episodeAction = readFromJsonObject(jsonAction)
            if (episodeAction != null) {
                episodeActions.add(episodeAction)
            }
        }
        return EpisodeActionChanges(episodeActions, timestamp)
    }
}
