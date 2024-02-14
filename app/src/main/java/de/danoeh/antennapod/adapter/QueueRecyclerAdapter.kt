package de.danoeh.antennapod.adapter

import de.danoeh.antennapod.activity.MainActivity
import android.annotation.SuppressLint
import android.util.Log
import android.view.ContextMenu
import android.view.MenuInflater
import android.view.MotionEvent
import android.view.View
import androidx.media3.common.util.UnstableApi
import de.danoeh.antennapod.R
import de.danoeh.antennapod.fragment.swipeactions.SwipeActions
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.view.viewholder.EpisodeItemViewHolder

/**
 * List adapter for the queue.
 */
open class QueueRecyclerAdapter(mainActivity: MainActivity, swipeActions: SwipeActions) : EpisodeItemListAdapter(mainActivity) {
    private val swipeActions: SwipeActions = swipeActions
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
        if (!dragDropEnabled) {
            holder.dragHandle.setVisibility(View.GONE)
            holder.dragHandle.setOnTouchListener(null)
            holder.coverHolder.setOnTouchListener(null)
        } else {
            holder.dragHandle.setVisibility(View.VISIBLE)
            holder.dragHandle.setOnTouchListener { v1: View?, event: MotionEvent ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    Log.d(TAG, "startDrag()")
                    swipeActions.startDrag(holder)
                }
                false
            }
            holder.coverHolder.setOnTouchListener { v1, event ->
                if (event.actionMasked === MotionEvent.ACTION_DOWN) {
                    val isLtr = holder.itemView.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR
                    val factor = (if (isLtr) 1 else -1).toFloat()
                    if (factor * event.x < factor * 0.5 * v1.width) {
                        Log.d(TAG, "startDrag()")
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
            holder.coverHolder.setOnTouchListener(null)
        }

        holder.isInQueue.setVisibility(View.GONE)
    }

    @UnstableApi override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
        val inflater: MenuInflater = activity!!.getMenuInflater()
        inflater.inflate(R.menu.queue_context, menu)
        super.onCreateContextMenu(menu, v, menuInfo)

        if (!inActionMode()) {
            menu.findItem(R.id.multi_select).setVisible(true)
            val keepSorted: Boolean = UserPreferences.isQueueKeepSorted
            if (getItem(0).id === longPressedItem?.id || keepSorted) {
                menu.findItem(R.id.move_to_top_item).setVisible(false)
            }
            if (getItem(itemCount - 1).id === longPressedItem?.id || keepSorted) {
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
