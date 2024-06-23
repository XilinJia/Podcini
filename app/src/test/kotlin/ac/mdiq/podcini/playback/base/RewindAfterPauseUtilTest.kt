package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.calculatePositionWithRewind
import org.junit.Assert
import org.junit.Test


class RewindAfterPauseUtilTest {
    @Test
    fun testCalculatePositionWithRewindNoRewind() {
        val ORIGINAL_POSITION = 10000
        val lastPlayed = System.currentTimeMillis()
        val position = calculatePositionWithRewind(ORIGINAL_POSITION, lastPlayed)

        Assert.assertEquals(ORIGINAL_POSITION.toLong(), position.toLong())
    }

    @Test
    fun testCalculatePositionWithRewindSmallRewind() {
        val ORIGINAL_POSITION = 10000
        val lastPlayed = System.currentTimeMillis() - MediaPlayerBase.ELAPSED_TIME_FOR_SHORT_REWIND - 1000
        val position = calculatePositionWithRewind(ORIGINAL_POSITION, lastPlayed)

        Assert.assertEquals(ORIGINAL_POSITION - MediaPlayerBase.SHORT_REWIND, position.toLong())
    }

    @Test
    fun testCalculatePositionWithRewindMediumRewind() {
        val ORIGINAL_POSITION = 10000
        val lastPlayed = System.currentTimeMillis() - MediaPlayerBase.ELAPSED_TIME_FOR_MEDIUM_REWIND - 1000
        val position = calculatePositionWithRewind(ORIGINAL_POSITION, lastPlayed)

        Assert.assertEquals(ORIGINAL_POSITION - MediaPlayerBase.MEDIUM_REWIND, position.toLong())
    }

    @Test
    fun testCalculatePositionWithRewindLongRewind() {
        val ORIGINAL_POSITION = 30000
        val lastPlayed = System.currentTimeMillis() - MediaPlayerBase.ELAPSED_TIME_FOR_LONG_REWIND - 1000
        val position = calculatePositionWithRewind(ORIGINAL_POSITION, lastPlayed)

        Assert.assertEquals(ORIGINAL_POSITION - MediaPlayerBase.LONG_REWIND, position.toLong())
    }

    @Test
    fun testCalculatePositionWithRewindNegativeNumber() {
        val ORIGINAL_POSITION = 100
        val lastPlayed = System.currentTimeMillis() - MediaPlayerBase.ELAPSED_TIME_FOR_LONG_REWIND - 1000
        val position = calculatePositionWithRewind(ORIGINAL_POSITION, lastPlayed)

        Assert.assertEquals(0, position.toLong())
    }
}
