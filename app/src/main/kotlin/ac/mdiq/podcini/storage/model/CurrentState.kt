package ac.mdiq.podcini.storage.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class CurrentState : RealmObject {
    @PrimaryKey
    var id: Long = 0L

    var curMediaType: Long = NO_MEDIA_PLAYING

    var curFeedId: Long = 0

    var curMediaId: Long = 0

    var curIsVideo: Boolean = false

    var curPlayerStatus: Int = PLAYER_STATUS_OTHER

    var curTempSpeed: Float = FeedPreferences.SPEED_USE_GLOBAL

    constructor() {}

    companion object {
        val TAG: String = CurrentState::class.simpleName ?: "Anonymous"
        /**
         * Value of PREF_CURRENTLY_PLAYING_MEDIA if no media is playing.
         */
        const val NO_MEDIA_PLAYING: Long = -1

        /**
         * Value of PREF_CURRENT_PLAYER_STATUS if media player status is playing.
         */
        const val PLAYER_STATUS_PLAYING: Int = 1

        /**
         * Value of PREF_CURRENT_PLAYER_STATUS if media player status is paused.
         */
        const val PLAYER_STATUS_PAUSED: Int = 2

        /**
         * Value of PREF_CURRENT_PLAYER_STATUS if media player status is neither playing nor paused.
         */
        const val PLAYER_STATUS_OTHER: Int = 3
    }
}