package ac.mdiq.podcini.ui.actions

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.storage.database.Episodes.deleteMediaSync
import ac.mdiq.podcini.storage.database.Episodes.setPlayState
import ac.mdiq.podcini.storage.database.Episodes.setPlayStateSync
import ac.mdiq.podcini.storage.database.Episodes.shouldDeleteRemoveFromQueue
import ac.mdiq.podcini.storage.database.Feeds.shouldAutoDeleteItem
import ac.mdiq.podcini.storage.database.Queues.addToQueue
import ac.mdiq.podcini.storage.database.Queues.removeFromQueue
import ac.mdiq.podcini.storage.database.Queues.removeFromQueueSync
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.utils.EpisodeUtil
import ac.mdiq.podcini.ui.actions.SwipeAction.ActionTypes.NO_ACTION
import ac.mdiq.podcini.ui.actions.SwipeAction.ActionTypes
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.ChooseRatingDialog
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.dialog.SwipeActionsDialog
import ac.mdiq.podcini.ui.fragment.AllEpisodesFragment
import ac.mdiq.podcini.ui.fragment.DownloadsFragment
import ac.mdiq.podcini.ui.fragment.HistoryFragment
import ac.mdiq.podcini.ui.fragment.QueuesFragment
import ac.mdiq.podcini.ui.utils.LocalDeleteModal.deleteEpisodesWarnLocal
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.util.TypedValue
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.math.ceil

open class SwipeActions(private val fragment: Fragment, private val tag: String) : DefaultLifecycleObserver {

    @set:JvmName("setFilterProperty")
    var filter: EpisodeFilter? = null
    var actions: Actions? = null

    init {
        actions = getPrefs(tag)
    }

    override fun onStart(owner: LifecycleOwner) {
        actions = getPrefs(tag)
    }

    override fun onStop(owner: LifecycleOwner) {
        actions = null
    }

    @JvmName("setFilterFunction")
    fun setFilter(filter: EpisodeFilter?) {
        this.filter = filter
    }

    fun showDialog() {
        SwipeActionsDialog(fragment.requireContext(), tag).show(object : SwipeActionsDialog.Callback {
            override fun onCall() {
                actions = getPrefs(tag)
                EventFlow.postEvent(FlowEvent.SwipeActionsChangedEvent())
            }
        })
    }

    class Actions(prefs: String?) {
        @JvmField
        var right: SwipeAction = swipeActions[0]
        @JvmField
        var left: SwipeAction = swipeActions[0]

        init {
            val actions = prefs!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (actions.size == 2) {
                val rActs = swipeActions.filter { a: SwipeAction -> a.getId().equals(actions[0]) }
                this.right = if (rActs.isEmpty()) swipeActions[0] else rActs[0]
                val lActs = swipeActions.filter { a: SwipeAction -> a.getId().equals(actions[1]) }
                this.left = if (lActs.isEmpty()) swipeActions[0] else lActs[0]
            }
        }
    }

    companion object {
        const val SWIPE_ACTIONS_PREF_NAME: String = "SwipeActionsPrefs"
        const val KEY_PREFIX_SWIPEACTIONS: String = "PrefSwipeActions"
        const val KEY_PREFIX_NO_ACTION: String = "PrefNoSwipeAction"

        var prefs: SharedPreferences? = null
        fun getSharedPrefs(context: Context) {
            if (prefs == null) prefs = context.getSharedPreferences(SWIPE_ACTIONS_PREF_NAME, Context.MODE_PRIVATE)
        }

        @JvmField
        val swipeActions: List<SwipeAction> = listOf(
            NoActionSwipeAction(), ComboSwipeAction(), AddToQueueSwipeAction(),
            StartDownloadSwipeAction(), SetRatingSwipeAction(),
            TogglePlaybackStateSwipeAction(), RemoveFromQueueSwipeAction(),
            DeleteSwipeAction(), RemoveFromHistorySwipeAction())

        private fun getPrefs(tag: String, defaultActions: String): Actions {
            val prefsString = prefs!!.getString(KEY_PREFIX_SWIPEACTIONS + tag, defaultActions)
            return Actions(prefsString)
        }

        fun getPrefs(tag: String): Actions {
            return getPrefs(tag, "")
        }

        @OptIn(UnstableApi::class) @JvmStatic
        fun getPrefsWithDefaults(tag: String): Actions {
            val defaultActions = when (tag) {
                QueuesFragment.TAG -> "${NO_ACTION.name},${NO_ACTION.name}"
                DownloadsFragment.TAG -> "${NO_ACTION.name},${NO_ACTION.name}"
                HistoryFragment.TAG -> "${NO_ACTION.name},${NO_ACTION.name}"
                AllEpisodesFragment.TAG -> "${NO_ACTION.name},${NO_ACTION.name}"
                else -> "${NO_ACTION.name},${NO_ACTION.name}"
            }
            return getPrefs(tag, defaultActions)
        }

        fun isSwipeActionEnabled(tag: String): Boolean {
            return prefs!!.getBoolean(KEY_PREFIX_NO_ACTION + tag, true)
        }
    }

    class AddToQueueSwipeAction : SwipeAction {
        override fun getId(): String {
            return ActionTypes.ADD_TO_QUEUE.name
        }

        override fun getActionIcon(): Int {
            return R.drawable.ic_playlist_play
        }

        override fun getActionColor(): Int {
            return androidx.appcompat.R.attr.colorAccent
        }

        override fun getTitle(context: Context): String {
            return context.getString(R.string.add_to_queue_label)
        }

        @OptIn(UnstableApi::class)
        override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
            addToQueue( true, item)
        }

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return filter.showQueued || filter.showNew
        }
    }

    class ComboSwipeAction : SwipeAction {
        override fun getId(): String {
            return ActionTypes.COMBO.name
        }

        override fun getActionIcon(): Int {
            return R.drawable.baseline_category_24
        }

        override fun getActionColor(): Int {
            return androidx.appcompat.R.attr.colorAccent
        }

        override fun getTitle(context: Context): String {
            return context.getString(R.string.add_to_queue_label)
        }

        @OptIn(UnstableApi::class)
        override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
            val composeView = ComposeView(fragment.requireContext()).apply {
                setContent {
                    var showDialog by remember { mutableStateOf(true) }
                    CustomTheme(fragment.requireContext()) {
                        if (showDialog) Dialog(onDismissRequest = {
                            showDialog = false
                            (fragment.view as? ViewGroup)?.removeView(this@apply)
                        }) {
                            val context = LocalContext.current
                            Surface(shape = RoundedCornerShape(16.dp)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    for (action in swipeActions) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp).clickable {
                                            action.performAction(item, fragment, filter)
                                            showDialog = false
                                            (fragment.view as? ViewGroup)?.removeView(this@apply)
                                        }) {
                                            val colorAccent = remember {
                                                val typedValue = TypedValue()
                                                context.theme.resolveAttribute(action.getActionColor(), typedValue, true)
                                                Color(typedValue.data)
                                            }
                                            Icon(imageVector = ImageVector.vectorResource(id = action.getActionIcon()),  tint = colorAccent, contentDescription = action.getTitle(context))
                                            Text(action.getTitle(context), Modifier.padding(start = 4.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            (fragment.view as? ViewGroup)?.addView(composeView)
        }

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return filter.showQueued || filter.showNew
        }
    }

    class DeleteSwipeAction : SwipeAction {
        override fun getId(): String {
            return ActionTypes.DELETE.name
        }

        override fun getActionIcon(): Int {
            return R.drawable.ic_delete
        }

        override fun getActionColor(): Int {
            return R.attr.icon_red
        }

        override fun getTitle(context: Context): String {
            return context.getString(R.string.delete_episode_label)
        }

        @UnstableApi
        override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
            if (!item.isDownloaded && item.feed?.isLocalFeed != true) return
            deleteEpisodesWarnLocal(fragment.requireContext(), listOf(item))
        }

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return filter.showDownloaded && (item.isDownloaded || item.feed?.isLocalFeed == true)
        }
    }

    class SetRatingSwipeAction : SwipeAction {
        override fun getId(): String {
            return ActionTypes.MARK_FAV.name
        }

        override fun getActionIcon(): Int {
            return R.drawable.ic_star
        }

        override fun getActionColor(): Int {
            return R.attr.icon_yellow
        }

        override fun getTitle(context: Context): String {
            return context.getString(R.string.switch_rating_label)
        }

        @OptIn(UnstableApi::class)
        override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
            var showChooseRatingDialog by mutableStateOf(true)
            val composeView = ComposeView(fragment.requireContext()).apply {
                setContent {
                    CustomTheme(fragment.requireContext()) {
                        if (showChooseRatingDialog) ChooseRatingDialog(listOf(item)) {
                            showChooseRatingDialog = false
                            (fragment.view as? ViewGroup)?.removeView(this@apply)
                        }
                    }
                }
            }
            (fragment.view as? ViewGroup)?.addView(composeView)
        }

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return filter.showIsFavorite || filter.showNotFavorite
        }
    }

    class NoActionSwipeAction : SwipeAction {
        override fun getId(): String {
            return NO_ACTION.name
        }

        override fun getActionIcon(): Int {
            return R.drawable.ic_questionmark
        }

        override fun getActionColor(): Int {
            return R.attr.icon_red
        }

        override fun getTitle(context: Context): String {
            return context.getString(R.string.no_action_label)
        }

        @UnstableApi
        override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {}

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return false
        }
    }

    class RemoveFromHistorySwipeAction : SwipeAction {
        val TAG = this::class.simpleName ?: "Anonymous"

        override fun getId(): String {
            return ActionTypes.REMOVE_FROM_HISTORY.name
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

        @OptIn(UnstableApi::class)
        override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
            val playbackCompletionDate: Date? = item.media?.playbackCompletionDate
            val lastPlayedDate = item.media?.lastPlayedTime
            setHistoryDates(item)

            (fragment.requireActivity() as MainActivity)
                .showSnackbarAbovePlayer(R.string.removed_history_label, Snackbar.LENGTH_LONG)
                .setAction(fragment.getString(R.string.undo)) {
                    if (playbackCompletionDate != null) setHistoryDates(item, lastPlayedDate?:0, playbackCompletionDate) }
        }

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return true
        }

        fun setHistoryDates(episode: Episode, lastPlayed: Long = 0, completed: Date = Date(0)) {
            runOnIOScope {
                val episode_ = realm.query(Episode::class).query("id == $0", episode.id).first().find()
                if (episode_ != null) {
                    upsert(episode_) {
                        it.media?.lastPlayedTime = lastPlayed
                        it.media?.playbackCompletionDate = completed
                    }
                    EventFlow.postEvent(FlowEvent.HistoryEvent())
                }
            }
        }
    }

    class RemoveFromQueueSwipeAction : SwipeAction {
        override fun getId(): String {
            return ActionTypes.REMOVE_FROM_QUEUE.name
        }

        override fun getActionIcon(): Int {
            return R.drawable.ic_playlist_remove
        }

        override fun getActionColor(): Int {
            return androidx.appcompat.R.attr.colorAccent
        }

        override fun getTitle(context: Context): String {
            return context.getString(R.string.remove_from_queue_label)
        }

        @OptIn(UnstableApi::class)
        override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
            val position: Int = curQueue.episodes.indexOf(item)
            removeFromQueue(item)
            if (willRemove(filter, item)) {
                (fragment.requireActivity() as MainActivity).showSnackbarAbovePlayer(fragment.resources.getQuantityString(R.plurals.removed_from_queue_batch_label, 1, 1), Snackbar.LENGTH_LONG)
                    .setAction(fragment.getString(R.string.undo)) {
                        addToQueueAt(item, position)
                    }
            }
        }

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return filter.showQueued || filter.showNotQueued
        }

        /**
         * Inserts a Episode in the queue at the specified index. The 'read'-attribute of the Episode will be set to
         * true. If the Episode is already in the queue, the queue will not be modified.
         * @param episode                the Episode that should be added to the queue.
         * @param index               Destination index. Must be in range 0..queue.size()
         * @throws IndexOutOfBoundsException if index < 0 || index >= queue.size()
         */
        @UnstableApi
        fun addToQueueAt(episode: Episode, index: Int) : Job {
            return runOnIOScope {
                if (curQueue.episodeIds.contains(episode.id)) return@runOnIOScope
                if (episode.isNew) setPlayState(Episode.PlayState.UNPLAYED.code, false, episode)
                curQueue = upsert(curQueue) {
                    it.episodeIds.add(index, episode.id)
                    it.update()
                }
//            curQueue.episodes.add(index, episode)
                EventFlow.postEvent(FlowEvent.QueueEvent.added(episode, index))
//            if (performAutoDownload) autodownloadEpisodeMedia(context)
            }
        }
    }

    class StartDownloadSwipeAction : SwipeAction {
        override fun getId(): String {
            return ActionTypes.START_DOWNLOAD.name
        }

        override fun getActionIcon(): Int {
            return R.drawable.ic_download
        }

        override fun getActionColor(): Int {
            return R.attr.icon_green
        }

        override fun getTitle(context: Context): String {
            return context.getString(R.string.download_label)
        }

        override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
            if (!item.isDownloaded && item.feed != null && !item.feed!!.isLocalFeed) {
                DownloadActionButton(item).onClick(fragment.requireContext())
            }
        }

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return false
        }
    }

    class TogglePlaybackStateSwipeAction : SwipeAction {
        override fun getId(): String {
            return ActionTypes.TOGGLE_PLAYED.name
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
            val newState = if (item.playState == Episode.PlayState.UNPLAYED.code) Episode.PlayState.PLAYED.code else Episode.PlayState.UNPLAYED.code

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
                    if (shouldDeleteRemoveFromQueue()) removeFromQueueSync(null, item)   }
            }
            val playStateStringRes: Int = when (newState) {
                Episode.PlayState.UNPLAYED.code -> if (item.playState == Episode.PlayState.NEW.code) R.string.removed_inbox_label    //was new
                else R.string.marked_as_unplayed_label   //was played
                Episode.PlayState.PLAYED.code -> R.string.marked_as_played_label
                else -> if (item.playState == Episode.PlayState.NEW.code) R.string.removed_inbox_label
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
                if (shouldDeleteRemoveFromQueue()) removeFromQueueSync(null, item)   }
        }

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return if (item.playState == Episode.PlayState.NEW.code) filter.showPlayed || filter.showNew
            else filter.showUnplayed || filter.showPlayed || filter.showNew
        }
    }
}
