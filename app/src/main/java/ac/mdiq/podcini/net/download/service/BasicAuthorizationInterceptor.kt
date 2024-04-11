package ac.mdiq.podcini.net.download.service

import android.text.TextUtils
import android.util.Log
import ac.mdiq.podcini.net.download.service.HttpCredentialEncoder.encode
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.util.URIUtil
import ac.mdiq.podcini.net.download.serviceinterface.DownloadRequest
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Request
import okhttp3.Request.Builder
import okhttp3.Response
import java.io.IOException
import java.net.HttpURLConnection

class BasicAuthorizationInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Chain): Response {
        val request: Request = chain.request()

        var response: Response = chain.proceed(request)

        if (response.code != HttpURLConnection.HTTP_UNAUTHORIZED) {
            return response
        }

        val newRequest: Builder = request.newBuilder()
        if (!TextUtils.equals(response.request.url.toString(), request.url.toString())) {
            // Redirect detected. OkHTTP does not re-add the headers on redirect, so calling the new location directly.
            newRequest.url(response.request.url)

            val authorizationHeaders = request.headers.values(HEADER_AUTHORIZATION)
            if (authorizationHeaders.isNotEmpty() && authorizationHeaders[0].isNotEmpty()) {
                // Call already had authorization headers. Try again with the same credentials.
                newRequest.header(HEADER_AUTHORIZATION, authorizationHeaders[0])
                return chain.proceed(newRequest.build())
            }
        }

        var userInfo = ""
        if (request.tag() is DownloadRequest) {
            val downloadRequest = request.tag() as? DownloadRequest
            if (downloadRequest?.source != null) {
                userInfo = URIUtil.getURIFromRequestUrl(downloadRequest.source!!).userInfo
                if (userInfo.isEmpty() && (!downloadRequest.username.isNullOrEmpty() || !downloadRequest.password.isNullOrEmpty())) {
                    userInfo = downloadRequest.username + ":" + downloadRequest.password
                }
            }
        } else {
            userInfo = DBReader.getImageAuthentication(request.url.toString())
        }

        if (userInfo.isEmpty()) {
            Log.d(TAG, "no credentials for '" + request.url + "'")
            return response
        }

        if (!userInfo.contains(":")) {
            Log.d(TAG, "Invalid credentials for '" + request.url + "'")
            return response
        }
        val username = userInfo.substring(0, userInfo.indexOf(':'))
        val password = userInfo.substring(userInfo.indexOf(':') + 1)

        Log.d(TAG, "Authorization failed, re-trying with ISO-8859-1 encoded credentials")
        newRequest.header(HEADER_AUTHORIZATION, encode(username, password, "ISO-8859-1"))
        response = chain.proceed(newRequest.build())

        if (response.code != HttpURLConnection.HTTP_UNAUTHORIZED) {
            return response
        }

        Log.d(TAG, "Authorization failed, re-trying with UTF-8 encoded credentials")
        newRequest.header(HEADER_AUTHORIZATION, encode(username, password, "UTF-8"))
        return chain.proceed(newRequest.build())
    }

    companion object {
        private const val TAG = "BasicAuthInterceptor"
        private const val HEADER_AUTHORIZATION = "Authorization"
    }
}
