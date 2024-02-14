package de.danoeh.antennapod.view

import androidx.recyclerview.widget.RecyclerView

/**
 * AdapterDataObserver that relays all events to the method anythingChanged().
 */
abstract class SimpleAdapterDataObserver : RecyclerView.AdapterDataObserver() {
    abstract fun anythingChanged()

    override fun onChanged() {
        anythingChanged()
    }

    override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
        anythingChanged()
    }

    override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
        anythingChanged()
    }

    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
        anythingChanged()
    }

    override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
        anythingChanged()
    }

    override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
        anythingChanged()
    }
}
