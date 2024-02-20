package ac.mdiq.podcini.net.sync.model

open class SyncServiceException : Exception {
    constructor(message: String?) : super(message)

    constructor(cause: Throwable?) : super(cause)

    companion object {
        private const val serialVersionUID = 1L
    }
}
