package ac.mdiq.podcini.ui.actions.swipeactions

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeAction.Companion.NO_ACTION
import ac.mdiq.podcini.ui.dialog.SwipeActionsDialog
import ac.mdiq.podcini.ui.fragment.AllEpisodesFragment
import ac.mdiq.podcini.ui.fragment.DownloadsFragment
import ac.mdiq.podcini.ui.fragment.HistoryFragment
import ac.mdiq.podcini.ui.fragment.QueuesFragment
import ac.mdiq.podcini.ui.utils.ThemeUtils.getColorFromAttr
import ac.mdiq.podcini.ui.view.EpisodeViewHolder
import ac.mdiq.podcini.util.event.EventFlow
import ac.mdiq.podcini.util.event.FlowEvent
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import androidx.annotation.OptIn
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.annimon.stream.Stream
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

open class SwipeActions(dragDirs: Int, private val fragment: Fragment, private val tag: String)
    : ItemTouchHelper.SimpleCallback(dragDirs, ItemTouchHelper.RIGHT or ItemTouchHelper.LEFT), DefaultLifecycleObserver {

    private var filter: EpisodeFilter? = null

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

    @UnstableApi override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
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
}
