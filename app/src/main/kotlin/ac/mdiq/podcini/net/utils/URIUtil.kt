package ac.mdiq.podcini.net.utils

import ac.mdiq.podcini.util.Logd
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.HttpURLConnection
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

//fun getFinalUrl(url: String): String? {
//    var connection: HttpURLConnection? = null
//    return try {
//        val urlObj = URL(url)
//        connection = urlObj.openConnection() as HttpURLConnection
//        connection.instanceFollowRedirects = true
//        connection.requestMethod = "GET"
//        connection.connect()
//        connection.url.toString()
//    } catch (e: Exception) {
//        e.printStackTrace()
//        null
//    } finally { connection?.disconnect() }
//}

fun getFinalRedirectedUrl(url: String): String? {
    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()
    client.newCall(request).execute().use { response -> return if (response.isSuccessful) response.request.url.toString() else { null } }
}