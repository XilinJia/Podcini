package ac.mdiq.podvinci.ui.common

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.min

class CircularProgressBar : View {
    private val paintBackground = Paint()
    private val paintProgress = Paint()
    private var percentage = 0f
    private var targetPercentage = 0f
    private var isIndeterminate = false
    private var tag: Any? = null
    private val bounds = RectF()

    constructor(context: Context?) : super(context) {
        setup(null)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        setup(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setup(attrs)
    }

    private fun setup(attrs: AttributeSet?) {
        paintBackground.isAntiAlias = true
        paintBackground.style = Paint.Style.STROKE

        paintProgress.isAntiAlias = true
        paintProgress.style = Paint.Style.STROKE
        paintProgress.strokeCap = Paint.Cap.ROUND

        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.CircularProgressBar)
        val color = typedArray.getColor(R.styleable.CircularProgressBar_foregroundColor, Color.GREEN)
        typedArray.recycle()
        paintProgress.color = color
        paintBackground.color = color
    }

    /**
     * Sets the percentage to be displayed.
     * @param percentage Number from 0 to 1
     * @param tag When the tag is the same as last time calling setPercentage, the update is animated
     */
    fun setPercentage(percentage: Float, tag: Any?) {
        targetPercentage = percentage

        if (tag == null || tag != this.tag) {
            // Do not animate
            this.percentage = percentage
            this.tag = tag
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = height * 0.07f
        paintBackground.strokeWidth = height * 0.02f
        paintBackground.setPathEffect(if (isIndeterminate) DASHED else null)
        paintProgress.strokeWidth = padding
        bounds[padding, padding, width - padding] = height - padding
        canvas.drawArc(bounds, -90f, 360f, false, paintBackground)

        if (MINIMUM_PERCENTAGE <= percentage && percentage <= MAXIMUM_PERCENTAGE) {
            canvas.drawArc(bounds, -90f, percentage * 360, false, paintProgress)
        }

        if (abs((percentage - targetPercentage).toDouble()) > MINIMUM_PERCENTAGE) {
            var speed = 0.02f
            if (abs((targetPercentage - percentage).toDouble()) < 0.1 && targetPercentage > percentage) {
                speed = 0.006f
            }
            val delta = min(speed.toDouble(), abs((targetPercentage - percentage).toDouble()))
                .toFloat()
            val direction = (if ((targetPercentage - percentage) > 0) 1f else -1f)
            percentage += delta * direction
            invalidate()
        }
    }

    fun setIndeterminate(indeterminate: Boolean) {
        isIndeterminate = indeterminate
    }

    companion object {
        const val MINIMUM_PERCENTAGE: Float = 0.005f
        const val MAXIMUM_PERCENTAGE: Float = 1 - MINIMUM_PERCENTAGE
        private val DASHED: PathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
    }
}
