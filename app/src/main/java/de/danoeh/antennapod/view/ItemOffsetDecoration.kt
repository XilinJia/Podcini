package de.danoeh.antennapod.view

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Source: https://stackoverflow.com/a/30794046
 */
class ItemOffsetDecoration(context: Context, itemOffsetDp: Int) : RecyclerView.ItemDecoration() {
    private val itemOffset = (itemOffsetDp * context.resources.displayMetrics.density).toInt()

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)
        outRect[itemOffset, itemOffset, itemOffset] = itemOffset
    }
}
