package ac.mdiq.podcini.ui.view

import android.animation.ValueAnimator
import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Workaround for app:liftOnScroll flickering when in SwipeRefreshLayout
 */
class LiftOnScrollListener(appBar: View) : RecyclerView.OnScrollListener(), NestedScrollView.OnScrollChangeListener {
    private val animator: ValueAnimator = ValueAnimator.ofFloat(0f, appBar.context.resources.displayMetrics.density * 8)
    private var animatingToScrolled = false

    init {
        animator.addUpdateListener { animation: ValueAnimator -> appBar.elevation = animation.animatedValue as Float }
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        elevate(isScrolled(recyclerView))
    }

    private fun isScrolled(recyclerView: RecyclerView): Boolean {
        val firstItem = (recyclerView.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition()?:-1
        when {
            firstItem < 0 -> return false
            firstItem > 0 -> return true
            else -> {
                val firstItemView = recyclerView.layoutManager?.findViewByPosition(firstItem)
                return if (firstItemView == null) false else firstItemView.top < 0
            }
        }
    }

    override fun onScrollChange(v: NestedScrollView, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {
        elevate(scrollY != 0)
    }

    private fun elevate(isScrolled: Boolean) {
        if (isScrolled == animatingToScrolled) return

        animatingToScrolled = isScrolled
        if (isScrolled) animator.start()
        else animator.reverse()
    }
}
