package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.model.VolumeAdaptionSetting.Companion.fromInteger
import androidx.media3.common.C
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.annotations.Ignore

class FeedPreferences : EmbeddedRealmObject {

    var feedID: Long = 0L

    /**
     * @return true if this feed should be refreshed when everything else is being refreshed
     * if false the feed should only be refreshed if requested directly.
     */
    var keepUpdated: Boolean = true

    var username: String? = null
    var password: String? = null

    //    var playAudioOnly: Boolean = false
    @Ignore
    var videoModePolicy: VideoMode = VideoMode.NONE
        get() = VideoMode.fromCode(videoMode)
        set(value) {
            field = value
            videoMode = field.code
        }
    var videoMode: Int = VideoMode.NONE.code

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
    var autoDelete: Int = AutoDeleteAction.GLOBAL.code

    @Ignore
    var audioTypeSetting: AudioType = AudioType.SPEECH
        get() = AudioType.fromCode(audioType)
        set(value) {
            field = value
            audioType = field.code
        }
    var audioType: Int = AudioType.SPEECH.code

    @Ignore
    var volumeAdaptionSetting: VolumeAdaptionSetting = VolumeAdaptionSetting.OFF
        get() = fromInteger(volumeAdaption)
        set(value) {
            field = value
            volumeAdaption = field.toInteger()
        }
    var volumeAdaption: Int = VolumeAdaptionSetting.OFF.value

    @Ignore
    var audioQualitySetting: AVQuality = AVQuality.GLOBAL
        get() = AVQuality.fromCode(audioQuality)
        set(value) {
            field = value
            audioQuality = field.code
        }
    var audioQuality: Int = AVQuality.GLOBAL.code

    @Ignore
    var videoQualitySetting: AVQuality = AVQuality.GLOBAL
        get() = AVQuality.fromCode(videoQuality)
        set(value) {
            field = value
            videoQuality = field.code
        }
    var videoQuality: Int = AVQuality.GLOBAL.code

    var prefStreamOverDownload: Boolean = false

    var filterString: String = ""

    var sortOrderCode: Int = 0      // in EpisodeSortOrder

//    seems not too useful
//    var sortOrderAuxCode: Int = 0      // in EpisodeSortOrder

    @Ignore
    val tagsAsString: String
        get() = tags.joinToString(TAG_SEPARATOR)
    var tags: RealmSet<String> = realmSetOf()

    var autoDownload: Boolean = false

    @Ignore
    var queue: PlayQueue? = null
        get() = when {
            queueId >= 0 -> realm.query(PlayQueue::class).query("id == $queueId").first().find()
            queueId == -1L -> curQueue
            queueId == -2L -> null
            else -> null
        }
        set(value) {
            field = value
            queueId = value?.id ?: -1L
        }
    @Ignore
    var queueText: String = "Default"
        get() = when (queueId) {
            0L -> "Default"
            -1L -> "Active"
            -2L -> "None"
            else -> "Custom"
        }
    @Ignore
    val queueTextExt: String
        get() = when (queueId) {
            -1L -> "Active"
            -2L -> "None"
            else -> queue?.name ?: "Default"
        }
    var queueId: Long = 0L

    var autoAddNewToQueue: Boolean = false

    @Ignore
    var autoDownloadFilter: FeedAutoDownloadFilter? = null
        get() = field ?: FeedAutoDownloadFilter(autoDLInclude, autoDLExclude, autoDLMinDuration, markExcludedPlayed)
        set(value) {
            field = value
            autoDLInclude = value?.includeFilterRaw ?: ""
            autoDLExclude = value?.excludeFilterRaw ?: ""
            autoDLMinDuration = value?.minimalDurationFilter ?: -1
            markExcludedPlayed = value?.markExcludedPlayed == true
        }
    var autoDLInclude: String? = ""
    var autoDLExclude: String? = ""
    var autoDLMinDuration: Int = -1
    var markExcludedPlayed: Boolean = false

    var autoDLMaxEpisodes: Int = 3
    var countingPlayed: Boolean = true      // relates to autoDLMaxEpisodes

    @Ignore
    var autoDLPolicy: AutoDownloadPolicy = AutoDownloadPolicy.ONLY_NEW
        get() = AutoDownloadPolicy.fromCode(autoDLPolicyCode)
        set(value) {
            field = value
            autoDLPolicyCode = value.code
        }
    var autoDLPolicyCode: Int = AutoDownloadPolicy.ONLY_NEW.code

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

//    These appear not needed
    /**
     * Compare another FeedPreferences with this one. .
     * @return True if the two objects are different.
     */
//    fun compareWithOther(other: FeedPreferences?): Boolean {
//        if (other == null) return true
//        if (username != other.username) return true
//        if (password != other.password) return true
//        return false
//    }

    /**
     * Update this FeedPreferences object from another one.
     */
//    fun updateFromOther(other: FeedPreferences?) {
//        if (other == null) return
//        this.username = other.username
//        this.password = other.password
//    }

    enum class AutoDownloadPolicy(val code: Int, val resId: Int) {
        ONLY_NEW(0, R.string.feed_auto_download_new),
        NEWER(1, R.string.feed_auto_download_newer),
        OLDER(2, R.string.feed_auto_download_older),
        SOON(3, R.string.feed_auto_download_soon);

        companion object {
            fun fromCode(code: Int): AutoDownloadPolicy {
                return enumValues<AutoDownloadPolicy>().firstOrNull { it.code == code } ?: ONLY_NEW
            }
        }
    }

    enum class AutoDeleteAction(val code: Int, val tag: String) {
        GLOBAL(0, "global"),
        ALWAYS(1, "always"),
        NEVER(2, "never");

        companion object {
            fun fromCode(code: Int): AutoDeleteAction {
                return enumValues<AutoDeleteAction>().firstOrNull { it.code == code } ?: NEVER
            }
            fun fromTag(tag: String): AutoDeleteAction {
                return enumValues<AutoDeleteAction>().firstOrNull { it.tag == tag } ?: NEVER
            }
        }
    }

    enum class AudioType(val code: Int, val tag: String) {
        UNKNOWN(C.AUDIO_CONTENT_TYPE_UNKNOWN, "Unknown"),
        SPEECH(C.AUDIO_CONTENT_TYPE_SPEECH, "Speech"),
        MUSIC(C.AUDIO_CONTENT_TYPE_MUSIC, "Music"),
        MOVIE(C.AUDIO_CONTENT_TYPE_MOVIE, "Movie");

        companion object {
            fun fromCode(code: Int): AudioType {
                return enumValues<AudioType>().firstOrNull { it.code == code } ?: SPEECH
            }
            fun fromTag(tag: String): AudioType {
                return enumValues<AudioType>().firstOrNull { it.tag == tag } ?: SPEECH
            }
        }
    }

    enum class AVQuality(val code: Int, val tag: String) {
        GLOBAL(0, "Global"),
        LOW(1, "Low"),
        MEDIUM(5, "Medium"),
        HIGH(10, "High");

        companion object {
            fun fromCode(code: Int): AVQuality {
                return enumValues<AVQuality>().firstOrNull { it.code == code } ?: GLOBAL
            }
            fun fromTag(tag: String): AVQuality {
                return enumValues<AVQuality>().firstOrNull { it.tag == tag } ?: GLOBAL
            }
        }
    }

    companion object {
        const val SPEED_USE_GLOBAL: Float = -1f
        const val TAG_ROOT: String = "#root"
        const val TAG_SEPARATOR: String = "\u001e"

        val FeedAutoDeleteOptions = AutoDeleteAction.entries.map { it.tag }
    }
}
