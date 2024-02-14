package ac.mdiq.podvinci.core

/**
 * Stores callbacks for core classes like Services, DB classes etc. and other configuration variables.
 * Apps using the core module of PodVinci should register implementations of all interfaces here.
 */
object ClientConfig {
    /**
     * Should be used when setting User-Agent header for HTTP-requests.
     */
    var USER_AGENT: String? = null

    @JvmField
    var applicationCallbacks: ApplicationCallbacks? = null
}
