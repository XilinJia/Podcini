package ac.mdiq.podcini.ui.statistics

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class PieChartView : AppCompatImageView {
    private var drawable: PieChartDrawable? = null

    constructor(context: Context?) : super(context!!) {
        setup()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        setup()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context!!, attrs, defStyleAttr) {
        setup()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setup() {
        drawable = PieChartDrawable()
        setImageDrawable(drawable)
    }

    /**
     * Set of data values to display.
     */
    fun setData(data: PieChartData?) {
        drawable!!.data = data
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = measuredWidth
        setMeasuredDimension(width, width / 2)
    }

    class PieChartData(val values: FloatArray) {
        val sum: Float

        init {
            var valueSum = 0f
            for (datum in values) {
                valueSum += datum
            }
            this.sum = valueSum
        }

        fun getPercentageOfItem(index: Int): Float {
            if (sum == 0f) {
                return 0f
            }
            return values[index] / sum
        }

        fun isLargeEnoughToDisplay(index: Int): Boolean {
            return getPercentageOfItem(index) > 0.04
        }

        fun getColorOfItem(index: Int): Int {
            if (!isLargeEnoughToDisplay(index)) {
                return Color.GRAY
            }
            return COLOR_VALUES[index % COLOR_VALUES.size]
        }

        companion object {
            private val COLOR_VALUES = intArrayOf(-0xc88a1a, -0x1ae3dd, -0x6800, -0xda64dc, -0x63d850,
                -0xff663a, -0x22bb89, -0x995600, -0x47d1d2, -0xce9c6b,
                -0x66bb67, -0xdd5567, -0x5555ef, -0x99cc34, -0xff8c1a)
        }
    }

    private class PieChartDrawable : Drawable() {
        var data: PieChartData? = null
        private val paint = Paint()

        init {
            paint.flags = Paint.ANTI_ALIAS_FLAG
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeCap = Paint.Cap.ROUND
        }

        override fun draw(canvas: Canvas) {
            val strokeSize = bounds.height() / 30f
            paint.strokeWidth = strokeSize

            val radius = bounds.height() - strokeSize
            val center = bounds.width() / 2f
            val arcBounds = RectF(center - radius, strokeSize, center + radius, strokeSize + radius * 2)

            var startAngle = 180f
            for (i in data!!.values.indices) {
                if (!data!!.isLargeEnoughToDisplay(i)) {
                    break
                }
                paint.color = data!!.getColorOfItem(i)
                val padding = if (i == 0) PADDING_DEGREES / 2 else PADDING_DEGREES
                val sweepAngle = (180f - PADDING_DEGREES) * data!!.getPercentageOfItem(i)
                canvas.drawArc(arcBounds, startAngle + padding, sweepAngle - padding, false, paint)
                startAngle += sweepAngle
            }

            paint.color = Color.GRAY
            val sweepAngle = 360 - startAngle - PADDING_DEGREES / 2
            if (sweepAngle > PADDING_DEGREES) {
                canvas.drawArc(arcBounds, startAngle + PADDING_DEGREES, sweepAngle - PADDING_DEGREES, false, paint)
            }
        }

        override fun getOpacity(): Int {
            return PixelFormat.TRANSLUCENT
        }

        override fun setAlpha(alpha: Int) {
        }

        override fun setColorFilter(cf: ColorFilter?) {
        }

        companion object {
            private const val PADDING_DEGREES = 3f
        }
    }
}
