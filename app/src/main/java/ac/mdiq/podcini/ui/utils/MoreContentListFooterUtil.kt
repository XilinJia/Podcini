package ac.mdiq.podcini.ui.utils

import ac.mdiq.podcini.databinding.MoreContentListFooterBinding
import android.view.View

/**
 * Utility methods for the more_content_list_footer layout.
 */
class MoreContentListFooterUtil(val root: View) {
    private var loading = false

    private var listener: Listener? = null

    init {
        root.setOnClickListener {
            if (!loading) listener?.onClick()
        }
    }

    fun setLoadingState(newState: Boolean) {
        val binding = MoreContentListFooterBinding.bind(root)
        val imageView = binding.imgExpand
        val progressBar = binding.progBar
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
