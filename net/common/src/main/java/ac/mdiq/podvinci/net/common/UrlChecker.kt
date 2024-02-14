package ac.mdiq.podvinci.net.common

import android.net.Uri
import android.text.TextUtils
import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.UnsupportedEncodingException
import java.net.URLDecoder

/**
 * Provides methods for checking and editing a URL.
 */
object UrlChecker {
    /**
     * Logging tag.
     */
    private const val TAG = "UrlChecker"

    private const val AP_SUBSCRIBE = "podvinci-subscribe://"
    private const val AP_SUBSCRIBE_DEEPLINK = "podvinci.org/deeplink/subscribe"

    /**
     * Checks if URL is valid and modifies it if necessary.
     *
     * @param url The url which is going to be prepared
     * @return The prepared url
     */
    @JvmStatic
    fun prepareUrl(url: String): String {
        var url = url
        url = url.trim { it <= ' ' }
        val lowerCaseUrl = url.lowercase() // protocol names are case insensitive
        if (lowerCaseUrl.startsWith("feed://")) {
            Log.d(TAG, "Replacing feed:// with http://")
            return prepareUrl(url.substring("feed://".length))
        } else if (lowerCaseUrl.startsWith("pcast://")) {
            Log.d(TAG, "Removing pcast://")
            return prepareUrl(url.substring("pcast://".length))
        } else if (lowerCaseUrl.startsWith("pcast:")) {
            Log.d(TAG, "Removing pcast:")
            return prepareUrl(url.substring("pcast:".length))
        } else if (lowerCaseUrl.startsWith("itpc")) {
            Log.d(TAG, "Replacing itpc:// with http://")
            return prepareUrl(url.substring("itpc://".length))
        } else if (lowerCaseUrl.startsWith(AP_SUBSCRIBE)) {
            Log.d(TAG, "Removing podvinci-subscribe://")
            return prepareUrl(url.substring(AP_SUBSCRIBE.length))
        } else if (lowerCaseUrl.contains(AP_SUBSCRIBE_DEEPLINK)) {
            Log.d(TAG, "Removing $AP_SUBSCRIBE_DEEPLINK")
            val removedWebsite = url.substring(url.indexOf("?url=") + "?url=".length)
            return try {
                prepareUrl(URLDecoder.decode(removedWebsite, "UTF-8"))
            } catch (e: UnsupportedEncodingException) {
                prepareUrl(removedWebsite)
            }
        } else if (!(lowerCaseUrl.startsWith("http://") || lowerCaseUrl.startsWith("https://"))) {
            Log.d(TAG, "Adding http:// at the beginning of the URL")
            return "http://$url"
        } else {
            return url
        }
    }

    /**
     * Checks if URL is valid and modifies it if necessary.
     * This method also handles protocol relative URLs.
     *
     * @param url  The url which is going to be prepared
     * @param base The url against which the (possibly relative) url is applied. If this is null,
     * the result of prepareURL(url) is returned instead.
     * @return The prepared url
     */
    @JvmStatic
    fun prepareUrl(url: String, base: String?): String {
        var url = url
        var base = base ?: return prepareUrl(url)
        url = url.trim { it <= ' ' }
        base = prepareUrl(base)
        val urlUri = Uri.parse(url)
        val baseUri = Uri.parse(base)
        return if (urlUri.isRelative && baseUri.isAbsolute) {
            urlUri.buildUpon().scheme(baseUri.scheme).build().toString()
        } else {
            prepareUrl(url)
        }
    }

    fun containsUrl(list: List<String?>, url: String?): Boolean {
        for (item in list) {
            if (urlEquals(item, url)) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun urlEquals(string1: String?, string2: String?): Boolean {
        val url1 = string1!!.toHttpUrlOrNull()
        val url2 = string2!!.toHttpUrlOrNull()
        if (url1!!.host != url2!!.host) {
            return false
        }
        val pathSegments1 = normalizePathSegments(url1.pathSegments)
        val pathSegments2 = normalizePathSegments(url2.pathSegments)
        if (pathSegments1 != pathSegments2) {
            return false
        }
        if (TextUtils.isEmpty(url1.query)) {
            return TextUtils.isEmpty(url2.query)
        }
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
            if (!TextUtils.isEmpty(string)) {
                result.add(string.lowercase())
            }
        }
        return result
    }
}
