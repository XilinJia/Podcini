package ac.mdiq.podcini.util.config

/**
 * Stores callbacks for core classes like Services, DB classes etc. and other configuration variables.
 * Apps using the core module of Podcini should register implementations of all interfaces here.
 */
object ClientConfig {
    /**
     * Should be used when setting User-Agent header for HTTP-requests.
     */
    var USER_AGENT: String? = null

    @JvmField
    var applicationCallbacks: ac.mdiq.podcini.util.config.ApplicationCallbacks? = null
}
