package ac.mdiq.podcini.ui.adapter

import ac.mdiq.podcini.ui.activity.MainActivity
import android.annotation.SuppressLint
import android.util.Log
import android.view.ContextMenu
import android.view.MenuInflater
import android.view.MotionEvent
import android.view.View
import androidx.media3.common.util.UnstableApi
import ac.mdiq.podcini.R
import ac.mdiq.podcini.ui.actions.swipeactions.SwipeActions
import ac.mdiq.podcini.preferences.UserPreferences
import ac.mdiq.podcini.ui.view.viewholder.EpisodeItemViewHolder

/**
 * List adapter for the queue.
 */
open class QueueRecyclerAdapter(mainActivity: MainActivity, private val swipeActions: SwipeActions) : EpisodeItemListAdapter(mainActivity) {
    private var dragDropEnabled: Boolean

    init {
        dragDropEnabled = !(UserPreferences.isQueueKeepSorted || UserPreferences.isQueueLocked)
    }

    fun updateDragDropEnabled() {
        dragDropEnabled = !(UserPreferences.isQueueKeepSorted || UserPreferences.isQueueLocked)
        notifyDataSetChanged()
    }

    @UnstableApi @SuppressLint("ClickableViewAccessibility")
    override fun afterBindViewHolder(holder: EpisodeItemViewHolder, pos: Int) {
        if (inActionMode() || !dragDropEnabled) {
            holder.dragHandle.setVisibility(View.GONE)
            holder.dragHandle.setOnTouchListener(null)
//            holder.coverHolder.setOnTouchListener(null)
        } else {
            holder.dragHandle.setVisibility(View.VISIBLE)
            holder.dragHandle.setOnTouchListener { _: View?, event: MotionEvent ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    swipeActions.startDrag(holder)
                }
                false
            }
            holder.coverHolder.setOnTouchListener { v1, event ->
                if (!inActionMode() && event.actionMasked == MotionEvent.ACTION_DOWN) {
                    val isLtr = holder.itemView.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR
                    val factor = (if (isLtr) 1 else -1).toFloat()
                    if (factor * event.x < factor * 0.5 * v1.width) {
                        swipeActions.startDrag(holder)
                    } else {
                        Log.d(TAG, "Ignoring drag in right half of the image")
                    }
                }
                false
            }
        }
        if (inActionMode()) {
            holder.dragHandle.setOnTouchListener(null)
//            holder.coverHolder.setOnTouchListener(null)
        }

        holder.isInQueue.setVisibility(View.GONE)
    }

    @UnstableApi override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        val inflater: MenuInflater = activity!!.getMenuInflater()
        inflater.inflate(R.menu.queue_context, menu)
        super.onCreateContextMenu(menu, v, menuInfo)

        if (!inActionMode()) {
//            menu.findItem(R.id.multi_select).setVisible(true)
            val keepSorted: Boolean = UserPreferences.isQueueKeepSorted
            if (getItem(0)?.id === longPressedItem?.id || keepSorted) {
                menu.findItem(R.id.move_to_top_item).setVisible(false)
            }
            if (getItem(itemCount - 1)?.id === longPressedItem?.id || keepSorted) {
                menu.findItem(R.id.move_to_bottom_item).setVisible(false)
            }
        } else {
            menu.findItem(R.id.move_to_top_item).setVisible(false)
            menu.findItem(R.id.move_to_bottom_item).setVisible(false)
        }
    }

    companion object {
        private const val TAG = "QueueRecyclerAdapter"
    }
}
