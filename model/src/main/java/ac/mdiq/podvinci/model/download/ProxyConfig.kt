package ac.mdiq.podvinci.model.download

import java.net.Proxy

class ProxyConfig(@JvmField val type: Proxy.Type,
                  @JvmField val host: String?,
                  @JvmField val port: Int,
                  @JvmField val username: String?,
                  @JvmField val password: String?
) {
    companion object {
        const val DEFAULT_PORT: Int = 8080
    }
}