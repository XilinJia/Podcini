/*
 * Copyright (C) 2016 Shota Saito
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Source: https://github.com/shts/TriangleLabelView
 * Modified for our need; see Podcini #5925 for context
 */
package ac.mdiq.podcini.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.sqrt

class TriangleLabelView : View {
    private val primary = PaintHolder()
    private var topPadding = 0f
    private var bottomPadding = 0f
    private var centerPadding = 0f
    private var trianglePaint: Paint? = null
    private var width = 0
    private var height = 0
    private var corner: Corner? = null

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null,
                defStyleAttr: Int = 0
    ) : super(context, attrs, defStyleAttr) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?,
                defStyleAttr: Int, defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.TriangleLabelView)

        this.topPadding = ta.getDimension(R.styleable.TriangleLabelView_labelTopPadding, dp2px(7f).toFloat())
        this.centerPadding = ta.getDimension(R.styleable.TriangleLabelView_labelCenterPadding, dp2px(3f).toFloat())
        this.bottomPadding = ta.getDimension(R.styleable.TriangleLabelView_labelBottomPadding, dp2px(3f).toFloat())

        val backgroundColor = ta.getColor(R.styleable.TriangleLabelView_backgroundColor,
            Color.parseColor("#66000000"))
        primary.color = ta.getColor(R.styleable.TriangleLabelView_primaryTextColor, Color.WHITE)

        primary.size = ta.getDimension(R.styleable.TriangleLabelView_primaryTextSize, sp2px(11f))

        val primary = ta.getString(R.styleable.TriangleLabelView_primaryText)
        if (primary != null) {
            this.primary.text = primary
        }

        this.corner = Corner.from(ta.getInt(R.styleable.TriangleLabelView_corner, 1))

        ta.recycle()

        this.primary.initPaint()

        trianglePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        trianglePaint!!.color = backgroundColor

        this.primary.resetStatus()
    }

    fun setPrimaryText(text: String) {
        primary.text = text
        primary.resetStatus()
        relayout()
    }

    fun getCorner(): Corner? {
        return corner
    }

    fun setCorner(corner: Corner?) {
        this.corner = corner
        relayout()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()

        // translate
        canvas.translate(0f, ((height * sqrt(2.0)) - height).toFloat())

        // rotate
        if (corner!!.left()) {
            canvas.rotate(DEGREES_LEFT.toFloat(), 0f, height.toFloat())
        } else {
            canvas.rotate(DEGREES_RIGHT.toFloat(), width.toFloat(), height.toFloat())
        }

        // draw triangle
        @SuppressLint("DrawAllocation") val path = Path()
        path.moveTo(0f, height.toFloat())
        path.lineTo(width / 2f, 0f)
        path.lineTo(width.toFloat(), height.toFloat())
        path.close()
        canvas.drawPath(path, trianglePaint!!)

        // draw primaryText
        canvas.drawText(primary.text, (width) / 2f,
            (topPadding + centerPadding + primary.height), primary.paint!!)
        canvas.restore()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        height = (topPadding + centerPadding + bottomPadding + primary.height).toInt()
        width = 2 * height
        val realHeight = (height * sqrt(2.0)).toInt()
        setMeasuredDimension(width, realHeight)
    }

    fun dp2px(dpValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    fun sp2px(spValue: Float): Float {
        val scale = context.resources.displayMetrics.scaledDensity
        return spValue * scale
    }

    /**
     * Should be called whenever what we're displaying could have changed.
     */
    private fun relayout() {
        invalidate()
        requestLayout()
    }

    enum class Corner(private val type: Int) {
        TOP_LEFT(1),
        TOP_RIGHT(2);

        fun left(): Boolean {
            return this == TOP_LEFT
        }

        companion object {
            internal fun from(type: Int): Corner {
                for (c in entries) {
                    if (c.type == type) {
                        return c
                    }
                }
                return TOP_LEFT
            }
        }
    }

    private class PaintHolder {
        var text: String = ""
        var paint: Paint? = null
        var color: Int = 0
        var size: Float = 0f
        var height: Float = 0f
        var width: Float = 0f

        fun initPaint() {
            paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint!!.color = color
            paint!!.textAlign = Paint.Align.CENTER
            paint!!.textSize = size
            paint!!.setTypeface(Typeface.DEFAULT_BOLD)
        }

        fun resetStatus() {
            val rectText = Rect()
            paint!!.getTextBounds(text, 0, text.length, rectText)
            width = rectText.width().toFloat()
            height = rectText.height().toFloat()
        }
    }

    companion object {
        private const val DEGREES_LEFT = -45
        private const val DEGREES_RIGHT = 45
    }
}
