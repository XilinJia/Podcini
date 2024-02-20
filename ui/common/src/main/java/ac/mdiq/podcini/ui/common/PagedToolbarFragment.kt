package ac.mdiq.podcini.ui.common

import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.appbar.MaterialToolbar

/**
 * Fragment with a ViewPager where the displayed items influence the top toolbar's menu.
 * All items share the same general menu items and are just allowed to show/hide them.
 */
abstract class PagedToolbarFragment : Fragment() {
    private var toolbar: MaterialToolbar? = null
    private var viewPager: ViewPager2? = null

    /**
     * Invalidate the toolbar menu if the current child fragment is visible.
     * @param child The fragment to invalidate
     */
    fun invalidateOptionsMenuIfActive(child: Fragment) {
        val visibleChild = childFragmentManager.findFragmentByTag("f" + viewPager!!.currentItem)
        if (visibleChild === child) {
            visibleChild.onPrepareOptionsMenu(toolbar!!.menu)
        }
    }

    protected fun setupPagedToolbar(toolbar: MaterialToolbar, viewPager: ViewPager2) {
        this.toolbar = toolbar
        this.viewPager = viewPager

        toolbar.setOnMenuItemClickListener { item: MenuItem? ->
            if (this.onOptionsItemSelected(item!!)) {
                return@setOnMenuItemClickListener true
            }
            val child = childFragmentManager.findFragmentByTag("f" + viewPager.currentItem)
            if (child != null) {
                return@setOnMenuItemClickListener child.onOptionsItemSelected(item)
            }
            false
        }
        viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val child = childFragmentManager.findFragmentByTag("f$position")
                child?.onPrepareOptionsMenu(toolbar.menu)
            }
        })
    }
}
