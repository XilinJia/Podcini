package de.danoeh.antennapod.ui.echo.screens

import android.content.Context
import android.graphics.Canvas

class WaveformScreen(context: Context?) : BaseScreen(context) {
    init {
        for (i in 0 until NUM_PARTICLES) {
            particles.add(Particle((1.1f + 1.1f * i / NUM_PARTICLES - 0.05f).toDouble(), 0.0, 0.0, 0.0))
        }
    }

    override fun drawParticle(canvas: Canvas, p: Particle?, width: Float, height: Float,
                              innerBoxX: Float, innerBoxY: Float, innerBoxSize: Float
    ) {
        val x = (width * p!!.positionX).toFloat()
        canvas.drawRect(x, height, x + (1.1f * width) / NUM_PARTICLES,
            (0.95f * height - 0.3f * p.positionY * height).toFloat(), paintParticles)
    }

    override fun particleTick(p: Particle?, timeSinceLastFrame: Long) {
        p!!.positionX += 0.0001 * timeSinceLastFrame
        if (p!!.positionY <= 0.2 || p.positionY >= 1) {
            p.speed = -p.speed
            p.positionY -= p.speed * timeSinceLastFrame
        }
        p.positionY -= p.speed * timeSinceLastFrame
        if (p.positionX > 1.05f) {
            p.positionX -= 1.1
            p.positionY = 0.2 + 0.8 * Math.random()
            p.speed = 0.0008 * Math.random() - 0.0004
        }
    }

    companion object {
        protected const val NUM_PARTICLES: Int = 40
    }
}
