package ac.mdiq.podcini.core.sync.queue

import android.content.Context
import android.content.SharedPreferences
import ac.mdiq.podcini.core.sync.SynchronizationSettings
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import org.json.JSONArray
import org.json.JSONException

class SynchronizationQueueStorage(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    val queuedEpisodeActions: ArrayList<EpisodeAction>
        get() {
            val actions = ArrayList<EpisodeAction>()
            try {
                val json = sharedPreferences
                    .getString(QUEUED_EPISODE_ACTIONS, "[]")
                val queue = JSONArray(json)
                for (i in 0 until queue.length()) {
                    val act = EpisodeAction.readFromJsonObject(queue.getJSONObject(i))?: continue
                    actions.add(act)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            return actions
        }

    val queuedRemovedFeeds: ArrayList<String>
        get() {
            val removedFeedUrls = ArrayList<String>()
            try {
                val json = sharedPreferences
                    .getString(QUEUED_FEEDS_REMOVED, "[]")
                val queue = JSONArray(json)
                for (i in 0 until queue.length()) {
                    removedFeedUrls.add(queue.getString(i))
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            return removedFeedUrls
        }

    val queuedAddedFeeds: ArrayList<String>
        get() {
            val addedFeedUrls = ArrayList<String>()
            try {
                val json = sharedPreferences
                    .getString(QUEUED_FEEDS_ADDED, "[]")
                val queue = JSONArray(json)
                for (i in 0 until queue.length()) {
                    addedFeedUrls.add(queue.getString(i))
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            return addedFeedUrls
        }

    fun clearEpisodeActionQueue() {
        sharedPreferences.edit()
            .putString(QUEUED_EPISODE_ACTIONS, "[]").apply()
    }

    fun clearFeedQueues() {
        sharedPreferences.edit()
            .putString(QUEUED_FEEDS_ADDED, "[]")
            .putString(QUEUED_FEEDS_REMOVED, "[]")
            .apply()
    }

    fun clearQueue() {
        SynchronizationSettings.resetTimestamps()
        sharedPreferences.edit()
            .putString(QUEUED_EPISODE_ACTIONS, "[]")
            .putString(QUEUED_FEEDS_ADDED, "[]")
            .putString(QUEUED_FEEDS_REMOVED, "[]")
            .apply()
    }

    fun enqueueFeedAdded(downloadUrl: String) {
        val sharedPreferences = sharedPreferences
        try {
            val addedQueue = JSONArray(sharedPreferences.getString(QUEUED_FEEDS_ADDED, "[]"))
            addedQueue.put(downloadUrl)
            val removedQueue = JSONArray(sharedPreferences.getString(QUEUED_FEEDS_REMOVED, "[]"))
            removedQueue.remove(indexOf(downloadUrl, removedQueue))
            sharedPreferences.edit()
                .putString(QUEUED_FEEDS_ADDED, addedQueue.toString())
                .putString(QUEUED_FEEDS_REMOVED, removedQueue.toString())
                .apply()
        } catch (jsonException: JSONException) {
            jsonException.printStackTrace()
        }
    }

    fun enqueueFeedRemoved(downloadUrl: String) {
        val sharedPreferences = sharedPreferences
        try {
            val removedQueue = JSONArray(sharedPreferences.getString(QUEUED_FEEDS_REMOVED, "[]"))
            removedQueue.put(downloadUrl)
            val addedQueue = JSONArray(sharedPreferences.getString(QUEUED_FEEDS_ADDED, "[]"))
            addedQueue.remove(indexOf(downloadUrl, addedQueue))
            sharedPreferences.edit()
                .putString(QUEUED_FEEDS_ADDED, addedQueue.toString())
                .putString(QUEUED_FEEDS_REMOVED, removedQueue.toString())
                .apply()
        } catch (jsonException: JSONException) {
            jsonException.printStackTrace()
        }
    }

    private fun indexOf(string: String, array: JSONArray): Int {
        try {
            for (i in 0 until array.length()) {
                if (array.getString(i) == string) {
                    return i
                }
            }
        } catch (jsonException: JSONException) {
            jsonException.printStackTrace()
        }
        return -1
    }

    fun enqueueEpisodeAction(action: EpisodeAction) {
        val sharedPreferences = sharedPreferences
        val json = sharedPreferences.getString(QUEUED_EPISODE_ACTIONS, "[]")
        try {
            val queue = JSONArray(json)
            queue.put(action.writeToJsonObject())
            sharedPreferences.edit().putString(
                QUEUED_EPISODE_ACTIONS, queue.toString()
            ).apply()
        } catch (jsonException: JSONException) {
            jsonException.printStackTrace()
        }
    }

    companion object {
        private const val NAME = "synchronization"
        private const val QUEUED_EPISODE_ACTIONS = "sync_queued_episode_actions"
        private const val QUEUED_FEEDS_REMOVED = "sync_removed"
        private const val QUEUED_FEEDS_ADDED = "sync_added"
    }
}
