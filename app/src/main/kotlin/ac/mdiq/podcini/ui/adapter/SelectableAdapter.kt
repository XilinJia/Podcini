package ac.mdiq.podcini.ui.adapter

import ac.mdiq.podcini.R
import android.app.Activity
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.recyclerview.widget.RecyclerView

/**
 * Used by Recyclerviews that need to provide ability to select items.
 */
abstract class SelectableAdapter<T : RecyclerView.ViewHolder?>(private val activity: Activity) : RecyclerView.Adapter<T>() {

    private var actionMode: ActionMode? = null
    private val selectedIds = HashSet<Long>()
    private var onSelectModeListener: OnSelectModeListener? = null
    var shouldSelectLazyLoadedItems: Boolean = false
    internal var totalNumberOfItems = COUNT_AUTOMATICALLY

    val selectedCount: Int
        get() = selectedIds.size

    fun startSelectMode(pos: Int) {
        if (inActionMode()) endSelectMode()

        onSelectModeListener?.onStartSelectMode()

        shouldSelectLazyLoadedItems = false
        selectedIds.clear()
        selectedIds.add(getItemId(pos))
        notifyItemChanged(pos, "foo")
//        notifyDataSetChanged()

        actionMode = activity.startActionMode(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val inflater = mode.menuInflater
                inflater.inflate(R.menu.multi_select_options, menu)
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                updateTitle()
                toggleSelectAllIcon(menu.findItem(R.id.select_toggle), false)
                return false
            }
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    R.id.select_toggle -> {
                        val selectAll = selectedIds.size != itemCount
                        shouldSelectLazyLoadedItems = selectAll
                        setSelected(0, itemCount, selectAll)
                        toggleSelectAllIcon(item, selectAll)
                        updateTitle()
                        return true
                    }
                    R.id.select_all_above -> {
                        shouldSelectLazyLoadedItems = true
                        toggleSelected(0, pos)
                        return true
                    }
                    R.id.select_all_below -> {
                        shouldSelectLazyLoadedItems = true
                        toggleSelected(pos + 1, itemCount)
                        return true
                    }
                    else -> return false
                }
            }
            override fun onDestroyActionMode(mode: ActionMode) {
                callOnEndSelectMode()
                actionMode = null
                shouldSelectLazyLoadedItems = false
                selectedIds.clear()
                notifyDataSetChanged()
            }
        })
        updateTitle()
    }

    /**
     * End action mode if currently in select mode, otherwise do nothing
     */
    fun endSelectMode() {
        if (inActionMode()) {
            callOnEndSelectMode()
            actionMode?.finish()
        }
    }

    fun isSelected(pos: Int): Boolean {
        return selectedIds.contains(getItemId(pos))
    }

    /**
     * Set the selected state of item at given position
     * @param pos      the position to select
     * @param selected true for selected state and false for unselected
     */
    open fun setSelected(pos: Int, selected: Boolean) {
        if (selected) selectedIds.add(getItemId(pos))
        else selectedIds.remove(getItemId(pos))
        updateTitle()
    }

    /**
     * Set the selected state of item for a given range
     * @param startPos start position of range, inclusive
     * @param endPos   end position of range, inclusive
     * @param selected indicates the selection state
     * @throws IllegalArgumentException if start and end positions are not valid
     */
    @Throws(IllegalArgumentException::class)
    fun setSelected(startPos: Int, endPos: Int, selected: Boolean) {
        var i = startPos
        while (i < endPos && i < itemCount) {
            setSelected(i, selected)
            i++
        }
        notifyItemRangeChanged(startPos, (endPos - startPos))
    }

    fun toggleSelected(startPos: Int, endPos: Int) {
        var i = startPos
        while (i < endPos && i < itemCount) {
            toggleSelection(i)
            i++
        }
        notifyItemRangeChanged(startPos, (endPos - startPos))
    }

    protected fun toggleSelection(pos: Int) {
        setSelected(pos, !isSelected(pos))
        notifyItemChanged(pos)

        if (selectedIds.size == 0) endSelectMode()
    }

    fun inActionMode(): Boolean {
        return actionMode != null
    }

    private fun toggleSelectAllIcon(selectAllItem: MenuItem, allSelected: Boolean) {
        if (allSelected) {
            selectAllItem.setIcon(R.drawable.ic_select_none)
            selectAllItem.setTitle(R.string.deselect_all_label)
        } else {
            selectAllItem.setIcon(R.drawable.ic_select_all)
            selectAllItem.setTitle(R.string.select_all_label)
        }
    }

    fun updateTitle() {
        if (actionMode == null) return
        var totalCount = itemCount
        var selectedCount = selectedIds.size
        if (totalNumberOfItems != COUNT_AUTOMATICALLY) {
            totalCount = totalNumberOfItems
            if (shouldSelectLazyLoadedItems) selectedCount += (totalNumberOfItems - itemCount)
        }
        actionMode!!.title = activity.resources
            .getQuantityString(R.plurals.num_selected_label, selectedIds.size,
                selectedCount, totalCount)
    }

    fun setOnSelectModeListener(onSelectModeListener: OnSelectModeListener?) {
        this.onSelectModeListener = onSelectModeListener
    }

    private fun callOnEndSelectMode() {
        onSelectModeListener?.onEndSelectMode()
    }

    fun shouldSelectLazyLoadedItems(): Boolean {
        return shouldSelectLazyLoadedItems
    }

    /**
     * Sets the total number of items that could be lazy-loaded.
     * Can also be set to [.COUNT_AUTOMATICALLY] to simply use [.getItemCount]
     */
    fun setTotalNumberOfItems(totalNumberOfItems: Int) {
        this.totalNumberOfItems = totalNumberOfItems
    }

    interface OnSelectModeListener {
        fun onStartSelectMode()

        fun onEndSelectMode()
    }

    companion object {
        const val COUNT_AUTOMATICALLY: Int = -1
    }
}
