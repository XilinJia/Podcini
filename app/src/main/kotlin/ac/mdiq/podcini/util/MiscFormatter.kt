package ac.mdiq.podcini.util

import android.content.Context
import android.text.format.DateUtils
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Formats dates.
 */
object MiscFormatter {
    @JvmStatic
    fun formatRfc822Date(date: Date?): String {
        val format = SimpleDateFormat("dd MMM yy HH:mm:ss Z", Locale.US)
        return format.format(date?: Date(0))
    }

    @JvmStatic
    fun formatAbbrev(context: Context?, date: Date?): String {
        if (date == null) return ""
        val now = GregorianCalendar()
        val cal = GregorianCalendar()
        cal.time = date
        val withinLastYear = now[Calendar.YEAR] == cal[Calendar.YEAR]
        var format = DateUtils.FORMAT_ABBREV_ALL
        if (withinLastYear) format = format or DateUtils.FORMAT_NO_YEAR
        return DateUtils.formatDateTime(context, date.time, format)
    }

    @JvmStatic
    fun formatForAccessibility(date: Date?): String {
        if (date == null) return ""
        return DateFormat.getDateInstance(DateFormat.LONG).format(date)
    }

    fun formatDateTimeFlex(date: Date?): String {
        if (date == null) return "0000"
        val now = Date()
        return when {
            isSameDay(date, now) -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            isSameYear(date, now) -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(date)
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
        }
    }

    fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance()
        cal1.time = date1
        val cal2 = Calendar.getInstance()
        cal2.time = date2
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    fun isSameYear(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance()
        cal1.time = date1
        val cal2 = Calendar.getInstance()
        cal2.time = date2
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
    }

    fun formatNumber(n: Int): String {
        return when {
            n < 1000 -> n.toString()
            n < 1_000_000 -> String.format(Locale.getDefault(), "%.2fK", n / 1000.0)
            n < 1_000_000_000 -> String.format(Locale.getDefault(), "%.2fM", n / 1_000_000.0)
            else -> String.format(Locale.getDefault(), "%.2fB", n / 1_000_000_000.0)
        }
    }
}
