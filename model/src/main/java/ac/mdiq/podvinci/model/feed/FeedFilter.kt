package ac.mdiq.podvinci.model.feed

import java.io.Serializable
import java.util.*
import java.util.regex.Pattern

class FeedFilter // We're storing the strings and not the parsed terms because
// 1. It's easier to show the user exactly what they typed in this way
//    (we don't have to recreate it)
// 2. We don't know if we'll actually be asked to parse anything anyways.
@JvmOverloads constructor(val includeFilterRaw: String? = "",
                          val excludeFilterRaw: String? = "",
                          val minimalDurationFilter: Int = -1
) : Serializable {
    /**
     * Parses the text in to a list of single words or quoted strings.
     * Example: "One "Two Three"" returns ["One", "Two Three"]
     * @param filter string to parse in to terms
     * @return list of terms
     */
    private fun parseTerms(filter: String?): List<String> {
        // from http://stackoverflow.com/questions/7804335/split-string-on-spaces-in-java-except-if-between-quotes-i-e-treat-hello-wor
        val list: MutableList<String> = ArrayList()
        val m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(filter.toString())
        while (m.find()) {
            if (m.group(1) != null) list.add(m.group(1)!!.replace("\"", ""))
        }
        return list
    }

    /**
     * @param item
     * @return true if the item should be downloaded
     */
    fun shouldAutoDownload(item: FeedItem): Boolean {
        val includeTerms = parseTerms(includeFilterRaw)
        val excludeTerms = parseTerms(excludeFilterRaw)

        if (includeTerms.isEmpty() && excludeTerms.isEmpty() && minimalDurationFilter <= -1) {
            // nothing has been specified, so include everything
            return true
        }

        // Check if the episode is long enough if minimal duration filter is on
        if (hasMinimalDurationFilter() && item.media != null) {
            val durationInMs = item.media!!.getDuration()
            // Minimal Duration is stored in seconds
            if (durationInMs > 0 && durationInMs / 1000 < minimalDurationFilter) {
                return false
            }
        }

        // check using lowercase so the users don't have to worry about case.
        val title = item.title?.lowercase(Locale.getDefault())?:""

        // if it's explicitly excluded, it shouldn't be autodownloaded
        // even if it has include terms
        for (term in excludeTerms) {
            if (title.contains(term.trim { it <= ' ' }.lowercase(Locale.getDefault()))) {
                return false
            }
        }

        for (term in includeTerms) {
            if (title.contains(term.trim { it <= ' ' }.lowercase(Locale.getDefault()))) {
                return true
            }
        }

        // now's the tricky bit
        // if they haven't set an include filter, but they have set an exclude filter
        // default to including, but if they've set both, then exclude
        if (!hasIncludeFilter() && hasExcludeFilter()) {
            return true
        }

        // if they only set minimal duration filter and arrived here, autodownload
        // should happen
        if (hasMinimalDurationFilter()) {
            return true
        }

        return false
    }

    fun getIncludeFilter(): List<String> {
        return if (includeFilterRaw == null) ArrayList() else parseTerms(includeFilterRaw)
    }

    fun getExcludeFilter(): List<String> {
        return if (excludeFilterRaw == null) ArrayList() else parseTerms(excludeFilterRaw)
    }

    /**
     * @return true if only include is set
     */
    fun includeOnly(): Boolean {
        return hasIncludeFilter() && !hasExcludeFilter()
    }

    /**
     * @return true if only exclude is set
     */
    fun excludeOnly(): Boolean {
        return hasExcludeFilter() && !hasIncludeFilter()
    }

    fun hasIncludeFilter(): Boolean {
        return includeFilterRaw!!.isNotEmpty()
    }

    fun hasExcludeFilter(): Boolean {
        return excludeFilterRaw!!.isNotEmpty()
    }

    fun hasMinimalDurationFilter(): Boolean {
        return minimalDurationFilter > -1
    }
}
