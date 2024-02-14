package de.danoeh.antennapod.event.playback

import kotlin.math.abs
import kotlin.math.max

class SleepTimerUpdatedEvent private constructor(private val timeLeft: Long) {
    fun getTimeLeft(): Long {
        return abs(timeLeft.toDouble()).toLong()
    }

    val isOver: Boolean
        get() = timeLeft == 0L

    fun wasJustEnabled(): Boolean {
        return timeLeft < 0
    }

    val isCancelled: Boolean
        get() = timeLeft == CANCELLED

    companion object {
        private const val CANCELLED = Long.MAX_VALUE
        fun justEnabled(timeLeft: Long): SleepTimerUpdatedEvent {
            return SleepTimerUpdatedEvent(-timeLeft)
        }

        fun updated(timeLeft: Long): SleepTimerUpdatedEvent {
            return SleepTimerUpdatedEvent(max(0.0, timeLeft.toDouble()).toLong())
        }

        fun cancelled(): SleepTimerUpdatedEvent {
            return SleepTimerUpdatedEvent(CANCELLED)
        }
    }
}
