package ac.mdiq.podcini.util

import ac.mdiq.podcini.preferences.UserPreferences

class TimeSpeedConverter(private val speed: Float) {
    /** Convert millisecond according to the current playback speed
     * @param time time to convert
     * @return converted time (can be < 0 if time is < 0)
     */
    var timeRespectsSpeed: Boolean = UserPreferences.timeRespectsSpeed()

    fun convert(time: Int): Int {
        if (time > 0 && timeRespectsSpeed) return (time / speed).toInt()
        return time
    }
}
