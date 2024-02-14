package ac.mdiq.podvinci.ui.echo.screens

import android.content.Context
import android.graphics.Canvas

open class BubbleScreen(context: Context?) : BaseScreen(context) {
    init {
        for (i in 0 until NUM_PARTICLES) {
            particles.add(Particle(Math.random(), 2.0 * Math.random() - 0.5,  // Could already be off-screen
                0.0, PARTICLE_SPEED + 2 * PARTICLE_SPEED * Math.random()))
        }
    }

    override fun drawParticle(canvas: Canvas, p: Particle?, width: Float, height: Float,
                              innerBoxX: Float, innerBoxY: Float, innerBoxSize: Float
    ) {
        canvas.drawCircle((width * p!!.positionX).toFloat(), (p.positionY * height).toFloat(),
            innerBoxSize / 5, paintParticles)
    }

    override fun particleTick(p: Particle?, timeSinceLastFrame: Long) {
        p!!.positionY -= p!!.speed * timeSinceLastFrame
        if (p.positionY < -0.5) {
            p.positionX = Math.random()
            p.positionY = 1.5
            p.speed = PARTICLE_SPEED + 2 * PARTICLE_SPEED * Math.random()
        }
    }

    companion object {
        protected const val PARTICLE_SPEED: Double = 0.00002
        protected const val NUM_PARTICLES: Int = 15
    }
}
