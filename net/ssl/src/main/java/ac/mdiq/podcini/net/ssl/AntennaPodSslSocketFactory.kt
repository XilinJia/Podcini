package ac.mdiq.podcini.net.ssl

import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.security.GeneralSecurityException
import java.security.NoSuchAlgorithmException
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager

/**
 * SSLSocketFactory that does not use TLS 1.0
 * This fixes issues with old Android versions that abort if the server does not know TLS 1.0
 */
class PodciniSslSocketFactory(trustManager: TrustManager) : SSLSocketFactory() {
    private var factory: SSLSocketFactory? = null

    init {
        try {
            var sslContext: SSLContext

            try {
                sslContext = SSLContext.getInstance("TLSv1.3")
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
                // In the play flavor (security provider can vary), some devices only support TLSv1.2.
                sslContext = SSLContext.getInstance("TLSv1.2")
            }

            sslContext.init(null, arrayOf(trustManager), null)
            factory = sslContext.socketFactory
        } catch (e: GeneralSecurityException) {
            e.printStackTrace()
        }
    }

    override fun getDefaultCipherSuites(): Array<String> {
        return factory!!.defaultCipherSuites
    }

    override fun getSupportedCipherSuites(): Array<String> {
        return factory!!.supportedCipherSuites
    }

    @Throws(IOException::class)
    override fun createSocket(): Socket {
        val result = factory!!.createSocket() as SSLSocket
        configureSocket(result)
        return result
    }

    @Throws(IOException::class)
    override fun createSocket(var1: String, var2: Int): Socket {
        val result = factory!!.createSocket(var1, var2) as SSLSocket
        configureSocket(result)
        return result
    }

    @Throws(IOException::class)
    override fun createSocket(var1: Socket, var2: String, var3: Int, var4: Boolean): Socket {
        val result = factory!!.createSocket(var1, var2, var3, var4) as SSLSocket
        configureSocket(result)
        return result
    }

    @Throws(IOException::class)
    override fun createSocket(var1: InetAddress, var2: Int): Socket {
        val result = factory!!.createSocket(var1, var2) as SSLSocket
        configureSocket(result)
        return result
    }

    @Throws(IOException::class)
    override fun createSocket(var1: String, var2: Int, var3: InetAddress, var4: Int): Socket {
        val result = factory!!.createSocket(var1, var2, var3, var4) as SSLSocket
        configureSocket(result)
        return result
    }

    @Throws(IOException::class)
    override fun createSocket(var1: InetAddress, var2: Int, var3: InetAddress, var4: Int): Socket {
        val result = factory!!.createSocket(var1, var2, var3, var4) as SSLSocket
        configureSocket(result)
        return result
    }

    private fun configureSocket(s: SSLSocket) {
        // TLS 1.0 is enabled by default on some old systems, which causes connection errors. This disables that.
        try {
            s.enabledProtocols = arrayOf("TLSv1.3", "TLSv1.2")
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            // In play flavor, supported cipher suites may vary.
            // Old protocols might be necessary to keep things working.
            s.enabledProtocols = arrayOf("TLSv1.2", "TLSv1.1", "TLSv1")
        }
    }
}