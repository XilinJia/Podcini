package ac.mdiq.podcini.storage.model.download

import ac.mdiq.podcini.storage.model.feed.FeedFile
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import java.util.*

/**
 * Contains status attributes for one download
 */
class DownloadResult(
        /**
         * Unique id for storing the object in database.
         */
        @JvmField var id: Long,
        /**
         * A human-readable string which is shown to the user so that he can
         * identify the download. Should be the title of the item/feed/media or the
         * URL if the download has no other title.
         */
        @JvmField val title: String,
        @JvmField val feedfileId: Long,
        /**
         * Is used to determine the type of the feedfile even if the feedfile does
         * not exist anymore. The value should be FEEDFILETYPE_FEED,
         * FEEDFILETYPE_FEEDIMAGE or FEEDFILETYPE_FEEDMEDIA
         */
        @JvmField val feedfileType: Int,
        var isSuccessful: Boolean,
        @JvmField var reason: DownloadError?,
        completionDate: Date,
        /**
         * A message which can be presented to the user to give more information.
         * Should be null if Download was successful.
         */
        @JvmField var reasonDetailed: String
) {
    private val completionDate = completionDate.clone() as Date

    /**
     * Constructor for creating new completed downloads.
     */
    constructor(feedfile: FeedFile, title: String, reason: DownloadError, successful: Boolean, reasonDetailed: String
    ) : this(0, title, feedfile.id, FeedMedia.FEEDFILETYPE_FEEDMEDIA, successful, reason, Date(),
        reasonDetailed)

    override fun toString(): String {
        return ("DownloadStatus [id=" + id + ", title=" + title + ", reason="
                + reason + ", reasonDetailed=" + reasonDetailed
                + ", successful=" + isSuccessful + ", completionDate="
                + completionDate + ", feedfileId=" + feedfileId
                + ", feedfileType=" + feedfileType + "]")
    }

    fun getCompletionDate(): Date {
        return completionDate.clone() as Date
    }

    fun setSuccessful() {
        this.isSuccessful = true
        this.reason = DownloadError.SUCCESS
    }

    fun setFailed(reason: DownloadError, reasonDetailed: String) {
        this.isSuccessful = false
        this.reason = reason
        this.reasonDetailed = reasonDetailed
    }

    fun setCancelled() {
        this.isSuccessful = false
        this.reason = DownloadError.ERROR_DOWNLOAD_CANCELLED
    }

    companion object {
        /**
         * Downloaders should use this constant for the size attribute if necessary
         * so that the listadapters etc. can react properly.
         */
        const val SIZE_UNKNOWN: Int = -1
    }
}