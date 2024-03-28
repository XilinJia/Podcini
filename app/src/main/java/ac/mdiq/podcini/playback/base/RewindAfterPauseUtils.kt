package ac.mdiq.podcini.playback.base

import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * This class calculates the proper rewind time after the pause and resume.
 *
 *
 * User might loose context if he/she pauses and resumes the media after longer time.
 * Media file should be "rewinded" x seconds after user resumes the playback.
 */
object RewindAfterPauseUtils {
    @JvmField
    val ELAPSED_TIME_FOR_SHORT_REWIND: Long = TimeUnit.MINUTES.toMillis(1)
    @JvmField
    val ELAPSED_TIME_FOR_MEDIUM_REWIND: Long = TimeUnit.HOURS.toMillis(1)
    @JvmField
    val ELAPSED_TIME_FOR_LONG_REWIND: Long = TimeUnit.DAYS.toMillis(1)

    @JvmField
    val SHORT_REWIND: Long = TimeUnit.SECONDS.toMillis(3)
    @JvmField
    val MEDIUM_REWIND: Long = TimeUnit.SECONDS.toMillis(10)
    @JvmField
    val LONG_REWIND: Long = TimeUnit.SECONDS.toMillis(20)

    /**
     * @param currentPosition  current position in a media file in ms
     * @param lastPlayedTime  timestamp when was media paused
     * @return  new rewinded position for playback in milliseconds
     */
    @JvmStatic
    fun calculatePositionWithRewind(currentPosition: Int, lastPlayedTime: Long): Int {
        if (currentPosition > 0 && lastPlayedTime > 0) {
            val elapsedTime = System.currentTimeMillis() - lastPlayedTime
            var rewindTime: Long = 0

            when {
                elapsedTime > ELAPSED_TIME_FOR_LONG_REWIND -> {
                    rewindTime = LONG_REWIND
                }
                elapsedTime > ELAPSED_TIME_FOR_MEDIUM_REWIND -> {
                    rewindTime = MEDIUM_REWIND
                }
                elapsedTime > ELAPSED_TIME_FOR_SHORT_REWIND -> {
                    rewindTime = SHORT_REWIND
                }
            }

            val newPosition = currentPosition - rewindTime.toInt()

            return max(newPosition.toDouble(), 0.0).toInt()
        } else {
            return currentPosition
        }
    }
}
