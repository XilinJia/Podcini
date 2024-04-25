package ac.mdiq.podcini.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.ViewPagerBottomSheetBehavior

/**
 * Based on https://stackoverflow.com/a/40798214
 */
class LockableBottomSheetBehavior<V : View?> : ViewPagerBottomSheetBehavior<V> {
    private var isLocked = false

    constructor()

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    fun setLocked(locked: Boolean) {
        isLocked = locked
    }

    override fun onInterceptTouchEvent(parent: CoordinatorLayout, child: V & Any, event: MotionEvent): Boolean {
        var handled = false

        if (!isLocked) handled = super.onInterceptTouchEvent(parent, child, event)

        return handled
    }

    override fun onTouchEvent(parent: CoordinatorLayout, child: V & Any, event: MotionEvent): Boolean {
        var handled = false

        if (!isLocked) handled = super.onTouchEvent(parent, child, event)

        return handled
    }

    override fun onStartNestedScroll(coordinatorLayout: CoordinatorLayout, child: V & Any, directTargetChild: View, target: View, axes: Int, type: Int): Boolean {
        var handled = false

        if (!isLocked) handled = super.onStartNestedScroll(coordinatorLayout, child, directTargetChild, target, axes, type)

        return handled
    }

    override fun onNestedPreScroll(coordinatorLayout: CoordinatorLayout, child: V & Any, target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        if (!isLocked) super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type)
    }

    override fun onStopNestedScroll(coordinatorLayout: CoordinatorLayout, child: V & Any, target: View, type: Int) {
        if (!isLocked) super.onStopNestedScroll(coordinatorLayout, child, target, type)
    }

    override fun onNestedPreFling(coordinatorLayout: CoordinatorLayout, child: V & Any, target: View, velocityX: Float, velocityY: Float): Boolean {
        var handled = false

        if (!isLocked) handled = super.onNestedPreFling(coordinatorLayout, child, target, velocityX, velocityY)

        return handled
    }
}
