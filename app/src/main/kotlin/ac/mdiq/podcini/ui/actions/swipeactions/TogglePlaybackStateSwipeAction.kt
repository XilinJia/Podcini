package ac.mdiq.podcini.ui.actions.swipeactions

import android.content.Context
import androidx.fragment.app.Fragment
import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.UserPreferences.shouldAutoDeleteItem
import ac.mdiq.podcini.preferences.UserPreferences.shouldDeleteRemoveFromQueue
import ac.mdiq.podcini.storage.database.Episodes.deleteMediaSync
import ac.mdiq.podcini.storage.database.Episodes.setPlayState
import ac.mdiq.podcini.storage.database.Episodes.setPlayStateSync
import ac.mdiq.podcini.storage.database.Queues.removeFromQueueSync
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.utils.EpisodeUtil
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.util.Logd
import android.os.Handler
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.math.ceil

class TogglePlaybackStateSwipeAction : SwipeAction {
    override fun getId(): String {
        return SwipeAction.TOGGLE_PLAYED
    }

    override fun getActionIcon(): Int {
        return R.drawable.ic_mark_played
    }

    override fun getActionColor(): Int {
        return R.attr.icon_gray
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.toggle_played_label)
    }

    override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
        val newState = if (item.playState == Episode.UNPLAYED) Episode.PLAYED else Episode.UNPLAYED

        Logd("TogglePlaybackStateSwipeAction", "performAction( ${item.id} )")
        // we're marking it as unplayed since the user didn't actually play it
        // but they don't want it considered 'NEW' anymore
        var item = runBlocking {  setPlayStateSync(newState, false, item) }

        val h = Handler(fragment.requireContext().mainLooper)
        val r = Runnable {
            val media: EpisodeMedia? = item.media
            val shouldAutoDelete = if (item.feed == null) false else shouldAutoDeleteItem(item.feed!!)
            if (media != null && EpisodeUtil.hasAlmostEnded(media) && shouldAutoDelete) {
                item = deleteMediaSync(fragment.requireContext(), item)
                if (shouldDeleteRemoveFromQueue()) removeFromQueueSync(null, null, item)   }
        }
        val playStateStringRes: Int = when (newState) {
            Episode.UNPLAYED -> if (item.playState == Episode.NEW) R.string.removed_inbox_label    //was new
            else R.string.marked_as_unplayed_label   //was played
            Episode.PLAYED -> R.string.marked_as_played_label
            else -> if (item.playState == Episode.NEW) R.string.removed_inbox_label
            else R.string.marked_as_unplayed_label
        }
        val duration: Int = Snackbar.LENGTH_LONG

        if (willRemove(filter, item)) {
            (fragment.activity as MainActivity).showSnackbarAbovePlayer(
                playStateStringRes, duration)
                .setAction(fragment.getString(R.string.undo)) {
                    setPlayState(item.playState, false, item)
                    // don't forget to cancel the thing that's going to remove the media
                    h.removeCallbacks(r)
                }
        }

        h.postDelayed(r, ceil((duration * 1.05f).toDouble()).toLong())
    }

    private fun delayedExecution(item: Episode, fragment: Fragment, duration: Float) = runBlocking {
        delay(ceil((duration * 1.05f).toDouble()).toLong())
        val media: EpisodeMedia? = item.media
        val shouldAutoDelete = if (item.feed == null) false else shouldAutoDeleteItem(item.feed!!)
        if (media != null && EpisodeUtil.hasAlmostEnded(media) && shouldAutoDelete) {
//                deleteMediaOfEpisode(fragment.requireContext(), item)
            var item = deleteMediaSync(fragment.requireContext(), item)
            if (shouldDeleteRemoveFromQueue()) removeFromQueueSync(null, null, item)   }
    }

    override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
        return if (item.playState == Episode.NEW) filter.showPlayed || filter.showNew
        else filter.showUnplayed || filter.showPlayed || filter.showNew
    }
}
