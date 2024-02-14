package de.danoeh.antennapod.dialog

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.text.format.DateFormat
import android.view.MotionEvent
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.ui.common.ThemeUtils.getColorFromAttr
import java.util.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class TimeRangeDialog(context: Context, from: Int, to: Int) : MaterialAlertDialogBuilder(context) {
    private val view = TimeRangeView(context, from, to)

    init {
        setView(view)
        setPositiveButton(android.R.string.ok, null)
    }

    val from: Int
        get() = view.from

    val to: Int
        get() = view.to

    internal class TimeRangeView @JvmOverloads constructor(context: Context?,
                                                           internal var from: Int = 0,
                                                           var to: Int = 0
    ) : View(context) {
        private val paintDial = Paint()
        private val paintSelected = Paint()
        private val paintText = Paint()
        private val bounds = RectF()
        var touching: Int = 0

        init {
            setup()
        }

        private fun setup() {
            paintDial.isAntiAlias = true
            paintDial.style = Paint.Style.STROKE
            paintDial.strokeCap = Paint.Cap.ROUND
            paintDial.color = getColorFromAttr(context, android.R.attr.textColorPrimary)
            paintDial.alpha = DIAL_ALPHA

            paintSelected.isAntiAlias = true
            paintSelected.style = Paint.Style.STROKE
            paintSelected.strokeCap = Paint.Cap.ROUND
            paintSelected.color = getColorFromAttr(context, R.attr.colorAccent)

            paintText.isAntiAlias = true
            paintText.style = Paint.Style.FILL
            paintText.color =
                getColorFromAttr(context, android.R.attr.textColorPrimary)
            paintText.textAlign = Paint.Align.CENTER
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY
                    && MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            } else if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
                super.onMeasure(widthMeasureSpec, widthMeasureSpec)
            } else if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
                super.onMeasure(heightMeasureSpec, heightMeasureSpec)
            } else if (MeasureSpec.getSize(widthMeasureSpec) < MeasureSpec.getSize(heightMeasureSpec)) {
                super.onMeasure(widthMeasureSpec, widthMeasureSpec)
            } else {
                super.onMeasure(heightMeasureSpec, heightMeasureSpec)
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val size = height.toFloat() // square
            val padding = size * 0.1f
            paintDial.strokeWidth = size * 0.005f
            bounds[padding, padding, size - padding] = size - padding

            paintText.alpha = DIAL_ALPHA
            canvas.drawArc(bounds, 0f, 360f, false, paintDial)
            for (i in 0..23) {
                paintDial.strokeWidth = size * 0.005f
                if (i % 6 == 0) {
                    paintDial.strokeWidth = size * 0.01f
                    val textPos = radToPoint(i / 24.0f * 360f, size / 2 - 2.5f * padding)
                    paintText.textSize = 0.4f * padding
                    canvas.drawText(i.toString(), textPos.x.toFloat(),
                        textPos.y + (-paintText.descent() - paintText.ascent()) / 2, paintText)
                }
                val outer = radToPoint(i / 24.0f * 360f, size / 2 - 1.7f * padding)
                val inner = radToPoint(i / 24.0f * 360f, size / 2 - 1.9f * padding)
                canvas.drawLine(outer.x.toFloat(), outer.y.toFloat(), inner.x.toFloat(), inner.y.toFloat(), paintDial)
            }
            paintText.alpha = 255

            val angleFrom = from.toFloat() / 24 * 360 - 90
            val angleDistance = ((to - from + 24) % 24).toFloat() / 24 * 360
            paintSelected.strokeWidth = padding / 6
            paintSelected.style = Paint.Style.STROKE
            canvas.drawArc(bounds, angleFrom, angleDistance, false, paintSelected)
            paintSelected.style = Paint.Style.FILL
            val p1 = radToPoint(angleFrom + 90, size / 2 - padding)
            canvas.drawCircle(p1.x.toFloat(), p1.y.toFloat(), padding / 2, paintSelected)
            val p2 = radToPoint(angleFrom + angleDistance + 90, size / 2 - padding)
            canvas.drawCircle(p2.x.toFloat(), p2.y.toFloat(), padding / 2, paintSelected)

            paintText.textSize = 0.6f * padding
            val timeRange = if (from == to) {
                context.getString(R.string.sleep_timer_always)
            } else if (DateFormat.is24HourFormat(context)) {
                String.format(Locale.getDefault(), "%02d:00 - %02d:00", from, to)
            } else {
                String.format(Locale.getDefault(), "%02d:00 %s - %02d:00 %s", from % 12,
                    if (from >= 12) "PM" else "AM", to % 12, if (to >= 12) "PM" else "AM")
            }
            canvas.drawText(timeRange, size / 2, (size - paintText.descent() - paintText.ascent()) / 2, paintText)
        }

        protected fun radToPoint(angle: Float, radius: Float): Point {
            return Point((width / 2 + radius * sin(-angle * Math.PI / 180 + Math.PI)).toInt(),
                (height / 2 + radius * cos(-angle * Math.PI / 180 + Math.PI)).toInt())
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            parent.requestDisallowInterceptTouchEvent(true)
            val center = Point(width / 2, height / 2)
            val angleRad = atan2((center.y - event.y).toDouble(), (center.x - event.x).toDouble())
            var angle = (angleRad * (180 / Math.PI)).toFloat()
            angle += (360 + 360 - 90).toFloat()
            angle %= 360f

            if (event.action == MotionEvent.ACTION_DOWN) {
                val fromDistance = abs((angle - from.toFloat() / 24 * 360).toDouble()).toFloat()
                val toDistance = abs((angle - to.toFloat() / 24 * 360).toDouble()).toFloat()
                if (fromDistance < 15 || fromDistance > (360 - 15)) {
                    touching = 1
                    return true
                } else if (toDistance < 15 || toDistance > (360 - 15)) {
                    touching = 2
                    return true
                }
            } else if (event.action == MotionEvent.ACTION_MOVE) {
                val newTime = (24 * (angle / 360.0)).toInt()
                if (from == to && touching != 0) {
                    // Switch which handle is focussed such that selection is the smaller arc
                    touching = if ((((newTime - to + 24) % 24) < 12)) 2 else 1
                }
                if (touching == 1) {
                    from = newTime
                    invalidate()
                    return true
                } else if (touching == 2) {
                    to = newTime
                    invalidate()
                    return true
                }
            } else if (touching != 0) {
                touching = 0
                return true
            }
            return super.onTouchEvent(event)
        }

        companion object {
            private const val DIAL_ALPHA = 120
        }
    }
}
