package ac.mdiq.podcini.feed.parser.element.util

import ac.mdiq.podcini.net.feed.parser.utils.DurationParser.inMillis
import org.junit.Assert
import org.junit.Test

class DurationParserTest {
    private val milliseconds = 1
    private val seconds = 1000 * milliseconds
    private val minutes = 60 * seconds
    private val hours = 60 * minutes

    @Test
    fun testSecondDurationInMillis() {
        val duration = inMillis("00:45")
        Assert.assertEquals((45 * seconds).toLong(), duration)
    }

    @Test
    fun testSingleNumberDurationInMillis() {
        val twoHoursInSeconds = 2 * 60 * 60
        val duration = inMillis(twoHoursInSeconds.toString())
        Assert.assertEquals((2 * hours).toLong(), duration)
    }

    @Test
    fun testMinuteSecondDurationInMillis() {
        val duration = inMillis("05:10")
        Assert.assertEquals((5 * minutes + 10 * seconds).toLong(), duration)
    }

    @Test
    fun testHourMinuteSecondDurationInMillis() {
        val duration = inMillis("02:15:45")
        Assert.assertEquals((2 * hours + 15 * minutes + 45 * seconds).toLong(), duration)
    }

    @Test
    fun testSecondsWithMillisecondsInMillis() {
        val duration = inMillis("00:00:00.123")
        Assert.assertEquals(123, duration)
    }
}
