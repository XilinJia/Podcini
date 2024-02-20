package ac.mdiq.podcini.ui.common

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class PlaybackSpeedIndicatorView : View {
    private val arcPaint = Paint()
    private val indicatorPaint = Paint()
    private val trianglePath = Path()
    private val arcBounds = RectF()
    private var angle = VALUE_UNSET
    private var targetAngle = VALUE_UNSET
    private var degreePerFrame = 1.6f
    private var paddingArc = 20f
    private var paddingIndicator = 10f

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
        setSpeed(1.0f) // Set default angle to 1.0
        targetAngle = VALUE_UNSET // Do not move to first value that is set externally

        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.PlaybackSpeedIndicatorView)
        val color = typedArray.getColor(R.styleable.PlaybackSpeedIndicatorView_foregroundColor, Color.GREEN)
        typedArray.recycle()
        arcPaint.color = color
        indicatorPaint.color = color

        arcPaint.isAntiAlias = true
        arcPaint.style = Paint.Style.STROKE
        arcPaint.strokeCap = Paint.Cap.ROUND

        indicatorPaint.isAntiAlias = true
        indicatorPaint.style = Paint.Style.FILL

        trianglePath.fillType = Path.FillType.EVEN_ODD
    }

    fun setSpeed(value: Float) {
        val maxAnglePerDirection = 90 + 45 - 2 * paddingArc
        // Speed values above 3 are probably not too common. Cap at 3 for better differentiation
        val normalizedValue = (min(2.5, (value - 0.5f).toDouble()) / 2.5f).toFloat() // Linear between 0 and 1
        val target = -maxAnglePerDirection + 2 * maxAnglePerDirection * normalizedValue
        if (targetAngle == VALUE_UNSET) {
            angle = target
        }
        targetAngle = target
        degreePerFrame = (abs((targetAngle - angle).toDouble()) / 20).toFloat()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        paddingArc = measuredHeight / 4.5f
        paddingIndicator = measuredHeight / 6f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val radiusInnerCircle = width / 10f
        canvas.drawCircle(width / 2f, height / 2f, radiusInnerCircle, indicatorPaint)

        trianglePath.rewind()
        val bigRadius = height / 2f - paddingIndicator
        trianglePath.moveTo(width / 2f + (bigRadius * sin(((-angle + 180) * DEG_2_RAD).toDouble())).toFloat(),
            height / 2f + (bigRadius * cos(((-angle + 180) * DEG_2_RAD).toDouble())).toFloat())
        trianglePath.lineTo(width / 2f + (radiusInnerCircle * sin(((-angle + 180 - 90) * DEG_2_RAD).toDouble())).toFloat(),
            height / 2f + (radiusInnerCircle * cos(((-angle + 180 - 90) * DEG_2_RAD).toDouble())).toFloat())
        trianglePath.lineTo(width / 2f + (radiusInnerCircle * sin(((-angle + 180 + 90) * DEG_2_RAD).toDouble())).toFloat(),
            height / 2f + (radiusInnerCircle * cos(((-angle + 180 + 90) * DEG_2_RAD).toDouble())).toFloat())
        trianglePath.close()
        canvas.drawPath(trianglePath, indicatorPaint)

        arcPaint.strokeWidth = height / 15f
        arcBounds[paddingArc, paddingArc, width - paddingArc] = height - paddingArc
        canvas.drawArc(arcBounds, (-180 - 45).toFloat(), 90 + 45 + angle - PADDING_ANGLE, false, arcPaint)
        canvas.drawArc(arcBounds, -90 + PADDING_ANGLE + angle, 90 + 45 - PADDING_ANGLE - angle, false, arcPaint)

        if (abs((angle - targetAngle).toDouble()) > 0.5 && targetAngle != VALUE_UNSET) {
            angle = (angle + sign((targetAngle - angle).toDouble()) * min(degreePerFrame.toDouble(), abs((targetAngle - angle).toDouble()))).toFloat()
            invalidate()
        }
    }

    companion object {
        private const val DEG_2_RAD = (Math.PI / 180).toFloat()
        private const val PADDING_ANGLE = 30f
        private const val VALUE_UNSET = -4242f
    }
}
