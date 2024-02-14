package ac.mdiq.podvinci.net.sync.gpoddernet

import ac.mdiq.podvinci.net.sync.model.SyncServiceException

open class GpodnetServiceException : SyncServiceException {
    constructor(message: String?) : super(message)

    constructor(e: Throwable?) : super(e)

    companion object {
        private const val serialVersionUID = 1L
    }
}
