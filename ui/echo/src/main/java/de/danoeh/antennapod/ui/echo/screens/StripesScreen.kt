package de.danoeh.antennapod.ui.echo.screens

import android.content.Context
import android.graphics.Canvas

class StripesScreen(context: Context?) : BaseScreen(context) {
    init {
        for (i in 0 until NUM_PARTICLES) {
            particles.add(Particle((2f * i / NUM_PARTICLES - 1f).toDouble(), 0.0, 0.0, 0.0))
        }
    }

    override fun draw(canvas: Canvas) {
        paintParticles.strokeWidth = 0.05f * bounds.width()
        super.draw(canvas)
    }

    override fun drawParticle(canvas: Canvas, p: Particle?, width: Float, height: Float,
                              innerBoxX: Float, innerBoxY: Float, innerBoxSize: Float
    ) {
        val strokeWidth = 0.05f * width
        val x = (width * p!!.positionX).toFloat()
        canvas.drawLine(x, -strokeWidth, x + width, height + strokeWidth, paintParticles)
    }

    override fun particleTick(p: Particle?, timeSinceLastFrame: Long) {
        p!!.positionX += 0.00005 * timeSinceLastFrame
        if (p!!.positionX > 1f) {
            p.positionX -= 2.0
        }
    }

    companion object {
        protected const val NUM_PARTICLES: Int = 15
    }
}
