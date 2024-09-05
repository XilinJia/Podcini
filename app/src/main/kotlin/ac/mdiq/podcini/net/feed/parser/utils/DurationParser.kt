package ac.mdiq.podcini.net.feed.parser.utils

import java.util.concurrent.TimeUnit

object DurationParser {
    @JvmStatic
    @Throws(NumberFormatException::class)
    fun inMillis(durationStr: String): Long {
        val parts = durationStr.trim { it <= ' ' }.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        return when (parts.size) {
            1 -> toMillis(parts[0])
            2 -> toMillis("0", parts[0], parts[1])
            3 -> toMillis(parts[0], parts[1], parts[2])
            else -> throw NumberFormatException()
        }
    }

    private fun toMillis(hours: String, minutes: String, seconds: String): Long {
        return (TimeUnit.HOURS.toMillis(hours.toLong()) + TimeUnit.MINUTES.toMillis(minutes.toLong()) + toMillis(seconds))
    }

    private fun toMillis(seconds: String): Long {
        if (seconds.contains(".")) {
            val value = seconds.toFloat()
            val millis = value % 1
            return TimeUnit.SECONDS.toMillis(value.toLong()) + (millis * 1000).toLong()
        } else return TimeUnit.SECONDS.toMillis(seconds.toLong())
    }
}
