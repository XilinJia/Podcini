package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.storage.model.VolumeAdaptionSetting.Companion.fromInteger
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.Index

/**
 * Contains preferences for a single feed.
 */
class FeedPreferences : EmbeddedRealmObject {

    @Index var feedID: Long = 0L

    var autoDownload: Boolean = false

    /**
     * @return true if this feed should be refreshed when everything else is being refreshed
     * if false the feed should only be refreshed if requested directly.
     */
    var keepUpdated: Boolean = true

    var username: String? = null
    var password: String? = null

    var playSpeed: Float = SPEED_USE_GLOBAL

    var introSkip: Int = 0
    var endingSkip: Int = 0

    @Ignore
    var autoDeleteAction: AutoDeleteAction = AutoDeleteAction.GLOBAL
        get() = AutoDeleteAction.fromCode(autoDelete)
        set(value) {
            field = value
            autoDelete = field.code
        }
    var autoDelete: Int = 0

    @Ignore
    var volumeAdaptionSetting: VolumeAdaptionSetting = VolumeAdaptionSetting.OFF
        get() = fromInteger(volumeAdaption)
        set(value) {
            field = value
            volumeAdaption = field.toInteger()
        }
    var volumeAdaption: Int = 0

    var tags: RealmSet<String> = realmSetOf()

    @Ignore
    val tagsAsString: String
        get() = tags.joinToString(TAG_SEPARATOR)

    @Ignore
    var autoDownloadFilter: FeedAutoDownloadFilter = FeedAutoDownloadFilter()
        get() = FeedAutoDownloadFilter(autoDLInclude, autoDLExclude, autoDLMinDuration)
        set(value) {
            field = value
            autoDLInclude = field.includeFilterRaw
            autoDLExclude = field.excludeFilterRaw
            autoDLMinDuration = field.minimalDurationFilter
        }
    var autoDLInclude: String? = ""
    var autoDLExclude: String? = ""
    var autoDLMinDuration: Int = -1

    var filterString: String = ""

    var sortOrderCode: Int = 0

    enum class AutoDeleteAction(@JvmField val code: Int) {
        GLOBAL(0),
        ALWAYS(1),
        NEVER(2);

        companion object {
            @JvmStatic
            fun fromCode(code: Int): AutoDeleteAction {
                return enumValues<AutoDeleteAction>().firstOrNull { it.code == code } ?: NEVER
            }
        }
    }

    constructor() {}

    constructor(feedID: Long, autoDownload: Boolean, autoDeleteAction: AutoDeleteAction,
                volumeAdaptionSetting: VolumeAdaptionSetting?, username: String?, password: String?) {
        this.feedID = feedID
        this.autoDownload = autoDownload
        this.autoDeleteAction = autoDeleteAction
        if (volumeAdaptionSetting != null) this.volumeAdaptionSetting = volumeAdaptionSetting
        this.username = username
        this.password = password
        this.autoDelete = autoDeleteAction.code
        this.volumeAdaption = volumeAdaptionSetting?.toInteger() ?: 0
    }

    /**
     * Compare another FeedPreferences with this one. The feedID, autoDownload and AutoDeleteAction attribute are excluded from the
     * comparison.
     * @return True if the two objects are different.
     */
    fun compareWithOther(other: FeedPreferences?): Boolean {
        if (other == null) return true
        if (username != other.username) return true
        if (password != other.password) return true
        return false
    }

    /**
     * Update this FeedPreferences object from another one. The feedID, autoDownload and AutoDeleteAction attributes are excluded
     * from the update.
     */
    fun updateFromOther(other: FeedPreferences?) {
        if (other == null) return
        this.username = other.username
        this.password = other.password
    }

    companion object {
        const val SPEED_USE_GLOBAL: Float = -1f
        const val TAG_ROOT: String = "#root"
        const val TAG_SEPARATOR: String = "\u001e"
    }
}
