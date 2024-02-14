package de.danoeh.antennapod.net.sync.nextcloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import de.danoeh.antennapod.net.sync.HostnameParser
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
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
import java.util.concurrent.TimeUnit

class NextcloudLoginFlow(private val httpClient: OkHttpClient,
                         private val rawHostUrl: String,
                         private val context: Context,
                         private val callback: AuthenticationCallback
) {
    private val hostname = HostnameParser(rawHostUrl)
    private var token: String? = null
    private var endpoint: String? = null
    private var startDisposable: Disposable? = null
    private var pollDisposable: Disposable? = null

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
        startDisposable = Observable.fromCallable {
            val url = URI(hostname.scheme, null, hostname.host, hostname.port,
                hostname.subfolder + "/index.php/login/v2", null, null).toURL()
            val result = doRequest(url, "")
            val loginUrl = result.getString("login")
            this.token = result.getJSONObject("poll").getString("token")
            this.endpoint = result.getJSONObject("poll").getString("endpoint")
            loginUrl
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result: String? ->
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(result))
                    context.startActivity(browserIntent)
                    poll()
                }, { error: Throwable ->
                    Log.e(TAG, Log.getStackTraceString(error))
                    this.token = null
                    this.endpoint = null
                    callback.onNextcloudAuthError(error.localizedMessage)
                })
    }

    private fun poll() {
        pollDisposable = Observable.fromCallable { doRequest(URI.create(endpoint).toURL(), "token=$token") }
            .retryWhen { t: Observable<Throwable?> -> t.delay(1, TimeUnit.SECONDS) }
            .timeout(5, TimeUnit.MINUTES)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result: JSONObject ->
                callback.onNextcloudAuthenticated(
                    result.getString("server"), result.getString("loginName"), result.getString("appPassword"))
            },
                { error: Throwable ->
                    this.token = null
                    this.endpoint = null
                    callback.onNextcloudAuthError(error.localizedMessage)
                })
    }

    fun cancel() {
        if (startDisposable != null) {
            startDisposable!!.dispose()
        }
        if (pollDisposable != null) {
            pollDisposable!!.dispose()
        }
    }

    @Throws(IOException::class, JSONException::class)
    private fun doRequest(url: URL, bodyContent: String): JSONObject {
        val requestBody = RequestBody.create(
            "application/x-www-form-urlencoded".toMediaType(), bodyContent)
        val request: Request = Builder().url(url).method("POST", requestBody).build()
        val response = httpClient.newCall(request).execute()
        if (response.code != 200) {
            response.close()
            throw IOException("Return code " + response.code)
        }
        val body = response.body ?: throw IOException("Empty response")
        return JSONObject(body.string())
    }

    interface AuthenticationCallback {
        fun onNextcloudAuthenticated(server: String, username: String, password: String)

        fun onNextcloudAuthError(errorMessage: String?)
    }

    companion object {
        private const val TAG = "NextcloudLoginFlow"

        fun fromInstanceState(httpClient: OkHttpClient, context: Context,
                              callback: AuthenticationCallback, instanceState: ArrayList<String>
        ): NextcloudLoginFlow {
            val flow = NextcloudLoginFlow(httpClient, instanceState[0], context, callback)
            flow.token = instanceState[1]
            flow.endpoint = instanceState[2]
            return flow
        }
    }
}
