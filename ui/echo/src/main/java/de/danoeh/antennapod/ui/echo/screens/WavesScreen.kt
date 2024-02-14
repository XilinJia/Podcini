package de.danoeh.antennapod.ui.echo.screens

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint

class WavesScreen(context: Context?) : BaseScreen(context) {
    init {
        paintParticles.style = Paint.Style.STROKE
        for (i in 0 until NUM_PARTICLES) {
            particles.add(Particle(0.0, 0.0, (1.0f * i / NUM_PARTICLES).toDouble(), 0.0))
        }
    }

    override fun draw(canvas: Canvas) {
        paintParticles.strokeWidth = 0.05f * bounds.height()
        super.draw(canvas)
    }

    override fun drawParticle(canvas: Canvas, p: Particle?, width: Float, height: Float,
                              innerBoxX: Float, innerBoxY: Float, innerBoxSize: Float
    ) {
        canvas.drawCircle(width / 2, 1.1f * height, (p!!.positionZ * 1.2f * height).toFloat(), paintParticles)
    }

    override fun particleTick(p: Particle?, timeSinceLastFrame: Long) {
        p!!.positionZ += 0.00005 * timeSinceLastFrame
        if (p!!.positionZ > 1f) {
            p.positionZ -= 1.0
        }
    }

    companion object {
        protected const val NUM_PARTICLES: Int = 10
    }
}
