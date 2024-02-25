package ac.mdiq.podcini.feed.parser.util

object SyndStringUtils {
    /**
     * Trims all whitespace from beginning and ending of a String. {[String.trim]} only trims spaces.
     */
    @JvmStatic
    fun trimAllWhitespace(string: String): String {
        return string.replace("(^\\s*)|(\\s*$)".toRegex(), "")
    }
}
