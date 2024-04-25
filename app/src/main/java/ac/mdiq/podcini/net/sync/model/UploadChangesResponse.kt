package ac.mdiq.podcini.net.sync.model

/**
 * timestamp/ID that can be used for requesting changes since this upload.
 */
abstract class UploadChangesResponse(@JvmField val timestamp: Long)
