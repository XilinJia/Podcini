package ac.mdiq.podcini.net.ssl

import android.util.Log
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.Charset
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * SSL trust manager that allows old Android systems to use modern certificates.
 */
object BackportTrustManager {
    private const val TAG = "BackportTrustManager"

    private fun getSystemTrustManager(keystore: KeyStore?): X509TrustManager {
        val factory: TrustManagerFactory
        try {
            factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(keystore)
            for (manager in factory.trustManagers) {
                if (manager is X509TrustManager) {
                    return manager
                }
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
                ByteArrayInputStream(BackportCaCerts.COMODO.toByteArray(Charset.forName("UTF-8")))))
            keystore.setCertificateEntry("SECTIGO_USER_TRUST_CA", cf.generateCertificate(
                ByteArrayInputStream(BackportCaCerts.SECTIGO_USER_TRUST.toByteArray(Charset.forName("UTF-8")))))
            keystore.setCertificateEntry("LETSENCRYPT_ISRG_CA", cf.generateCertificate(
                ByteArrayInputStream(BackportCaCerts.LETSENCRYPT_ISRG.toByteArray(Charset.forName("UTF-8")))))

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
}
