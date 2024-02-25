package ac.mdiq.podcini.ui.common


import ac.mdiq.podcini.R
import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/**
 * From http://stackoverflow.com/a/19449488/6839
 */
class SquareImageView : AppCompatImageView {
    private var direction = DIRECTION_WIDTH

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        loadAttrs(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        loadAttrs(context, attrs)
    }

    private fun loadAttrs(context: Context, attrs: AttributeSet?) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.SquareImageView)
        direction = a.getInt(R.styleable.SquareImageView_direction, DIRECTION_WIDTH)
        a.recycle()
    }

    fun setDirection(direction: Int) {
        this.direction = direction
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        when (direction) {
            DIRECTION_MINIMUM -> {
                val size = min(measuredWidth.toDouble(), measuredHeight.toDouble()).toInt()
                setMeasuredDimension(size, size)
            }
            DIRECTION_HEIGHT -> setMeasuredDimension(measuredHeight, measuredHeight)
            else -> setMeasuredDimension(measuredWidth, measuredWidth)
        }
    }

    companion object {
        const val DIRECTION_WIDTH: Int = 0
        const val DIRECTION_HEIGHT: Int = 1
        const val DIRECTION_MINIMUM: Int = 2
    }
}