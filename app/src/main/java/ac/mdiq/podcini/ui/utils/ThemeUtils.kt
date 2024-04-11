package ac.mdiq.podcini.ui.utils

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat

object ThemeUtils {
    @JvmStatic
    @ColorInt
    fun getColorFromAttr(context: Context, @AttrRes attr: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
//        Log.d("ThemeUtils", "getColorFromAttr ${attr.toHexString()}, ${typedValue.resourceId.toHexString()} ${typedValue.data.toHexString()}")
        if (typedValue.resourceId != 0) {
            return ContextCompat.getColor(context, typedValue.resourceId)
        }
        return typedValue.data
    }

    @JvmStatic
    @DrawableRes
    fun getDrawableFromAttr(context: Context, @AttrRes attr: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return typedValue.resourceId
    }
}
