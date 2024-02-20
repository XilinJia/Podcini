package ac.mdiq.podcini.parser.feed.util

object SyndStringUtils {
    /**
     * Trims all whitespace from beginning and ending of a String. {[String.trim]} only trims spaces.
     */
    @JvmStatic
    fun trimAllWhitespace(string: String): String {
        return string.replace("(^\\s*)|(\\s*$)".toRegex(), "")
    }
}
