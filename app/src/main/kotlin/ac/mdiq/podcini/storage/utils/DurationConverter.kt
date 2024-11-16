package ac.mdiq.podcini.storage.utils

import ac.mdiq.podcini.R
import android.content.Context
import java.util.*

/** Provides methods for converting various units.  */
object DurationConverter {
    private const val HOURS_MIL = 3600000
    private const val MINUTES_MIL = 60000
    private const val SECONDS_MIL = 1000

    /**
     * Converts milliseconds to a string containing hours, minutes and seconds.
     */
    @JvmStatic
    fun getDurationStringLong(duration: Int): String {
        if (duration <= 0) return "00:00:00"
        else {
            val hms = millisecondsToHms(duration.toLong())
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hms[0], hms[1], hms[2])
        }
    }

    private fun millisecondsToHms(duration: Long): IntArray {
        val h = (duration / HOURS_MIL).toInt()
        var rest = duration - h * HOURS_MIL
        val m = (rest / MINUTES_MIL).toInt()
        rest -= (m * MINUTES_MIL).toLong()
        val s = (rest / SECONDS_MIL).toInt()
        return intArrayOf(h, m, s)
    }

    /**
     * Converts milliseconds to a string containing hours and minutes or minutes and seconds.
     */
    @JvmStatic
    fun getDurationStringShort(duration: Int, durationIsInHours: Boolean): String {
        val firstPartBase = if (durationIsInHours) HOURS_MIL else MINUTES_MIL
        val firstPart = duration / firstPartBase
        val leftoverFromFirstPart = duration - firstPart * firstPartBase
        val secondPart = leftoverFromFirstPart / (if (durationIsInHours) MINUTES_MIL else SECONDS_MIL)
        return String.format(Locale.getDefault(), "%02d:%02d", firstPart, secondPart)
    }

    /**
     * Converts long duration string (HH:MM:SS) to milliseconds.
     */
    @JvmStatic
    fun durationStringLongToMs(input: String): Int {
        val parts = input.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.size != 3) return 0

        return parts[0].toInt() * 3600 * 1000 + parts[1].toInt() * 60 * 1000 + parts[2].toInt() * 1000
    }

    /**
     * Converts short duration string (XX:YY) to milliseconds. If durationIsInHours is true then the
     * format is HH:MM, otherwise it's MM:SS.
     */
    @JvmStatic
    fun durationStringShortToMs(input: String, durationIsInHours: Boolean): Int {
        val parts = input.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.size != 2) return 0
        val modifier = if (durationIsInHours) 60 else 1
        return (parts[0].toInt() * 60 * 1000 * modifier + parts[1].toInt() * 1000 * modifier)
    }

    /**
     * Converts milliseconds to a localized string containing hours and minutes.
     */
    @JvmStatic
    fun getDurationStringLocalized(context: Context, duration: Long): String {
        val resources = context.resources
        var result = ""
        var h = (duration / HOURS_MIL).toInt()
        val d = h / 24
        if (d > 0) {
            val days = resources.getQuantityString(R.plurals.time_days_quantified, d, d)
            result += days.replace(" ", "\u00A0") + " "
            h -= d * 24
        }
        val rest = (duration - (d * 24 + h) * HOURS_MIL).toInt()
        val m = rest / MINUTES_MIL
        if (h > 0) {
            val hours = resources.getQuantityString(R.plurals.time_hours_quantified, h, h)
            result += hours.replace(" ", "\u00A0")
            if (d == 0) result += " "
        }
        if (d == 0) {
            val minutes = resources.getQuantityString(R.plurals.time_minutes_quantified, m, m)
            result += minutes.replace(" ", "\u00A0")
        }
        return result
    }

    /**
     * Converts seconds to a localized representation.
     * @param time The time in seconds
     * @return "HH:MM hours"
     */
    @JvmStatic
    fun shortLocalizedDuration(context: Context, time: Long, showHoursText: Boolean = true): String {
        val hours = time.toFloat() / 3600f
        return String.format(Locale.getDefault(), "%.2f ", hours) + if (showHoursText) context.getString(R.string.time_hours) else ""
    }
}
