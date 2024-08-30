package ac.mdiq.podcini.net.utils

import ac.mdiq.podcini.util.Logd
import android.net.Uri
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Provides methods for checking and editing a URL.
 */
object UrlChecker {
    private val TAG: String = UrlChecker::class.simpleName ?: "Anonymous"

    private const val AP_SUBSCRIBE = "podcini-subscribe://"
//    private const val AP_SUBSCRIBE_DEEPLINK = "podcini.org/deeplink/subscribe"

    /**
     * Checks if URL is valid and modifies it if necessary.
     * @param url_ The url which is going to be prepared
     * @return The prepared url
     */
    @JvmStatic
    fun prepareUrl(url_: String): String {
        var url = url_
        url = url.trim { it <= ' ' }
        val lowerCaseUrl = url.lowercase() // protocol names are case insensitive
        Logd(TAG, "prepareUrl lowerCaseUrl: $lowerCaseUrl")
        return when {
            lowerCaseUrl.startsWith("feed://") ->  prepareUrl(url.substring("feed://".length))
            lowerCaseUrl.startsWith("pcast://") ->  prepareUrl(url.substring("pcast://".length))
            lowerCaseUrl.startsWith("pcast:") ->  prepareUrl(url.substring("pcast:".length))
            lowerCaseUrl.startsWith("itpc") ->  prepareUrl(url.substring("itpc://".length))
            lowerCaseUrl.startsWith(AP_SUBSCRIBE) ->  prepareUrl(url.substring(AP_SUBSCRIBE.length))
//            lowerCaseUrl.contains(AP_SUBSCRIBE_DEEPLINK) -> {
//                Log.d(TAG, "Removing $AP_SUBSCRIBE_DEEPLINK")
//                val removedWebsite = url.substring(url.indexOf("?url=") + "?url=".length)
//                return try {
//                    prepareUrl(URLDecoder.decode(removedWebsite, "UTF-8"))
//                } catch (e: UnsupportedEncodingException) {
//                    prepareUrl(removedWebsite)
//                }
//            }
            !(lowerCaseUrl.startsWith("http://") || lowerCaseUrl.startsWith("https://")) ->  "http://$url"
            else ->  url
        }
    }

    /**
     * Checks if URL is valid and modifies it if necessary.
     * This method also handles protocol relative URLs.

     * @param url_  The url which is going to be prepared
     * @param base_ The url against which the (possibly relative) url is applied. If this is null,
     * the result of prepareURL(url) is returned instead.
     * @return The prepared url
     */
    @JvmStatic
    fun prepareUrl(url_: String, base_: String?): String {
        var url = url_
        var base = base_ ?: return prepareUrl(url)
        url = url.trim { it <= ' ' }
        base = prepareUrl(base)
        val urlUri = Uri.parse(url)
        val baseUri = Uri.parse(base)
        return if (urlUri.isRelative && baseUri.isAbsolute) urlUri.buildUpon().scheme(baseUri.scheme).build().toString() else prepareUrl(url)
    }

    fun containsUrl(list: List<String>, url: String?): Boolean {
        for (item in list) {
            if (urlEquals(item, url)) return true
        }
        return false
    }

    @JvmStatic
    fun urlEquals(string1: String?, string2: String?): Boolean {
        if (string1 == null || string2 == null) return false
        val url1 = string1.toHttpUrlOrNull() ?: return false
        val url2 = string2.toHttpUrlOrNull() ?: return false
        if (url1.host != url2.host) return false

        val pathSegments1 = normalizePathSegments(url1.pathSegments)
        val pathSegments2 = normalizePathSegments(url2.pathSegments)
        if (pathSegments1 != pathSegments2) return false

        if (url1.query.isNullOrEmpty()) return url2.query.isNullOrEmpty()
        return url1.query == url2.query
    }

    /**
     * Removes empty segments and converts all to lower case.
     * @param input List of path segments
     * @return Normalized list of path segments
     */
    private fun normalizePathSegments(input: List<String>): List<String> {
        val result: MutableList<String> = ArrayList()
        for (string in input) {
            if (string.isNotEmpty()) result.add(string.lowercase())
        }
        return result
    }
}
