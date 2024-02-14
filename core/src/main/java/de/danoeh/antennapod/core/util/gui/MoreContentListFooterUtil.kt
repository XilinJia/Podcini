package de.danoeh.antennapod.core.util.gui

import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import de.danoeh.antennapod.core.R

/**
 * Utility methods for the more_content_list_footer layout.
 */
class MoreContentListFooterUtil(val root: View) {
    private var loading = false

    private var listener: Listener? = null

    init {
        root.setOnClickListener { v: View? ->
            if (listener != null && !loading) {
                listener!!.onClick()
            }
        }
    }

    fun setLoadingState(newState: Boolean) {
        val imageView = root.findViewById<ImageView>(R.id.imgExpand)
        val progressBar = root.findViewById<ProgressBar>(R.id.progBar)
        if (newState) {
            imageView.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
        } else {
            imageView.visibility = View.VISIBLE
            progressBar.visibility = View.GONE
        }
        loading = newState
    }

    fun setClickListener(l: Listener?) {
        listener = l
    }

    interface Listener {
        fun onClick()
    }
}
