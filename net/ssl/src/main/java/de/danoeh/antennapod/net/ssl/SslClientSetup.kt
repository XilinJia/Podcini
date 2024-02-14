package de.danoeh.antennapod.net.ssl

import de.danoeh.antennapod.net.ssl.BackportTrustManager.create
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient.Builder
import java.util.*

object SslClientSetup {
    fun installCertificates(builder: Builder) {
        val trustManager = create()
        builder.sslSocketFactory(AntennaPodSslSocketFactory(trustManager!!), trustManager)
        builder.connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
    }
}
