package de.danoeh.antennapod.ui.echo.screens

import android.content.Context
import android.graphics.Canvas

class RotatingSquaresScreen(context: Context?) : BaseScreen(context) {
    init {
        for (i in 0..15) {
            particles.add(Particle(
                0.3 * (i % 4).toFloat() + 0.05 + 0.1 * Math.random() - 0.05,
                0.2 * (i / 4).toFloat() + 0.20 + 0.1 * Math.random() - 0.05,
                Math.random(), 0.00001 * (2 * Math.random() + 2)))
        }
    }

    override fun drawParticle(canvas: Canvas, p: Particle?, width: Float, height: Float,
                              innerBoxX: Float, innerBoxY: Float, innerBoxSize: Float
    ) {
        val x = (p!!.positionX * width).toFloat()
        val y = (p.positionY * height).toFloat()
        val size = innerBoxSize / 6
        canvas.save()
        canvas.rotate((360 * p.positionZ).toFloat(), x, y)
        canvas.drawRect(x - size, y - size, x + size, y + size, paintParticles)
        canvas.restore()
    }

    override fun particleTick(p: Particle?, timeSinceLastFrame: Long) {
        p!!.positionZ += p!!.speed * timeSinceLastFrame
        if (p.positionZ > 1) {
            p.positionZ -= 1.0
        }
    }
}