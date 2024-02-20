package ac.mdiq.podcini.net.ssl

import android.content.Context
import org.conscrypt.Conscrypt
import java.security.Security

object SslProviderInstaller {
    fun install(context: Context?) {
        // Insert bundled conscrypt as highest security provider (overrides OS version).
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
    }
}
