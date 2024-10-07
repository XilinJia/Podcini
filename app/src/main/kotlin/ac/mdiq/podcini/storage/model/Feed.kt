package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.model.FeedFunding.Companion.extractPaymentLinks
import ac.mdiq.podcini.storage.model.EpisodeSortOrder.Companion.fromCode
import ac.mdiq.podcini.storage.utils.EpisodesPermutors.getPermutor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.FullText
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.*

class Feed : RealmObject {
    @PrimaryKey
    var id: Long = 0L  // increments from Date().time * 100 at time of creation

    @Index
    var identifier: String? = null

    var fileUrl: String? = null
    var downloadUrl: String? = null
//    var downloaded: Boolean = false

    /**
     * title as defined by the feed.
     */
    @FullText
    var eigenTitle: String? = null
        private set

    /**
     * custom title set by the user.
     */
    @FullText
    var customTitle: String? = null

    var link: String? = null

    @FullText
    var description: String? = null

    var language: String? = null

    @FullText
    var author: String? = null
    var imageUrl: String? = null

    var episodes: RealmList<Episode> = realmListOf()

    /**
     * String that identifies the last update (adopted from Last-Modified or ETag header).
     */
    var lastUpdate: String? = null

    // recorded when an episode starts playing when FeedEpisodes is open
    var lastPlayed: Long = 0
    /**
     * Feed type, options are defined in [FeedType].
     */
    var type: String? = null

    /**
     * The page number that this feed is on. Only feeds with page number "0" should be stored in the
     * database, feed objects with a higher page number only exist temporarily and should be merged
     * into feeds with page number "0".
     * This attribute's value is not saved in the database
     */
    var pageNr: Int = 0

    /**
     * True if this is a "paged feed", i.e. there exist other feed files that belong to the same
     * logical feed.
     */
    var isPaged: Boolean = false

    /**
     * Link to the next page of this feed. If this feed object represents a logical feed (i.e. a feed
     * that is saved in the database) this might be null while still being a paged feed.
     */
    var nextPageLink: String? = null

    var lastUpdateFailed = false

    var preferences: FeedPreferences? = null

    // TODO: this might not be needed
    var measures: FeedMeasures? = null

    var hasVideoMedia: Boolean = false

    /**
     * Returns the value that uniquely identifies this Feed. If the
     * feedIdentifier attribute is not null, it will be returned. Else it will
     * try to return the title. If the title is not given, it will use the link
     * of the feed.
     */
    @Ignore
    val identifyingValue: String?
        get() = when {
            !identifier.isNullOrEmpty() -> identifier
            !downloadUrl.isNullOrEmpty() -> downloadUrl
            !eigenTitle.isNullOrEmpty() -> eigenTitle
            else -> link
        }

    var payment_link: String? = null
    @Ignore
    var paymentLinks: ArrayList<FeedFunding> = ArrayList()
        private set

    @Ignore
    val isLocalFeed: Boolean
        get() = downloadUrl?.startsWith(PREFIX_LOCAL_FOLDER) ?: false

    @Ignore
    var episodeFilter: EpisodeFilter = EpisodeFilter("")
        private set
        get() = EpisodeFilter(preferences?.filterString ?: "")

    @Ignore
    var sortOrder: EpisodeSortOrder? = null
        get() = fromCode(preferences?.sortOrderCode ?: 0)
        set(value) {
            if (value == null) return
            field = value
            preferences?.sortOrderCode = value.code
        }

    @Ignore
    val mostRecentItem: Episode?
        get() = realm.query(Episode::class).query("feedId == $id SORT(pubDate DESC)").first().find()

    @Ignore
    var title: String?
        get() = if (!customTitle.isNullOrEmpty()) customTitle else eigenTitle
        set(value) {
            this.eigenTitle = value
        }

    @Ignore
    var sortInfo: String = ""

    @Ignore
    var isBuilding by mutableStateOf(false)

    /**
     * This constructor is used for test purposes.
     */
    constructor(id: Long, lastUpdate: String?, title: String?, link: String?, description: String?, paymentLink: String?,
                author: String?, language: String?, type: String?, feedIdentifier: String?, imageUrl: String?, fileUrl: String?,
                downloadUrl: String?) {
        this.id = id
        this.fileUrl = fileUrl
        this.downloadUrl = downloadUrl
        this.eigenTitle = title
        setCustomTitle1(customTitle)
        this.lastUpdate = lastUpdate
        this.link = link
        this.description = description
        this.paymentLinks = extractPaymentLinks(paymentLink)
        this.author = author
        this.language = language
        this.type = type
        this.identifier = feedIdentifier
        this.imageUrl = imageUrl
        this.isPaged = false
        this.preferences?.filterString = ""
        this.sortOrder = sortOrder
        this.preferences?.sortOrderCode = sortOrder?.code ?: 0
    }

    /**
     * This constructor can be used when parsing feed data. Only the 'lastUpdate' and 'items' field are initialized.
     */
    constructor() : super()

    /**
     * This constructor is used for requesting a feed download (it must not be used for anything else!). It should NOT be
     * used if the title of the feed is already known.
     */
    constructor(url: String?, lastUpdate: String?) {
        this.lastUpdate = lastUpdate
        fileUrl = null
        this.downloadUrl = url
    }

    /**
     * This constructor is used for requesting a feed download (it must not be used for anything else!). It should be
     * used if the title of the feed is already known.
     */
    constructor(url: String?, lastUpdate: String?, title: String?) : this(url, lastUpdate) {
        this.eigenTitle = title
    }

    /**
     * This constructor is used for requesting a feed download (it must not be used for anything else!). It should be
     * used if the title of the feed is already known.
     */
    constructor(url: String?, lastUpdate: String?, title: String?, username: String?, password: String?) : this(url, lastUpdate, title) {
        preferences = FeedPreferences(0, false, FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, username, password)
    }

    fun setCustomTitle1(value: String?) {
        customTitle = if (value == null || value == eigenTitle) null else value
    }

    fun getTextIdentifier(): String? {
        return when {
            !customTitle.isNullOrEmpty() -> customTitle
            !eigenTitle.isNullOrEmpty() -> eigenTitle
            else -> downloadUrl
        }
    }

    fun updateFromOther(other: Feed) {
        // don't update feed's download_url, we do that manually if redirected
        // see PodciniHttpClient
        if (other.imageUrl != null) this.imageUrl = other.imageUrl
        if (other.eigenTitle != null) eigenTitle = other.eigenTitle
        if (other.identifier != null) identifier = other.identifier
        if (other.link != null) link = other.link
        if (other.description != null) description = other.description
        if (other.language != null) language = other.language
        if (other.author != null) author = other.author
        if (other.paymentLinks.isNotEmpty()) paymentLinks = other.paymentLinks

        // this feed's nextPage might already point to a higher page, so we only update the nextPage value
        // if this feed is not paged and the other feed is.
        if (!this.isPaged && other.isPaged) {
            this.isPaged = other.isPaged
            this.nextPageLink = other.nextPageLink
        }
    }

    fun compareWithOther(other: Feed): Boolean {
        if (other.imageUrl != null) {
            if (imageUrl == null || imageUrl != other.imageUrl) return true
        }
        if (eigenTitle != other.eigenTitle) return true
        if (other.identifier != null) {
            if (identifier == null || identifier != other.identifier) return true
        }
        if (other.link != null) {
            if (link == null || link != other.link) return true
        }
        if (other.description != null) {
            if (description == null || description != other.description) return true
        }
        if (other.language != null) {
            if (language == null || language != other.language) return true
        }
        if (other.author != null) {
            if (author == null || author != other.author) return true
        }
        if (other.paymentLinks.isNotEmpty()) {
            if (paymentLinks.isEmpty() || paymentLinks != other.paymentLinks) return true
        }
        if (other.isPaged && !this.isPaged) return true
        if (other.nextPageLink != this.nextPageLink) return true
        return false
    }

    fun getTypeAsInt(): Int {
        return FEEDFILETYPE_FEED
    }

    fun addPayment(funding: FeedFunding) {
        paymentLinks.add(funding)
    }

    fun getVirtualQueueItems():  List<Episode> {
        var qString = "feedId == $id AND playState != ${Episode.PlayState.PLAYED.code}"
//        TODO: perhaps need to set prefStreamOverDownload for youtube feeds
        if (type != FeedType.YOUTUBE.name && preferences?.prefStreamOverDownload != true) qString += " AND media.downloaded == true"
        val eList_ = realm.query(Episode::class, qString).find().toMutableList()
        if (sortOrder != null) getPermutor(sortOrder!!).reorder(eList_)
        return eList_
    }

    enum class FeedType(name: String) {
        RSS("rss"),
        ATOM1("atom"),
        YOUTUBE("YouTube")
    }

    companion object {
        val TAG: String = Feed::class.simpleName ?: "Anonymous"

        const val FEEDFILETYPE_FEED: Int = 0
        const val PREFIX_LOCAL_FOLDER: String = "podcini_local:"
        const val PREFIX_GENERATIVE_COVER: String = "podcini_generative_cover:"

        fun newId(): Long {
            return Date().time * 100
        }
    }
}
