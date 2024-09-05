package ac.mdiq.podcini.net.utils

import ac.mdiq.podcini.util.Logd
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL

/**
 * Utility methods for dealing with URL encoding.
 */
object URIUtil {
    private val TAG: String = URIUtil::class.simpleName ?: "Anonymous"

    @JvmStatic
    fun getURIFromRequestUrl(source: String): URI {
        // try without encoding the URI
        try { return URI(source) } catch (e: URISyntaxException) { Logd(TAG, "Source is not encoded, encoding now") }
        try {
            val url = URL(source)
            return URI(url.protocol, url.userInfo, url.host, url.port, url.path, url.query, url.ref)
        } catch (e: MalformedURLException) {
            Logd(TAG, "source: $source")
            throw IllegalArgumentException(e)
        } catch (e: URISyntaxException) {
            Logd(TAG, "source: $source")
            throw IllegalArgumentException(e)
        }
    }
}
