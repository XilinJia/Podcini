package ac.mdiq.podcini.ui.echo

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.floor

class EchoProgress(private val numScreens: Int) : Drawable() {
    private val paint = Paint()
    private var progress = 0f

    init {
        paint.flags = Paint.ANTI_ALIAS_FLAG
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = -0x1
    }

    fun setProgress(progress: Float) {
        this.progress = progress
    }

    override fun draw(canvas: Canvas) {
        paint.strokeWidth = 0.5f * bounds.height()

        val y = 0.5f * bounds.height()
        val sectionWidth = 1.0f * bounds.width() / numScreens
        val sectionPadding = 0.03f * sectionWidth

        for (i in 0 until numScreens) {
            if (i + 1 < progress) {
                paint.alpha = 255
            } else {
                paint.alpha = 100
            }
            canvas.drawLine(i * sectionWidth + sectionPadding, y, (i + 1) * sectionWidth - sectionPadding, y, paint)
            if (floor(1.0 * i) == floor(progress.toDouble())) {
                paint.alpha = 255
                canvas.drawLine(i * sectionWidth + sectionPadding, y,
                    i * sectionWidth + sectionPadding + (progress - i) * (sectionWidth - 2 * sectionPadding), y, paint)
            }
        }
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setAlpha(alpha: Int) {
    }

    override fun setColorFilter(cf: ColorFilter?) {
    }
}
