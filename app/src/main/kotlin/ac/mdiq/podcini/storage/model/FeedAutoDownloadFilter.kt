package ac.mdiq.podcini.storage.model

import java.io.Serializable
import java.util.*
import java.util.regex.Pattern

// We're storing the strings and not the parsed terms because
// 1. It's easier to show the user exactly what they typed in this way (we don't have to recreate it)
// 2. We don't know if we'll actually be asked to parse anything anyways.

class FeedAutoDownloadFilter(
        val includeFilterRaw: String? = "",
        val excludeFilterRaw: String? = "",
        val minimalDurationFilter: Int = -1,
        val markExcludedPlayed: Boolean = false) : Serializable {

    val includeTerms: List<String> by lazy { parseTerms(includeFilterRaw) }
    val excludeTerms: List<String> by lazy { parseTerms(excludeFilterRaw) }

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
    fun meetsAutoDLCriteria(item: Episode): Boolean {
//        if (includeTerms == null) includeTerms = parseTerms(includeFilterRaw)
//        if (excludeTerms == null)  excludeTerms = parseTerms(excludeFilterRaw)

        // nothing has been specified, so include everything
        if (includeTerms.isNullOrEmpty() && excludeTerms.isNullOrEmpty() && minimalDurationFilter <= -1) return true

        // Check if the episode is long enough if minimal duration filter is on
        if (hasMinimalDurationFilter()) {
            val durationInMs = item.duration
            // Minimal Duration is stored in seconds
            if (durationInMs > 0 && durationInMs / 1000 < minimalDurationFilter) return false
        }

        // check using lowercase so the users don't have to worry about case.
        val title = item.title?.lowercase(Locale.getDefault())?:""

        // if it's explicitly excluded, it shouldn't be autodownloaded
        // even if it has include terms
        for (term in excludeTerms!!) {
            if (title.contains(term.trim { it <= ' ' }.lowercase(Locale.getDefault()))) return false
        }

        for (term in includeTerms!!) {
            if (title.contains(term.trim { it <= ' ' }.lowercase(Locale.getDefault()))) return true
        }

        // now's the tricky bit
        // if they haven't set an include filter, but they have set an exclude filter
        // default to including, but if they've set both, then exclude
        if (!hasIncludeFilter() && hasExcludeFilter()) return true

        // if they only set minimal duration filter and arrived here, autodownload
        // should happen
        if (hasMinimalDurationFilter()) return true

        return false
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
        return !includeFilterRaw.isNullOrEmpty()
    }

    fun hasExcludeFilter(): Boolean {
        return !excludeFilterRaw.isNullOrEmpty()
    }

    fun hasMinimalDurationFilter(): Boolean {
        return minimalDurationFilter > -1
    }
}
