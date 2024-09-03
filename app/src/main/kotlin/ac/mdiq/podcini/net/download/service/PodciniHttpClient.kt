package ac.mdiq.podcini.net.download.service

import ac.mdiq.podcini.net.download.service.HttpCredentialEncoder.encode
import ac.mdiq.podcini.net.download.serviceinterface.DownloadRequest
import ac.mdiq.podcini.net.utils.URIUtil
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.ProxyConfig
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.config.ClientConfig
import android.annotation.SuppressLint
import android.net.TrafficStats
import android.util.Log
import okhttp3.*
import okhttp3.Credentials.basic
import okhttp3.Interceptor.Chain
import okhttp3.OkHttpClient.Builder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.*
import java.nio.charset.Charset
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*
import kotlin.concurrent.Volatile


/**
 * Provides access to a HttpClient singleton.
 */
object PodciniHttpClient {
    private val TAG: String = PodciniHttpClient::class.simpleName ?: "Anonymous"
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
                    response.request.newBuilder().header("Proxy-Authorization", credentials).build()
                }
            }
        }

        installCertificates(builder)
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

    class UserAgentInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Chain): Response {
            TrafficStats.setThreadStatsTag(Thread.currentThread().id.toInt())
            return chain.proceed(chain.request().newBuilder().header("User-Agent", ClientConfig.USER_AGENT?:"").build())
        }
    }

    private fun installCertificates(builder: Builder) {
        val trustManager = create()
        builder.sslSocketFactory(PodciniSslSocketFactory(trustManager!!), trustManager)
        builder.connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
    }

    /**
     * SSLSocketFactory that does not use TLS 1.0
     * This fixes issues with old Android versions that abort if the server does not know TLS 1.0
     */
    class PodciniSslSocketFactory(trustManager: TrustManager) : SSLSocketFactory() {
        private lateinit var factory: SSLSocketFactory

        init {
            try {
                var sslContext: SSLContext
                try { sslContext = SSLContext.getInstance("TLSv1.3")
                } catch (e: NoSuchAlgorithmException) {
                    e.printStackTrace()
                    // In the play flavor (security provider can vary), some devices only support TLSv1.2.
                    sslContext = SSLContext.getInstance("TLSv1.2")
                }
                sslContext.init(null, arrayOf(trustManager), null)
                factory = sslContext.socketFactory
            } catch (e: GeneralSecurityException) { e.printStackTrace() }
        }

        override fun getDefaultCipherSuites(): Array<String> {
            return factory.defaultCipherSuites
        }

        override fun getSupportedCipherSuites(): Array<String> {
            return factory.supportedCipherSuites
        }

        @Throws(IOException::class)
        override fun createSocket(): Socket {
            val result = factory.createSocket() as SSLSocket
            configureSocket(result)
            return result
        }

        @Throws(IOException::class)
        override fun createSocket(var1: String, var2: Int): Socket {
            val result = factory.createSocket(var1, var2) as SSLSocket
            configureSocket(result)
            return result
        }

        @Throws(IOException::class)
        override fun createSocket(var1: Socket, var2: String, var3: Int, var4: Boolean): Socket {
            val result = factory.createSocket(var1, var2, var3, var4) as SSLSocket
            configureSocket(result)
            return result
        }

        @Throws(IOException::class)
        override fun createSocket(var1: InetAddress, var2: Int): Socket {
            val result = factory.createSocket(var1, var2) as SSLSocket
            configureSocket(result)
            return result
        }

        @Throws(IOException::class)
        override fun createSocket(var1: String, var2: Int, var3: InetAddress, var4: Int): Socket {
            val result = factory.createSocket(var1, var2, var3, var4) as SSLSocket
            configureSocket(result)
            return result
        }

        @Throws(IOException::class)
        override fun createSocket(var1: InetAddress, var2: Int, var3: InetAddress, var4: Int): Socket {
            val result = factory.createSocket(var1, var2, var3, var4) as SSLSocket
            configureSocket(result)
            return result
        }

        private fun configureSocket(s: SSLSocket) {
            // TLS 1.0 is enabled by default on some old systems, which causes connection errors. This disables that.
            try {
                TrafficStats.setThreadStatsTag(Thread.currentThread().id.toInt())
                s.enabledProtocols = arrayOf("TLSv1.3", "TLSv1.2")
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                // In play flavor, supported cipher suites may vary.
                // Old protocols might be necessary to keep things working.
                s.enabledProtocols = arrayOf("TLSv1.2", "TLSv1.1", "TLSv1")
            }
        }
    }

    class BasicAuthorizationInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Chain): Response {
            TrafficStats.setThreadStatsTag(Thread.currentThread().id.toInt())

            val request: Request = chain.request()
            var response: Response = chain.proceed(request)

            if (response.code != HttpURLConnection.HTTP_UNAUTHORIZED) return response

            val newRequest: Request.Builder = request.newBuilder()
            if (response.request.url.toString() != request.url.toString()) {
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
                    userInfo = URIUtil.getURIFromRequestUrl(downloadRequest.source).userInfo
                    if (userInfo.isEmpty() && (!downloadRequest.username.isNullOrEmpty() || !downloadRequest.password.isNullOrEmpty()))
                        userInfo = downloadRequest.username + ":" + downloadRequest.password
                }
            } else userInfo = getImageAuthentication(request.url.toString())

            if (userInfo.isEmpty()) {
                Logd(TAG, "no credentials for '" + request.url + "'")
                return response
            }

            if (!userInfo.contains(":")) {
                Logd(TAG, "Invalid credentials for '" + request.url + "'")
                return response
            }
            val username = userInfo.substring(0, userInfo.indexOf(':'))
            val password = userInfo.substring(userInfo.indexOf(':') + 1)

            Logd(TAG, "Authorization failed, re-trying with ISO-8859-1 encoded credentials")
            newRequest.header(HEADER_AUTHORIZATION, encode(username, password, "ISO-8859-1"))
            response = chain.proceed(newRequest.build())

            if (response.code != HttpURLConnection.HTTP_UNAUTHORIZED) return response

            Logd(TAG, "Authorization failed, re-trying with UTF-8 encoded credentials")
            newRequest.header(HEADER_AUTHORIZATION, encode(username, password, "UTF-8"))
            return chain.proceed(newRequest.build())
        }

        /**
         * Returns credentials based on image URL
         * @param imageUrl The URL of the image
         * @return Credentials in format "Username:Password", empty String if no authorization given
         */
        private fun getImageAuthentication(imageUrl: String): String {
            Logd(TAG, "getImageAuthentication() called with: imageUrl = [$imageUrl]")
            val episode = realm.query(Episode::class).query("imageUrl == $0", imageUrl).first().find() ?: return ""
            val username = episode.feed?.preferences?.username
            val password = episode.feed?.preferences?.password
            if (username != null && password != null) return "$username:$password"
            return ""
        }

        companion object {
            private val TAG: String = BasicAuthorizationInterceptor::class.simpleName ?: "Anonymous"
            private const val HEADER_AUTHORIZATION = "Authorization"
        }
    }

    private fun getSystemTrustManager(keystore: KeyStore?): X509TrustManager {
        val factory: TrustManagerFactory
        try {
            factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(keystore)
            for (manager in factory.trustManagers) {
                if (manager is X509TrustManager) return manager
            }
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: KeyStoreException) {
            e.printStackTrace()
        }
        throw IllegalStateException("Unexpected default trust managers")
    }

    @JvmStatic
    fun create(): X509TrustManager? {
        try {
            val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
            keystore.load(null) // Clear
            val cf = CertificateFactory.getInstance("X.509")
            keystore.setCertificateEntry("BACKPORT_COMODO_ROOT_CA", cf.generateCertificate(
                ByteArrayInputStream(COMODO.toByteArray(Charset.forName("UTF-8")))))
            keystore.setCertificateEntry("SECTIGO_USER_TRUST_CA", cf.generateCertificate(
                ByteArrayInputStream(SECTIGO_USER_TRUST.toByteArray(Charset.forName("UTF-8")))))
            keystore.setCertificateEntry("LETSENCRYPT_ISRG_CA", cf.generateCertificate(
                ByteArrayInputStream(LETSENCRYPT_ISRG.toByteArray(Charset.forName("UTF-8")))))

            val managers: MutableList<X509TrustManager> = ArrayList()
            managers.add(getSystemTrustManager(keystore))
            managers.add(getSystemTrustManager(null))
            return CompositeX509TrustManager(managers)
        } catch (e: KeyStoreException) {
            Log.e(TAG, Log.getStackTraceString(e))
            return null
        } catch (e: CertificateException) {
            Log.e(TAG, Log.getStackTraceString(e))
            return null
        } catch (e: NoSuchAlgorithmException) {
            Log.e(TAG, Log.getStackTraceString(e))
            return null
        } catch (e: IOException) {
            Log.e(TAG, Log.getStackTraceString(e))
            return null
        }
    }

    /**
     * Represents an ordered list of [X509TrustManager]s with additive trust. If any one of the composed managers
     * trusts a certificate chain, then it is trusted by the composite manager.
     * Based on https://stackoverflow.com/a/16229909
     */
    @SuppressLint("CustomX509TrustManager")
    class CompositeX509TrustManager(private val trustManagers: List<X509TrustManager>) : X509TrustManager {
        @Throws(CertificateException::class)
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            var reason: CertificateException? = null
            for (trustManager in trustManagers) {
                try {
                    trustManager.checkClientTrusted(chain, authType)
                    return  // someone trusts them. success!
                } catch (e: CertificateException) {
                    // maybe someone else will trust them
                    reason = e
                }
            }
            throw reason!!
        }

        @Throws(CertificateException::class)
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            var reason: CertificateException? = null
            for (trustManager in trustManagers) {
                try {
                    trustManager.checkServerTrusted(chain, authType)
                    return  // someone trusts them. success!
                } catch (e: CertificateException) {
                    // maybe someone else will trust them
                    reason = e
                }
            }
            throw reason!!
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            val certificates: MutableList<X509Certificate> = java.util.ArrayList()
            for (trustManager in trustManagers) {
//            TODO: appears time consuming
                certificates.addAll(listOf(*trustManager.acceptedIssuers))
            }
            return certificates.toTypedArray<X509Certificate>()
        }
    }

    private const val SECTIGO_USER_TRUST: String = ("-----BEGIN CERTIFICATE-----\n"
            + "MIIF3jCCA8agAwIBAgIQAf1tMPyjylGoG7xkDjUDLTANBgkqhkiG9w0BAQwFADCB\n"
            + "iDELMAkGA1UEBhMCVVMxEzARBgNVBAgTCk5ldyBKZXJzZXkxFDASBgNVBAcTC0pl\n"
            + "cnNleSBDaXR5MR4wHAYDVQQKExVUaGUgVVNFUlRSVVNUIE5ldHdvcmsxLjAsBgNV\n"
            + "BAMTJVVTRVJUcnVzdCBSU0EgQ2VydGlmaWNhdGlvbiBBdXRob3JpdHkwHhcNMTAw\n"
            + "MjAxMDAwMDAwWhcNMzgwMTE4MjM1OTU5WjCBiDELMAkGA1UEBhMCVVMxEzARBgNV\n"
            + "BAgTCk5ldyBKZXJzZXkxFDASBgNVBAcTC0plcnNleSBDaXR5MR4wHAYDVQQKExVU\n"
            + "aGUgVVNFUlRSVVNUIE5ldHdvcmsxLjAsBgNVBAMTJVVTRVJUcnVzdCBSU0EgQ2Vy\n"
            + "dGlmaWNhdGlvbiBBdXRob3JpdHkwggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAwggIK\n"
            + "AoICAQCAEmUXNg7D2wiz0KxXDXbtzSfTTK1Qg2HiqiBNCS1kCdzOiZ/MPans9s/B\n"
            + "3PHTsdZ7NygRK0faOca8Ohm0X6a9fZ2jY0K2dvKpOyuR+OJv0OwWIJAJPuLodMkY\n"
            + "tJHUYmTbf6MG8YgYapAiPLz+E/CHFHv25B+O1ORRxhFnRghRy4YUVD+8M/5+bJz/\n"
            + "Fp0YvVGONaanZshyZ9shZrHUm3gDwFA66Mzw3LyeTP6vBZY1H1dat//O+T23LLb2\n"
            + "VN3I5xI6Ta5MirdcmrS3ID3KfyI0rn47aGYBROcBTkZTmzNg95S+UzeQc0PzMsNT\n"
            + "79uq/nROacdrjGCT3sTHDN/hMq7MkztReJVni+49Vv4M0GkPGw/zJSZrM233bkf6\n"
            + "c0Plfg6lZrEpfDKEY1WJxA3Bk1QwGROs0303p+tdOmw1XNtB1xLaqUkL39iAigmT\n"
            + "Yo61Zs8liM2EuLE/pDkP2QKe6xJMlXzzawWpXhaDzLhn4ugTncxbgtNMs+1b/97l\n"
            + "c6wjOy0AvzVVdAlJ2ElYGn+SNuZRkg7zJn0cTRe8yexDJtC/QV9AqURE9JnnV4ee\n"
            + "UB9XVKg+/XRjL7FQZQnmWEIuQxpMtPAlR1n6BB6T1CZGSlCBst6+eLf8ZxXhyVeE\n"
            + "Hg9j1uliutZfVS7qXMYoCAQlObgOK6nyTJccBz8NUvXt7y+CDwIDAQABo0IwQDAd\n"
            + "BgNVHQ4EFgQUU3m/WqorSs9UgOHYm8Cd8rIDZsswDgYDVR0PAQH/BAQDAgEGMA8G\n"
            + "A1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQEMBQADggIBAFzUfA3P9wF9QZllDHPF\n"
            + "Up/L+M+ZBn8b2kMVn54CVVeWFPFSPCeHlCjtHzoBN6J2/FNQwISbxmtOuowhT6KO\n"
            + "VWKR82kV2LyI48SqC/3vqOlLVSoGIG1VeCkZ7l8wXEskEVX/JJpuXior7gtNn3/3\n"
            + "ATiUFJVDBwn7YKnuHKsSjKCaXqeYalltiz8I+8jRRa8YFWSQEg9zKC7F4iRO/Fjs\n"
            + "8PRF/iKz6y+O0tlFYQXBl2+odnKPi4w2r78NBc5xjeambx9spnFixdjQg3IM8WcR\n"
            + "iQycE0xyNN+81XHfqnHd4blsjDwSXWXavVcStkNr/+XeTWYRUc+ZruwXtuhxkYze\n"
            + "Sf7dNXGiFSeUHM9h4ya7b6NnJSFd5t0dCy5oGzuCr+yDZ4XUmFF0sbmZgIn/f3gZ\n"
            + "XHlKYC6SQK5MNyosycdiyA5d9zZbyuAlJQG03RoHnHcAP9Dc1ew91Pq7P8yF1m9/\n"
            + "qS3fuQL39ZeatTXaw2ewh0qpKJ4jjv9cJ2vhsE/zB+4ALtRZh8tSQZXq9EfX7mRB\n"
            + "VXyNWQKV3WKdwrnuWih0hKWbt5DHDAff9Yk2dDLWKMGwsAvgnEzDHNb842m1R0aB\n"
            + "L6KCq9NjRHDEjf8tM7qtj3u1cIiuPhnPQCjY/MiQu12ZIvVS5ljFH4gxQ+6IHdfG\n"
            + "jjxDah2nGN59PRbxYvnKkKj9\n"
            + "-----END CERTIFICATE-----\n")

    private const val COMODO: String = ("-----BEGIN CERTIFICATE-----\n"
            + "MIIF2DCCA8CgAwIBAgIQTKr5yttjb+Af907YWwOGnTANBgkqhkiG9w0BAQwFADCB\n"
            + "hTELMAkGA1UEBhMCR0IxGzAZBgNVBAgTEkdyZWF0ZXIgTWFuY2hlc3RlcjEQMA4G\n"
            + "A1UEBxMHU2FsZm9yZDEaMBgGA1UEChMRQ09NT0RPIENBIExpbWl0ZWQxKzApBgNV\n"
            + "BAMTIkNPTU9ETyBSU0EgQ2VydGlmaWNhdGlvbiBBdXRob3JpdHkwHhcNMTAwMTE5\n"
            + "MDAwMDAwWhcNMzgwMTE4MjM1OTU5WjCBhTELMAkGA1UEBhMCR0IxGzAZBgNVBAgT\n"
            + "EkdyZWF0ZXIgTWFuY2hlc3RlcjEQMA4GA1UEBxMHU2FsZm9yZDEaMBgGA1UEChMR\n"
            + "Q09NT0RPIENBIExpbWl0ZWQxKzApBgNVBAMTIkNPTU9ETyBSU0EgQ2VydGlmaWNh\n"
            + "dGlvbiBBdXRob3JpdHkwggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAwggIKAoICAQCR\n"
            + "6FSS0gpWsawNJN3Fz0RndJkrN6N9I3AAcbxT38T6KhKPS38QVr2fcHK3YX/JSw8X\n"
            + "pz3jsARh7v8Rl8f0hj4K+j5c+ZPmNHrZFGvnnLOFoIJ6dq9xkNfs/Q36nGz637CC\n"
            + "9BR++b7Epi9Pf5l/tfxnQ3K9DADWietrLNPtj5gcFKt+5eNu/Nio5JIk2kNrYrhV\n"
            + "/erBvGy2i/MOjZrkm2xpmfh4SDBF1a3hDTxFYPwyllEnvGfDyi62a+pGx8cgoLEf\n"
            + "Zd5ICLqkTqnyg0Y3hOvozIFIQ2dOciqbXL1MGyiKXCJ7tKuY2e7gUYPDCUZObT6Z\n"
            + "+pUX2nwzV0E8jVHtC7ZcryxjGt9XyD+86V3Em69FmeKjWiS0uqlWPc9vqv9JWL7w\n"
            + "qP/0uK3pN/u6uPQLOvnoQ0IeidiEyxPx2bvhiWC4jChWrBQdnArncevPDt09qZah\n"
            + "SL0896+1DSJMwBGB7FY79tOi4lu3sgQiUpWAk2nojkxl8ZEDLXB0AuqLZxUpaVIC\n"
            + "u9ffUGpVRr+goyhhf3DQw6KqLCGqR84onAZFdr+CGCe01a60y1Dma/RMhnEw6abf\n"
            + "Fobg2P9A3fvQQoh/ozM6LlweQRGBY84YcWsr7KaKtzFcOmpH4MN5WdYgGq/yapiq\n"
            + "crxXStJLnbsQ/LBMQeXtHT1eKJ2czL+zUdqnR+WEUwIDAQABo0IwQDAdBgNVHQ4E\n"
            + "FgQUu69+Aj36pvE8hI6t7jiY7NkyMtQwDgYDVR0PAQH/BAQDAgEGMA8GA1UdEwEB\n"
            + "/wQFMAMBAf8wDQYJKoZIhvcNAQEMBQADggIBAArx1UaEt65Ru2yyTUEUAJNMnMvl\n"
            + "wFTPoCWOAvn9sKIN9SCYPBMtrFaisNZ+EZLpLrqeLppysb0ZRGxhNaKatBYSaVqM\n"
            + "4dc+pBroLwP0rmEdEBsqpIt6xf4FpuHA1sj+nq6PK7o9mfjYcwlYRm6mnPTXJ9OV\n"
            + "2jeDchzTc+CiR5kDOF3VSXkAKRzH7JsgHAckaVd4sjn8OoSgtZx8jb8uk2Intzna\n"
            + "FxiuvTwJaP+EmzzV1gsD41eeFPfR60/IvYcjt7ZJQ3mFXLrrkguhxuhoqEwWsRqZ\n"
            + "CuhTLJK7oQkYdQxlqHvLI7cawiiFwxv/0Cti76R7CZGYZ4wUAc1oBmpjIXUDgIiK\n"
            + "boHGhfKppC3n9KUkEEeDys30jXlYsQab5xoq2Z0B15R97QNKyvDb6KkBPvVWmcke\n"
            + "jkk9u+UJueBPSZI9FoJAzMxZxuY67RIuaTxslbH9qh17f4a+Hg4yRvv7E491f0yL\n"
            + "S0Zj/gA0QHDBw7mh3aZw4gSzQbzpgJHqZJx64SIDqZxubw5lT2yHh17zbqD5daWb\n"
            + "QOhTsiedSrnAdyGN/4fy3ryM7xfft0kL0fJuMAsaDk527RH89elWsn2/x20Kk4yl\n"
            + "0MC2Hb46TpSi125sC8KKfPog88Tk5c0NqMuRkrF8hey1FGlmDoLnzc7ILaZRfyHB\n"
            + "NVOFBkpdn627G190\n"
            + "-----END CERTIFICATE-----")

    private const val LETSENCRYPT_ISRG: String = ("-----BEGIN CERTIFICATE-----\n"
            + "MIIFazCCA1OgAwIBAgIRAIIQz7DSQONZRGPgu2OCiwAwDQYJKoZIhvcNAQELBQAw\n"
            + "TzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh\n"
            + "cmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMTUwNjA0MTEwNDM4\n"
            + "WhcNMzUwNjA0MTEwNDM4WjBPMQswCQYDVQQGEwJVUzEpMCcGA1UEChMgSW50ZXJu\n"
            + "ZXQgU2VjdXJpdHkgUmVzZWFyY2ggR3JvdXAxFTATBgNVBAMTDElTUkcgUm9vdCBY\n"
            + "MTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBAK3oJHP0FDfzm54rVygc\n"
            + "h77ct984kIxuPOZXoHj3dcKi/vVqbvYATyjb3miGbESTtrFj/RQSa78f0uoxmyF+\n"
            + "0TM8ukj13Xnfs7j/EvEhmkvBioZxaUpmZmyPfjxwv60pIgbz5MDmgK7iS4+3mX6U\n"
            + "A5/TR5d8mUgjU+g4rk8Kb4Mu0UlXjIB0ttov0DiNewNwIRt18jA8+o+u3dpjq+sW\n"
            + "T8KOEUt+zwvo/7V3LvSye0rgTBIlDHCNAymg4VMk7BPZ7hm/ELNKjD+Jo2FR3qyH\n"
            + "B5T0Y3HsLuJvW5iB4YlcNHlsdu87kGJ55tukmi8mxdAQ4Q7e2RCOFvu396j3x+UC\n"
            + "B5iPNgiV5+I3lg02dZ77DnKxHZu8A/lJBdiB3QW0KtZB6awBdpUKD9jf1b0SHzUv\n"
            + "KBds0pjBqAlkd25HN7rOrFleaJ1/ctaJxQZBKT5ZPt0m9STJEadao0xAH0ahmbWn\n"
            + "OlFuhjuefXKnEgV4We0+UXgVCwOPjdAvBbI+e0ocS3MFEvzG6uBQE3xDk3SzynTn\n"
            + "jh8BCNAw1FtxNrQHusEwMFxIt4I7mKZ9YIqioymCzLq9gwQbooMDQaHWBfEbwrbw\n"
            + "qHyGO0aoSCqI3Haadr8faqU9GY/rOPNk3sgrDQoo//fb4hVC1CLQJ13hef4Y53CI\n"
            + "rU7m2Ys6xt0nUW7/vGT1M0NPAgMBAAGjQjBAMA4GA1UdDwEB/wQEAwIBBjAPBgNV\n"
            + "HRMBAf8EBTADAQH/MB0GA1UdDgQWBBR5tFnme7bl5AFzgAiIyBpY9umbbjANBgkq\n"
            + "hkiG9w0BAQsFAAOCAgEAVR9YqbyyqFDQDLHYGmkgJykIrGF1XIpu+ILlaS/V9lZL\n"
            + "ubhzEFnTIZd+50xx+7LSYK05qAvqFyFWhfFQDlnrzuBZ6brJFe+GnY+EgPbk6ZGQ\n"
            + "3BebYhtF8GaV0nxvwuo77x/Py9auJ/GpsMiu/X1+mvoiBOv/2X/qkSsisRcOj/KK\n"
            + "NFtY2PwByVS5uCbMiogziUwthDyC3+6WVwW6LLv3xLfHTjuCvjHIInNzktHCgKQ5\n"
            + "ORAzI4JMPJ+GslWYHb4phowim57iaztXOoJwTdwJx4nLCgdNbOhdjsnvzqvHu7Ur\n"
            + "TkXWStAmzOVyyghqpZXjFaH3pO3JLF+l+/+sKAIuvtd7u+Nxe5AW0wdeRlN8NwdC\n"
            + "jNPElpzVmbUq4JUagEiuTDkHzsxHpFKVK7q4+63SM1N95R1NbdWhscdCb+ZAJzVc\n"
            + "oyi3B43njTOQ5yOf+1CceWxG1bQVs5ZufpsMljq4Ui0/1lvh+wjChP4kqKOJ2qxq\n"
            + "4RgqsahDYVvTH9w7jXbyLeiNdd8XM2w9U/t7y0Ff/9yi0GE44Za4rF2LN9d11TPA\n"
            + "mRGunUHBcnWEvgJBQl9nJEiU0Zsnvgc/ubhPgXRR4Xq37Z0j4r7g1SgEEzwxA57d\n"
            + "emyPxgcYxn/eR44/KJ4EBs+lVDR3veyJm+kXQ99b21/+jh5Xos1AnX5iItreGCc=\n"
            + "-----END CERTIFICATE-----")

}
