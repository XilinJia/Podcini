package ac.mdiq.podcini.playback.service

import ac.mdiq.podcini.preferences.SleepTimerPreferences
import ac.mdiq.podcini.storage.model.Playable
import ac.mdiq.podcini.ui.widget.WidgetUpdater
import ac.mdiq.podcini.ui.widget.WidgetUpdater.WidgetState
import ac.mdiq.podcini.storage.utils.ChapterUtils
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * Manages the background tasks of PlaybackSerivce, i.e.
 * the sleep timer, the position saver, the widget updater and
 * the queue loader.
 *
 *
 * The PlaybackServiceTaskManager(PSTM) uses a callback object (PSTMCallback)
 * to notify the PlaybackService about updates from the running tasks.
 */
class TaskManager(private val context: Context, private val callback: PSTMCallback) {
    private val schedExecutor: ScheduledThreadPoolExecutor = ScheduledThreadPoolExecutor(SCHED_EX_POOL_SIZE) { r: Runnable? ->
        val t = Thread(r)
        t.priority = Thread.MIN_PRIORITY
        t
    }

    private var positionSaverFuture: ScheduledFuture<*>? = null
    private var widgetUpdaterFuture: ScheduledFuture<*>? = null
    private var sleepTimerFuture: ScheduledFuture<*>? = null

//    @Volatile
//    private var chapterLoaderFuture: Disposable? = null

    private var sleepTimer: SleepTimer? = null

    /**
     * Returns true if the sleep timer is currently active.
     */
    @get:Synchronized
    val isSleepTimerActive: Boolean
        get() = sleepTimerFuture?.isCancelled == false && sleepTimerFuture?.isDone == false && (sleepTimer?.getWaitingTime() ?: 0) > 0

    /**
     * Returns the current sleep timer time or 0 if the sleep timer is not active.
     */
    @get:Synchronized
    val sleepTimerTimeLeft: Long
        get() = if (isSleepTimerActive) sleepTimer!!.getWaitingTime() else 0

    /**
     * Returns true if the widget updater is currently running.
     */
    @get:Synchronized
    val isWidgetUpdaterActive: Boolean
        get() = widgetUpdaterFuture != null && !widgetUpdaterFuture!!.isCancelled && !widgetUpdaterFuture!!.isDone

    /**
     * Returns true if the position saver is currently running.
     */
    @get:Synchronized
    val isPositionSaverActive: Boolean
        get() = positionSaverFuture != null && !positionSaverFuture!!.isCancelled && !positionSaverFuture!!.isDone

    /**
     * Starts the position saver task. If the position saver is already active, nothing will happen.
     */
    @Synchronized
    fun startPositionSaver() {
        if (!isPositionSaverActive) {
            var positionSaver = Runnable { callback.positionSaverTick() }
            positionSaver = useMainThreadIfNecessary(positionSaver)
            positionSaverFuture = schedExecutor.scheduleWithFixedDelay(
                positionSaver, POSITION_SAVER_WAITING_INTERVAL.toLong(), POSITION_SAVER_WAITING_INTERVAL.toLong(), TimeUnit.MILLISECONDS)
            Logd(TAG, "Started PositionSaver")
        } else Logd(TAG, "Call to startPositionSaver was ignored.")
    }

    /**
     * Cancels the position saver. If the position saver is not running, nothing will happen.
     */
    @Synchronized
    fun cancelPositionSaver() {
        if (isPositionSaverActive) {
            positionSaverFuture!!.cancel(false)
            Logd(TAG, "Cancelled PositionSaver")
        }
    }

    /**
     * Starts the widget updater task. If the widget updater is already active, nothing will happen.
     */
    @Synchronized
    fun startWidgetUpdater() {
        if (!isWidgetUpdaterActive && !schedExecutor.isShutdown) {
            var widgetUpdater = Runnable { this.requestWidgetUpdate() }
            widgetUpdater = useMainThreadIfNecessary(widgetUpdater)
            widgetUpdaterFuture = schedExecutor.scheduleWithFixedDelay(
                widgetUpdater, WIDGET_UPDATER_NOTIFICATION_INTERVAL.toLong(), WIDGET_UPDATER_NOTIFICATION_INTERVAL.toLong(), TimeUnit.MILLISECONDS)
            Logd(TAG, "Started WidgetUpdater")
        }
    }

    /**
     * Retrieves information about the widget state in the calling thread and then displays it in a background thread.
     */
    @Synchronized
    fun requestWidgetUpdate() {
        val state = callback.requestWidgetState()
        if (!schedExecutor.isShutdown) schedExecutor.execute { WidgetUpdater.updateWidget(context, state) }
        else Logd(TAG, "Call to requestWidgetUpdate was ignored.")
    }

    /**
     * Starts a new sleep timer with the given waiting time. If another sleep timer is already active, it will be
     * cancelled first.
     * After waitingTime has elapsed, onSleepTimerExpired() will be called.
     *
     * @throws java.lang.IllegalArgumentException if waitingTime <= 0
     */
    @Synchronized
    fun setSleepTimer(waitingTime: Long) {
        require(waitingTime > 0) { "Waiting time <= 0" }

        Logd(TAG, "Setting sleep timer to $waitingTime milliseconds")
        if (isSleepTimerActive) sleepTimerFuture!!.cancel(true)
        sleepTimer = SleepTimer(waitingTime)
        sleepTimerFuture = schedExecutor.schedule(sleepTimer, 0, TimeUnit.MILLISECONDS)
        EventFlow.postEvent(FlowEvent.SleepTimerUpdatedEvent.justEnabled(waitingTime))
    }

    /**
     * Disables the sleep timer. If the sleep timer is not active, nothing will happen.
     */
    @Synchronized
    fun disableSleepTimer() {
        if (isSleepTimerActive) {
            Logd(TAG, "Disabling sleep timer")
            sleepTimer!!.cancel()
        }
    }

    /**
     * Restarts the sleep timer. If the sleep timer is not active, nothing will happen.
     */
    @Synchronized
    fun restartSleepTimer() {
        if (isSleepTimerActive) {
            Logd(TAG, "Restarting sleep timer")
            sleepTimer!!.restart()
        }
    }

    /**
     * Cancels the widget updater. If the widget updater is not running, nothing will happen.
     */
    @Synchronized
    fun cancelWidgetUpdater() {
        if (isWidgetUpdaterActive) {
            widgetUpdaterFuture!!.cancel(false)
            Logd(TAG, "Cancelled WidgetUpdater")
        }
    }

    /**
     * Starts a new thread that loads the chapter marks from a playable object. If another chapter loader is already active,
     * it will be cancelled first.
     * On completion, the callback's onChapterLoaded method will be called.
     */
    @Synchronized
    fun startChapterLoader(media: Playable) {
//        chapterLoaderFuture?.dispose()
//        chapterLoaderFuture = null

        if (!media.chaptersLoaded()) {
            val scope = CoroutineScope(Dispatchers.Main)
            scope.launch(Dispatchers.IO) {
                try {
                    ChapterUtils.loadChapters(media, context, false)
                    withContext(Dispatchers.Main) { callback.onChapterLoaded(media) }
                } catch (e: Throwable) {
                    Logd(TAG, "Error loading chapters: ${Log.getStackTraceString(e)}")
                }
            }
        }
    }

    /**
     * Cancels all tasks. The PSTM will be in the initial state after execution of this method.
     */
    @Synchronized
    fun cancelAllTasks() {
        cancelPositionSaver()
        cancelWidgetUpdater()
        disableSleepTimer()

//        chapterLoaderFuture?.dispose()
//        chapterLoaderFuture = null
    }

    /**
     * Cancels all tasks and shuts down the internal executor service of the PSTM. The object should not be used after
     * execution of this method.
     */
    fun shutdown() {
        cancelAllTasks()
        schedExecutor.shutdownNow()
    }

    private fun useMainThreadIfNecessary(runnable: Runnable): Runnable {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Called in main thread => ExoPlayer is used
            // Run on ui thread even if called from schedExecutor
            val handler = Handler(Looper.getMainLooper())
            return Runnable { handler.post(runnable) }
        } else return runnable
    }

    /**
     * Sleeps for a given time and then pauses playback.
     */
    internal inner class SleepTimer(private val waitingTime: Long) : Runnable {
        private var hasVibrated = false
        private var timeLeft = waitingTime
        private var shakeListener: ShakeListener? = null

        override fun run() {
            Logd(TAG, "Starting SleepTimer")
            var lastTick = System.currentTimeMillis()
            EventFlow.postEvent(FlowEvent.SleepTimerUpdatedEvent.updated(timeLeft))
            while (timeLeft > 0) {
                try {
                    Thread.sleep(SLEEP_TIMER_UPDATE_INTERVAL)
                } catch (e: InterruptedException) {
                    Logd(TAG, "Thread was interrupted while waiting")
                    e.printStackTrace()
                    break
                }

                val now = System.currentTimeMillis()
                timeLeft -= now - lastTick
                lastTick = now

                EventFlow.postEvent(FlowEvent.SleepTimerUpdatedEvent.updated(timeLeft))
                if (timeLeft < NOTIFICATION_THRESHOLD) {
                    Logd(TAG, "Sleep timer is about to expire")
                    if (SleepTimerPreferences.vibrate() && !hasVibrated) {
                        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        if (v != null) {
                            v.vibrate(500)
                            hasVibrated = true
                        }
                    }
                    if (shakeListener == null && SleepTimerPreferences.shakeToReset()) shakeListener = ShakeListener(context, this)
                }
                if (timeLeft <= 0) {
                    Logd(TAG, "Sleep timer expired")
                    shakeListener?.pause()
                    shakeListener = null

                    hasVibrated = false
                }
            }
        }
        fun getWaitingTime(): Long {
            return timeLeft
        }
        fun restart() {
            EventFlow.postEvent(FlowEvent.SleepTimerUpdatedEvent.cancelled())
            setSleepTimer(waitingTime)
            shakeListener?.pause()
            shakeListener = null
        }
        fun cancel() {
            sleepTimerFuture!!.cancel(true)
            shakeListener?.pause()
            EventFlow.postEvent(FlowEvent.SleepTimerUpdatedEvent.cancelled())
        }
    }

    interface PSTMCallback {
        fun positionSaverTick()
        fun requestWidgetState(): WidgetState
        fun onChapterLoaded(media: Playable?)
    }

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
            if (mSensorMgr == null) throw UnsupportedOperationException("Sensors not supported")

            mAccelerometer = mSensorMgr!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (!mSensorMgr!!.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_UI)) { // if not supported
                mSensorMgr!!.unregisterListener(this)
                throw UnsupportedOperationException("Accelerometer not supported")
            }
        }
        fun pause() {
            mSensorMgr?.unregisterListener(this)
            mSensorMgr = null
        }
        override fun onSensorChanged(event: SensorEvent) {
            val gX = event.values[0] / SensorManager.GRAVITY_EARTH
            val gY = event.values[1] / SensorManager.GRAVITY_EARTH
            val gZ = event.values[2] / SensorManager.GRAVITY_EARTH

            val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble())
            if (gForce > 2.25) {
                Logd(TAG, "Detected shake $gForce")
                mSleepTimer.restart()
            }
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        companion object {
            private val TAG: String = ShakeListener::class.simpleName ?: "Anonymous"
        }
    }

    companion object {
        private val TAG: String = TaskManager::class.simpleName ?: "Anonymous"

        private const val SCHED_EX_POOL_SIZE = 2

        private const val SLEEP_TIMER_UPDATE_INTERVAL = 10000L  // in millisoconds
        const val POSITION_SAVER_WAITING_INTERVAL: Int = 5000   // in millisoconds
        const val WIDGET_UPDATER_NOTIFICATION_INTERVAL: Int = 5000  // in millisoconds
        const val NOTIFICATION_THRESHOLD: Long = 10000  // in millisoconds
    }
}
