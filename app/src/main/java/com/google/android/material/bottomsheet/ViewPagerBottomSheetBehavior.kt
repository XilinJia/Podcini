package com.google.android.material.bottomsheet

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.lang.ref.WeakReference

/**
 * Override [.findScrollingChild] to support [ViewPager]'s nested scrolling.
 * In order to override package level method and field.
 * This class put in the same package path where [BottomSheetBehavior] located.
 * Source: https://medium.com/@hanru.yeh/funny-solution-that-makes-bottomsheetdialog-support-viewpager-with-nestedscrollingchilds-bfdca72235c3
 */
open class ViewPagerBottomSheetBehavior<V : View?> : BottomSheetBehavior<V> {
    constructor() : super()

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    public override fun findScrollingChild(view: View): View? {
        if (view.isNestedScrollingEnabled) {
            return view
        }

        if (view is ViewPager2) {
            val recycler = view.getChildAt(0) as RecyclerView
            val currentViewPagerChild = recycler.getChildAt(view.currentItem)
            if (currentViewPagerChild != null) {
                return findScrollingChild(currentViewPagerChild)
            }
        } else if (view is ViewGroup) {
            var i = 0
            val count = view.childCount
            while (i < count) {
                val scrollingChild = findScrollingChild(view.getChildAt(i))
                if (scrollingChild != null) {
                    return scrollingChild
                }
                i++
            }
        }
        return null
    }

    fun updateScrollingChild() {
        val childView = viewRef?.get() ?: return
        val scrollingChild = findScrollingChild(childView)
        nestedScrollingChildRef = WeakReference(scrollingChild)
    }
}