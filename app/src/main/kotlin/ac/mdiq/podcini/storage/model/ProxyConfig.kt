package ac.mdiq.podcini.storage.model

import java.net.Proxy

class ProxyConfig(@JvmField val type: Proxy.Type,
                  @JvmField val host: String?,
                  @JvmField val port: Int,
                  @JvmField val username: String?,
                  @JvmField val password: String?) {
    
    companion object {
        const val DEFAULT_PORT: Int = 8080
    }
}