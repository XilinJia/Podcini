package ac.mdiq.podcini.net.download.service

import android.util.Log
import ac.mdiq.podcini.storage.model.download.ProxyConfig
import ac.mdiq.podcini.net.ssl.SslClientSetup
import ac.mdiq.podcini.util.Logd
import okhttp3.*
import okhttp3.Credentials.basic
import okhttp3.OkHttpClient.Builder
import java.io.File
import java.net.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile

/**
 * Provides access to a HttpClient singleton.
 */
object PodciniHttpClient {
    private const val TAG = "PodciniHttpClient"
    private const val CONNECTION_TIMEOUT = 10000
    private const val READ_TIMEOUT = 30000
    private const val MAX_CONNECTIONS = 8
    private var cacheDirectory: File? = null
    private var proxyConfig: ProxyConfig? = null

    @Volatile
    private var httpClient: OkHttpClient? = null

    /**
     * Returns the HttpClient singleton.
     */
    @JvmStatic
    @Synchronized
    fun getHttpClient(): OkHttpClient {
        if (httpClient == null) httpClient = newBuilder().build()
        return httpClient!!
    }

    @JvmStatic
    @Synchronized
    fun reinit() {
        httpClient = newBuilder().build()
    }

    /**
     * Creates a new HTTP client.  Most users should just use
     * getHttpClient() to get the standard Podcini client,
     * but sometimes it's necessary for others to have their own
     * copy so that the clients don't share state.
     * @return http client
     */
    @JvmStatic
    fun newBuilder(): Builder {
        Logd(TAG, "Creating new instance of HTTP client")

        System.setProperty("http.maxConnections", MAX_CONNECTIONS.toString())

        val builder = Builder()
        builder.interceptors().add(BasicAuthorizationInterceptor())
        builder.networkInterceptors().add(UserAgentInterceptor())

        // set cookie handler
        val cm = CookieManager()
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER)
        builder.cookieJar(JavaNetCookieJar(cm))

        // set timeouts
        builder.connectTimeout(CONNECTION_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        builder.readTimeout(READ_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        builder.writeTimeout(READ_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        builder.cache(Cache(cacheDirectory!!, 20L * 1000000)) // 20MB

        // configure redirects
        builder.followRedirects(true)
        builder.followSslRedirects(true)

        if (proxyConfig != null && proxyConfig!!.type != Proxy.Type.DIRECT && !proxyConfig?.host.isNullOrEmpty()) {
            val port = if (proxyConfig!!.port > 0) proxyConfig!!.port else ProxyConfig.DEFAULT_PORT
            val address: SocketAddress = InetSocketAddress.createUnresolved(proxyConfig!!.host, port)
            builder.proxy(Proxy(proxyConfig!!.type, address))
            if (!proxyConfig!!.username.isNullOrEmpty() && proxyConfig!!.password != null) {
                builder.proxyAuthenticator { _: Route?, response: Response ->
                    val credentials = basic(proxyConfig!!.username!!, proxyConfig!!.password!!)
                    response.request.newBuilder()
                        .header("Proxy-Authorization", credentials)
                        .build()
                }
            }
        }

        SslClientSetup.installCertificates(builder)
        return builder
    }

    @JvmStatic
    fun setCacheDirectory(cacheDirectory: File?) {
        PodciniHttpClient.cacheDirectory = cacheDirectory
    }

    @JvmStatic
    fun setProxyConfig(proxyConfig: ProxyConfig?) {
        PodciniHttpClient.proxyConfig = proxyConfig
    }
}
