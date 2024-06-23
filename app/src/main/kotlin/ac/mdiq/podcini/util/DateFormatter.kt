package ac.mdiq.podcini.util

import android.content.Context
import android.text.format.DateUtils
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Formats dates.
 */
object DateFormatter {
    @JvmStatic
    fun formatRfc822Date(date: Date?): String {
        val format = SimpleDateFormat("dd MMM yy HH:mm:ss Z", Locale.US)
        return format.format(date)
    }

    @JvmStatic
    fun formatAbbrev(context: Context?, date: Date?): String {
        if (date == null) return ""

        val now = GregorianCalendar()
        val cal = GregorianCalendar()
        cal.time = date
        val withinLastYear = now[Calendar.YEAR] == cal[Calendar.YEAR]
        var format = DateUtils.FORMAT_ABBREV_ALL
        if (withinLastYear) {
            format = format or DateUtils.FORMAT_NO_YEAR
        }
        return DateUtils.formatDateTime(context, date.time, format)
    }

    @JvmStatic
    fun formatForAccessibility(date: Date?): String {
        if (date == null) return ""

        return DateFormat.getDateInstance(DateFormat.LONG).format(date)
    }
}
