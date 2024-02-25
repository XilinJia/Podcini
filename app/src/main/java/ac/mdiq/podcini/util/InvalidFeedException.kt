package ac.mdiq.podcini.util

/**
 * Thrown if a feed has invalid attribute values.
 */
class InvalidFeedException(message: String?) : Exception(message) {
    companion object {
        private const val serialVersionUID = 1L
    }
}
