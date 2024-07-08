package ac.mdiq.podcini.ui.actions.swipeactions

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.Episodes.addToHistory
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.ui.activity.MainActivity
import android.content.Context
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import com.google.android.material.snackbar.Snackbar
import java.util.*

class RemoveFromHistorySwipeAction : SwipeAction {
    val TAG = this::class.simpleName ?: "Anonymous"

    override fun getId(): String {
        return SwipeAction.REMOVE_FROM_HISTORY
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_history_remove
    }

    override fun getActionColor(): Int {
        return R.attr.icon_purple
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.remove_history_label)
    }

    @OptIn(UnstableApi::class) override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
        val playbackCompletionDate: Date? = item.media?.playbackCompletionDate
        deleteFromHistory(item)

        (fragment.requireActivity() as MainActivity)
            .showSnackbarAbovePlayer(R.string.removed_history_label, Snackbar.LENGTH_LONG)
            .setAction(fragment.getString(R.string.undo)) {
                if (playbackCompletionDate != null) addToHistory(item, playbackCompletionDate) }
    }

    override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
        return true
    }

    fun deleteFromHistory(episode: Episode) {
        addToHistory(episode, Date(0))
    }
}