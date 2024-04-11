package ac.mdiq.podcini.ui.statistics.years


import ac.mdiq.podcini.R
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import ac.mdiq.podcini.storage.DBReader.MonthlyStatisticsItem
import ac.mdiq.podcini.ui.utils.ThemeUtils.getColorFromAttr
import kotlin.math.floor
import kotlin.math.max

class BarChartView : AppCompatImageView {
    private var drawable: BarChartDrawable? = null

    constructor(context: Context) : super(context!!) {
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context!!, attrs) {
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context!!, attrs, defStyleAttr) {
        setup()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setup() {
        drawable = BarChartDrawable()
        setImageDrawable(drawable)
    }

    /**
     * Set of data values to display.
     */
    fun setData(data: List<MonthlyStatisticsItem>) {
        drawable!!.data = data
        drawable!!.maxValue = 1
        for (item in data) {
            drawable!!.maxValue = max(drawable!!.maxValue.toDouble(), item.timePlayed.toDouble()).toLong()
        }
    }

    private inner class BarChartDrawable : Drawable() {
        private val ONE_HOUR = 3600000L

        var data: List<MonthlyStatisticsItem>? = null
        var maxValue: Long = 1
        private val paintBars: Paint
        private val paintGridLines: Paint
        private val paintGridText: Paint
        private val colors = intArrayOf(0, -0x63d850)

        init {
            colors[0] = getColorFromAttr(context, R.attr.colorAccent)
            paintBars = Paint()
            paintBars.style = Paint.Style.FILL
            paintBars.isAntiAlias = true
            paintGridLines = Paint()
            paintGridLines.style = Paint.Style.STROKE
            paintGridLines.setPathEffect(DashPathEffect(floatArrayOf(10f, 10f), 0f))
            paintGridLines.color =
                getColorFromAttr(context, android.R.attr.textColorSecondary)
            paintGridText = Paint()
            paintGridText.isAntiAlias = true
            paintGridText.color =
                getColorFromAttr(context, android.R.attr.textColorSecondary)
        }

        override fun draw(canvas: Canvas) {
            val width = bounds.width().toFloat()
            val height = bounds.height().toFloat()
            val barHeight = height * 0.9f
            val textPadding = width * 0.05f
            val stepSize = (width - textPadding) / (data!!.size + 2)
            val textSize = height * 0.06f
            paintGridText.textSize = textSize

            paintBars.strokeWidth = height * 0.015f
            paintBars.color = colors[0]
            var colorIndex = 0
            var lastYear = if (data!!.size > 0) data!![0].year else 0
            for (i in data!!.indices) {
                val x = textPadding + (i + 1) * stepSize
                if (lastYear != data!![i].year) {
                    lastYear = data!![i].year
                    colorIndex++
                    paintBars.color = colors[colorIndex % 2]
                    if (i < data!!.size - 2) {
                        canvas.drawText(data!![i].year.toString(), x + stepSize,
                            barHeight + (height - barHeight + textSize) / 2, paintGridText)
                    }
                    canvas.drawLine(x, height, x, barHeight, paintGridText)
                }

                val valuePercentage = max(0.005, (data!![i].timePlayed.toFloat() / maxValue).toDouble())
                    .toFloat()
                val y = (1 - valuePercentage) * barHeight
                canvas.drawRect(x, y, x + stepSize * 0.95f, barHeight, paintBars)
            }

            val maxLine = (floor(maxValue / (10.0 * ONE_HOUR)) * 10 * ONE_HOUR).toFloat()
            var y = (1 - (maxLine / maxValue)) * barHeight
            canvas.drawLine(0f, y, width, y, paintGridLines)
            canvas.drawText((maxLine.toLong() / ONE_HOUR).toString(), 0f, y + 1.2f * textSize, paintGridText)

            val midLine = maxLine / 2
            y = (1 - (midLine / maxValue)) * barHeight
            canvas.drawLine(0f, y, width, y, paintGridLines)
            canvas.drawText((midLine.toLong() / ONE_HOUR).toString(), 0f, y + 1.2f * textSize, paintGridText)
        }

        override fun getOpacity(): Int {
            return PixelFormat.TRANSLUCENT
        }

        override fun setAlpha(alpha: Int) {
        }

        override fun setColorFilter(cf: ColorFilter?) {
        }
    }
}
