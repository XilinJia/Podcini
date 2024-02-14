package ac.mdiq.podvinci.core.sync

import android.util.Log
import androidx.collection.ArrayMap
import androidx.core.util.Pair
import ac.mdiq.podvinci.net.sync.model.EpisodeAction

object EpisodeActionFilter {
    const val TAG: String = "EpisodeActionFilter"

    fun getRemoteActionsOverridingLocalActions(
            remoteActions: List<EpisodeAction>,
            queuedEpisodeActions: List<EpisodeAction>
    ): Map<Pair<String, String>, EpisodeAction> {
        // make sure more recent local actions are not overwritten by older remote actions
        val remoteActionsThatOverrideLocalActions: MutableMap<Pair<String, String>, EpisodeAction> = ArrayMap()
        val localMostRecentPlayActions = createUniqueLocalMostRecentPlayActions(queuedEpisodeActions)
        for (remoteAction in remoteActions) {
            if (remoteAction.podcast == null || remoteAction.episode == null) continue
            val key = Pair(remoteAction.podcast!!, remoteAction.episode!!)
            when (remoteAction.action) {
                EpisodeAction.Action.NEW, EpisodeAction.Action.DOWNLOAD -> {}
                EpisodeAction.Action.PLAY -> {
                    val localMostRecent = localMostRecentPlayActions[key]
                    if (secondActionOverridesFirstAction(remoteAction, localMostRecent)) {
                        break
                    }
                    val remoteMostRecentAction = remoteActionsThatOverrideLocalActions[key]
                    if (secondActionOverridesFirstAction(remoteAction, remoteMostRecentAction)) {
                        break
                    }
                    remoteActionsThatOverrideLocalActions[key] = remoteAction
                }
                EpisodeAction.Action.DELETE -> {}
                else -> Log.e(TAG, "Unknown remoteAction: $remoteAction")
            }
        }

        return remoteActionsThatOverrideLocalActions
    }

    private fun createUniqueLocalMostRecentPlayActions(
            queuedEpisodeActions: List<EpisodeAction>
    ): Map<Pair<String, String>, EpisodeAction> {
        val localMostRecentPlayAction: MutableMap<Pair<String, String>, EpisodeAction> =
            ArrayMap()
        for (action in queuedEpisodeActions) {
            if (action.podcast == null || action.episode == null) continue
            val key = Pair(action.podcast!!, action.episode!!)
            val mostRecent = localMostRecentPlayAction[key]
            if (mostRecent?.timestamp == null) {
                localMostRecentPlayAction[key] = action
            } else if (mostRecent.timestamp!!.before(action.timestamp)) {
                localMostRecentPlayAction[key] = action
            }
        }
        return localMostRecentPlayAction
    }

    private fun secondActionOverridesFirstAction(firstAction: EpisodeAction,
                                                 secondAction: EpisodeAction?
    ): Boolean {
        return secondAction?.timestamp != null && (firstAction.timestamp == null || secondAction.timestamp!!.after(firstAction.timestamp))
    }
}
