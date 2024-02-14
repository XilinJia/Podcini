package de.danoeh.antennapod.view

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import de.danoeh.antennapod.R

abstract class ToolbarIconTintManager(private val context: Context,
                                      private val toolbar: MaterialToolbar,
                                      private val collapsingToolbar: CollapsingToolbarLayout
) : OnOffsetChangedListener {
    private var isTinted = false

    override fun onOffsetChanged(appBarLayout: AppBarLayout, offset: Int) {
        val tint = (collapsingToolbar.height + offset) > (2 * collapsingToolbar.minimumHeight)
        if (isTinted != tint) {
            isTinted = tint
            updateTint()
        }
    }

    fun updateTint() {
        if (isTinted) {
            doTint(ContextThemeWrapper(context, R.style.Theme_AntennaPod_Dark))
            safeSetColorFilter(toolbar.navigationIcon, PorterDuffColorFilter(-0x1, PorterDuff.Mode.SRC_ATOP))
            safeSetColorFilter(toolbar.overflowIcon, PorterDuffColorFilter(-0x1, PorterDuff.Mode.SRC_ATOP))
            safeSetColorFilter(toolbar.collapseIcon, PorterDuffColorFilter(-0x1, PorterDuff.Mode.SRC_ATOP))
        } else {
            doTint(context)
            safeSetColorFilter(toolbar.navigationIcon, null)
            safeSetColorFilter(toolbar.overflowIcon, null)
            safeSetColorFilter(toolbar.collapseIcon, null)
        }
    }

    private fun safeSetColorFilter(icon: Drawable?, filter: PorterDuffColorFilter?) {
        if (icon != null) {
            icon.colorFilter = filter
        }
    }

    /**
     * View expansion was changed. Icons need to be tinted
     * @param themedContext ContextThemeWrapper with dark theme while expanded
     */
    protected abstract fun doTint(themedContext: Context?)
}
