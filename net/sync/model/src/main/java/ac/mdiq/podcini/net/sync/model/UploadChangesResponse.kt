package ac.mdiq.podcini.net.sync.model

abstract class UploadChangesResponse(
        /**
         * timestamp/ID that can be used for requesting changes since this upload.
         */
        @JvmField val timestamp: Long
)
