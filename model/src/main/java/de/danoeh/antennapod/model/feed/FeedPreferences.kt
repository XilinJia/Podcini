package de.danoeh.antennapod.model.feed

import android.text.TextUtils
import java.io.Serializable

/**
 * Contains preferences for a single feed.
 */
class FeedPreferences(@JvmField var feedID: Long, @JvmField var autoDownload: Boolean,
                      /**
                       * @return true if this feed should be refreshed when everything else is being refreshed
                       * if false the feed should only be refreshed if requested directly.
                       */
                      @JvmField var keepUpdated: Boolean,
                      var currentAutoDelete: AutoDeleteAction, @JvmField var volumeAdaptionSetting: VolumeAdaptionSetting?,
                      @JvmField var username: String?, @JvmField var password: String?,
                      /**
                       * @return the filter for this feed
                       */
                      @JvmField var filter: FeedFilter,
                      @JvmField var feedPlaybackSpeed: Float, @JvmField var feedSkipIntro: Int, @JvmField var feedSkipEnding: Int,
                      /**
                       * getter for preference if notifications should be display for new episodes.
                       * @return true for displaying notifications
                       */
                      @JvmField var showEpisodeNotification: Boolean, @JvmField var newEpisodesAction: NewEpisodesAction?,
                      tags: Set<String>?
) : Serializable {
    enum class AutoDeleteAction(@JvmField val code: Int) {
        GLOBAL(0),
        ALWAYS(1),
        NEVER(2);

        companion object {
            @JvmStatic
            fun fromCode(code: Int): AutoDeleteAction {
                for (action in entries) {
                    if (code == action.code) {
                        return action
                    }
                }
                return NEVER
            }
        }
    }

    enum class NewEpisodesAction(@JvmField val code: Int) {
        GLOBAL(0),
        ADD_TO_INBOX(1),
        NOTHING(2);

        companion object {
            @JvmStatic
            fun fromCode(code: Int): NewEpisodesAction {
                for (action in entries) {
                    if (code == action.code) {
                        return action
                    }
                }
                return ADD_TO_INBOX
            }
        }
    }

    private val tags: MutableSet<String> = HashSet()

    constructor(feedID: Long, autoDownload: Boolean, autoDeleteAction: AutoDeleteAction,
                volumeAdaptionSetting: VolumeAdaptionSetting?, newEpisodesAction: NewEpisodesAction?,
                username: String?, password: String?
    ) : this(feedID, autoDownload, true, autoDeleteAction, volumeAdaptionSetting, username, password,
        FeedFilter(), SPEED_USE_GLOBAL, 0, 0, false, newEpisodesAction, HashSet<String>())

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
        if (other == null) {
            return true
        }
        if (!TextUtils.equals(username, other.username)) {
            return true
        }
        if (!TextUtils.equals(password, other.password)) {
            return true
        }
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

    val tagsAsString: String
        get() = TextUtils.join(TAG_SEPARATOR, tags)

    companion object {
        const val SPEED_USE_GLOBAL: Float = -1f
        const val TAG_ROOT: String = "#root"
        const val TAG_SEPARATOR: String = "\u001e"
    }
}
