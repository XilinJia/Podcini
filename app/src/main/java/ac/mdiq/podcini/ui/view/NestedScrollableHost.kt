/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Source: https://github.com/android/views-widgets-samples/blob/87e58d1/ViewPager2/app/src/main/java/androidx/viewpager2/integration/testapp/NestedScrollableHost.kt
 * And modified for our need
 */
package ac.mdiq.podcini.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
import ac.mdiq.podcini.R
import kotlin.math.abs

/**
 * Layout to wrap a scrollable component inside a ViewPager2. Provided as a solution to the problem
 * where pages of ViewPager2 have nested scrollable elements that scroll in the same direction as
 * ViewPager2. The scrollable element needs to be the immediate and only child of this host layout.
 *
 * This solution has limitations when using multiple levels of nested scrollable elements
 * (e.g. a horizontal RecyclerView in a vertical RecyclerView in a horizontal ViewPager2).
 */
// KhaledAlharthi/NestedScrollableHost.java
class NestedScrollableHost : FrameLayout {
    private var parentViewPager: ViewPager2? = null
    private var touchSlop = 0
    private var initialX = 0f
    private var initialY = 0f
    private var preferVertical = 1
    private var preferHorizontal = 1
    private var scrollDirection = 0

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
        setAttributes(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context)
        setAttributes(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?,
                defStyleAttr: Int, defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(context)
        setAttributes(context, attrs)
    }

    private fun setAttributes(context: Context, attrs: AttributeSet?) {
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.NestedScrollableHost, 0, 0)

        try {
            preferHorizontal = a.getInteger(R.styleable.NestedScrollableHost_preferHorizontal, 1)
            preferVertical = a.getInteger(R.styleable.NestedScrollableHost_preferVertical, 1)
            scrollDirection = a.getInteger(R.styleable.NestedScrollableHost_scrollDirection, 0)
        } finally {
            a.recycle()
        }
    }

    private fun init(context: Context) {
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop


        viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                var v = parent as? View
                while (v != null && v !is ViewPager2 || isntSameDirection(v)) {
                    v = v!!.parent as? View
                }
                parentViewPager = v as? ViewPager2

                viewTreeObserver.removeOnPreDrawListener(this)
                return false
            }
        })
    }

    private fun isntSameDirection(v: View?): Boolean {
        val orientation: Int = when (scrollDirection) {
            0 -> return false
            1 -> ViewPager2.ORIENTATION_VERTICAL
            2 -> ViewPager2.ORIENTATION_HORIZONTAL
            else -> return false
        }
        return ((v is ViewPager2) && v.orientation != orientation)
    }


    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        handleInterceptTouchEvent(ev)
        return super.onInterceptTouchEvent(ev)
    }


    private fun canChildScroll(orientation: Int, delta: Float): Boolean {
        val direction = -delta.toInt()
        val child = getChildAt(0)
        return when (orientation) {
            0 -> {
                child.canScrollHorizontally(direction)
            }
            1 -> {
                child.canScrollVertically(direction)
            }
            else -> {
                throw IllegalArgumentException()
            }
        }
    }

    private fun handleInterceptTouchEvent(e: MotionEvent) {
        if (parentViewPager == null) {
            return
        }
        val orientation = parentViewPager!!.orientation
        val preferedDirection = preferHorizontal + preferVertical > 2

        // Early return if child can't scroll in same direction as parent and theres no prefered scroll direction
        if (!canChildScroll(orientation, -1f) && !canChildScroll(orientation, 1f) && !preferedDirection) {
            return
        }

        if (e.action == MotionEvent.ACTION_DOWN) {
            initialX = e.x
            initialY = e.y
            parent.requestDisallowInterceptTouchEvent(true)
        } else if (e.action == MotionEvent.ACTION_MOVE) {
            val dx = e.x - initialX
            val dy = e.y - initialY
            val isVpHorizontal = orientation == ViewPager2.ORIENTATION_HORIZONTAL

            // assuming ViewPager2 touch-slop is 2x touch-slop of child
            val scaledDx = (abs(dx.toDouble()) * (if (isVpHorizontal) 1f else 0.5f) * preferHorizontal).toFloat()
            val scaledDy = (abs(dy.toDouble()) * (if (isVpHorizontal) 0.5f else 1f) * preferVertical).toFloat()
            if (scaledDx > touchSlop || scaledDy > touchSlop) {
                if (isVpHorizontal == (scaledDy > scaledDx)) {
                    // Gesture is perpendicular, allow all parents to intercept
                    parent.requestDisallowInterceptTouchEvent(preferedDirection)
                } else {
                    // Gesture is parallel, query child if movement in that direction is possible
                    if (canChildScroll(orientation, if (isVpHorizontal) dx else dy)) {
                        // Child can scroll, disallow all parents to intercept
                        parent.requestDisallowInterceptTouchEvent(true)
                    } else {
                        // Child cannot scroll, allow all parents to intercept
                        parent.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }
        }
    }
}
