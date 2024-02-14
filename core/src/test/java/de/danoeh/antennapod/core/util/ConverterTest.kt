package de.danoeh.antennapod.core.util

import de.danoeh.antennapod.core.util.Converter.durationStringLongToMs
import de.danoeh.antennapod.core.util.Converter.durationStringShortToMs
import de.danoeh.antennapod.core.util.Converter.getDurationStringLong
import de.danoeh.antennapod.core.util.Converter.getDurationStringShort
import org.junit.Assert
import org.junit.Test

/**
 * Test class for converter
 */
class ConverterTest {
    @Test
    fun testGetDurationStringLong() {
        val expected = "13:05:10"
        val input = 47110000
        Assert.assertEquals(expected, getDurationStringLong(input))
    }

    @Test
    fun testGetDurationStringShort() {
        val expected = "13:05"
        Assert.assertEquals(expected, getDurationStringShort(47110000, true))
        Assert.assertEquals(expected, getDurationStringShort(785000, false))
    }

    @Test
    fun testDurationStringLongToMs() {
        val input = "01:20:30"
        val expected: Long = 4830000
        Assert.assertEquals(expected, durationStringLongToMs(input).toLong())
    }

    @Test
    fun testDurationStringShortToMs() {
        val input = "8:30"
        Assert.assertEquals(30600000, durationStringShortToMs(input, true).toLong())
        Assert.assertEquals(510000, durationStringShortToMs(input, false).toLong())
    }
}
