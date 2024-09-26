package ac.mdiq.podcini.ui.actions.swipeactions

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.storage.database.Episodes.addToHistory
import ac.mdiq.podcini.storage.database.Episodes.deleteMediaSync
import ac.mdiq.podcini.storage.database.Episodes.setFavorite
import ac.mdiq.podcini.storage.database.Episodes.setPlayState
import ac.mdiq.podcini.storage.database.Episodes.setPlayStateSync
import ac.mdiq.podcini.storage.database.Episodes.shouldDeleteRemoveFromQueue
import ac.mdiq.podcini.storage.database.Feeds.shouldAutoDeleteItem
import ac.mdiq.podcini.storage.database.Queues.addToQueue
import ac.mdiq.podcini.storage.database.Queues.removeFromQueue
import ac.mdiq.podcini.storage.database.Queues.removeFromQueueSync
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.EpisodeMedia
import ac.mdiq.podcini.storage.utils.EpisodeUtil
import ac.mdiq.podcini.ui.actions.actionbutton.DownloadActionButton
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeAction.Companion.NO_ACTION
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.dialog.SwipeActionsDialog
import ac.mdiq.podcini.ui.fragment.*
import ac.mdiq.podcini.ui.utils.LocalDeleteModal.deleteEpisodesWarnLocal
import ac.mdiq.podcini.ui.utils.ThemeUtils.getColorFromAttr
import ac.mdiq.podcini.ui.view.EpisodeViewHolder
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.os.Handler
import androidx.annotation.OptIn
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.annimon.stream.Stream
import com.google.android.material.snackbar.Snackbar
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

open class SwipeActions(dragDirs: Int, private val fragment: Fragment, private val tag: String)
    : ItemTouchHelper.SimpleCallback(dragDirs, ItemTouchHelper.RIGHT or ItemTouchHelper.LEFT), DefaultLifecycleObserver {

    @set:JvmName("setFilterProperty")
    var filter: EpisodeFilter? = null

    var actions: Actions? = null
    var swipeOutEnabled: Boolean = true
    var swipedOutTo: Int = 0
    private val itemTouchHelper = ItemTouchHelper(this)

    init {
        actions = getPrefs(tag)
    }

    constructor(fragment: Fragment, tag: String) : this(0, fragment, tag)

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

    fun attachTo(recyclerView: RecyclerView?): SwipeActions {
        itemTouchHelper.attachToRecyclerView(recyclerView)
        return this
    }

    fun detach() {
        itemTouchHelper.attachToRecyclerView(null)
    }

    private val isSwipeActionEnabled: Boolean
        get() = isSwipeActionEnabled(tag)

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        return false
    }

    @UnstableApi override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int) {
        if (actions != null && !actions!!.hasActions(swipeDir)) {
            showDialog()
            return
        }
        val item = (viewHolder as EpisodeViewHolder).episode
        if (actions != null && item != null && filter != null)
            (if (swipeDir == ItemTouchHelper.RIGHT) actions!!.right else actions!!.left)?.performAction(item, fragment, filter!!)
    }

    fun showDialog() {
        SwipeActionsDialog(fragment.requireContext(), tag).show(object : SwipeActionsDialog.Callback {
            override fun onCall() {
                actions = getPrefs(tag)
                EventFlow.postEvent(FlowEvent.SwipeActionsChangedEvent())
            }
        })
    }

    @UnstableApi
    override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                             dx: Float, dy: Float, actionState: Int, isCurrentlyActive: Boolean) {
        var dx = dx
        val right: SwipeAction
        val left: SwipeAction
        if (actions != null && actions!!.hasActions()) {
            right = actions!!.right!!
            left = actions!!.left!!
        } else {
            left = ShowFirstSwipeDialogAction()
            right = left
        }

        //check if it will be removed
        val item = (viewHolder as EpisodeViewHolder).episode
        var wontLeave = false
        if (item != null && filter != null) {
            val rightWillRemove = right.willRemove(filter!!, item)
            val leftWillRemove = left.willRemove(filter!!, item)
            wontLeave = (dx > 0 && !rightWillRemove) || (dx < 0 && !leftWillRemove)
        }
        //Limit swipe if it's not removed
        val maxMovement = recyclerView.width * 2 / 5
        val sign = (if (dx > 0) 1 else -1).toFloat()
        val limitMovement = min(maxMovement.toDouble(), (sign * dx).toDouble()).toFloat()
        val displacementPercentage = limitMovement / maxMovement

        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && wontLeave) {
            swipeOutEnabled = false
            val swipeThresholdReached = displacementPercentage == 1f

            // Move slower when getting near the maxMovement
            dx = sign * maxMovement * sin((Math.PI / 2) * displacementPercentage).toFloat()

            if (isCurrentlyActive) {
                val dir = if (dx > 0) ItemTouchHelper.RIGHT else ItemTouchHelper.LEFT
                swipedOutTo = if (swipeThresholdReached) dir else 0
            }
        } else swipeOutEnabled = true

        //add color and icon
        val context = fragment.requireContext()
        val themeColor = getColorFromAttr(context, android.R.attr.colorBackground)
        val actionColor = getColorFromAttr(context, if (dx > 0) right.getActionColor() else left.getActionColor())
        val builder = RecyclerViewSwipeDecorator.Builder(c, recyclerView, viewHolder, dx, dy, actionState, isCurrentlyActive)
            .addSwipeRightActionIcon(right.getActionIcon())
            .addSwipeLeftActionIcon(left.getActionIcon())
            .addSwipeRightBackgroundColor(getColorFromAttr(context, R.attr.background_elevated))
            .addSwipeLeftBackgroundColor(getColorFromAttr(context, R.attr.background_elevated))
            .setActionIconTint(ColorUtils.blendARGB(themeColor, actionColor, max(0.5, displacementPercentage.toDouble()).toFloat()))
        builder.create().decorate()

        super.onChildDraw(c, recyclerView, viewHolder, dx, dy, actionState, isCurrentlyActive)
    }

    override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
        return if (swipeOutEnabled) defaultValue * 1.5f else Float.MAX_VALUE
    }

    override fun getSwipeVelocityThreshold(defaultValue: Float): Float {
        return if (swipeOutEnabled) defaultValue * 0.6f else 0f
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        return if (swipeOutEnabled) 0.6f else 1.0f
    }

    @UnstableApi
    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)

        if (swipedOutTo != 0) {
            onSwiped(viewHolder, swipedOutTo)
            swipedOutTo = 0
        }
    }

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        return if (!isSwipeActionEnabled) makeMovementFlags(getDragDirs(recyclerView, viewHolder), 0)
        else super.getMovementFlags(recyclerView, viewHolder)
    }

    fun startDrag(holder: EpisodeViewHolder?) {
        if (holder != null) itemTouchHelper.startDrag(holder)
    }

    class Actions(prefs: String?) {
        @JvmField
        var right: SwipeAction? = null
        @JvmField
        var left: SwipeAction? = null

        init {
            val actions = prefs!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (actions.size == 2) {
                this.right = Stream.of(swipeActions).filter { a: SwipeAction -> a.getId().equals(actions[0]) }.single()
                this.left = Stream.of(swipeActions).filter { a: SwipeAction -> a.getId().equals(actions[1]) }.single()
            }
        }
        fun hasActions(): Boolean {
            return right != null && left != null
        }
        fun hasActions(swipeDir: Int): Boolean {
            return when (swipeDir) {
                ItemTouchHelper.RIGHT -> right != null && right?.getId() != NO_ACTION
                ItemTouchHelper.LEFT -> left != null && left?.getId() != NO_ACTION
                else -> false
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
            NoActionSwipeAction(), AddToQueueSwipeAction(),
            StartDownloadSwipeAction(), MarkFavoriteSwipeAction(),
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
                QueuesFragment.TAG -> NO_ACTION + "," + NO_ACTION
                DownloadsFragment.TAG -> NO_ACTION + "," + NO_ACTION
                HistoryFragment.TAG -> NO_ACTION + "," + NO_ACTION
                AllEpisodesFragment.TAG -> NO_ACTION + "," + NO_ACTION
                else -> NO_ACTION + "," + NO_ACTION
            }
            return getPrefs(tag, defaultActions)
        }

        fun isSwipeActionEnabled(tag: String): Boolean {
            return prefs!!.getBoolean(KEY_PREFIX_NO_ACTION + tag, true)
        }
    }

    class AddToQueueSwipeAction : SwipeAction {
        override fun getId(): String {
            return SwipeAction.ADD_TO_QUEUE
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
//        else RemoveFromQueueSwipeAction().performAction(item, fragment, filter)
        }

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return filter.showQueued || filter.showNew
        }
    }

    class DeleteSwipeAction : SwipeAction {
        override fun getId(): String {
            return SwipeAction.DELETE
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

    class MarkFavoriteSwipeAction : SwipeAction {
        override fun getId(): String {
            return SwipeAction.MARK_FAV
        }

        override fun getActionIcon(): Int {
            return R.drawable.ic_star
        }

        override fun getActionColor(): Int {
            return R.attr.icon_yellow
        }

        override fun getTitle(context: Context): String {
            return context.getString(R.string.add_to_favorite_label)
        }

        @OptIn(UnstableApi::class)
        override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
            setFavorite(item, !item.isFavorite)
        }

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return filter.showIsFavorite || filter.showNotFavorite
        }
    }

    class NoActionSwipeAction : SwipeAction {
        override fun getId(): String {
            return SwipeAction.NO_ACTION
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

        @OptIn(UnstableApi::class)
        override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
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

    class RemoveFromQueueSwipeAction : SwipeAction {
        override fun getId(): String {
            return SwipeAction.REMOVE_FROM_QUEUE
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

    class ShowFirstSwipeDialogAction : SwipeAction {
        override fun getId(): String {
            return "SHOW_FIRST_SWIPE_DIALOG"
        }

        override fun getActionIcon(): Int {
            return R.drawable.ic_settings
        }

        override fun getActionColor(): Int {
            return R.attr.icon_gray
        }

        override fun getTitle(context: Context): String {
            return ""
        }

        override fun performAction(item: Episode, fragment: Fragment, filter: EpisodeFilter) {
            //handled in SwipeActions
        }

        override fun willRemove(filter: EpisodeFilter, item: Episode): Boolean {
            return false
        }
    }

    class StartDownloadSwipeAction : SwipeAction {
        override fun getId(): String {
            return SwipeAction.START_DOWNLOAD
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
