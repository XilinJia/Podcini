package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.storage.utils.FeedEpisodesFilter
import ac.mdiq.podcini.storage.utils.VolumeAdaptionSetting
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.Index

/**
 * Contains preferences for a single feed.
 */
class FeedPreferences(@Index var feedID: Long,
                      var autoDownload: Boolean,
                      /**
                       * @return true if this feed should be refreshed when everything else is being refreshed
                       * if false the feed should only be refreshed if requested directly.
                       */
                      var keepUpdated: Boolean,
                      @Ignore var currentAutoDelete: AutoDeleteAction,
                      @Ignore @JvmField var volumeAdaptionSetting: VolumeAdaptionSetting?,
                      var username: String?,
                      var password: String?,
                      @Ignore @JvmField var filter: FeedEpisodesFilter,
                      var playSpeed: Float,
                      var introSkip: Int,
                      var endingSkip: Int,
                      tags: RealmSet<String>) : EmbeddedRealmObject {

    var autoDelete: Int = 0
    var volumeAdaption: Int = 0

    var tags: RealmSet<String> = realmSetOf()

    @Ignore
    val tagsAsString: String
        get() = tags.joinToString(TAG_SEPARATOR)

    /**
     * Contains property strings. If such a property applies to a feed item, it is not shown in the feed list
     */
    var filterString: String = ""

    var sortOrderCode: Int = 0

    enum class AutoDeleteAction(@JvmField val code: Int) {
        GLOBAL(0),
        ALWAYS(1),
        NEVER(2);

        companion object {
            @JvmStatic
            fun fromCode(code: Int): AutoDeleteAction {
                for (action in entries) {
                    if (code == action.code) return action
                }
                return NEVER
            }
        }
    }

    constructor() : this(0L, false, true, AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, null, null,
        FeedEpisodesFilter(), SPEED_USE_GLOBAL, 0, 0, realmSetOf())

    constructor(feedID: Long, autoDownload: Boolean, autoDeleteAction: AutoDeleteAction,
                volumeAdaptionSetting: VolumeAdaptionSetting?, username: String?, password: String?)
            : this(feedID, autoDownload, true, autoDeleteAction, volumeAdaptionSetting, username, password,
        FeedEpisodesFilter(), SPEED_USE_GLOBAL, 0, 0, realmSetOf()) {

        this.autoDelete = autoDeleteAction.code
        this.volumeAdaption = volumeAdaptionSetting?.toInteger() ?: 0
    }

    init {
        this.tags.addAll(tags!!)
    }

    /**
     * Compare another FeedPreferences with this one. The feedID, autoDownload and AutoDeleteAction attribute are excluded from the
     * comparison.
     *
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

    fun getTags(): MutableSet<String> {
        return tags
    }

    companion object {
        const val SPEED_USE_GLOBAL: Float = -1f
        const val TAG_ROOT: String = "#root"
        const val TAG_SEPARATOR: String = "\u001e"
    }
}
