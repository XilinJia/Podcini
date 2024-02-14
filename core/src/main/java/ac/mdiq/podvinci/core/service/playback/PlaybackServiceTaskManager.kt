package ac.mdiq.podvinci.core.service.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.util.Log
import ac.mdiq.podvinci.core.preferences.SleepTimerPreferences
import ac.mdiq.podvinci.core.util.ChapterUtils
import ac.mdiq.podvinci.core.widget.WidgetUpdater
import ac.mdiq.podvinci.core.widget.WidgetUpdater.WidgetState
import ac.mdiq.podvinci.event.playback.SleepTimerUpdatedEvent
import ac.mdiq.podvinci.model.playback.Playable
import io.reactivex.Completable
import io.reactivex.CompletableEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile

/**
 * Manages the background tasks of PlaybackSerivce, i.e.
 * the sleep timer, the position saver, the widget updater and
 * the queue loader.
 *
 *
 * The PlaybackServiceTaskManager(PSTM) uses a callback object (PSTMCallback)
 * to notify the PlaybackService about updates from the running tasks.
 */
class PlaybackServiceTaskManager(private val context: Context,
                                 private val callback: PSTMCallback
) {
    private val schedExecutor: ScheduledThreadPoolExecutor

    private var positionSaverFuture: ScheduledFuture<*>? = null
    private var widgetUpdaterFuture: ScheduledFuture<*>? = null
    private var sleepTimerFuture: ScheduledFuture<*>? = null

    @Volatile
    private var chapterLoaderFuture: Disposable? = null

    private var sleepTimer: SleepTimer? = null

    /**
     * Sets up a new PSTM. This method will also start the queue loader task.
     *
     * @param context
     * @param callback A PSTMCallback object for notifying the user about updates. Must not be null.
     */
    init {
        schedExecutor = ScheduledThreadPoolExecutor(SCHED_EX_POOL_SIZE) { r: Runnable? ->
            val t = Thread(r)
            t.priority = Thread.MIN_PRIORITY
            t
        }
    }

    /**
     * Starts the position saver task. If the position saver is already active, nothing will happen.
     */
    @Synchronized
    fun startPositionSaver() {
        if (!isPositionSaverActive) {
            var positionSaver = Runnable { callback.positionSaverTick() }
            positionSaver = useMainThreadIfNecessary(positionSaver)
            positionSaverFuture =
                schedExecutor.scheduleWithFixedDelay(positionSaver, POSITION_SAVER_WAITING_INTERVAL.toLong(),
                    POSITION_SAVER_WAITING_INTERVAL.toLong(), TimeUnit.MILLISECONDS)

            Log.d(TAG, "Started PositionSaver")
        } else {
            Log.d(TAG, "Call to startPositionSaver was ignored.")
        }
    }

    @get:Synchronized
    val isPositionSaverActive: Boolean
        /**
         * Returns true if the position saver is currently running.
         */
        get() = positionSaverFuture != null && !positionSaverFuture!!.isCancelled && !positionSaverFuture!!.isDone

    /**
     * Cancels the position saver. If the position saver is not running, nothing will happen.
     */
    @Synchronized
    fun cancelPositionSaver() {
        if (isPositionSaverActive) {
            positionSaverFuture!!.cancel(false)
            Log.d(TAG, "Cancelled PositionSaver")
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
            widgetUpdaterFuture = schedExecutor.scheduleWithFixedDelay(widgetUpdater,
                WIDGET_UPDATER_NOTIFICATION_INTERVAL.toLong(),
                WIDGET_UPDATER_NOTIFICATION_INTERVAL.toLong(),
                TimeUnit.MILLISECONDS)
            Log.d(TAG, "Started WidgetUpdater")
        } else {
            Log.d(TAG, "Call to startWidgetUpdater was ignored.")
        }
    }

    /**
     * Retrieves information about the widget state in the calling thread and then displays it in a background thread.
     */
    @Synchronized
    fun requestWidgetUpdate() {
        val state = callback.requestWidgetState()
        if (!schedExecutor.isShutdown) {
            schedExecutor.execute { WidgetUpdater.updateWidget(context, state) }
        } else {
            Log.d(TAG, "Call to requestWidgetUpdate was ignored.")
        }
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

        Log.d(TAG, "Setting sleep timer to $waitingTime milliseconds")
        if (isSleepTimerActive) {
            sleepTimerFuture!!.cancel(true)
        }
        sleepTimer = SleepTimer(waitingTime)
        sleepTimerFuture = schedExecutor.schedule(sleepTimer, 0, TimeUnit.MILLISECONDS)
        EventBus.getDefault().post(SleepTimerUpdatedEvent.justEnabled(waitingTime))
    }

    @get:Synchronized
    val isSleepTimerActive: Boolean
        /**
         * Returns true if the sleep timer is currently active.
         */
        get() = (sleepTimer != null && sleepTimerFuture != null && !sleepTimerFuture!!.isCancelled
                && !sleepTimerFuture!!.isDone) && sleepTimer!!.getWaitingTime() > 0

    /**
     * Disables the sleep timer. If the sleep timer is not active, nothing will happen.
     */
    @Synchronized
    fun disableSleepTimer() {
        if (isSleepTimerActive) {
            Log.d(TAG, "Disabling sleep timer")
            sleepTimer!!.cancel()
        }
    }

    /**
     * Restarts the sleep timer. If the sleep timer is not active, nothing will happen.
     */
    @Synchronized
    fun restartSleepTimer() {
        if (isSleepTimerActive) {
            Log.d(TAG, "Restarting sleep timer")
            sleepTimer!!.restart()
        }
    }

    @get:Synchronized
    val sleepTimerTimeLeft: Long
        /**
         * Returns the current sleep timer time or 0 if the sleep timer is not active.
         */
        get() = if (isSleepTimerActive) {
            sleepTimer!!.getWaitingTime()
        } else {
            0
        }

    @get:Synchronized
    val isWidgetUpdaterActive: Boolean
        /**
         * Returns true if the widget updater is currently running.
         */
        get() = widgetUpdaterFuture != null && !widgetUpdaterFuture!!.isCancelled && !widgetUpdaterFuture!!.isDone

    /**
     * Cancels the widget updater. If the widget updater is not running, nothing will happen.
     */
    @Synchronized
    fun cancelWidgetUpdater() {
        if (isWidgetUpdaterActive) {
            widgetUpdaterFuture!!.cancel(false)
            Log.d(TAG, "Cancelled WidgetUpdater")
        }
    }

    /**
     * Starts a new thread that loads the chapter marks from a playable object. If another chapter loader is already active,
     * it will be cancelled first.
     * On completion, the callback's onChapterLoaded method will be called.
     */
    @Synchronized
    fun startChapterLoader(media: Playable) {
        if (chapterLoaderFuture != null) {
            chapterLoaderFuture!!.dispose()
            chapterLoaderFuture = null
        }

        if (media.getChapters().isEmpty()) {
            chapterLoaderFuture = Completable.create { emitter: CompletableEmitter ->
                ChapterUtils.loadChapters(media, context, false)
                emitter.onComplete()
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ callback.onChapterLoaded(media) },
                    { throwable: Throwable? ->
                        Log.d(TAG,
                            "Error loading chapters: " + Log.getStackTraceString(throwable))
                    })
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

        if (chapterLoaderFuture != null) {
            chapterLoaderFuture!!.dispose()
            chapterLoaderFuture = null
        }
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
        } else {
            return runnable
        }
    }

    /**
     * Sleeps for a given time and then pauses playback.
     */
    internal inner class SleepTimer(private val waitingTime: Long) : Runnable {
        private var hasVibrated = false
        private var timeLeft = waitingTime
        private var shakeListener: ShakeListener? = null

        override fun run() {
            Log.d(Companion.TAG, "Starting")
            var lastTick = System.currentTimeMillis()
            EventBus.getDefault().post(SleepTimerUpdatedEvent.updated(timeLeft))
            while (timeLeft > 0) {
                try {
                    Thread.sleep(Companion.UPDATE_INTERVAL)
                } catch (e: InterruptedException) {
                    Log.d(Companion.TAG, "Thread was interrupted while waiting")
                    e.printStackTrace()
                    break
                }

                val now = System.currentTimeMillis()
                timeLeft -= now - lastTick
                lastTick = now

                EventBus.getDefault().post(SleepTimerUpdatedEvent.updated(timeLeft))
                if (timeLeft < Companion.NOTIFICATION_THRESHOLD) {
                    Log.d(Companion.TAG, "Sleep timer is about to expire")
                    if (SleepTimerPreferences.vibrate() && !hasVibrated) {
                        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        if (v != null) {
                            v.vibrate(500)
                            hasVibrated = true
                        }
                    }
                    if (shakeListener == null && SleepTimerPreferences.shakeToReset()) {
                        shakeListener = ShakeListener(context, this)
                    }
                }
                if (timeLeft <= 0) {
                    Log.d(Companion.TAG, "Sleep timer expired")
                    if (shakeListener != null) {
                        shakeListener!!.pause()
                        shakeListener = null
                    }
                    hasVibrated = false
                }
            }
        }

        fun getWaitingTime(): Long {
            return timeLeft
        }

        fun restart() {
            EventBus.getDefault().post(SleepTimerUpdatedEvent.cancelled())
            setSleepTimer(waitingTime)
            if (shakeListener != null) {
                shakeListener!!.pause()
                shakeListener = null
            }
        }

        fun cancel() {
            sleepTimerFuture!!.cancel(true)
            if (shakeListener != null) {
                shakeListener!!.pause()
            }
            EventBus.getDefault().post(SleepTimerUpdatedEvent.cancelled())
        }

//        companion object {
//            private const val TAG = "SleepTimer"
//            private const val UPDATE_INTERVAL = 1000L
//            const val NOTIFICATION_THRESHOLD: Long = 10000
//        }
    }

    interface PSTMCallback {
        fun positionSaverTick()

        fun requestWidgetState(): WidgetState

        fun onChapterLoaded(media: Playable?)
    }

    companion object {
        private const val TAG = "PlaybackServiceTaskMgr"

        /**
         * Update interval of position saver in milliseconds.
         */
        const val POSITION_SAVER_WAITING_INTERVAL: Int = 5000

        /**
         * Notification interval of widget updater in milliseconds.
         */
        const val WIDGET_UPDATER_NOTIFICATION_INTERVAL: Int = 1000

        private const val SCHED_EX_POOL_SIZE = 2

        private const val UPDATE_INTERVAL = 1000L
        const val NOTIFICATION_THRESHOLD: Long = 10000

    }
}
