package ac.mdiq.podvinci.core.service.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import ac.mdiq.podvinci.core.service.playback.PlaybackServiceTaskManager.SleepTimer
import kotlin.math.sqrt

internal class ShakeListener(private val mContext: Context, private val mSleepTimer: SleepTimer) : SensorEventListener {
    private var mAccelerometer: Sensor? = null
    private var mSensorMgr: SensorManager? = null

    init {
        resume()
    }

    private fun resume() {
        // only a precaution, the user should actually not be able to activate shake to reset
        // when the accelerometer is not available
        mSensorMgr = mContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        if (mSensorMgr == null) {
            throw UnsupportedOperationException("Sensors not supported")
        }
        mAccelerometer = mSensorMgr!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!mSensorMgr!!.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_UI)) { // if not supported
            mSensorMgr!!.unregisterListener(this)
            throw UnsupportedOperationException("Accelerometer not supported")
        }
    }

    fun pause() {
        if (mSensorMgr != null) {
            mSensorMgr!!.unregisterListener(this)
            mSensorMgr = null
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val gX = event.values[0] / SensorManager.GRAVITY_EARTH
        val gY = event.values[1] / SensorManager.GRAVITY_EARTH
        val gZ = event.values[2] / SensorManager.GRAVITY_EARTH

        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble())
        if (gForce > 2.25) {
            Log.d(TAG, "Detected shake $gForce")
            mSleepTimer.restart()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
    }

    companion object {
        private val TAG: String = ShakeListener::class.java.simpleName
    }
}