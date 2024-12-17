package ac.mdiq.podcini.net.sync.nextcloud

import ac.mdiq.podcini.net.sync.HostnameParser
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Request.Builder
import okhttp3.RequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.net.URL

class NextcloudLoginFlow(
        private val httpClient: OkHttpClient,
        private val rawHostUrl: String,
        private val context: Context,
        private val callback: AuthenticationCallback) {

    private val hostname = HostnameParser(rawHostUrl)
    private var token: String? = null
    private var endpoint: String? = null
    private var isWaitingForBrowser:Boolean = false

    fun saveInstanceState(): ArrayList<String?> {
        val state = ArrayList<String?>()
        state.add(rawHostUrl)
        state.add(token)
        state.add(endpoint)
        return state
    }

    fun start() {
        if (token != null) {
            poll()
            return
        }

        val coroutineScope = CoroutineScope(Dispatchers.Main)
        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val url = URI(hostname.scheme, null, hostname.host, hostname.port, hostname.subfolder + "/index.php/login/v2", null, null).toURL()
                    val result = doRequest(url, "")
                    val loginUrl = result.getString("login")
                    token = result.getJSONObject("poll").getString("token")
                    endpoint = result.getJSONObject("poll").getString("endpoint")
                    loginUrl
                }
                withContext(Dispatchers.Main) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(result))
                    context.startActivity(browserIntent)
                    isWaitingForBrowser = true
                }
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
                token = null
                endpoint = null
                callback.onNextcloudAuthError(e.localizedMessage)
            }
        }
    }

    private suspend fun <T> retryIO(retries: Int = 3, delay: Long = 1000, block: suspend () -> T): T {
        var attempt = 0
        while (attempt < retries) {
            try { return block() }
            catch (e: Throwable) {
                if (attempt < retries - 1) {
                    delay(delay)
                    attempt++
                } else throw e
            }
        }
        throw RuntimeException("Maximum retries exceeded")
    }

    // trigger poll only when returning from the browser
    fun onResume(){
        if (token != null && isWaitingForBrowser){
            poll()
            isWaitingForBrowser = false
        }
    }

    fun poll() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // time out in 5 minutes, retry 5 times
                val result = withTimeout(5 * 60 * 1000) { retryIO(5) { doRequest(URI.create(endpoint).toURL(), "token=$token") } }
                withContext(Dispatchers.Main) { callback.onNextcloudAuthenticated(result.getString("server"), result.getString("loginName"), result.getString("appPassword")) }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    token = null
                    endpoint = null
                    callback.onNextcloudAuthError(e.localizedMessage)
                }
            }
        }
    }

    fun cancel() {
//        TODO: need to cancel the coroutines
    }

    @Throws(IOException::class, JSONException::class)
    private fun doRequest(url: URL, bodyContent: String): JSONObject {
        Logd(TAG, "doRequest $url $bodyContent")
        val requestBody = RequestBody.create("application/x-www-form-urlencoded".toMediaType(), bodyContent)
        val request: Request = Builder().url(url).method("POST", requestBody).build()
        val response = httpClient.newCall(request).execute()
        if (response.code != 200) {
            response.close()
            throw IOException("Return code " + response.code)
        }
        val body = response.body ?: throw IOException("Empty response")
        Logd(TAG, "doRequest body: $body ")
        return JSONObject(body.string())
    }

    interface AuthenticationCallback {
        fun onNextcloudAuthenticated(server: String, username: String, password: String)
        fun onNextcloudAuthError(errorMessage: String?)
    }

    companion object {
        private val TAG: String = NextcloudLoginFlow::class.simpleName ?: "Anonymous"

        fun fromInstanceState(httpClient: OkHttpClient, context: Context, callback: AuthenticationCallback, instanceState: ArrayList<String>): NextcloudLoginFlow {
            val flow = NextcloudLoginFlow(httpClient, instanceState[0], context, callback)
            flow.token = instanceState[1]
            flow.endpoint = instanceState[2]
            return flow
        }
    }
}
