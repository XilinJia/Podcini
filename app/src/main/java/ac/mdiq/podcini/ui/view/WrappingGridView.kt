package ac.mdiq.podcini.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.GridView

/**
 * Source: https://stackoverflow.com/a/46350213/
 */
class WrappingGridView : GridView {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var heightSpec = heightMeasureSpec
        // The great Android "hackatlon", the love, the magic.
        // The two leftmost bits in the height measure spec have
        // a special meaning, hence we can't use them to describe height.
        if (layoutParams.height == LayoutParams.WRAP_CONTENT) heightSpec = MeasureSpec.makeMeasureSpec(Int.MAX_VALUE shr 2, MeasureSpec.AT_MOST)

        super.onMeasure(widthMeasureSpec, heightSpec)
    }
}
