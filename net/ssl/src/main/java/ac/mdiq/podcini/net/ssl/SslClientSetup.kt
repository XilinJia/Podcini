package ac.mdiq.podcini.net.ssl

import ac.mdiq.podcini.net.ssl.BackportTrustManager.create
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient.Builder
import java.util.*

object SslClientSetup {
    fun installCertificates(builder: Builder) {
        val trustManager = create()
        builder.sslSocketFactory(PodciniSslSocketFactory(trustManager!!), trustManager)
        builder.connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
    }
}
