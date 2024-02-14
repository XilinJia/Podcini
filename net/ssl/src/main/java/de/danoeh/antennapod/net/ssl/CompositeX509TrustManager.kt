package de.danoeh.antennapod.net.ssl

import android.annotation.SuppressLint
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.X509TrustManager

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
        val certificates: MutableList<X509Certificate> = ArrayList()
        for (trustManager in trustManagers) {
            certificates.addAll(Arrays.asList(*trustManager.acceptedIssuers))
        }
        return certificates.toTypedArray<X509Certificate>()
    }
}
