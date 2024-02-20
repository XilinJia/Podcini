package ac.mdiq.podcini.ui.echo.screens

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import ac.mdiq.podcini.ui.echo.R
import kotlin.math.abs
import kotlin.math.min

abstract class BaseScreen(context: Context?) : Drawable() {
    private val paintBackground = Paint()
    @JvmField
    protected val paintParticles: Paint = Paint()
    @JvmField
    protected val particles: ArrayList<Particle> = ArrayList()
    private val colorBackgroundFrom = ContextCompat.getColor(context!!, R.color.gradient_000)
    private val colorBackgroundTo = ContextCompat.getColor(context!!, R.color.gradient_100)
    private var lastFrame: Long = 0

    init {
        paintParticles.color = -0x1
        paintParticles.flags = Paint.ANTI_ALIAS_FLAG
        paintParticles.style = Paint.Style.FILL
        paintParticles.alpha = 25
    }

    override fun draw(canvas: Canvas) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        paintBackground.setShader(LinearGradient(0f, 0f, 0f, height,
            colorBackgroundFrom, colorBackgroundTo, Shader.TileMode.CLAMP))
        canvas.drawRect(0f, 0f, width, height, paintBackground)

        var timeSinceLastFrame = System.currentTimeMillis() - lastFrame
        lastFrame = System.currentTimeMillis()
        if (timeSinceLastFrame > 500) {
            timeSinceLastFrame = 0
        }
        val innerBoxSize = if ((abs((width - height).toDouble()) < 0.001f) // Square share version
        ) (0.9f * width) else ((0.9f * min(width.toDouble(), (0.7f * height).toDouble())).toFloat())
        val innerBoxX = (width - innerBoxSize) / 2
        val innerBoxY = (height - innerBoxSize) / 2

        for (p in particles) {
            drawParticle(canvas, p, width, height, innerBoxX, innerBoxY, innerBoxSize)
            particleTick(p, timeSinceLastFrame)
        }

        drawInner(canvas, innerBoxX, innerBoxY, innerBoxSize)
    }

    protected open fun drawInner(canvas: Canvas?, innerBoxX: Float, innerBoxY: Float, innerBoxSize: Float) {
    }

    protected abstract fun particleTick(p: Particle?, timeSinceLastFrame: Long)

    protected abstract fun drawParticle(canvas: Canvas, p: Particle?, width: Float, height: Float,
                                        innerBoxX: Float, innerBoxY: Float, innerBoxSize: Float
    )

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setAlpha(alpha: Int) {
    }

    override fun setColorFilter(cf: ColorFilter?) {
    }

    protected class Particle(@JvmField var positionX: Double, @JvmField var positionY: Double, @JvmField var positionZ: Double, @JvmField var speed: Double)
}
