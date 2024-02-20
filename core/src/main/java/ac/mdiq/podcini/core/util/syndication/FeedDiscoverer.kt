package ac.mdiq.podcini.core.util.syndication

import android.net.Uri
import androidx.collection.ArrayMap
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.IOException

/**
 * Finds RSS/Atom URLs in a HTML document using the auto-discovery techniques described here:
 *
 *
 * http://www.rssboard.org/rss-autodiscovery
 *
 *
 * http://blog.whatwg.org/feed-autodiscovery
 */
class FeedDiscoverer {
    /**
     * Discovers links to RSS and Atom feeds in the given File which must be a HTML document.
     *
     * @return A map which contains the feed URLs as keys and titles as values (the feed URL is also used as a title if
     * a title cannot be found).
     */
    @Throws(IOException::class)
    fun findLinks(inVal: File, baseUrl: String): Map<String, String> {
        return findLinks(Jsoup.parse(inVal), baseUrl)
    }

    /**
     * Discovers links to RSS and Atom feeds in the given File which must be a HTML document.
     *
     * @return A map which contains the feed URLs as keys and titles as values (the feed URL is also used as a title if
     * a title cannot be found).
     */
    fun findLinks(inVal: String, baseUrl: String): Map<String, String> {
        return findLinks(Jsoup.parse(inVal), baseUrl)
    }

    private fun findLinks(document: Document, baseUrl: String): Map<String, String> {
        val res: MutableMap<String, String> = ArrayMap()
        val links = document.head().getElementsByTag("link")
        for (link in links) {
            val rel = link.attr("rel")
            val href = link.attr("href")
            if (href.isNotEmpty() && (rel == "alternate" || rel == "feed")) {
                val type = link.attr("type")
                if (type == MIME_RSS || type == MIME_ATOM) {
                    val title = link.attr("title")
                    val processedUrl = processURL(baseUrl, href)
                    if (processedUrl != null) {
                        res[processedUrl] = title.ifEmpty { href }
                    }
                }
            }
        }
        return res
    }

    private fun processURL(baseUrl: String, strUrl: String): String? {
        val uri = Uri.parse(strUrl)
        if (uri.isRelative) {
            val res = Uri.parse(baseUrl).buildUpon().path(strUrl).build()
            return if ((res != null)) res.toString() else null
        } else {
            return strUrl
        }
    }

    companion object {
        private const val MIME_RSS = "application/rss+xml"
        private const val MIME_ATOM = "application/atom+xml"
    }
}
