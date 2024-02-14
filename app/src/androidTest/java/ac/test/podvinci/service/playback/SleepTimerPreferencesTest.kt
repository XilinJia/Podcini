package de.test.podvinci.service.playback

import ac.mdiq.podvinci.core.preferences.SleepTimerPreferences.isInTimeRange
import org.junit.Assert
import org.junit.Test

class SleepTimerPreferencesTest {
    @Test
    fun testIsInTimeRange() {
        Assert.assertTrue(isInTimeRange(0, 10, 8))
        Assert.assertTrue(isInTimeRange(1, 10, 8))
        Assert.assertTrue(isInTimeRange(1, 10, 1))
        Assert.assertTrue(isInTimeRange(20, 10, 8))
        Assert.assertTrue(isInTimeRange(20, 20, 8))
        Assert.assertFalse(isInTimeRange(1, 6, 8))
        Assert.assertFalse(isInTimeRange(1, 6, 6))
        Assert.assertFalse(isInTimeRange(20, 6, 8))
    }
}
