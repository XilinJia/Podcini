package ac.mdiq.podcini.model.feed

import android.text.TextUtils
import ac.mdiq.podcini.model.feed.FeedFunding.Companion.extractPaymentLinks
import java.util.*
import kotlin.collections.ArrayList

/**
 * Data Object for a whole feed.
 *
 * @author daniel
 */
class Feed : FeedFile {
    /**
     * title as defined by the feed.
     */
    var feedTitle: String? = null
        private set

    /**
     * custom title set by the user.
     */
    private var customTitle: String? = null

    /**
     * Contains 'id'-element in Atom feed.
     */
    @JvmField
    var feedIdentifier: String? = null

    /**
     * Link to the website.
     */
    @JvmField
    var link: String? = null
    @JvmField
    var description: String? = null
    @JvmField
    var language: String? = null

    /**
     * Name of the author.
     */
    @JvmField
    var author: String? = null
    @JvmField
    var imageUrl: String? = null
    @JvmField
    var items: MutableList<FeedItem> = mutableListOf()

    /**
     * String that identifies the last update (adopted from Last-Modified or ETag header).
     */
    @JvmField
    var lastUpdate: String? = null

    var paymentLinks: ArrayList<FeedFunding> = ArrayList()
        private set

    /**
     * Feed type, for example RSS 2 or Atom.
     */
    @JvmField
    var type: String? = null

    /**
     * Feed preferences.
     */
    @JvmField
    var preferences: FeedPreferences? = null

    /**
     * The page number that this feed is on. Only feeds with page number "0" should be stored in the
     * database, feed objects with a higher page number only exist temporarily and should be merged
     * into feeds with page number "0".
     *
     *
     * This attribute's value is not saved in the database
     */
    @JvmField
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
    @JvmField
    var nextPageLink: String? = null

    private var lastUpdateFailed = false

    /**
     * Contains property strings. If such a property applies to a feed item, it is not shown in the feed list
     */
    var itemFilter: FeedItemFilter? = null
        private set

    /**
     * User-preferred sortOrder for display.
     * Only those of scope [SortOrder.Scope.INTRA_FEED] is allowed.
     */
    var sortOrder: SortOrder? = null
        set(sortOrder) {
            require(!(sortOrder != null && sortOrder.scope != SortOrder.Scope.INTRA_FEED)) {
                ("The specified sortOrder " + sortOrder
                        + " is invalid. Only those with INTRA_FEED scope are allowed.")
            }
            field = sortOrder
        }

    /**
     * This constructor is used for restoring a feed from the database.
     */
    constructor(id: Long, lastUpdate: String?, title: String?, customTitle: String?, link: String?,
                description: String?, paymentLinks: String?, author: String?, language: String?,
                type: String?, feedIdentifier: String?, imageUrl: String?, fileUrl: String?,
                downloadUrl: String?, downloaded: Boolean, paged: Boolean, nextPageLink: String?,
                filter: String?, sortOrder: SortOrder?, lastUpdateFailed: Boolean
    ) : super(fileUrl, downloadUrl, downloaded) {
        this.id = id
        this.feedTitle = title
        this.customTitle = customTitle
        this.lastUpdate = lastUpdate
        this.link = link
        this.description = description
        this.paymentLinks = extractPaymentLinks(paymentLinks)
        this.author = author
        this.language = language
        this.type = type
        this.feedIdentifier = feedIdentifier
        this.imageUrl = imageUrl
        this.isPaged = paged
        this.nextPageLink = nextPageLink
        this.items = mutableListOf()
        if (filter != null) {
            this.itemFilter = FeedItemFilter(filter)
        } else {
            this.itemFilter = FeedItemFilter()
        }
        this.sortOrder = sortOrder
        this.lastUpdateFailed = lastUpdateFailed
    }

    /**
     * This constructor is used for test purposes.
     */
    constructor(id: Long,
                lastUpdate: String?,
                title: String?,
                link: String?,
                description: String?,
                paymentLink: String?,
                author: String?,
                language: String?,
                type: String?,
                feedIdentifier: String?,
                imageUrl: String?,
                fileUrl: String?,
                downloadUrl: String?,
                downloaded: Boolean
    ) : this(id,
        lastUpdate,
        title,
        null,
        link,
        description,
        paymentLink,
        author,
        language,
        type,
        feedIdentifier,
        imageUrl,
        fileUrl,
        downloadUrl,
        downloaded,
        false,
        null,
        null,
        null,
        false)

    /**
     * This constructor can be used when parsing feed data. Only the 'lastUpdate' and 'items' field are initialized.
     */
    constructor() : super()

    /**
     * This constructor is used for requesting a feed download (it must not be used for anything else!). It should NOT be
     * used if the title of the feed is already known.
     */
    constructor(url: String?, lastUpdate: String?) : super(null, url, false) {
        this.lastUpdate = lastUpdate
    }

    /**
     * This constructor is used for requesting a feed download (it must not be used for anything else!). It should be
     * used if the title of the feed is already known.
     */
    constructor(url: String?, lastUpdate: String?, title: String?) : this(url, lastUpdate) {
        this.feedTitle = title
    }

    /**
     * This constructor is used for requesting a feed download (it must not be used for anything else!). It should be
     * used if the title of the feed is already known.
     */
    constructor(url: String?, lastUpdate: String?, title: String?, username: String?, password: String?) : this(url,
        lastUpdate,
        title) {
        preferences = FeedPreferences(0, true, FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
            FeedPreferences.NewEpisodesAction.GLOBAL, username, password)
    }

    /**
     * Returns the item at the specified index.
     *
     */
    fun getItemAtIndex(position: Int): FeedItem {
        return items[position]
    }

    val identifyingValue: String?
        /**
         * Returns the value that uniquely identifies this Feed. If the
         * feedIdentifier attribute is not null, it will be returned. Else it will
         * try to return the title. If the title is not given, it will use the link
         * of the feed.
         */
        get() = if (!feedIdentifier.isNullOrEmpty()) {
            feedIdentifier
        } else if (!download_url.isNullOrEmpty()) {
            download_url
        } else if (!feedTitle.isNullOrEmpty()) {
            feedTitle
        } else {
            link
        }

    override fun getHumanReadableIdentifier(): String? {
        return if (!TextUtils.isEmpty(customTitle)) {
            customTitle
        } else if (!TextUtils.isEmpty(feedTitle)) {
            feedTitle
        } else {
            download_url
        }
    }

    fun updateFromOther(other: Feed) {
        // don't update feed's download_url, we do that manually if redirected
        // see PodciniHttpClient
        if (other.imageUrl != null) {
            this.imageUrl = other.imageUrl
        }
        if (other.feedTitle != null) {
            feedTitle = other.feedTitle
        }
        if (other.feedIdentifier != null) {
            feedIdentifier = other.feedIdentifier
        }
        if (other.link != null) {
            link = other.link
        }
        if (other.description != null) {
            description = other.description
        }
        if (other.language != null) {
            language = other.language
        }
        if (other.author != null) {
            author = other.author
        }
        if (other.paymentLinks.isNotEmpty()) {
            paymentLinks = other.paymentLinks
        }
        // this feed's nextPage might already point to a higher page, so we only update the nextPage value
        // if this feed is not paged and the other feed is.
        if (!this.isPaged && other.isPaged) {
            this.isPaged = other.isPaged
            this.nextPageLink = other.nextPageLink
        }
    }

    fun compareWithOther(other: Feed): Boolean {
        if (super.compareWithOther(other)) {
            return true
        }
        if (other.imageUrl != null) {
            if (imageUrl == null || !TextUtils.equals(imageUrl, other.imageUrl)) {
                return true
            }
        }
        if (!TextUtils.equals(feedTitle, other.feedTitle)) {
            return true
        }
        if (other.feedIdentifier != null) {
            if (feedIdentifier == null || feedIdentifier != other.feedIdentifier) {
                return true
            }
        }
        if (other.link != null) {
            if (link == null || link != other.link) {
                return true
            }
        }
        if (other.description != null) {
            if (description == null || description != other.description) {
                return true
            }
        }
        if (other.language != null) {
            if (language == null || language != other.language) {
                return true
            }
        }
        if (other.author != null) {
            if (author == null || author != other.author) {
                return true
            }
        }
        if (other.paymentLinks.isNotEmpty()) {
            if (paymentLinks.isEmpty() || paymentLinks != other.paymentLinks) {
                return true
            }
        }
        if (other.isPaged && !this.isPaged) {
            return true
        }
        if (!TextUtils.equals(other.nextPageLink, this.nextPageLink)) {
            return true
        }
        return false
    }

    val mostRecentItem: FeedItem?
        get() {
            // we could sort, but we don't need to, a simple search is fine...
            var mostRecentDate = Date(0)
            var mostRecentItem: FeedItem? = null
            for (item in items) {
                val date = item.getPubDate()
                if (date != null && date.after(mostRecentDate)) {
                    mostRecentDate = date
                    mostRecentItem = item
                }
            }
            return mostRecentItem
        }

    override fun getTypeAsInt(): Int {
        return FEEDFILETYPE_FEED
    }

    var title: String?
        get() = if (!TextUtils.isEmpty(customTitle)) customTitle else feedTitle
        set(title) {
            this.feedTitle = title
        }

    fun getCustomTitle(): String? {
        return this.customTitle
    }

    fun setCustomTitle(customTitle: String?) {
        if (customTitle == null || customTitle == feedTitle) {
            this.customTitle = null
        } else {
            this.customTitle = customTitle
        }
    }

    fun addPayment(funding: FeedFunding) {
        paymentLinks.add(funding)
    }

    override var id: Long
        get() = super.id
        set(id) {
            super.id = id
            if (preferences != null) {
                preferences!!.feedID = id
            }
        }

    fun setItemFilter(properties: Array<String?>?) {
        if (properties != null) {
            val props = properties.filterNotNull().toTypedArray()
            this.itemFilter = FeedItemFilter(*props)
        }
    }

    fun hasLastUpdateFailed(): Boolean {
        return this.lastUpdateFailed
    }

    fun setLastUpdateFailed(lastUpdateFailed: Boolean) {
        this.lastUpdateFailed = lastUpdateFailed
    }

    val isLocalFeed: Boolean
        get() = download_url!!.startsWith(PREFIX_LOCAL_FOLDER)

    companion object {
        const val FEEDFILETYPE_FEED: Int = 0
        const val TYPE_RSS2: String = "rss"
        const val TYPE_ATOM1: String = "atom"
        const val PREFIX_LOCAL_FOLDER: String = "podcini_local:"
        const val PREFIX_GENERATIVE_COVER: String = "podcini_generative_cover:"
    }
}
