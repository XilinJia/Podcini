package ac.mdiq.podvinci.net.sync.gpoddernet

internal class GpodnetServiceBadStatusCodeException(message: String?, private val statusCode: Int) :
    GpodnetServiceException(message) {
    companion object {
        private const val serialVersionUID = 1L
    }
}
