package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.net.download.DownloadError
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey

import java.util.*

/**
 * Contains status attributes for one download
 */
class DownloadResult(
        /**
         * A human-readable string which is shown to the user so that he can
         * identify the download. Should be the title of the item/feed/media or the
         * URL if the download has no other title.
         */
        var title: String,
        var feedfileId: Long,
        /**
         * Is used to determine the type of the feedfile even if the feedfile does
         * not exist anymore. The value should be FEEDFILETYPE_FEED,
         * FEEDFILETYPE_FEEDIMAGE or FEEDFILETYPE_FEEDMEDIA
         */
        var feedfileType: Int,
        var isSuccessful: Boolean,
        @Ignore var reason: DownloadError?,
        completionDate: Date,
        /**
         * A message which can be presented to the user to give more information.
         * Should be null if Download was successful.
         */
        var reasonDetailed: String) : RealmObject {

    @PrimaryKey var id: Long = 0L

    @Ignore
    private val completionDate = completionDate.clone() as Date

    constructor() : this("", 0L, 0, false, null, Date(0L), "") {}

    /**
     * Constructor for creating new completed downloads.
     */
    constructor(id: Long, title: String, reason: DownloadError, successful: Boolean, reasonDetailed: String)
            : this(title, id, EpisodeMedia.FEEDFILETYPE_FEEDMEDIA, successful, reason, Date(), reasonDetailed)

    override fun toString(): String {
        return ("DownloadStatus [id=$id, title=$title, reason=$reason, reasonDetailed=$reasonDetailed, successful=$isSuccessful, completionDate=$completionDate, feedfileId=$feedfileId, feedfileType=$feedfileType]")
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