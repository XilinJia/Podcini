package ac.mdiq.podcini.ui.actions

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.storage.database.Episodes.deleteEpisodesWarnLocal
import ac.mdiq.podcini.storage.database.Episodes.hasAlmostEnded
import ac.mdiq.podcini.storage.database.Episodes.setPlayStateSync
import ac.mdiq.podcini.storage.database.Queues.addToQueue
import ac.mdiq.podcini.storage.database.Queues.removeFromQueueSync
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.PlayState
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.compose.*
import ac.mdiq.podcini.ui.fragment.*
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.MiscFormatter.fullDateTimeString
import android.content.Context
import android.content.SharedPreferences
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.runBlocking
import java.util.*

interface SwipeAction {
    fun getId(): String
    fun getTitle(context: Context): String
    @DrawableRes
    fun getActionIcon(): Int
    @AttrRes
    @DrawableRes
    fun getActionColor(): Int
    @Composable
    fun ActionOptions() {}
    fun performAction(item: Episode)
}

class SwipeActions(private val fragment: Fragment, private val tag: String) : DefaultLifecycleObserver {
//    val prefs: SharedPreferences by lazy { fragment.requireContext().getSharedPreferences(SWIPE_ACTIONS_PREF_NAME, Context.MODE_PRIVATE)}

    val actionsList: List<SwipeAction> = listOf(
        NoActionSwipeAction(), ComboSwipeAction(),
        AddToQueueSwipeAction(), PutToQueueSwipeAction(),
        StartDownloadSwipeAction(), SetRatingSwipeAction(), AddCommentSwipeAction(),
        SetPlaybackStateSwipeAction(), RemoveFromQueueSwipeAction(),
        DeleteSwipeAction(), RemoveFromHistorySwipeAction(),
        ShelveSwipeAction(), EraseSwipeAction())

    var actions by mutableStateOf<RightLeftActions>(getPrefs(tag, ""))

    override fun onStart(owner: LifecycleOwner) {
        actions = getPrefs(tag, "")
    }

    @Composable
    fun ActionOptionsDialog() {
        actions.left[0].ActionOptions()
        actions.right[0].ActionOptions()
    }

    private fun getPrefs(tag: String, defaultActions: String): RightLeftActions {
        val prefsString = prefs?.getString(KEY_PREFIX_SWIPEACTIONS + tag, defaultActions) ?: defaultActions
        return RightLeftActions(prefsString)
    }

    inner class RightLeftActions(actions_: String) {
        @JvmField
        var right: MutableList<SwipeAction> = mutableListOf(NoActionSwipeAction(), NoActionSwipeAction())
        @JvmField
        var left: MutableList<SwipeAction> = mutableListOf(NoActionSwipeAction(), NoActionSwipeAction())

        init {
            val actions = actions_.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (actions.size == 2) {
                val rActs = actionsList.filter { a: SwipeAction -> a.getId() == actions[0] }
                this.right[0] = if (rActs.isEmpty()) actionsList[0] else rActs[0]
                val lActs = actionsList.filter { a: SwipeAction -> a.getId() == actions[1] }
                this.left[0] = if (lActs.isEmpty()) actionsList[0] else lActs[0]
            }
        }
    }

    enum class ActionTypes {
        NO_ACTION,
        COMBO,
        RATING,
        COMMENT,
        SET_PLAY_STATE,
        ADD_TO_QUEUE,
        PUT_TO_QUEUE,
        REMOVE_FROM_QUEUE,
        START_DOWNLOAD,
        DELETE,
        REMOVE_FROM_HISTORY,
        SHELVE,
        ERASE
    }

    inner class AddToQueueSwipeAction : SwipeAction {
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
        override fun performAction(item: Episode) {
            addToQueue(item)
        }
    }

    inner class ComboSwipeAction : SwipeAction {
        var showDialog by mutableStateOf(false)
        var onEpisode by mutableStateOf<Episode?>(null)
        var useAction by mutableStateOf<SwipeAction?>(null)
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
            return context.getString(R.string.combo_action)
        }
        override fun performAction(item: Episode) {
            onEpisode = item
            showDialog = true
        }
        @Composable
        override fun ActionOptions() {
            useAction?.ActionOptions()
            if (showDialog && onEpisode!= null) Dialog(onDismissRequest = { showDialog = false }) {
                val context = LocalContext.current
                Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (action in actionsList) {
                            if (action.getId() == ActionTypes.NO_ACTION.name || action.getId() == ActionTypes.COMBO.name) continue
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp).clickable {
                                useAction = action
                                action.performAction(onEpisode!!)
                                showDialog = false
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

    inner class DeleteSwipeAction : SwipeAction {
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
        override fun performAction(item_: Episode) {
            var item = item_
            if (!item.isDownloaded && item.feed?.isLocalFeed != true) return
            val media = item.media
            if (media != null) {
                val almostEnded = hasAlmostEnded(media)
                if (almostEnded && item.playState < PlayState.PLAYED.code)
                    item = runBlocking { setPlayStateSync(PlayState.PLAYED.code, item, resetMediaPosition = true, removeFromQueue = false) }
                if (almostEnded) item = upsertBlk(item) { it.media?.playbackCompletionDate = Date() }
            }
            deleteEpisodesWarnLocal(fragment.requireContext(), listOf(item))
        }
    }

    inner class SetRatingSwipeAction : SwipeAction {
        var showChooseRatingDialog by mutableStateOf(false)
        var onEpisode by mutableStateOf<Episode?>(null)
        override fun getId(): String {
            return ActionTypes.RATING.name
        }
        override fun getActionIcon(): Int {
            return R.drawable.ic_star
        }
        override fun getActionColor(): Int {
            return R.attr.icon_yellow
        }
        override fun getTitle(context: Context): String {
            return context.getString(R.string.set_rating_label)
        }
        override fun performAction(item: Episode) {
            onEpisode = item
            showChooseRatingDialog = true
        }
        @Composable
        override fun ActionOptions() {
            if (showChooseRatingDialog && onEpisode!= null) ChooseRatingDialog(listOf(onEpisode!!)) { showChooseRatingDialog = false }
        }
    }

    inner class AddCommentSwipeAction : SwipeAction {
        var showEditComment by mutableStateOf(false)
        var onEpisode by mutableStateOf<Episode?>(null)
        var localTime by mutableLongStateOf(System.currentTimeMillis())
        var editCommentText by mutableStateOf(TextFieldValue(""))
        override fun getId(): String {
            return ActionTypes.COMMENT.name
        }
        override fun getActionIcon(): Int {
            return R.drawable.baseline_comment_24
        }
        override fun getActionColor(): Int {
            return R.attr.icon_yellow
        }
        override fun getTitle(context: Context): String {
            return context.getString(R.string.add_opinion_label)
        }
        override fun performAction(item: Episode) {
//            onEpisode = realm.query(Episode::class).query("id == ${item.id}").first().find()
            onEpisode = item
            localTime = System.currentTimeMillis()
            editCommentText = TextFieldValue((if (onEpisode?.comment.isNullOrBlank()) "" else onEpisode!!.comment + "\n") + fullDateTimeString(localTime) + ":\n")
            showEditComment = true
        }
        @Composable
        override fun ActionOptions() {
            if (showEditComment && onEpisode != null) {
                LargeTextEditingDialog(textState = editCommentText, onTextChange = { editCommentText = it }, onDismissRequest = { showEditComment = false },
                    onSave = { text ->
                        runOnIOScope {
                            upsert(onEpisode!!) {
                                it.comment = text
                                it.commentTime = localTime
                            }
                            onEpisode = null
                        }
                    })
            }
        }
    }

    class NoActionSwipeAction : SwipeAction {
        override fun getId(): String {
            return ActionTypes.NO_ACTION.name
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
        override fun performAction(item: Episode) {}
    }

    inner class RemoveFromHistorySwipeAction : SwipeAction {
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
        override fun performAction(item: Episode) {
            val playbackCompletionDate: Date? = item.media?.playbackCompletionDate
            val lastPlayedDate = item.media?.lastPlayedTime
            setHistoryDates(item)

            (fragment.requireActivity() as MainActivity).showSnackbarAbovePlayer(R.string.removed_history_label, Snackbar.LENGTH_LONG)
                .setAction(fragment.getString(R.string.undo)) { if (playbackCompletionDate != null) setHistoryDates(item, lastPlayedDate?:0, playbackCompletionDate) }
        }
        private fun setHistoryDates(episode: Episode, lastPlayed: Long = 0, completed: Date = Date(0)) {
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

    inner class RemoveFromQueueSwipeAction : SwipeAction {
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
        override fun performAction(item_: Episode) {
//            val position: Int = curQueue.episodes.indexOf(item_)
            var item = item_
            val media = item.media
            if (media != null) {
                val almostEnded = hasAlmostEnded(media)
                if (almostEnded && item.playState < PlayState.PLAYED.code) item = runBlocking { setPlayStateSync(PlayState.PLAYED.code, item, resetMediaPosition = true, removeFromQueue = false) }
                if (almostEnded) item = upsertBlk(item) { it.media?.playbackCompletionDate = Date() }
            }
            if (item.playState < PlayState.SKIPPED.code) item = runBlocking { setPlayStateSync(PlayState.SKIPPED.code, item, resetMediaPosition = false, removeFromQueue = false) }
            runOnIOScope { removeFromQueueSync(curQueue, item) }
        }
    }

    inner class PutToQueueSwipeAction : SwipeAction {
        var showPutToQueueDialog by mutableStateOf(false)
        var onEpisode by mutableStateOf<Episode?>(null)
        override fun getId(): String {
            return ActionTypes.PUT_TO_QUEUE.name
        }
        override fun getActionIcon(): Int {
            return R.drawable.ic_playlist_play
        }
        override fun getActionColor(): Int {
            return R.attr.icon_gray
        }
        override fun getTitle(context: Context): String {
            return context.getString(R.string.put_in_queue_label)
        }
        override fun performAction(item: Episode) {
            onEpisode = item
            showPutToQueueDialog = true
        }
        @Composable
        override fun ActionOptions() {
            if (showPutToQueueDialog && onEpisode != null) PutToQueueDialog(listOf(onEpisode!!)) { showPutToQueueDialog = false }
        }
    }

    inner class StartDownloadSwipeAction : SwipeAction {
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
        override fun performAction(item: Episode) {
            if (!item.isDownloaded && item.feed != null && !item.feed!!.isLocalFeed) DownloadActionButton(item).onClick(fragment.requireContext())
        }
    }

    inner class SetPlaybackStateSwipeAction : SwipeAction {
        var showPlayStateDialog by mutableStateOf(false)
        var onEpisode by mutableStateOf<Episode?>(null)
        override fun getId(): String {
            return ActionTypes.SET_PLAY_STATE.name
        }
        override fun getActionIcon(): Int {
            return R.drawable.ic_mark_played
        }
        override fun getActionColor(): Int {
            return R.attr.icon_gray
        }
        override fun getTitle(context: Context): String {
            return context.getString(R.string.set_play_state_label)
        }
        override fun performAction(item: Episode) {
            onEpisode = item
            showPlayStateDialog = true
        }
        @Composable
        override fun ActionOptions() {
            if (showPlayStateDialog && onEpisode != null) PlayStateDialog(listOf(onEpisode!!)) { showPlayStateDialog = false }
        }
//        private fun delayedExecution(item: Episode, fragment: Fragment, duration: Float) = runBlocking {
//            delay(ceil((duration * 1.05f).toDouble()).toLong())
//            val media: EpisodeMedia? = item.media
//            val shouldAutoDelete = if (item.feed == null) false else shouldAutoDeleteItem(item.feed!!)
//            if (media != null && Episodes.hasAlmostEnded(media) && shouldAutoDelete) {
////                deleteMediaOfEpisode(fragment.requireContext(), item)
//                val item_ = deleteMediaSync(fragment.requireContext(), item)
//                if (prefDeleteRemovesFromQueue) removeFromQueueSync(null, item_)   }
//        }
    }

    inner class ShelveSwipeAction : SwipeAction {
        var showShelveDialog by mutableStateOf(false)
        var onEpisode by mutableStateOf<Episode?>(null)
        override fun getId(): String {
            return ActionTypes.SHELVE.name
        }
        override fun getActionIcon(): Int {
            return R.drawable.baseline_shelves_24
        }
        override fun getActionColor(): Int {
            return R.attr.icon_gray
        }
        override fun getTitle(context: Context): String {
            return context.getString(R.string.shelve_label)
        }
        override fun performAction(item: Episode) {
            onEpisode = item
            showShelveDialog = true
        }
        @Composable
        override fun ActionOptions() {
            if (showShelveDialog && onEpisode!= null) ShelveDialog(listOf(onEpisode!!)) { showShelveDialog = false }
        }
    }

    inner class EraseSwipeAction : SwipeAction {
        var showEraseDialog by mutableStateOf(false)
        var onEpisode by mutableStateOf<Episode?>(null)
        override fun getId(): String {
            return ActionTypes.ERASE.name
        }
        override fun getActionIcon(): Int {
            return R.drawable.baseline_delete_forever_24
        }
        override fun getActionColor(): Int {
            return R.attr.icon_gray
        }
        override fun getTitle(context: Context): String {
            return context.getString(R.string.erase_episodes_label)
        }
        override fun performAction(item: Episode) {
            onEpisode = item
            showEraseDialog = true
        }
        @Composable
        override fun ActionOptions() {
            if (showEraseDialog && onEpisode!= null) EraseEpisodesDialog(listOf(onEpisode!!), onEpisode!!.feed) { showEraseDialog = false }
        }
    }

    companion object {
        private const val SWIPE_ACTIONS_PREF_NAME: String = "SwipeActionsPrefs"
        private const val KEY_PREFIX_SWIPEACTIONS: String = "PrefSwipeActions"
        private const val KEY_PREFIX_NO_ACTION: String = "PrefNoSwipeAction"

        var prefs: SharedPreferences? = null
        fun getSharedPrefs(context: Context) {
            if (prefs == null) prefs = context.getSharedPreferences(SWIPE_ACTIONS_PREF_NAME, Context.MODE_PRIVATE)
        }

        @Composable
        fun SwipeActionsSettingDialog(sa: SwipeActions, onDismissRequest: () -> Unit, callback: (RightLeftActions)->Unit) {
            val context = LocalContext.current
            val textColor = MaterialTheme.colorScheme.onSurface

            var actions = remember { sa.actions }
            val leftAction = remember { mutableStateOf(actions.left) }
            val rightAction = remember { mutableStateOf(actions.right) }
            var keys by remember { mutableStateOf(sa.actionsList) }

            fun savePrefs(tag: String, right: String?, left: String?) {
//                getSharedPrefs(context)
                prefs?.edit()?.putString(KEY_PREFIX_SWIPEACTIONS + tag, "$right,$left")?.apply()
            }
            fun saveActionsEnabledPrefs(enabled: Boolean) {
//                getSharedPrefs(context)
                prefs?.edit()?.putBoolean(KEY_PREFIX_NO_ACTION + sa.tag, enabled)?.apply()
            }

            var direction by remember { mutableIntStateOf(0) }
            var showPickerDialog by remember { mutableStateOf(false) }
            if (showPickerDialog) {
                Dialog(onDismissRequest = { showPickerDialog = false }) {
                    Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.padding(16.dp)) {
                            items(keys.size) { index ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp).clickable {
                                    when (direction) {
                                        -1 -> leftAction.value[0] = keys[index]
                                        1 -> rightAction.value[0] = keys[index]
                                        else -> {}
                                    }
                                    showPickerDialog = false
                                }) {
                                    Icon(imageVector = ImageVector.vectorResource(keys[index].getActionIcon()), tint = textColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp))
                                    Text(keys[index].getTitle(context), color = textColor, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }

            if (!showPickerDialog) Dialog(onDismissRequest = { onDismissRequest() }) {
                val forFragment = remember(sa.tag) {
                    if (sa.tag != QueuesFragment.TAG) keys = keys.filter { a: SwipeAction -> a.getId() != ActionTypes.REMOVE_FROM_QUEUE.name }
                    when (sa.tag) {
                    EpisodesFragment.TAG -> context.getString(R.string.episodes_label)
                    OnlineEpisodesFragment.TAG -> context.getString(R.string.online_episodes_label)
                    SearchFragment.TAG -> context.getString(R.string.search_label)
                    FeedEpisodesFragment.TAG -> {
                        keys = keys.filter { a: SwipeAction -> a.getId() != ActionTypes.REMOVE_FROM_HISTORY.name }
                        context.getString(R.string.subscription)
                    }
                    QueuesFragment.TAG -> {
                        keys = keys.filter { a: SwipeAction -> (a.getId() != ActionTypes.ADD_TO_QUEUE.name && a.getId() != ActionTypes.REMOVE_FROM_HISTORY.name) }
                        context.getString(R.string.queue_label)
                    }
                    else -> { "" }
                } }
                Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(stringResource(R.string.swipeactions_label) + " - " + forFragment)
                        Text(stringResource(R.string.swipe_left))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 10.dp)) {
                            Spacer(Modifier.weight(0.1f))
                            Icon(imageVector = ImageVector.vectorResource(leftAction.value[0].getActionIcon()), tint = textColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp)
                                .clickable(onClick = {
                                    direction = -1
                                    showPickerDialog = true
                                }))
                            Spacer(Modifier.weight(0.1f))
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_left_alt_24), tint = textColor, contentDescription = "right_arrow", modifier = Modifier.width(50.dp).height(35.dp))
                            Spacer(Modifier.weight(0.5f))
                        }
                        Text(stringResource(R.string.swipe_right))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 10.dp)) {
                            Spacer(Modifier.weight(0.5f))
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_right_alt_24), tint = textColor, contentDescription = "right_arrow", modifier = Modifier.width(50.dp).height(35.dp))
                            Spacer(Modifier.weight(0.1f))
                            Icon(imageVector = ImageVector.vectorResource(rightAction.value[0].getActionIcon()), tint = textColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp)
                                .clickable(onClick = {
                                    direction = 1
                                    showPickerDialog = true
                                }))
                            Spacer(Modifier.weight(0.1f))
                        }
                        Button(onClick = {
                            actions = sa.RightLeftActions("${rightAction.value[0].getId()},${leftAction.value[0].getId()}")
                            savePrefs(sa.tag, rightAction.value[0].getId(), leftAction.value[0].getId())
                            saveActionsEnabledPrefs(true)
                            callback(actions)
                            onDismissRequest()
                        }) { Text("Confirm") }
                    }
                }
            }
        }
    }
}
