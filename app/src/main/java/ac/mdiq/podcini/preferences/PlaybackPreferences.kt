package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.storage.DBReader
import ac.mdiq.podcini.storage.model.feed.FeedItem
import ac.mdiq.podcini.storage.model.feed.FeedMedia
import ac.mdiq.podcini.storage.model.feed.FeedPreferences
import ac.mdiq.podcini.storage.model.playback.MediaType
import ac.mdiq.podcini.storage.model.playback.Playable
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.event.PlayerStatusEvent
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.util.Log
import androidx.preference.PreferenceManager
import org.greenrobot.eventbus.EventBus

/**
 * Provides access to preferences set by the playback service. A private
 * instance of this class must first be instantiated via createInstance() or
 * otherwise every public method will throw an Exception when called.
 */
class PlaybackPreferences private constructor() : OnSharedPreferenceChangeListener {

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (PREF_CURRENT_PLAYER_STATUS == key) EventBus.getDefault().post(PlayerStatusEvent())
    }

    companion object {
        private const val TAG = "PlaybackPreferences"

        /**
         * Contains the feed id of the currently playing item if it is a FeedMedia
         * object.
         */
        private const val PREF_CURRENTLY_PLAYING_FEED_ID = "ac.mdiq.podcini.preferences.lastPlayedFeedId"

        /**
         * Contains the id of the currently playing FeedMedia object or
         * NO_MEDIA_PLAYING if the currently playing media is no FeedMedia object.
         */
        private const val PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID = "ac.mdiq.podcini.preferences.lastPlayedFeedMediaId"

        /**
         * Type of the media object that is currently being played. This preference
         * is set to NO_MEDIA_PLAYING after playback has been completed and is set
         * as soon as the 'play' button is pressed.
         */
        private const val PREF_CURRENTLY_PLAYING_MEDIA_TYPE = "ac.mdiq.podcini.preferences.currentlyPlayingMedia"

        /**
         * True if last played media was a video.
         */
        private const val PREF_CURRENT_EPISODE_IS_VIDEO = "ac.mdiq.podcini.preferences.lastIsVideo"

        /**
         * The current player status as int.
         */
        private const val PREF_CURRENT_PLAYER_STATUS = "ac.mdiq.podcini.preferences.currentPlayerStatus"

        /**
         * A temporary playback speed which overrides the per-feed playback speed for the currently playing
         * media. Considered unset if set to SPEED_USE_GLOBAL;
         */
        private const val PREF_CURRENTLY_PLAYING_TEMPORARY_PLAYBACK_SPEED =
            "ac.mdiq.podcini.preferences.temporaryPlaybackSpeed"


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

        private var instance: PlaybackPreferences? = null
        private lateinit var prefs: SharedPreferences

        @JvmStatic
        fun init(context: Context) {
            instance = PlaybackPreferences()
            prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.registerOnSharedPreferenceChangeListener(instance)
        }

        @JvmStatic
        val currentlyPlayingMediaType: Long
            get() = prefs.getLong(PREF_CURRENTLY_PLAYING_MEDIA_TYPE, NO_MEDIA_PLAYING)

        @JvmStatic
        val currentlyPlayingFeedMediaId: Long
            get() = prefs.getLong(PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID, NO_MEDIA_PLAYING)

        @JvmStatic
        val currentEpisodeIsVideo: Boolean
            get() = prefs.getBoolean(PREF_CURRENT_EPISODE_IS_VIDEO, false)

        @JvmStatic
        val currentPlayerStatus: Int
            get() = prefs.getInt(PREF_CURRENT_PLAYER_STATUS, PLAYER_STATUS_OTHER)

        @JvmStatic
        var currentlyPlayingTemporaryPlaybackSpeed: Float
            get() = prefs.getFloat(PREF_CURRENTLY_PLAYING_TEMPORARY_PLAYBACK_SPEED, FeedPreferences.SPEED_USE_GLOBAL)
            set(speed) {
                val editor = prefs.edit()
                editor.putFloat(PREF_CURRENTLY_PLAYING_TEMPORARY_PLAYBACK_SPEED, speed)
                editor.apply()
            }

        @JvmStatic
        fun writeNoMediaPlaying() {
            val editor = prefs.edit()
            editor.putLong(PREF_CURRENTLY_PLAYING_MEDIA_TYPE, NO_MEDIA_PLAYING)
            editor.putLong(PREF_CURRENTLY_PLAYING_FEED_ID, NO_MEDIA_PLAYING)
            editor.putLong(PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID, NO_MEDIA_PLAYING)
            editor.putInt(PREF_CURRENT_PLAYER_STATUS, PLAYER_STATUS_OTHER)
            editor.apply()
        }

        @JvmStatic
        fun writeMediaPlaying(playable: Playable?, playerStatus: PlayerStatus, item: FeedItem? = null) {
            Logd(TAG, "Writing playback preferences ${playable?.getIdentifier()}")
            val editor = prefs.edit()

            if (playable == null) {
                writeNoMediaPlaying()
            } else {
//                Log.d(TAG, "writeMediaPlaying ${playable.getPlayableType()}")
                editor.putLong(PREF_CURRENTLY_PLAYING_MEDIA_TYPE, playable.getPlayableType().toLong())
                editor.putBoolean(PREF_CURRENT_EPISODE_IS_VIDEO, playable.getMediaType() == MediaType.VIDEO)
                if (playable is FeedMedia) {
                    val itemId = playable.item?.feed?.id
                    if (itemId != null) editor.putLong(PREF_CURRENTLY_PLAYING_FEED_ID, itemId)
                    editor.putLong(PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID, playable.id)
                } else {
                    editor.putLong(PREF_CURRENTLY_PLAYING_FEED_ID, NO_MEDIA_PLAYING)
                    editor.putLong(PREF_CURRENTLY_PLAYING_FEEDMEDIA_ID, NO_MEDIA_PLAYING)
                }
                playable.writeToPreferences(editor)
            }
            editor.putInt(PREF_CURRENT_PLAYER_STATUS, getCurrentPlayerStatusAsInt(playerStatus))

            editor.apply()
        }

        @JvmStatic
        fun writePlayerStatus(playerStatus: PlayerStatus) {
            Logd(TAG, "Writing player status playback preferences")

            val editor = prefs.edit()
            editor.putInt(PREF_CURRENT_PLAYER_STATUS, getCurrentPlayerStatusAsInt(playerStatus))
            editor.apply()
        }

        @JvmStatic
        fun clearCurrentlyPlayingTemporaryPlaybackSpeed() {
            val editor = prefs.edit()
            editor.remove(PREF_CURRENTLY_PLAYING_TEMPORARY_PLAYBACK_SPEED)
            editor.apply()
        }

        private fun getCurrentPlayerStatusAsInt(playerStatus: PlayerStatus): Int {
            val playerStatusAsInt = when (playerStatus) {
                PlayerStatus.PLAYING -> PLAYER_STATUS_PLAYING
                PlayerStatus.PAUSED -> PLAYER_STATUS_PAUSED
                else -> PLAYER_STATUS_OTHER
            }
            return playerStatusAsInt
        }

        /**
         * Restores a playable object from a sharedPreferences file. This method might load data from the database,
         * depending on the type of playable that was restored.
         *
         * @return The restored Playable object
         */
        @JvmStatic
        fun createInstanceFromPreferences(context: Context): Playable? {
            val currentlyPlayingMedia = currentlyPlayingMediaType
            Logd(TAG, "currentlyPlayingMedia: $currentlyPlayingMedia")
            if (currentlyPlayingMedia != NO_MEDIA_PLAYING) {
                val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
                return createInstanceFromPreferences(currentlyPlayingMedia.toInt(), prefs)
            }
            return null
        }

        /**
         * Restores a playable object from a sharedPreferences file. This method might load data from the database,
         * depending on the type of playable that was restored.
         *
         * @param type An integer that represents the type of the Playable object
         * that is restored.
         * @param pref The SharedPreferences file from which the Playable object
         * is restored
         * @return The restored Playable object
         */
        private fun createInstanceFromPreferences(type: Int, pref: SharedPreferences): Playable? {
            if (type == FeedMedia.PLAYABLE_TYPE_FEEDMEDIA) {
                return createFeedMediaInstance(pref)
            } else {
                Log.e(TAG, "Could not restore Playable object from preferences")
                return null
            }
        }

        private fun createFeedMediaInstance(pref: SharedPreferences): Playable? {
            var result: Playable? = null
            val mediaId = pref.getLong(FeedMedia.PREF_MEDIA_ID, -1)
            if (mediaId != -1L) result = DBReader.getFeedMedia(mediaId)
            return result
        }
    }
}
